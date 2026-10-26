package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员维护的全局大模型配置，全库仅一条（id 固定为 1）。
 *
 * <p>{@code apiKeyCipher} 是 AES-GCM 加密后的密文（Base64(iv):Base64(密文)），
 * 主密钥来自环境变量 {@code AI_CONFIG_SECRET}，任何接口都不回传完整 Key。
 * {@code baseUrl} 与 {@code model} 可空，为空表示沿用服务端默认配置。
 */
@Data
@TableName("ai_system_config")
public class AiSystemConfig {

    /** 单行配置，写入时固定为 1 */
    @TableId(type = IdType.INPUT)
    private Short id;

    private String apiKeyCipher;

    private String baseUrl;

    private String model;

    /** 最近一次保存的管理员用户ID */
    private Long updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
