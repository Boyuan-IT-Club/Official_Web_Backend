package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.persistence.entity.InterviewSlot;
import club.boyuan.official.persistence.mapper.InterviewSlotMapper;
import club.boyuan.official.domain.interview.service.impl.InterviewSlotInventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewSlotInventoryServiceImplTest {

    private static final String REMAIN_KEY_PREFIX = "interview:slot:remain:";

    @Mock
    private InterviewSlotMapper interviewSlotMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private InterviewSlotInventoryServiceImpl inventoryService;

    @Test
    void tryOccupy_fastRejectedWhenRedisRemainIsZero_doesNotTouchDb() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REMAIN_KEY_PREFIX + 1)).thenReturn("0");

        boolean occupied = inventoryService.tryOccupy(1);

        assertFalse(occupied);
        verify(interviewSlotMapper, never()).occupyOneIfAvailable(any());
    }

    @Test
    void tryOccupy_successWhenDbUpdatesRow_decrementsRedis() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REMAIN_KEY_PREFIX + 2)).thenReturn("3");
        when(interviewSlotMapper.occupyOneIfAvailable(2)).thenReturn(1);
        when(stringRedisTemplate.hasKey(REMAIN_KEY_PREFIX + 2)).thenReturn(true);
        when(valueOperations.decrement(REMAIN_KEY_PREFIX + 2)).thenReturn(2L);

        boolean occupied = inventoryService.tryOccupy(2);

        assertTrue(occupied);
        verify(interviewSlotMapper).occupyOneIfAvailable(2);
    }

    @Test
    void tryOccupy_nullSlotReturnsFalse() {
        assertFalse(inventoryService.tryOccupy(null));
        verify(interviewSlotMapper, never()).occupyOneIfAvailable(any());
    }

    @Test
    void syncRemainCacheFromDb_computesRemainAsCapacityMinusOccupied() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(interviewSlotMapper.selectById(5)).thenReturn(
                new InterviewSlot().setMaxCapacity(10).setCurrentOccupied(4));

        inventoryService.syncRemainCacheFromDb(5);

        verify(valueOperations).set(eq(REMAIN_KEY_PREFIX + 5), eq("6"), anyLong(), eq(TimeUnit.HOURS));
    }

    @Test
    void syncRemainCacheFromDb_clampsNegativeRemainToZero() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(interviewSlotMapper.selectById(6)).thenReturn(
                new InterviewSlot().setMaxCapacity(2).setCurrentOccupied(5));

        inventoryService.syncRemainCacheFromDb(6);

        verify(valueOperations).set(eq(REMAIN_KEY_PREFIX + 6), eq("0"), anyLong(), eq(TimeUnit.HOURS));
    }

    @Test
    void syncRemainCacheFromDb_deletesKeyWhenSlotMissing() {
        when(interviewSlotMapper.selectById(9)).thenReturn(null);

        inventoryService.syncRemainCacheFromDb(9);

        verify(stringRedisTemplate).delete(REMAIN_KEY_PREFIX + 9);
    }
}
