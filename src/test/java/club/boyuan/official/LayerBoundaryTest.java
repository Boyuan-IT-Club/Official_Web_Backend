package club.boyuan.official;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerBoundaryTest {
    @Test
    void persistenceAndHttpFollowTheKnownDependencyBaseline() throws IOException {
        JavaClasses app = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("club.boyuan.official");
        Set<String> actual = new TreeSet<>();
        for (JavaClass type : app) {
            if (type.getPackageName().startsWith("club.boyuan.official.persistence.")) {
                type.getDirectDependenciesFromSelf().stream()
                        .filter(d -> d.getTargetClass().getPackageName().startsWith("club.boyuan.official.domain."))
                        .filter(d -> d.getTargetClass().getPackageName().contains(".dto"))
                        .map(d -> d.getDescription().replaceFirst(" in \\(.*:[0-9]+\\)$", ""))
                        .forEach(actual::add);
            }
            if (!AnnotatedElementUtils.hasAnnotation(type.reflect(), Controller.class)) continue;
            if (AnnotatedElementUtils.hasAnnotation(type.reflect(), Transactional.class)) {
                actual.add("@Transactional " + type.getName());
            }
            for (var method : type.getMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method.reflect(), Transactional.class)) {
                    actual.add("@Transactional " + method.getFullName());
                }
            }
            type.getDirectDependenciesFromSelf().stream().map(d -> d.getTargetClass())
                    .filter(t -> t.isAssignableTo(PlatformTransactionManager.class)
                            || t.isAssignableTo(TransactionOperations.class))
                    .forEach(t -> actual.add(type.getName() + " uses " + t.getName()));
        }

        Set<String> expected = new TreeSet<>(Files.readAllLines(
                Path.of("src/test/resources/contracts/layer-boundaries.txt")));
        assertEquals(expected, actual, () -> {
            Set<String> added = new TreeSet<>(actual);
            added.removeAll(expected);
            Set<String> removed = new TreeSet<>(expected);
            removed.removeAll(actual);
            return "New violations:\n" + String.join("\n", added)
                    + "\nRemove resolved entries from the baseline:\n" + String.join("\n", removed);
        });
    }
}
