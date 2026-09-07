package club.boyuan.official.common.exception;

import club.boyuan.official.common.dto.ResponseMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * 回归：建场次时前端不再传 deptId，{@code @Valid} 校验失败曾落进兜底 Exception 分支，
     * 前端只看到 500「系统异常」。现在应是 400 并带上字段级提示。
     */
    @Test
    void validationFailure_returns400WithFieldMessage_notSystemError() throws Exception {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "request");
        br.addError(new FieldError("request", "deptId", null, false, null, null, "部门ID不能为空"));
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, br);

        ResponseEntity<ResponseMessage<?>> resp = handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED.getCode(), resp.getBody().getCode());
        assertEquals("部门ID不能为空", resp.getBody().getMessage());
    }

    @Test
    void validationFailure_joinsMultipleFieldMessages() throws Exception {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "request");
        br.addError(new FieldError("request", "location", null, false, null, null, "面试地点不能为空"));
        br.addError(new FieldError("request", "capacity", null, false, null, null, "容量至少为1"));
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);

        ResponseEntity<ResponseMessage<?>> resp =
                handler.handleMethodArgumentNotValid(new MethodArgumentNotValidException(param, br));

        String msg = resp.getBody().getMessage();
        assertTrue(msg.contains("面试地点不能为空") && msg.contains("容量至少为1"), msg);
    }

    @SuppressWarnings("unused")
    void sample(String arg) {
    }
}
