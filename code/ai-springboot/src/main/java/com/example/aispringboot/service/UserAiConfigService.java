package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.DTO.command.AiConfigSaveCommandDTO;
import com.example.aispringboot.DTO.response.AiConfigResponseDTO;
import com.example.aispringboot.ai.AiCallOptions;
import com.example.aispringboot.ai.AiEndpointTester;
import com.example.aispringboot.common.AiKeyCipher;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.RateLimiter;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.entity.UserAiConfig;
import com.example.aispringboot.mapper.UserAiConfigMapper;
import com.example.aispringboot.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 用户自配大模型 API 配置。
 *
 * <p>职责：Key 的加密存取、掩码展示、连通性校验，以及给对话链路解析运行时参数。
 * 任何接口都不回传完整 Key；日志不打印 Key 明文。
 *
 * <p>安全边界：
 * <ul>
 *   <li>保存与校验都要求 {@code AI_CONFIG_SECRET} 就绪，不降级明文存储；</li>
 *   <li>{@code baseUrl} 仅允许 http/https 且禁止指向本机与内网地址（防 SSRF）；</li>
 *   <li>保存与校验接口按用户做进程内频控（复用登录限流器）。</li>
 * </ul>
 */
@Slf4j
@Service
public class UserAiConfigService {

    private static final String REAL_PROVIDER = "deepseek";

    /** 单用户保存/校验的频控：每分钟 10 次 */
    private static final int RATE_LIMIT = 10;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    @Resource
    private UserAiConfigMapper configMapper;

    @Resource
    private AiKeyCipher cipher;

    @Resource
    private RateLimiter rateLimiter;

    @Resource
    private AiSystemConfigService aiSystemConfigService;

    @Resource
    private UserMapper userMapper;

    @Value("${app.ai.provider:mock}")
    private String provider;

    // ---------------------------------------------------------------- 查询

    public AiConfigResponseDTO getConfig(Long userId) {
        AiConfigResponseDTO view = new AiConfigResponseDTO();
        view.setActive(REAL_PROVIDER.equalsIgnoreCase(provider));
        UserAiConfig config = selectByUserId(userId);
        if (config == null) {
            view.setConfigured(false);
            return view;
        }
        view.setConfigured(true);
        view.setMaskedKey(maskKey(decryptQuietly(config)));
        view.setBaseUrl(config.getBaseUrl());
        view.setModel(config.getModel());
        view.setUpdatedAt(config.getUpdatedAt());
        return view;
    }

    // ---------------------------------------------------------------- 保存

    public AiConfigResponseDTO saveConfig(Long userId, AiConfigSaveCommandDTO command) {
        requireRate("ai-config-save:" + userId);
        String apiKey = command.getApiKey() == null ? "" : command.getApiKey().trim();
        String baseUrl = trimToNull(command.getBaseUrl());
        String model = trimToNull(command.getModel());
        validateBaseUrl(baseUrl);

        UserAiConfig config = selectByUserId(userId);
        if (config == null) {
            config = new UserAiConfig();
            config.setUserId(userId);
            config.setApiKeyCipher(cipher.encrypt(apiKey));
            config.setBaseUrl(baseUrl);
            config.setModel(model);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(config);
        } else {
            config.setApiKeyCipher(cipher.encrypt(apiKey));
            config.setBaseUrl(baseUrl);
            config.setModel(model);
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(config);
        }
        log.info("用户 {} 更新了自配 AI 配置（model={}，baseUrl={}）", userId,
                model == null ? "默认" : model, baseUrl == null ? "默认" : baseUrl);
        return getConfig(userId);
    }

    public void deleteConfig(Long userId) {
        configMapper.delete(Wrappers.lambdaQuery(UserAiConfig.class)
                .eq(UserAiConfig::getUserId, userId));
    }

    // ---------------------------------------------------------------- 对话链路

    /**
     * 解析用户自配的运行时参数；未配置返回 null，调用方按原路径走服务端默认配置。
     *
     * <p>密钥解密失败会抛 {@link ResultCode#AI_CONFIG_DECRYPT_FAILED}，
     * 由流式对话转成错误帧提示用户重新保存。
     */
    public AiCallOptions resolveOptions(Long userId) {
        UserAiConfig config = selectByUserId(userId);
        if (config == null) {
            return null;
        }
        return new AiCallOptions(cipher.decrypt(config.getApiKeyCipher()),
                config.getBaseUrl(), config.getModel());
    }

    /** 用户是否已配置自配 Key（不解密，供演示模式判断） */
    public boolean hasConfig(Long userId) {
        return selectByUserId(userId) != null;
    }

    /**
     * 对话链路的统一入参解析：用户自配 Key 优先，其次管理员配置的全局 Key，都没有返回 null。
     *
     * <p>管理员可在后台按用户关闭真实模型（user.ai_real_enabled = 0），
     * 此时无论 Key 是否存在都返回 null，强制该用户走演示模式。
     */
    public AiCallOptions resolveOptionsWithFallback(Long userId) {
        if (!isRealModelAllowed(userId)) {
            return null;
        }
        AiCallOptions options = resolveOptions(userId);
        if (options != null) {
            return options;
        }
        return aiSystemConfigService.resolveOptions();
    }

    /**
     * 该用户是否允许使用真实大模型（管理员在后台用户管理中维护的开关）。
     * 用户不存在或开关缺失时按允许处理，保证存量行为不变。
     */
    public boolean isRealModelAllowed(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null || user.getAiRealEnabled() == null || user.getAiRealEnabled() == 1;
    }

    // ---------------------------------------------------------------- 连通性校验

    /**
     * 用请求体里的 Key 发一条 max_tokens=1 的真实请求，验证 Key/端点/模型是否可用。
     * 只做校验，不落库。
     *
     * @return 校验通过时实际命中的模型名
     */
    public String testConfig(Long userId, AiConfigSaveCommandDTO command) {
        requireRate("ai-config-test:" + userId);
        String apiKey = command.getApiKey() == null ? "" : command.getApiKey().trim();
        String baseUrl = trimToNull(command.getBaseUrl());
        String model = trimToNull(command.getModel());
        validateBaseUrl(baseUrl);
        try {
            return AiEndpointTester.probe(apiKey, baseUrl, model);
        } catch (BusinessException e) {
            log.info("用户 {} 校验自配 Key 未通过：{}", userId, e.getMessage());
            throw e;
        }
    }

    // ---------------------------------------------------------------- 内部工具

    private UserAiConfig selectByUserId(Long userId) {
        return configMapper.selectOne(Wrappers.lambdaQuery(UserAiConfig.class)
                .eq(UserAiConfig::getUserId, userId));
    }

    /** 读取掩码时解密失败不阻塞查看，只提示已配置但密钥不可读 */
    private String decryptQuietly(UserAiConfig config) {
        try {
            return cipher.decrypt(config.getApiKeyCipher());
        } catch (BusinessException e) {
            return "不可读";
        }
    }

    /** 掩码规则：保留前 3 后 4，中间打码；过短直接全打码 */
    static String maskKey(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        if (plain.length() <= 8) {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }

    private void requireRate(String key) {
        if (!rateLimiter.tryAcquire(key, RATE_LIMIT, RATE_WINDOW)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "操作过于频繁，请稍后再试");
        }
    }

    static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * baseUrl 安全校验：仅允许 http/https，且主机不能是本机/内网/链路本地地址。
     * 只做字面量判断，不做 DNS 解析，保证离线环境下保存配置不受影响。
     */
    static void validateBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return;
        }
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "baseUrl 格式不正确");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "baseUrl 必须以 http:// 或 https:// 开头");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "baseUrl 缺少主机名");
        }
        if (isPrivateHost(host)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "baseUrl 不允许指向本机或内网地址");
        }
    }

    static boolean isPrivateHost(String rawHost) {
        String host = rawHost.toLowerCase();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        // IPv6 唯一本地地址（fc00::/7）与链路本地（fe80::/10）
        if (host.contains(":")) {
            return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe8")
                    || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb");
        }
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            return a == 10 || a == 127 || a == 0
                    || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168)
                    || (a == 169 && b == 254);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
