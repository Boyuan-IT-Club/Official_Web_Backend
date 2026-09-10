package club.boyuan.official.domain.resume.controller;

import java.util.Map;
import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.dto.ResumeFieldValueDTO;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeFieldDefinition;
import club.boyuan.official.persistence.entity.ResumeFieldValue;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.resume.service.IResumeFieldDefinitionService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.common.utils.PdfExportUtil;
import club.boyuan.official.common.utils.PermissionUtils;
import club.boyuan.official.common.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历控制器
 * 处理简历相关操作，包括字段定义管理、简历管理等。
 * <p>
 * 认证由 JwtAuthenticationFilter 统一完成、鉴权由 {@link PreAuthorize} 保证、
 * 异常由 GlobalExceptionHandler 统一处理，Controller 只表达正常业务流程。
 */
@RestController
@RequestMapping("/api/resumes")
@AllArgsConstructor
public class ResumeController {

    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);

    private final IResumeService resumeService;
    private final club.boyuan.official.domain.resume.service.IResumePhotoService resumePhotoService;
    private final IResumeFieldDefinitionService fieldDefinitionService;
    private final IUserService userService;
    private final club.boyuan.official.persistence.mapper.ResumeMapper resumeMapper;
    private final club.boyuan.official.persistence.mapper.RecruitmentCycleMapper recruitmentCycleMapper;
    private final club.boyuan.official.domain.interview.service.InterviewNotificationService interviewNotificationService;


    /** 三态化：草稿且周期已截止 → 状态 3（已截止未提交），仅影响展示层 */
    private Integer effectiveStatus(Integer status, Integer cycleId) {
        if (status == null || status != 1 || cycleId == null) return status;
        club.boyuan.official.persistence.entity.RecruitmentCycle cycle = recruitmentCycleMapper.selectById(cycleId);
        if (cycle != null && cycle.getEndDate() != null
                && cycle.getEndDate().isBefore(java.time.LocalDate.now())) {
            return 3;
        }
        return status;
    }

    /**
     * 查询本人历届申请（各周期的简历概要，按周期倒序）。
     * 供个人主页「我的申请」列表使用；点击后按 cycleId 查看该届完整进度。
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> getMyResumes() {
        User currentUser = currentUser();
        java.util.List<Resume> resumes = resumeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, currentUser.getUserId())
                        .orderByDesc(Resume::getCycleId));
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (Resume r : resumes) {
            club.boyuan.official.persistence.entity.RecruitmentCycle cycle =
                    r.getCycleId() == null ? null : recruitmentCycleMapper.selectById(r.getCycleId());
            // 已软删除的周期不再出现在「我的申请」里：周期被删表示这一届作废，
            // 挂在下面的申请对用户只是困惑（点进去也无事可做）
            if (cycle != null && Integer.valueOf(1).equals(cycle.getIsDeleted())) {
                continue;
            }
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("resumeId", r.getResumeId());
            item.put("cycleId", r.getCycleId());
            item.put("status", effectiveStatus(r.getStatus(), r.getCycleId()));
            item.put("createdAt", r.getCreatedAt());
            item.put("cycleName", cycle != null ? cycle.getCycleName() : null);
            item.put("academicYear", cycle != null ? cycle.getAcademicYear() : null);
            // 起止日期：用户端的周期切换器要在每张卡上显示「起 ~ 止」。
            // 缺了它，往届卡片上就只剩一个孤零零的「~」（线上就是这样）。
            item.put("startDate", cycle != null ? cycle.getStartDate() : null);
            item.put("endDate", cycle != null ? cycle.getEndDate() : null);
            list.add(item);
        }
        return ResponseEntity.ok(ResponseMessage.success(list));
    }

    /**
     * 获取指定招募周期的简历字段定义
     */
    @GetMapping("/fields/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<List<ResumeFieldDefinition>>> getFieldDefinitions(
            @PathVariable Integer cycleId) {
        logger.info("获取ID为{}的招募周期的简历字段定义", cycleId);
        List<ResumeFieldDefinition> fieldDefinitions = fieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
        logger.info("成功获取ID为{}的招募周期的简历字段定义，共{}条记录", cycleId, fieldDefinitions.size());
        return ResponseEntity.ok(new ResponseMessage<>(200, "获取字段定义成功", fieldDefinitions));
    }

    /**
     * 创建字段定义
     */
    @PostMapping("/fields")
    @PreAuthorize("hasAnyAuthority('cycle:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<ResumeFieldDefinition>> createFieldDefinition(
            @RequestBody ResumeFieldDefinition fieldDefinition) {
        logger.info("创建字段定义");
        ResumeFieldDefinition createdField = fieldDefinitionService.createFieldDefinition(fieldDefinition);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义创建成功", createdField));
    }

    /**
     * 更新字段定义
     */
    @PutMapping("/fields")
    @PreAuthorize("hasAnyAuthority('cycle:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<ResumeFieldDefinition>> updateFieldDefinition(
            @RequestBody ResumeFieldDefinition fieldDefinition) {
        logger.info("更新字段定义，字段ID: {}", fieldDefinition.getFieldId());
        ResumeFieldDefinition updatedField = fieldDefinitionService.updateFieldDefinition(fieldDefinition);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义更新成功", updatedField));
    }

    /**
     * 按模板初始化某周期的字段定义:只补该周期尚不存在的 field_key,已存在的一律不动。
     *
     * 这个接口此前不存在,而前端「加载默认配置」一直在调它 —— 每次 404,
     * 前端便回退到本地默认模板再走批量更新。那份模板的 fieldId 是 1..20 的顺序编号,
     * 与真实行毫无关系,于是把线上字段定义整体错位覆盖了(姓名/学号/性别 直接消失,
     * 「年级」那一格里存着姓名)。补上它,让「加载默认配置」走一条只新增、不覆盖的路径。
     */
    @PostMapping("/fields/{cycleId}/init")
    @PreAuthorize("hasAnyAuthority('cycle:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<List<ResumeFieldDefinition>>> initFieldDefinitions(
            @PathVariable Integer cycleId,
            @RequestBody List<ResumeFieldDefinition> templates) {
        logger.info("初始化周期 {} 的字段定义，模板数量: {}", cycleId, templates == null ? 0 : templates.size());
        List<ResumeFieldDefinition> result = fieldDefinitionService.initFieldDefinitions(cycleId, templates);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义初始化成功", result));
    }

    /**
     * 批量更新字段定义
     */
    @PutMapping("/fields/batch")
    @PreAuthorize("hasAnyAuthority('cycle:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<List<ResumeFieldDefinition>>> batchUpdateFieldDefinitions(
            @RequestBody List<ResumeFieldDefinition> fieldDefinitions) {
        logger.info("批量更新字段定义，字段数量: {}", fieldDefinitions.size());
        List<ResumeFieldDefinition> updatedFields = fieldDefinitionService.batchUpdateFieldDefinitions(fieldDefinitions);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义批量更新成功", updatedFields));
    }

    /**
     * 删除字段定义
     */
    @DeleteMapping("/fields/{fieldId}")
    @PreAuthorize("hasAnyAuthority('cycle:manage', 'resume:audit')")
    public ResponseEntity<ResponseMessage<String>> deleteFieldDefinition(@PathVariable Integer fieldId) {
        logger.info("管理员{}删除字段定义，字段ID: {}", SecurityUtil.getCurrentUsername(), fieldId);
        fieldDefinitionService.deleteFieldDefinition(fieldId);
        return ResponseEntity.ok(new ResponseMessage<>(200, "字段定义删除成功", "字段定义删除成功"));
    }

    /**
     * 根据招募周期获取当前用户的简历（不存在则自动创建草稿）
     */
    @GetMapping("/cycle/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> getResumeByCycleId(
            @PathVariable Integer cycleId,
            @RequestParam(required = false, defaultValue = "true") Boolean autoCreate) {
        User currentUser = currentUser();
        logger.info("用户{}({})获取招募周期ID为{}的简历", currentUser.getUsername(), currentUser.getUserId(), cycleId);

        ResumeDTO resumeDTO = resumeService.getResumeWithFieldValues(currentUser.getUserId(), cycleId);
        // 只读查询（如首页进度卡）：不存在时不自动创建草稿，直接返回 null
        if (resumeDTO == null && !Boolean.TRUE.equals(autoCreate)) {
            return ResponseEntity.ok(ResponseMessage.success(null));
        }
        if (resumeDTO != null) {
            resumeDTO.setStatus(effectiveStatus(resumeDTO.getStatus(), cycleId));
        }
        if (resumeDTO == null) {
            Resume resume = new Resume();
            resume.setUserId(currentUser.getUserId());
            resume.setCycleId(cycleId);
            resume.setStatus(1);
            resume.setCreatedAt(LocalDateTime.now());
            resumeService.createResume(resume);
            resumeDTO = resumeService.getResumeWithFieldValues(currentUser.getUserId(), cycleId);
        }

        logger.info("用户{}({})成功获取招募周期ID为{}的简历", currentUser.getUsername(), currentUser.getUserId(), cycleId);
        return ResponseEntity.ok(ResponseMessage.success(resumeDTO));
    }

    /**
     * 管理员查看指定用户的简历
     */
    @GetMapping("/admin/{userId}/{cycleId}")
    @PreAuthorize("hasAuthority('resume:view')")
    public ResponseEntity<ResponseMessage<?>> getResumeByUserIdAndCycleId(
            @PathVariable Integer userId, @PathVariable Integer cycleId) {
        logger.info("管理员{}查看用户{}的{}年份简历", SecurityUtil.getCurrentUsername(), userId, cycleId);
        ResumeDTO resumeDTO = resumeService.getResumeWithFieldValues(userId, cycleId);
        if (resumeDTO == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        return ResponseEntity.ok(ResponseMessage.success(resumeDTO));
    }

    /**
     * 保存字段值（不存在简历则自动创建草稿）
     */
    @PostMapping("/cycle/{cycleId}/field-values")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> saveFieldValues(
            @PathVariable Integer cycleId,
            @RequestBody List<ResumeFieldValue> fieldValues) {
        User currentUser = currentUser();
        logger.info("用户{}保存招募周期ID为{}的简历字段值，字段数量: {}",
                currentUser.getUsername(), cycleId, fieldValues.size());

        // 周期关闭后编辑一并拒绝：内容改了也投不进去，留着入口只会造成
        // "填写完成却查无此人"的困惑（管理端只看已提交）
        resumeService.assertCycleOpen(cycleId);

        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            resume = new Resume();
            resume.setUserId(currentUser.getUserId());
            resume.setCycleId(cycleId);
            resume.setStatus(1);
            resume.setCreatedAt(LocalDateTime.now());
            resume = resumeService.createResume(resume);
        }

        for (ResumeFieldValue fieldValue : fieldValues) {
            fieldValue.setResumeId(resume.getResumeId());
        }
        resumeService.saveFieldValues(fieldValues);

        return ResponseEntity.ok(ResponseMessage.success("字段值保存成功"));
    }

    /**
     * 获取当前用户字段值
     */
    @GetMapping("/cycle/{cycleId}/field-values")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> getFieldValues(@PathVariable Integer cycleId) {
        User currentUser = currentUser();
        logger.info("用户{}获取招募周期ID为{}的简历字段值", currentUser.getUsername(), cycleId);

        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }

        List<ResumeFieldValueDTO> fieldValues =
                resumeService.getFieldValuesWithDefinitionsByResumeId(resume.getResumeId());
        return ResponseEntity.ok(ResponseMessage.success(fieldValues));
    }

    /**
     * 提交简历
     */
    @PostMapping("/cycle/{cycleId}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> submitResume(@PathVariable Integer cycleId) {
        User currentUser = currentUser();
        logger.info("用户{}提交招募周期ID为{}的简历", currentUser.getUsername(), cycleId);

        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        // 周期已截止：不再接受提交
        club.boyuan.official.persistence.entity.RecruitmentCycle cycle = recruitmentCycleMapper.selectById(cycleId);
        if (cycle != null && cycle.getEndDate() != null
                && cycle.getEndDate().isBefore(java.time.LocalDate.now())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD,
                    "本周期简历提交已于 " + cycle.getEndDate() + " 截止");
        }
        if (resume.getStatus() != null && resume.getStatus() >= 2) {
            logger.warn("简历已提交或已在评审中，用户ID: {}，招募周期ID: {}，状态: {}",
                    currentUser.getUserId(), cycleId, resume.getStatus());
            throw new BusinessException(BusinessExceptionEnum.RESUME_ALREADY_SUBMITTED);
        }

        Resume submittedResume = resumeService.submitResume(resume.getResumeId());
        return ResponseEntity.ok(ResponseMessage.success(submittedResume));
    }

    /**
     * 更新简历状态（管理员）
     */
    /**
     * 简历打分（0~100）。resume_score 列此前只有飞书导出在读，没有任何写入口。
     * 权限与改状态/删除同属收窄后的 resume:audit —— 打分正是「审核简历」的一部分。
     */
    @PutMapping("/{resumeId}/score")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<ResumeDTO>> updateResumeScore(
            @PathVariable Integer resumeId,
            @RequestBody Map<String, Integer> body) {
        Integer score = body == null ? null : body.get("score");
        logger.info("更新简历评分，简历ID: {}，分数: {}", resumeId, score);
        // 语义：写入或更新「当前登录人」的那一票；返回的 resumeScore 是全部票的平均分，
        // scoreEntries 是逐人明细
        ResumeDTO dto = resumeService.updateResumeScore(resumeId, score, currentUser().getUserId());
        return ResponseEntity.ok(new ResponseMessage<>(200, "简历评分已更新", dto));
    }

    /**
     * 批量初筛：把选中的简历标为通过(4)/未通过(5)。
     *
     * 与「面试结果」是两回事：初筛决定谁能进面试，结果决定谁被录取。
     * 未通过初筛的简历不再参与自动分配、也不能再提交面试意向。
     * 本接口只改状态、不发邮件——通知走下面的 screening/notify，
     * 让管理员先核对名单再决定什么时候通知（与录取流程一致）。
     */
    @PostMapping("/screening/batch")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<Map<String, Integer>>> batchScreening(
            @RequestBody Map<String, Object> body) {
        List<Integer> resumeIds = toIntList(body.get("resumeIds"));
        Object passedRaw = body == null ? null : body.get("passed");
        if (resumeIds.isEmpty() || passedRaw == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD,
                    "请提供 resumeIds 与 passed");
        }
        boolean passed = Boolean.parseBoolean(String.valueOf(passedRaw));
        logger.info("管理员{}批量初筛 {} 份简历，结论: {}",
                SecurityUtil.getCurrentUsername(), resumeIds.size(), passed ? "通过" : "未通过");
        int updated = resumeService.batchScreening(resumeIds, passed);
        return ResponseEntity.ok(ResponseMessage.success(Map.of("updated", updated)));
    }

    /**
     * 给初筛未通过的同学批量发送通知。
     *
     * 只对状态确为「未通过初筛」的简历发送——避免误选把还在流程里的人
     * 通知成落选。每份简历一次入队、各自一个 requestId，可重复发送。
     */
    @PostMapping("/screening/notify")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<Map<String, Object>>> notifyScreenedOut(
            @RequestBody Map<String, Object> body) {
        List<Integer> resumeIds = toIntList(body.get("resumeIds"));
        if (resumeIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "请提供 resumeIds");
        }
        String customBody = body.get("customMessage") == null
                ? null : String.valueOf(body.get("customMessage"));

        List<Integer> queued = new java.util.ArrayList<>();
        List<Integer> skipped = new java.util.ArrayList<>();
        for (Integer resumeId : resumeIds) {
            Resume resume = resumeService.getResumeById(resumeId);
            if (resume != null && Integer.valueOf(5).equals(resume.getStatus())) {
                interviewNotificationService.enqueueResumeRejectedNotification(resumeId, customBody);
                queued.add(resumeId);
            } else {
                skipped.add(resumeId);
            }
        }
        logger.info("管理员{}发送初筛未通过通知，入队 {} 份，跳过 {} 份",
                SecurityUtil.getCurrentUsername(), queued.size(), skipped.size());
        return ResponseEntity.ok(ResponseMessage.success(
                Map.of("queued", queued.size(), "skipped", skipped)));
    }

    /** 请求体里的 id 数组转 List<Integer>，容忍字符串与数字混写 */
    private static List<Integer> toIntList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                try {
                    out.add(Integer.valueOf(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                    // 非法 id 直接跳过，不因为一个脏值让整批失败
                }
            }
        }
        return out;
    }

    @PutMapping("/{resumeId}/status/{status}")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<?>> updateResumeStatus(
            @PathVariable Integer resumeId, @PathVariable Integer status) {
        logger.info("管理员{}更新简历{}状态为{}", SecurityUtil.getCurrentUsername(), resumeId, status);

        // 1草稿 2已提交 4通过初筛 5未通过初筛；
        // 3 是「草稿且周期已截止」的派生态，不允许手工设置
        // 6=AI初筛中（瞬态，仅 agent 初筛 job 运行期间设置，结束后回落 2；
        //   与 4/5 正交——初筛结论仍由评审/面试结果模块落 4/5）
        if (status == null
                || (status != 1 && status != 2 && status != 4 && status != 5 && status != 6)) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD,
                    "简历状态仅支持 1(草稿)/2(已提交)/4(通过初筛)/5(未通过初筛)/6(AI初筛中,系统设置)，"
                            + "录取结论请使用面试结果模块");
        }
        }
        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        resume.setStatus(status);
        Resume updatedResume = resumeService.updateResume(resume);
        return ResponseEntity.ok(ResponseMessage.success(updatedResume));
    }

    /**
     * 更新简历内容（未提交状态才可更新）
     */
    @PutMapping("/cycle/{cycleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<?>> updateResume(
            @PathVariable Integer cycleId,
            @RequestBody List<ResumeFieldValue> fieldValues) {
        User currentUser = currentUser();
        logger.info("用户{}更新招募周期ID为{}的简历，字段数量: {}",
                currentUser.getUsername(), cycleId, fieldValues.size());

        // 与字段值保存同一道闸：周期关闭后不再接受任何学生侧修改
        resumeService.assertCycleOpen(cycleId);

        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        if (resume.getStatus() != null && resume.getStatus() > 2) {
            logger.warn("尝试更新已提交的简历，用户ID: {}，招募周期ID: {}，状态: {}",
                    currentUser.getUserId(), cycleId, resume.getStatus());
            throw new BusinessException(BusinessExceptionEnum.RESUME_ALREADY_SUBMITTED);
        }

        for (ResumeFieldValue fieldValue : fieldValues) {
            fieldValue.setResumeId(resume.getResumeId());
        }
        resumeService.saveFieldValues(fieldValues);

        resume.setUpdatedAt(LocalDateTime.now());
        resumeService.updateResume(resume);

        return ResponseEntity.ok(ResponseMessage.success("简历更新成功"));
    }

    /**
     * 删除简历（管理员或本人）
     */
    @DeleteMapping("/{resumeId}")
    @PreAuthorize("hasAuthority('resume:audit')")
    public ResponseEntity<ResponseMessage<String>> deleteResume(@PathVariable Integer resumeId) {
        User currentUser = currentUser();
        logger.info("用户{}尝试删除简历{}", currentUser.getUsername(), resumeId);

        Resume resume = resumeService.getResumeById(resumeId);
        if (resume == null) {
            throw new BusinessException(BusinessExceptionEnum.RESUME_NOT_FOUND);
        }
        if (!PermissionUtils.canAccessUserResource(currentUser, resume.getUserId())) {
            logger.warn("用户{}尝试删除不属于自己的简历{}", currentUser.getUsername(), resumeId);
            throw new BusinessException(BusinessExceptionEnum.USER_ROLE_NOT_AUTHORIZED);
        }

        resumeService.deleteResume(resumeId);
        logger.info("用户{}成功删除简历{}", currentUser.getUsername(), resumeId);
        return ResponseEntity.ok(new ResponseMessage<>(200, "简历删除成功", "简历删除成功"));
    }

    /**
     * 导出简历为PDF格式（管理员或本人）。
     * 因直接向响应流写二进制，保留局部异常处理（无法交给 GlobalExceptionHandler 处理半写响应）。
     */
    @GetMapping("/export/pdf/{resumeId}")
    @PreAuthorize("isAuthenticated()")  // 归属校验在方法内：本人或具备管理权限者可导出
    public void exportResumeToPdf(@PathVariable Integer resumeId, HttpServletResponse response) {
        try {
            User currentUser = currentUser();

            ResumeDTO resumeDTO = resumeService.getResumeWithFieldValuesById(resumeId);
            if (resumeDTO == null) {
                logger.warn("尝试导出不存在的简历，简历ID: {}，用户: {}", resumeId, currentUser.getUsername());
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "简历不存在");
                return;
            }
            if (!PermissionUtils.canAccessUserResource(currentUser, resumeDTO.getUserId())) {
                logger.warn("用户{}尝试导出不属于自己的简历{}", currentUser.getUsername(), resumeId);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "权限不足");
                return;
            }

            // 照片迁到 COS 后字段值是 objectKey，先取回字节再交给导出工具；
            // 历史 base64 数据返回 null，由工具内部按旧逻辑解析
            byte[] photoBytes = resumeDTO.getSimpleFields() == null ? null
                    : resumeDTO.getSimpleFields().stream()
                            .filter(f -> "personal_photo".equals(f.getFieldKey()))
                            .map(f -> resumePhotoService.readBytesIfObjectKey(f.getFieldValue()))
                            .filter(java.util.Objects::nonNull)
                            .findFirst().orElse(null);
            byte[] pdfBytes = PdfExportUtil.exportResumeToPdf(resumeDTO, photoBytes);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=resume_" + resumeId + ".pdf");
            response.setContentLength(pdfBytes.length);
            response.getOutputStream().write(pdfBytes);
            response.getOutputStream().flush();

            logger.info("用户{}成功导出简历{}为PDF", currentUser.getUsername(), resumeId);
        } catch (Exception e) {
            logger.error("导出简历为PDF失败，简历ID: {}", resumeId, e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "导出失败: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 表单里不渲染的字段。
     *
     * 与前端 resumeFieldRegistry 的 inForm=false 加 DEPRECATED_RESUME_FIELD_KEYS
     * 同一份清单——那张规范表在前端，Java 侧只能手抄，改那边时记得成对改这里。
     * 只影响空白模板：已填简历的导出照常展示历史数据。
     */
    private static final java.util.Set<String> NON_FORM_FIELD_KEYS = java.util.Set.of(
            // 由第一/第二志愿合成，不单独成栏
            "expected_departments",
            // 与「自我介绍」重复，2026-09 已从表单撤下
            "introduction",
            // 方案A（自助抢时段）遗留：时间窗现在由投递页的「面试意向」卡管
            "expected_interview_time", "second_interview_time", "can_attend_offline_interview");

    /**
     * 导出本周期的空白报名表模板（PDF）。
     *
     * 周期还没开始时表单不可填，但个人简介、项目经验这类题现场憋很吃亏。
     * 这里按已配置的字段渲染一份可打印的空白表，同学先在纸上/文档里打草稿。
     *
     * 走后端渲染而不是前端生成：前端 PDF 库默认字体不含中文，导出来是方框，
     * 要正常显示得再打包几 MB 字体；而容器里已装了 Noto CJK，简历导出用的
     * 就是它，版式也能和正式简历保持一致。
     */
    @GetMapping("/fields/{cycleId}/template.pdf")
    @PreAuthorize("isAuthenticated()")
    public void exportBlankTemplate(@PathVariable Integer cycleId, HttpServletResponse response) {
        try {
            List<ResumeFieldDefinition> defs = fieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
            List<PdfExportUtil.TemplateField> fields = defs.stream()
                    // 停用的字段学生根本看不到，模板里也不该出现
                    .filter(d -> !Boolean.FALSE.equals(d.getIsActive()))
                    // 表单里不渲染的字段也不该出现在模板上：第一版漏了这层过滤，
                    // 「期望部门」（由第一/第二志愿合成）和「第一/第二面试时间」
                    // （方案A 遗留，时间窗现在归面试意向卡管）都印了出来，
                    // 看着像要填三遍志愿
                    .filter(d -> !NON_FORM_FIELD_KEYS.contains(d.getFieldKey()))
                    .sorted(java.util.Comparator.comparing(
                            d -> d.getSortOrder() == null ? Integer.MAX_VALUE : d.getSortOrder()))
                    .map(d -> new PdfExportUtil.TemplateField(
                            d.getFieldLabel(), d.getFieldType(),
                            Boolean.TRUE.equals(d.getIsRequired()),
                            d.getOptions() == null ? List.of() : d.getOptions(),
                            d.getPlaceholder()))
                    .toList();
            if (fields.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "该周期还没有配置报名表字段");
                return;
            }

            club.boyuan.official.persistence.entity.RecruitmentCycle cycle =
                    recruitmentCycleMapper.selectById(cycleId);
            String cycleName = cycle != null && cycle.getCycleName() != null ? cycle.getCycleName() : "招新报名表";
            byte[] pdf = PdfExportUtil.exportBlankTemplateToPdf(cycleName, fields);

            response.setContentType("application/pdf");
            // 文件名含中文，按 RFC 5987 给 filename* —— 直接写会被部分浏览器截成乱码
            String ascii = "resume_template_" + cycleId + ".pdf";
            String utf8 = java.net.URLEncoder.encode(cycleName + "报名表模板.pdf",
                    java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + utf8);
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.getOutputStream().flush();
            logger.info("导出周期{}的空白报名表模板，共{}项", cycleId, fields.size());
        } catch (Exception e) {
            logger.error("导出空白报名表模板失败，周期ID: {}", cycleId, e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "导出失败");
            } catch (Exception ex) {
                logger.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 条件查询简历列表（管理员）。
     * 支持按姓名、专业、期望部门、招募周期、状态等多条件组合查询。
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('resume:view')")
    public ResponseEntity<ResponseMessage<?>> queryResumes(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String expectedDepartment,
            @RequestParam(required = false) Integer cycleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        String username = SecurityUtil.getCurrentUsername();

        // 若 page/size 均为默认且未指定排序，使用非分页查询（保持向后兼容）
        if (page == 0 && size == 10 && sortBy == null) {
            List<ResumeDTO> result = resumeService.queryResumes(name, major, expectedDepartment, cycleId, status);
            logger.info("管理员{}执行条件查询简历，结果数量: {}", username, result.size());
            return ResponseEntity.ok(ResponseMessage.success(result));
        }
        PageResultDTO<ResumeDTO> result = resumeService.queryResumesWithPagination(
                name, major, expectedDepartment, cycleId, status, page, size, sortBy, sortOrder);
        logger.info("管理员{}执行分页条件查询简历，结果数量: {}，总记录数: {}",
                username, result.getContent().size(), result.getTotalElements());
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    /**
     * 获取当前登录用户实体。令牌已由过滤器校验，这里只从 SecurityContext 读取用户名并查用户。
     */
    private User currentUser() {
        return userService.getUserByUsername(SecurityUtil.getCurrentUsername());
    }
}
