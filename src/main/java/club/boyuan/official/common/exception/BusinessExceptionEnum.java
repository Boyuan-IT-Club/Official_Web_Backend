package club.boyuan.official.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常枚举类
 * 用于统一管理系统中的业务异常码、提示信息与对应的 HTTP 状态码。
 * <p>
 * HTTP 状态码内聚到每个枚举常量，避免在 GlobalExceptionHandler 中维护脆弱的区间 if-else。
 */
@Getter
public enum BusinessExceptionEnum {

    // JWT相关异常 (1000-1099)
    JWT_VERIFICATION_FAILED(1001, "token验证失败", HttpStatus.CONFLICT),
    JWT_TOKEN_EXPIRED(1002, "token已过期", HttpStatus.CONFLICT),
    JWT_HAS_BEEN_LOGGED_OUT(1003, "token已被注销", HttpStatus.BAD_REQUEST),

    // 用户相关异常 (2000-2099)
    USER_NOT_FOUND(2001, "用户不存在", HttpStatus.CONFLICT),
    USERNAME_OR_PASSWORD_ERROR(2002, "用户名或密码错误", HttpStatus.CONFLICT),
    USER_ALREADY_EXISTS(2003, "用户已存在", HttpStatus.BAD_REQUEST),
    USER_NOT_LOGIN(2004, "用户未登录", HttpStatus.BAD_REQUEST),
    USER_ROLE_NOT_AUTHORIZED(2005, "用户角色无权限", HttpStatus.BAD_REQUEST),
    USER_AUTHENTICATION_FAILED(2006, "用户认证失败", HttpStatus.UNAUTHORIZED),
    USERNAME_ALREADY_EXISTS(2007, "用户名已存在", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS(2008, "邮箱已存在", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(2009, "邮箱格式不正确", HttpStatus.BAD_REQUEST),
    USER_INFO_UPDATE_FAILED(2010, "用户信息更新失败", HttpStatus.BAD_REQUEST),
    PHONE_ALREADY_EXISTS(2011, "手机号已存在", HttpStatus.BAD_REQUEST),
    PHONE_FORMAT_ERROR(2012, "手机号格式不正确", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCH(2013, "密码不一致", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(2014, "角色不存在", HttpStatus.BAD_REQUEST),
    PERMISSION_NOT_FOUND(2015, "权限不存在", HttpStatus.BAD_REQUEST),
    DEPARTMENT_NOT_FOUND(2016, "部门不存在", HttpStatus.BAD_REQUEST),
    GITHUB_ALREADY_BOUND(2017, "该 GitHub 账号已被其他用户绑定", HttpStatus.BAD_REQUEST),
    INVALID_REPORT(2018, "报告单无效", HttpStatus.BAD_REQUEST),
    EVALUATION_SUBMISSION_NOT_FOUND(2019, "评测提交不存在", HttpStatus.NOT_FOUND),
    EVALUATION_SUBMISSION_ALREADY_CLAIMED(2020, "该评测提交已被认领", HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(2021, "请求体过大", HttpStatus.PAYLOAD_TOO_LARGE),

    // 认证相关异常 (2100-2199)
    AUTHENTICATION_FAILED(2100, "认证失败", HttpStatus.BAD_REQUEST),
    PERMISSION_DENIED(2101, "权限不足", HttpStatus.FORBIDDEN),

    // 验证码相关异常 (2200-2299)
    MISSING_REQUIRED_FIELD(2201, "缺少必填字段", HttpStatus.BAD_REQUEST),
    INVALID_CAPTCHA(2202, "验证码不正确或已过期", HttpStatus.BAD_REQUEST),

    // 奖项经验相关异常 (2300-2399)
    AWARD_EXPERIENCE_NOT_FOUND(2301, "奖项经验不存在", HttpStatus.BAD_REQUEST),
    AWARD_EXPERIENCE_CREATE_FAILED(2302, "奖项经验创建失败", HttpStatus.BAD_REQUEST),
    AWARD_EXPERIENCE_UPDATE_FAILED(2303, "奖项经验更新失败", HttpStatus.BAD_REQUEST),
    AWARD_EXPERIENCE_DELETE_FAILED(2304, "奖项经验删除失败", HttpStatus.BAD_REQUEST),
    AWARD_EXPERIENCE_QUERY_FAILED(2305, "奖项经验查询失败", HttpStatus.BAD_REQUEST),

    // 密码相关异常 (2400-2499)
    PASSWORD_TOO_SIMPLE(2401, "密码过于简单", HttpStatus.BAD_REQUEST),

    // 简历相关异常 (3000-3099)
    RESUME_NOT_FOUND(3001, "简历不存在", HttpStatus.NOT_FOUND),
    RESUME_ALREADY_SUBMITTED(3002, "简历已提交或已在评审中", HttpStatus.BAD_REQUEST),
    RESUME_FIELD_DEFINITION_NOT_FOUND(3003, "简历字段定义不存在", HttpStatus.BAD_REQUEST),
    RESUME_FIELD_VALUE_SAVE_FAILED(3004, "简历字段值保存失败", HttpStatus.BAD_REQUEST),
    RESUME_CREATE_FAILED(3005, "简历创建失败", HttpStatus.BAD_REQUEST),
    RESUME_UPDATE_FAILED(3006, "简历更新失败", HttpStatus.BAD_REQUEST),
    RESUME_DELETE_FAILED(3007, "简历删除失败", HttpStatus.BAD_REQUEST),
    RESUME_SUBMIT_FAILED(3008, "简历提交失败", HttpStatus.BAD_REQUEST),
    RESUME_QUERY_FAILED(3009, "简历查询失败", HttpStatus.BAD_REQUEST),

    // 简历字段定义相关异常 (3100-3199)
    RESUME_FIELD_DEFINITION_CREATE_FAILED(3101, "简历字段定义创建失败", HttpStatus.BAD_REQUEST),
    RESUME_FIELD_DEFINITION_UPDATE_FAILED(3102, "简历字段定义更新失败", HttpStatus.BAD_REQUEST),
    RESUME_FIELD_DEFINITION_DELETE_FAILED(3103, "简历字段定义删除失败", HttpStatus.BAD_REQUEST),
    RESUME_FIELD_DEFINITION_QUERY_FAILED(3104, "简历字段定义查询失败", HttpStatus.BAD_REQUEST),

    // 招募周期相关异常 (3200-3299)
    RECRUITMENT_CYCLE_NOT_FOUND(3201, "招募周期不存在", HttpStatus.BAD_REQUEST),
    RECRUITMENT_CYCLE_CREATE_FAILED(3202, "招募周期创建失败", HttpStatus.BAD_REQUEST),
    RECRUITMENT_CYCLE_UPDATE_FAILED(3203, "招募周期更新失败", HttpStatus.BAD_REQUEST),
    RECRUITMENT_CYCLE_DELETE_FAILED(3204, "招募周期删除失败", HttpStatus.BAD_REQUEST),
    RECRUITMENT_CYCLE_QUERY_FAILED(3205, "招募周期查询失败", HttpStatus.BAD_REQUEST),

    // 导出相关异常 (3300-3399)
    EXPORT_PDF_FAILED(3301, "PDF导出失败", HttpStatus.BAD_REQUEST),
    EXPORT_EXCEL_FAILED(3302, "Excel导出失败", HttpStatus.BAD_REQUEST),
    EXPORT_DATA_NOT_FOUND(3303, "导出数据不存在", HttpStatus.BAD_REQUEST),
    EXPORT_PERMISSION_DENIED(3304, "导出权限不足", HttpStatus.BAD_REQUEST),

    // 数据库相关异常 (4000-4099)
    DATABASE_OPERATION_FAILED(4001, "数据库操作失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_CONNECTION_FAILED(4002, "数据库连接失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_QUERY_FAILED(4003, "数据库查询失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_INSERT_FAILED(4004, "数据库插入失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_UPDATE_FAILED(4005, "数据库更新失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_DELETE_FAILED(4006, "数据库删除失败", HttpStatus.INTERNAL_SERVER_ERROR),

    // 系统异常 (5000-5099)
    SYSTEM_ERROR(5001, "系统异常", HttpStatus.INTERNAL_SERVER_ERROR),
    PARAMETER_VALIDATION_FAILED(5002, "参数校验失败", HttpStatus.INTERNAL_SERVER_ERROR),
    ILLEGAL_ARGUMENT(5003, "非法参数", HttpStatus.INTERNAL_SERVER_ERROR),
    NULL_POINTER_EXCEPTION(5004, "空指针异常", HttpStatus.INTERNAL_SERVER_ERROR),
    UNSUPPORTED_OPERATION(5005, "不支持的操作", HttpStatus.INTERNAL_SERVER_ERROR),
    RESOURCE_CONFLICT(5006, "资源冲突", HttpStatus.CONFLICT),

    // 面试预约相关异常 (3400-3499)
    INTERVIEW_SLOT_NOT_FOUND(3401, "面试时段不存在", HttpStatus.BAD_REQUEST),
    INTERVIEW_SLOT_FULL(3402, "该面试时段已满", HttpStatus.CONFLICT),
    INTERVIEW_SLOT_CLOSED(3403, "该面试时段已关闭", HttpStatus.BAD_REQUEST),
    INTERVIEW_SLOT_CYCLE_MISMATCH(3404, "面试时段与招募周期不匹配", HttpStatus.BAD_REQUEST),
    INTERVIEW_BOOKING_NOT_FOUND(3405, "面试预约不存在", HttpStatus.BAD_REQUEST),
    INTERVIEW_BOOKING_FORBIDDEN(3406, "无权操作该面试预约", HttpStatus.FORBIDDEN),
    RESUME_NOT_SUBMITTED_FOR_BOOKING(3407, "请先提交简历后再预约面试", HttpStatus.BAD_REQUEST),
    INTERVIEW_BOOKING_PROCESSING(3408, "您在本周期已有预约正在处理中，请稍后再试", HttpStatus.BAD_REQUEST),
    INTERVIEW_BOOKING_REQUEST_NOT_FOUND(3409, "预约请求不存在或已过期", HttpStatus.BAD_REQUEST),
    INTERVIEW_SECKILL_DISABLED(3410, "秒杀预约模式未开启", HttpStatus.BAD_REQUEST),
    TOO_MANY_REQUESTS(4291, "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    // 飞书集成 (3500-3599)
    FEISHU_NOT_CONFIGURED(3501, "飞书应用未配置", HttpStatus.BAD_REQUEST),
    FEISHU_AUTH_FAILED(3502, "飞书鉴权失败", HttpStatus.BAD_REQUEST),
    FEISHU_TABLE_URL_INVALID(3503, "飞书表格 URL 无效", HttpStatus.BAD_REQUEST),
    FEISHU_TABLE_URL_MISSING(3504, "未配置飞书表格 URL", HttpStatus.BAD_REQUEST),
    FEISHU_NO_SCHEDULES(3505, "没有可导入的面试安排", HttpStatus.BAD_REQUEST),
    FEISHU_IMPORT_FAILED(3506, "飞书导入失败", HttpStatus.BAD_REQUEST),
    FEISHU_SYNC_TASK_NOT_FOUND(3507, "飞书同步任务不存在", HttpStatus.BAD_REQUEST),
    FEISHU_TABLE_EMPTY(3508, "飞书表格无数据", HttpStatus.BAD_REQUEST),

    // 面试志愿/场次分配相关异常 (3600-3699)
    INTERVIEW_TIME_SLOT_NOT_FOUND(3601, "面试时间窗不存在", HttpStatus.BAD_REQUEST),
    INTERVIEW_TIME_SLOT_CYCLE_MISMATCH(3602, "面试时间窗与招募周期不匹配", HttpStatus.BAD_REQUEST),
    INTERVIEW_SESSION_NOT_FOUND(3603, "面试场次不存在", HttpStatus.BAD_REQUEST),
    INTERVIEW_SESSION_FULL(3604, "该面试场次已满", HttpStatus.CONFLICT),
    INTERVIEW_SESSION_CYCLE_MISMATCH(3605, "面试场次与招募周期不匹配", HttpStatus.BAD_REQUEST),
    INTERVIEW_SESSION_HAS_SCHEDULE(3606, "该场次已有面试安排，无法删除", HttpStatus.CONFLICT),
    INTERVIEW_TIME_SLOT_HAS_SESSION(3607, "该时间窗下已有场次，无法删除", HttpStatus.CONFLICT),
    INTERVIEW_PREFERENCE_DEPT_REQUIRED(3608, "请至少选择一个志愿部门", HttpStatus.BAD_REQUEST),
    INTERVIEW_PREFERENCE_DEPT_DUPLICATE(3609, "第一、第二志愿部门不能相同", HttpStatus.BAD_REQUEST),
    INTERVIEW_PREFERENCE_TIME_REQUIRED(3610, "请至少勾选一个可接受的时间窗", HttpStatus.BAD_REQUEST),
    INTERVIEW_PREFERENCE_TIME_INVALID(3611, "所选时间窗无效或不属于该周期", HttpStatus.BAD_REQUEST),
    INTERVIEW_SCHEDULE_NOT_FOUND(3612, "面试安排不存在", HttpStatus.BAD_REQUEST),

    // 活动相关异常 (7000-7099)
    ACTIVITY_NOT_FOUND(7001, "活动不存在", HttpStatus.BAD_REQUEST),
    ACTIVITY_CREATE_FAILED(7002, "创建活动失败", HttpStatus.BAD_REQUEST),
    ACTIVITY_UPDATE_FAILED(7003, "更新活动失败", HttpStatus.BAD_REQUEST),
    ACTIVITY_DELETE_FAILED(7004, "删除活动失败", HttpStatus.BAD_REQUEST);


    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    BusinessExceptionEnum(Integer code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
