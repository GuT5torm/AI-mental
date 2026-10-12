package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收藏。articleId 为 varchar(36)，与 knowledge_article.id 对应。
 *
 * <p>前端目前没有收藏相关接口，本实体仅完成数据层映射，接口留待后续需求。
 */
@Data
@TableName("user_favorite")
public class UserFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String articleId;

    private LocalDateTime createdAt;

}
