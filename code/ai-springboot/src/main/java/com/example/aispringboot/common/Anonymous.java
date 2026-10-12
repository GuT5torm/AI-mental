package com.example.aispringboot.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记该接口无需登录即可访问。
 *
 * <p>TokenInterceptor 默认拦截所有请求，标注此注解的 handler 会被放行。
 * 比维护一份路径白名单更可靠——后续阶段新增的公开接口（前台知识库、
 * 文章列表等）只需在方法上加一个注解，不会因漏登记而被拦截。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Anonymous {
}
