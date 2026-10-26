package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员全局 AI 配置的对外视图。只含掩码 Key，永远不返回完整密钥。
 */
@Data
public class AiSystemConfigResponseDTO {

    /** 是否已配置全局 Key */
    private boolean configured;

    /** 掩码形式，如 {@code sk-****abcd}；未配置时为 null */
    private String maskedKey;

    /** 自定义端点（不含敏感信息）；未配置或用默认时为 null */
    private String baseUrl;

    /** 自定义模型名；未配置或用默认时为 null */
    private String model;

    private LocalDateTime updatedAt;
}
