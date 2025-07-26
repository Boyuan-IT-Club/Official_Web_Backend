package club.boyuan.official.exception;

import lombok.Getter;

/**
 * 自定义业务异常类
 * 用于统一处理业务逻辑中的异常情况
 */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;

    /**
     * 根据业务异常枚举创建异常实例
     * @param exceptionEnum 业务异常枚举
     */
    public BusinessException(BusinessExceptionEnum exceptionEnum) {
        this.code = exceptionEnum.getCode();
        this.message = exceptionEnum.getMessage();
    }

    /**
     * 根据业务异常枚举和自定义消息创建异常实例
     * @param exceptionEnum 业务异常枚举
     * @param message 自定义异常消息
     */
    public BusinessException(BusinessExceptionEnum exceptionEnum, String message) {
        this.code = exceptionEnum.getCode();
        this.message = message;
    }
}