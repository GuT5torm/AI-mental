package com.example.aispringboot.entity;

//用户实体类
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体。
 *
 * <p>表名必须写成带双引号的形式：{@code "user"}。在 PostgreSQL 中 {@code user} 是保留字，
 * 不加引号的 {@code SELECT * FROM user} 不会报错，但查到的是系统关键字（返回当前登录用户名），
 * 而非业务用户表。这是一个静默错误，实测 {@code count(*) FROM user} 返回 1、
 * {@code count(*) FROM "user"} 返回 3。
 */
@Data
@TableName("\"user\"")
public class User {
    //用户id
    @TableId(type = IdType.AUTO)
    private Long id;

    //用户名
    @Size(min = 3, max = 50, message = "用户名长度必须在3到50之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    //邮箱，数据库可为空
    @Email(message = "邮箱格式错误")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    //手机号，数据库可为空
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    @Pattern(regexp = "^$|^[1][3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    //密码，BCrypt 密文，长度 60
    @Size(max = 255, message = "密码长度不能超过255个字符")
    private String password;

    //昵称，数据库可为空
    @Size(max = 100, message = "昵称长度不能超过100个字符")
    private String nickname;

    //头像
    @Size(max = 500, message = "头像地址长度不能超过500个字符")
    private String avatar;

    //性别
    private Integer gender;

    //生日
    private LocalDate birthday;

    //用户类型 1：普通用户 2：管理员
    private Integer userType;

    //状态 0：禁用 1：正常
    private Integer status;

    //真实模型开关 1：允许（全局 Key 对其生效） 0：强制演示模式，由管理员在后台维护
    private Integer aiRealEnabled;

    //创建时间，由数据库默认值维护
    private LocalDateTime createdAt;

    //更新时间，由数据库触发器 trg_user_updated_at 维护，不要在应用层写入
    private LocalDateTime updatedAt;

}
