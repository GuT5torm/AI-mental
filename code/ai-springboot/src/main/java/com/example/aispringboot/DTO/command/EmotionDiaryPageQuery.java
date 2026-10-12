package com.example.aispringboot.DTO.command;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 后台情绪日记分页查询。
 *
 * <p>moodScoreRange 是前端 emotional.vue 的下拉筛选项，取值形如 {@code 1-3}、
 * {@code 4-6}、{@code 7-10}。后端不做字符串拼接，解析成闭区间后再走参数绑定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmotionDiaryPageQuery extends PageQuery {

    private Long userId;

    private String moodScoreRange;

    private LocalDate dateFrom;

    private LocalDate dateTo;

    /** 解析评分下限，格式非法或未传返回 null */
    public Integer getMoodMin() {
        return parseBound(moodScoreRange, true);
    }

    /** 解析评分上限，格式非法或未传返回 null */
    public Integer getMoodMax() {
        return parseBound(moodScoreRange, false);
    }

    private static Integer parseBound(String range, boolean lower) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String[] parts = range.trim().split("-");
        if (parts.length != 2) {
            return null;
        }
        try {
            return Integer.valueOf(parts[lower ? 0 : 1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
