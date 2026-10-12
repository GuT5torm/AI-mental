package com.example.aispringboot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.aispringboot.DTO.command.PageQuery;
import com.example.aispringboot.DTO.command.SessionStartCommandDTO;
import com.example.aispringboot.DTO.command.StreamCommandDTO;
import com.example.aispringboot.DTO.response.MessageResponseDTO;
import com.example.aispringboot.DTO.response.SessionResponseDTO;
import com.example.aispringboot.DTO.response.SessionStartResponseDTO;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.common.UserContext;
import com.example.aispringboot.service.ChatStreamService;
import com.example.aispringboot.service.ConsultationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 心理咨询。
 *
 * <p>路径前缀与前端 api/frontend.js 完全一致（前端 axios 的 baseURL 已是 /api）。
 *
 * <p>sessionId 在 URL 上是 {@code session_22} 这种带前缀的形式，
 * 由 {@link ConsultationService#parseSessionId} 统一解析，
 * 因此 /sessions/22 与 /sessions/session_22 都能工作。
 */
@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChatController {

    @Resource
    private ConsultationService consultationService;

    @Resource
    private ChatStreamService chatStreamService;

    @PostMapping("/session/start")
    public Result<SessionStartResponseDTO> startSession(@RequestBody(required = false) SessionStartCommandDTO command) {
        return Result.ok(consultationService.startSession(command));
    }

    @GetMapping("/sessions")
    public Result<IPage<SessionResponseDTO>> sessions(@Valid PageQuery query) {
        return Result.ok(consultationService.pageSessions(query));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<MessageResponseDTO>> messages(@PathVariable("sessionId") String sessionId) {
        return Result.ok(consultationService.listMessages(ConsultationService.parseSessionId(sessionId)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable("sessionId") String sessionId) {
        consultationService.deleteSession(ConsultationService.parseSessionId(sessionId));
        return Result.ok(null);
    }

    @GetMapping("/session/{sessionId}/emotion")
    public Result<Map<String, Object>> emotion(@PathVariable("sessionId") String sessionId) {
        return Result.ok(consultationService.getSessionEmotion(ConsultationService.parseSessionId(sessionId)));
    }

    /**
     * 流式对话。
     *
     * <p>produces 用裸的 text/event-stream，不带 charset——前端 onopen 是字符串全等比较。
     * userId 在派发到线程池之前取好：UserContext 是 ThreadLocal，异步线程里读不到。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody StreamCommandDTO command) {
        Long userId = UserContext.getUserId();
        return chatStreamService.stream(userId, command);
    }
}
