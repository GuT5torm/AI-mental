package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章状态变更。前端只传 status：1 发布、2 下线。
 */
@Data
public class ArticleStatusCommandDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
