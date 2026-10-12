package club.boyuan.official.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import club.boyuan.official.common.dto.ResponseMessage;
import club.boyuan.official.domain.interview.dto.UpdateInterviewTimeRequestDTO;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端错误必须是 4xx，不能掉进兜底变成 500。
 *
 * <p>500 会点着 5xx 告警 —— 运维看到的是「服务挂了」，实际只是某个页面少带了个参数。
 * 2026-10-12 的告警就是这么来的：学生端首屏 cycleId 还没加载完就发了
 * /interview/schedule/my 与 /preference/my，每开一次页面产生两个 500。
 *
 * <p>同一天之前那个「调时间一按就系统异常」也是同类：日期反序列化失败被包进
 * HttpMessageNotReadableException 后掉进兜底，前端只看到一句没有信息量的「系统异常」。
 */
class ClientErrorStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("少了必填 query 参数 → 400，并说清少了哪个")
    void missingParameterIsBadRequest() {
        MethodParameter p = org.mockito.Mockito.mock(MethodParameter.class);
        var ex = new MissingServletRequestParameterException("cycleId", "Integer");

        ResponseEntity<ResponseMessage<?>> res = handler.handleMissingParameter(ex);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().getMessage().contains("cycleId"),
                "得说清是哪个参数，否则前端还得翻服务器日志");
        assertNotEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
    }

    @Test
    @DisplayName("参数类型对不上（cycleId=abc）→ 400")
    void typeMismatchIsBadRequest() {
        var ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "cycleId", null, new NumberFormatException());

        ResponseEntity<ResponseMessage<?>> res = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().getMessage().contains("cycleId"));
    }

    @Test
    @DisplayName("请求体里某字段格式不对 → 400，并指出字段名")
    void unreadableBodyNamesTheField() throws Exception {
        // 真造一次反序列化失败，而不是手搓异常：格式变了测试才跟着变
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        InvalidFormatException cause = null;
        try {
            mapper.readValue("{\"interviewTime\":\"not-a-time\"}", UpdateInterviewTimeRequestDTO.class);
        } catch (InvalidFormatException e) {
            cause = e;
        }
        org.junit.jupiter.api.Assertions.assertNotNull(cause, "这段输入本该解析失败");

        ResponseEntity<ResponseMessage<?>> res = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("unreadable", cause, null));

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().getMessage().contains("interviewTime"),
                "要指出是哪个字段，这正是「调时间系统异常」当初难查的原因");
    }

    @Test
    @DisplayName("对外不泄露包名与解析器细节")
    void messageDoesNotLeakInternals() {
        ResponseEntity<ResponseMessage<?>> res = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("boom", new RuntimeException("内部细节"), null));

        String msg = res.getBody().getMessage();
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(msg.contains("请求体格式不正确"), msg);
        assertTrue(!msg.contains("club.boyuan") && !msg.contains("com.fasterxml"), msg);
    }

    /** 兜底仍然是 500：真正的服务端故障不能被降级成 400 藏起来 */
    @Test
    @DisplayName("非客户端错误仍然是 500")
    void genuineFailureStaysServerError() {
        ResponseEntity<ResponseMessage<?>> res =
                handler.handleGenericException(new IllegalStateException("数据库连不上"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
    }

    /** 顺带确认修复后的 DTO 仍然两种格式都收（与 UpdateInterviewTimeRequestDTOTest 呼应） */
    @Test
    @DisplayName("合法时间照常解析")
    void validTimeStillParses() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var dto = mapper.readValue("{\"interviewTime\":\"2026-10-11T19:45:00\"}",
                UpdateInterviewTimeRequestDTO.class);
        assertEquals(LocalDateTime.of(2026, 10, 11, 19, 45), dto.getInterviewTime());
    }
}
