package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息。
 *
 * <p>字段对齐 consultations.vue 的消息模板：id、senderType、content、createdAt。
 */
@Data
public class MessageResponseDTO {

    private Long id;

    private Long sessionId;

    private Integer senderType;

    private Integer messageType;

    private String content;

    private String emotionTag;

    private String aiModel;

    private LocalDateTime createdAt;
}
