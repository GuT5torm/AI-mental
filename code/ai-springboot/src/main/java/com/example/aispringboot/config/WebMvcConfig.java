package com.example.aispringboot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;
import java.io.File;

/**
 * 静态资源映射。
 *
 * <p>库中历史数据的 filePath 形如 {@code /files/bussiness/article/1756963989972.png}
 * （注意 bussiness 为既有拼写），前端直接拼接 fileBaseUrl 与该路径访问图片，
 * 因此后端必须把 /files/** 映射到上传根目录。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.file.url-prefix:/files}")
    private String fileUrlPrefix;

    @Resource
    private TokenInterceptor tokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截全部请求，放行规则由 @Anonymous 注解表达；
        // actuator 健康端点放行，供容器 HEALTHCHECK 与运维探活使用
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("上传目录创建失败: " + dir.getAbsolutePath());
        }
        String location = "file:" + dir.getAbsolutePath() + File.separator;
        registry.addResourceHandler(fileUrlPrefix + "/**").addResourceLocations(location);
    }

}
