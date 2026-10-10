package club.boyuan.official.domain.interview.service;

import java.util.List;

/**
 * 面试场次与面试官的绑定关系。
 *
 * @author dhy
 */
public interface ISessionInterviewerService {

    /**
     * 整场覆盖绑定：传空列表即解绑该场次全部面试官。
     */
    List<Integer> bindInterviewers(Integer sessionId, List<Integer> userIds);

    /**
     * 自助加入：把某人补进该场次的面试官，不动已有绑定。
     * <p>
     * 与 {@link #bindInterviewers} 的区别是增量而非覆盖 —— 面试当天在评价表上
     * 临时顶班，若复用覆盖式接口会把同场其他面试官一并删掉。
     * 幂等：已绑定时原样返回。
     *
     * @return 该场次绑定后的全部面试官用户ID
     */
    List<Integer> joinAsInterviewer(Integer sessionId, Integer userId);

    /**
     * 列出某场次绑定的面试官用户ID。
     */
    List<Integer> listInterviewerIds(Integer sessionId);
}
