package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话的消息聚合结果，用于填充会话列表的派生字段。
 */
@Data
public class SessionMessageStatDTO {

    private Long sessionId;

    private Long messageCount;

    private LocalDateTime firstMessageAt;

    private LocalDateTime lastMessageAt;

    private Long lastMessageId;
}
