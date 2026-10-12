package com.example.aispringboot.controller;

import com.example.aispringboot.DTO.command.UserLoginCommandDTO;
import com.example.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.example.aispringboot.DTO.response.UserLoginResponseDTO;
import com.example.aispringboot.common.Anonymous;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
