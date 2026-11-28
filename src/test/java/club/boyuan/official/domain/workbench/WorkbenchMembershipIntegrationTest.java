package club.boyuan.official.domain.workbench;

import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.domain.user.service.UserRoleService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "workbench.directory-token=member-contract-fixture")
@AutoConfigureMockMvc
class WorkbenchMembershipIntegrationTest {
    private static final String PUBLIC = "/api/workbench";
    private static final String INTERNAL = "/api/internal/workbench";
    private final String manager = "r1-manager-" + UUID.randomUUID();
    private final String candidate = "r1-user-" + UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRoleService roles;
    @Autowired IUserService users;
    private int managerId;
    private int target;
    private int dev;
    private int admin;

    @BeforeEach
    void seed() {
        managerId = account(manager);
        target = account(candidate);
        dev = role(DevRole.CODE);
        admin = role("ADMIN");
        roles.assignRoles(managerId, List.of(admin, dev));
    }

    private int account(String name) {
        jdbc.update("INSERT INTO `user`(username,password,name,email) VALUES (?,?,?,?)",
            name, "test-only", name, name + "@example.invalid");
        return jdbc.queryForObject("SELECT user_id FROM `user` WHERE username=?", Integer.class, name);
    }

    private int role(String code) {
        return jdbc.queryForObject("SELECT role_id FROM role WHERE role_code=?", Integer.class, code);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM `user` WHERE username IN (?,?)", manager, candidate);
    }

    @Test
    void protectsBothCredentialsAndPreservesWireTypes() throws Exception {
        mvc.perform(get(PUBLIC + "/access-context")).andExpect(status().isUnauthorized());
        mvc.perform(get(PUBLIC + "/member-candidates").with(user(candidate))).andExpect(status().isForbidden());
        mvc.perform(get(INTERNAL + "/dev-members").with(user(manager))).andExpect(status().isForbidden());
        mvc.perform(get(INTERNAL + "/dev-members")).andExpect(status().isUnauthorized());
        mvc.perform(get(PUBLIC + "/access-context").with(user(manager))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(Integer.toString(managerId)))
            .andExpect(jsonPath("$.data.membershipVersion").value("1"))
            .andExpect(jsonPath("$.data.isDevMember").value(true))
            .andExpect(jsonPath("$.data.canManageMembers").value(true));
        mvc.perform(directory(post(INTERNAL + "/member-names")).contentType(MediaType.APPLICATION_JSON)
                .content("[" + managerId + "]")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").isNumber()).andExpect(jsonPath("$.data[0].id").value(managerId));
        mvc.perform(get(PUBLIC + "/member-candidates").with(user(manager)).param("q", candidate))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(Integer.toString(target)))
            .andExpect(jsonPath("$.data.items[0].isMember").value(false));
        mvc.perform(directory(get(INTERNAL + "/dev-members")).param("cursor", Integer.toString(managerId - 1))
                .param("limit", "1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].userId").value(Integer.toString(managerId)));
    }

    @Test
    void concurrentRetriesAndOtherRolePathsAdvanceOnlyRealChanges() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);
        try {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                mvc.perform(put(PUBLIC + "/dev-members/{id}", target).with(user(manager)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.membershipVersion").value("1"));
                return true;
            })).toList();
            for (var call : calls) call.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        // General role administration and profile edits must agree with the workbench point query.
        roles.assignRoles(target, List.of(dev, admin));
        roles.batchAssignRoles(List.of(target), List.of(dev));
        jdbc.update("UPDATE `user` SET name=? WHERE user_id=?", "Edited", target);
        point("1", true, false, true);
        mvc.perform(delete(PUBLIC + "/dev-members/{id}", target).with(user(manager)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.membershipVersion").value("2"));
        mvc.perform(delete(PUBLIC + "/dev-members/{id}", target).with(user(manager)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.membershipVersion").value("2"));
        roles.assignRoles(target, List.of(role("MEMBER")));
        roles.batchAssignRoles(List.of(target), List.of(dev));
        jdbc.update("UPDATE `user` SET status=0 WHERE user_id=?", target);
        point("3", true, false, false);
        users.softDeleteUser(target);
        point("4", false, true, false);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id=? AND role_id=?",
            Integer.class, target, dev));
    }

    private void point(String version, boolean bound, boolean cancelled, boolean enabled) throws Exception {
        mvc.perform(directory(get(INTERNAL + "/dev-members/{id}", target))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(Integer.toString(target)))
            .andExpect(jsonPath("$.data.membershipVersion").value(version))
            .andExpect(jsonPath("$.data.bound").value(bound)).andExpect(jsonPath("$.data.cancelled").value(cancelled))
            .andExpect(jsonPath("$.data.accountEnabled").value(enabled));
    }

    private MockHttpServletRequestBuilder directory(MockHttpServletRequestBuilder request) {
        return request.header("X-Service-Token", "member-contract-fixture");
    }
}
