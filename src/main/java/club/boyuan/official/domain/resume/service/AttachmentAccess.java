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
 */
public final class AttachmentAccess {

    static final Set<String> VIEWER_AUTHORITIES = Set.of(
            "resume:view",
            "resume:audit",
            "interview:result",
            "interview:board:manage",
            // 超管兜底：它理应持有上面的权限码，但万一某次授权漏了，不该被关在自己的系统外面
            "ROLE_SUPER_ADMIN");

    private AttachmentAccess() {
    }

    public static boolean canView(Integer ownerUserId, Integer currentUserId, Collection<String> authorities) {
        if (currentUserId != null && currentUserId.equals(ownerUserId)) {
            return true;
        }
        return authorities != null && authorities.stream().anyMatch(VIEWER_AUTHORITIES::contains);
    }
}
