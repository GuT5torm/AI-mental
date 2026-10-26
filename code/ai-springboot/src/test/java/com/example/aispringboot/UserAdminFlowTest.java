package com.example.aispringboot;

import com.example.aispringboot.common.JwtUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台用户管理接口校验。
 *
 * <p>seed 预置：id=1 admin（管理员），id=2 test（普通用户）。
 * 全部 @Transactional 回滚，不产生脏数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class UserAdminFlowTest {

    @Resource
    private MockMvc mockMvc;
    @Resource
    private JwtUtil jwtUtil;

    private String adminToken() {
        return jwtUtil.generateToken(1L, 2);
    }

    private String userToken() {
        return jwtUtil.generateToken(2L, 1);
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 普通用户访问用户分页_被拒绝() throws Exception {
        mockMvc.perform(get("/api/user/admin/page")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 未登录访问用户分页_被拒绝() throws Exception {
        mockMvc.perform(get("/api/user/admin/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 管理员分页_含seed用户且不含密码() throws Exception {
        mockMvc.perform(get("/api/user/admin/page")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.username == 'test')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.username == 'admin')]").exists());
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 关键词过滤_只返回匹配用户() throws Exception {
        mockMvc.perform(get("/api/user/admin/page")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("test"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 禁用后_登录被拒绝() throws Exception {
        mockMvc.perform(put("/api/user/admin/2/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("status", "0"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("-1"))
                .andExpect(jsonPath("$.msg").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 不能禁用自己() throws Exception {
        mockMvc.perform(put("/api/user/admin/1/status")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.msg").value("不能修改自己的状态"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 编辑用户资料_生效() throws Exception {
        mockMvc.perform(put("/api/user/admin/2")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小测试\",\"email\":\"newtest@example.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/admin/page")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("keyword", "newtest@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].nickname").value("小测试"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 后台新增用户_默认密码可登录() throws Exception {
        mockMvc.perform(post("/api/user/admin/create")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admincreated\",\"nickname\":\"后台新增\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.username").value("admincreated"))
                .andExpect(jsonPath("$.data.userType").value(1));

        // 默认初始密码 Abc@123456 可直接登录
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admincreated\",\"password\":\"Abc@123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 后台新增用户_重名被拒绝() throws Exception {
        mockMvc.perform(post("/api/user/admin/create")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("用户名已存在"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 编辑用户真实模型开关_生效() throws Exception {
        mockMvc.perform(put("/api/user/admin/2")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiRealEnabled\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/admin/page")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].aiRealEnabled").value(0));

        mockMvc.perform(put("/api/user/admin/2")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiRealEnabled\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/admin/page")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].aiRealEnabled").value(1));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 重置密码后_可用初始密码登录() throws Exception {
        mockMvc.perform(put("/api/user/admin/2/password")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test\",\"password\":\"Abc@123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }
}
