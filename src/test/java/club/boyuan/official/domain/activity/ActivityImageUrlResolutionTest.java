package club.boyuan.official.domain.activity;

import club.boyuan.official.domain.activity.controller.ActivityController;
import club.boyuan.official.domain.activity.service.IActivityService;
import club.boyuan.official.infra.storage.CosStorageService;
import club.boyuan.official.persistence.entity.Activity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityImageUrlResolutionTest {

    @Test
    void listResponseTurnsStoredObjectKeyIntoPublicUrl() {
        IActivityService activityService = mock(IActivityService.class);
        CosStorageService storageService = mock(CosStorageService.class);
        ActivityController controller = new ActivityController(activityService, storageService);

        Activity activity = new Activity();
        activity.setCoverImage("activities/cover.png");
        when(activityService.getAllActivities()).thenReturn(List.of(activity));
        when(storageService.resolvePublicUrl("activities/cover.png"))
                .thenReturn("/api/files/activities/cover.png");

        var response = controller.getAllActivities(new MockHttpServletRequest());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).singleElement()
                .extracting(Activity::getCoverImage)
                .isEqualTo("/api/files/activities/cover.png");
    }
}
