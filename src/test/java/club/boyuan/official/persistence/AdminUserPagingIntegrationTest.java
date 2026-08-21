package club.boyuan.official.persistence;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端用户列表的分页与搜索语义。
 *
 * 线上故障:11 个用户只显示 10 个、第 2 页完全空白。原因是前后端参数口径不一致 ——
 * 后端是 Spring Pageable（page 为 0 基页码、参数名 size），而前端发的是
 * 偏移量 +「pageSize」:
 *   第 1 页 page=0 碰巧对，但 pageSize 被忽略、回落到默认 10 条
 *   第 2 页 page=10 → 被当成第 10 页 → 空白
 * 同时 keyword 后端根本不接，搜索框点了没反应。
 *
 * 这类 bug 全是静默失败(没有异常、没有日志),只能靠测试守。
 */
@SpringBootTest
class AdminUserPagingIntegrationTest {

    @Autowired
    private IUserService userService;

    @Test
    @DisplayName("分页按页码切分：两页不重叠，且能覆盖到总数")
    void pagingSplitsByPageIndexNotOffset() {
        PageResultDTO<User> first = userService.getUsersByConditions(
                null, null, null, null,
                PageRequest.of(0, 5, Sort.by("userId")), null);
        assertNotNull(first);
        long total = first.getTotalElements();
        assertTrue(total > 5,
                "本测试需要总数 > 5 才有第二页可验；当前 total=" + total);

        assertEquals(5, first.getContent().size(), "第一页应恰好 5 条");

        PageResultDTO<User> second = userService.getUsersByConditions(
                null, null, null, null,
                PageRequest.of(1, 5, Sort.by("userId")), null);

        assertFalse(second.getContent().isEmpty(),
                "第二页不能是空的 —— 这正是线上「点第2页啥都没有」的症状");

        Set<Integer> firstIds = first.getContent().stream()
                .map(User::getUserId).collect(Collectors.toSet());
        Set<Integer> secondIds = second.getContent().stream()
                .map(User::getUserId).collect(Collectors.toSet());
        assertTrue(firstIds.stream().noneMatch(secondIds::contains),
                "两页不该有重复用户，否则说明 page 被当成了偏移量");
    }

    @Test
    @DisplayName("每页条数真的生效：要 3 条就给 3 条，不回落到默认 10")
    void pageSizeIsHonoured() {
        PageResultDTO<User> page = userService.getUsersByConditions(
                null, null, null, null,
                PageRequest.of(0, 3, Sort.by("userId")), null);

        assertTrue(page.getTotalElements() >= 3, "需要至少 3 个用户");
        assertEquals(3, page.getContent().size(),
                "每页条数被忽略时会回落到 10 —— 那正是「11 个用户只看见 10 个」的原因");
    }

    @Test
    @DisplayName("keyword 匹配姓名或学号，且计数与内容一致")
    void keywordFiltersByNameOrUsername() {
        PageResultDTO<User> all = userService.getUsersByConditions(
                null, null, null, null,
                PageRequest.of(0, 200, Sort.by("userId")), null);
        List<User> pool = all.getContent();
        assertFalse(pool.isEmpty(), "库里得有用户");

        User probe = pool.stream()
                .filter(u -> u.getUsername() != null && u.getUsername().length() >= 4)
                .findFirst()
                .orElseThrow(() -> new AssertionError("需要一个 username 长度 >= 4 的用户"));

        PageResultDTO<User> hit = userService.getUsersByConditions(
                null, null, null, probe.getUsername(),
                PageRequest.of(0, 50, Sort.by("userId")), null);

        assertFalse(hit.getContent().isEmpty(),
                "按学号搜索必须有结果 —— keyword 曾被后端整个忽略，搜索框形同虚设");
        assertTrue(hit.getContent().stream()
                        .anyMatch(u -> probe.getUsername().equals(u.getUsername())),
                "结果里应包含被搜的那个人");
        assertEquals(hit.getContent().size(), hit.getTotalElements(),
                "计数查询也要带上 keyword，否则分页器会显示不存在的页数");
    }

    @Test
    @DisplayName("keyword 无匹配时返回空结果且总数为 0，而不是退化成全量")
    void keywordWithNoMatchReturnsEmpty() {
        PageResultDTO<User> none = userService.getUsersByConditions(
                null, null, null, "绝不可能存在的关键词_zzz_9f3a",
                PageRequest.of(0, 10, Sort.by("userId")), null);

        assertTrue(none.getContent().isEmpty(), "无匹配就该是空列表");
        assertEquals(0, none.getTotalElements(), "无匹配时总数必须是 0，不能退化成全量计数");
    }
}
