package club.boyuan.official.infra.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EvaluationIntakeSizeLimitFilterTest {

    private final EvaluationIntakeSizeLimitFilter filter = new EvaluationIntakeSizeLimitFilter();

    /** MockHttpServletRequest 无 setContentLengthLong,覆写读取侧即可 */
    private static MockHttpServletRequest post(String uri, long contentLengthLong) {
        return new MockHttpServletRequest("POST", uri) {
            @Override
            public long getContentLengthLong() {
                return contentLengthLong;
            }
        };
    }

    @Test
    void oversizedIntakeRejectedBeforeChain() throws ServletException, IOException {
        MockHttpServletRequest request =
                post("/api/public/evaluations", EvaluationIntakeSizeLimitFilter.MAX_BODY_BYTES + 1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest()); // 未进入后续过滤链/控制器
    }

    @Test
    void normalSizedIntakePassesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = post("/api/public/evaluations", 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertSame(request, chain.getRequest()); // 正常放行
    }

    @Test
    void chunkedRequestWithoutContentLengthPasses() throws ServletException, IOException {
        // chunked 无 Content-Length(-1),交由 service 层长度上限兜底
        MockHttpServletRequest request = post("/api/public/evaluations", -1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
    }

    @Test
    void otherEndpointsNotAffected() throws ServletException, IOException {
        MockHttpServletRequest request =
                post("/api/auth/login", EvaluationIntakeSizeLimitFilter.MAX_BODY_BYTES * 2);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
    }
}
