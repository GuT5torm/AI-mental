package com.example.aispringboot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.aispringboot.mapper")
public class MybatisPlusConfig {

    /**
     * 分页插件。不注册该拦截器时，MyBatis-Plus 的 selectPage 会退化为全量查询
     * 且 total 始终为 0，前端所有分页列表都会失效。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 3.5.17 中枚举常量名为 POSTGRE_SQL，旧版本的 POSTGRESQL 已不存在
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

}
