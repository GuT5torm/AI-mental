package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册入参。
 *
 * <p>前端 register.vue 只在昵称/手机号非空时才提交这两个字段，因此二者允许为空。
 * userType 由前端传入但后端不使用——注册入口固定写入普通用户，避免提权。
 */
@Data
public class UserRegisterCommandDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3到50之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在6到50个字符之间")
    private String password;

    @Size(max = 100, message = "昵称长度不能超过100个字符")
    private String nickname;

    @Size(max = 20, message = "手机号长度不能超过20个字符")
    @Pattern(regexp = "^$|^[1][3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;
}
