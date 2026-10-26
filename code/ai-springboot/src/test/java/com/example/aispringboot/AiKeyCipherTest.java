package com.example.aispringboot;

import com.example.aispringboot.common.AiKeyCipher;
import com.example.aispringboot.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自配 API Key 加密的纯单元测试，不依赖 Spring 上下文与数据库。
 */
public class AiKeyCipherTest {

    @Test
    public void 加解密往返还原原文() {
        AiKeyCipher cipher = new AiKeyCipher("unit-test-secret");
        String plain = "sk-abcdef1234567890";
        String stored = cipher.encrypt(plain);
        assertNotEquals(plain, stored, "密文不能等于明文");
        assertEquals(plain, cipher.decrypt(stored));
    }

    @Test
    public void 每次加密使用随机IV_密文不同() {
        AiKeyCipher cipher = new AiKeyCipher("unit-test-secret");
        String plain = "sk-same-plain-text";
        assertNotEquals(cipher.encrypt(plain), cipher.encrypt(plain), "随机 IV 应使密文每次不同");
    }

    @Test
    public void 主密钥不匹配时解密失败() {
        AiKeyCipher cipherA = new AiKeyCipher("secret-a");
        AiKeyCipher cipherB = new AiKeyCipher("secret-b");
        String stored = cipherA.encrypt("sk-key-of-a");
        BusinessException e = assertThrows(BusinessException.class, () -> cipherB.decrypt(stored));
        assertEquals("6052", e.getCode(), "应抛 AI_CONFIG_DECRYPT_FAILED");
    }

    @Test
    public void 篡改密文后解密失败() {
        AiKeyCipher cipher = new AiKeyCipher("unit-test-secret");
        String stored = cipher.encrypt("sk-abcdef1234567890");
        String tampered = stored.substring(0, stored.length() - 2) + "AA";
        assertThrows(BusinessException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    public void 未配置主密钥时拒绝加密() {
        AiKeyCipher cipher = new AiKeyCipher("");
        assertFalse(cipher.isReady());
        BusinessException e = assertThrows(BusinessException.class, () -> cipher.encrypt("sk-any"));
        assertEquals("6051", e.getCode(), "应抛 AI_CONFIG_SECRET_MISSING");
        assertTrue(e.getMessage().contains("AI_CONFIG_SECRET"));
    }
}
