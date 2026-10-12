package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文章新增/编辑入参。
 *
 * <p>前端 ArticleDialog 提交时把 tagArray 合并成逗号分隔的 tags 字符串；
 * 新增时还会带上上传封面时生成的 UUID 作为 id——该值可能为 null（未上传封面），
 * 为空时由后端生成。
 */
@Data
public class ArticleCommandDTO {

    @Size(max = 36, message = "文章ID长度不能超过36个字符")
    private String id;

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 200, message = "文章标题最多200个字符")
    private String title;

    @NotBlank(message = "文章内容不能为空")
    private String content;

    @NotNull(message = "请选择分类")
    private Long categoryId;

    @Size(max = 500, message = "封面地址长度不能超过500个字符")
    private String coverImage;

    private String summary;

    @Size(max = 500, message = "标签长度不能超过500个字符")
    private String tags;

    /** 0 草稿 / 1 已发布 / 2 已下线。不传时按草稿处理 */
    private Integer status;
}
