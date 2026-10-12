package com.example.aispringboot;

import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.entity.KnowledgeArticle;
import com.example.aispringboot.mapper.KnowledgeArticleMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库与文件上传的端到端校验。
 *
 * <p>库里既有 7 篇文章，状态全为 1（已发布）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class KnowledgeFlowTest {

    @Resource
    private MockMvc mockMvc;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private KnowledgeArticleMapper articleMapper;

    private String adminToken() {
        return jwtUtil.generateToken(1L, 2);
    }

    private String userToken() {
        return jwtUtil.generateToken(2L, 1);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    public void 分类列表_返回全部启用分类() throws Exception {
        mockMvc.perform(get("/api/knowledge/category/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].categoryName").isNotEmpty());
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 前台匿名只看已发布_管理员可看草稿() throws Exception {
        // 先用管理员建一篇草稿
        String id = createDraft("草稿文章-匿名不可见");

        // 匿名访问：草稿应被过滤掉，只剩既有的 7 篇
        mockMvc.perform(get("/api/knowledge/article/page")
                        .param("currentPage", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7));

        // 管理员访问：能看到 8 篇
        mockMvc.perform(get("/api/knowledge/article/page")
                        .header("token", adminToken())
                        .param("currentPage", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(8));

        // 管理员按状态筛选草稿
        mockMvc.perform(get("/api/knowledge/article/page")
                        .header("token", adminToken())
                        .param("currentPage", "1").param("size", "10").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("草稿文章-匿名不可见"));

        mockMvc.perform(delete("/api/knowledge/article/" + id).header("token", adminToken()))
                .andExpect(jsonPath("$.code").value("200"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 分页参数_两套命名都能生效() throws Exception {
        // pageNum / pageSize
        mockMvc.perform(get("/api/knowledge/article/page")
                        .param("pageNum", "1").param("pageSize", "3"))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.records.length()").value(3));

        // currentPage / size
        mockMvc.perform(get("/api/knowledge/article/page")
                        .param("currentPage", "2").param("size", "3"))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.records.length()").value(3))
                .andExpect(jsonPath("$.data.current").value(2));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 排序参数_按阅读量倒序() throws Exception {
        mockMvc.perform(get("/api/knowledge/article/page")
                        .param("currentPage", "1").param("size", "10")
                        .param("sortField", "readCount").param("sortDirection", "desc"))
                .andExpect(jsonPath("$.data.records[0].readCount").value(42));
    }

    /** 既有数据中的第一篇文章，标签为「焦虑,情绪管理,心理健康」 */
    private static final String SAMPLE_ARTICLE_ID = "550e8400-e29b-41d4-a716-446655440001";

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 前台详情_自增阅读量且返回分类作者名() throws Exception {
        KnowledgeArticle sample = articleMapper.selectById(SAMPLE_ARTICLE_ID);
        int before = sample.getReadCount() == null ? 0 : sample.getReadCount();

        mockMvc.perform(get("/api/knowledge/article/" + sample.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.title").value(sample.getTitle()))
                .andExpect(jsonPath("$.data.categoryName").isNotEmpty())
                .andExpect(jsonPath("$.data.authorName").isNotEmpty())
                .andExpect(jsonPath("$.data.tagArray.length()").value(3))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                // 后台列表页用的字段名是 updateAt
                .andExpect(jsonPath("$.data.updateAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content").isNotEmpty());

        KnowledgeArticle after = articleMapper.selectById(sample.getId());
        assertEquals(before + 1, after.getReadCount(), "前台访问详情应自增阅读量");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 管理员访问详情_不自增阅读量() throws Exception {
        KnowledgeArticle sample = articleMapper.selectById(SAMPLE_ARTICLE_ID);
        int before = sample.getReadCount() == null ? 0 : sample.getReadCount();

        mockMvc.perform(get("/api/knowledge/article/" + sample.getId()).header("token", adminToken()))
                .andExpect(jsonPath("$.code").value("200"));

        assertEquals(before, articleMapper.selectById(sample.getId()).getReadCount(),
                "后台编辑读取详情不应计入阅读量");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 普通用户_不能新增文章() throws Exception {
        mockMvc.perform(post("/api/knowledge/article")
                        .header("token", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "越权测试", "content", "x", "categoryId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 管理员_新增发布下线删除全流程() throws Exception {
        String id = createDraft("全流程测试文章");
        mockMvc.perform(put("/api/knowledge/article/" + id + "/status")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 1))))
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusDisplayName").value("已发布"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());

        mockMvc.perform(put("/api/knowledge/article/" + id + "/status")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 2))))
                .andExpect(jsonPath("$.data.statusDisplayName").value("已下线"));

        mockMvc.perform(delete("/api/knowledge/article/" + id).header("token", adminToken()))
                .andExpect(jsonPath("$.code").value("200"));

        mockMvc.perform(get("/api/knowledge/article/" + id).header("token", adminToken()))
                .andExpect(jsonPath("$.code").value("6003"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 文件上传_落盘并写入文件记录() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", MediaType.IMAGE_PNG_VALUE, "fake-png-bytes".getBytes());

        MvcResult r = mockMvc.perform(multipart("/api/file/upload")
                        .file(file)
                        .header("token", adminToken())
                        .param("businessType", "ARTICLE")
                        .param("businessId", "  b51ecba7-db3c-4a30-84ab-3a19e1e8264b  ")
                        .param("businessField", "cover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.filePath").isNotEmpty())
                .andExpect(jsonPath("$.data.fileType").value("IMG"))
                .andExpect(jsonPath("$.data.businessType").value("ARTICLE"))
                .andReturn();

        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        String filePath = data.path("filePath").asText();
        // 沿用库里既有路径的拼写，含 bussiness
        assertTrue(filePath.startsWith("/files/bussiness/article/"), "实际路径: " + filePath);
        assertTrue(filePath.endsWith(".png"), "实际路径: " + filePath);
        // business_id 是 char(255)，读回来必须已 trim
        assertEquals("b51ecba7-db3c-4a30-84ab-3a19e1e8264b", data.path("businessId").asText());

        String diskPath = "uploads" + filePath.substring("/files".length());
        assertTrue(Files.exists(Paths.get(diskPath)), "文件应已落盘: " + diskPath);
        Files.deleteIfExists(Paths.get(diskPath));
    }

    @Test
    public void 未登录_不能上传文件() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", MediaType.IMAGE_PNG_VALUE, "x".getBytes());
        mockMvc.perform(multipart("/api/file/upload").file(file))
                .andExpect(jsonPath("$.code").value("401"));
    }

    private String createDraft(String title) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/knowledge/article")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", title,
                                "content", "<p>正文</p>",
                                "categoryId", 1,
                                "summary", "摘要",
                                "tags", "测试,临时"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.statusDisplayName").value("草稿"))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }
}
