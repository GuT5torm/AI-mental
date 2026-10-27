package com.example.aispringboot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置。
 *
 * <p>生产默认采用同源反向代理部署（前端 nginx 把 /api、/files 转发到后端），
 * 此时浏览器请求同源、不触发 CORS，本配置仅服务于前后端分离域名部署：
 * 通过环境变量 {@code CORS_ALLOWED_ORIGINS} 注入允许的来源（逗号分隔，
 * 支持 * 通配，如 {@code https://app.example.com,https://www.example.com}）。
 * 缺省保持本地开发行为：放行 localhost / 127.0.0.1 任意端口。
 *
 * <p>注意：这里必须用 String 整串接收、交给 {@code allowedOriginPatterns}
 * 自行按逗号解析（Spring 对 {@code ${origin}} 占位符场景的推荐用法）。
 * 若声明为 String[] 注入，属性存在但为空串时字段会被置为 null，
 * allowCredentials(true) 下会触发 Spring 对默认 {@code allowedOrigins=["*"]}
 * 的合法性校验异常，导致所有请求 500。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 前端开发服务器默认跑在 5173。exposedHeaders 必须带上 token，
     * 否则浏览器端读取不到登录响应中的令牌。
     */
    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("token", "Token")
                .allowCredentials(true)
                .maxAge(3600);
    }

}
