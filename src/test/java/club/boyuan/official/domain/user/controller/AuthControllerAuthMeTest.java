package club.boyuan.official.domain.user.controller;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.common.utils.JwtTokenUtil;
import club.boyuan.official.common.utils.MessageUtils;
import club.boyuan.official.domain.user.service.ILoginService;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.messaging.EmailVerificationProducer;
import club.boyuan.official.domain.user.dto.AuthLoginDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthController 的 /api/auth/me 与 login permissionCodes 契约单元测试。
 * 纯 Mockito 单测(不依赖 SpringContext/DB):getAuthMe 只依赖 JwtTokenUtil,
 * login 只依赖 jwtTokenUtil + loginService 桩。验证:
 * - /auth/me 返回最小身份(userId/roleNames/permissionCodes,不含 PII)
 * - 缺 Authorization 头 → 401
 * - login 响应包含 permissionCodes
 * - 无效 token → 401
 */
class AuthControllerAuthMeTest {

    private final JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
    private final ILoginService loginService = mock(ILoginService.class);
    private final IUserService userService = mock(IUserService.class);
    private final MessageUtils messageUtils = mock(MessageUtils.class);
    private final EmailVerificationProducer emailVerificationProducer = mock(EmailVerificationProducer.class);
    private final AuthController controller = new AuthController(
            loginService, userService, jwtTokenUtil, messageUtils, emailVerificationProducer);

    @Test
    void authMe_returnsMinimalIdentity() {
        when(jwtTokenUtil.extractUserId("tok")).thenReturn(42);
        when(jwtTokenUtil.extractRoleNames("tok")).thenReturn(List.of("申请人"));
        when(jwtTokenUtil.extractPermissionCodes("tok")).thenReturn(List.of("candidate:read:own"));
        club.boyuan.official.persistence.entity.User u = new club.boyuan.official.persistence.entity.User();
        u.setName("申请人甲");
        when(userService.getUserById(42)).thenReturn(u);

        ResponseEntity<?> resp = controller.getAuthMe("Bearer tok");
        assertEquals(200, resp.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) extractData(resp);
        assertEquals(42, data.get("userId"));
        assertEquals(List.of("申请人"), data.get("roleNames"));
        assertEquals(List.of("candidate:read:own"), data.get("permissionCodes"));
        assertEquals("申请人甲", data.get("name")); // 档案字段(#89 follow-up)
        // 最小暴露:身份字段 + name,不含 user 全量/PII
        assertNull(data.get("user"));
        assertNull(data.get("awardExperiences"));
        assertNull(data.get("phone"));
        assertEquals(4, data.size());
    }

    @Test
    void authMe_missingToken_returns401() {
        ResponseEntity<?> resp = controller.getAuthMe(null);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void authMe_invalidToken_returns401() {
        when(jwtTokenUtil.extractUserId("bad")).thenThrow(new RuntimeException("expired"));
        ResponseEntity<?> resp = controller.getAuthMe("Bearer bad");
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void authMe_nonBearerHeader_returns401() {
        ResponseEntity<?> resp = controller.getAuthMe("Token abc");
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void login_includesPermissionCodes() {
        // 构造一次合法登录:username-password,响应 data 含 token + user
        AuthLoginDTO dto = new AuthLoginDTO();
        dto.setAuth_type("username-password");
        dto.setAuth_id("alice");
        dto.setVerify("secret");

        club.boyuan.official.persistence.entity.User user = new club.boyuan.official.persistence.entity.User();
        user.setUserId(7);
        when(userService.getUserByUsername("alice")).thenReturn(user);

        // loginService 返回 data 为 TokenVO(与 LoginServiceImpl 契约一致)
        var tokenVO = new club.boyuan.official.domain.user.service.impl.LoginServiceImpl.TokenVO("tok");
        club.boyuan.official.common.dto.ResponseMessage<Object> loginResp =
                new club.boyuan.official.common.dto.ResponseMessage<>(200, "ok", tokenVO);
        when(jwtTokenUtil.extractRoleNames("tok")).thenReturn(List.of("申请人"));
        when(jwtTokenUtil.extractPermissionCodes("tok")).thenReturn(List.of("candidate:read:own"));
        org.mockito.Mockito.doAnswer(inv -> loginResp).when(loginService)
                .loginByUsernamePassword("alice", "secret");
        ResponseEntity<?> resp = controller.login(dto);
        assertEquals(200, resp.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) extractData(resp);
        assertEquals(7, data.get("user_id"));
        assertEquals("tok", data.get("token"));
        assertEquals(List.of("申请人"), data.get("roleNames"));
        assertEquals(List.of("candidate:read:own"), data.get("permissionCodes"));
        verify(jwtTokenUtil).extractPermissionCodes("tok");
    }

    private Object extractData(ResponseEntity<?> resp) {
        Object body = resp.getBody();
        assertTrue(body instanceof club.boyuan.official.common.dto.ResponseMessage<?>);
        return ((club.boyuan.official.common.dto.ResponseMessage<?>) body).getData();
    }
}