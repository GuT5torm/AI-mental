package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aispringboot.DTO.command.PageQuery;
import com.example.aispringboot.DTO.command.SessionStartCommandDTO;
import com.example.aispringboot.DTO.response.MessageResponseDTO;
import com.example.aispringboot.DTO.response.SessionMessageStatDTO;
import com.example.aispringboot.DTO.response.SessionResponseDTO;
import com.example.aispringboot.DTO.response.SessionStartResponseDTO;
import com.example.aispringboot.ai.AiCallOptions;
import com.example.aispringboot.ai.AiChatClient;
import com.example.aispringboot.ai.ChatMessage;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.common.UserContext;
import com.example.aispringboot.entity.ConsultationMessage;
import com.example.aispringboot.entity.ConsultationSession;
import com.example.aispringboot.mapper.ConsultationMessageMapper;
import com.example.aispringboot.mapper.ConsultationSessionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 心理咨询会话。
 *
 * <p>会话与消息都严格按 user_id / session_id 归属校验，跨用户访问一律 6040，
 * 而不是靠前端隐藏按钮。
 */
@Slf4j
@Service
public class ConsultationService {

    /** 交给模型的上下文轮数上限，避免长会话把 prompt 撑爆 */
    private static final int HISTORY_LIMIT = 20;

    public static final String STATUS_ACTIVATE = "ACTIVATE";
    private static final String SESSION_ID_PREFIX = "session_";

    @Resource
    private ConsultationSessionMapper sessionMapper;

    @Resource
    private ConsultationMessageMapper messageMapper;

    @Resource
    private AiChatClient aiChatClient;

    @Resource
    private UserAiConfigService userAiConfigService;

    @Resource
    private ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 会话

    /**
     * 创建会话。
     *
     * <p>不落 initialMessage：前端拿到响应后会用同一条消息调 /stream，
     * 这里再写一份就会出现两条重复的用户消息。
     */
    public SessionStartResponseDTO startSession(SessionStartCommandDTO command) {
        Long userId = requireUserId();
        String title = resolveTitle(command);

        ConsultationSession session = new ConsultationSession();
        session.setUserId(userId);
        session.setSessionTitle(title);
        session.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        return new SessionStartResponseDTO(SESSION_ID_PREFIX + session.getId(), STATUS_ACTIVATE, title);
    }

    public IPage<SessionResponseDTO> pageSessions(PageQuery query) {
        Long userId = requireUserId();
        Page<ConsultationSession> page = new Page<>(query.getCurrent(), query.getSizeValue());
        LambdaQueryWrapper<ConsultationSession> wrapper = Wrappers.lambdaQuery(ConsultationSession.class)
                .eq(ConsultationSession::getUserId, userId)
                .orderByDesc(ConsultationSession::getStartedAt)
                .orderByDesc(ConsultationSession::getId);
        IPage<ConsultationSession> raw = sessionMapper.selectPage(page, wrapper);

        List<Long> ids = raw.getRecords().stream()
                .map(ConsultationSession::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SessionMessageStatDTO> stats = loadStats(ids);
        Map<Long, String> lastContents = loadLastContents(stats.values().stream()
                .map(SessionMessageStatDTO::getLastMessageId)
                .filter(Objects::nonNull)
                .toList());

        List<SessionResponseDTO> rows = raw.getRecords().stream()
                .map(s -> toResponse(s, stats.get(s.getId()), lastContents))
                .toList();

        Page<SessionResponseDTO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(rows);
        return result;
    }

    public List<MessageResponseDTO> listMessages(Long sessionId) {
        Long userId = requireUserId();
        ConsultationSession session = requireOwnedSession(sessionId, userId);
        return loadMessages(session.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        Long userId = requireUserId();
        ConsultationSession session = requireOwnedSession(sessionId, userId);
        messageMapper.deleteBySessionId(session.getId());
        sessionMapper.deleteById(session.getId());
    }

    // ---------------------------------------------------------------- 情绪

    /**
     * 读取会话情绪分析结果。
     *
     * <p>库里已有结果就直接返回；没有则现场分析一次并回写。
     * 前端在 done 事件之后立刻调这个接口，所以分析必须在推 done 之前完成，
     * 否则前端会读到旧值或空值。
     */
    public Map<String, Object> getSessionEmotion(Long sessionId) {
        Long userId = requireUserId();
        ConsultationSession session = requireOwnedSession(sessionId, userId);

        if (session.getLastEmotionAnalysis() != null && !session.getLastEmotionAnalysis().isBlank()) {
            try {
                return objectMapper.readValue(session.getLastEmotionAnalysis(),
                        new TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("会话 {} 的情绪分析结果不是合法 JSON，重新分析", session.getId(), e);
            }
        }

        List<ChatMessage> history = loadHistory(session.getId(), HISTORY_LIMIT);
        if (history.isEmpty()) {
            return defaultEmotion();
        }
        // 用户自配 Key 优先，其次管理员全局 Key；都没有时走服务端默认实现
        Map<String, Object> emotion = aiChatClient.analyzeEmotion(history,
                userAiConfigService.resolveOptionsWithFallback(userId));
        saveEmotion(session.getId(), emotion);
        return emotion;
    }

    /**
     * 分析当前会话并回写。供流式对话在推送 done 之前调用。
     */
    public Map<String, Object> analyzeAndSave(Long sessionId, List<ChatMessage> recent) {
        return analyzeAndSave(sessionId, recent, null);
    }

    /**
     * 带用户自配参数的分析版本：options 为 null 时行为与单参版本一致。
     */
    public Map<String, Object> analyzeAndSave(Long sessionId, List<ChatMessage> recent, AiCallOptions options) {
        Map<String, Object> emotion = aiChatClient.analyzeEmotion(recent, options);
        saveEmotion(sessionId, emotion);
        return emotion;
    }

    public void saveEmotion(Long sessionId, Map<String, Object> emotion) {
        try {
            String json = objectMapper.writeValueAsString(emotion);
            sessionMapper.updateEmotionAnalysis(sessionId, json, LocalDateTime.now());
        } catch (Exception e) {
            // 情绪分析失败不影响对话主流程，只记日志
            log.error("回写会话 {} 情绪分析结果失败", sessionId, e);
        }
    }

    // ---------------------------------------------------------------- 消息

    /**
     * 落库一条用户消息。流式接口在推送之前调用，保证断流时用户说过的话不丢。
     */
    public ConsultationMessage appendUserMessage(Long sessionId, String content) {
        ConsultationMessage message = new ConsultationMessage();
        message.setSessionId(sessionId);
        message.setSenderType(1);
        message.setMessageType(1);
        message.setContent(content);
        messageMapper.insert(message);
        return message;
    }

    public ConsultationMessage appendAiMessage(Long sessionId, String content) {
        ConsultationMessage message = new ConsultationMessage();
        message.setSessionId(sessionId);
        message.setSenderType(2);
        message.setMessageType(1);
        message.setContent(content);
        message.setAiModel(aiChatClient.getModelName());
        messageMapper.insert(message);
        return message;
    }

    /**
     * 取最近若干条消息作为模型上下文，按 id 升序。
     */
    public List<ChatMessage> loadHistory(Long sessionId, int limit) {
        List<ConsultationMessage> all = messageMapper.selectList(
                Wrappers.lambdaQuery(ConsultationMessage.class)
                        .eq(ConsultationMessage::getSessionId, sessionId)
                        .orderByAsc(ConsultationMessage::getId));
        if (all.size() <= limit) {
            return all.stream().map(ConsultationService::toChatMessage).toList();
        }
        return all.subList(all.size() - limit, all.size()).stream()
                .map(ConsultationService::toChatMessage)
                .toList();
    }

    // ---------------------------------------------------------------- 校验

    /**
     * 解析前端传来的 sessionId，兼容 {@code session_22} 与 {@code 22} 两种写法。
     */
    public static Long parseSessionId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "sessionId不能为空");
        }
        String s = raw.trim();
        if (s.startsWith(SESSION_ID_PREFIX)) {
            s = s.substring(SESSION_ID_PREFIX.length());
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "sessionId格式不正确");
        }
    }

    public ConsultationSession requireOwnedSession(Long sessionId, Long userId) {
        ConsultationSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_EXIST.getCode(), "会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(ResultCode.SESSION_FORBIDDEN.getCode(), "无权访问该会话");
        }
        return session;
    }

    // ---------------------------------------------------------------- 内部

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "暂未登录或token已经过期");
        }
        return userId;
    }

    private static String resolveTitle(SessionStartCommandDTO command) {
        if (command == null) {
            return defaultTitle();
        }
        if (command.getSessionTitle() != null && !command.getSessionTitle().isBlank()) {
            String t = command.getSessionTitle().trim();
            return t.length() > 200 ? t.substring(0, 200) : t;
        }
        String initial = command.getInitialMessage();
        if (initial != null && !initial.isBlank()) {
            String t = "会话 - " + initial.trim();
            return t.length() > 200 ? t.substring(0, 200) : t;
        }
        return defaultTitle();
    }

    private static String defaultTitle() {
        return "小暖助手 - " + LocalDateTime.now();
    }

    private Map<Long, SessionMessageStatDTO> loadStats(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<ConsultationMessage> wrapper = new QueryWrapper<ConsultationMessage>()
                .in("session_id", sessionIds)
                .groupBy("session_id");
        List<SessionMessageStatDTO> stats = messageMapper.selectStatsBySession(wrapper);
        return stats.stream().collect(Collectors.toMap(SessionMessageStatDTO::getSessionId, Function.identity()));
    }

    private Map<Long, String> loadLastContents(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ConsultationMessage> messages = messageMapper.selectBatchIds(messageIds);
        return messages.stream()
                .collect(Collectors.toMap(ConsultationMessage::getId, m -> abbreviate(m.getContent())));
    }

    private static String abbreviate(String content) {
        if (content == null) {
            return null;
        }
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine;
    }

    private static SessionResponseDTO toResponse(ConsultationSession session,
                                                 SessionMessageStatDTO stat,
                                                 Map<Long, String> lastContents) {
        SessionResponseDTO dto = new SessionResponseDTO();
        dto.setId(session.getId());
        dto.setSessionTitle(session.getSessionTitle());
        dto.setStartedAt(session.getStartedAt());
        if (stat == null) {
            dto.setMessageCount(0L);
            dto.setDurationMinutes(0L);
            return dto;
        }
        dto.setMessageCount(stat.getMessageCount());
        dto.setDurationMinutes(durationMinutes(stat));
        if (stat.getLastMessageId() != null) {
            dto.setLastMessageContent(lastContents.get(stat.getLastMessageId()));
        }
        return dto;
    }

    private static Long durationMinutes(SessionMessageStatDTO stat) {
        LocalDateTime from = stat.getFirstMessageAt();
        LocalDateTime to = stat.getLastMessageAt();
        if (from == null || to == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(from, to).toMinutes());
    }

    private List<MessageResponseDTO> loadMessages(Long sessionId) {
        List<ConsultationMessage> messages = messageMapper.selectList(
                Wrappers.lambdaQuery(ConsultationMessage.class)
                        .eq(ConsultationMessage::getSessionId, sessionId)
                        .orderByAsc(ConsultationMessage::getId));
        return messages.stream().map(ConsultationService::toMessageResponse).toList();
    }

    private static MessageResponseDTO toMessageResponse(ConsultationMessage m) {
        MessageResponseDTO dto = new MessageResponseDTO();
        dto.setId(m.getId());
        dto.setSessionId(m.getSessionId());
        dto.setSenderType(m.getSenderType());
        dto.setMessageType(m.getMessageType());
        dto.setContent(m.getContent());
        dto.setEmotionTag(m.getEmotionTag());
        dto.setAiModel(m.getAiModel());
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }

    private static ChatMessage toChatMessage(ConsultationMessage m) {
        return m.getSenderType() != null && m.getSenderType() == 2
                ? ChatMessage.assistant(m.getContent())
                : ChatMessage.user(m.getContent());
    }

    private static Map<String, Object> defaultEmotion() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("icon", "🙂");
        result.put("label", "平静");
        result.put("keywords", List.of("倾听"));
        result.put("riskLevel", 0);
        result.put("timestamp", System.currentTimeMillis());
        result.put("isNegative", false);
        result.put("suggestion", "还没有足够的对话内容，继续聊聊吧。");
        result.put("emotionScore", 50);
        result.put("primaryEmotion", "平静");
        result.put("riskDescription", "情绪稳定");
        result.put("improvementSuggestions", List.of("说说最近的状态"));
        return result;
    }
}
