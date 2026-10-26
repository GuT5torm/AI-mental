package com.example.aispringboot.ai;

/**
 * 单次大模型调用的运行时参数，来自用户自配（user_ai_config）。
 *
 * <p>设计原则：只做「调用级覆盖」，三个字段都为空时等价于没有这个对象，
 * 客户端实现必须回落到自己的实例级配置（环境变量），保证既有行为不变。
 *
 * @param apiKey 用户自配的 API Key，可空
 * @param baseUrl 用户自配的 OpenAI 兼容端点，可空
 * @param model 用户自配的模型名，可空
 */
public record AiCallOptions(String apiKey, String baseUrl, String model) {

    public static AiCallOptions empty() {
        return new AiCallOptions(null, null, null);
    }

    /** 三个字段是否至少有一个要覆盖 */
    public boolean hasOverride() {
        return isNotBlank(apiKey) || isNotBlank(baseUrl) || isNotBlank(model);
    }

    /** 运行时生效值：调用级优先，为空回落实例级 */
    public static String firstNonBlank(String override, String fallback) {
        return isNotBlank(override) ? override.trim() : fallback;
    }

    public static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public String toString() {
        // 永不把 apiKey 带进日志：只输出是否覆盖与 model/baseUrl
        return "AiCallOptions[apiKey=" + (isNotBlank(apiKey) ? "自定义" : "默认")
                + ", baseUrl=" + (isNotBlank(baseUrl) ? baseUrl : "默认")
                + ", model=" + (isNotBlank(model) ? model : "默认") + "]";
    }
}
