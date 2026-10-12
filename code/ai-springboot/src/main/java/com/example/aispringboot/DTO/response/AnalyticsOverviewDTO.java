package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 数据分析看板的整体返回结构。
 *
 * <p>字段名与层级严格对齐 {@code dashboard.vue}：前端直接把响应塞进
 * {@code aiData} 后用 {@code systemOverview?.totalUsers} 之类的路径取值，
 * 任何一处改名都会让卡片显示成 0 而不报错。
 *
 * <p>数值字段用 {@link BigDecimal} 而不是 double：金额与评分都要保证
 * 序列化的小数位与库里 round 出来的结果一致，double 会多出 6.0999999 这类尾巴。
 */
@Data
public class AnalyticsOverviewDTO {

    private SystemOverview systemOverview;

    /** 按日期升序，空缺日期补零 */
    private List<EmotionTrendItem> emotionTrend;

    private ConsultationStats consultationStats;

    /** 按日期升序，空缺日期补零 */
    private List<UserActivityItem> userActivity;

    /** 顶部四张卡片 */
    @Data
    public static class SystemOverview {
        /** 注册用户总数 */
        private Long totalUsers;
        /** 统计区间内有日记或咨询行为的去重用户数 */
        private Long activeUsers;
        private Long totalDiaries;
        private Long todayNewDiaries;
        private Long totalSession;
        private Long todayNewSession;
        /** 全部日记的平均情绪评分，1 位小数 */
        private BigDecimal avgMoodScore;
    }

    /** 情绪趋势图的一个点 */
    @Data
    public static class EmotionTrendItem {
        private LocalDate date;
        /**
         * 当天平均情绪评分，1 位小数。
         *
         * <p>当天没有日记时为 null，让前端图表画成断点而不是 0——
         * 评分的"没有记录"与"评分为 0"不是一回事，而评分取值是 1–10，0 本身也不合法。
         */
        private BigDecimal avgMoodScore;
        /** 当天日记条数，无记录时为 0 */
        private Long recordCount;
    }

    @Data
    public static class ConsultationStats {
        private Long totalSessions;
        /**
         * 平均会话时长，单位分钟，1 位小数。
         *
         * <p>库里 consultation_session 只有 started_at，没有结束时间，
         * 这里用该会话最后一条消息的时间减开始时间作为时长。
         * 一条消息都没有的会话计不进平均值。
         */
        private BigDecimal avgDurationMinutes;
        /** 按日期升序，空缺日期补零 */
        private List<ConsultationDailyTrendItem> dailyTrend;
    }

    @Data
    public static class ConsultationDailyTrendItem {
        private LocalDate date;
        private Long sessionCount;
        private Long userCount;
    }

    /** 用户活跃度趋势图的一个点 */
    @Data
    public static class UserActivityItem {
        private LocalDate date;
        /** 当天有日记或咨询行为的去重用户数 */
        private Long activeUsers;
        /** 当天注册的用户数 */
        private Long newUsers;
        /** 当天写过日记的去重用户数 */
        private Long diaryUsers;
        /** 当天发起过咨询的去重用户数 */
        private Long consultationUsers;
    }
}
