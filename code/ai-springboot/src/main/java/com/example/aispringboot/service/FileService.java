package com.example.aispringboot.service;

import com.example.aispringboot.DTO.response.FileUploadResponseDTO;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.common.UserContext;
import com.example.aispringboot.entity.SysFileInfo;
import com.example.aispringboot.mapper.SysFileInfoMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传。
 *
 * <p>落盘位置与库里既有记录保持一致：既有 {@code file_path} 形如
 * {@code /files/bussiness/article/1756963989972.png}，其中 bussiness 是原实现里的拼写错误
 * （多了一个 s）。前端 getImage() 直接拼接 fileBaseUrl 与 file_path，因此沿用这个拼写，
 * 否则历史封面全部 404。
 *
 * <p>安全约束：
 * <ul>
 *   <li>businessType 必须匹配 {@code [a-z0-9_-]{1,32}}，杜绝目录穿越；</li>
 *   <li>扩展名白名单校验，杜绝 .html/.svg 等可执行类型带来的存储型 XSS；</li>
 *   <li>UUID 命名不可预测，防止上传文件被枚举下载（/files/** 由静态资源映射直接读盘，
 *       不经鉴权拦截器，可预测性是唯一的访问屏障）。</li>
 * </ul>
 */
@Service
public class FileService {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.file.url-prefix:/files}")
    private String fileUrlPrefix;

    @Resource
    private SysFileInfoMapper sysFileInfoMapper;

    /** 允许落盘的扩展名白名单：图片 + 常规文档 + 音视频 + 纯文本。svg 不在其中（可携带脚本）。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
            "mp4", "webm", "mp3", "wav", "txt", "md");

    public FileUploadResponseDTO upload(MultipartFile file, String businessType,
                                        String businessId, String businessField) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_CONTENT_INVALID.getCode(), "上传文件为空");
        }

        String originalName = safeOriginalName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        if (!ext.isEmpty() && !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.FILE_CONTENT_INVALID.getCode(), "不支持的文件类型: " + ext);
        }
        String type = validateBusinessType(businessType);

        // UUID 命名：不可预测，防止文件被枚举下载；也避免原始文件名里的中文与特殊字符引发编码问题
        String fileName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        String relativeDir = "bussiness/" + type.toLowerCase(Locale.ROOT);
        Path dir = Paths.get(uploadDir, relativeDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            // 不用 MultipartFile.transferTo：Tomcat 的 Part.write 对相对路径处理不一致，
            // 实测在真实容器里会抛 IOException（MockMvc 下不会复现，只能靠 HTTP 验证发现）
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_SAVE_FAILED.getCode(), "文件保存失败");
        }

        String filePath = fileUrlPrefix + "/" + relativeDir + "/" + fileName;

        SysFileInfo info = new SysFileInfo();
        info.setOriginalName(originalName);
        info.setFilePath(filePath);
        info.setFileSize(file.getSize());
        info.setFileType(resolveFileType(file.getContentType()));
        info.setBusinessType(type);
        // business_id 是 char(255)，定长类型会补空格，写入前必须 trim
        info.setBusinessId(businessId == null ? null : businessId.trim());
        info.setBusinessField(businessField);
        info.setUploadUserId(UserContext.getUserId());
        info.setIsTemp(false);
        info.setStatus(true);
        sysFileInfoMapper.insert(info);

        SysFileInfo persisted = sysFileInfoMapper.selectById(info.getId());

        FileUploadResponseDTO dto = new FileUploadResponseDTO();
        dto.setId(persisted.getId());
        dto.setOriginalName(persisted.getOriginalName());
        dto.setFilePath(persisted.getFilePath());
        dto.setUrl(persisted.getFilePath());
        dto.setFileSize(persisted.getFileSize());
        dto.setFileType(persisted.getFileType());
        dto.setBusinessType(persisted.getBusinessType());
        // 读回来时会带 char(255) 的尾随空格
        dto.setBusinessId(persisted.getBusinessId() == null ? null : persisted.getBusinessId().trim());
        dto.setBusinessField(persisted.getBusinessField());
        dto.setCreateTimeValue(persisted.getCreateTime() == null ? LocalDateTime.now() : persisted.getCreateTime());
        return dto;
    }

    /**
     * businessType 用于拼接磁盘目录，必须限定为安全的目录名字符集。
     * 不做这个校验的话，"../.." 之类的输入会在 normalize 后跳出上传根目录。
     * 校验按小写比对，返回值保留原大小写入库（既有记录形如 ARTICLE / USER_AVATAR）。
     */
    private static String validateBusinessType(String businessType) {
        String type = businessType == null || businessType.isBlank() ? "COMMON" : businessType.trim();
        if (!type.toLowerCase(Locale.ROOT).matches("[a-z0-9_-]{1,32}")) {
            throw new BusinessException(ResultCode.FILE_CONTENT_INVALID.getCode(), "上传业务类型不合法");
        }
        return type;
    }

    private static String safeOriginalName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        // 只取文件名部分，丢掉客户端可能带来的路径
        String cleaned = name.replace("\\", "/");
        int idx = cleaned.lastIndexOf('/');
        if (idx >= 0) {
            cleaned = cleaned.substring(idx + 1);
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private static String extensionOf(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        String ext = name.substring(idx + 1).toLowerCase(Locale.ROOT);
        // 只保留字母数字，杜绝 ../ 之类的路径穿越
        if (!ext.matches("[a-z0-9]{1,10}")) {
            return "";
        }
        return ext;
    }

    private static String resolveFileType(String contentType) {
        if (contentType == null) {
            return "OTHER";
        }
        if (contentType.startsWith("image/")) {
            return "IMG";
        }
        if (contentType.startsWith("video/")) {
            return "VIDEO";
        }
        if (contentType.startsWith("audio/")) {
            return "AUDIO";
        }
        if (contentType.contains("pdf")) {
            return "PDF";
        }
        return "OTHER";
    }
}
