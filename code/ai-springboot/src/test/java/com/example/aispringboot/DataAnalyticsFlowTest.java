package com.example.aispringboot;

import com.example.aispringboot.common.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据分析看板。
 *
 * <p>只读接口，不写库，因此不用 @Transactional。
 * 断言的基线取自当前存量：3 个用户、17 条日记（user_id 2 和 4）、
 * 5 个会话、13 条消息，日记日期分布在 2025-09-08 至 2025-11-11。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class DataAnalyticsFlowTest {

    private static final long EXISTING_USERS = 3;
    private static final long EXISTING_DIARIES = 17;
    private static final long EXISTING_SESSIONS = 5;
    /** 存量日记分布在两个用户下，全量区间内都应算活跃 */
    private static final long ACTIVE_USERS_IN_FULL_RANGE = 2;

    @Resource
    private MockMvc mockMvc;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JwtUtil jwtUtil;

    private String adminToken() {
        return jwtUtil.generateToken(1L, 2);
    }

    private String userToken() {
        return jwtUtil.generateToken(2L, 1);
    }

    private JsonNode overview(String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/data-analytics/overview" + (query == null ? "" : query))
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    @Test
    public void 卡片字段与存量数据一致() throws Exception {
        JsonNode data = overview(null);
        JsonNode so = data.get("systemOverview");

        assertEquals(EXISTING_USERS, so.get("totalUsers").asLong());
        assertEquals(EXISTING_DIARIES, so.get("totalDiaries").asLong());
        assertEquals(EXISTING_SESSIONS, so.get("totalSession").asLong());
        assertEquals(ACTIVE_USERS_IN_FULL_RANGE, so.get("activeUsers").asLong());
        assertEquals(6.1, so.get("avgMoodScore").asDouble(), 0.05);

        // 存量数据都停在 2025 年，今天没有新增
        assertEquals(0, so.get("todayNewDiaries").asLong());
        assertEquals(0, so.get("todayNewSession").asLong());
    }

    @Test
    public void 四个分块齐全且层级与前端一致() throws Exception {
        JsonNode data = overview(null);
        assertTrue(data.has("systemOverview"));
        assertTrue(data.has("emotionTrend"));
        assertTrue(data.has("consultationStats"));
        assertTrue(data.has("userActivity"));

        JsonNode stats = data.get("consultationStats");
        assertEquals(EXISTING_SESSIONS, stats.get("totalSessions").asLong());
        assertNotNull(stats.get("avgDurationMinutes"), "会话时长没有结束时间字段，取最后一条消息时间计算");
        assertTrue(stats.has("dailyTrend"));

        JsonNode first = data.get("userActivity").get(0);
        assertTrue(first.has("activeUsers"));
        assertTrue(first.has("newUsers"));
        assertTrue(first.has("diaryUsers"));
        assertTrue(first.has("consultationUsers"));
    }

    @Test
    public void 趋势按天补零且记录数守恒() throws Exception {
        JsonNode data = overview(null);
        JsonNode trend = data.get("emotionTrend");

        long records = 0;
        LocalDate prev = null;
        for (JsonNode item : trend) {
            LocalDate date = LocalDate.parse(item.get("date").asText());
            if (prev != null) {
                assertEquals(1, ChronoUnit.DAYS.between(prev, date), "日期必须连续，缺天会让前端图表整体错位");
            }
            prev = date;
            long count = item.get("recordCount").asLong();
            records += count;
            if (count > 0) {
                assertTrue(item.get("avgMoodScore").isNumber(), "有记录日应给出平均分");
            } else {
                assertTrue(item.get("avgMoodScore").isNull(), "无记录日的评分应为 null");
            }
        }
        assertEquals(EXISTING_DIARIES, records, "补零不能改变日记总量");

        // 区间覆盖全库最早的一天
        LocalDate earliest = LocalDate.parse(trend.get(0).get("date").asText());
        assertTrue(earliest.isBefore(LocalDate.of(2025, 9, 20)), "区间起点应早于存量数据，实际为 " + earliest);
        assertEquals(LocalDate.now(), prev);
    }

    @Test
    public void 指定天数时三条趋势都按该长度补零() throws Exception {
        JsonNode data = overview("?days=7");
        assertEquals(7, data.get("emotionTrend").size());
        assertEquals(7, data.get("userActivity").size());
        assertEquals(7, data.get("consultationStats").get("dailyTrend").size());

        // 最近 7 天没有存量数据：记录数补 0，但评分保持 null 让前端画成断点
        assertEquals(EXISTING_DIARIES, data.get("systemOverview").get("totalDiaries").asLong());
        for (JsonNode item : data.get("emotionTrend")) {
            assertEquals(0, item.get("recordCount").asLong());
            assertTrue(item.get("avgMoodScore").isNull(), "无记录日的评分应为 null，补 0 会把折线拽到轴底");
        }
        assertEquals(LocalDate.now().minusDays(6),
                LocalDate.parse(data.get("emotionTrend").get(0).get("date").asText()));
    }

    @Test
    public void 活跃度取并集去重而非相加() throws Exception {
        JsonNode data = overview(null);
        for (JsonNode item : data.get("userActivity")) {
            long diary = item.get("diaryUsers").asLong();
            long consult = item.get("consultationUsers").asLong();
            long active = item.get("activeUsers").asLong();
            assertTrue(active >= diary && active >= consult,
                    "活跃用户应覆盖两类行为：date=" + item.get("date").asText());
            assertTrue(active <= diary + consult,
                    "同一用户既写日记又咨询只能算一次：date=" + item.get("date").asText());
        }
    }

    @Test
    public void 未登录返回401() throws Exception {
        mockMvc.perform(get("/api/data-analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    public void 普通用户返回A0301() throws Exception {
        mockMvc.perform(get("/api/data-analytics/overview").header("token", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    public void 天数越界返回400() throws Exception {
        mockMvc.perform(get("/api/data-analytics/overview").param("days", "0")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));

        mockMvc.perform(get("/api/data-analytics/overview").param("days", "99999")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));
    }

    /** 类型绑定失败走 MethodArgumentTypeMismatchException，与业务参数错误的码不同 */
    @Test
    public void 天数非数字返回4002() throws Exception {
        mockMvc.perform(get("/api/data-analytics/overview").param("days", "abc")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("4002"));
    }
}
