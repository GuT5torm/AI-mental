package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 文章响应。
 *
 * <p>几个字段说明：
 * <ul>
 *   <li>{@code categoryName}：库中没有该列，由 category_id 反查知识分类得到。</li>
 *   <li>{@code authorName}：库中没有该列（只有 author_id），由 user 表反查昵称，
 *       取不到时回落到用户名。</li>
 *   <li>{@code tagArray}：tags 是逗号分隔的字符串，前端详情标签云需要数组。</li>
 *   <li>{@code updateAt}：后台列表页 knowledge.vue 第 28 行写的是 updateAt，
 *       而其余页面用的是 updatedAt。按决策 D2 的同一思路，由后端补一个同值字段，
 *       避免为了一个列名去改前端。</li>
 * </ul>
 */
@Data
public class ArticleResponseDTO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String id;
    private String title;
    private String summary;
    /** 后台编辑需要原文；前台详情也需要。列表不返回以减小响应体 */
    private String content;
    private String coverImage;
    private String tags;
    private List<String> tagArray;
    private Long categoryId;
    private String categoryName;
    private Long authorId;
    private String authorName;
    private Integer readCount;
    private Integer status;
    private String statusDisplayName;
    private String publishedAt;
    private String createdAt;
    private String updatedAt;

    /** 见类注释：后台列表页用的字段名是 updateAt */
    public String getUpdateAt() {
        return updatedAt;
    }

    public void setTimeFields(LocalDateTime published, LocalDateTime created, LocalDateTime updated) {
        this.publishedAt = format(published);
        this.createdAt = format(created);
        this.updatedAt = format(updated);
    }

    private static String format(LocalDateTime t) {
        return t == null ? null : FMT.format(t);
    }
}
