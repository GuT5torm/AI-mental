package com.example.aispringboot.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内固定窗口限流器，用于登录失败锁定与注册频控。
 *
 * <p>状态保存在 JVM 内存中，重启即清零，且仅在单实例部署下有效；
 * 多实例部署应换用 Redis 或 Caffeine 等带过期的缓存实现，接口保持不变。
 */
@Component
public class RateLimiter {

    /** [窗口起始毫秒, 已计数, 窗口时长毫秒] */
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    /**
     * 记录一次失败。同一 key 在 window 内累计到 limit 次后，isBlocked 开始返回 true，
     * 直到窗口滑过。
     */
    public void recordFailure(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        windows.compute(key, (k, w) -> {
            if (w == null || now - w[0] >= w[2]) {
                return new long[]{now, 1, window.toMillis()};
            }
            w[1] = Math.min(limit, w[1] + 1);
            return w;
        });
    }

    /** 是否已达失败上限（封禁中）。 */
    public boolean isBlocked(String key, int limit, Duration window) {
        long[] w = windows.get(key);
        return w != null
                && w[1] >= limit
                && System.currentTimeMillis() - w[0] < w[2];
    }

    /** 成功后清除失败计数。 */
    public void reset(String key) {
        windows.remove(key);
    }

    /**
     * 按次数直接限流（每次调用都计数），用于注册等不受失败语义约束的场景。
     * 窗口内超过 limit 返回 false。
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w[0] >= w[2]) {
                allowed[0] = limit > 0;
                return new long[]{now, 1, window.toMillis()};
            }
            if (w[1] < limit) {
                w[1]++;
                allowed[0] = true;
            }
            return w;
        });
        return allowed[0];
    }
}
