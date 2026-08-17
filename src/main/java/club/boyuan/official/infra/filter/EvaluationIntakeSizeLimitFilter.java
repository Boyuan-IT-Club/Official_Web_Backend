package club.boyuan.official.infra.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 公开 intake 端点请求体大小上限:在 JSON 解析(全量入堆)之前按 Content-Length 拒绝超大载荷。
 * 零认证端点 + 无界 body = 内存 DoS 面;正常报告单 &lt;10KB,上限 1MiB 已极宽松。
 * chunked 传输无 Content-Length(-1)不在此层拦截,由 service 层 report 长度上限兜底。
 */
@Component
@Order(1)
public class EvaluationIntakeSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 1024 * 1024;
    private static final String INTAKE_URI = "/api/public/evaluations";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if ("POST".equalsIgnoreCase(request.getMethod())
                && INTAKE_URI.equals(request.getRequestURI())
                && contentLength > MAX_BODY_BYTES) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large");
            return;
        }
        chain.doFilter(request, response);
    }
}
