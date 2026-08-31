package club.boyuan.official.persistence;

import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户部门分配与取消分配。
 *
 * 两个线上问题:
 *   1. user 表有 dept(varchar) 与 dept_id(int) 两列，界面读 dept，
 *      而 batchUpdateDeptByIds 只写 dept_id —— 「分配部门」在列表上看不出
 *      任何变化(用户很早就报过)，两列还越走越偏(实测 8 人里 6 人不一致)。
 *   2. 部门一旦分配就撤不回来:controller 与 service 都把空部门判成非法。
 */
@SpringBootTest
class UserDeptAssignmentIntegrationTest {

    /** V6 种子部门:1=技术部 2=综合部 */
    private static final String DEPT_A = "技术部";
    private static final String DEPT_B = "综合部";

    @Autowired
    private IUserService userService;

    @Autowired
    private UserMapper userMapper;

    private final List<Integer> created = new ArrayList<>();

    @BeforeEach
    void seed() {
        // 用唯一后缀:deleteById 是软删，行还在，uk_username 不区分软删
        String tag = String.valueOf(System.nanoTime());
        User u = new User();
        u.setUsername("dept_" + tag);
        u.setPassword("x");
        u.setEmail("dept_" + tag + "@example.invalid");
        u.setName("部门测试_" + tag);
        u.setStatus(1);
        userMapper.insert(u);
        created.add(u.getUserId());
    }

    @AfterEach
    void cleanup() {
        created.forEach(userMapper::deleteById);
        created.clear();
    }

    @Test
    @DisplayName("分配部门后，界面读的那一列(dept)真的变了")
    void assigningUpdatesTheDisplayedColumn() {
        Integer id = created.get(0);

        userService.batchUpdateUserDept(List.of(id), DEPT_A);

        User after = userMapper.selectById(id);
        assertEquals(DEPT_A, after.getDept(),
                "dept 是列表展示用的列 —— 只写 dept_id 会让「分配部门」看起来没反应");
    }

    @Test
    @DisplayName("重新分配到另一个部门也生效，不残留旧值")
    void reassigningOverwrites() {
        Integer id = created.get(0);

        userService.batchUpdateUserDept(List.of(id), DEPT_A);
        userService.batchUpdateUserDept(List.of(id), DEPT_B);

        assertEquals(DEPT_B, userMapper.selectById(id).getDept());
    }

    @Test
    @DisplayName("取消分配：dept 传 null 时清空，而不是被当成非法入参拒绝")
    void clearingRemovesDept() {
        Integer id = created.get(0);
        userService.batchUpdateUserDept(List.of(id), DEPT_A);
        assertEquals(DEPT_A, userMapper.selectById(id).getDept(), "前置条件：先分配上");

        int affected = userService.batchUpdateUserDept(List.of(id), null);

        assertEquals(1, affected,
                "清空必须真的影响到行 —— SQL 里的 EXISTS(dept_name = NULL) 恒不成立，"
                        + "不跳过它会静默更新 0 行");
        assertNull(userMapper.selectById(id).getDept(), "取消分配后 dept 应为空");
    }

    @Test
    @DisplayName("不存在的部门名不会写脏数据")
    void unknownDeptIsRejectedByExistsGuard() {
        Integer id = created.get(0);
        userService.batchUpdateUserDept(List.of(id), DEPT_A);

        userService.batchUpdateUserDept(List.of(id), "并不存在的部门_zzz");

        assertEquals(DEPT_A, userMapper.selectById(id).getDept(),
                "部门名不存在时应被 EXISTS 挡住，保持原值而不是写进一个野字符串");
    }
}
