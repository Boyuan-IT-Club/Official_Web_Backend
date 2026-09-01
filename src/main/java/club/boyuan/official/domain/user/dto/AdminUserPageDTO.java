package club.boyuan.official.domain.user.dto;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.persistence.entity.User;

/**
 * 管理端用户列表的分页结果,在通用分页字段之外附带三个全库统计数。
 *
 * 为什么单独开一个子类而不是往 PageResultDTO 上加字段:那是所有分页接口共用的
 * 通用 DTO,加进去会让其它接口的响应凭空多出三个恒为 null 的键。
 *
 * 为什么需要这三个数:管理端顶部四张统计卡读的是 memberCount / nonMemberCount /
 * frozenCount,而后端从来没返回过这三个键,所以除「总数」外三张卡一直恒显示 0。
 * 它们统计的是全库,不随当前分页/筛选变化。
 */
public class AdminUserPageDTO extends PageResultDTO<User> {

    private long memberCount;
    private long nonMemberCount;
    private long frozenCount;

    public AdminUserPageDTO(PageResultDTO<User> page, long memberCount, long nonMemberCount, long frozenCount) {
        super(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getCurrentPage(), page.getPageSize(), page.isFirst(), page.isLast());
        this.memberCount = memberCount;
        this.nonMemberCount = nonMemberCount;
        this.frozenCount = frozenCount;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
    }

    public long getNonMemberCount() {
        return nonMemberCount;
    }

    public void setNonMemberCount(long nonMemberCount) {
        this.nonMemberCount = nonMemberCount;
    }

    public long getFrozenCount() {
        return frozenCount;
    }

    public void setFrozenCount(long frozenCount) {
        this.frozenCount = frozenCount;
    }
}
