package club.boyuan.official.common.exception;

import club.boyuan.official.common.dto.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 * 统一处理应用中的业务异常和其他异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理限流异常——比 BusinessException 更具体，Spring 会优先匹配到这里。
     * 除了统一响应体，额外写出 Retry-After，告诉客户端隔多久再来。
     * 没有这个头，前端只能盲目重试（线上出现过 11 分钟 211 次的重试风暴）。
     * @param ex 限流异常
     * @return 统一响应格式 + Retry-After
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ResponseMessage<?>> handleRateLimitExceeded(RateLimitExceededException ex) {
        logger.warn("触发限流: code={}, retryAfter={}s", ex.getCode(), ex.getRetryAfterSeconds());
        ResponseMessage<?> response = new ResponseMessage<>(ex.getCode(), ex.getMessage(), null);
        return ResponseEntity.status(ex.getHttpStatus())
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(response);
    }

    /**
     * 处理业务异常
     * @param ex 业务异常
     * @return 统一响应格式
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseMessage<?>> handleBusinessException(BusinessException ex) {
        logger.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        ResponseMessage<?> response = new ResponseMessage<>(ex.getCode(), ex.getMessage(), null);
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 处理 Spring Security 认证异常（已认证信息缺失/无效）——返回 401
     * @param ex 认证异常
     * @return 统一响应格式
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseMessage<?>> handleAuthenticationException(AuthenticationException ex) {
        logger.warn("认证异常: {}", ex.getMessage());
        ResponseMessage<?> response = new ResponseMessage<>(
                BusinessExceptionEnum.USER_AUTHENTICATION_FAILED.getCode(),
                BusinessExceptionEnum.USER_AUTHENTICATION_FAILED.getMessage(),
                null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 处理 Spring Security 授权异常（已认证但权限不足）——返回 403
     * 注意：这里必须是 org.springframework.security.access.AccessDeniedException，
     * 方法级 @PreAuthorize 拒绝会抛出其子类 AuthorizationDeniedException，需被此处统一捕获。
     * @param ex 授权异常
     * @return 统一响应格式
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseMessage<?>> handleAccessDeniedException(AccessDeniedException ex) {
        logger.warn("权限异常: {}", ex.getMessage());
        ResponseMessage<?> response = new ResponseMessage<>(
                BusinessExceptionEnum.PERMISSION_DENIED.getCode(), 
                BusinessExceptionEnum.PERMISSION_DENIED.getMessage(), 
                null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 处理上传体积超过 multipart 上限——返回 413。
     * <p>
     * 这个异常由 DispatcherServlet 解析 multipart 时抛出，早于 Controller，
     * 与认证、授权一样属于框架层异常，只能在这里翻成统一响应格式；
     * 不接住会落到下面的兜底 Exception：用户只看到「系统异常」，
     * 不知道自己只是文件太大（前端也会误报成接口挂了）。
     * @param ex 体积超限异常
     * @return 统一响应格式
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseMessage<?>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        logger.warn("上传文件过大: {}", ex.getMessage());
        ResponseMessage<?> response = new ResponseMessage<>(
                BusinessExceptionEnum.FILE_TOO_LARGE.getCode(),
                BusinessExceptionEnum.FILE_TOO_LARGE.getMessage(),
                null);
        return ResponseEntity.status(BusinessExceptionEnum.FILE_TOO_LARGE.getHttpStatus()).body(response);
    }

    /**
     * 处理 {@code @Valid} 请求体校验失败——返回 400 并带上字段级提示。
     * <p>
     * 此前没有这个分支，校验失败会落进下面的兜底 Exception 处理，
     * 前端只看到「系统异常」，真正的原因（如「部门ID不能为空」）完全被掩盖。
     * @param ex 参数校验异常
     * @return 统一响应格式
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseMessage<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describeFieldError)
                .reduce((a, b) -> a + "；" + b)
                .orElse(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED.getMessage());
        logger.warn("参数校验失败: {}", detail);
        ResponseMessage<?> response = new ResponseMessage<>(
                BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED.getCode(), detail, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 请求少了必填的 query 参数 —— 客户端错误，必须是 400。
     *
     * 不接住会落进下面的兜底 Exception 返回 500：一个前端传参失误就会把 5xx 告警
     * 点着，运维看到的是「服务挂了」，实际只是某个页面少带了个 cycleId。
     * 2026-10-12 的 5xx 告警就是这么来的：学生端首屏 cycleId 还没加载完就发了
     * /interview/schedule/my 与 /preference/my，每开一次页面产生两个 500。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseMessage<?>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String detail = "缺少必填参数：" + ex.getParameterName();
        logger.warn("请求缺少必填参数: {}", detail);
        return badRequest(detail);
    }

    /**
     * 参数类型对不上（如 cycleId=abc）—— 同样是客户端错误。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseMessage<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "参数格式不正确：" + ex.getName();
        logger.warn("请求参数类型不匹配: {} = {}", ex.getName(), ex.getValue());
        return badRequest(detail);
    }

    /**
     * 请求体读不出来：不是合法 JSON，或某个字段反序列化失败。
     *
     * 日期格式对不上就落在这里。「调时间/地点」曾经一按就报「系统异常」，正是
     * 前端发 2026-10-11T19:45:00、而 DTO 的 @JsonFormat 不含秒 —— 那条
     * InvalidFormatException 被包进本异常后掉进兜底，变成 500 与一句没有信息量的
     * 「系统异常」，只能翻服务器日志才找得到真因。现在原因直接回给调用方。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseMessage<?>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        // 对外只给到「哪个字段」这一层：异常原文里带着包名与解析器细节，不该露给前端。
        // 沿 cause 链找 InvalidFormatException，而不是用 getMostSpecificCause()——
        // 后者会一路走到最底层（日期就是 DateTimeParseException），跳过了带字段路径的那一层。
        String field = fieldPathOf(ex);
        String detail = field == null ? "请求体格式不正确" : "字段格式不正确：" + field;
        logger.warn("请求体解析失败: {}", ex.getMostSpecificCause().getMessage());
        return badRequest(detail);
    }

    /** 沿 cause 链找出带字段路径的那层异常，取不到就返回 null */
    private static String fieldPathOf(Throwable t) {
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            if (c instanceof InvalidFormatException ife) {
                return pathOf(ife);
            }
        }
        return null;
    }

    private static String pathOf(InvalidFormatException ife) {
        return ife.getPath().stream()
                .map(com.fasterxml.jackson.databind.JsonMappingException.Reference::getFieldName)
                .filter(java.util.Objects::nonNull)
                .reduce((a, b) -> a + "." + b)
                .orElse(null);
    }

    private static ResponseEntity<ResponseMessage<?>> badRequest(String detail) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ResponseMessage<>(
                BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED.getCode(), detail, null));
    }

    private static String describeFieldError(FieldError fe) {
        String msg = fe.getDefaultMessage();
        return msg == null || msg.isBlank() ? fe.getField() + " 不合法" : msg;
    }

    /**
     * 处理通用异常
     * @param e 通用异常
     * @return 统一响应格式
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseMessage<?>> handleGenericException(Exception e) {
        logger.error("系统异常: {}", e.getMessage(), e);
        ResponseMessage<?> response = new ResponseMessage<>(
                BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                BusinessExceptionEnum.SYSTEM_ERROR.getMessage(), 
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}