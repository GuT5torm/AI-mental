package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户管理行数据。不脱敏登录名但绝不含密码字段。
 */
@Data
public class UserAdminResponseDTO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;

    private Integer gender;

    /** 1普通用户 2管理员 */
    private Integer userType;

    /** 1正常 0禁用 */
    private Integer status;

    /** 真实模型开关 1允许 0强制演示模式 */
    private Integer aiRealEnabled;

    private LocalDateTime createdAt;
}
