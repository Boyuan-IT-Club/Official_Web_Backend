package club.boyuan.official.domain.feedback.controller;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.feedback.dto.FeedbackCreateRequest;
import club.boyuan.official.domain.feedback.service.IFeedbackService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.Feedback;
import club.boyuan.official.persistence.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {

    /** 单张截图上限。反馈是给人看的，不是传作品集的地方 */
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final IFeedbackService feedbackService;
    private final IUserService userService;
    private final CosStorageService cosStorageService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<Feedback>> create(@Valid @RequestBody FeedbackCreateRequest request) {
        return ResponseEntity.ok(ResponseMessage.success(feedbackService.create(
                currentUserId(), request.getCategory(), request.getContent(), request.getImageKeys())));
    }

    /**
     * 先传图拿 objectKey，再随反馈一起提交。
     *
     * 分两步而不是一次 multipart 提交：用户往往先截图、再慢慢描述问题，
     * 一次性提交意味着写到一半刷新就全丢了；分开传，图先落盘。
     * 代价是可能留下没被引用的孤儿图——比丢用户内容划算得多。
     */
    @PostMapping("/images")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<Map<String, String>>> uploadImage(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "请选择图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(BusinessExceptionEnum.ILLEGAL_ARGUMENT, "单张截图不能超过 5MB");
        }
        try {
            // imageOnly 走默认的 true：反馈只收图，不做第二个附件入口
            String key = cosStorageService.upload(file, "feedback/");
            return ResponseEntity.ok(ResponseMessage.success(Map.of("imageKey", key)));
        } catch (IOException e) {
            log.warn("反馈截图上传失败", e);
            throw new BusinessException(BusinessExceptionEnum.ILLEGAL_ARGUMENT, "图片上传失败：" + e.getMessage());
        }
    }

    /**
     * 按序号取截图。不暴露 objectKey，逐次鉴权——反馈截图常带个人信息，
     * 不能靠「猜不到路径」当防线。
     */
    @GetMapping("/{feedbackId}/images/{index}")
    @PreAuthorize("isAuthenticated()")
    public void image(@PathVariable Long feedbackId, @PathVariable int index, HttpServletResponse response) {
        String key = feedbackService.imageKeyFor(feedbackId, index, currentUserId(), hasFeedbackView());
        try (InputStream in = cosStorageService.download(key)) {
            response.setContentType("image/*");
            // 反馈截图不该被当成脚本执行，也不该被浏览器猜类型
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, max-age=300");
            in.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.warn("读取反馈截图失败 feedbackId={}, index={}", feedbackId, index, e);
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException ignored) {
                // 响应已经写了一半，没法再改状态码
            }
        }
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<PageResultDTO<Feedback>>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ResponseMessage.success(
                feedbackService.pageMine(currentUserId(), safePage(page), safeSize(size))));
    }

    private boolean hasFeedbackView() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "feedback:view".equals(a.getAuthority()));
    }

    private Integer currentUserId() {
        User user = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_LOGIN);
        }
        return user.getUserId();
    }

    private int safePage(int page) { return Math.max(page, 0); }
    private int safeSize(int size) { return Math.min(Math.max(size, 1), 100); }
}
