package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 开始会话。
 *
 * <p>{@code initialMessage} 只用于在没有标题时兜底生成标题，不落库——
 * 前端 startNewSession 在拿到响应后会立刻用同一条消息调用 /stream，
 * 若在 start 里也写一份，用户消息就会重复一条。
 */
@Data
public class SessionStartCommandDTO {

    private String sessionTitle;

    private String initialMessage;
}
