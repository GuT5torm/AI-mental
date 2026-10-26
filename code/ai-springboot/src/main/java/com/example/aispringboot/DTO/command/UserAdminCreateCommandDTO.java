package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台新增用户命令。密码留空时使用默认初始密码 Abc@123456。
 */
@Data
public class UserAdminCreateCommandDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 20, message = "用户名长度不能超过20")
    private String username;

    /** 选填，留空使用默认初始密码 Abc@123456 */
    @Size(min = 6, max = 32, message = "密码长度需在6-32之间")
    private String password;

    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    /** 角色：1普通用户 2管理员，默认普通用户 */
    private Integer userType;
}
