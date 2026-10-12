package com.example.aispringboot.service;

import com.example.aispringboot.DTO.command.StreamCommandDTO;
import com.example.aispringboot.ai.AiChatClient;
import com.example.aispringboot.ai.ChatMessage;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.entity.ConsultationMessage;
import com.example.aispringboot.entity.ConsultationSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Resource
    private ConsultationService consultationService;

    @Resource
    private AiChatClient aiChatClient;

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

            List<ChatMessage> history = new ArrayList<>(
                    consultationService.loadHistory(session.getId(), 20));
            history.remove(history.size() - 1); // 上一步刚插入的用户消息，不重复进上下文
            List<ChatMessage> context = new ArrayList<>(history);
            context.add(ChatMessage.user(command.getUserMessage()));

            StringBuilder answer = new StringBuilder();
            aiChatClient.streamChat(context, command.getUserMessage(), chunk -> {
                answer.append(chunk);
                try {
                    emitter.send(chunkFrame(chunk));
                } catch (Exception e) {
                    // IOException 表示客户端已断开；连接不可用时 Spring 还会抛
                    // AsyncRequestNotUsableException。两种情况都终止推流，避免空转占用线程。
                    throw new ClientDisconnectedException(e);
                }
            });

            String content = answer.toString();
            if (!content.isBlank()) {
                ConsultationMessage aiMessage = consultationService.appendAiMessage(session.getId(), content);
                log.debug("AI 消息落库 id={}", aiMessage.getId());
            }

            // 情绪分析必须在 done 之前完成：前端一收到 done 就立刻回查情绪
            consultationService.analyzeAndSave(session.getId(), contextWithAnswer(context, content));

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
