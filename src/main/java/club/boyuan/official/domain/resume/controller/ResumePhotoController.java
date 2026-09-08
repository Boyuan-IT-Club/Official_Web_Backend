package club.boyuan.official.domain.resume.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.resume.service.IResumePhotoService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.infra.storage.CosFile;
import club.boyuan.official.persistence.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * 简历个人照片：字节存 COS，personal_photo 字段值里只存 objectKey。
 *
 * 读取不走 /api/files —— 那条通道只对 avatars/activities/qrcodes 三个公开前缀
 * 放行（SecurityConfig）。照片是申请人的个人资料，与附件同一待遇：逐次鉴权。
 *
 * 上传返回的 objectKey 会经由前端写进字段值、随简历接口返回给能看到这份简历的人。
 * 这不同于附件 DTO 刻意隐藏 objectKey：照片的 key 本身就是字段值的引用形态，
 * 且拿到 key 也只能通过本控制器的鉴权接口读到内容。
 */
@Slf4j
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumePhotoController {

    private final IResumePhotoService photoService;
    private final IUserService userService;

    /** 学生上传自己简历的照片，返回 objectKey，由前端随字段保存流写入 personal_photo。 */
    @PostMapping("/{resumeId}/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<Map<String, String>>> upload(
            @PathVariable Integer resumeId,
            @RequestParam("file") MultipartFile file) {
        User me = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        String objectKey = photoService.upload(resumeId, me.getUserId(), file);
        return ResponseEntity.ok(ResponseMessage.success(Map.of("objectKey", objectKey)));
    }

    /**
     * 取照片内容（学生看自己的、管理端看候选人的，门槛与附件列表一致）。
     *
     * 带 private 缓存：管理端列表一页几十张卡片、翻页往返，重复拉照片没有意义。
     * 换照片后 key 会变，前端用字段值当 v 参数做 cache-busting，所以这里可以放心缓存。
     */
    @GetMapping("/{resumeId}/photo")
    @PreAuthorize("isAuthenticated()")
    public void content(@PathVariable Integer resumeId,
                        @RequestParam(name = "v", required = false) String ignoredCacheBuster,
                        HttpServletResponse response) throws IOException {
        CosFile file = photoService.openByResumeId(resumeId);
        if (file.contentType() != null) {
            response.setContentType(file.contentType());
        }
        if (file.contentLength() >= 0) {
            response.setContentLengthLong(file.contentLength());
        }
        response.setHeader("Cache-Control", "private, max-age=86400");
        // 上传侧只收位图，但仍与附件同款兜底：不嗅探、沙箱内渲染
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self' data:");
        response.setHeader("Content-Disposition", "inline");

        try (InputStream in = file.inputStream(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
