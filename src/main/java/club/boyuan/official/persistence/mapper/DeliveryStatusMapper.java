package club.boyuan.official.persistence.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface DeliveryStatusMapper {
    record State(int targetId, String typ, String mailState, LocalDateTime confirmedAt) {
        public boolean sent() { return "SENT".equals(mailState); }
    }

    @Select("SELECT target_id,typ,mail_state,confirmed_at FROM delivery_target_status WHERE cycle_id=#{cycle}")
    List<State> list(@Param("cycle") int cycle);

    default Map<String, Map<Integer, State>> byType(int cycle) {
        return list(cycle).stream().collect(Collectors.groupingBy(State::typ,
                Collectors.toMap(State::targetId, state -> state)));
    }

    static State state(Map<String, Map<Integer, State>> statuses, String type, int id) {
        return statuses.getOrDefault(type, Map.of()).getOrDefault(id, new State(id, type, "UNSENT", null));
    }

    static Set<Integer> sentIds(Map<String, Map<Integer, State>> statuses, String type) {
        return statuses.getOrDefault(type, Map.of()).values().stream().filter(State::sent)
                .map(State::targetId).collect(Collectors.toSet());
    }
}
