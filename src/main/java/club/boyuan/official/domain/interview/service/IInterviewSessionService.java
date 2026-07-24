package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.CreateInterviewSessionRequestDTO;
import club.boyuan.official.domain.interview.dto.InterviewSessionDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewSessionRequestDTO;
import club.boyuan.official.persistence.entity.InterviewSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 面试场次服务
 */
public interface IInterviewSessionService extends IService<InterviewSession> {

    InterviewSession createSession(CreateInterviewSessionRequestDTO request);

    InterviewSession updateSession(Integer sessionId, UpdateInterviewSessionRequestDTO request);

    void deleteSession(Integer sessionId);

    /**
     * 列出某周期的场次（可按部门过滤），并补全部门名 / 时间窗信息。
     *
     * @param deptId       为空则不按部门过滤
     * @param onlyAvailable true 时仅返回状态可用且尚有剩余名额的场次
     */
    List<InterviewSessionDTO> listSessionDTOs(Integer cycleId, Integer deptId, boolean onlyAvailable);

    InterviewSessionDTO toDTO(InterviewSession session);
}
