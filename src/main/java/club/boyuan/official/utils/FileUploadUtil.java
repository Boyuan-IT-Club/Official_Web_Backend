package club.boyuan.official.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件上传工具类
 */
public class FileUploadUtil {
    
    // 默认存储目录
    private static final String DEFAULT_UPLOAD_DIR = "uploads/";
    
    /**
     * 上传头像文件（兼容旧方法）
     * @param file 上传的文件
     * @return 文件存储路径
     * @throws IOException 文件操作异常
     */
    public static String uploadAvatar(MultipartFile file) throws IOException {
        return uploadFile(file, "avatars/");
    }
    
    /**
     * 通用文件上传方法
     * @param file 上传的文件
     * @param uploadPath 上传路径（相对于默认上传目录）
     * @return 文件存储路径
     * @throws IOException 文件操作异常
     */
    public static String uploadFile(MultipartFile file, String uploadPath) throws IOException {
        // 检查文件是否为空
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        
        // 标准化路径，防止路径穿越
        String normalizedPath = Paths.get(uploadPath).normalize().toString();
        if (normalizedPath.startsWith("..") || normalizedPath.startsWith("/")) {
            throw new IOException("非法的上传路径");
        }
        
        // 创建完整上传目录路径
        String fullUploadPath = DEFAULT_UPLOAD_DIR + normalizedPath;
        if (!fullUploadPath.endsWith("/")) {
            fullUploadPath += "/";
        }
        
        // 创建上传目录
        File uploadDir = new File(fullUploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
        
        // 保存文件
        Path filePath = Paths.get(fullUploadPath, uniqueFilename);
        Files.write(filePath, file.getBytes());
        
        // 返回相对路径
        return "/" + fullUploadPath + uniqueFilename;
    }
    
    /**
     * 上传文件并验证文件类型
     * @param file 上传的文件
     * @param uploadPath 上传路径（相对于默认上传目录）
     * @param allowedTypes 允许的文件类型（如 "image/" 表示只允许图片）
     * @return 文件存储路径
     * @throws IOException 文件操作异常
     */
    public static String uploadFile(MultipartFile file, String uploadPath, String allowedTypes) throws IOException {
        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith(allowedTypes)) {
            throw new IOException("不允许上传此类型的文件");
        }
        
        return uploadFile(file, uploadPath);
    }
}