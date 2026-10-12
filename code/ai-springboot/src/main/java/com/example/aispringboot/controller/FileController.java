package com.example.aispringboot.controller;

import com.example.aispringboot.DTO.response.FileUploadResponseDTO;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.service.FileService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传。表单字段名与前端 ArticleDialog 一致：file / businessType / businessId / businessField。
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Resource
    private FileService fileService;

    @PostMapping("/upload")
    public Result<FileUploadResponseDTO> upload(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "businessType", required = false) String businessType,
                                                @RequestParam(value = "businessId", required = false) String businessId,
                                                @RequestParam(value = "businessField", required = false) String businessField) {
        return Result.ok(fileService.upload(file, businessType, businessId, businessField));
    }
}
