package com.example.aispringboot.ai;

import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大模型端点连通性探测：用待验证的 Key 发一条 max_tokens=1 的真实请求。
 *
 * <p>用户自配配置与管理员全局配置共用本探测，只做校验不落库。
 * 任何非 200 响应都转成 {@link ResultCode#AI_CONFIG_TEST_FAILED} 的业务异常，
 * 日志不打印 Key 明文。
 */
public final class AiEndpointTester {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /** 连通性校验只发一条 max_tokens=1 的短请求，超时也相应收紧 */
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiEndpointTester() {
    }

    /**
     * 探测端点连通性。
     *
     * @param apiKey  完整 Key 明文（仅用于本次请求头，不落日志）
     * @param baseUrl OpenAI 兼容端点，空则用 DeepSeek 默认
     * @param model   模型名，空则用默认
     * @return 校验通过时实际命中的模型名
     */
    public static String probe(String apiKey, String baseUrl, String model) {
        String effectiveBase = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        String effectiveModel = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", effectiveModel);
        payload.put("messages", new Object[]{Map.of("role", "user", "content", "ping")});
        payload.put("stream", false);
        payload.put("max_tokens", 1);

        HttpResponse<String> resp;
        try {
            resp = HTTP_CLIENT.send(HttpRequest.newBuilder()
                            .uri(URI.create(effectiveBase + "/chat/completions"))
                            .timeout(PING_TIMEOUT)
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AI_CONFIG_TEST_FAILED,
                    "无法连接 " + effectiveBase + "，请检查地址与网络");
        }
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            throw new BusinessException(ResultCode.AI_CONFIG_TEST_FAILED, "Key 无效或未授权");
        }
        if (resp.statusCode() == 404) {
            throw new BusinessException(ResultCode.AI_CONFIG_TEST_FAILED,
                    "端点不存在，请检查 baseUrl 是否为 OpenAI 兼容地址");
        }
        if (resp.statusCode() != 200) {
            throw new BusinessException(ResultCode.AI_CONFIG_TEST_FAILED,
                    "服务端返回 HTTP " + resp.statusCode());
        }
        return effectiveModel;
    }
}
