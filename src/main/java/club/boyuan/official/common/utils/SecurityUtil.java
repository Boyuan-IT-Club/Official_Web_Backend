package club.boyuan.official.common.utils;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类。
 * <p>
 * JWT 的解析与校验已由 {@code JwtAuthenticationFilter} 统一完成，并把用户名写入
 * {@link SecurityContextHolder}。Controller / Service 直接从这里读取当前登录用户，
 * 不应再自行解析 Authorization 头或校验 token（避免认证逻辑重复、重复查库）。
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户名；未认证时抛出业务异常。
     *
     * @return 当前登录用户名（即 JWT subject）
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_LOGIN);
        }
        return authentication.getName();
    }

    /**
     * 获取当前登录用户名；未认证时返回 {@code null}（用于允许匿名的场景）。
     */
    public static String getCurrentUsernameOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }
}
