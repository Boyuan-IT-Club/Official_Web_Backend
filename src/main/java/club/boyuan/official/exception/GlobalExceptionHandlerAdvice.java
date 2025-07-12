package club.boyuan.official.exception;

import club.boyuan.official.dto.ResponseMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandlerAdvice {
    @ExceptionHandler({Exception.class})
    public ResponseMessage handlerException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        Logger log= LoggerFactory.getLogger(GlobalExceptionHandlerAdvice.class);
        // 记录日志
        log.error("统一异常: {}", e.toString(), e);
        return new ResponseMessage(500, e.getClass().getSimpleName() + ": " + e.getMessage(), null);
    }
}