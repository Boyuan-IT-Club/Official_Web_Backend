package club.boyuan.official.common.utils;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.Locale;

/**
 * GitHub 账号(登录名)规范化工具。
 *
 * <p>用户资料里的 {@code github} 字段从"主页地址"演变为"登录名绑定键":
 * 统一归一化后存储、按归一化后的值比较,作为 Autograder 评测提交的归属键
 * (Actions 推送携带的 {@code github_username} 就是登录名)。</p>
 */
public final class GitHubAccountUtil {

    private GitHubAccountUtil() {
    }

    /**
     * 把用户输入的 GitHub 地址/登录名归一化为登录名。
     *
     * <p>支持:裸登录名、{@code https://github.com/xxx}、{@code github.com/xxx}、
     * {@code git@github.com:xxx/repo.git}、带尾斜杠/路径段的变体。
     * 空串/纯分隔符输入返回 {@code null}(语义:解绑)。</p>
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // ssh 形式:git@host:owner/repo.git → 去掉 user@ 前缀
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }
        // 去掉协议头
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        }
        // 去掉 www.
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        // 剥掉主机名(到第一个 '/' 或 ':'):host/path 或 host:path → 只剩 path
        // 裸登录名(如 "Zewang0217")无分隔符,原样保留
        int slash = s.indexOf('/');
        int colon = s.indexOf(':');
        int sep = (colon >= 0 && (slash < 0 || colon < slash)) ? colon : slash;
        if (sep >= 0) {
            s = s.substring(sep + 1);
        }
        // 取第一个路径段:.../Zewang0217/repo → Zewang0217
        int firstSlash = s.indexOf('/');
        if (firstSlash >= 0) {
            s = s.substring(0, firstSlash);
        }
        // 裸 "owner.git" 形式
        if (s.endsWith(".git")) {
            s = s.substring(0, s.length() - 4);
        }
        s = s.trim();
        if (s.isEmpty()) {
            return null;
        }
        // GitHub 登录名大小写不敏感,统一小写作为规范形
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * 校验归一化后的登录名未被其他用户占用;已占用抛 {@link BusinessException}。
     * {@code normalizedGithub} 为 {@code null} 时直接通过(解绑场景)。
     *
     * @param excludeUserId 排除的用户(更新自身时传入自身 ID),可为 null
     */
    public static void assertNotBound(UserMapper userMapper, String normalizedGithub, Integer excludeUserId) {
        if (normalizedGithub == null) {
            return;
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getGithub, normalizedGithub)
                .ne(excludeUserId != null, User::getUserId, excludeUserId));
        if (count != null && count > 0) {
            throw new BusinessException(BusinessExceptionEnum.GITHUB_ALREADY_BOUND);
        }
    }
}