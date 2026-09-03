package club.boyuan.official.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 PDF 导出的「已渲染字段」清单与前端规范表分叉。
 *
 * 起因：这份清单在三个地方各写了一份（前端 Word 的 KNOWN、后端 PDF 的 known、
 * 前端 resumeFieldRegistry），互相漂移。前端那份漏了 introduction，
 * 于是「个人简介」在 Word 导出里出现两次——正文一次、其他信息又一次。
 *
 * 前端已改为从 RESUME_FIELDS 推导，Java 这边没有那张表只能手抄，
 * 所以用这个测试当哨兵：规范表里新增了标准字段而这里没跟上，立刻报出来。
 *
 * 找不到前端仓库时跳过（CI 只 check out 后端），本测试只在本地同时存在
 * 两个仓库时起作用——它是给改代码的人用的护栏，不是发布门禁。
 */
class PdfKnownKeysDriftTest {

    private static final Path REGISTRY = Path.of(
            "..", "Official_Web_Frontend", "src", "config", "resumeFieldRegistry.ts");

    @Test
    @DisplayName("PDF 的 known 清单必须覆盖前端规范表里的全部标准字段")
    void knownCoversEveryRegistryKey() throws IOException {
        if (!Files.isReadable(REGISTRY)) {
            System.out.println("跳过：找不到前端规范表 " + REGISTRY.toAbsolutePath());
            return;
        }

        Set<String> registryKeys = parseRegistryKeys(Files.readString(REGISTRY));
        assertTrue(registryKeys.size() > 10,
                "只从规范表解析出 " + registryKeys.size() + " 个字段，解析多半坏了");

        Set<String> known = parseKnownKeys(Files.readString(Path.of(
                "src/main/java/club/boyuan/official/common/utils/PdfExportUtil.java")));

        Set<String> missing = new LinkedHashSet<>(registryKeys);
        missing.removeAll(known);
        assertTrue(missing.isEmpty(),
                "PdfExportUtil 的 known 清单漏了这些标准字段，它们会被当成自定义字段"
                        + "重复打印到 PDF 末尾的「其他信息」里：" + missing);
    }

    /** 从 RESUME_FIELDS 里抓出所有 key: 'xxx'。 */
    private static Set<String> parseRegistryKeys(String ts) {
        int start = ts.indexOf("export const RESUME_FIELDS");
        String body = start >= 0 ? ts.substring(start) : ts;
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\\{\\s*key:\\s*'([a-z_]+)'").matcher(body);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    /** 从 known = new HashSet<>(Arrays.asList( ... )) 里抓出字符串字面量。 */
    private static Set<String> parseKnownKeys(String java) {
        int start = java.indexOf("java.util.Set<String> known");
        int end = java.indexOf("));", start);
        String body = java.substring(start, end);
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"([a-z_]+)\"").matcher(body);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }
}
