package club.boyuan.official.domain.profile;

import club.boyuan.official.domain.profile.dto.CandidateProfileDetail;
import club.boyuan.official.domain.profile.dto.CandidateProfileListRow;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** 候选档案：管理端查看用户跨周期聚合数据（面试安排/评测成绩/获奖/简历）。 */
public interface IProfileService {

    /** 候选档案列表：收录「至少一条面试安排」的用户，按最近面试时间排序。 */
    List<CandidateProfileListRow> listCandidates(Integer cycleId);

    /** 候选档案详情：某用户全部周期的聚合视图。 */
    CandidateProfileDetail getProfile(Integer userId);
}