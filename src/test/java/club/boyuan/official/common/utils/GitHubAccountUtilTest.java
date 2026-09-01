package club.boyuan.official.common.utils;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubAccountUtilTest {

    @Mock
    private UserMapper userMapper;

    @Test
    void normalizeBareLogin() {
        assertEquals("zewang0217", GitHubAccountUtil.normalize("Zewang0217"));
    }

    @Test
    void normalizeHttpsProfileUrl() {
        assertEquals("zewang0217", GitHubAccountUtil.normalize("https://github.com/Zewang0217"));
    }

    @Test
    void normalizeHostWithoutScheme() {
        assertEquals("zewang0217", GitHubAccountUtil.normalize("github.com/Zewang0217"));
    }

    @Test
    void normalizeTrailingSlash() {
        assertEquals("zewang0217", GitHubAccountUtil.normalize("https://github.com/Zewang0217/"));
    }

    @Test
    void normalizeRepoUrlTakesOwnerSegment() {
        assertEquals("zewang0217", GitHubAccountUtil.normalize("https://github.com/Zewang0217/Official_Web_Backend"));
    }

    @Test
    void normalizeSshUrl() {
        assertEquals("zewang0217", GitHubAccountUtil.normalize("git@github.com:Zewang0217/repo.git"));
    }

    @Test
    void normalizeMixedCaseAndWww() {
        assertEquals("zewang0217", GitHubAccountUtil.normalize("HTTPS://WWW.GITHUB.COM/Zewang0217"));
    }

    @Test
    void normalizeBareDotGitSuffix() {
        assertEquals("zewang0217", GitHubAccountUtil.normalize("Zewang0217.git"));
    }

    @Test
    void normalizeBlankOrEmptyReturnsNull() {
        assertNull(GitHubAccountUtil.normalize(null));
        assertNull(GitHubAccountUtil.normalize(""));
        assertNull(GitHubAccountUtil.normalize("   "));
        assertNull(GitHubAccountUtil.normalize("https://github.com/"));
        assertNull(GitHubAccountUtil.normalize("https://github.com///"));
    }

    @Test
    void assertNotBoundThrowsWhenTaken() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class,
                () -> GitHubAccountUtil.assertNotBound(userMapper, "zewang0217", null));
    }

    @Test
    void assertNotBoundPassesWhenFree() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        assertDoesNotThrow(() -> GitHubAccountUtil.assertNotBound(userMapper, "zewang0217", 1));
    }

    @Test
    void assertNotBoundSkipsNullGithub() {
        assertDoesNotThrow(() -> GitHubAccountUtil.assertNotBound(userMapper, null, 1));
    }
}