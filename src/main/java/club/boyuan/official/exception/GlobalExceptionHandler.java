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
 * 统一处理应用中的业务异常和其他异常
 */
@ControllerAdvice
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 映射业务异常码到HTTP状态码
     * @param businessCode 业务异常码
     * @return 对应的HTTP状态码
     */
    private HttpStatus mapBusinessCodeToHttpStatus(int businessCode) {
        // JWT相关异常 (1000-1099)
        if (businessCode == 1001 || businessCode == 1002) {
            return HttpStatus.CONFLICT; // 409状态码
        } else if (businessCode == 1003) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        } else if (businessCode == 1008) {
            return HttpStatus.UNAUTHORIZED; // 401状态码
        } else if (businessCode >= 1009 && businessCode <= 1018) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        } else if (businessCode >= 1019 && businessCode <= 1020) {
            return HttpStatus.UNAUTHORIZED; // 401状态码
        } else if (businessCode >= 1021 && businessCode <= 1025) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        } else if (businessCode == 1026) {
            return HttpStatus.FORBIDDEN; // 403状态码
        }
        // 用户相关异常 (2000-2099)
        else if (businessCode >= 2001 && businessCode <= 2002) {
            return HttpStatus.CONFLICT; // 409状态码
        } else if ((businessCode >= 2003 && businessCode <= 2005) || businessCode == 2010) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        } else if (businessCode >= 2006 && businessCode <= 2007) {
            return HttpStatus.UNAUTHORIZED; // 401状态码
        } else if (businessCode >= 2008 && businessCode <= 2012) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        }
        // 认证相关异常 (2100-2199)
        else if (businessCode == 2100) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        } else if (businessCode == 2101) {
            return HttpStatus.FORBIDDEN; // 403状态码
        }
        // 验证码相关异常 (2200-2299)
        else if (businessCode >= 2201 && businessCode <= 2202) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        }
        // 奖项经验相关异常 (2300-2399)
        else if (businessCode >= 2301 && businessCode <= 2305) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        }
        // 密码相关异常 (2400-2499)
        else if (businessCode == 2401) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        }
        // 简历相关异常 (3000-3099)
        else if (businessCode >= 3001 && businessCode <= 3009) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        }
        // 简历字段定义相关异常 (3100-3199)
        else if (businessCode >= 3101 && businessCode <= 3104) {
            return HttpStatus.BAD_REQUEST; // 400状态码
        }
        // 数据库相关异常 (4000-4099)
        else if (businessCode >= 4001 && businessCode <= 4006) {
            return HttpStatus.INTERNAL_SERVER_ERROR; // 500状态码
        }
        // 系统异常 (5000-5099)
        else if (businessCode >= 5001 && businessCode <= 5005) {
            return HttpStatus.INTERNAL_SERVER_ERROR; // 500状态码
        } else {
            return HttpStatus.BAD_REQUEST; // 默认400
        }
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
        return ResponseEntity.status(mapBusinessCodeToHttpStatus(ex.getCode())).body(response);
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