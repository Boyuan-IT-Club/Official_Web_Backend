package club.boyuan.official.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenUtilTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private JwtTokenUtil jwtTokenUtil;

    private Key signingKey;
    private String secretKey = "test-secret-key-for-jwt-token-generation-and-validation"; 

    @BeforeEach
    void setUp() {
        // 设置JWT密钥
        ReflectionTestUtils.setField(jwtTokenUtil, "secretKey", secretKey);
        // 设置过期时间
        ReflectionTestUtils.setField(jwtTokenUtil, "expirationTime", 3600000L); // 1小时
        
        signingKey = Keys.hmacShaKeyFor(secretKey.getBytes());
        
        // 模拟Redis黑名单检查
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
    }

    @Test
    void testGenerateToken() {
        // 准备测试数据
        String username = "testuser";
        Integer userId = 1;
        List<String> roles = List.of("USER", "ADMIN");
        List<String> permissions = List.of("user:read", "user:write");

        // 生成Token
        String token = jwtTokenUtil.generateToken(username, userId, roles, permissions);

        // 验证Token是否生成成功
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // 解析Token，验证内容是否正确
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(username, claims.getSubject());
        assertEquals(userId, claims.get("userId", Integer.class));
        assertEquals(roles, claims.get("roles", List.class));
        assertEquals(permissions, claims.get("permissions", List.class));
    }

    @Test
    void testValidateToken() {
        // 准备测试数据
        String username = "testuser";
        Integer userId = 1;
        List<String> roles = List.of("USER");
        List<String> permissions = List.of("user:read");

        // 生成Token
        String token = jwtTokenUtil.generateToken(username, userId, roles, permissions);

        // 验证Token是否有效
        assertTrue(jwtTokenUtil.validateToken(token, username));
    }

    @Test
    void testExtractUsername() {
        // 准备测试数据
        String username = "testuser";
        Integer userId = 1;
        List<String> roles = Collections.emptyList();
        List<String> permissions = Collections.emptyList();

        // 生成Token
        String token = jwtTokenUtil.generateToken(username, userId, roles, permissions);

        // 从Token中提取用户名
        String extractedUsername = jwtTokenUtil.extractUsername(token);

        // 验证提取的用户名是否正确
        assertEquals(username, extractedUsername);
    }

    @Test
    void testExtractRoles() {
        // 准备测试数据
        String username = "testuser";
        Integer userId = 1;
        List<String> roles = List.of("USER", "ADMIN");
        List<String> permissions = Collections.emptyList();

        // 生成Token
        String token = jwtTokenUtil.generateToken(username, userId, roles, permissions);

        // 从Token中提取角色
        List<String> extractedRoles = jwtTokenUtil.extractRoles(token);

        // 验证提取的角色是否正确
        assertEquals(roles, extractedRoles);
    }

    @Test
    void testExtractPermissions() {
        // 准备测试数据
        String username = "testuser";
        Integer userId = 1;
        List<String> roles = Collections.emptyList();
        List<String> permissions = List.of("user:read", "user:write");

        // 生成Token
        String token = jwtTokenUtil.generateToken(username, userId, roles, permissions);

        // 从Token中提取权限
        List<String> extractedPermissions = jwtTokenUtil.extractPermissions(token);

        // 验证提取的权限是否正确
        assertEquals(permissions, extractedPermissions);
    }

    @Test
    void testValidateTokenWithInvalidToken() {
        // 准备无效的Token
        String invalidToken = "invalid-jwt-token";

        // 验证无效Token
        assertFalse(jwtTokenUtil.validateToken(invalidToken, "testuser"));
    }

    @Test
    void testExtractUserId() {
        // 准备测试数据
        String username = "testuser";
        Integer userId = 1;
        List<String> roles = Collections.emptyList();
        List<String> permissions = Collections.emptyList();

        // 生成Token
        String token = jwtTokenUtil.generateToken(username, userId, roles, permissions);

        // 从Token中提取用户ID
        Integer extractedUserId = jwtTokenUtil.extractUserId(token);

        // 验证提取的用户ID是否正确
        assertEquals(userId, extractedUserId);
    }
}