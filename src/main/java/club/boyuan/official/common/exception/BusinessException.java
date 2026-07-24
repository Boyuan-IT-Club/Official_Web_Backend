package club.boyuan.official.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 自定义业务异常类
 * 用于统一处理业务逻辑中的异常情况。
 * <p>
 * 异常自带对应的 HTTP 状态码（来自 {@link BusinessExceptionEnum}），
 * GlobalExceptionHandler 直接读取即可，无需再维护错误码到状态码的映射。
 */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    /**
     * 根据业务异常枚举创建异常实例
     * @param exceptionEnum 业务异常枚举
     */
    public BusinessException(BusinessExceptionEnum exceptionEnum) {
        this.code = exceptionEnum.getCode();
        this.message = exceptionEnum.getMessage();
        this.httpStatus = exceptionEnum.getHttpStatus();
    }

    /**
     * 根据业务异常枚举和自定义消息创建异常实例
     * @param exceptionEnum 业务异常枚举
     * @param message 自定义异常消息
     */
    public BusinessException(BusinessExceptionEnum exceptionEnum, String message) {
        this.code = exceptionEnum.getCode();
        this.message = message;
        this.httpStatus = exceptionEnum.getHttpStatus();
    }
}
