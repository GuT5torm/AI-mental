package com.example.aispringboot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 大模型客户端的路由入口，容器中唯一的 {@link AiChatClient} 主 Bean。
 *
 * <p>路由规则：
 * <ul>
 *   <li>调用携带 {@link AiCallOptions}（用户自配 Key 或管理员全局 Key）→ 走
 *       {@link DeepSeekAiChatClient} 真实模型；</li>
 *   <li>未携带任何 Key → 按 {@code app.ai.provider} 走默认实现（mock 为演示模式）。</li>
 * </ul>
 * 因此管理员在后台保存全局 Key 后立即对全部用户生效，无需改环境变量或重启；
 * {@code provider} 只决定「没有任何可用 Key 时」的默认行为。
 *
 * <p>依赖注入使用具体类型而非 {@link AiChatClient} 接口，避免自注入循环；
 * 测试中 {@code @MockBean AiChatClient} 会整体替换本路由，语义与替换单一实现一致。
 */
@Component
@Primary
public class RoutingAiChatClient implements AiChatClient {

    private static final String REAL_PROVIDER = "deepseek";

    private final String provider;
    private final MockAiChatClient mockClient;
    private final DeepSeekAiChatClient deepSeekClient;

    public RoutingAiChatClient(@Value("${app.ai.provider:mock}") String provider,
                               MockAiChatClient mockClient,
                               DeepSeekAiChatClient deepSeekClient) {
        this.provider = provider;
        this.mockClient = mockClient;
        this.deepSeekClient = deepSeekClient;
    }

    @Override
    public String getModelName() {
        return defaultClient().getModelName();
    }

    @Override
    public void streamChat(List<ChatMessage> history, String userMessage, Consumer<String> chunkConsumer) {
        defaultClient().streamChat(history, userMessage, chunkConsumer);
    }

    @Override
    public void streamChat(List<ChatMessage> history, String userMessage,
                           Consumer<String> chunkConsumer, AiCallOptions options) {
        if (options != null) {
            deepSeekClient.streamChat(history, userMessage, chunkConsumer, options);
            return;
        }
        defaultClient().streamChat(history, userMessage, chunkConsumer);
    }

    @Override
    public Map<String, Object> analyzeEmotion(List<ChatMessage> recentMessages) {
        return defaultClient().analyzeEmotion(recentMessages);
    }

    @Override
    public Map<String, Object> analyzeEmotion(List<ChatMessage> recentMessages, AiCallOptions options) {
        if (options != null) {
            return deepSeekClient.analyzeEmotion(recentMessages, options);
        }
        return defaultClient().analyzeEmotion(recentMessages);
    }

    /** 没有任何可用 Key 时的默认实现：provider=deepseek 走真实客户端，否则演示模式 */
    private AiChatClient defaultClient() {
        return REAL_PROVIDER.equalsIgnoreCase(provider) ? deepSeekClient : mockClient;
    }
}
