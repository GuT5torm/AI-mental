package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项。
 *
 * <p>字段对齐 consultations.vue 的会话列表模板：
 * id、sessionTitle、startedAt、lastMessageContent、messageCount、durationMinutes。
 * 后三个在库里没有对应列，由消息表聚合得出。
 */
@Data
public class SessionResponseDTO {

    private Long id;

    private String sessionTitle;

    private LocalDateTime startedAt;

    private String lastMessageContent;

    private Long messageCount;

    private Long durationMinutes;
}
