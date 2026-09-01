package club.boyuan.official.infra.config;

import club.boyuan.official.domain.system.controller.FileController;
import club.boyuan.official.infra.filter.JwtAuthenticationFilter;
import club.boyuan.official.infra.filter.ServiceTokenAuthenticationFilter;
import club.boyuan.official.infra.storage.CosStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {SecurityConfig.class, PublicImageSecurityTest.TestConfig.class})
class PublicImageSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void activityImagesArePublicLikeAvatarImages() throws Exception {
        // COS mock 默认未启用，因此控制器返回 404；重点是请求不能在鉴权层被拦成 401。
        mockMvc.perform(get("/api/files/activities/cover.png"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files/avatars/avatar.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unrelatedCosDirectoriesAreStillProtected() throws Exception {
        mockMvc.perform(get("/api/files/private/secret.png"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {

        @Bean
        CosStorageService cosStorageService() {
            return mock(CosStorageService.class);
        }

        @Bean
        FileController fileController(CosStorageService storageService) {
            return new FileController(storageService);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(RedisTemplate.class));
        }

        @Bean
        ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter() {
            return new ServiceTokenAuthenticationFilter();
        }

        @Bean
        DispatcherServletPath dispatcherServletPath() {
            return () -> "";
        }
    }
}
