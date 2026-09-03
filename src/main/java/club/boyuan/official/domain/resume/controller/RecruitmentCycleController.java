package club.boyuan.official.domain.resume.controller;

import club.boyuan.official.domain.resume.dto.OpenCycleDTO;
import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.common.utils.SecurityUtil;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 招募周期Controller
 */
@RestController
@RequestMapping("/api/cycles")
@AllArgsConstructor
public class RecruitmentCycleController {
    
    private static final Logger logger = LoggerFactory.getLogger(RecruitmentCycleController.class);
    
    private final IRecruitmentCycleService recruitmentCycleService;
    private final IUserService userService;
    
    /**
     * 创建招募周期（仅管理员）
     */
    @PostMapping
    @PreAuthorize("hasAuthority('cycle:manage')")
    public ResponseEntity<ResponseMessage<RecruitmentCycle>> createRecruitmentCycle(@RequestBody RecruitmentCycle recruitmentCycle) {
        try {
            logger.info("创建招募周期");
            RecruitmentCycle createdCycle = recruitmentCycleService.createRecruitmentCycle(recruitmentCycle);
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期创建成功", createdCycle));
        } catch (BusinessException e) {
            logger.warn("创建招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("创建招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期创建失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 更新招募周期（仅管理员）
     */
    @PutMapping
    @PreAuthorize("hasAuthority('cycle:manage')")
    public ResponseEntity<ResponseMessage<RecruitmentCycle>> updateRecruitmentCycle(@RequestBody RecruitmentCycle recruitmentCycle) {
        try {
            logger.info("更新招募周期，ID: {}", recruitmentCycle.getCycleId());
            RecruitmentCycle updatedCycle = recruitmentCycleService.updateRecruitmentCycle(recruitmentCycle);
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期更新成功", updatedCycle));
        } catch (BusinessException e) {
            logger.warn("更新招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("更新招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期更新失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 删除招募周期（仅管理员）
     */
    @DeleteMapping("/{cycleId}")
    @PreAuthorize("hasAuthority('cycle:manage')")
    public ResponseEntity<ResponseMessage<String>> deleteRecruitmentCycle(@PathVariable Integer cycleId) {
        try {
            logger.info("删除招募周期，ID: {}", cycleId);
            recruitmentCycleService.deleteRecruitmentCycle(cycleId);
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期删除成功", "招募周期删除成功"));
        } catch (BusinessException e) {
            logger.warn("删除招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("删除招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期删除失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据ID获取招募周期
     */
    @GetMapping("/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<RecruitmentCycle>> getRecruitmentCycleById(@PathVariable Integer cycleId) {
        try {
            logger.debug("获取招募周期，ID: {}", cycleId);
            RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleById(cycleId);
            if (cycle == null) {
                logger.warn("招募周期不存在，ID: {}", cycleId);
                throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
            }
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期成功", cycle));
        } catch (BusinessException e) {
            logger.warn("获取招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 获取所有招募周期
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<RecruitmentCycle>>> getAllRecruitmentCycles() {
        try {
            logger.debug("获取所有招募周期");
            List<RecruitmentCycle> cycles = recruitmentCycleService.getAllRecruitmentCycles();
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期列表成功", cycles));
        } catch (BusinessException e) {
            logger.warn("获取招募周期列表业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("获取招募周期列表系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期列表失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据状态获取招募周期
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<RecruitmentCycle>>> getRecruitmentCyclesByStatus(@PathVariable Integer status) {
        try {
            logger.debug("根据状态获取招募周期，状态: {}", status);
            List<RecruitmentCycle> cycles = recruitmentCycleService.getRecruitmentCyclesByStatus(status);
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期列表成功", cycles));
        } catch (BusinessException e) {
            logger.warn("根据状态获取招募周期列表业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("根据状态获取招募周期列表系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期列表失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据是否启用获取招募周期
     */
    /**
     * 当前开放投递的周期列表(用户端周期选择器用)。
     *
     * 与 /active/{isActive} 的区别：is_active 的语义只是「是否启用」，往届周期
     * 为了能查历史简历通常也保持启用，所以「启用中」不等于「现在能投」。
     * 用户端此前直接取 /active/1 的第一条，同时有两个启用周期时就会任选一个 ——
     * 若选中的那个还没配简历字段，投递页会渲染成一张零字段的空表单，
     * 且前后端都不报错，表现就是「用户端显示不出来」。
     *
     * 本接口以起止日期为权威(管理端唯一真正维护的字段)，不看 status 列：
     * status 只有一个手动管理接口会刷新，没有定时任务，实际长期陈旧。
     */
    @GetMapping("/open")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<OpenCycleDTO>>> getOpenCycles() {
        List<OpenCycleDTO> cycles = recruitmentCycleService.getOpenCyclesForApplication();
        logger.debug("返回当前开放投递的周期，共{}个", cycles.size());
        return ResponseEntity.ok(ResponseMessage.success(cycles));
    }

    /**
     * 即将开放的周期（用户端预告用）。
     *
     * 与 /open 分开而不是并成一个列表：前端拿 /open 的 id 集合当「能不能投」的闸门，
     * 未开始的混进去，用户就能给一个还没开始的周期提交简历。
     * 可见与可投是两件事——未开始的周期该看得到（预告），但不该能投。
     */
    @GetMapping("/upcoming")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<OpenCycleDTO>>> getUpcomingCycles() {
        List<OpenCycleDTO> cycles = recruitmentCycleService.getUpcomingCyclesForApplication();
        logger.debug("返回即将开放的周期，共{}个", cycles.size());
        return ResponseEntity.ok(ResponseMessage.success(cycles));
    }

    @GetMapping("/active/{isActive}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<RecruitmentCycle>>> getRecruitmentCyclesByIsActive(@PathVariable Integer isActive) {
        try {
            logger.debug("根据是否启用获取招募周期，是否启用: {}", isActive);
            List<RecruitmentCycle> cycles = recruitmentCycleService.getRecruitmentCyclesByIsActive(isActive);
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期列表成功", cycles));
        } catch (BusinessException e) {
            logger.warn("根据是否启用获取招募周期列表业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("根据是否启用获取招募周期列表系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期列表失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据学年获取招募周期
     */
    @GetMapping("/academic-year/{academicYear}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<RecruitmentCycle>> getRecruitmentCycleByAcademicYear(@PathVariable String academicYear) {
        try {
            logger.debug("根据学年获取招募周期，学年: {}", academicYear);
            RecruitmentCycle cycle = recruitmentCycleService.getRecruitmentCycleByAcademicYear(academicYear);
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期成功", cycle));
        } catch (BusinessException e) {
            logger.warn("根据学年获取招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("根据学年获取招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 批量删除招募周期（仅管理员）
     */
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('cycle:manage')")
    public ResponseEntity<ResponseMessage<String>> deleteRecruitmentCycles(@RequestBody List<Integer> cycleIds) {
        try {
            // 验证管理员权限
            User currentUser = getCurrentUser();
            // 注意：不再使用 checkAdminPermission 进行检查
            // 权限检查由 @PreAuthorize 注解统一管理
            
            logger.info("管理员{}批量删除招募周期，IDs: {}", currentUser.getUsername(), cycleIds);
            recruitmentCycleService.deleteRecruitmentCycles(cycleIds);
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期批量删除成功", "招募周期批量删除成功"));
        } catch (BusinessException e) {
            logger.warn("批量删除招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("批量删除招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期批量删除失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 批量更新招募周期（仅管理员）
     */
    @PutMapping("/batch")
    @PreAuthorize("hasAuthority('cycle:manage')")
    public ResponseEntity<ResponseMessage<String>> updateRecruitmentCycles(@RequestBody List<RecruitmentCycle> recruitmentCycles) {
        try {
            // 验证管理员权限
            User currentUser = getCurrentUser();
            // 注意：不再使用 checkAdminPermission 进行检查
            // 权限检查由 @PreAuthorize 注解统一管理
            
            logger.info("管理员{}批量更新招募周期，数量: {}", currentUser.getUsername(), recruitmentCycles.size());
            recruitmentCycleService.updateRecruitmentCycles(recruitmentCycles);
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期批量更新成功", "招募周期批量更新成功"));
        } catch (BusinessException e) {
            logger.warn("批量更新招募周期业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("批量更新招募周期系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期批量更新失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据当前时间自动更新招募周期状态（仅管理员）
     */
    @PostMapping("/update-statuses")
    @PreAuthorize("hasAuthority('cycle:manage')")
    public ResponseEntity<ResponseMessage<String>> updateRecruitmentCycleStatusesBasedOnDate() {
        try {
            // 验证管理员权限
            User currentUser = getCurrentUser();
            // 注意：不再使用 checkAdminPermission 进行检查
            // 权限检查由 @PreAuthorize 注解统一管理
            
            logger.info("管理员{}根据当前时间更新招募周期状态", currentUser.getUsername());
            recruitmentCycleService.updateRecruitmentCycleStatusesBasedOnDate(LocalDate.now());
            return ResponseEntity.ok(new ResponseMessage<>(200, "招募周期状态更新成功", "招募周期状态更新成功"));
        } catch (BusinessException e) {
            logger.warn("根据当前时间更新招募周期状态业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("根据当前时间更新招募周期状态系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "招募周期状态更新失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 分页获取所有招募周期
     */
    @GetMapping("/page")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<PageResultDTO<RecruitmentCycle>>> getAllRecruitmentCyclesWithPagination(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "cycleId") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        try {
            logger.debug("分页获取所有招募周期，页码: {}, 大小: {}, 排序字段: {}, 排序顺序: {}", page, size, sortBy, sortOrder);
            PageResultDTO<RecruitmentCycle> pageResult = recruitmentCycleService.getAllRecruitmentCyclesWithPagination(page, size, sortBy, sortOrder);
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期列表成功", pageResult));
        } catch (BusinessException e) {
            logger.warn("分页获取招募周期列表业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("分页获取招募周期列表系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期列表失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 根据条件分页查询招募周期
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<PageResultDTO<RecruitmentCycle>>> getRecruitmentCyclesByConditions(
            @RequestParam(required = false) String cycleName,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "cycleId") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder) {
        try {
            logger.debug("根据条件分页查询招募周期，名称: {}, 学年: {}, 状态: {}, 是否启用: {}, 页码: {}, 大小: {}, 排序字段: {}, 排序顺序: {}", 
                    cycleName, academicYear, status, isActive, page, size, sortBy, sortOrder);
            PageResultDTO<RecruitmentCycle> pageResult = recruitmentCycleService.getRecruitmentCyclesByConditions(
                    cycleName, academicYear, status, isActive, page, size, sortBy, sortOrder);
            return ResponseEntity.ok(new ResponseMessage<>(200, "获取招募周期列表成功", pageResult));
        } catch (BusinessException e) {
            logger.warn("根据条件分页查询招募周期列表业务异常，错误码: {}，错误信息: {}", e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            logger.error("根据条件分页查询招募周期列表系统异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                            "获取招募周期列表失败: " + e.getMessage(), null));
        }
    }
    
    /**
     * 获取当前登录用户。令牌解析与校验已由 JwtAuthenticationFilter 完成，此处只从 SecurityContext 读取。
     */
    private User getCurrentUser() {
        String username = SecurityUtil.getCurrentUsername();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.JWT_VERIFICATION_FAILED);
        }
        return user;
    }
}