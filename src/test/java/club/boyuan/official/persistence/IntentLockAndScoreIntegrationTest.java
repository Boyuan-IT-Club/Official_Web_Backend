package club.boyuan.official.persistence;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.SubmitInterviewPreferenceRequestDTO;
import club.boyuan.official.domain.interview.service.IInterviewPreferenceService;
import club.boyuan.official.domain.interview.service.IInterviewResultService;
import club.boyuan.official.persistence.entity.InterviewResult;
import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import club.boyuan.official.domain.resume.service.IResumeService;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewTimeSlot;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.InterviewTimeSlotMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 两个功能的回归保护,共用一套测试数据(周期/时间窗/简历):
 *
 * 一、面试意向在「已安排」后必须锁定。
 *    修复前 POST /api/interview/preference 没有任何检查 —— 算法按旧志愿排完场次后,
 *    学生仍能改志愿/时间窗,以为改了,面试官却按旧安排等人。
 *    已取消(status=2)的安排不拦:取消后重排前学生应能改意向。
 *
 * 二、简历打分。
 *
 * 三、站内生成结果名单 + 周期关闭后拒收简历（与上面共用同一套数据）。
 *    resume_score 列此前只有飞书导出在读,全后端没有写入口 —— 管理端没有打分的地方。
 */
@SpringBootTest
class IntentLockAndScoreIntegrationTest {

    /** V6 种子:管理员账号 user_id=1,部门 1=技术部、2=综合部 */
    private static final int SEEDED_USER_ID = 1;
    private static final int SEEDED_DEPT_TECH = 1;
    private static final int SEEDED_DEPT_GENERAL = 2;
    private static final int SCHEDULE_ACTIVE = 1;
    private static final int SCHEDULE_CANCELLED = 2;

    @Autowired
    private IInterviewPreferenceService preferenceService;

    @Autowired
    private IResumeService resumeService;

    @Autowired
    private IInterviewResultService resultService;

    @Autowired
    private InterviewResultMapper resultMapper;

    @Autowired
    private RecruitmentCycleMapper cycleMapper;

    @Autowired
    private InterviewTimeSlotMapper timeSlotMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private InterviewScheduleMapper scheduleMapper;

    @Test
    @DisplayName("意向：未安排可改，已安排被拒，安排取消后又可改；打分：写入生效且校验范围")
    void intentLocksOnScheduleAndScoreWorks() {
        Integer cycleId = null;
        Integer resumeId = null;
        Integer slotId = null;
        try {
            RecruitmentCycle cycle = new RecruitmentCycle();
            cycle.setCycleName("测试-意向锁定与打分");
            cycle.setAcademicYear("2099-2100");
            cycle.setStartDate(LocalDate.now().minusDays(1));
            cycle.setEndDate(LocalDate.now().plusDays(1));
            cycle.setIsActive(0);
            cycle.setStatus(2);
            cycleMapper.insert(cycle);
            cycleId = cycle.getCycleId();

            InterviewTimeSlot slot = new InterviewTimeSlot();
            slot.setCycleId(cycleId);
            slot.setSlotName("测试-时段");
            slot.setInterviewDate(LocalDate.now().plusDays(1));
            slot.setStartTime(LocalTime.of(9, 0));
            slot.setEndTime(LocalTime.of(11, 0));
            timeSlotMapper.insert(slot);
            slotId = slot.getTimeSlotId();

            Resume resume = new Resume();
            resume.setUserId(SEEDED_USER_ID);
            resume.setCycleId(cycleId);
            resume.setStatus(2);   // 已提交，满足 requireSubmittedResume
            resume.setResumeScore(0);
            resumeMapper.insert(resume);
            resumeId = resume.getResumeId();

            SubmitInterviewPreferenceRequestDTO req = new SubmitInterviewPreferenceRequestDTO();
            req.setCycleId(cycleId);
            req.setFirstDeptId(SEEDED_DEPT_TECH);
            req.setSecondDeptId(SEEDED_DEPT_GENERAL);
            req.setTimeSlotIds(List.of(slotId));

            // 1) 未安排：正常提交
            assertDoesNotThrow(() -> preferenceService.submitPreference(SEEDED_USER_ID, req),
                    "没有面试安排时提交意向应照常成功");

            // 2) 安排生效（status=1）：提交被拒
            InterviewSchedule schedule = new InterviewSchedule();
            schedule.setResumeId(resumeId);
            schedule.setUserId(SEEDED_USER_ID);
            schedule.setCycleId(cycleId);
            schedule.setStatus(SCHEDULE_ACTIVE);
            scheduleMapper.insert(schedule);

            BusinessException locked = assertThrows(BusinessException.class,
                    () -> preferenceService.submitPreference(SEEDED_USER_ID, req),
                    "面试已安排后仍能改意向 —— 这正是要修的 bug");
            assertEquals(BusinessExceptionEnum.INTERVIEW_PREFERENCE_LOCKED_BY_SCHEDULE.getCode(),
                    locked.getCode(), "应返回意向锁定的专属错误码，而不是笼统失败");

            // 3) 安排取消（status=2）：解锁，可再改
            schedule.setStatus(SCHEDULE_CANCELLED);
            scheduleMapper.updateById(schedule);
            assertDoesNotThrow(() -> preferenceService.submitPreference(SEEDED_USER_ID, req),
                    "已取消的安排不该继续锁意向 —— 取消后重排前学生应能改");

            // 4) 打分：写入生效
            final Integer rid = resumeId;
            resumeService.updateResumeScore(rid, 85);
            assertEquals(85, resumeMapper.selectById(rid).getResumeScore(),
                    "打分应落库到 resume_score");

            // 5) 范围与存在性校验
            assertThrows(BusinessException.class, () -> resumeService.updateResumeScore(rid, 101),
                    "超出 0~100 必须拒绝");
            assertThrows(BusinessException.class, () -> resumeService.updateResumeScore(rid, -1),
                    "负分必须拒绝");
            assertThrows(BusinessException.class, () -> resumeService.updateResumeScore(999999, 60),
                    "不存在的简历必须拒绝");
            assertEquals(85, resumeMapper.selectById(rid).getResumeScore(),
                    "被拒的打分不得留下任何写入");

            // 6) 周期关闭（本测试周期 is_active=0）：提交简历必须被拒
            BusinessException closed = assertThrows(BusinessException.class,
                    () -> resumeService.submitResume(rid),
                    "周期未开放时提交必须被拒 —— 前端藏入口挡不住直接调接口");
            assertEquals(BusinessExceptionEnum.RESUME_CYCLE_CLOSED.getCode(), closed.getCode());

            // 7) 周期开放（启用且窗口覆盖今天）：提交恢复正常
            RecruitmentCycle reopen = new RecruitmentCycle();
            reopen.setCycleId(cycleId);
            reopen.setIsActive(1);
            cycleMapper.updateById(reopen);
            assertDoesNotThrow(() -> resumeService.submitResume(rid),
                    "周期开放时提交应照常成功");

            // 8) 站内生成结果名单：把安排恢复为生效，再 seed
            schedule.setStatus(SCHEDULE_ACTIVE);
            scheduleMapper.updateById(schedule);
            final Integer cid2 = cycleId;
            assertEquals(1, resultService.seedFromSchedules(cid2),
                    "一条生效安排应生成一行待定结果 —— 此前结果行只有飞书拉取会建");
            InterviewResult seededRow = resultMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewResult>()
                            .eq(InterviewResult::getScheduleId, schedule.getScheduleId()));
            assertNotNull(seededRow, "结果行应已创建");
            assertEquals(0, seededRow.getDecision(), "新生成的结果应为待定（decision=0）");
            assertEquals(SEEDED_USER_ID, seededRow.getUserId(), "候选人应取自安排");
            assertEquals(0, resultService.seedFromSchedules(cid2),
                    "重复生成必须幂等 —— 已有结果行（含飞书拉的）一律不动");
        } finally {
            // 意向与安排随简历级联删（fk_pref_resume / fk_schedule_resume 均为 CASCADE）
            if (resumeId != null) {
                resumeMapper.deleteById(resumeId);
            }
            if (slotId != null) {
                timeSlotMapper.deleteById(slotId);
            }
            if (cycleId != null) {
                cycleMapper.deleteById(cycleId);
            }
        }
    }
}
