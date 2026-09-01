package club.boyuan.official.infra.storage;

import club.boyuan.official.infra.config.CosProperties;
import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CosStorageServiceTest {

    @Test
    void objectKeyFallsBackToBackendProxyWhenPublicDomainIsNotConfigured() {
        CosProperties properties = new CosProperties();
        CosStorageService service = new CosStorageService(mock(COSClient.class), properties);

        assertThat(service.resolvePublicUrl("activities/cover.png"))
                .isEqualTo("/api/files/activities/cover.png");
    }

    @Test
    void existingPublicUrlsAndSitePathsAreIdempotent() {
        CosProperties properties = new CosProperties();
        CosStorageService service = new CosStorageService(mock(COSClient.class), properties);

        assertThat(service.resolvePublicUrl("/api/files/activities/cover.png"))
                .isEqualTo("/api/files/activities/cover.png");
        assertThat(service.resolvePublicUrl("/uploads/activities/cover.png"))
                .isEqualTo("/uploads/activities/cover.png");
        assertThat(service.resolvePublicUrl("https://static.boyuan.club/activities/cover.png"))
                .isEqualTo("https://static.boyuan.club/activities/cover.png");
    }

    @Test
    void objectKeyUsesConfiguredPublicDomainWithoutDoubleSlash() {
        CosProperties properties = new CosProperties();
        properties.setPublicBaseUrl("https://static.boyuan.club///");
        CosStorageService service = new CosStorageService(mock(COSClient.class), properties);

        assertThat(service.resolvePublicUrl("activities/cover.png"))
                .isEqualTo("https://static.boyuan.club/activities/cover.png");
    }
}
