package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.Activity;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IActivityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 活动管理控制器
 * 提供活动相关的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    @Autowired
    private IActivityService activityService;

    /**
     * 获取所有活动
     */
    @GetMapping
    public ResponseEntity<ResponseMessage<List<Activity>>> getAllActivities(HttpServletRequest request) {
        try {
            log.info("获取所有活动，用户IP: {}", request.getRemoteAddr());
            List<Activity> activities = activityService.getAllActivities();
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
            Activity activity = activityService.getActivityById(id);
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
            Activity createdActivity = activityService.createActivity(activity);
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
            Activity updatedActivity = activityService.updateActivity(activity);
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
            List<Activity> activities = activityService.getActivitiesByCategory(category);
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
            List<Activity> activities = activityService.getActiveActivities();
            log.info("获取进行中的活动成功，活动数量: {}", activities.size());
            return ResponseEntity.ok(ResponseMessage.success(activities));
        } catch (BusinessException e) {
            log.error("获取进行中的活动失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error(e.getCode(), e.getMessage()));
        }
    }
}
