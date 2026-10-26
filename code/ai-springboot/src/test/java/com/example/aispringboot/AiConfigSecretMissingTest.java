package com.example.aispringboot;

import com.example.aispringboot.common.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 服务端未配置 AI_CONFIG_SECRET 时保存 Key 必须明确报错。
 *
 * <p>属性组合与既有测试类一致，共享同一个 Spring 上下文；
 * 该上下文里没有 config-secret，cipher 处于未就绪状态。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class AiConfigSecretMissingTest {

    @Resource
    private MockMvc mockMvc;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JwtUtil jwtUtil;

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 服务端未配置主密钥时保存返回6051且不落明文() throws Exception {
        mockMvc.perform(put("/api/ai-config")
                        .header("token", jwtUtil.generateToken(1L, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("apiKey", "sk-no-secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("6051"));
    }
}
