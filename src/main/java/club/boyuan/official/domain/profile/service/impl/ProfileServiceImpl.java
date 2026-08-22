package club.boyuan.official.domain.profile.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.profile.IProfileService;
import club.boyuan.official.domain.profile.dto.CandidateProfileDetail;
import club.boyuan.official.domain.profile.dto.CandidateProfileListRow;
import club.boyuan.official.persistence.entity.AwardExperience;
import club.boyuan.official.persistence.entity.Department;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.AwardExperienceMapper;
import club.boyuan.official.persistence.mapper.DepartmentMapper;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.InterviewSlotMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 候选档案实现：全部数据走批量查询 + 内存组装，避免 N+1。 */
@Service
@AllArgsConstructor
public class ProfileServiceImpl implements IProfileService {

    private final InterviewScheduleMapper interviewScheduleMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewSlotMapper interviewSlotMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final RecruitmentCycleMapper recruitmentCycleMapper;
    private final ResumeMapper resumeMapper;
    private final AwardExperienceMapper awardExperienceMapper;
    private final EvaluationSubmissionMapper evaluationSubmissionMapper;

    @Override
    public List<CandidateProfileListRow> listCandidates(Integer cycleId) {
        List<Map<String, Object>> rows = interviewScheduleMapper.selectCandidateProfileRows(cycleId);
        if (rows == null) {
            return Collections.emptyList();
        }
        List<CandidateProfileListRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            CandidateProfileListRow row = new CandidateProfileListRow();
            row.setUserId(asInt(r.get("userId")));
            row.setName(asString(r.get("name")));
            row.setUsername(asString(r.get("username")));
            row.setMajor(asString(r.get("major")));
            row.setDeptName(asString(r.get("deptName")));
            row.setCycleName(asString(r.get("cycleName")));
            // 时间/地点由 mapper 返回 java.sql.Timestamp/字符串，安全转换
            row.setLatestInterviewTime(toLocalDateTime(r.get("latestInterviewTime")));
            row.setInterviewLocation(asString(r.get("interviewLocation")));
            result.add(row);
        }
        return result;
    }

    @Override
    public CandidateProfileDetail getProfile(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        CandidateProfileDetail detail = new CandidateProfileDetail();
        detail.setUserId(user.getUserId());
        detail.setName(user.getName());
        detail.setUsername(user.getUsername());
        detail.setMajor(user.getMajor());
        detail.setEmail(user.getEmail());
        detail.setPhone(user.getPhone());
        detail.setGithub(user.getGithub());
        if (user.getDeptId() != null) {
            Department dept = departmentMapper.selectById(user.getDeptId());
            detail.setDeptName(dept != null ? dept.getDeptName() : null);
        }

        // 简历（按 user，跨周期）
        List<Resume> resumes = resumeMapper.findByUserId(userId);
        detail.setResumes(resumes);
        List<Integer> resumeIds = resumes == null
                ? Collections.emptyList()
                : resumes.stream().map(Resume::getResumeId).collect(Collectors.toList());

        // 面试安排（按简历 ids 批量查，全部周期）
        List<InterviewSchedule> schedules = (resumeIds.isEmpty())
                ? Collections.emptyList()
                : interviewScheduleMapper.selectList(new LambdaQueryWrapper<InterviewSchedule>()
                        .in(InterviewSchedule::getResumeId, resumeIds)
                        .orderByAsc(InterviewSchedule::getInterviewTime));

        // 批量索引：场次/时段/周期/部门，供内存组装（避免逐行查库）
        Map<Integer, InterviewSession> sessionIdx = indexSessions(schedules);
        Map<Integer, InterviewSlot> slotIdx = indexSlots(schedules);
        Map<Integer, RecruitmentCycle> cycleIdx = indexCycles(schedules);
        Map<Integer, Department> deptIdx = indexDepartments(schedules, sessionIdx);

        List<CandidateProfileDetail.InterviewScheduleSection> interviewSections = new ArrayList<>();
        if (schedules != null) {
            for (InterviewSchedule sc : schedules) {
                CandidateProfileDetail.InterviewScheduleSection sec =
                        new CandidateProfileDetail.InterviewScheduleSection();
                sec.setSchedule(sc);
                InterviewSession sess = sessionIdx.get(sc.getSessionId());
                if (sess != null) {
                    sec.setLocation(sess.getLocation());
                    if (sess.getDeptId() != null) {
                        Department d = deptIdx.get(sess.getDeptId());
                        sec.setDeptName(d != null ? d.getDeptName() : null);
                    }
                } else {
                    InterviewSlot slot = slotIdx.get(sc.getSlotId());
                    if (slot != null) {
                        sec.setLocation(slot.getLocation());
                    }
                }
                RecruitmentCycle rc = cycleIdx.get(sc.getCycleId());
                sec.setCycleName(rc != null ? rc.getCycleName() : null);
                interviewSections.add(sec);
            }
        }
        detail.setInterviews(interviewSections);

        // 获奖 / 评测（按 user 批量）
        List<AwardExperience> awards = awardExperienceMapper.selectByUserId(userId);
        detail.setAwards(awards == null ? Collections.emptyList() : awards);
        List<EvaluationSubmission> submissions = evaluationSubmissionMapper.selectByUserId(userId);
        detail.setSubmissions(submissions == null ? Collections.emptyList() : submissions);
        return detail;
    }

    // ── 工具 ──────────────────────────────────────────────────────────────
    private Map<Integer, InterviewSession> indexSessions(List<InterviewSchedule> schedules) {
        if (schedules == null) return Collections.emptyMap();
        List<Integer> ids = schedules.stream().map(InterviewSchedule::getSessionId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Integer, InterviewSession> map = new HashMap<>();
        List<InterviewSession> list = interviewSessionMapper.selectBatchIds(ids);
        if (list != null) for (InterviewSession s : list) if (s != null) map.put(s.getSessionId(), s);
        return map;
    }

    private Map<Integer, InterviewSlot> indexSlots(List<InterviewSchedule> schedules) {
        if (schedules == null) return Collections.emptyMap();
        List<Integer> ids = schedules.stream().map(InterviewSchedule::getSlotId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Integer, InterviewSlot> map = new HashMap<>();
        List<InterviewSlot> list = interviewSlotMapper.selectBatchIds(ids);
        if (list != null) for (InterviewSlot s : list) if (s != null) map.put(s.getSlotId(), s);
        return map;
    }

    private Map<Integer, RecruitmentCycle> indexCycles(List<InterviewSchedule> schedules) {
        if (schedules == null) return Collections.emptyMap();
        List<Integer> ids = schedules.stream().map(InterviewSchedule::getCycleId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Integer, RecruitmentCycle> map = new HashMap<>();
        List<RecruitmentCycle> list = recruitmentCycleMapper.selectBatchIds(ids);
        if (list != null) for (RecruitmentCycle s : list) if (s != null) map.put(s.getCycleId(), s);
        return map;
    }

    private Map<Integer, Department> indexDepartments(List<InterviewSchedule> schedules,
                                                      Map<Integer, InterviewSession> sessionIdx) {
        if (schedules == null) return Collections.emptyMap();
        Map<Integer, Department> map = new HashMap<>();
        for (Integer deptId : schedules.stream().map(InterviewSchedule::getDeptId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toList())) {
            if (deptId != null) map.put(deptId, null);
        }
        for (InterviewSession s : sessionIdx.values()) {
            if (s != null && s.getDeptId() != null) map.put(s.getDeptId(), null);
        }
        if (map.isEmpty()) return Collections.emptyMap();
        List<Department> list = departmentMapper.selectBatchIds(new ArrayList<>(map.keySet()));
        if (list != null) for (Department d : list) if (d != null) map.put(d.getDeptId(), d);
        return map;
    }

    private Integer asInt(Object o) {
        return o == null ? null : (o instanceof Number) ? ((Number) o).intValue() : null;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private java.time.LocalDateTime toLocalDateTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.time.LocalDateTime) {
            return (java.time.LocalDateTime) o;
        }
        if (o instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) o).toLocalDateTime();
        }
        if (o instanceof java.util.Date) {
            return new java.sql.Timestamp(((java.util.Date) o).getTime()).toLocalDateTime();
        }
        return null;
    }
}