package club.boyuan.official.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

class RedisValueCompatibilityTest {
    @Test
    @SuppressWarnings("unchecked")
    void readsAndReproducesCachedValuesFromThePreviousRelease() {
        var template = new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));
        var serializer = (RedisSerializer<Object>) template.getValueSerializer();
        byte[] previous = """
                ["java.util.LinkedHashMap",{"cycleId":7,"phase":"active"}]"""
                .getBytes(StandardCharsets.UTF_8);
        var expected = new LinkedHashMap<String, Object>();
        expected.put("cycleId", 7);
        expected.put("phase", "active");
        assertThat(serializer.deserialize(previous)).isEqualTo(expected);
        assertThat(serializer.serialize(expected)).isEqualTo(previous);
    }
}
