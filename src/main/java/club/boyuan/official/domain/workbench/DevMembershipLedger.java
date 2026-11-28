package club.boyuan.official.domain.workbench;

import club.boyuan.official.common.utils.SecurityUtil;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 研发组绑定的版本。只在「是否拥有 website_dev_member」真正变化时加一，
 * 并锁住目标用户行，让同一账号的角色变更串行化。
 */
@Service
public class DevMembershipLedger {
    private static final Logger logger = LoggerFactory.getLogger(DevMembershipLedger.class);

    private final JdbcTemplate jdbc;

    public DevMembershipLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(int userId) {
        jdbc.query("SELECT user_id FROM `user` WHERE user_id = ? FOR UPDATE", rs -> {}, userId);
    }

    public boolean bound(int userId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM user_role ur
            JOIN role r ON r.role_id = ur.role_id AND r.role_code = ?
            WHERE ur.user_id = ?
            """, Integer.class, DevRole.CODE, userId);
        return count != null && count > 0;
    }

    /** 调用方已经比较过事务前后的绑定。重复添加或重复移出不会到这里。 */
    public void record(int userId, boolean before, boolean after) {
        if (before == after) return;
        int updated = jdbc.update(
            "UPDATE `user` SET workbench_membership_version = workbench_membership_version + 1 WHERE user_id = ?",
            userId);
        logger.info(
            "研发组成员资格变更 operator={} target={} role={} before={} after={} versionRows={}",
            SecurityUtil.getCurrentUsernameOrNull(), userId, DevRole.CODE, before, after, updated);
    }

    /** 永久注销时移除研发组绑定并推进版本。其他角色不动。 */
    public void exitIfBound(int userId) {
        lock(userId);
        if (!bound(userId)) return;
        jdbc.update("""
            DELETE ur FROM user_role ur
            JOIN role r ON r.role_id = ur.role_id AND r.role_code = ?
            WHERE ur.user_id = ?
            """, DevRole.CODE, userId);
        record(userId, true, false);
    }

    public Integer devRoleId() {
        List<Integer> ids = jdbc.query(
            "SELECT role_id FROM role WHERE role_code = ?",
            (rs, row) -> rs.getInt(1),
            DevRole.CODE);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
