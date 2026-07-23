package club.boyuan.official.infra.seckill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterviewBookingRedisKeysTest {

    @Test
    void remain_usesSlotPrefix() {
        assertEquals("interview:slot:remain:42", InterviewBookingRedisKeys.remain(42));
    }

    @Test
    void userCycleLock_combinesUserAndCycle() {
        assertEquals("booking:seckill:user:cycle:7:3", InterviewBookingRedisKeys.userCycleLock(7, 3));
    }

    @Test
    void requestAndIdempotentAndProcessed_useDistinctPrefixes() {
        assertEquals("booking:seckill:request:req-1", InterviewBookingRedisKeys.requestStatus("req-1"));
        assertEquals("booking:seckill:idempotent:k-1", InterviewBookingRedisKeys.idempotent("k-1"));
        assertEquals("booking:seckill:processed:req-1", InterviewBookingRedisKeys.processed("req-1"));
    }
}
