package com.example.aispringboot.controller;

import com.example.aispringboot.DTO.command.AiConfigSaveCommandDTO;
import com.example.aispringboot.DTO.response.AiSystemConfigResponseDTO;
import com.example.aispringboot.common.AuthUtil;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.service.AiSystemConfigService;
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
 * 管理员维护的全局大模型配置。
 *
 * <p>所有接口都要求管理员身份；GET/DELETE 无请求体；PUT 保存并加密落库，保存后
 * 对话链路自动启用（用户自配 Key 仍优先），无需重启；POST /test 用请求体里的 Key
 * 发一次真实短请求做连通性校验，不落库。任何接口都不回传完整 Key。
 */
@RestController
@RequestMapping("/api/ai/admin-config")
public class AiAdminConfigController {

    @Resource
    private AiSystemConfigService aiSystemConfigService;

    @GetMapping
    public Result<AiSystemConfigResponseDTO> get() {
        AuthUtil.requireAdmin();
        return Result.ok(aiSystemConfigService.getConfig());
    }

    @PutMapping
    public Result<AiSystemConfigResponseDTO> save(@Valid @RequestBody AiConfigSaveCommandDTO command) {
        AuthUtil.requireAdmin();
        Long operatorId = AuthUtil.requireUserId();
        return Result.ok(aiSystemConfigService.saveConfig(operatorId, command));
    }

    @DeleteMapping
    public Result<Void> delete() {
        AuthUtil.requireAdmin();
        aiSystemConfigService.deleteConfig();
        return Result.ok(null);
    }

    @PostMapping("/test")
    public Result<Map<String, Object>> test(@Valid @RequestBody AiConfigSaveCommandDTO command) {
        AuthUtil.requireAdmin();
        String model = aiSystemConfigService.testConfig(command);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", true);
        data.put("model", model);
        return Result.ok(data);
    }
}
