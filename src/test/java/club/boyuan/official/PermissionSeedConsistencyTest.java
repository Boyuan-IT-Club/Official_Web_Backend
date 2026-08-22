package club.boyuan.official;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每个被 @PreAuthorize 引用的权限码,都必须在 Flyway 迁移里被播种。
 *
 * 守的是这一类静默失效:注解要求某个权限码,而该码从未进过 permission 表 ——
 * Spring 只会对所有人返回 403,没有任何报错提示"这个码不存在"。
 * evaluation:view 就是活例:V13 播种它时硬编码的 permission_id 撞了 V10,
 * 被 INSERT IGNORE 静默跳过,于是整个评测管理模块对所有角色 403(见 V18 的事故记录)。
 *
 * 不需要 Spring 上下文与数据库,纯文本比对,因此在任何环境都能跑。
 */
class PermissionSeedConsistencyTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java");
    private static final Path MIGRATION_ROOT = Path.of("src/main/resources/db/migration");

    /** 形如 'resume:audit' 的权限码 */
    private static final Pattern CODE = Pattern.compile("'([a-z]+(?::[a-z_]+)+)'");

    @Test
    @DisplayName("注解引用的每个权限码都能在迁移脚本里找到")
    void everyReferencedCodeIsSeeded() throws IOException {
        Set<String> referenced = collect(JAVA_ROOT, ".java", line -> line.contains("@PreAuthorize"));
        Set<String> seeded = collect(MIGRATION_ROOT, ".sql", line -> true);

        assertTrue(referenced.size() >= 10,
                "只扫到 " + referenced.size() + " 个权限码，扫描逻辑可能失效了：" + referenced);

        Set<String> missing = new TreeSet<>(referenced);
        missing.removeAll(seeded);

        assertTrue(missing.isEmpty(),
                "这些权限码被 @PreAuthorize 要求，但迁移脚本里从未播种 —— "
                        + "线上表现是相关接口对所有角色 403，且不会有任何报错: " + missing
                        + "\n（已播种的有: " + new TreeSet<>(seeded) + "）");
    }

    private static Set<String> collect(Path root, String suffix,
                                       java.util.function.Predicate<String> lineFilter) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(suffix)).toList()) {
                for (String line : Files.readAllLines(f)) {
                    if (!lineFilter.test(line)) {
                        continue;
                    }
                    Matcher m = CODE.matcher(line);
                    while (m.find()) {
                        found.add(m.group(1));
                    }
                }
            }
        }
        return found;
    }
}
