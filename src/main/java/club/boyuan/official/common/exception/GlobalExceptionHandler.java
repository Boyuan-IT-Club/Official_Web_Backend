package club.boyuan.official.common.exception;

import club.boyuan.official.common.dto.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理应用中的业务异常和其他异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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