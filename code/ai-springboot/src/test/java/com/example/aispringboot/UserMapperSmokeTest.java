package com.example.aispringboot;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 user 表保留字修复是否生效。
 *
 * <p>在 PostgreSQL 中 user 是保留字，不带引号的 {@code SELECT * FROM user}
 * 不会报错，但查到的是系统关键字而非业务用户表。此用例直接对着库里既有的
 * 3 条账号做断言，表名引号写错时必定失败。
 */
@SpringBootTest
class UserMapperSmokeTest {

    @Resource
    private UserMapper userMapper;

    @Test
    void selectListShouldReadBusinessUserTable() {
        List<User> users = userMapper.selectList(null);
        assertEquals(3, users.size(), "user 表应读到 3 条既有账号，若数量不符说明表名引号丢失");
        assertTrue(users.stream().anyMatch(u -> "admin".equals(u.getUsername())),
                "应包含管理员账号 admin");
    }

    /**
     * 验证分页插件已生效。未注册 PaginationInnerInterceptor 时，
     * selectPage 会退化为全量查询且 total 为 0。
     */
    @Test
    void selectPageShouldApplyLimitAndCountTotal() {
        IPage<User> page = userMapper.selectPage(new Page<>(1, 2), null);
        assertEquals(3, page.getTotal(), "总数应为 3");
        assertEquals(2, page.getRecords().size(), "每页 2 条时首页应返回 2 条");
    }

}
