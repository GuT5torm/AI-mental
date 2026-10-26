package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 后台用户管理分页查询：关键词匹配用户名/昵称/邮箱，可选状态过滤。
 */
@Data
public class UserAdminPageQuery extends PageQuery {

    /** 关键词：模糊匹配用户名/昵称/邮箱 */
    private String keyword;

    /** 状态过滤：1=正常 0=禁用，空为全部 */
    private Integer status;
}
