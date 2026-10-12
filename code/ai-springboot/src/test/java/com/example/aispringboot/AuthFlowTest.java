package com.example.aispringboot;

import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.common.UserContext;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证链路端到端校验。
 *
 * <p>库中三个既有账号（admin / test / ces）的口令均为 123456，已通过 BCrypt matches 实测确认。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class AuthFlowTest {

    @Resource
    private MockMvc mockMvc;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JwtUtil jwtUtil;

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    public void login_管理员账号_返回token且userType为2() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userInfo.userType").value(2))
                .andExpect(jsonPath("$.data.userInfo.username").value("admin"))
                .andReturn();

        String token = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertEquals(1L, jwtUtil.getUserId(jwtUtil.parseToken(token)), "token 应携带 admin 的 userId");
        assertEquals(2, jwtUtil.getUserType(jwtUtil.parseToken(token)));
    }

    @Test
    public void login_密码错误_返回业务错误码() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "wrong-pwd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"));
    }

    @Test
    public void login_账号不存在_返回6002() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "no_such_user_xyz", "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("6002"));
    }

    /**
     * 注册后立刻登录。方法上加 @Transactional 是为了让测试数据回滚，
     * 不在既有库中留下垃圾账号。
     */
    @Test
    @Transactional(rollbackFor = Exception.class)
    public void register_后登录_可成功且固定为普通用户() throws Exception {
        String username = "junit_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/user/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", username + "@junit.local",
                                "password", "abc123456",
                                "userType", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                // 注册入口不接受提权，userType 强制为 1
                .andExpect(jsonPath("$.data.userInfo.userType").value(1))
                .andExpect(jsonPath("$.data.userInfo.status").value(1))
                // created_at 由数据库默认值写入，需回读后才能返回
                .andExpect(jsonPath("$.data.userInfo.createdAt").isNotEmpty());

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "abc123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.userInfo.username").value(username));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void register_用户名重复_返回6001() throws Exception {
        Map<String, Object> payload = Map.of(
                "username", "admin",
                "email", "admin@example.com",
                "password", "abc123456");
        mockMvc.perform(post("/api/user/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("6001"))
                .andExpect(jsonPath("$.msg").value("用户名已存在"));
    }

    @Test
    public void 受保护接口_无token时返回401() throws Exception {
        mockMvc.perform(post("/api/user/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    public void 受保护接口_带token时放行且上下文在请求结束后被清理() throws Exception {
        String token = jwtUtil.generateToken(1L, 2);
        mockMvc.perform(post("/api/user/logout").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        // afterCompletion 必须清掉 ThreadLocal，否则线程复用会串号
        assertNull(UserContext.getUserId(), "请求结束后 UserContext 应已清理");
    }

    @Test
    public void 受保护接口_无效token返回401() throws Exception {
        mockMvc.perform(post("/api/user/logout").header("token", "not-a-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    public void 匿名接口_无需token即可访问() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn();
        assertNotNull(r.getResponse().getContentAsString());
    }
}
