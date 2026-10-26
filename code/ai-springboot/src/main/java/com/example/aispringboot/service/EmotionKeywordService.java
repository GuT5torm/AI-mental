package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.entity.EmotionKeyword;
import com.example.aispringboot.mapper.EmotionKeywordMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 情绪触发关键词匹配。
 *
 * <p>词表存在 emotion_keyword 表中，可随时增删或停用。表只有十几行，
 * 这里不做缓存层，每次直接查库——改词即时生效，也避免缓存失效问题。
 */
@Slf4j
@Service
public class EmotionKeywordService {

    public static final int ENABLED = 1;

    @Resource
    private EmotionKeywordMapper keywordMapper;

    /**
     * 返回消息中命中的全部启用关键词，保持词表顺序；未命中返回空列表。
     */
    public List<String> matchKeywords(String message) {
        if (message == null || message.isBlank()) {
            return Collections.emptyList();
        }
        List<EmotionKeyword> words = keywordMapper.selectList(
                Wrappers.lambdaQuery(EmotionKeyword.class)
                        .eq(EmotionKeyword::getEnabled, ENABLED)
                        .orderByAsc(EmotionKeyword::getId));
        return words.stream()
                .map(EmotionKeyword::getKeyword)
                .filter(k -> k != null && !k.isBlank() && message.contains(k))
                .toList();
    }

}
