package com.example.aispringboot.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 用户自配 API Key 的 AES-GCM 加解密。
 *
 * <p>主密钥不走仓库：环境变量 {@code AI_CONFIG_SECRET} 经 SHA-256 派生为 256 位 AES Key。
 * 每次加密用随机 96 位 IV，存储格式为 {@code Base64(iv):Base64(密文+GCM标签)}。
 *
 * <p>未配置该环境变量时组件处于「未就绪」状态：读取/删除仍可用（历史密文仍在），
 * 但保存新 Key 会明确报错，绝不降级为明文存储。
 */
@Component
public class AiKeyCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String SEPARATOR = ":";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AiKeyCipher(@Value("${app.ai.config-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            this.key = null;
            return;
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("AI_CONFIG_SECRET 初始化失败", e);
        }
    }

    /** 是否可以加密保存新 Key */
    public boolean isReady() {
        return key != null;
    }

    public String encrypt(String plain) {
        requireReady();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + SEPARATOR
                    + Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AI_CONFIG_ERROR, "API Key 加密失败");
        }
    }

    public String decrypt(String stored) {
        requireReady();
        int sep = stored.indexOf(SEPARATOR);
        if (sep <= 0) {
            throw new BusinessException(ResultCode.AI_CONFIG_DECRYPT_FAILED);
        }
        try {
            byte[] iv = Base64.getDecoder().decode(stored.substring(0, sep));
            byte[] cipherText = Base64.getDecoder().decode(stored.substring(sep + 1));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 密文被篡改或主密钥已变更，GCM 校验会直接失败
            throw new BusinessException(ResultCode.AI_CONFIG_DECRYPT_FAILED);
        }
    }

    private void requireReady() {
        if (key == null) {
            throw new BusinessException(ResultCode.AI_CONFIG_SECRET_MISSING);
        }
    }
}
