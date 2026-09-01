package club.boyuan.official.domain.activity.controller;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.utils.FileUploadUtil;
import club.boyuan.official.persistence.entity.Activity;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.activity.service.IActivityService;
import club.boyuan.official.infra.storage.CosStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 活动管理控制器
 * 提供活动相关的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final IActivityService activityService;

    private final CosStorageService cosStorageService;

    /**
     * 上传活动图片（封面或正文插图），返回可直接引用的 URL。
     * 与头像上传同一套存储：COS 未配置时降级本地磁盘，保证本地开发与 CI 可用。
     * 图片与活动记录不强关联——正文里的图以 URL 内嵌，删活动不追删对象，避免误伤被多处引用的图。
     */
    @PostMapping("/image")
    @PreAuthorize("hasAuthority('activity:manage')")
    public ResponseEntity<ResponseMessage<?>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(ResponseMessage.error(400, "上传文件为空"));
            }
            String stored = cosStorageService.isEnabled()
                    ? cosStorageService.upload(file, "activities/")
                    : FileUploadUtil.uploadFile(file, "activities/", "image/");
            String url = cosStorageService.resolvePublicUrl(stored);
            log.info("活动图片上传成功，存储值: {}，访问地址: {}", stored, url);
            return ResponseEntity.ok(ResponseMessage.success(Map.of("url", url, "objectKey", stored)));
        } catch (IOException e) {
            log.error("活动图片上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(500, "图片上传失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有活动
     */
    @GetMapping
    public ResponseEntity<ResponseMessage<List<Activity>>> getAllActivities(HttpServletRequest request) {
        try {
            log.info("获取所有活动，用户IP: {}", request.getRemoteAddr());
            List<Activity> activities = resolveImageUrls(activityService.getAllActivities());
            log.info("获取所有活动成功，活动数量: {}", activities.size());
            return ResponseEntity.ok(ResponseMessage.success(activities));
        } catch (BusinessException e) {
            log.error("获取活动列表失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 根据ID获取活动详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseMessage<Activity>> getActivityById(@PathVariable Integer id, HttpServletRequest request) {
        try {
            log.info("根据ID获取活动，活动ID: {}，用户IP: {}", id, request.getRemoteAddr());
            Activity activity = resolveImageUrl(activityService.getActivityById(id));
            log.info("获取活动成功，活动标题: {}", activity.getTitle());
            return ResponseEntity.ok(ResponseMessage.success(activity));
        } catch (BusinessException e) {
            log.error("获取活动失败，活动ID: {}，错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 创建活动
     */
    @PostMapping
    @PreAuthorize("hasAuthority('activity:manage')")
    public ResponseEntity<ResponseMessage<Activity>> createActivity(@RequestBody Activity activity, HttpServletRequest request) {
        try {
            log.info("创建活动，活动标题: {}，用户IP: {}", activity.getTitle(), request.getRemoteAddr());
            Activity createdActivity = resolveImageUrl(activityService.createActivity(activity));
            log.info("成功创建活动，活动ID: {}", createdActivity.getActivityId());
            return ResponseEntity.ok(ResponseMessage.success(createdActivity));
        } catch (BusinessException e) {
            log.error("创建活动失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 更新活动
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('activity:manage')")
    public ResponseEntity<ResponseMessage<Activity>> updateActivity(
            @PathVariable Integer id,
            @RequestBody Activity activity,
            HttpServletRequest request) {
        try {
            log.info("更新活动，活动ID: {}，用户IP: {}", id, request.getRemoteAddr());
            activity.setActivityId(id); // 确保ID一致
            Activity updatedActivity = resolveImageUrl(activityService.updateActivity(activity));
            log.info("成功更新活动，活动标题: {}", updatedActivity.getTitle());
            return ResponseEntity.ok(ResponseMessage.success(updatedActivity));
        } catch (BusinessException e) {
            log.error("更新活动失败，活动ID: {}，错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 删除活动
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('activity:manage')")
    public ResponseEntity<ResponseMessage<Void>> deleteActivity(@PathVariable Integer id, HttpServletRequest request) {
        try {
            log.info("删除活动，活动ID: {}，用户IP: {}", id, request.getRemoteAddr());
            boolean result = activityService.deleteActivity(id);
            if (result) {
                log.info("成功删除活动，活动ID: {}", id);
                return ResponseEntity.ok(ResponseMessage.success(null));
            } else {
                log.warn("删除活动失败，活动ID: {}，活动不存在", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ResponseMessage.error(
                                BusinessExceptionEnum.ACTIVITY_NOT_FOUND.getCode(),
                                "删除失败，活动不存在"
                        ));
            }
        } catch (BusinessException e) {
            log.error("删除活动失败，活动ID: {}，错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 根据分类获取活动
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<ResponseMessage<List<Activity>>> getActivitiesByCategory(
            @PathVariable String category,
            HttpServletRequest request) {
        try {
            log.info("根据分类获取活动，分类: {}，用户IP: {}", category, request.getRemoteAddr());
            List<Activity> activities = resolveImageUrls(activityService.getActivitiesByCategory(category));
            log.info("根据分类获取活动成功，分类: {}，活动数量: {}", category, activities.size());
            return ResponseEntity.ok(ResponseMessage.success(activities));
        } catch (BusinessException e) {
            log.error("根据分类获取活动失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 获取进行中的活动
     */
    @GetMapping("/active")
    public ResponseEntity<ResponseMessage<List<Activity>>> getActiveActivities(HttpServletRequest request) {
        try {
            log.info("获取进行中的活动，用户IP: {}", request.getRemoteAddr());
            List<Activity> activities = resolveImageUrls(activityService.getActiveActivities());
            log.info("获取进行中的活动成功，活动数量: {}", activities.size());
            return ResponseEntity.ok(ResponseMessage.success(activities));
        } catch (BusinessException e) {
            log.error("获取进行中的活动失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 数据库允许保存稳定的 COS objectKey，也兼容已经保存的完整 URL 或本地路径；
     * 对外响应统一转换成浏览器可直接访问的地址。
     */
    private Activity resolveImageUrl(Activity activity) {
        if (activity != null) {
            activity.setCoverImage(cosStorageService.resolvePublicUrl(activity.getCoverImage()));
        }
        return activity;
    }

    private List<Activity> resolveImageUrls(List<Activity> activities) {
        activities.forEach(this::resolveImageUrl);
        return activities;
    }
}
