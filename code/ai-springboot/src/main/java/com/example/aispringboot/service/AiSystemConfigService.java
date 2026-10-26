package com.example.aispringboot.service;

import com.example.aispringboot.DTO.command.AiConfigSaveCommandDTO;
import com.example.aispringboot.DTO.response.AiSystemConfigResponseDTO;
import com.example.aispringboot.ai.AiCallOptions;
import com.example.aispringboot.ai.AiEndpointTester;
import com.example.aispringboot.common.AiKeyCipher;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.RateLimiter;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.entity.AiSystemConfig;
import com.example.aispringboot.mapper.AiSystemConfigMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 管理员维护的全局大模型配置。
 *
 * <p>职责：全局 Key 的加密存取、掩码展示、连通性校验，以及给对话链路兜底解析运行时参数。
 * 保存后无需重启即对全部用户生效（用户自配 Key 仍优先）；任何接口都不回传完整 Key。
 *
 * <p>安全边界与 {@link UserAiConfigService} 一致：{@code AI_CONFIG_SECRET} 未就绪时拒绝保存，
 * baseUrl 禁止指向本机/内网（防 SSRF），保存与校验做全局频控。
 */
@Slf4j
@Service
public class AiSystemConfigService {

    /** 单行配置的固定主键 */
    private static final short SINGLETON_ID = 1;

    /** 管理员保存/校验的全局频控：每分钟 10 次 */
    private static final int RATE_LIMIT = 10;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    @Resource
    private AiSystemConfigMapper configMapper;

    @Resource
    private AiKeyCipher cipher;

    @Resource
    private RateLimiter rateLimiter;

    // ---------------------------------------------------------------- 查询

    public AiSystemConfigResponseDTO getConfig() {
        AiSystemConfigResponseDTO view = new AiSystemConfigResponseDTO();
        AiSystemConfig config = selectSingleton();
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

    /** 是否已配置全局 Key（不解密，供演示模式判断与对话链路快速分支） */
    public boolean hasConfig() {
        return selectSingleton() != null;
    }

    // ---------------------------------------------------------------- 保存

    public AiSystemConfigResponseDTO saveConfig(Long operatorId, AiConfigSaveCommandDTO command) {
        requireRate("ai-admin-config-save");
        String apiKey = command.getApiKey() == null ? "" : command.getApiKey().trim();
        String baseUrl = UserAiConfigService.trimToNull(command.getBaseUrl());
        String model = UserAiConfigService.trimToNull(command.getModel());
        UserAiConfigService.validateBaseUrl(baseUrl);

        LocalDateTime now = LocalDateTime.now();
        AiSystemConfig config = selectSingleton();
        if (config == null) {
            config = new AiSystemConfig();
            config.setId(SINGLETON_ID);
            config.setApiKeyCipher(cipher.encrypt(apiKey));
            config.setBaseUrl(baseUrl);
            config.setModel(model);
            config.setUpdatedBy(operatorId);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            configMapper.insert(config);
        } else {
            config.setApiKeyCipher(cipher.encrypt(apiKey));
            config.setBaseUrl(baseUrl);
            config.setModel(model);
            config.setUpdatedBy(operatorId);
            config.setUpdatedAt(now);
            configMapper.updateById(config);
        }
        log.info("管理员 {} 更新了全局 AI 配置（model={}，baseUrl={}）", operatorId,
                model == null ? "默认" : model, baseUrl == null ? "默认" : baseUrl);
        return getConfig();
    }

    /** 清空全局配置，对话链路随之回到演示模式（未配置用户级 Key 的用户） */
    public void deleteConfig() {
        configMapper.deleteById(SINGLETON_ID);
    }

    // ---------------------------------------------------------------- 连通性校验

    /**
     * 用请求体里的 Key 做连通性校验，只校验不落库。
     *
     * @return 校验通过时实际命中的模型名
     */
    public String testConfig(AiConfigSaveCommandDTO command) {
        requireRate("ai-admin-config-test");
        String apiKey = command.getApiKey() == null ? "" : command.getApiKey().trim();
        String baseUrl = UserAiConfigService.trimToNull(command.getBaseUrl());
        String model = UserAiConfigService.trimToNull(command.getModel());
        UserAiConfigService.validateBaseUrl(baseUrl);
        return AiEndpointTester.probe(apiKey, baseUrl, model);
    }

    // ---------------------------------------------------------------- 对话链路

    /**
     * 解析全局运行时参数；未配置返回 null，调用方继续走服务端默认链路。
     *
     * <p>密钥解密失败会抛 {@link ResultCode#AI_CONFIG_DECRYPT_FAILED}，
     * 由流式对话转成错误帧提示管理员重新保存。
     */
    public AiCallOptions resolveOptions() {
        AiSystemConfig config = selectSingleton();
        if (config == null) {
            return null;
        }
        return new AiCallOptions(cipher.decrypt(config.getApiKeyCipher()),
                config.getBaseUrl(), config.getModel());
    }

    // ---------------------------------------------------------------- 内部工具

    private AiSystemConfig selectSingleton() {
        return configMapper.selectById(SINGLETON_ID);
    }

    /** 读取掩码时解密失败不阻塞查看，只提示已配置但密钥不可读 */
    private String decryptQuietly(AiSystemConfig config) {
        try {
            return cipher.decrypt(config.getApiKeyCipher());
        } catch (BusinessException e) {
            return "不可读";
        }
    }

    /** 与用户级配置一致的掩码规则：保留前 3 后 4，中间打码 */
    static String maskKey(String plain) {
        return UserAiConfigService.maskKey(plain);
    }

    private void requireRate(String key) {
        if (!rateLimiter.tryAcquire(key, RATE_LIMIT, RATE_WINDOW)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "操作过于频繁，请稍后再试");
        }
    }
}
