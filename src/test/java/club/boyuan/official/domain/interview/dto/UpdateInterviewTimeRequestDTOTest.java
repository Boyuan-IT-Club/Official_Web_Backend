package club.boyuan.official.domain.interview.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 「调时间 / 地点」的入参反序列化。
 *
 * <p>这里原先钉死成 {@code @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")}（不含秒），
 * 而前端两个调用方发的都是 {@code dayjs().format('YYYY-MM-DDTHH:mm:00')} —— 带字面的 :00。
 * 严格模式解析到第 16 位就抛 DateTimeParseException，请求直接 400，界面上只剩一句
 * 「系统异常」。功能从加上那天起就没通过，线上 2026-10-10 当天刷了一串同样的栈。
 */
class UpdateInterviewTimeRequestDTOTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UpdateInterviewTimeRequestDTO parse(String json) throws Exception {
        return mapper.readValue(json, UpdateInterviewTimeRequestDTO.class);
    }

    @Test
    @DisplayName("带秒：前端实际发的就是这种，必须收")
    void acceptsSeconds() throws Exception {
        UpdateInterviewTimeRequestDTO dto = parse("{\"interviewTime\":\"2026-10-11T19:45:00\"}");
        assertEquals(LocalDateTime.of(2026, 10, 11, 19, 45), dto.getInterviewTime());
    }

    @Test
    @DisplayName("不带秒：旧格式仍要收，别把别的调用方弄坏")
    void acceptsWithoutSeconds() throws Exception {
        UpdateInterviewTimeRequestDTO dto = parse("{\"interviewTime\":\"2026-10-11T19:45\"}");
        assertEquals(LocalDateTime.of(2026, 10, 11, 19, 45), dto.getInterviewTime());
    }

    @Test
    @DisplayName("非零的秒照常解析，不被悄悄抹掉")
    void keepsNonZeroSeconds() throws Exception {
        UpdateInterviewTimeRequestDTO dto = parse("{\"interviewTime\":\"2026-10-11T19:45:30\"}");
        assertEquals(LocalDateTime.of(2026, 10, 11, 19, 45, 30), dto.getInterviewTime());
    }

    @Test
    @DisplayName("换场次是可选的，不传就是只改时间")
    void sessionIdIsOptional() throws Exception {
        assertNull(parse("{\"interviewTime\":\"2026-10-11T19:45:00\"}").getSessionId());
        assertEquals(29, parse("{\"interviewTime\":\"2026-10-11T19:45:00\",\"sessionId\":29}").getSessionId());
    }
}
