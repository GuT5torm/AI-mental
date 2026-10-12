package com.example.aispringboot.common;

/**
 * 权限判定。管理员的判定依据是 token 里携带的 userType=2，
 * 与前端 router/index.js、login.vue 的判定口径一致。
 */
public final class AuthUtil {

    private static final int USER_TYPE_ADMIN = 2;

    private AuthUtil() {
    }

    public static boolean isAdmin() {
        return Integer.valueOf(USER_TYPE_ADMIN).equals(UserContext.getUserType());
    }

    /**
     * 写操作统一走这里。普通用户拿到有效 token 也不能改内容。
     */
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED.getCode(), "需要管理员权限");
        }
    }

    /**
     * 取当前登录用户。匿名接口也会解析 token，但没有 token 时就是 null，
     * 需要登录态的写操作必须先过这一关。
     */
    public static Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "暂未登录或token已经过期");
        }
        return userId;
    }
}
