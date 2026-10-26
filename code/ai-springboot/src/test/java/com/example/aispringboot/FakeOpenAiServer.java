package com.example.aispringboot;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用 OpenAI 兼容假端点：返回固定内容的 SSE 流，替代真实大模型服务。
 *
 * <p>用于「配置 Key 后自动走真实模型」的链路验证：测试直插加密配置绕过
 * SSRF 校验，把 baseUrl 指向本类监听的 127.0.0.1 随机端口，断言回复确实
 * 来自该端点。JDK 自带 HttpServer，不引入额外依赖。
 */
public final class FakeOpenAiServer implements AutoCloseable {

    /** 假端点的流式回复正文，两个 delta 各推一半 */
    public static final String REPLY_TEXT = "FakeReply";

    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();

    public FakeOpenAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            // 消费请求体，避免连接悬挂
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("""
                    data: {"choices":[{"delta":{"content":"Fake"}}]}

                    data: {"choices":[{"delta":{"content":"Reply"}}]}

                    data: [DONE]

                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    /** 假端点地址，可直接作为 AI 配置的 baseUrl */
    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int requestCount() {
        return requestCount.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
