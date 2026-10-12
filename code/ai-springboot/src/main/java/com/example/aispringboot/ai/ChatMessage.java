package com.example.aispringboot.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 交给模型的一轮对话。
 *
 * <p>role 用小写字符串，与主流 Chat Completions 接口一致，
 * 便于后续替换为真实实现时直接映射。
 */
@Data
@AllArgsConstructor
public class ChatMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /** user / assistant */
    private String role;

    private String content;

    public static ChatMessage user(String content) {
        return new ChatMessage(ROLE_USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ROLE_ASSISTANT, content);
    }
}
