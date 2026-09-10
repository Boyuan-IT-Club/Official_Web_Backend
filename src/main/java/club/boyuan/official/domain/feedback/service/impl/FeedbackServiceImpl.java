package club.boyuan.official.domain.feedback.service.impl;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.feedback.dto.FeedbackAdminView;
import club.boyuan.official.domain.feedback.service.IFeedbackService;
import club.boyuan.official.persistence.entity.Feedback;
import club.boyuan.official.persistence.mapper.FeedbackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements IFeedbackService {

    private final FeedbackMapper feedbackMapper;

    @Override
    public Feedback create(Integer userId, String content) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(content.trim());
        LocalDateTime now = LocalDateTime.now();
        feedback.setCreatedAt(now);
        feedback.setUpdatedAt(now);
        feedback.setIsDeleted(0);
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
    public PageResultDTO<FeedbackAdminView> pageAll(int page, int size) {
        int offset = page * size;
        long total = feedbackMapper.countAdmin();
        return pageResult(feedbackMapper.selectAdminPage(offset, size), total, page, size);
    }

    private <T> PageResultDTO<T> pageResult(java.util.List<T> records, long total, int page, int size) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResultDTO<>(records, total, totalPages, page, size,
                page == 0, totalPages == 0 || page >= totalPages - 1);
    }
}
