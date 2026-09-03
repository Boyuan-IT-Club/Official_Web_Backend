package club.boyuan.official.infra.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传的类型闸门：默认只收图片，只有简历附件例外。
 *
 * 起因是一个只在真实环境才暴露的 bug：CosStorageService.upload 里写死了
 * 「非 image/* 一律拒绝」，而简历附件的需求恰恰是任意格式，
 * 结果上传 PDF 直接 4007。单元测试当时只覆盖了类型判定与文件名消毒，
 * 没碰上传路径（那需要真的 COS），所以一路绿到线上才发现。
 *
 * 这个测试守两件事：
 *   1) 附件确实以 imageOnly=false 调用 —— 否则功能又会退回「只能传图」
 *   2) 别的调用方没有顺手一起放开 —— 放开类型只该发生在附件这一处，
 *      因为只有它在读取那一侧配了完整的防护（逐次鉴权 + nosniff + 内联白名单）
 */
class UploadTypeGateTest {

    private static final Path SRC = Path.of("src/main/java");

    @Test
    @DisplayName("简历附件以 imageOnly=false 上传")
    void attachmentUploadAllowsAnyType() throws IOException {
        String impl = Files.readString(SRC.resolve(
                "club/boyuan/official/domain/resume/service/impl/ResumeAttachmentServiceImpl.java"));
        assertTrue(impl.contains("upload(file, \"attachments\", false)"),
                "附件必须以 imageOnly=false 上传，否则「任意格式」这个需求就没了");
    }

    @Test
    @DisplayName("放开类型的调用方有且只有简历附件")
    void onlyAttachmentsBypassTheImageGate() throws IOException {
        // 三参数重载里最后一个实参为 false 的调用点
        Pattern bypass = Pattern.compile("upload\\(\\s*\\w+\\s*,\\s*\"([a-z]+)\"\\s*,\\s*false\\s*\\)");
        int found = 0;
        try (var paths = Files.walk(SRC)) {
            for (Path p : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = bypass.matcher(Files.readString(p));
                while (m.find()) {
                    assertEquals("attachments", m.group(1),
                            "只有简历附件该放开类型限制，这里却是 " + m.group(1)
                                    + "（" + p.getFileName() + "）——"
                                    + "别的上传口没有附件那套读取侧防护");
                    found++;
                }
            }
        }
        assertEquals(1, found, "预期恰好一处放开类型限制，实际 " + found + " 处");
    }
}
