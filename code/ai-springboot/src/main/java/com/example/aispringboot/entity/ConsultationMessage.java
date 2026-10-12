package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 咨询消息。
 *
 * <p>senderType：1=用户消息，2=AI 回复（实测数据 7 条 / 6 条）。
 * messageType 默认 1，为文本消息。
 */
@Data
@TableName("consultation_message")
public class ConsultationMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Integer senderType;

    private Integer messageType;

    private String content;

    private String emotionTag;

    private String aiModel;

    private LocalDateTime createdAt;

}
