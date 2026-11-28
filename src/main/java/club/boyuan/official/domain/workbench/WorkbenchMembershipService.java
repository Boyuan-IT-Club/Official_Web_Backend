package club.boyuan.official.domain.workbench;

import club.boyuan.official.common.utils.SecurityUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import club.boyuan.official.domain.user.service.UserRoleService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 按数据库里的账号、角色绑定和有效权限计算工作台准入，并只增删研发组这一条绑定。 */
@Service
public class WorkbenchMembershipService {
    private static final Logger logger = LoggerFactory.getLogger(WorkbenchMembershipService.class);

    public record AccessContext(
        String userId,
        String name,
        @JsonProperty("isDevMember") boolean isDevMember,
        boolean canAccess,
        boolean canManageMembers,
        String membershipVersion,
        boolean cancelled
    ) {}

    public record DevMemberState(String userId, boolean bound, String membershipVersion, boolean cancelled) {}

    public record Candidate(String id, String name, boolean isMember) {}

    public record CandidatePage(List<Candidate> items, boolean hasMore) {}

    public record Snapshot(
        String userId,
        boolean bound,
        boolean cancelled,
        boolean accountEnabled,
        String membershipVersion
    ) {}

    public record SnapshotPage(List<Snapshot> items, String nextCursor) {}

    public record Name(long id, String name) {}

    private record Account(int id, String name, int status, boolean deleted, long version) {}

    private final JdbcTemplate jdbc;
    private final DevMembershipLedger ledger;
    private final UserRoleService roles;

    public WorkbenchMembershipService(JdbcTemplate jdbc, DevMembershipLedger ledger, UserRoleService roles) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.roles = roles;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccessContext accessContext() {
        Account account = operator();
        return contextOf(account);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CandidatePage candidates(String raw, int page, int size) {
        requireManager();
        CandidateSearch.Query query = CandidateSearch.parse(raw, page, size);
        if (query.blank()) return new CandidatePage(List.of(), false);
        long offset = (query.page() - 1L) * query.size();
        List<Candidate> rows = jdbc.query("""
            SELECT u.user_id, u.name,
              EXISTS (
                SELECT 1 FROM user_role ur
                JOIN role r ON r.role_id = ur.role_id AND r.role_code = ?
                WHERE ur.user_id = u.user_id
              ) AS bound
            FROM `user` u
            WHERE u.is_deleted = 0 AND u.status = 1
              AND (u.name LIKE ? ESCAPE '\\\\' OR (? IS NOT NULL AND u.user_id = ?))
            ORDER BY u.name ASC, u.user_id ASC
            LIMIT ? OFFSET ?
            """,
            (rs, row) -> new Candidate(Integer.toString(rs.getInt(1)), rs.getString(2), rs.getBoolean(3)),
            DevRole.CODE,
            CandidateSearch.like(query.text()),
            query.exactId(),
            query.exactId(),
            query.size() + 1,
            offset);
        boolean hasMore = rows.size() > query.size();
        if (hasMore) rows = new ArrayList<>(rows.subList(0, query.size()));
        return new CandidatePage(rows, hasMore);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public DevMemberState add(int userId) {
        Account operator = lockForChange(userId);
        Account target = requirePresent(userId);
        if (target.deleted()) throw missing();
        if (target.status() != 1) throw new WorkbenchMembershipException(HttpStatus.CONFLICT, "账号已停用");
        Integer roleId = requireRole();
        if (!ledger.bound(target.id())) roles.addRoleToUser(target.id(), roleId);
        DevMemberState state = stateOf(target.id());
        logger.info(
            "工作台添加研发组成员 operator={} target={} role={} bound={} version={}",
            operator.id(), state.userId(), DevRole.CODE, state.bound(), state.membershipVersion());
        return state;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public DevMemberState remove(int userId) {
        Account operator = lockForChange(userId);
        Account target = requirePresent(userId);
        if (target.deleted()) {
            ledger.exitIfBound(target.id());
        } else if (ledger.bound(target.id())) {
            Integer roleId = requireRole();
            roles.removeRoleFromUser(target.id(), roleId);
        }
        DevMemberState state = stateOf(target.id());
        logger.info(
            "工作台移出研发组成员 operator={} target={} role={} bound={} version={}",
            operator.id(), state.userId(), DevRole.CODE, state.bound(), state.membershipVersion());
        return state;
    }

    public SnapshotPage reconcile(String cursor, int limit) {
        long after = parseCursor(cursor);
        int size = limit < 1 ? 200 : Math.min(limit, 500);
        List<Snapshot> rows = jdbc.query("""
            SELECT u.user_id, u.status, u.is_deleted, u.workbench_membership_version,
              EXISTS (
                SELECT 1 FROM user_role ur
                JOIN role r ON r.role_id = ur.role_id AND r.role_code = ?
                WHERE ur.user_id = u.user_id
              ) AS bound
            FROM `user` u
            WHERE u.user_id > ?
              AND (
                u.workbench_membership_version > 0
                OR EXISTS (
                  SELECT 1 FROM user_role ur
                  JOIN role r ON r.role_id = ur.role_id AND r.role_code = ?
                  WHERE ur.user_id = u.user_id
                )
              )
            ORDER BY u.user_id ASC
            LIMIT ?
            """,
            (rs, row) -> snapshot(rs.getInt(1), rs.getInt(2), rs.getInt(3) != 0, rs.getLong(4), rs.getBoolean(5)),
            DevRole.CODE, after, DevRole.CODE, size + 1);
        String next = null;
        if (rows.size() > size) {
            rows = new ArrayList<>(rows.subList(0, size));
            next = rows.get(rows.size() - 1).userId();
        }
        return new SnapshotPage(rows, next);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot one(int userId) {
        Account account = account(userId);
        if (account == null) {
            return new Snapshot(Integer.toString(userId), false, true, false, "0");
        }
        return snapshot(account.id(), account.status(), account.deleted(), account.version(), ledger.bound(account.id()));
    }

    public List<Name> names(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0 || id > Integer.MAX_VALUE)) {
            throw new WorkbenchMembershipException(HttpStatus.BAD_REQUEST, "每次可查询 1 至 100 个成员");
        }
        String marks = String.join(",", ids.stream().map(id -> "?").toList());
        Object[] args = ids.stream().map(Long::intValue).toArray();
        return jdbc.query(
            "SELECT user_id, name FROM `user` WHERE is_deleted = 0 AND user_id IN (" + marks + ")",
            (rs, row) -> new Name(rs.getLong(1), rs.getString(2)),
            args);
    }

    private Snapshot snapshot(int id, int status, boolean deleted, long version, boolean bound) {
        boolean cancelled = deleted;
        return new Snapshot(
            Integer.toString(id),
            bound && !cancelled,
            cancelled,
            status == 1 && !cancelled,
            Long.toString(version));
    }

    private DevMemberState stateOf(int userId) {
        Account account = requirePresent(userId);
        boolean bound = !account.deleted() && ledger.bound(userId);
        return new DevMemberState(Integer.toString(userId), bound, Long.toString(account.version()), account.deleted());
    }

    private AccessContext contextOf(Account account) {
        boolean bound = !account.deleted() && ledger.bound(account.id());
        boolean roleEnabled = roleEnabled();
        boolean access = !account.deleted() && account.status() == 1 && bound && roleEnabled && permitted(account.id(), DevRole.ACCESS);
        boolean manage = access && permitted(account.id(), DevRole.MANAGE);
        return new AccessContext(
            Integer.toString(account.id()),
            account.name(),
            bound,
            access,
            manage,
            Long.toString(account.version()),
            account.deleted());
    }

    private boolean roleEnabled() {
        Integer status = jdbc.query("""
            SELECT status FROM role WHERE role_code = ?
            """, rs -> rs.next() ? rs.getInt(1) : null, DevRole.CODE);
        return status != null && status == 1;
    }

    private boolean permitted(int userId, String code) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM user_role ur
            JOIN role r ON r.role_id = ur.role_id AND r.status = 1
            JOIN role_permission rp ON rp.role_id = r.role_id
            JOIN permission p ON p.permission_id = rp.permission_id
            WHERE ur.user_id = ? AND p.permission_code = ?
            """, Integer.class, userId, code);
        return count != null && count > 0;
    }

    private Account lockForChange(int targetId) {
        int operatorId = operator().id();
        ledger.lock(Math.min(operatorId, targetId));
        if (operatorId != targetId) ledger.lock(Math.max(operatorId, targetId));
        return requireManager();
    }

    private Account requireManager() {
        Account account = operator();
        if (!contextOf(account).canManageMembers()) {
            throw new WorkbenchMembershipException(HttpStatus.FORBIDDEN, "没有成员管理权限");
        }
        return account;
    }

    private Account operator() {
        String username = SecurityUtil.getCurrentUsername();
        List<Account> rows = jdbc.query("""
            SELECT user_id, name, status, is_deleted, workbench_membership_version
            FROM `user` WHERE username = ?
            """, (rs, row) -> new Account(
            rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4) != 0, rs.getLong(5)), username);
        if (rows.isEmpty()) throw new WorkbenchMembershipException(HttpStatus.UNAUTHORIZED, "登录已失效");
        return rows.get(0);
    }

    private Account requirePresent(int userId) {
        Account account = account(userId);
        if (account == null) throw missing();
        return account;
    }

    private Account account(int userId) {
        List<Account> rows = jdbc.query("""
            SELECT user_id, name, status, is_deleted, workbench_membership_version
            FROM `user` WHERE user_id = ?
            """, (rs, row) -> new Account(
            rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4) != 0, rs.getLong(5)), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Integer requireRole() {
        Integer roleId = ledger.devRoleId();
        if (roleId == null) {
            throw new WorkbenchMembershipException(HttpStatus.SERVICE_UNAVAILABLE, "研发组角色尚未初始化");
        }
        return roleId;
    }

    private static WorkbenchMembershipException missing() {
        return new WorkbenchMembershipException(HttpStatus.NOT_FOUND, "目标账号不存在");
    }

    private static long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            long value = Long.parseLong(cursor);
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new WorkbenchMembershipException(HttpStatus.BAD_REQUEST, "游标无效");
        }
    }
}
