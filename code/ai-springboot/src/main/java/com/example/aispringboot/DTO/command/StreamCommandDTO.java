package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流式对话请求。
 *
 * <p>{@code sessionId} 有两种来源：新建会话后前端拿到的是 {@code "session_22"}，
 * 点击历史会话时是 {@code "session_" + session.id}，同一个值。
 * 服务端解析时对 {@code session_} 前缀与纯数字都要兼容。
 */
@Data
public class StreamCommandDTO {

    @NotBlank(message = "sessionId不能为空")
    private String sessionId;

    @NotBlank(message = "消息内容不能为空")
    private String userMessage;
}
