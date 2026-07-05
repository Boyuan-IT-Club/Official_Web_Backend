package club.boyuan.official.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 异步任务 SSE：本机维护 SseEmitter，跨实例通过 Redis Pub/Sub 广播。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncTaskSseHub implements MessageListener {

    public static final String REDIS_TOPIC = "official:async-task:sse";

    private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    private final Map<String, CopyOnWriteArraySet<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @PostConstruct
    void subscribeRedis() {
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(REDIS_TOPIC));
    }

    public SseEmitter register(AsyncTaskChannel channel, String key) {
        String mapKey = mapKey(channel, key);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(mapKey, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(mapKey, emitter));
        emitter.onTimeout(() -> removeEmitter(mapKey, emitter));
        emitter.onError(ex -> removeEmitter(mapKey, emitter));
        return emitter;
    }

    public void publish(AsyncTaskChannel channel, String key, Object data, boolean terminal) {
        try {
            String payloadJson = objectMapper.writeValueAsString(data);
            dispatchLocal(channel, key, payloadJson, terminal);
            AsyncTaskSseEnvelope envelope = new AsyncTaskSseEnvelope(channel, key, payloadJson, terminal);
            stringRedisTemplate.convertAndSend(REDIS_TOPIC, objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            log.error("SSE 序列化失败 channel={}, key={}", channel, key, e);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            AsyncTaskSseEnvelope envelope = objectMapper.readValue(body, AsyncTaskSseEnvelope.class);
            if (envelope.getChannel() == null || envelope.getKey() == null) {
                return;
            }
            dispatchLocal(envelope.getChannel(), envelope.getKey(), envelope.getPayloadJson(), envelope.isTerminal());
        } catch (Exception ex) {
            log.warn("SSE Redis 消息解析失败 body={}", body, ex);
        }
    }

    private void dispatchLocal(AsyncTaskChannel channel, String key, String payloadJson, boolean terminal) {
        CopyOnWriteArraySet<SseEmitter> set = emitters.get(mapKey(channel, key));
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name("status").data(payloadJson));
                if (terminal) {
                    emitter.complete();
                    removeEmitter(mapKey(channel, key), emitter);
                }
            } catch (IOException ex) {
                removeEmitter(mapKey(channel, key), emitter);
            }
        }
    }

    private void removeEmitter(String mapKey, SseEmitter emitter) {
        CopyOnWriteArraySet<SseEmitter> set = emitters.get(mapKey);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(mapKey);
            }
        }
    }

    private static String mapKey(AsyncTaskChannel channel, String key) {
        return channel.name() + ":" + key;
    }
}
