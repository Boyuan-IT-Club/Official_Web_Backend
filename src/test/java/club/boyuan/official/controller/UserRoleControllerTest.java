package club.boyuan.official.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@AutoConfigureMockMvc
class UserRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

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