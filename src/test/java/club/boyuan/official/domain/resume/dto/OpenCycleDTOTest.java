package club.boyuan.official.domain.resume.dto;

import club.boyuan.official.persistence.entity.RecruitmentCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * intakeOpen 是前端区分「可投」与「只读可见」的唯一依据：
 * /api/cycles/open 不再按 is_active 过滤之后，这个标记错了，
 * 用户端要么放学生给已停止投递的周期提交（后端会拦但体验很差），
 * 要么把正在开放的周期当成只读。
 */
class OpenCycleDTOTest {

    @Test
    @DisplayName("is_active=1 → intakeOpen=true；is_active=0/null → false")
    void intakeOpenFollowsIsActive() {
        assertTrue(new OpenCycleDTO(cycle(1), 3).isIntakeOpen());
        assertFalse(new OpenCycleDTO(cycle(0), 3).isIntakeOpen(), "停止投递的周期不能标成可投");
        assertFalse(new OpenCycleDTO(cycle(null), 3).isIntakeOpen(), "is_active 为空按不可投处理，宁可只读");
    }

    private static RecruitmentCycle cycle(Integer isActive) {
        RecruitmentCycle c = new RecruitmentCycle();
        c.setCycleId(9);
        c.setCycleName("t");
        c.setStartDate(LocalDate.of(2026, 9, 1));
        c.setEndDate(LocalDate.of(2026, 9, 30));
        c.setIsActive(isActive);
        return c;
    }
}
