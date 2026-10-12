package com.example.aispringboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加解密器。
 *
 * <p>库中 user.password 现存值为 {@code $2a$10$...} 形式的 BCrypt 密文（长度 60），
 * 因此登录必须用 {@link PasswordEncoder#matches(CharSequence, String)} 校验，
 * 不能用自研哈希或明文比对。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
