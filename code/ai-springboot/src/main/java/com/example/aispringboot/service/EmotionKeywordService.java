package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.entity.EmotionKeyword;
import com.example.aispringboot.mapper.EmotionKeywordMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 情绪触发关键词匹配与初始心情分推导。
 *
 * <p>词表存在 emotion_keyword 表中，可随时增删或停用。表只有几十行，
 * 这里不做缓存层，每次直接查库——改词即时生效，也避免缓存失效问题。
 *
 * <p>每个词带严重度等级 {@code level}（1=轻度、2=中度、3=重度），
 * 命中多个词时按"最重词定档、同级多词降档"推导自动记录的初始心情分，
 * 后续 AI 评估完成还会用 emotionScore 回写修正（见 AiAnalysisTaskService）。
 */
@Slf4j
@Service
public class EmotionKeywordService {

    public static final int ENABLED = 1;

    /** 轻度：日常吐槽信号 */
    public static final int LEVEL_LIGHT = 1;
    /** 中度：明确负向状态 */
    public static final int LEVEL_MODERATE = 2;
    /** 重度：痛苦/危机信号 */
    public static final int LEVEL_SEVERE = 3;

    /** 各等级对应的基础心情分（1-10，分越低心情越差） */
    private static final Map<Integer, Integer> LEVEL_BASE_SCORE = Map.of(
            LEVEL_LIGHT, 4,
            LEVEL_MODERATE, 3,
            LEVEL_SEVERE, 2);

    /** 同一等级命中达到该数量的不同词时，再降 1 分 */
    private static final int SAME_LEVEL_DROP_THRESHOLD = 3;

    @Resource
    private EmotionKeywordMapper keywordMapper;

    /**
     * 返回消息中命中的全部启用关键词，保持词表顺序；未命中返回空列表。
     */
    public List<String> matchKeywords(String message) {
        return match(message).stream()
                .map(EmotionKeyword::getKeyword)
                .toList();
    }

    /**
     * 返回消息中命中的全部启用关键词实体（含等级），保持词表顺序；未命中返回空列表。
     */
    public List<EmotionKeyword> match(String message) {
        if (message == null || message.isBlank()) {
            return Collections.emptyList();
        }
        List<EmotionKeyword> words = keywordMapper.selectList(
                Wrappers.lambdaQuery(EmotionKeyword.class)
                        .eq(EmotionKeyword::getEnabled, ENABLED)
                        .orderByAsc(EmotionKeyword::getId));
        return words.stream()
                .filter(k -> k.getKeyword() != null && !k.getKeyword().isBlank()
                        && message.contains(k.getKeyword()))
                .toList();
    }

    /**
     * 按命中词的等级推导初始心情分：最重词定档（轻→4、中→3、重→2），
     * 同一等级命中 ≥3 个不同词再降 1 分，最低 1 分；未命中返回默认分。
     *
     * <p>该分数只是自动记录落库时的初始值，AI 评估完成后会用
     * emotionScore 回写修正，让「我的心情」走势逐渐贴近真实状态。
     */
    public int deriveMoodScore(List<EmotionKeyword> matched, int defaultScore) {
        if (matched == null || matched.isEmpty()) {
            return defaultScore;
        }
        Map<Integer, Long> countByLevel = matched.stream()
                .collect(Collectors.groupingBy(this::levelOf, Collectors.counting()));

        // 命中词存在时只按等级定档，defaultScore 不参与取小——
        // 否则轻度词的 4 分会被更低的默认分 3 压住，等级就失去意义了
        int score = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Long> entry : countByLevel.entrySet()) {
            Integer base = LEVEL_BASE_SCORE.get(entry.getKey());
            if (base == null) {
                continue;
            }
            int candidate = entry.getValue() >= SAME_LEVEL_DROP_THRESHOLD ? base - 1 : base;
            score = Math.min(score, candidate);
        }
        return Math.max(1, score);
    }

    /** 等级缺失或非法时按轻度兜底，保证迁移前的存量数据也能参与推导 */
    private int levelOf(EmotionKeyword keyword) {
        Integer level = keyword.getLevel();
        return (level == null || level < LEVEL_LIGHT || level > LEVEL_SEVERE)
                ? LEVEL_LIGHT : level;
    }

}
