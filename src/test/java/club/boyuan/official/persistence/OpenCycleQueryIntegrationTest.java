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

    @Test
    @DisplayName("未开始的周期进「即将开放」，且绝不能混进「开放投递」")
    void upcomingCyclesAreSeparateFromOpenOnes() {
        // 线上问题：管理员把已经开投的周期的开始时间往后推，周期掉出开放列表，
        // 用户端却只会判断「不在开放列表里」，于是一律显示「招募周期已结束」——
        // 和旁边那句「已提交（可修改）」自相矛盾。
        // 未开始的周期得能单独查出来做预告，但可见与可投必须分开：
        // 前端拿开放列表的 id 当「能不能投」的闸门，混进去就能投一个还没开始的周期。
        LocalDate today = LocalDate.now();
        List<Integer> created = new ArrayList<>();
        try {
            Integer notStarted = insert("测试-还没开始", today.plusDays(7), today.plusDays(30), 1, created);
            Integer openNow = insert("测试-正在开放", today.minusDays(1), today.plusDays(9), 1, created);
            Integer ended = insert("测试-已截止", today.minusDays(60), today.minusDays(30), 1, created);
            Integer disabled = insert("测试-未启用但未开始", today.plusDays(7), today.plusDays(30), 0, created);
            Integer startsToday = insert("测试-今天开始", today, today.plusDays(10), 1, created);

            List<Integer> upcoming = cycleMapper.findUpcomingForApplication(today).stream()
                    .map(RecruitmentCycle::getCycleId).toList();
            List<Integer> open = cycleMapper.findOpenForApplication(today).stream()
                    .map(RecruitmentCycle::getCycleId).toList();

            assertTrue(upcoming.contains(notStarted), "开始日期还没到的周期要能查到，用户端才做得出预告");
            assertFalse(upcoming.contains(openNow), "正在开放的不属于「即将开放」");
            assertFalse(upcoming.contains(ended), "已截止的不属于「即将开放」");
            assertFalse(upcoming.contains(disabled), "未启用的不该出现，管理员还没打算公开它");

            // 边界：今天开始就是今天能投，不是预告
            assertFalse(upcoming.contains(startsToday), "今天开始的周期属于开放，不是即将开放");
            assertTrue(open.contains(startsToday), "今天开始的周期今天就该能投");

            // 这条是重点：可见 != 可投
            assertFalse(open.contains(notStarted),
                    "未开始的周期绝不能进开放列表，否则用户能给还没开始的周期提交简历");
        } finally {
            created.forEach(cycleMapper::deleteById);
        }
    }

    @Test
    @DisplayName("即将开放按 start_date 升序，最快开始的排最前")
    void upcomingIsSortedByStartDateAscending() {
        LocalDate today = LocalDate.now();
        List<Integer> created = new ArrayList<>();
        try {
            Integer later = insert("测试-一个月后", today.plusDays(30), today.plusDays(60), 1, created);
            Integer sooner = insert("测试-三天后", today.plusDays(3), today.plusDays(20), 1, created);

            List<Integer> ids = cycleMapper.findUpcomingForApplication(today).stream()
                    .map(RecruitmentCycle::getCycleId).toList();

            // 预告卡片按「还有几天开始」自然排列；与开放列表的倒序相反是有意的
            assertTrue(ids.indexOf(sooner) < ids.indexOf(later),
                    "即将开放应按 start_date 升序: " + ids);
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
