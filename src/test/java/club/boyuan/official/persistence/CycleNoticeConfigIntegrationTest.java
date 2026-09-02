package club.boyuan.official.persistence;

import club.boyuan.official.domain.resume.service.IRecruitmentCycleService;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 周期上的通知配置：候场教室、负责人联系方式。
 *
 * 重点在「能清空」——MyBatis-Plus 的 updateById 跳过 null 字段，
 * 不额外处理的话这两项填过一次就再也删不掉（部门分配踩过同一个坑）。
 */
@SpringBootTest
class CycleNoticeConfigIntegrationTest {

    @Autowired
    private IRecruitmentCycleService cycleService;

    @Autowired
    private RecruitmentCycleMapper cycleMapper;

    private Integer cycleId;

    @BeforeEach
    void seed() {
        RecruitmentCycle c = new RecruitmentCycle();
        c.setCycleName("测试-通知配置-" + System.nanoTime());
        c.setAcademicYear("2099-2100");
        c.setStartDate(LocalDate.now().minusDays(1));
        c.setEndDate(LocalDate.now().plusDays(1));
        c.setIsActive(0);
        c.setStatus(2);
        cycleMapper.insert(c);
        cycleId = c.getCycleId();
    }

    @AfterEach
    void cleanup() {
        if (cycleId != null) {
            cycleMapper.deleteById(cycleId);
        }
    }

    @Test
    @DisplayName("候场教室与联系方式：能存、能改、能清空")
    void configCanBeSetChangedAndCleared() {
        RecruitmentCycle set = cycleMapper.selectById(cycleId);
        set.setWaitingRoom("教书院202");
        set.setContactInfo("丁华烨 微信 dinghuaye");
        cycleService.updateRecruitmentCycle(set);

        RecruitmentCycle afterSet = cycleMapper.selectById(cycleId);
        assertEquals("教书院202", afterSet.getWaitingRoom());
        assertEquals("丁华烨 微信 dinghuaye", afterSet.getContactInfo());

        // 改
        afterSet.setWaitingRoom("教书院301");
        cycleService.updateRecruitmentCycle(afterSet);
        assertEquals("教书院301", cycleMapper.selectById(cycleId).getWaitingRoom());

        // 清空 —— updateById 跳过 null，不额外处理的话这一步会静默无效
        RecruitmentCycle clear = cycleMapper.selectById(cycleId);
        clear.setWaitingRoom(null);
        clear.setContactInfo(null);
        cycleService.updateRecruitmentCycle(clear);

        RecruitmentCycle afterClear = cycleMapper.selectById(cycleId);
        assertNull(afterClear.getWaitingRoom(), "候场教室应能清空");
        assertNull(afterClear.getContactInfo(), "联系方式应能清空");
    }

    @Test
    @DisplayName("只改别的字段时不误清通知配置")
    void unrelatedUpdateKeepsConfig() {
        RecruitmentCycle set = cycleMapper.selectById(cycleId);
        set.setWaitingRoom("教书院202");
        set.setContactInfo("联系人");
        cycleService.updateRecruitmentCycle(set);

        // 模拟「只改了名字」的请求：把整个实体读出来改一个字段再存回，
        // 这是前端表单的实际行为
        RecruitmentCycle rename = cycleMapper.selectById(cycleId);
        rename.setCycleName("改个名字");
        cycleService.updateRecruitmentCycle(rename);

        RecruitmentCycle after = cycleMapper.selectById(cycleId);
        assertEquals("教书院202", after.getWaitingRoom(), "不该被顺手清掉");
        assertEquals("联系人", after.getContactInfo());
        assertEquals("改个名字", after.getCycleName());
    }
}
