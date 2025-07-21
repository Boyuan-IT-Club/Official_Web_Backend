package club.boyuan.official.exception;

import lombok.Getter;

/**
 * 业务异常枚举类
 * 统一管理错误码和错误消息
 */
@Getter
public enum BusinessExceptionEnum {
    // 用户注册相关异常
    USERNAME_ALREADY_EXISTS(1001, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(1002, "邮箱已存在"),
    INVALID_EMAIL_FORMAT(1003, "邮箱格式不正确，必须以@stu.ecnu.edu.cn结尾"),
    INVALID_PHONE_FORMAT(1004, "手机号格式不正确"),
    INVALID_PHONE_LENGTH(1005, "手机号长度不正确"),
    USER_NOT_FOUND(1006, "用户不存在"),
    LOGIN_FAILED(1007, "登录失败"),
    UNSUPPORTED_AUTH_TYPE(1008, "不支持的认证方式");

    private final int code;
    private final String message;

    BusinessExceptionEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}