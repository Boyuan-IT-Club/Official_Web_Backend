package club.boyuan.official.filter;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.utils.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.security.Key;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    private String validToken;
    private String invalidToken = "invalid-jwt-token";
    private String secretKey;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        String username = "testuser";
        Integer userId = 1;
        List<String> roles = List.of("USER");
        List<String> permissions = List.of("user:read");

        // 生成有效Token
        validToken = jwtTokenUtil.generateToken(username, userId, roles, permissions);

        // 模拟Redis黑名单检查
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
    }

    @Test
    void testFilterWithValidToken() throws Exception {
        // 测试使用有效Token访问需要认证的接口
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/1/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void testFilterWithInvalidToken() throws Exception {
        // 测试使用无效Token访问需要认证的接口
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/1/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(result -> {
                    String content = result.getResponse().getContentAsString();
                    ResponseMessage<?> response = new ObjectMapper().readValue(content, ResponseMessage.class);
                    assertEquals(401, response.getCode());
                });
    }

    @Test
    void testFilterWithoutToken() throws Exception {
        // 测试没有Token访问需要认证的接口
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user-roles/1/roles")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void testFilterWithPublicEndpoint() throws Exception {
        // 测试访问公开接口（不需要Token）
        mockMvc.perform(MockMvcRequestBuilders.get("/api/health")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    private void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", Actual: " + actual);
        }
    }
}