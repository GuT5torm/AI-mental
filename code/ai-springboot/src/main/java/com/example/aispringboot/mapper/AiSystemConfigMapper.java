package com.example.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aispringboot.entity.AiSystemConfig;

/**
 * 全局 AI 配置。单行表，读写都按 id=1 精确匹配。
 */
public interface AiSystemConfigMapper extends BaseMapper<AiSystemConfig> {
}
