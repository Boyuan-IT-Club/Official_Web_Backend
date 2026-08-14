package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.EvaluationBoardDTO;
import club.boyuan.official.domain.interview.dto.EvaluationBoardSeedDTO;
import club.boyuan.official.domain.resume.dto.ResumeDTO;

/**
 * 协同评价表的生命周期：开表、锁定、以及给协同服务提供播种数据。
 * <p>
 * 编辑期的真源是协同服务持有的 Y.Doc，本服务不碰单元格内容，只管「表存不存在、能不能写、有哪些行列」。
 *
 * @author dhy
 */
public interface IEvaluationBoardService {

    /**
     * 开启某周期的协同评价表：建立 collab_doc 记录。
     * 若已开启则原样返回，便于前端重复点击不出错。
     */
    EvaluationBoardDTO openBoard(Integer cycleId);

    /**
     * 查询评价表状态；未开启时抛 {@code EVALUATION_BOARD_NOT_OPENED}。
     */
    EvaluationBoardDTO getBoard(Integer cycleId);

    /**
     * 锁定/解锁评价表。锁定后协同服务拒绝一切写入，全员只读。
     */
    EvaluationBoardDTO setLocked(Integer cycleId, boolean locked);

    /**
     * 生成播种数据供协同服务构造 Y.Doc：列取自评分维度，行取自已分配的面试名单。
     * <p>
     * 协同服务在文档首次加载或定时对账时拉取，因此本方法必须可重复调用且结果稳定。
     */
    EvaluationBoardSeedDTO getSeed(Integer cycleId);

    /**
     * 评价表内速览候选人简历。
     * <p>
     * 面试官没有 {@code resume:view}，够不到简历库，但面试时必须看得到简历；
     * 这里以「该场次的面试官绑定」为准放行，范围恰好是他要面的那几个人。
     *
     * @param viewerUserId 当前查看者
     * @param admin        持有 {@code resume:audit} 时可越过绑定关系查看全部
     */
    ResumeDTO getCandidateResume(Integer cycleId, Integer scheduleId, Integer viewerUserId, boolean admin);
}
