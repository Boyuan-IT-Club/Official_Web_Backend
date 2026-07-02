package club.boyuan.official.service;

import club.boyuan.official.dto.*;

import java.util.List;

/**
 * 学生面试时段预约服务。
 * <p>
 * 学生可提交一个或多个 {@code interview_slot} 大时段；服务端按志愿部门选择最终 slot，
 * 成功预约后按最终 slot 容量均分写入 {@code interview_schedule.interview_time}。
 */
public interface IInterviewBookingService {

    /**
     * 列出某周期可预约时段（排除已关闭的 slot，并计算 fullyBooked）。
     *
     * @param resumeSubmittedOnly 为 true 时要求简历已提交（status≥2）
     */
    List<InterviewBookableSlotDTO> listBookableSlots(Integer userId, Integer cycleId, boolean resumeSubmittedOnly);

    /**
     * 创建或更新预约：无记录则新建；有记录则按新的候选时段重新分配或复用已取消记录。
     */
    InterviewBookingDTO createOrUpdateBooking(Integer userId, CreateInterviewBookingRequestDTO request);

    /** 查询用户在某周期的有效预约，无则返回 null */
    InterviewBookingDTO getMyBooking(Integer userId, Integer cycleId);

    /** 改期到新的一个或多个候选 slot（bookingId = scheduleId） */
    InterviewBookingDTO rescheduleBooking(Integer userId, Integer scheduleId, UpdateInterviewBookingRequestDTO request);

    /** 取消预约并释放 slot 占用 */
    void cancelBooking(Integer userId, Integer scheduleId);

    /** 管理员分页列表，可按是否已写入精确 interviewTime 筛选 */
    InterviewBookingAdminListResponseDTO listBookingsForAdmin(
            Integer cycleId, Boolean hasFineInterviewTime, Integer page, Integer size);
}
