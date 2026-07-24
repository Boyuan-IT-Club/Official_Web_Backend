package club.boyuan.official.domain.user.controller;

import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.domain.user.service.UserRoleService;
import club.boyuan.official.persistence.entity.Role;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * UserRoleController 的权限与行为测试。
 * 通过 @MockBean 隔离服务层，聚焦验证 Spring Security 授权（401/403）与控制器成功路径（200），
 * 不依赖真实数据库中的种子数据，保证结果确定性。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRoleService userRoleService;

    @MockBean
    private IUserService userService;

    @BeforeEach
    void setUp() throws Exception {
        // 成功路径的确定性桩：无权限用例会在进入服务层前被 @PreAuthorize 拦截，故使用 lenient
        lenient().when(userRoleService.assignRoles(eq(1), anyList())).thenReturn(List.of(new Role()));
        lenient().when(userRoleService.addRoleToUser(1, 1)).thenReturn(new UserRole());
        lenient().when(userRoleService.removeRoleFromUser(1, 1)).thenReturn(true);
        lenient().when(userRoleService.getRolesByUserId(anyInt())).thenReturn(List.of(new Role()));
        lenient().when(userRoleService.getUsersByRoleId(eq(1), anyInt(), anyInt())).thenReturn(List.of(new User()));
        lenient().when(userRoleService.batchAssignRoles(anyList(), anyList())).thenReturn(1);

        User currentUser = new User();
        currentUser.setUserId(1);
        lenient().when(userService.getUserByUsername("user")).thenReturn(currentUser);
    }

    @Test
    @WithMockUser(authorities = "role:assign")
    void testAssignRolesWithPermission() throws Exception {
        // 测试具有role:assign权限的用户可以访问assignRoles方法
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user-roles")
                .param("userId", "1")
                .param("roleIds", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(authorities = "user:read")
    void testAssignRolesWithoutPermission() throws Exception {
        // 测试不具有role:assign权限的用户不能访问assignRoles方法
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user-roles")
                .param("userId", "1")
                .param("roleIds", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser
    void testGetRolesByUserIdWithAuthentication() throws Exception {
        // 测试已认证用户可以访问getRolesByUserId方法
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/1/roles")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void testGetRolesByUserIdWithoutAuthentication() throws Exception {
        // 测试未认证用户不能访问getRolesByUserId方法
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/1/roles")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "role:assign")
    void testAddRoleToUserWithPermission() throws Exception {
        // 测试具有role:assign权限的用户可以访问addRoleToUser方法
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user-roles/1/roles/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(authorities = "user:read")
    void testAddRoleToUserWithoutPermission() throws Exception {
        // 测试不具有role:assign权限的用户不能访问addRoleToUser方法
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user-roles/1/roles/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "role:assign")
    void testRemoveRoleFromUserWithPermission() throws Exception {
        // 测试具有role:assign权限的用户可以访问removeRoleFromUser方法
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/user-roles/1/roles/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(authorities = "user:read")
    void testRemoveRoleFromUserWithoutPermission() throws Exception {
        // 测试不具有role:assign权限的用户不能访问removeRoleFromUser方法
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/user-roles/1/roles/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "role:assign")
    void testGetUsersByRoleIdWithPermission() throws Exception {
        // 测试具有role:assign权限的用户可以访问getUsersByRoleId方法
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/role/1/users")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(authorities = "user:read")
    void testGetUsersByRoleIdWithoutPermission() throws Exception {
        // 测试不具有role:assign权限的用户不能访问getUsersByRoleId方法
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/role/1/users")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "role:assign")
    void testBatchAssignRolesWithPermission() throws Exception {
        // 测试具有role:assign权限的用户可以访问batchAssignRoles方法
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user-roles/batch")
                .param("userIds", "1")
                .param("roleIds", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(authorities = "user:read")
    void testBatchAssignRolesWithoutPermission() throws Exception {
        // 测试不具有role:assign权限的用户不能访问batchAssignRoles方法
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user-roles/batch")
                .param("userIds", "1")
                .param("roleIds", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser
    void testGetCurrentUserRolesWithAuthentication() throws Exception {
        // 测试已认证用户可以访问getCurrentUserRoles方法
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/me/roles")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void testGetCurrentUserRolesWithoutAuthentication() throws Exception {
        // 测试未认证用户不能访问getCurrentUserRoles方法
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/me/roles")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
