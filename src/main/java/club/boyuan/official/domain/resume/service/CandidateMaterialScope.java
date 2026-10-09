package club.boyuan.official.domain.resume.service;

/**
 * 面试官对候选人材料的可见范围。
 *
 * <p>面试官角色只持有 {@code console:access} 与 {@code interview:evaluate}，不在
 * {@link AttachmentAccess} 的权限白名单里 —— 按那条规则他看不到任何人的附件，
 * 可他正要面的那几个人的作品集恰恰是必须看的。
 *
 * <p>反过来把 {@code interview:evaluate} 直接塞进白名单也不行：附件 id 是连续整数，
 * 那等于让任意面试官顺着 id 把全部候选人的材料拖走，正是 AttachmentAccess 当初修掉的洞。
 *
 * <p>所以范围由场次绑定决定，与评价表里的简历速览同一条规则。实现落在面试域
 * （它才认识场次与绑定关系）；简历域只依赖这个接口，不反向依赖面试域。
 */
public interface CandidateMaterialScope {

    /**
     * viewer 是否是 candidate 某一场面试的面试官。
     *
     * @param viewerUserId    当前登录用户
     * @param candidateUserId 材料所属的候选人
     */
    boolean canSeeCandidateMaterials(Integer viewerUserId, Integer candidateUserId);
}
