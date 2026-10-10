package club.boyuan.official.domain.resume.service;

import java.util.Collection;
import java.util.Set;

/**
 * 谁能看某份简历的附件。
 *
 * 以前附件的列表与下载接口只要求「已登录」，真正的判断一处都没有——
 * 附件 id、简历 id 都是连续整数，任何一个登录的学生从 1 往上数，
 * 就能把所有人的作品集、成绩单下载下来。
 *
 * 规则：本人，或持有下列任一权限。这几个是现有角色里看候选人材料的全部入口：
 * 管理端简历详情（resume:view / resume:audit）、面试管理的预录取速览
 * （interview:result / interview:board:manage）。
 *
 * <p>面试官（只有 interview:evaluate）不在这份白名单里，但他要面的那几个人的附件
 * 必须看得到。那条路不走权限码而走场次绑定，见 {@link CandidateMaterialScope}：
 * 把 interview:evaluate 加进白名单等于让任意面试官顺着连续的附件 id 拖走全部材料。
 */
public final class AttachmentAccess {

    static final Set<String> VIEWER_AUTHORITIES = Set.of(
            "resume:view",
            "resume:audit",
            "interview:result",
            "interview:board:manage",
            // 超管兜底：它理应持有上面的权限码，但万一某次授权漏了，不该被关在自己的系统外面
            "ROLE_SUPER_ADMIN");

    /**
     * 持有这些权限码的人，可见范围不由权限码本身决定，还要再过一遍
     * {@link CandidateMaterialScope} 的场次绑定判定。
     */
    static final Set<String> SCOPED_AUTHORITIES = Set.of("interview:evaluate");

    private AttachmentAccess() {
    }

    public static boolean canView(Integer ownerUserId, Integer currentUserId, Collection<String> authorities) {
        if (currentUserId != null && currentUserId.equals(ownerUserId)) {
            return true;
        }
        return authorities != null && authorities.stream().anyMatch(VIEWER_AUTHORITIES::contains);
    }

    /**
     * 该用户是否属于「要再查一次场次绑定」的那一类（面试官）。
     * 返回 true 只说明值得去查，不等于放行。
     */
    public static boolean isScopedViewer(Collection<String> authorities) {
        return authorities != null && authorities.stream().anyMatch(SCOPED_AUTHORITIES::contains);
    }
}
