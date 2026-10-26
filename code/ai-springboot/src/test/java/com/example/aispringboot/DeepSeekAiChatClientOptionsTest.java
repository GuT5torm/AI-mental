package com.example.aispringboot;

import com.example.aispringboot.ai.AiCallOptions;
import com.example.aispringboot.ai.ChatMessage;
import com.example.aispringboot.ai.DeepSeekAiChatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeepSeekAiChatClient 调用级覆盖（用户自配 Key / 端点 / 模型）的单元测试。
 *
 * <p>用 JDK 自带 HttpServer 起一个本地 mock 端点，验证请求头里的
 * Authorization、请求体里的 model 是否按「调用级优先、为空回落实例配置」生效。
 * 不访问任何外网。
 */
public class DeepSeekAiChatClientOptionsTest {

    private HttpServer server;
    private int port;

    /** 每次请求捕获到的 Authorization 与 model，供断言 */
    private final AtomicReference<String> seenAuth = new AtomicReference<>();
    private final AtomicReference<String> seenModel = new AtomicReference<>();
    /** 每次请求捕获到的 messages 数组，供人设注入断言 */
    private final AtomicReference<JsonNode> seenMessages = new AtomicReference<>();

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", this::handle);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            JsonNode root = new ObjectMapper().readTree(body);
            seenModel.set(root.path("model").asText());
            seenMessages.set(root.path("messages"));
        } catch (Exception ignored) {
        }
        byte[] resp = String.join("\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
                "data: [DONE]",
                "").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, resp.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
        }
    }

    private DeepSeekAiChatClient newClient() {
        DeepSeekAiChatClient client = new DeepSeekAiChatClient();
        ReflectionTestUtils.setField(client, "apiKey", "sk-server-key");
        ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:" + port);
        ReflectionTestUtils.setField(client, "model", "server-model");
        ReflectionTestUtils.setField(client, "temperature", 0.8);
        ReflectionTestUtils.setField(client, "maxTokens", 2048);
        ReflectionTestUtils.setField(client, "timeoutSeconds", 10L);
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
        return client;
    }

    @Test
    public void 调用级参数覆盖实例配置() {
        DeepSeekAiChatClient client = newClient();
        List<String> chunks = new ArrayList<>();
        client.streamChat(List.of(), "你好", chunks::add,
                new AiCallOptions("sk-user-key", "http://127.0.0.1:" + port, "user-model"));

        assertEquals("Bearer sk-user-key", seenAuth.get(), "应使用用户自配 Key");
        assertEquals("user-model", seenModel.get(), "应使用用户自配模型名");
        assertEquals(List.of("你好"), chunks, "应把 SSE 分片回调给消费方");
    }

    @Test
    public void options为空时回落实例配置() {
        DeepSeekAiChatClient client = newClient();
        List<String> chunks = new ArrayList<>();
        client.streamChat(List.of(), "你好", chunks::add, AiCallOptions.empty());

        assertEquals("Bearer sk-server-key", seenAuth.get(), "未覆盖时应回落服务端 Key");
        assertEquals("server-model", seenModel.get(), "未覆盖时应回落服务端模型名");
        assertTrue(chunks.contains("你好"));
    }

    @Test
    public void 部分覆盖时其余字段回落() {
        DeepSeekAiChatClient client = newClient();
        client.streamChat(List.of(), "你好", c -> {
        }, new AiCallOptions("sk-user-key", null, null));

        assertEquals("Bearer sk-user-key", seenAuth.get(), "覆盖的 Key 应生效");
        assertEquals("server-model", seenModel.get(), "未覆盖的模型应回落实例配置");
    }

    @Test
    public void 人设提示词注入为system消息() {
        DeepSeekAiChatClient client = newClient();
        ReflectionTestUtils.setField(client, "personaPrompt", "你是心理陪伴伙伴。");
        List<String> chunks = new ArrayList<>();
        client.streamChat(List.of(), "我有点难过", chunks::add);

        JsonNode messages = seenMessages.get();
        assertEquals(2, messages.size(), "应为人设 system + 用户消息共两条");
        assertEquals("system", messages.get(0).path("role").asText(), "首条消息应为人设 system");
        assertEquals("你是心理陪伴伙伴。", messages.get(0).path("content").asText());
        assertEquals("user", messages.get(1).path("role").asText());
        assertEquals("我有点难过", messages.get(1).path("content").asText());
        assertEquals(List.of("你好"), chunks, "人设注入不影响正常推流");
    }

    @Test
    public void 未加载人设时不注入system消息() {
        DeepSeekAiChatClient client = newClient();
        client.streamChat(List.of(), "你好", c -> {
        });

        JsonNode messages = seenMessages.get();
        assertEquals("user", messages.get(0).path("role").asText(), "无人设时首条不应是 system");
        assertEquals(1, messages.size());
    }

    @Test
    public void 人设注入时历史轮次仍按序保留() {
        DeepSeekAiChatClient client = newClient();
        ReflectionTestUtils.setField(client, "personaPrompt", "你是心理陪伴伙伴。");
        client.streamChat(
                List.of(ChatMessage.user("上次我说了那件事"), ChatMessage.assistant("我记着呢")),
                "今天还好吗", c -> {
                });

        JsonNode messages = seenMessages.get();
        assertEquals(4, messages.size(), "应为 system + 两轮历史 + 当前消息");
        assertEquals("上次我说了那件事", messages.get(1).path("content").asText());
        assertEquals("我记着呢", messages.get(2).path("content").asText());
        assertEquals("今天还好吗", messages.get(3).path("content").asText());
    }
}
