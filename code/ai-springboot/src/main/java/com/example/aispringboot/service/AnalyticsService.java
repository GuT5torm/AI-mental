package com.example.aispringboot.service;

import com.example.aispringboot.DTO.response.AnalyticsOverviewDTO;
import com.example.aispringboot.common.AuthUtil;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.mapper.AnalyticsMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 数据分析看板。
 *
 * <p>只有管理员能看：这三个接口的调用方都在后台 {@code gutts.js} 里，
 * 数据覆盖全库用户的情绪记录，放行给普通用户等于把别人的日记统计出去。
 */
@Slf4j
@Service
public class AnalyticsService {

    /** 显式传 days 时的上限，防止一次生成几万个日期点 */
    public static final int MAX_DAYS = 1825;

    @Resource
    private AnalyticsMapper analyticsMapper;

    /**
     * 组装看板数据。
     *
     * @param days 统计天数，为 null 时取全量区间（见 {@link #resolveFrom}）
     */
    public AnalyticsOverviewDTO overview(Integer days) {
        AuthUtil.requireAdmin();

        LocalDate to = LocalDate.now();
        LocalDate from = resolveFrom(days);

        AnalyticsOverviewDTO dto = new AnalyticsOverviewDTO();
        dto.setSystemOverview(analyticsMapper.selectSystemOverview(from));
        dto.setEmotionTrend(analyticsMapper.selectEmotionTrend(from, to));

        AnalyticsOverviewDTO.ConsultationStats stats = analyticsMapper.selectConsultationAggregate();
        stats.setDailyTrend(analyticsMapper.selectConsultationDailyTrend(from, to));
        dto.setConsultationStats(stats);

        dto.setUserActivity(analyticsMapper.selectUserActivity(from, to));
        return dto;
    }

    /**
     * 区间起点。
     *
     * <p>不传 days 时不是取"最近 30 天"，而是从全库最早的一天起算。
     * 存量数据集中在 2025 年 9—11 月，若按 30 天窗口，四张卡片里的
     * 今日新增之外全是 0，三条趋势线也全平，看板看上去像没接上数据。
     * 前端 {@code getAnalyticsOverview()} 不传参数，走的就是这个分支。
     *
     * <p>全量区间仍然有上限：库里若躺着多年前的记录，按天补零会生成
     * 成千上万个点，既拖慢接口也让趋势图挤成一团。
     */
    private LocalDate resolveFrom(Integer days) {
        if (days != null) {
            if (days < 1 || days > MAX_DAYS) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "统计天数需在 1 到 " + MAX_DAYS + " 之间");
            }
            return LocalDate.now().minusDays(days - 1);
        }

        LocalDate earliest = analyticsMapper.selectEarliestDate();
        if (earliest == null) {
            return LocalDate.now();
        }
        LocalDate floor = LocalDate.now().minusDays(MAX_DAYS - 1);
        return earliest.isBefore(floor) ? floor : earliest;
    }
}
