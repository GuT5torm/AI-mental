package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台编辑用户信息：昵称/邮箱/电话/性别/角色。不含密码，改密走独立的重置接口。
 */
@Data
public class UserAdminUpdateCommandDTO {

    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    /** 角色：1普通用户 2管理员 */
    private Integer userType;

    /** 真实模型开关：1允许（全局 Key 对其生效） 0强制演示模式 */
    private Integer aiRealEnabled;
}
