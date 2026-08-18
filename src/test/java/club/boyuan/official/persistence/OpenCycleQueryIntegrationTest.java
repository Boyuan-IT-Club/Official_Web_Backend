package club.boyuan.official.persistence;

import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归保护:「当前开放投递的周期」必须按起止日期算,且允许同时有多个。
 *
 * 线上问题:用户端原先取 /api/cycles/active/1 的第一条当作当前周期。
 * is_active 的语义只是「是否启用」,往届周期为了能查历史简历通常也保持启用,
 * 于是同时存在两个启用周期时会任选一个;一旦选中的那个还没配简历字段,
 * /api/resumes/fields/{id} 返回 200 + 空数组(不报错),投递页就渲染成一张
 * 零字段的空表单 —— 表现为「用户端显示不出来」,前后端都没有任何报错。
 */
@SpringBootTest
class OpenCycleQueryIntegrationTest {

    @Autowired
    private RecruitmentCycleMapper cycleMapper;

    @Test
    @DisplayName("同时开放的多个周期都要返回,按 start_date 倒序;已过期与未启用的不返回")
    void openCyclesAreResolvedByDateWindow() {
        LocalDate today = LocalDate.now();
        List<Integer> created = new ArrayList<>();
        try {
            // 两个窗口都覆盖今天,start_date 一早一晚
            Integer earlier = insert("测试-较早开放", today.minusDays(10), today.plusDays(10), 1, created);
            Integer later = insert("测试-较晚开放", today.minusDays(2), today.plusDays(20), 1, created);
            // 窗口已过去
            Integer expired = insert("测试-已截止", today.minusDays(60), today.minusDays(30), 1, created);
            // 在窗口内但被禁用
            Integer disabled = insert("测试-未启用", today.minusDays(5), today.plusDays(5), 0, created);

            List<Integer> ids = cycleMapper.findOpenForApplication(today).stream()
                    .map(RecruitmentCycle::getCycleId)
                    .toList();

            assertTrue(ids.contains(earlier), "窗口覆盖今天的周期必须返回");
            assertTrue(ids.contains(later), "同时开放的第二个周期也必须返回,不能只给一个");
            assertFalse(ids.contains(expired), "end_date 已过的周期不该出现在开放列表里");
            assertFalse(ids.contains(disabled), "is_active=0 的周期不该出现在开放列表里");

            // 默认选中项取列表第一个,所以顺序必须是 start_date 倒序
            assertTrue(ids.indexOf(later) < ids.indexOf(earlier),
                    "应按 start_date 倒序,较晚开始的排前面(前端以第一项为默认选中): " + ids);
        } finally {
            created.forEach(cycleMapper::deleteById);
        }
    }

    private Integer insert(String name, LocalDate start, LocalDate end, int isActive, List<Integer> created) {
        RecruitmentCycle c = new RecruitmentCycle();
        c.setCycleName(name);
        c.setAcademicYear("2099-2100");
        c.setStartDate(start);
        c.setEndDate(end);
        c.setIsActive(isActive);
        c.setStatus(1);
        cycleMapper.insert(c);
        assertNotNull(c.getCycleId(), "插入后应回填自增主键");
        created.add(c.getCycleId());
        return c.getCycleId();
    }
}
