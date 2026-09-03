package club.boyuan.official.domain.resume.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.resume.dto.ResumeAttachmentDTO;
import club.boyuan.official.domain.resume.service.IResumeAttachmentService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.infra.storage.CosFile;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.persistence.entity.ResumeAttachment;
import club.boyuan.official.persistence.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 简历附件：学生上传任意格式资料，面试官在管理端预览或下载。
 *
 * 取文件走本控制器而不是 /api/files —— 那条通道是给公开图片用的
 * （头像、活动图，在 SecurityConfig 里白名单放行）。附件是申请人的个人资料，
 * 必须逐次鉴权，绝不能进那份白名单。
 */
@Slf4j
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeAttachmentController {

    private final IResumeAttachmentService attachmentService;
    private final IUserService userService;

    /** 学生上传附件到自己的简历。 */
    @PostMapping("/{resumeId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<ResumeAttachmentDTO>> upload(
            @PathVariable Integer resumeId,
            @RequestParam("file") MultipartFile file) {
        User me = currentUser();
        return ResponseEntity.ok(ResponseMessage.success(
                attachmentService.upload(resumeId, me.getUserId(), file)));
    }

    /**
     * 列出某份简历的附件。
     *
     * 学生看自己的、面试官/管理员看候选人的，都走这一个接口。
     * 学生看不到别人的简历 id，而管理端本来就能看到候选人简历，
     * 所以这里只要求登录；真正的闸门在上传与删除（只能动自己的）。
     */
    @GetMapping("/{resumeId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<ResumeAttachmentDTO>>> list(
            @PathVariable Integer resumeId) {
        return ResponseEntity.ok(ResponseMessage.success(attachmentService.listByResume(resumeId)));
    }

    /** 删除自己上传的附件。 */
    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> delete(@PathVariable Integer id) {
        User me = currentUser();
        attachmentService.delete(id, me.getUserId());
        return ResponseEntity.ok(ResponseMessage.success(null));
    }

    /**
     * 取附件内容。inline=true 时尝试内联预览（管理端不下载直接看）。
     *
     * 内联与否不由调用方说了算：附件是申请人上传的、内容不可信，而本接口与
     * 站点同源。若把 text/html 或 image/svg+xml 内联返回，上传者就能在
     * 管理端的源上执行脚本、读走面试官的登录态——存储型 XSS。
     * 所以只有服务端认定安全的类型才内联，其余一律强制下载。
     */
    @GetMapping("/attachments/{id}/content")
    @PreAuthorize("isAuthenticated()")
    public void content(@PathVariable Integer id,
                        @RequestParam(name = "inline", defaultValue = "false") boolean inline,
                        HttpServletResponse response) throws IOException {
        ResumeAttachment a = attachmentService.getOrThrow(id);
        boolean canInline = inline && attachmentService.previewable(a.getContentType(), a.getFileName());

        CosFile file = attachmentService.open(a);
        if (file.contentType() != null) {
            response.setContentType(file.contentType());
        }
        if (file.contentLength() >= 0) {
            response.setContentLengthLong(file.contentLength());
        }

        // nosniff 不能省：不加的话浏览器会去嗅探内容，把一个声称 text/plain
        // 的文件当 HTML 执行，绕过上面那道类型判断
        response.setHeader("X-Content-Type-Options", "nosniff");
        // 即便内联，也在沙箱里渲染：禁掉脚本、表单与同源访问
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self' data:; media-src 'self'");
        response.setHeader("Content-Disposition",
                (canInline ? "inline" : "attachment") + "; filename*=UTF-8''"
                        + URLEncoder.encode(a.getFileName(), StandardCharsets.UTF_8).replace("+", "%20"));

        try (InputStream in = file.inputStream(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private User currentUser() {
        return userService.getUserByUsername(SecurityUtil.getCurrentUsername());
    }
}
