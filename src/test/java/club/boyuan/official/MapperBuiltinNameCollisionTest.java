package club.boyuan.official;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自定义 mapper XML 的语句 id 不得与 MyBatis-Plus BaseMapper 的集合类内置方法同名。
 *
 * 同名 XML 语句会顶掉内置实现,而内置方法的参数名是 coll —— 任何走
 * IService.listByIds()/removeByIds() 的调用都会撞上自定义语句的 @Param 名,
 * 报 "Parameter 'xxx' not found. Available parameters are [coll, param1]"。
 *
 * 线上事故:UserMapper 自定义了 selectByIds(@Param("userIds"))，飞书导入走
 * userService.listByIds() 命中它,整条飞书同步/拉取链路全挂。
 *
 * 只盯集合类方法:单参数语句(如 selectById)按位置绑定,同名不炸,不在此列。
 */
class MapperBuiltinNameCollisionTest {

    private static final Set<String> FORBIDDEN_IDS = Set.of(
            "selectByIds", "selectBatchIds", "deleteByIds", "deleteBatchIds");

    @Test
    @DisplayName("mapper XML 语句 id 不撞 MyBatis-Plus 集合类内置名")
    void noXmlStatementShadowsBuiltinCollectionMethods() throws IOException {
        Pattern id = Pattern.compile("<(?:select|update|delete|insert)\\s+id=\"(\\w+)\"");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/mapper"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                Matcher m = id.matcher(Files.readString(f));
                while (m.find()) {
                    if (FORBIDDEN_IDS.contains(m.group(1))) {
                        hits.add(f.getFileName() + " -> " + m.group(1));
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "这些 XML 语句 id 与 MyBatis-Plus 内置集合方法同名，会顶掉内置实现并让"
                        + " IService.listByIds() 等调用报 Parameter not found（飞书同步就是这么挂的），"
                        + "请重命名: " + hits);
    }
}
