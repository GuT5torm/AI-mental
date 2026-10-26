package com.example.aispringboot;

import com.example.aispringboot.common.JwtUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文章置顶功能校验：管理员可置顶/取消，前台列表置顶优先，普通用户无权操作。
 *
 * <p>文章 id 从 seed 数据的列表接口动态获取，不依赖固定 UUID。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class KnowledgeTopFlowTest {

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

    /** 取前台列表第一篇已发布文章的 id */
    private String firstArticleId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/knowledge/article/page")
                        .header("Authorization", "Bearer " + token)
                        .param("currentPage", "1").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn().getResponse().getContentAsString();
        // 简单提取 records[0].id（UUID 字符串）
        int idx = body.indexOf("\"records\":[{\"id\":\"");
        if (idx < 0) {
            return null;
        }
        int start = idx + "\"records\":[{\"id\":\"".length();
        return body.substring(start, body.indexOf("\"", start));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 普通用户置顶_被拒绝() throws Exception {
        String articleId = firstArticleId(adminToken());
        if (articleId == null) {
            return;
        }
        mockMvc.perform(put("/api/knowledge/article/{id}/top", articleId)
                        .header("Authorization", "Bearer " + userToken())
                        .param("top", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 置顶后_列表第一篇为该文且isTop为1() throws Exception {
        String token = adminToken();
        String articleId = firstArticleId(token);
        if (articleId == null) {
            return;
        }
        mockMvc.perform(put("/api/knowledge/article/{id}/top", articleId)
                        .header("Authorization", "Bearer " + token)
                        .param("top", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.isTop").value(1));

        mockMvc.perform(get("/api/knowledge/article/page")
                        .header("Authorization", "Bearer " + token)
                        .param("currentPage", "1").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(articleId))
                .andExpect(jsonPath("$.data.records[0].isTop").value(1));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 取消置顶_isTop归零() throws Exception {
        String token = adminToken();
        String articleId = firstArticleId(token);
        if (articleId == null) {
            return;
        }
        mockMvc.perform(put("/api/knowledge/article/{id}/top", articleId)
                        .header("Authorization", "Bearer " + token)
                        .param("top", "true"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/knowledge/article/{id}/top", articleId)
                        .header("Authorization", "Bearer " + token)
                        .param("top", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isTop").value(0));
    }
}
