package club.boyuan.official.domain.feedback.service.impl;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.feedback.dto.FeedbackAdminView;
import club.boyuan.official.domain.feedback.service.IFeedbackService;
import club.boyuan.official.persistence.entity.Feedback;
import club.boyuan.official.persistence.mapper.FeedbackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements IFeedbackService {

    /** 最多三张截图：再多管理端也看不过来，每张还要单独走一次鉴权取图 */
    private static final int MAX_IMAGES = 3;
    private static final String DEFAULT_CATEGORY = "other";

    private final FeedbackMapper feedbackMapper;

    @Override
    public Feedback create(Integer userId, String category, String content, List<String> imageKeys) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setCategory(category == null || category.isBlank() ? DEFAULT_CATEGORY : category);
        feedback.setContent(content.trim());
        // 空数组和 null 都存成 null：JSON_LENGTH 对空串会报错，统一成一种空值
        feedback.setImageKeys(imageKeys == null || imageKeys.isEmpty()
                ? null : imageKeys.stream().limit(MAX_IMAGES).toList());
        feedback.setIsDeleted(0);
        // createdAt / updatedAt 交给 MyBatis-Plus 的自动填充，不再手写：
        // 手写会和 FieldFill 打架，两处时间源以后必然对不上
        feedbackMapper.insert(feedback);
        return feedback;
    }

    @Override
    public PageResultDTO<Feedback> pageMine(Integer userId, int page, int size) {
        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(page + 1, size),
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getCreatedAt)
                        .orderByDesc(Feedback::getFeedbackId));
        return pageResult(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public PageResultDTO<FeedbackAdminView> pageAll(String category, int page, int size) {
        int offset = page * size;
        long total = feedbackMapper.countAdmin(category);
        return pageResult(feedbackMapper.selectAdminPage(category, offset, size), total, page, size);
    }

    @Override
    public String imageKeyFor(Long feedbackId, int index, Integer requesterId, boolean isAdmin) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(BusinessExceptionEnum.FEEDBACK_NOT_FOUND);
        }
        // 本人或有 feedback:view 的人才能看。反馈里常有截图带着个人信息，
        // 不能靠「猜不到 id」当防线
        if (!isAdmin && !feedback.getUserId().equals(requesterId)) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }
        List<String> keys = feedback.getImageKeys();
        if (keys == null || index < 0 || index >= keys.size()) {
            throw new BusinessException(BusinessExceptionEnum.FEEDBACK_NOT_FOUND);
        }
        return keys.get(index);
    }

    private <T> PageResultDTO<T> pageResult(List<T> records, long total, int page, int size) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResultDTO<>(records, total, totalPages, page, size,
                page == 0, totalPages == 0 || page >= totalPages - 1);
    }
}
