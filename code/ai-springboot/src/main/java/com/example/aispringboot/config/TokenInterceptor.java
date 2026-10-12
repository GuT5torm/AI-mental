package com.example.aispringboot.config;

import com.example.aispringboot.common.Anonymous;
import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 令牌校验拦截器。
 *
 * <p>前端 axios 拦截器写入的是 {@code token} 请求头，而非标准的
 * {@code Authorization: Bearer ...}；SSE 场景（@microsoft/fetch-event-source）
 * 传的是 {@code Token}。HTTP 头名大小写不敏感，二者同一处理。
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public TokenInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 预检请求与静态资源直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod hm)) {
            return true;
        }
        boolean anonymous = hm.hasMethodAnnotation(Anonymous.class)
                || hm.getBeanType().isAnnotationPresent(Anonymous.class);

        // 先尝试解析令牌：匿名接口在带 token 时也要能识别身份
        // （文章分页需要据此区分管理员视图与前台视图）
        String token = resolveToken(request);
        if (token != null) {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims == null ? null : jwtUtil.getUserId(claims);
            if (userId != null) {
                UserContext.set(userId, jwtUtil.getUserType(claims));
            }
        }

        if (anonymous) {
            return true;
        }
        if (UserContext.getUserId() == null) {
            writeUnauthorized(response, token == null ? "暂未登录或token已经过期" : "token无效或已过期");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (token == null || token.isBlank()) {
            token = request.getHeader("Authorization");
        }
        if (token != null && token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7);
        }
        if (token != null && token.isBlank()) {
            token = null;
        }
        return token;
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.error(ResultCode.UNAUTHORIZED.getCode(), msg, null);
        objectMapper.writeValue(response.getOutputStream(), result);
    }
}
