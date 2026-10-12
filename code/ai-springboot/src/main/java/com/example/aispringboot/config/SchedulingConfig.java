package com.example.aispringboot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务。
 *
 * <p>单独拎出来是为了能在测试里整体关掉：AI 任务消费者会真实写入数据库，
 * 测试若沿用默认配置，定时器可能在断言期间改掉被测数据。
 * 测试类上加 {@code app.ai.scheduler.enabled=false} 即可让本配置不注册。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.ai.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
