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
     * 列出某场次绑定的面试官用户ID。
     */
    List<Integer> listInterviewerIds(Integer sessionId);
}
