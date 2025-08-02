package club.boyuan.official.exception;

import lombok.Getter;

/**
 * 业务异常枚举类
 * 统一管理错误码和错误消息
 */
@Getter
public enum BusinessExceptionEnum {
    USERNAME_ALREADY_EXISTS(1001, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(1002, "邮箱已存在"),
    PHONE_ALREADY_EXISTS(1003, "手机号已存在"),
    INVALID_EMAIL_FORMAT(1004, "邮箱格式不正确，必须以@stu.ecnu.edu.cn结尾"),
    INVALID_PHONE_FORMAT(1005, "手机号格式不正确"),
    INVALID_PHONE_LENGTH(1006, "手机号长度不正确"),
    USER_NOT_FOUND(1007, "用户不存在"),
    LOGIN_FAILED(1008, "登录失败"),
    UNSUPPORTED_AUTH_TYPE(1009, "不支持的认证方式"),
    REGISTER_FAILED(1010, "注册失败"),
    PASSWORD_ENCRYPT_ERROR(1011, "密码加密失败"),
    INVALID_CAPTCHA(1012, "验证码无效或已过期"),
    USER_INFO_UPDATE_FAILED(1013, "用户信息更新失败"),
    USER_INFO_NOT_FOUND(1014, "用户信息不存在"),
    INVALID_USER_DATA(1015, "用户数据格式无效"),
    AWARD_EXPERIENCE_NOT_FOUND(1016, "获奖经历不存在"),
    AWARD_EXPERIENCE_ADD_FAILED(1017, "获奖经历添加失败"),
    AWARD_EXPERIENCE_UPDATE_FAILED(1018, "获奖经历更新失败"),
    AWARD_EXPERIENCE_DELETE_FAILED(1019, "获奖经历删除失败"),
    PERMISSION_DENIED(1020, "权限拒绝"),
    MISSING_REQUIRED_FIELD(1021, "缺少必填字段"),
    JWT_VERIFICATION_FAILED(1022, "JWT验证失败"),
    JWT_EXPIRED(1023, "JWT令牌已过期"),
    JWT_INVALID(1024, "JWT令牌无效"),
    AUTHENTICATION_FAILED(1025, "认证失败"),
    INVALID_VERIFICATION_CODE(1026, "验证码无效"),
    JWT_HAS_BEEN_LOGGED_OUT(1027, "JWT已注销"),
    PASSWORD_TOO_SIMPLE(1028, "密码过于简单"),
    SYSTEM_ERROR(500, "系统错误");

    private final int code;
    private final String message;

    BusinessExceptionEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}