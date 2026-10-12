package com.example.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 开始会话的响应。
 *
 * <p>{@code sessionId} 返回 {@code "session_" + id}。前端 consultations.vue 的
 * handleSessionClick 里对历史会话做的正是 {@code "session_" + session.id} 拼接，
 * 两种来源保持同一格式，startAIResponse 才能统一处理。
 *
 * <p>{@code status} 固定 ACTIVATE，前端用它判断当前不是临时会话（TEMP）。
 */
@Data
@AllArgsConstructor
public class SessionStartResponseDTO {

    private String sessionId;

    private String status;

    private String sessionTitle;
}
