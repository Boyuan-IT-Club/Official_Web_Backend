package club.boyuan.official.domain.feedback.service;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.feedback.dto.FeedbackAdminView;
import club.boyuan.official.persistence.entity.Feedback;

public interface IFeedbackService {
    Feedback create(Integer userId, String content);
    PageResultDTO<Feedback> pageMine(Integer userId, int page, int size);
    PageResultDTO<FeedbackAdminView> pageAll(int page, int size);
}
