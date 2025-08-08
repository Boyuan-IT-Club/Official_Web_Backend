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
    
    // 头像存储目录
    private static final String AVATAR_DIR = "uploads/avatars/";
    
    /**
     * 上传头像文件
     * @param file 上传的文件
     * @return 文件存储路径
     * @throws IOException 文件操作异常
     */
    public static String uploadAvatar(MultipartFile file) throws IOException {
        // 检查文件是否为空
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        
        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("只允许上传图片文件");
        }
        
        // 创建上传目录
        File uploadDir = new File(AVATAR_DIR);
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
        Path filePath = Paths.get(AVATAR_DIR, uniqueFilename);
        Files.write(filePath, file.getBytes());
        
        // 返回相对路径
        return "/uploads/avatars/" + uniqueFilename;
    }
}