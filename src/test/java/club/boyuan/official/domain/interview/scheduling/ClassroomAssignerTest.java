package club.boyuan.official.domain.interview.scheduling;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassroomAssignerTest {

    private final LocalDateTime slot = LocalDateTime.of(2025, 9, 27, 9, 0);

    @Test
    void assignsUpToThreeClassroomsPerTimeSlot() {
        ClassroomAssigner assigner = new ClassroomAssigner();

        assertEquals("教室1", assigner.assignClassroom(slot));
        assertEquals("教室2", assigner.assignClassroom(slot));
        assertEquals("教室3", assigner.assignClassroom(slot));
        // 第4个请求应无可用教室
        assertNull(assigner.assignClassroom(slot));
    }

    @Test
    void hasAvailableClassroomReflectsRemainingCapacity() {
        ClassroomAssigner assigner = new ClassroomAssigner();

        assertTrue(assigner.hasAvailableClassroom(slot));
        assigner.assignClassroom(slot);
        assigner.assignClassroom(slot);
        assertTrue(assigner.hasAvailableClassroom(slot));
        assigner.assignClassroom(slot);
        assertFalse(assigner.hasAvailableClassroom(slot));
    }

    @Test
    void differentTimeSlotsTrackedIndependently() {
        ClassroomAssigner assigner = new ClassroomAssigner();
        LocalDateTime other = slot.plusMinutes(10);

        assigner.assignClassroom(slot);
        assigner.assignClassroom(slot);
        assigner.assignClassroom(slot);
        assertFalse(assigner.hasAvailableClassroom(slot));

        // 另一时间点互不影响，仍可分配
        assertTrue(assigner.hasAvailableClassroom(other));
        assertNotNull(assigner.assignClassroom(other));
    }
}
