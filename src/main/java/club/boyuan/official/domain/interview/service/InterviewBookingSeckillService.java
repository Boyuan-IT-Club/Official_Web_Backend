package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.CreateInterviewBookingRequestDTO;
import club.boyuan.official.domain.interview.dto.InterviewBookingAsyncResultDTO;
import club.boyuan.official.domain.interview.dto.InterviewBookingDTO;

/**
 * 秒杀级面试预约（Lua + MQ）。
 */
public interface InterviewBookingSeckillService {

    InterviewBookingAsyncResultDTO submitSeckillBooking(
            Integer userId, CreateInterviewBookingRequestDTO request, String idempotencyKey);

    InterviewBookingAsyncResultDTO getRequestStatus(Integer userId, String requestId);

    void markRequestSuccess(String requestId, InterviewBookingDTO booking);

    void markRequestFailed(String requestId, String message, Integer slotId, Integer userId, Integer cycleId);
}
