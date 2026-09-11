package club.boyuan.official.domain.feedback.service;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.feedback.dto.FeedbackAdminView;
import club.boyuan.official.persistence.entity.Feedback;

public interface IFeedbackService {

    Feedback create(Integer userId, String category, String content, java.util.List<String> imageKeys);

    PageResultDTO<Feedback> pageMine(Integer userId, int page, int size);

    /** category / handled 为空表示该维度不筛 */
    PageResultDTO<FeedbackAdminView> pageAll(String category, Integer handled, int page, int size);

    /** 未处理条数，给管理端顶栏角标用 */
    long countUnhandled();

    /** 标记处理状态。handled=true 记录标记人与时间，取消则清空 */
    void markHandled(Long feedbackId, boolean handled, Integer operatorId);

    /**
     * 取某条反馈第 index 张截图的 objectKey，并校验调用者有权看。
     *
     * 权限在这里判而不是在控制器：本人和有 feedback:view 的人都能看，
     * 两个入口共用一条规则，分散写迟早对不上。
     */
    String imageKeyFor(Long feedbackId, int index, Integer requesterId, boolean isAdmin);
}
