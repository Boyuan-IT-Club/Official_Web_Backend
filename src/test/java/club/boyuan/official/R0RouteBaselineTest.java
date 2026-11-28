package club.boyuan.official;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class R0RouteBaselineTest {
    private static String[] paths(RequestMapping mapping) {
        return mapping == null || mapping.path().length == 0 ? new String[]{""} : mapping.path();
    }

    @Test
    void baselineRoutesRemainAvailable() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, Method> handlers = new HashMap<>();
        for (var bean : scanner.findCandidateComponents("club.boyuan.official")) {
            Class<?> controller = Class.forName(bean.getBeanClassName());
            var prefix = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            for (Method method : controller.getDeclaredMethods()) {
                var route = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (route == null || method.isBridge()) continue;
                var verbs = Arrays.stream(route.method()).map(Enum::name).toList();
                for (String base : paths(prefix)) {
                    for (String tail : paths(route)) {
                        String path = ("/" + base + "/" + tail).replaceAll("/+", "/").replaceFirst("(?<=.)/$", "");
                        for (String verb : verbs.isEmpty() ? java.util.List.of("ANY") : verbs) {
                            assertNull(handlers.put(verb + " " + path, method), () -> "Duplicate mapping: " + verb + " " + path);
                        }
                    }
                }
            }
        }

        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/contracts/r0/routes.tsv"))) {
            var rows = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
            assertEquals(300, rows.size());
            for (String row : rows) {
                var field = row.split("\\t");
                Method handler = handlers.get(field[1] + " " + field[2]);
                assertNotNull(handler, row);
                if (field[3].equals("retired-410")) {
                    var controller = org.mockito.Mockito.mock(handler.getDeclaringClass(), org.mockito.Mockito.CALLS_REAL_METHODS);
                    var response = (org.springframework.http.ResponseEntity<?>) handler.invoke(controller, new Object[handler.getParameterCount()]);
                    assertEquals(410, response.getStatusCode().value(), row);
                }
            }
        }
    }
}
