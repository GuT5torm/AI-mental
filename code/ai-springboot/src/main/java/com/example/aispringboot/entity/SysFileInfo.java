package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传文件记录。
 *
 * <p>两个与常规表不同的地方：
 * <ul>
 *   <li>时间字段名为 create_time，不是 created_at。</li>
 *   <li>business_id 在库中为 char(255) 定长类型，读取时会带尾随空格
 *       （实测同一值 length() 为 36、octet_length() 为 255），
 *       写入与返回前必须 trim，否则会污染关联 ID。</li>
 * </ul>
 */
@Data
@TableName("sys_file_info")
public class SysFileInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String originalName;

    private String filePath;

    private Long fileSize;

    private String fileType;

    private String businessType;

    private String businessId;

    private String businessField;

    private Long uploadUserId;

    private Boolean isTemp;

    private Boolean status;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

}
