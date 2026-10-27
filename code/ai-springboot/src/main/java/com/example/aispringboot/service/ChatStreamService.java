package com.example.aispringboot.service;

import com.example.aispringboot.DTO.command.StreamCommandDTO;
import com.example.aispringboot.ai.AiCallOptions;
import com.example.aispringboot.ai.AiChatClient;
import com.example.aispringboot.ai.ChatMessage;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.entity.ConsultationMessage;
import com.example.aispringboot.entity.ConsultationSession;
import com.example.aispringboot.entity.EmotionKeyword;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * SSE 流式对话。
 *
 * <p>推流格式完全按前端 consultations.vue 的 fetchEventSource 回调来定：
 * <ul>
 *   <li>正文块：无名事件，{@code data: {"code":"200","data":{"content":"…"}}}，前端逐块拼接；</li>
 *   <li>结束：命名事件 {@code done}，前端收到后 abort 并立刻拉取情绪分析；</li>
 *   <li>失败：{@code data: {"code":"-1","message":"…"}}，前端就地显示"AI回复失败"。</li>
 * </ul>
 *
 * <p>produces 必须是裸的 {@code text/event-stream}。前端 onopen 里做的是
 * {@code headers.get('Content-Type') !== 'text/event-stream'} 全等比较，
 * 多一个 {@code ;charset=UTF-8} 就会误报"服务器返回非流式数据"。
 * SSE 规范本身规定该媒体类型固定按 UTF-8 解码，不写 charset 不会乱码。
 *
 * <p>推流在独立线程池里做，因为 SseEmitter 的写入会阻塞，
 * 占用 Tomcat 请求线程会把连接池拖死。
 */
@Slf4j
@Service
public class ChatStreamService {

    /** 单次对话的超时上限，超时后由 onTimeout 兜底结束 */
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;

    /** 对话命中关键词时自动记录情绪日志的总开关 */
    @Value("${app.emotion-capture.enabled:true}")
    private boolean emotionCaptureEnabled;

    /** mood_score 非空，自动记录先落默认分，AI 评估结果另存分析字段 */
    @Value("${app.emotion-capture.default-mood-score:3}")
    private Integer emotionCaptureDefaultMoodScore;

    @Resource
    private ConsultationService consultationService;

    @Resource
    private AiChatClient aiChatClient;

    @Resource
    private UserAiConfigService userAiConfigService;

    @Resource
    private EmotionKeywordService emotionKeywordService;

    @Resource
    private EmotionDiaryService emotionDiaryService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    @Qualifier("sseExecutor")
    private Executor sseExecutor;

    public SseEmitter stream(Long userId, StreamCommandDTO command) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitter.onCompletion(() -> log.debug("SSE 正常结束"));
        emitter.onTimeout(() -> log.warn("SSE 超时，sessionId={}", command.getSessionId()));
        emitter.onError(e -> log.warn("SSE 异常，sessionId={}", command.getSessionId(), e));

        sseExecutor.execute(() -> runStream(emitter, userId, command));
        return emitter;
    }

    private void runStream(SseEmitter emitter, Long userId, StreamCommandDTO command) {
        ConsultationSession session = null;
        try {
            Long sessionId = ConsultationService.parseSessionId(command.getSessionId());
            session = consultationService.requireOwnedSession(sessionId, userId);

            // 先落用户消息：即使后面断流或模型失败，用户说过的内容也不丢
            consultationService.appendUserMessage(session.getId(), command.getUserMessage());

            // 关键词触发的情绪自动记录：命中且本会话未记录时写入并推事件，
            // 失败只记日志，不影响对话主流程
            pushEmotionCaptureIfNeeded(emitter, userId, session.getId(), command.getUserMessage());

            List<ChatMessage> history = new ArrayList<>(
                    consultationService.loadHistory(session.getId(), 20));
            history.remove(history.size() - 1); // 上一步刚插入的用户消息，不重复进上下文
            List<ChatMessage> context = new ArrayList<>(history);
            context.add(ChatMessage.user(command.getUserMessage()));

            // 运行时参数：用户自配 Key 优先，其次管理员全局 Key，都没有则走演示模式
            AiCallOptions options = userAiConfigService.resolveOptionsWithFallback(userId);

            StringBuilder answer = new StringBuilder();
            // 按契约 history 只含历史轮次，当前这条用户消息由客户端实现自行追加，
            // 否则真实模型会收到重复的同一条用户消息
            aiChatClient.streamChat(history, command.getUserMessage(), chunk -> {
                answer.append(chunk);
                try {
                    emitter.send(chunkFrame(chunk));
                } catch (Exception e) {
                    // IOException 表示客户端已断开；连接不可用时 Spring 还会抛
                    // AsyncRequestNotUsableException。两种情况都终止推流，避免空转占用线程。
                    throw new ClientDisconnectedException(e);
                }
            }, options);

            String content = answer.toString();
            if (!content.isBlank()) {
                ConsultationMessage aiMessage = consultationService.appendAiMessage(session.getId(), content);
                log.debug("AI 消息落库 id={}", aiMessage.getId());
            }

            // 情绪分析必须在 done 之前完成：前端一收到 done 就立刻回查情绪
            consultationService.analyzeAndSave(session.getId(), contextWithAnswer(context, content), options);

            emitter.send(SseEmitter.event()
                    .name("done")
                    .data("{\"code\":\"200\",\"msg\":\"complete\"}"));
        } catch (ClientDisconnectedException e) {
            log.info("客户端在推流过程中断开，sessionId={}", command.getSessionId());
        } catch (BusinessException e) {
            log.warn("流式对话业务失败，sessionId={}, code={}", command.getSessionId(), e.getCode());
            sendQuietly(emitter, errorFrame(e.getMessage()));
        } catch (Exception e) {
            log.error("流式对话异常，sessionId={}", command.getSessionId(), e);
            sendQuietly(emitter, errorFrame("AI回复失败，请重试"));
        } finally {
            // 客户端断开后连接已不可用，此时 complete() 会抛 AsyncRequestNotUsableException，
            // 不吞掉的话它会被全局异常处理器当成系统异常记一条 ERROR。
            // 这里只做收尾，任何异常都不影响主流程结果。
            try {
                emitter.complete();
            } catch (Exception ignored) {
                log.debug("SSE 收尾时连接已不可用");
            }
        }
    }

    private static List<ChatMessage> contextWithAnswer(List<ChatMessage> context, String answer) {
        List<ChatMessage> full = new ArrayList<>(context);
        if (!answer.isBlank()) {
            full.add(ChatMessage.assistant(answer));
        }
        return full;
    }

    /**
     * 用户消息命中情绪关键词时，自动记一条 CHAT 来源的情绪日志并推送
     * {@code emotion_captured} 命名事件（在 AI 正文之前发出，前端据此插入提示卡片）。
     *
     * <p>同一会话只记录一次（查 emotion_diary.source_session_id 去重）；
     * 评估由 ai_analysis_task 异步完成，这里不等待结果。
     * 任何异常都只记日志——自动记录是增强能力，绝不能拖垮对话主流程。
     */
    private void pushEmotionCaptureIfNeeded(SseEmitter emitter, Long userId, Long sessionId, String userMessage) {
        try {
            if (!emotionCaptureEnabled) {
                return;
            }
            List<EmotionKeyword> matched = emotionKeywordService.match(userMessage);
            if (matched.isEmpty() || emotionDiaryService.hasChatCapture(sessionId)) {
                return;
            }
            List<String> matchedKeywords = matched.stream().map(EmotionKeyword::getKeyword).toList();
            int moodScore = emotionKeywordService.deriveMoodScore(matched, emotionCaptureDefaultMoodScore);
            Long diaryId = emotionDiaryService.createDiaryFromChat(
                    userId, sessionId, userMessage, matchedKeywords, moodScore);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("diaryId", diaryId);
            data.put("matchedKeywords", matchedKeywords);
            data.put("moodScore", moodScore);
            data.put("hint", "检测到情绪波动，已为你记录，AI 评估稍后完成");
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("code", "200");
            frame.put("data", data);

            emitter.send(SseEmitter.event().name("emotion_captured").data(write(frame)));
            log.info("情绪自动记录完成 diaryId={} sessionId={} keywords={} moodScore={}",
                    diaryId, sessionId, matchedKeywords, moodScore);
        } catch (Exception e) {
            log.warn("情绪自动记录失败（不影响对话），sessionId={}", sessionId, e);
        }
    }

    private void sendQuietly(SseEmitter emitter, String frame) {
        try {
            emitter.send(frame);
        } catch (Exception e) {
            log.debug("错误帧推送失败，客户端可能已断开");
        }
    }

    private String chunkFrame(String content) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("code", "200");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", content);
        frame.put("data", data);
        return write(frame);
    }

    private String errorFrame(String message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("code", "-1");
        frame.put("message", message == null ? "AI回复失败" : message);
        return write(frame);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("序列化 SSE 帧失败", e);
            return "{\"code\":\"-1\",\"message\":\"序列化失败\"}";
        }
    }

    /** 客户端断开的内部信号，用于把 IOException 从 lambda 里带出来 */
    private static class ClientDisconnectedException extends RuntimeException {
        ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
