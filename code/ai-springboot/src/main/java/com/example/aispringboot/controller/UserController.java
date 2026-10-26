package com.example.aispringboot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.aispringboot.DTO.command.UserAdminCreateCommandDTO;
import com.example.aispringboot.DTO.command.UserAdminPageQuery;
import com.example.aispringboot.DTO.command.UserAdminUpdateCommandDTO;
import com.example.aispringboot.DTO.command.UserLoginCommandDTO;
import com.example.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.example.aispringboot.DTO.response.UserAdminResponseDTO;
import com.example.aispringboot.DTO.response.UserLoginResponseDTO;
import com.example.aispringboot.common.Anonymous;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证。
 *
 * <p>类名带 Controller 后缀，与 entity.User 区分：两者同名不同包时，
 * 同文件里同时引用会让编译器要求写全限定名，注入点也容易选错。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    @Anonymous
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO) {
        return userService.login(commandDTO);
    }

    /**
     * 注册。
     *
     * <p>两个路径都映射：前端 api/frontend.js 用的是 /user/add，
     * 而 /user/register 是同类项目里更常见的写法，留着别名省一次来回改。
     */
    @Anonymous
    @PostMapping({"/add", "/register"})
    public Result<UserLoginResponseDTO> register(@Valid @RequestBody UserRegisterCommandDTO commandDTO) {
        return userService.register(commandDTO);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return userService.logout();
    }

    // ==================== 后台用户管理 ====================

    /** 后台直接新增用户（密码留空使用默认初始密码 Abc@123456） */
    @PostMapping("/admin/create")
    public Result<UserAdminResponseDTO> adminCreate(@Valid @RequestBody UserAdminCreateCommandDTO command) {
        return Result.ok(userService.adminCreate(command));
    }

    /** 分页查询用户（关键词/状态筛选） */
    @GetMapping("/admin/page")
    public Result<IPage<UserAdminResponseDTO>> adminPage(UserAdminPageQuery query) {
        return Result.ok(userService.adminPage(query));
    }

    /** 启用/禁用用户 */
    @PutMapping("/admin/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.ok();
    }

    /** 编辑用户资料（昵称/邮箱/电话/性别/角色） */
    @PutMapping("/admin/{id}")
    public Result<Void> updateByAdmin(@PathVariable Long id,
                                      @Valid @RequestBody UserAdminUpdateCommandDTO command) {
        userService.updateByAdmin(id, command);
        return Result.ok();
    }

    /** 重置密码为初始值 Abc@123456 */
    @PutMapping("/admin/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.ok();
    }
}
