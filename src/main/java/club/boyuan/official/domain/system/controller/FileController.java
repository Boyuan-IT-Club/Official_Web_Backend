package club.boyuan.official.domain.system.controller;

import club.boyuan.official.infra.storage.CosFile;
import club.boyuan.official.infra.storage.CosStorageService;
import com.qcloud.cos.exception.CosServiceException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件读取中转接口：浏览器不直连 COS 私有桶，统一走后端流式返回。
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final CosStorageService cosStorageService;

    @GetMapping("/api/files/{prefix}/{filename}")
    public void download(
            @PathVariable String prefix,
            @PathVariable String filename,
            HttpServletResponse response) throws IOException {
        if (!cosStorageService.isEnabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }

        String objectKey = prefix + "/" + filename;
        try {
            CosFile cosFile = cosStorageService.open(objectKey);
            if (cosFile.contentType() != null) {
                response.setContentType(cosFile.contentType());
            }
            if (cosFile.contentLength() >= 0) {
                response.setContentLengthLong(cosFile.contentLength());
            }
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");

            try (InputStream input = cosFile.inputStream();
                 OutputStream output = response.getOutputStream()) {
                input.transferTo(output);
            }
        } catch (CosServiceException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
                return;
            }
            logger.error("读取 COS 文件失败，objectKey: {}", objectKey, e);
            throw new IOException("读取文件失败", e);
        }
    }
}
