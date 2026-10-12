package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文章。
 *
 * <p>主键为 varchar(36) 的 UUID 字符串，不是自增列，需应用层调用
 * {@code UUID.randomUUID().toString()} 生成，故使用 {@code IdType.INPUT}。
 */
@Data
@TableName("knowledge_article")
public class KnowledgeArticle {

    @TableId(type = IdType.INPUT)
    private String id;

    private Long categoryId;

    private String title;

    private String summary;

    private String content;

    private String coverImage;

    private String tags;

    private Long authorId;

    private Integer readCount;

    private Integer status;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
