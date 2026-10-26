package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户自配大模型 API 配置，一人一条（user_id 唯一索引）。
 *
 * <p>{@code apiKeyCipher} 是 AES-GCM 加密后的密文（Base64(iv):Base64(密文)），
 * 主密钥来自环境变量 {@code AI_CONFIG_SECRET}，任何接口都不回传完整 Key。
 * {@code baseUrl} 与 {@code model} 可空，为空表示沿用服务端默认配置。
 */
@Data
@TableName("user_ai_config")
public class UserAiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String apiKeyCipher;

    private String baseUrl;

    private String model;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
