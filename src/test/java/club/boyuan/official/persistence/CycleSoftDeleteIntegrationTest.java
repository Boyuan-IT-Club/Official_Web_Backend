package club.boyuan.official.persistence;

import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归保护:招募周期删除必须是软删除。
 *
 * 修复前是 deleteById 硬删,两头都是坑:
 *   - resume.cycle_id 是 ON DELETE RESTRICT —— 有简历的周期删不动,
 *     管理端只会得到一个外键报错(用户实际撞到的就是它);
 *   - interview_session / interview_time_slot 对周期是 ON DELETE CASCADE ——
 *     没简历的周期一旦删除成功,整个周期的面试数据被静默连带清掉。
 *
 * 软删语义(本测试逐条锁定):
 *   1. 带简历的周期能删,不抛外键异常 —— 用户要的就是这条
 *   2. 删除后从枚举查询(findAll / findOpenForApplication)里消失
 *   3. 按 ID 点查(findById / BaseMapper.selectById)仍返回 —— 历史简历要靠它解析周期名
 *   4. 简历数据原样保留
 */
@SpringBootTest
class CycleSoftDeleteIntegrationTest {

    /** V6 种子里的管理员账号,resume.user_id 的外键靠它满足 */
    private static final int SEEDED_ADMIN_USER_ID = 1;

    @Autowired
    private IRecruitmentCycleService service;

    @Autowired
    private RecruitmentCycleMapper cycleMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Test
    @DisplayName("带简历的周期可软删；列表消失、点查保留、简历不动")
    void softDeleteKeepsHistoryAndHidesFromLists() {
        Integer cycleId = null;
        Integer resumeId = null;
        try {
            // 开放窗口覆盖今天且启用:这样"从开放周期里消失"才能归因于软删本身
            RecruitmentCycle cycle = new RecruitmentCycle();
            cycle.setCycleName("测试-软删除");
            cycle.setAcademicYear("2099-2100");
            cycle.setStartDate(LocalDate.now().minusDays(1));
            cycle.setEndDate(LocalDate.now().plusDays(1));
            cycle.setIsActive(1);
            cycle.setStatus(2);
            cycleMapper.insert(cycle);
            cycleId = cycle.getCycleId();
            assertNotNull(cycleId, "插入周期后应回填主键");

            Resume resume = new Resume();
            resume.setUserId(SEEDED_ADMIN_USER_ID);
            resume.setCycleId(cycleId);
            resume.setStatus(2);
            resume.setResumeScore(0);
            resumeMapper.insert(resume);
            resumeId = resume.getResumeId();
            assertNotNull(resumeId, "插入简历后应回填主键");

            final Integer cid = cycleId;
            assertTrue(cycleMapper.findOpenForApplication(LocalDate.now()).stream()
                            .anyMatch(c -> cid.equals(c.getCycleId())),
                    "软删前该周期应出现在开放列表里（前置条件）");

            // 1) 带简历也能删 —— 修复前这里直接抛 DataIntegrityViolation
            assertDoesNotThrow(() -> service.deleteRecruitmentCycle(cid),
                    "有简历的周期必须能软删除，这正是用户报的 bug");

            // 2) 枚举查询里消失
            assertFalse(cycleMapper.findAll().stream()
                            .anyMatch(c -> cid.equals(c.getCycleId())),
                    "软删后不该再出现在 findAll 里");
            assertFalse(cycleMapper.findOpenForApplication(LocalDate.now()).stream()
                            .anyMatch(c -> cid.equals(c.getCycleId())),
                    "软删后不该再出现在开放投递列表里 —— 即使日期窗口仍覆盖今天");

            // 3) 点查保留（历史解析路径）
            RecruitmentCycle viaXml = cycleMapper.findById(cid);
            assertNotNull(viaXml, "findById 必须仍能查到 —— 历史简历靠它解析周期名");
            assertEquals(1, viaXml.getIsDeleted(), "点查结果应带出已删除标记");
            assertNotNull(cycleMapper.selectById(cid),
                    "BaseMapper.selectById 也必须仍能查到（简历详情/评价表走这条）");

            // 4) 简历原样保留
            assertNotNull(resumeMapper.selectById(resumeId), "软删周期不得动简历数据");

            // 幂等：再删一次不报错，且不重复计数
            assertDoesNotThrow(() -> service.deleteRecruitmentCycle(cid));
        } finally {
            if (resumeId != null) {
                resumeMapper.deleteById(resumeId);
            }
            if (cycleId != null) {
                // 清理用 BaseMapper 硬删（简历已先删，RESTRICT 不再拦）；
                // 该方法未暴露给任何接口，仅测试清理使用
                cycleMapper.deleteById(cycleId);
            }
        }
    }
}
