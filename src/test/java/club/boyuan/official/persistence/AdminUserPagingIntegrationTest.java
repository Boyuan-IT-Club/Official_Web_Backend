package club.boyuan.official.persistence;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端用户列表的分页与搜索语义。
 *
 * 线上故障:11 个用户只显示 10 个、第 2 页完全空白。前后端参数口径不一致 ——
 * 后端是 Spring Pageable(page 为 0 基页码、参数名 size),而前端发的是
 * 偏移量 +「pageSize」:
 *   第 1 页 page=0 碰巧对,但 pageSize 被忽略、回落到默认 10 条
 *   第 2 页 page=10 → 被当成第 10 页 → 空白
 * 同时 keyword 后端根本不接,搜索框点了没反应。
 *
 * 这类 bug 全是静默失败(没有异常、没有日志),只能靠测试守。
 *
 * 数据由本测试自己造:CI 是从零 Flyway 迁移的干净库,只有一个种子用户,
 * 依赖"库里本来就有几个人"的断言会在 CI 上必挂(初版就是这么挂的)。
 */
@SpringBootTest
class AdminUserPagingIntegrationTest {

    /** 造几个人:要能切出两页并留有余量 */
    private static final int SEEDED = 7;

    /**
     * 每个测试方法用独立后缀。
     *
     * UserMapper.deleteById 是软删(只置 is_deleted=1)，行还留在表里，而
     * uk_username 是普通唯一索引、不区分软删 —— 用固定用户名时第二个测试方法
     * 的 @BeforeEach 会撞 Duplicate entry(CI 上实测到)。
     */
    private String mark;

    @Autowired
    private IUserService userService;

    @Autowired
    private UserMapper userMapper;

    private final List<Integer> createdIds = new ArrayList<>();

    @BeforeEach
    void seed() {
        mark = "分页测试_" + System.nanoTime();
        for (int i = 0; i < SEEDED; i++) {
            User u = new User();
            u.setUsername("pg_" + mark + "_" + i);
            u.setPassword("x");                       // 只为满足 NOT NULL，不用于登录
            u.setEmail("pg_" + mark + "_" + i + "@example.invalid");
            u.setName(mark + "_" + i);
            u.setStatus(1);
            userMapper.insert(u);
            createdIds.add(u.getUserId());
        }
    }

    @AfterEach
    void cleanup() {
        createdIds.forEach(userMapper::deleteById);
        createdIds.clear();
    }

    @Test
    @DisplayName("分页按页码切分：两页不重叠，第二页非空")
    void pagingSplitsByPageIndexNotOffset() {
        // 用 keyword 把范围限定在本测试造的人身上，不受库里其它数据影响
        PageResultDTO<User> first = userService.getUsersByConditions(
                null, null, null, mark, PageRequest.of(0, 3, Sort.by("userId")), null);

        assertEquals(SEEDED, first.getTotalElements(), "总数应为本测试造的人数");
        assertEquals(3, first.getContent().size(), "第一页应恰好 3 条");

        PageResultDTO<User> second = userService.getUsersByConditions(
                null, null, null, mark, PageRequest.of(1, 3, Sort.by("userId")), null);

        assertFalse(second.getContent().isEmpty(),
                "第二页不能是空的 —— 这正是线上「点第2页啥都没有」的症状");
        assertEquals(3, second.getContent().size(), "第二页也应满 3 条（共 7 人）");

        Set<Integer> firstIds = first.getContent().stream()
                .map(User::getUserId).collect(Collectors.toSet());
        Set<Integer> secondIds = second.getContent().stream()
                .map(User::getUserId).collect(Collectors.toSet());
        assertTrue(firstIds.stream().noneMatch(secondIds::contains),
                "两页不该有重复用户，否则说明 page 被当成了偏移量");

        // 末页只剩 1 人，验证边界不越界
        PageResultDTO<User> last = userService.getUsersByConditions(
                null, null, null, mark, PageRequest.of(2, 3, Sort.by("userId")), null);
        assertEquals(1, last.getContent().size(), "7 人按每页 3 条切，末页应只有 1 条");
    }

    @Test
    @DisplayName("每页条数真的生效：要 5 条就给 5 条，不回落到默认 10")
    void pageSizeIsHonoured() {
        PageResultDTO<User> page = userService.getUsersByConditions(
                null, null, null, mark, PageRequest.of(0, 5, Sort.by("userId")), null);

        assertEquals(5, page.getContent().size(),
                "每页条数被忽略时会回落到 10（本测试有 7 人，会返回 7 条）—— "
                        + "那正是「11 个用户只看见 10 个」的原因");
    }

    @Test
    @DisplayName("keyword 匹配姓名与学号，且计数与内容一致")
    void keywordFiltersByNameOrUsername() {
        // 按姓名
        PageResultDTO<User> byName = userService.getUsersByConditions(
                null, null, null, mark, PageRequest.of(0, 50, Sort.by("userId")), null);
        assertEquals(SEEDED, byName.getContent().size(), "按姓名应命中全部造出来的人");
        assertEquals(SEEDED, byName.getTotalElements(),
                "计数查询也要带上 keyword，否则分页器会显示不存在的页数");

        // 按学号(username)
        String oneUsername = "pg_" + mark + "_3";
        PageResultDTO<User> byUsername = userService.getUsersByConditions(
                null, null, null, oneUsername, PageRequest.of(0, 50, Sort.by("userId")), null);
        assertEquals(1, byUsername.getContent().size(),
                "按学号搜索必须有结果 —— keyword 曾被后端整个忽略，搜索框形同虚设");
        assertEquals(oneUsername, byUsername.getContent().get(0).getUsername());
    }

    @Test
    @DisplayName("keyword 无匹配时返回空且总数为 0，而不是退化成全量")
    void keywordWithNoMatchReturnsEmpty() {
        PageResultDTO<User> none = userService.getUsersByConditions(
                null, null, null, "绝不可能存在的关键词_zzz_9f3a",
                PageRequest.of(0, 10, Sort.by("userId")), null);

        assertTrue(none.getContent().isEmpty(), "无匹配就该是空列表");
        assertEquals(0, none.getTotalElements(), "无匹配时总数必须是 0，不能退化成全量计数");
    }
}
