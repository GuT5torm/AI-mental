package com.example.aispringboot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 对接 DeepSeek（OpenAI 兼容 {@code /chat/completions}）的真实大模型实现。
 *
 * <p>当 {@code app.ai.provider=deepseek} 时激活；未设置时仍使用
 * {@link MockAiChatClient}，因此默认配置下所有依赖确定性输出的测试不受影响。
 *
 * <p>密钥与端点全部走环境变量，不写入仓库：
 * <ul>
 *   <li>{@code AI_API_KEY} —— 必填，无此值则该实现给出提示并降级；</li>
 *   <li>{@code AI_BASE_URL} —— 默认 {@code https://api.deepseek.com}；</li>
 *   <li>{@code AI_MODEL} —— 默认 {@code deepseek-chat}；</li>
 *   <li>{@code AI_TEMPERATURE} —— 默认 {@code 0.8}；</li>
 *   <li>{@code AI_MAX_TOKENS} —— 默认 {@code 2048}；</li>
 *   <li>{@code AI_TIMEOUT_SECONDS} —— 默认 {@code 60}，只约束响应头到达前的等待。</li>
 * </ul>
 * 由于协议是 OpenAI 兼容的，把 base-url 与 key 换成 OpenAI / Qwen / Moonshot 等
 * 兼容服务同样可用，业务代码无需改动。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "deepseek")
public class DeepSeekAiChatClient implements AiChatClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${app.ai.model:deepseek-chat}")
    private String model;

    @Value("${app.ai.temperature:0.8}")
    private double temperature;

    @Value("${app.ai.max-tokens:2048}")
    private int maxTokens;

    /** 只覆盖「响应头到达前」的等待；SSE 首字节到达后该超时即失效，不会误杀分块间隔 */
    @Value("${app.ai.timeout-seconds:60}")
    private long timeoutSeconds;

    @Resource
    private ObjectMapper objectMapper;

    /** 情绪分析 prompt：要求模型只输出约定字段的 JSON，避免夹带多余文本导致解析失败 */
    private static final String EMOTION_INSTRUCTION =
            "你是心理情感分析助手。根据对话内容，仅输出一个 JSON 对象（不要用 markdown 代码块），"
                    + "字段必须包含：icon,label,keywords(array),riskLevel(0-2),timestamp(毫秒),"
                    + "isNegative(boolean),suggestion,emotionScore(0-100),primaryEmotion,"
                    + "riskDescription,improvementSuggestions(array)。"
                    + "若用户表达负面情绪，isNegative 应为 true。";

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public void streamChat(List<ChatMessage> history, String userMessage, Consumer<String> chunkConsumer) {
        if (chunkConsumer == null) {
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            chunkConsumer.accept("尚未配置 AI_API_KEY，无法连接大模型。请在环境变量中设置后重启。");
            return;
        }
        List<Map<String, String>> messages = new ArrayList<>();
        appendHistory(messages, history);
        if (userMessage != null && !userMessage.isBlank()) {
            messages.add(message("user", userMessage));
        }
        try {
            HttpResponse<InputStream> resp = postStream(chatCompletions(messages, true));
            if (resp.statusCode() != 200) {
                String errBody = readQuietly(resp.body());
                log.warn("大模型流式接口返回 {}, body={}", resp.statusCode(), errBody);
                chunkConsumer.accept("大模型请求失败，请检查 AI_API_KEY 与网络后重试。");
                return;
            }
            // 逐行消费 SSE：响应头到达后 request timeout 即失效，
            // 读流期间不会被超时误杀，分块实时推给前端
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) {
                        break;
                    }
                    dispatchDelta(data, chunkConsumer);
                }
            }
        } catch (Exception e) {
            log.error("大模型流式请求失败", e);
            chunkConsumer.accept("大模型连接失败，请稍后重试。");
        }
    }

    /** 解析单个 SSE data 分片并推送增量；无法解析的分片直接跳过，不影响整体流式输出 */
    private void dispatchDelta(String data, Consumer<String> chunkConsumer) {
        try {
            JsonNode node = objectMapper.readTree(data);
            String delta = node.path("choices").path(0).path("delta").path("content").asText("");
            if (!delta.isEmpty()) {
                chunkConsumer.accept(delta);
            }
        } catch (Exception ignore) {
            // 无法解析的分片直接跳过，不影响整体流式输出
        }
    }

    private static String readQuietly(InputStream body) {
        try (InputStream is = body) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public Map<String, Object> analyzeEmotion(List<ChatMessage> recentMessages) {
        Map<String, Object> fallback = fallbackAnalysis();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI_API_KEY 未配置，情绪分析回退到内置默认方案");
            return fallback;
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", EMOTION_INSTRUCTION));
        appendHistory(messages, recentMessages);
        try {
            HttpResponse<String> resp = postForJson(chatCompletions(messages, false));
            if (resp.statusCode() != 200) {
                log.warn("情绪分析接口返回 {}", resp.statusCode());
                return fallback;
            }
            String content = objectMapper.readTree(resp.body())
                    .path("choices").path(0).path("message").path("content").asText(null);
            if (content == null) {
                return fallback;
            }
            JsonNode json = objectMapper.readTree(stripCodeFence(content));
            if (!json.isObject()) {
                return fallback;
            }
            return mergeAnalysis(fallback, json);
        } catch (Exception e) {
            log.warn("情绪分析结果解析失败，回退到内置默认方案", e);
            return fallback;
        }
    }

    /** 一次性 JSON 调用：整体读取响应体 */
    private HttpResponse<String> postForJson(String body) throws Exception {
        return httpClient.send(buildRequest(body), HttpResponse.BodyHandlers.ofString());
    }

    /** 流式调用：拿到响应头后立即返回，body 以输入流形式逐行消费 */
    private HttpResponse<InputStream> postStream(String body) throws Exception {
        return httpClient.send(buildRequest(body), HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpRequest buildRequest(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String chatCompletions(List<Map<String, String>> messages, boolean stream) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("stream", stream);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        return objectMapper.writeValueAsString(payload);
    }

    private void appendHistory(List<Map<String, String>> messages, List<ChatMessage> history) {
        if (history == null) {
            return;
        }
        for (ChatMessage m : history) {
            if (m != null && m.getRole() != null && m.getContent() != null) {
                messages.add(message(m.getRole(), m.getContent()));
            }
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /** 去掉模型可能返回的 markdown 代码块包裹 */
    private String stripCodeFence(String content) {
        String c = content.trim();
        if (c.startsWith("```")) {
            int newline = c.indexOf('\n');
            if (newline > 0) {
                c = c.substring(newline + 1);
            }
            if (c.endsWith("```")) {
                c = c.substring(0, c.length() - 3).trim();
            }
        }
        return c;
    }

    /** 以解析结果覆盖默认字段，缺失字段保留默认值，保证前端消费的键始终齐全 */
    private Map<String, Object> mergeAnalysis(Map<String, Object> fallback, JsonNode json) {
        Map<String, Object> out = new LinkedHashMap<>(fallback);
        out.put("label", json.path("label").asText((String) out.get("label")));
        out.put("primaryEmotion", json.path("primaryEmotion").asText((String) out.get("primaryEmotion")));
        out.put("suggestion", json.path("suggestion").asText((String) out.get("suggestion")));
        out.put("riskDescription", json.path("riskDescription").asText((String) out.get("riskDescription")));
        if (json.hasNonNull("icon")) {
            out.put("icon", json.get("icon").asText());
        }
        if (json.hasNonNull("riskLevel")) {
            out.put("riskLevel", json.get("riskLevel").asInt());
        }
        if (json.hasNonNull("emotionScore")) {
            out.put("emotionScore", json.get("emotionScore").asInt());
        }
        if (json.hasNonNull("isNegative")) {
            out.put("isNegative", json.get("isNegative").asBoolean());
        }
        if (json.hasNonNull("timestamp")) {
            out.put("timestamp", json.get("timestamp").asLong());
        }
        if (json.hasNonNull("keywords") && json.get("keywords").isArray()) {
            List<String> kw = new ArrayList<>();
            for (JsonNode k : json.get("keywords")) {
                kw.add(k.asText());
            }
            out.put("keywords", kw);
        }
        if (json.hasNonNull("improvementSuggestions") && json.get("improvementSuggestions").isArray()) {
            List<String> s = new ArrayList<>();
            for (JsonNode k : json.get("improvementSuggestions")) {
                s.add(k.asText());
            }
            out.put("improvementSuggestions", s);
        }
        return out;
    }

    private Map<String, Object> fallbackAnalysis() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("icon", "🙂");
        r.put("label", "平静");
        r.put("keywords", List.of("倾诉", "陪伴"));
        r.put("riskLevel", 0);
        r.put("timestamp", System.currentTimeMillis());
        r.put("isNegative", false);
        r.put("suggestion", "情绪状态平稳，如需倾诉随时可以继续。");
        r.put("emotionScore", 50);
        r.put("primaryEmotion", "平静");
        r.put("riskDescription", "情绪稳定");
        r.put("improvementSuggestions", List.of("保持规律作息", "记录今天让你感到不错的一件小事"));
        return r;
    }
}