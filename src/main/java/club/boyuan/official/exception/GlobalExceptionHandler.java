package club.boyuan.official.exception;

import club.boyuan.official.dto.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.naming.AuthenticationException;
import java.nio.file.AccessDeniedException;

/**
 * 全局异常处理器
 * 统一处理应用中的业务异常
 */
@ControllerAdvice
@RestControllerAdvice
public class GlobalExceptionHandler {

    private HttpStatus mapBusinessCodeToHttpStatus(int businessCode) {
        if (businessCode == 1001 || businessCode == 1002) {
            return HttpStatus.CONFLICT; // 原409状态码
        } else if ((businessCode >= 1003 && businessCode <= 1005) || businessCode == 1008 || (businessCode >= 1009 && businessCode <= 1018) || businessCode == 1021) {
            return HttpStatus.BAD_REQUEST; // 原400状态码
        } else if ((businessCode >= 1006 && businessCode <= 1007) || (businessCode >= 1019 && businessCode <= 1020) || (businessCode >= 1022 && businessCode <= 1025)) {
            return HttpStatus.UNAUTHORIZED; // 原401状态码
        } else if (businessCode == 1026) {
            return HttpStatus.FORBIDDEN; // 403状态码
        } else {
            return HttpStatus.BAD_REQUEST; // 默认400
        }
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseMessage<?>> handleBusinessException(BusinessException ex) {
        ResponseMessage<?> response = new ResponseMessage<>(ex.getCode(), ex.getMessage(), null);
        return ResponseEntity.status(mapBusinessCodeToHttpStatus(ex.getCode())).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseMessage<?>> handleGenericException(Exception e) {
        Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
        log.error("统一异常: {}", e.toString(), e);
        ResponseMessage<?> response = new ResponseMessage<>(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), BusinessExceptionEnum.SYSTEM_ERROR.getMessage(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}