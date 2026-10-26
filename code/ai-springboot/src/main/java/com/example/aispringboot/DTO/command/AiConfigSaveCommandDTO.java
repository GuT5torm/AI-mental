package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存 / 校验用户自配 AI Key 的请求体。
 *
 * <p>{@code baseUrl} 与 {@code model} 可空，为空表示沿用服务端默认。
 * 服务端会把 base_url 拿去发起真实调用，因此除长度外还必须通过
 * {@code UserAiConfigService#validateBaseUrl} 的协议与内网地址校验（防 SSRF）。
 */
@Data
public class AiConfigSaveCommandDTO {

    @NotBlank(message = "apiKey不能为空")
    @Size(max = 200, message = "apiKey长度超出限制")
    private String apiKey;

    @Size(max = 255, message = "baseUrl长度超出限制")
    private String baseUrl;

    @Size(max = 100, message = "model长度超出限制")
    private String model;
}
