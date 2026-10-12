package com.example.aispringboot.DTO.command;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章分页查询条件。后台与前台共用同一个接口，筛选字段都是可选的。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticlePageQuery extends PageQuery {

    /** 标题模糊匹配，命中 idx_ka_title */
    private String title;

    private Long categoryId;

    /** 0 草稿 / 1 已发布 / 2 已下线；仅后台传入，前台不传 */
    private Integer status;
}
