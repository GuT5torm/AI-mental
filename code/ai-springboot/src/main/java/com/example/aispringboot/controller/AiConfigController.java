package com.example.aispringboot.controller;

import com.example.aispringboot.DTO.command.AiConfigSaveCommandDTO;
import com.example.aispringboot.DTO.response.AiConfigResponseDTO;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.common.UserContext;
import com.example.aispringboot.service.AiSystemConfigService;
import com.example.aispringboot.service.UserAiConfigService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户自配大模型 API Key。
 *
 * <p>所有接口都要求登录（不标 {@code @Anonymous}），且只操作当前登录人自己的配置。
 * GET/DELETE 无请求体；PUT 保存并加密落库；POST /test 用请求体里的 Key 发一次
 * 真实短请求做连通性校验，不落库。
 *
 * <p>GET /status 供前台演示模式提示使用：用户自配 Key 或管理员全局 Key 存在、
 * 且管理员未关闭该用户的真实模型开关时，处于真实模型模式，否则为演示模式。
 */
@RestController
@RequestMapping("/api/ai-config")
public class AiConfigController {

    @Resource
    private UserAiConfigService userAiConfigService;

    @Resource
    private AiSystemConfigService aiSystemConfigService;

    @GetMapping
    public Result<AiConfigResponseDTO> get() {
        return Result.ok(userAiConfigService.getConfig(UserContext.getUserId()));
    }

    /**
     * 登录用户即可查询的 AI 模式状态，用于前台演示模式提示条。
     *
     * <p>demoMode 为真的两种情形：任何 Key 都不存在；或管理员在后台关闭了
     * 该用户使用真实大模型的开关（user.ai_real_enabled = 0）。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Long userId = UserContext.getUserId();
        boolean userConfigured = userAiConfigService.hasConfig(userId);
        boolean systemConfigured = aiSystemConfigService.hasConfig();
        boolean realAllowed = userAiConfigService.isRealModelAllowed(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userConfigured", userConfigured);
        data.put("systemConfigured", systemConfigured);
        data.put("demoMode", realAllowed ? !userConfigured && !systemConfigured : true);
        return Result.ok(data);
    }

    @PutMapping
    public Result<AiConfigResponseDTO> save(@Valid @RequestBody AiConfigSaveCommandDTO command) {
        return Result.ok(userAiConfigService.saveConfig(UserContext.getUserId(), command));
    }

    @DeleteMapping
    public Result<Void> delete() {
        userAiConfigService.deleteConfig(UserContext.getUserId());
        return Result.ok(null);
    }

    @PostMapping("/test")
    public Result<Map<String, Object>> test(@Valid @RequestBody AiConfigSaveCommandDTO command) {
        String model = userAiConfigService.testConfig(UserContext.getUserId(), command);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", true);
        data.put("model", model);
        return Result.ok(data);
    }
}
