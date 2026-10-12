package com.example.aispringboot.mapper;

import com.example.aispringboot.DTO.response.AnalyticsOverviewDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据分析看板的聚合查询。
 *
 * <p>全部走原生 SQL：这里是按日期分组 + 补零的聚合，MyBatis-Plus 的
 * LambdaQueryWrapper 拼不出来，硬套只会把 Java 侧的循环拖到数据库外面。
 *
 * <p>补零用 {@code generate_series} 左连业务表，而不是把结果捞回 Java 再补。
 * 前端 echarts 的 category 轴按数组下标对齐，缺一天就会把后面的点整体左移，
 * 让趋势图错位，这个错误在只有零星数据的库上尤其看不出来。
 */
public interface AnalyticsMapper {

    /**
     * 全库最早的一天，用于不传 days 时决定区间起点。
     *
     * <p>三张表各取 min 再取最小：看板同时展示日记、咨询、新增用户三条线，
     * 区间必须覆盖最靠前的那条，否则会出现"总量 17 但趋势图里加不出 17"的对不上账。
     * PostgreSQL 的 LEAST 会跳过 NULL，三张表都空时由 coalesce 兜到今天。
     */
    @Select("""
            SELECT coalesce(LEAST(
                     (SELECT min(diary_date) FROM emotion_diary),
                     (SELECT min(started_at)::date FROM consultation_session),
                     (SELECT min(created_at)::date FROM "user")
                   ), CURRENT_DATE)
            """)
    LocalDate selectEarliestDate();

    /**
     * 顶部四张卡片。
     *
     * <p>写成一行多列的标量查询，而不是七条独立语句：看板一次刷新要拿到全部卡片，
     * 拆成七次往返会让接口时间随卡片数线性增长。
     */
    @Select("""
            SELECT (SELECT count(*) FROM "user") AS total_users,
                   (SELECT count(DISTINCT t.user_id) FROM (
                        SELECT user_id FROM emotion_diary WHERE diary_date >= #{from}
                        UNION
                        SELECT user_id FROM consultation_session WHERE started_at::date >= #{from}
                    ) t) AS active_users,
                   (SELECT count(*) FROM emotion_diary) AS total_diaries,
                   (SELECT count(*) FROM emotion_diary WHERE diary_date = CURRENT_DATE) AS today_new_diaries,
                   (SELECT count(*) FROM consultation_session) AS total_session,
                   (SELECT count(*) FROM consultation_session WHERE started_at >= CURRENT_DATE) AS today_new_session,
                   (SELECT coalesce(round(avg(mood_score)::numeric, 1), 0) FROM emotion_diary) AS avg_mood_score
            """)
    AnalyticsOverviewDTO.SystemOverview selectSystemOverview(@Param("from") LocalDate from);

    /**
     * 情绪趋势。
     *
     * <p>当天没有日记时 avg 保持 NULL（只有 record_count 补 0）：echarts 默认
     * {@code connectNulls=false}，NULL 会画成断点，而补 0 会把折线拽到坐标轴底部——
     * 存量数据 406 天里只有 17 天有记录，补 0 出来的锯齿图看不出任何趋势。
     */
    @Select("""
            SELECT d.day::date AS date,
                   round(avg(e.mood_score)::numeric, 1) AS avg_mood_score,
                   count(e.id) AS record_count
              FROM generate_series(#{from}, #{to}, INTERVAL '1 day') AS d(day)
              LEFT JOIN emotion_diary e ON e.diary_date = d.day::date
             GROUP BY d.day::date
             ORDER BY d.day::date
            """)
    List<AnalyticsOverviewDTO.EmotionTrendItem> selectEmotionTrend(@Param("from") LocalDate from,
                                                                  @Param("to") LocalDate to);

    /**
     * 咨询会话的总量与平均时长。
     *
     * <p>会话表没有结束时间，只能把"最后一条消息的时间 − 开始时间"当作时长。
     * 无任何消息的会话在子查询里是 NULL，被 avg 自动跳过。
     */
    @Select("""
            SELECT count(*) AS total_sessions,
                   coalesce(round(avg(t.dur_minutes)::numeric, 1), 0) AS avg_duration_minutes
              FROM (
                    SELECT EXTRACT(EPOCH FROM (max(m.created_at) - s.started_at)) / 60.0 AS dur_minutes
                      FROM consultation_session s
                      LEFT JOIN consultation_message m ON m.session_id = s.id
                     GROUP BY s.id, s.started_at
                   ) t
            """)
    AnalyticsOverviewDTO.ConsultationStats selectConsultationAggregate();

    /** 咨询会话的每日趋势 */
    @Select("""
            SELECT d.day::date AS date,
                   count(s.id) AS session_count,
                   count(DISTINCT s.user_id) AS user_count
              FROM generate_series(#{from}, #{to}, INTERVAL '1 day') AS d(day)
              LEFT JOIN consultation_session s ON s.started_at::date = d.day::date
             GROUP BY d.day::date
             ORDER BY d.day::date
            """)
    List<AnalyticsOverviewDTO.ConsultationDailyTrendItem> selectConsultationDailyTrend(
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 用户活跃度。
     *
     * <p>四个指标都是标量子查询，外层不能加 GROUP BY——加了之后 PostgreSQL 会拒绝
     * 在子查询里引用 {@code d.day}（"子查询使用了外层查询中的非分组列"）。
     * generate_series 本身保证每天一行，不需要再分组。
     *
     * <p>activeUsers 取当天写日记与发起咨询的并集去重，不是两者相加，
     * 否则同一用户既写日记又咨询会被计两次。
     */
    @Select("""
            SELECT d.day::date AS date,
                   (SELECT count(DISTINCT a.user_id) FROM (
                        SELECT user_id FROM emotion_diary WHERE diary_date = d.day::date
                        UNION
                        SELECT user_id FROM consultation_session WHERE started_at::date = d.day::date
                    ) a) AS active_users,
                   (SELECT count(*) FROM "user" WHERE created_at::date = d.day::date) AS new_users,
                   (SELECT count(DISTINCT user_id) FROM emotion_diary WHERE diary_date = d.day::date) AS diary_users,
                   (SELECT count(DISTINCT user_id) FROM consultation_session WHERE started_at::date = d.day::date) AS consultation_users
              FROM generate_series(#{from}, #{to}, INTERVAL '1 day') AS d(day)
             ORDER BY d.day::date
            """)
    List<AnalyticsOverviewDTO.UserActivityItem> selectUserActivity(@Param("from") LocalDate from,
                                                                   @Param("to") LocalDate to);
}
