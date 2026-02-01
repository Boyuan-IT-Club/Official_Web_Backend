package club.boyuan.official.service;

import club.boyuan.official.dto.SendNotificationsRequestDTO;
import club.boyuan.official.dto.SendNotificationsResponseDTO;
import club.boyuan.official.entity.InterviewResult;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.validation.Valid;

/**
 * <p>
 * 面试结果表 服务类
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
public interface IInterviewResultService extends IService<InterviewResult> {

    SendNotificationsResponseDTO sendNotifications(@Valid SendNotificationsRequestDTO requestDTO);
}
