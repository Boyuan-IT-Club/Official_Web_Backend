package club.boyuan.official.domain.interview.controller;

import club.boyuan.official.domain.interview.dto.AutoAssignInterviewResponseDTO;
import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.common.utils.SecurityUtil;
import club.boyuan.official.domain.interview.service.IInterviewScheduleService;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * 面试安排表 前端控制器
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@Slf4j
@RestController
@RequestMapping("/api/interview/schedule")
@AllArgsConstructor
public class InterviewScheduleController {

    private final IInterviewScheduleService interviewScheduleService;

    private final IUserService userService;

    private final IResumeService resumeService;

    private final InterviewScheduleMapper interviewScheduleMapper;

    private final InterviewSessionMapper interviewSessionMapper;

    private final DepartmentMapper departmentMapper;

    private final club.boyuan.official.persistence.mapper.InterviewResultMapper interviewResultMapper;

    /**
     * 学生查询本人在指定周期的面试结果（录取/未录取）。
     * 结果未出（或管理员尚未录入 decision）时 data 为 null。
     */
    @GetMapping("/my-result")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<Map<String, Object>>> getMyResult(@RequestParam Integer cycleId) {
        User currentUser = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            return ResponseEntity.ok(ResponseMessage.success(null));
        }
        InterviewSchedule schedule = interviewScheduleMapper.selectOne(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getResumeId, resume.getResumeId())
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .orderByDesc(InterviewSchedule::getScheduleId)
                        .last("LIMIT 1"));
        if (schedule == null) {
            return ResponseEntity.ok(ResponseMessage.success(null));
        }
        club.boyuan.official.persistence.entity.InterviewResult result = interviewResultMapper.selectOne(
                new LambdaQueryWrapper<club.boyuan.official.persistence.entity.InterviewResult>()
                        .eq(club.boyuan.official.persistence.entity.InterviewResult::getScheduleId, schedule.getScheduleId())
                        .eq(club.boyuan.official.persistence.entity.InterviewResult::getUserId, currentUser.getUserId())
                        .orderByDesc(club.boyuan.official.persistence.entity.InterviewResult::getResultId)
                        .last("LIMIT 1"));
        if (result == null || result.getDecision() == null) {
            return ResponseEntity.ok(ResponseMessage.success(null));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resultId", result.getResultId());
        data.put("decision", result.getDecision()); // 1=通过 2=未通过
        data.put("decisionAt", result.getDecisionAt());
        if (result.getAssignedDeptId() != null) {
            Department dept = departmentMapper.selectById(result.getAssignedDeptId());
            data.put("assignedDeptId", result.getAssignedDeptId());
            data.put("assignedDeptName", dept != null ? dept.getDeptName() : null);
        }
        return ResponseEntity.ok(ResponseMessage.success(data));
    }

    /**
     * 学生查询本人在指定周期的面试安排（方案B分配结果）。
     * 未分配时 data 为 null；已分配时返回时间/部门/地点等信息。
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseMessage<Map<String, Object>>> getMySchedule(@RequestParam Integer cycleId) {
        User currentUser = userService.getUserByUsername(SecurityUtil.getCurrentUsername());
        Resume resume = resumeService.getResumeByUserIdAndCycleId(currentUser.getUserId(), cycleId);
        if (resume == null) {
            return ResponseEntity.ok(ResponseMessage.success(null));
        }
        InterviewSchedule schedule = interviewScheduleMapper.selectOne(
                new LambdaQueryWrapper<InterviewSchedule>()
                        .eq(InterviewSchedule::getResumeId, resume.getResumeId())
                        .eq(InterviewSchedule::getCycleId, cycleId)
                        .orderByDesc(InterviewSchedule::getScheduleId)
                        .last("LIMIT 1"));
        if (schedule == null) {
            return ResponseEntity.ok(ResponseMessage.success(null));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scheduleId", schedule.getScheduleId());
        result.put("cycleId", schedule.getCycleId());
        result.put("interviewTime", schedule.getInterviewTime());
        result.put("status", schedule.getStatus());
        result.put("deptId", schedule.getDeptId());
        if (schedule.getDeptId() != null) {
            Department dept = departmentMapper.selectById(schedule.getDeptId());
            result.put("deptName", dept != null ? dept.getDeptName() : null);
        }
        if (schedule.getSessionId() != null) {
            InterviewSession session = interviewSessionMapper.selectById(schedule.getSessionId());
            if (session != null) {
                result.put("location", session.getLocation());
            }
        }
        return ResponseEntity.ok(ResponseMessage.success(result));
    }

    /**
     * 一键分配面试成员面试时间地点（按招募周期）- 路径参数版本
     * 
     * @param cycleId 招募周期ID
     * @return 分配结果
     */
    @PostMapping("/auto-assign/{cycleId}")
    @PreAuthorize("hasAnyAuthority('interview:schedule', 'resume:audit')")
    public ResponseEntity<ResponseMessage<AutoAssignInterviewResponseDTO>> autoAssignInterviewsByCycleId(
            @PathVariable Integer cycleId) {
        try {
            log.info("开始一键分配面试，招募周期ID: {}", cycleId);
            AutoAssignInterviewResponseDTO result = interviewScheduleService.autoAssignInterviews(cycleId);
            log.info("一键分配面试完成，已分配 {} 人", result.getAssignedCount());
            return ResponseEntity.ok(ResponseMessage.success(result));
        } catch (Exception e) {
            log.error("一键分配面试失败，招募周期ID: {}", cycleId, e);
            return ResponseEntity.badRequest()
                    .body(ResponseMessage.error(400, "分配失败: " + e.getMessage()));
        }
    }
}