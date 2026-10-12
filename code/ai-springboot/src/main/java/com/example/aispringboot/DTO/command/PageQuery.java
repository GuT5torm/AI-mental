package com.example.aispringboot.DTO.command;

import lombok.Data;

/**
 * 分页查询基类。
 *
 * <p>前端五个页面的分页参数命名不一致（见方案 3.5）：页码有 {@code pageNum} 与
 * {@code currentPage} 两种，每页条数有 {@code pageSize} 与 {@code size} 两种。
 * 按决策 D2，由后端双字段兼容吸收这个差异，不去改五个页面。
 *
 * <p>排序字段同样不统一，且直接拼进 SQL 有注入风险，因此用白名单映射，
 * 未登记或方向非法的排序参数一律忽略。
 */
@Data
public class PageQuery {

    private Integer pageNum;
    private Integer currentPage;
    private Integer pageSize;
    private Integer size;

    private String sortField;
    private String sortDirection;

    public long getCurrent() {
        Integer v = firstNonNull(pageNum, currentPage);
        return v == null || v < 1 ? 1L : v;
    }

    public long getSizeValue() {
        Integer v = firstNonNull(pageSize, size);
        return v == null || v < 1 ? 10L : v;
    }

    /**
     * 白名单内的排序字段映射为真实列名；不在白名单返回 null。
     */
    public String resolveSortColumn() {
        if (sortField == null || sortField.isBlank()) {
            return null;
        }
        return switch (sortField.trim()) {
            case "publishedAt", "published_at" -> "published_at";
            case "readCount", "read_count" -> "read_count";
            case "createdAt", "created_at" -> "created_at";
            case "updatedAt", "updated_at" -> "updated_at";
            case "title" -> "title";
            case "sortOrder", "sort_order" -> "sort_order";
            default -> null;
        };
    }

    public boolean isAsc() {
        return "asc".equalsIgnoreCase(sortDirection);
    }

    private static Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }
}
