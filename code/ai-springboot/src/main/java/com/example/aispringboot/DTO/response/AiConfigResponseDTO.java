package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户自配 AI 配置的对外视图。只含掩码 Key，永远不返回完整密钥。
 *
 * <p>{@code active} 表示服务端当前 provider 是否为真实模型（deepseek）：
 * 为 false 时保存的 Key 不会参与对话（服务端仍用 mock 跑通链路），
 * 前端据此提示用户「Key 已保存，待服务端切换到真实模型后生效」。
 */
@Data
public class AiConfigResponseDTO {

    /** 是否已配置自研 Key */
    private boolean configured;

    /** 掩码形式，如 {@code sk-****abcd}；未配置时为 null */
    private String maskedKey;

    /** 自定义端点（掩码后原样返回，不含敏感信息）；未配置或用默认时为 null */
    private String baseUrl;

    /** 自定义模型名；未配置或用默认时为 null */
    private String model;

    private LocalDateTime updatedAt;

    /** 服务端 provider 是否为真实模型，即当前保存的 Key 是否参与对话 */
    private boolean active;
}
