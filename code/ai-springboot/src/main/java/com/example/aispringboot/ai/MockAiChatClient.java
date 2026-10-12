package com.example.aispringboot.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 大模型能力的占位实现。
 *
 * <p>存在的目的只有一个：在选型未定的情况下，让会话、消息、SSE 流式、
 * 情绪分析这些契约可以先跑通并联调。它不产生任何真实语义，
 * 输出是确定性的（同样的输入永远得到同样的输出），因此测试可以断言内容。
 *
 * <p>接入真实模型时新增一个实现类并标注
 * {@code @ConditionalOnProperty(name = "app.ai.provider", havingValue = "xxx")} 即可，
 * 本类在 provider 不等于 mock 时自动失效。
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiChatClient implements AiChatClient {

    public static final String MODEL_NAME = "mock-model";

    /** 流式推送的块大小，按字符切分 */
    private static final int CHUNK_SIZE = 8;

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public void streamChat(List<ChatMessage> history, String userMessage, Consumer<String> chunkConsumer) {
        String reply = buildReply(userMessage);
        for (int i = 0; i < reply.length(); i += CHUNK_SIZE) {
            chunkConsumer.accept(reply.substring(i, Math.min(i + CHUNK_SIZE, reply.length())));
        }
    }

    @Override
    public Map<String, Object> analyzeEmotion(List<ChatMessage> recentMessages) {
        String text = extractUserText(recentMessages);
        boolean negative = containsAny(text, NEGATIVE_WORDS);
        boolean positive = !negative && containsAny(text, POSITIVE_WORDS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("icon", negative ? "😔" : positive ? "😊" : "🙂");
        result.put("label", negative ? "低落" : positive ? "轻松" : "平静");
        result.put("keywords", pickKeywords(text));
        result.put("riskLevel", riskLevel(text));
        result.put("timestamp", System.currentTimeMillis());
        result.put("isNegative", negative);
        result.put("suggestion", negative
                ? "听起来你正在承受一些压力，愿意的话可以再多说说具体发生了什么。"
                : positive
                ? "很高兴听到这些，保持现在的节奏就很好。"
                : "情绪状态平稳，如需倾诉随时可以继续。");
        result.put("emotionScore", negative ? 35 : positive ? 70 : 50);
        result.put("primaryEmotion", negative ? "低落" : positive ? "开心" : "平静");
        result.put("riskDescription", riskDescription(riskLevel(text)));
        result.put("improvementSuggestions", suggestions(negative));
        return result;
    }

    private String buildReply(String userMessage) {
        String trimmed = userMessage == null ? "" : userMessage.trim();
        String head = trimmed.length() > 12 ? trimmed.substring(0, 12) : trimmed;
        return "我听到了你说的「" + head + "」。"
                + "这是 Mock 模型的固定回复，用于验证流式推送链路是否正常。"
                + "接入真实模型后，这段文案会被替换。";
    }

    private String extractUserText(List<ChatMessage> recentMessages) {
        if (recentMessages == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : recentMessages) {
            if (m != null && ChatMessage.ROLE_USER.equals(m.getRole()) && m.getContent() != null) {
                sb.append(m.getContent()).append('\n');
            }
        }
        return sb.toString();
    }

    private static final String[] NEGATIVE_WORDS = {"难过", "焦虑", "压力", "崩溃", "失眠", "想哭", "抑郁", "烦", "累"};
    private static final String[] POSITIVE_WORDS = {"开心", "高兴", "顺利", "成功", "期待", "感谢", "喜欢"};

    private static final String[] KEYWORD_POOL = {"倾听", "表达", "调整", "支持", "记录"};

    private static boolean containsAny(String text, String[] words) {
        if (text == null) {
            return false;
        }
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> pickKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        for (String w : NEGATIVE_WORDS) {
            if (text != null && text.contains(w)) {
                keywords.add(w);
            }
        }
        for (String w : POSITIVE_WORDS) {
            if (text != null && text.contains(w)) {
                keywords.add(w);
            }
        }
        if (keywords.isEmpty()) {
            for (int i = 0; i < 3 && i < KEYWORD_POOL.length; i++) {
                keywords.add(KEYWORD_POOL[i]);
            }
        }
        return keywords.subList(0, Math.min(keywords.size(), 5));
    }

    private static int riskLevel(String text) {
        if (text == null) {
            return 0;
        }
        for (String w : new String[]{"崩溃", "抑郁", "不想活"}) {
            if (text.contains(w)) {
                return 2;
            }
        }
        return containsAny(text, NEGATIVE_WORDS) ? 1 : 0;
    }

    private static String riskDescription(int level) {
        return switch (level) {
            case 2 -> "检测到较强烈的负面表达，建议寻求专业帮助";
            case 1 -> "情绪略有波动，注意休息";
            default -> "情绪稳定";
        };
    }

    private static List<String> suggestions(boolean negative) {
        List<String> list = new ArrayList<>();
        if (negative) {
            list.add("把困扰你的事情写下来，区分可控与不可控");
            list.add("尝试一次深呼吸或短时散步");
            list.add("和信任的人聊一聊");
        } else {
            list.add("保持规律作息");
            list.add("记录今天让你感到不错的一件小事");
        }
        return list;
    }
}
