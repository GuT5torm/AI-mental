package com.example.aispringboot.common;

/**
 * 当前登录用户的线程上下文。
 *
 * <p>由 TokenInterceptor 写入，必须在 afterCompletion 中清理，
 * 否则 Tomcat 线程池复用线程会造成用户身份串号。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> USER_TYPE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, Integer userType) {
        USER_ID.set(userId);
        USER_TYPE.set(userType);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Integer getUserType() {
        return USER_TYPE.get();
    }

    public static boolean isAdmin() {
        return Integer.valueOf(2).equals(USER_TYPE.get());
    }

    public static void clear() {
        USER_ID.remove();
        USER_TYPE.remove();
    }
}
