package club.boyuan.official.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务异常枚举类
 * 统一管理错误码和错误消息
 */
@Getter
@AllArgsConstructor
public enum BusinessExceptionEnum {
    
    // JWT相关异常 (1000-1099)
    JWT_VERIFICATION_FAILED(1001, "token验证失败"),
    JWT_TOKEN_EXPIRED(1002, "token已过期"),
    JWT_HAS_BEEN_LOGGED_OUT(1003, "token已被注销"),

    // 用户相关异常 (2000-2099)
    USER_NOT_FOUND(2001, "用户不存在"),
    USERNAME_OR_PASSWORD_ERROR(2002, "用户名或密码错误"),
    USER_ALREADY_EXISTS(2003, "用户已存在"),
    USER_NOT_LOGIN(2004, "用户未登录"),
    USER_ROLE_NOT_AUTHORIZED(2005, "用户角色无权限"),
    USER_AUTHENTICATION_FAILED(2006, "用户认证失败"),
    USERNAME_ALREADY_EXISTS(2007, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(2008, "邮箱已存在"),
    INVALID_EMAIL_FORMAT(2009, "邮箱格式不正确"),
    USER_INFO_UPDATE_FAILED(2010, "用户信息更新失败"),
    PHONE_ALREADY_EXISTS(2011, "手机号已存在"),
    PHONE_FORMAT_ERROR(2012, "手机号格式不正确"),
    PASSWORD_NOT_MATCH(2013, "密码不一致"),
    
    // 认证相关异常 (2100-2199)
    AUTHENTICATION_FAILED(2100, "认证失败"),
    PERMISSION_DENIED(2101, "权限不足"),
    
    // 验证码相关异常 (2200-2299)
    MISSING_REQUIRED_FIELD(2201, "缺少必填字段"),
    INVALID_CAPTCHA(2202, "验证码不正确或已过期"),
    
    // 奖项经验相关异常 (2300-2399)
    AWARD_EXPERIENCE_NOT_FOUND(2301, "奖项经验不存在"),
    AWARD_EXPERIENCE_CREATE_FAILED(2302, "奖项经验创建失败"),
    AWARD_EXPERIENCE_UPDATE_FAILED(2303, "奖项经验更新失败"),
    AWARD_EXPERIENCE_DELETE_FAILED(2304, "奖项经验删除失败"),
    AWARD_EXPERIENCE_QUERY_FAILED(2305, "奖项经验查询失败"),
    
    // 密码相关异常 (2400-2499)
    PASSWORD_TOO_SIMPLE(2401, "密码过于简单"),
    
    // 简历相关异常 (3000-3099)
    RESUME_NOT_FOUND(3001, "简历不存在"),
    RESUME_ALREADY_SUBMITTED(3002, "简历已提交或已在评审中"),
    RESUME_FIELD_DEFINITION_NOT_FOUND(3003, "简历字段定义不存在"),
    RESUME_FIELD_VALUE_SAVE_FAILED(3004, "简历字段值保存失败"),
    RESUME_CREATE_FAILED(3005, "简历创建失败"),
    RESUME_UPDATE_FAILED(3006, "简历更新失败"),
    RESUME_DELETE_FAILED(3007, "简历删除失败"),
    RESUME_SUBMIT_FAILED(3008, "简历提交失败"),
    RESUME_QUERY_FAILED(3009, "简历查询失败"),
    
    // 简历字段定义相关异常 (3100-3199)
    RESUME_FIELD_DEFINITION_CREATE_FAILED(3101, "简历字段定义创建失败"),
    RESUME_FIELD_DEFINITION_UPDATE_FAILED(3102, "简历字段定义更新失败"),
    RESUME_FIELD_DEFINITION_DELETE_FAILED(3103, "简历字段定义删除失败"),
    RESUME_FIELD_DEFINITION_QUERY_FAILED(3104, "简历字段定义查询失败"),
    
    // 数据库相关异常 (4000-4099)
    DATABASE_OPERATION_FAILED(4001, "数据库操作失败"),
    DATABASE_CONNECTION_FAILED(4002, "数据库连接失败"),
    DATABASE_QUERY_FAILED(4003, "数据库查询失败"),
    DATABASE_INSERT_FAILED(4004, "数据库插入失败"),
    DATABASE_UPDATE_FAILED(4005, "数据库更新失败"),
    DATABASE_DELETE_FAILED(4006, "数据库删除失败"),
    
    // 系统异常 (5000-5099)
    SYSTEM_ERROR(5001, "系统异常"),
    PARAMETER_VALIDATION_FAILED(5002, "参数校验失败"),
    ILLEGAL_ARGUMENT(5003, "非法参数"),
    NULL_POINTER_EXCEPTION(5004, "空指针异常"),
    UNSUPPORTED_OPERATION(5005, "不支持的操作");
    
    private final Integer code;
    private final String message;
}