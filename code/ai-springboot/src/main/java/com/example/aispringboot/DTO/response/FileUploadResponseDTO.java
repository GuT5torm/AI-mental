package com.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 上传结果。前端 ArticleDialog 只取 filePath，随后拼 fileBaseUrl 显示封面。
 */
@Data
public class FileUploadResponseDTO {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String originalName;
    /** 形如 /files/bussiness/article/1756963989972.png，前端直接拼 fileBaseUrl */
    private String filePath;
    private String url;
    private Long fileSize;
    private String fileType;
    private String businessType;
    private String businessId;
    private String businessField;
    private String createTime;

    public void setCreateTimeValue(LocalDateTime t) {
        this.createTime = t == null ? null : FMT.format(t);
    }
}
