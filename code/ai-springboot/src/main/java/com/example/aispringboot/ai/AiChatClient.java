package com.example.aispringboot.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 大模型能力抽象。
 *
 * <p>流式对话与情绪分析都依赖外部模型，当前模型选型尚未最终确定。
 * 这里先把契约固定下来：真模型接入时只需再提供一个实现类并在
 * {@code app.ai.provider} 上切换，Controller 与 Service 不需要改动。
 *
 * <p>三个方法的粒度是按前端实际需要的调用形态反推的：
 * <ul>
 *   <li>{@link #streamChat} —— 逐块回调，对应 SSE 的 data 帧；</li>
 *   <li>{@link #analyzeEmotion} —— 一次性返回结构化结果，对应会话情绪花园与日记分析。</li>
 * </ul>
 */
public interface AiChatClient {

    /**
     * 模型标识，会写入 {@code consultation_message.ai_model}，
     * 库里既有数据的取值为 {@code deepseek-chat}。
     */
    String getModelName();

    /**
     * 流式生成回复。
     *
     * <p>实现必须保证：回调按序同步触发，全部回调结束后方法才返回。
     * 调用方会在回调里直接写 SseEmitter。
     */
    void streamChat(List<ChatMessage> history, String userMessage, Consumer<String> chunkConsumer);

    /**
     * 对一轮或多轮对话做情绪分析，返回可直接落 jsonb 的结构化结果。
     *
     * <p>键名必须对齐 {@code consultation_session.last_emotion_analysis} 的既有格式，
     * 前端 consultations.vue 的 currentEmotion 直接消费这些字段。
     */
    Map<String, Object> analyzeEmotion(List<ChatMessage> recentMessages);
}
