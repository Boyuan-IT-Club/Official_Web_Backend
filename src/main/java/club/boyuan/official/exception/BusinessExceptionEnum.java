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
    INVALID_EMAIL_FORMAT(1003, "邮箱格式不正确，必须以@stu.ecnu.edu.cn结尾"),
    INVALID_PHONE_FORMAT(1004, "手机号格式不正确"),
    INVALID_PHONE_LENGTH(1005, "手机号长度不正确"),
    USER_NOT_FOUND(1006, "用户不存在"),
    LOGIN_FAILED(1007, "登录失败"),
    UNSUPPORTED_AUTH_TYPE(1008, "不支持的认证方式"),
    REGISTER_FAILED(1009, "注册失败"),
    PASSWORD_ENCRYPT_ERROR(1010, "密码加密失败"),
    INVALID_CAPTCHA(1011, "验证码无效或已过期"),
    USER_INFO_UPDATE_FAILED(1012, "用户信息更新失败"),
    USER_INFO_NOT_FOUND(1013, "用户信息不存在"),
    INVALID_USER_DATA(1014, "用户数据格式无效"),
    AWARD_EXPERIENCE_NOT_FOUND(1015, "获奖经历不存在"),
    AWARD_EXPERIENCE_ADD_FAILED(1016, "获奖经历添加失败"),
    AWARD_EXPERIENCE_UPDATE_FAILED(1017, "获奖经历更新失败"),
    AWARD_EXPERIENCE_DELETE_FAILED(1018, "获奖经历删除失败"),
    PERMISSION_DENIED(1020, "权限拒绝"),
    MISSING_REQUIRED_FIELD(1021, "缺少必填字段"),
    JWT_EXPIRED(1023, "JWT令牌已过期"),
    JWT_INVALID(1024, "JWT令牌无效"),
    AUTHENTICATION_FAILED(1025, "认证失败"),
    JWT_VERIFICATION_FAILED(1022, "JWT验证失败"),
    SYSTEM_ERROR(500, "系统错误");

    private final int code;
    private final String message;

    BusinessExceptionEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}