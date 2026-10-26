package com.example.aispringboot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.common.AiKeyCipher;
import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.entity.AiSystemConfig;
import com.example.aispringboot.entity.ConsultationMessage;
import com.example.aispringboot.entity.ConsultationSession;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.mapper.AiSystemConfigMapper;
import com.example.aispringboot.mapper.ConsultationMessageMapper;
import com.example.aispringboot.mapper.ConsultationSessionMapper;
import com.example.aispringboot.mapper.UserMapper;
import com.example.aispringboot.service.ConsultationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员全局 AI 配置的端到端校验。
 *
 * <p>属性组合与 {@link AiConfigFlowTest} 一致，共享同一个测试上下文。
 * 覆盖：管理员权限校验、加密落库与掩码回显、演示模式状态端点、
 * 全局 Key 保存即生效（对话链路无需重启自动切换真实模型）、
 * 用户级真实模型开关（关闭后强制演示模式，开启后立即恢复）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.ai.scheduler.enabled=false",
        "app.ai.config-secret=test-secret-0123456789",
        "app.emotion-capture.enabled=false"
})
public class AiSystemConfigFlowTest {

    private static final String PLAIN_KEY = "sk-global-secret-1234567890";
    private static final String MASKED_KEY = "sk-****7890";

    @Resource
    private MockMvc mockMvc;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private AiSystemConfigMapper configMapper;
    @Resource
    private AiKeyCipher cipher;
    @Resource
    private ConsultationSessionMapper sessionMapper;
    @Resource
    private ConsultationMessageMapper messageMapper;
    @Resource
    private UserMapper userMapper;

    private String adminToken() {
        return jwtUtil.generateToken(1L, 2);
    }

    private String userToken() {
        return jwtUtil.generateToken(2L, 1);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    /** 清理全局配置，用例间互不污染 */
    private void cleanGlobalConfig() {
        configMapper.deleteById((short) 1);
    }

    /** 直插一条指向假端点的全局配置（绕过 SSRF 校验） */
    private void insertGlobalConfig(FakeOpenAiServer fake) {
        cleanGlobalConfig();
        AiSystemConfig config = new AiSystemConfig();
        config.setId((short) 1);
        config.setApiKeyCipher(cipher.encrypt(PLAIN_KEY));
        config.setBaseUrl(fake.url());
        config.setModel("deepseek-chat");
        config.setUpdatedBy(1L);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(config);
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 非管理员访问全局配置被拒绝() throws Exception {
        mockMvc.perform(get("/api/ai/admin-config").header("token", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0301"));

        mockMvc.perform(put("/api/ai/admin-config")
                        .header("token", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("apiKey", PLAIN_KEY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 管理员保存后返回掩码且库里只落密文() throws Exception {
        try {
            mockMvc.perform(put("/api/ai/admin-config")
                            .header("token", adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("apiKey", PLAIN_KEY))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data.configured").value(true))
                    .andExpect(jsonPath("$.data.maskedKey").value(MASKED_KEY));

            AiSystemConfig saved = configMapper.selectById((short) 1);
            assertNotNull(saved, "全局配置应已落库");
            assertNotEquals(PLAIN_KEY, saved.getApiKeyCipher(), "库中不允许出现明文 Key");
            assertTrue(saved.getApiKeyCipher().contains(":"), "密文应为 Base64(iv):Base64(密文) 格式");
            assertEquals(PLAIN_KEY, cipher.decrypt(saved.getApiKeyCipher()), "密文应可用主密钥还原");
            assertEquals(1L, saved.getUpdatedBy(), "应记录操作管理员");
        } finally {
            cleanGlobalConfig();
        }
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 演示模式状态随全局配置存在与否切换() throws Exception {
        try (FakeOpenAiServer fake = new FakeOpenAiServer()) {
            mockMvc.perform(get("/api/ai-config/status").header("token", userToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.demoMode").value(true));

            insertGlobalConfig(fake);

            mockMvc.perform(get("/api/ai-config/status").header("token", userToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.systemConfigured").value(true))
                    .andExpect(jsonPath("$.data.demoMode").value(false));
        } finally {
            cleanGlobalConfig();
        }
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 管理员清空全局配置后回到演示模式() throws Exception {
        try (FakeOpenAiServer fake = new FakeOpenAiServer()) {
            insertGlobalConfig(fake);
            mockMvc.perform(delete("/api/ai/admin-config").header("token", adminToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"));
            mockMvc.perform(get("/api/ai-config/status").header("token", userToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.demoMode").value(true));
        } finally {
            cleanGlobalConfig();
        }
    }

    /**
     * 全局 Key 保存即生效：普通用户未自配 Key 时，流式对话自动走全局配置的端点。
     *
     * <p>不标事务：推流在独立线程里落库，改用 finally 自清理。
     */
    @Test
    public void 全局Key自动生效于流式对话() throws Exception {
        try (FakeOpenAiServer fake = new FakeOpenAiServer()) {
            insertGlobalConfig(fake);

            MvcResult start = mockMvc.perform(post("/api/psychological-chat/session/start")
                            .header("token", userToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("sessionTitle", "全局Key流式用例"))))
                    .andExpect(status().isOk())
                    .andReturn();
            String sessionId = objectMapper.readTree(start.getResponse().getContentAsString())
                    .get("data").get("sessionId").asText();
            long id = ConsultationService.parseSessionId(sessionId);

            try {
                MvcResult stream = mockMvc.perform(post("/api/psychological-chat/stream")
                                .header("token", userToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("sessionId", sessionId, "userMessage", "最近压力有点大"))))
                        .andExpect(request().asyncStarted())
                        .andReturn();
                stream.getAsyncResult(15000);

                assertTrue(stream.getResponse().getContentAsString().contains("event:done"),
                        "应推送命名事件 done");

                List<ConsultationMessage> messages = messageMapper.selectList(
                        Wrappers.lambdaQuery(ConsultationMessage.class)
                                .eq(ConsultationMessage::getSessionId, id)
                                .orderByAsc(ConsultationMessage::getId));
                assertEquals(2, messages.size(), "用户消息与 AI 消息都应落库");
                assertTrue(messages.get(1).getContent().contains(FakeOpenAiServer.REPLY_TEXT),
                        "全局 Key 应自动生效，回复来自假端点，实际=" + messages.get(1).getContent());
                assertTrue(fake.requestCount() > 0, "假端点应收到真实请求");
            } finally {
                messageMapper.deleteBySessionId(id);
                sessionMapper.deleteById(id);
                cleanGlobalConfig();
            }
        }
    }

    /**
     * 用户级真实模型开关：管理员关闭后，即使全局 Key 存在，该用户的流式对话仍走演示模式；
     * 重新开启后立即恢复真实模型，无需重启。
     *
     * <p>不标事务：推流在独立线程里落库，改用 finally 自清理。
     */
    @Test
    public void 用户开关关闭时全局Key被拦截回演示模式() throws Exception {
        try (FakeOpenAiServer fake = new FakeOpenAiServer()) {
            insertGlobalConfig(fake);

            MvcResult start = mockMvc.perform(post("/api/psychological-chat/session/start")
                            .header("token", userToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("sessionTitle", "用户开关流式用例"))))
                    .andExpect(status().isOk())
                    .andReturn();
            String sessionId = objectMapper.readTree(start.getResponse().getContentAsString())
                    .get("data").get("sessionId").asText();
            long id = ConsultationService.parseSessionId(sessionId);

            try {
                userMapper.update(null, Wrappers.lambdaUpdate(User.class)
                        .eq(User::getId, 2L).set(User::getAiRealEnabled, 0));

                mockMvc.perform(post("/api/psychological-chat/stream")
                                .header("token", userToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("sessionId", sessionId, "userMessage", "最近压力有点大"))))
                        .andExpect(request().asyncStarted())
                        .andReturn()
                        .getAsyncResult(15000);

                List<ConsultationMessage> messages = messageMapper.selectList(
                        Wrappers.lambdaQuery(ConsultationMessage.class)
                                .eq(ConsultationMessage::getSessionId, id)
                                .orderByAsc(ConsultationMessage::getId));
                assertEquals(2, messages.size(), "开关关闭时回复仍应落库");
                assertFalse(messages.get(1).getContent().contains(FakeOpenAiServer.REPLY_TEXT),
                        "开关关闭时不应调用真实端点，实际=" + messages.get(1).getContent());
                assertEquals(0, fake.requestCount(), "开关关闭时假端点不应收到任何请求");

                userMapper.update(null, Wrappers.lambdaUpdate(User.class)
                        .eq(User::getId, 2L).set(User::getAiRealEnabled, 1));

                mockMvc.perform(post("/api/psychological-chat/stream")
                                .header("token", userToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("sessionId", sessionId, "userMessage", "还是有点紧张"))))
                        .andExpect(request().asyncStarted())
                        .andReturn()
                        .getAsyncResult(15000);

                List<ConsultationMessage> after = messageMapper.selectList(
                        Wrappers.lambdaQuery(ConsultationMessage.class)
                                .eq(ConsultationMessage::getSessionId, id)
                                .orderByAsc(ConsultationMessage::getId));
                assertTrue(after.size() > 2, "重新开启后应继续产生新消息");
                assertTrue(after.get(after.size() - 1).getContent().contains(FakeOpenAiServer.REPLY_TEXT),
                        "重新开启后全局 Key 应立即生效，实际=" + after.get(after.size() - 1).getContent());
                assertTrue(fake.requestCount() > 0, "重新开启后假端点应收到请求");
            } finally {
                messageMapper.deleteBySessionId(id);
                sessionMapper.deleteById(id);
                cleanGlobalConfig();
            }
        }
    }
}
