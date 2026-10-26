package com.example.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aispringboot.entity.UserAiConfig;

/**
 * 用户自配 AI 配置。读写都按 user_id 精确匹配，唯一索引保证一人一条。
 */
public interface UserAiConfigMapper extends BaseMapper<UserAiConfig> {
}
