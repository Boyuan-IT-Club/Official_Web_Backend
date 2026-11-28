package club.boyuan.official.infra.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 工作台目录凭据和面试协同凭据不能互相打开对方的内部接口。 */
class WorkbenchDirectorySecurityTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void separatesDirectoryFromInterviewTokens() throws Exception {
        check("/api/internal/workbench/dev-members", "directory-test", "WORKBENCH_DIRECTORY");
        check("/api/internal/workbench/dev-members/102", "directory-test", "WORKBENCH_DIRECTORY");
        check("/api/internal/workbench/member-names", "directory-test", "WORKBENCH_DIRECTORY");
        check("/api/internal/workbench/dev-members", "interview-test", null);
        check("/api/internal/interview/evaluation", "directory-test", null);
        check("/api/internal/interview/evaluation", "interview-test", "ROLE_INTERNAL_SERVICE");
    }

    private void check(String path, String token, String expected) throws Exception {
        SecurityContextHolder.clearContext();
        var filter = new ServiceTokenAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "directoryToken", "directory-test");
        ReflectionTestUtils.setField(filter, "serviceToken", "interview-test");
        var request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-Service-Token", token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (expected == null) assertThat(auth).isNull();
            else assertThat(auth.getAuthorities()).extracting("authority").containsExactly(expected);
        });
    }
}
