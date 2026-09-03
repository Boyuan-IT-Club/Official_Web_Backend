package club.boyuan.official.infra.storage;

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
 * 上传到 COS 的每个前缀，都必须在 SecurityConfig 里放行 /api/files/{前缀}/**。
 *
 * 起因是招新二维码：上传成功、库里也有记录，但管理端缩略图是裂图、学生端与
 * 录取邮件里什么都看不到。原因是 SecurityConfig 只放行了 avatars 与 activities，
 * 漏了 qrcodes —— 取图请求一律 401。
 *
 * 这个漏洞很难在开发时发现：COS 是私有桶，static.boyuan.club 只是把 / 转发到
 * 后端的 /api/files/，真正的闸门就是那份白名单；而上传本身是成功的，
 * 只有「显示」这一步静默失败。加一个新的上传前缀却忘了放行，
 * 表现就和这次一模一样，所以用测试盯住。
 *
 * 注意语义：本测试断言的是「上传的前缀都可公开读」。将来若出现**不该**公开的
 * 上传（例如简历附件），不要为了让测试通过而去放行它 —— 应当把它加进下面的
 * 例外清单，并想清楚它的读取鉴权走哪条路。
 */
class PublicFilePrefixWhitelistTest {

    private static final Path SECURITY_CONFIG = Path.of(
            "src/main/java/club/boyuan/official/infra/config/SecurityConfig.java");

    /** 明确不打算公开读的前缀。目前为空 —— 有了再往里加，别默默放行。 */
    private static final Set<String> INTENTIONALLY_PRIVATE = Set.of();

    @Test
    @DisplayName("每个上传前缀都在 /api/files 白名单里")
    void everyUploadPrefixIsPubliclyReadable() throws IOException {
        Set<String> uploaded = collectUploadPrefixes();
        assertTrue(uploaded.contains("qrcodes"),
                "没扫到 qrcodes 前缀，扫描逻辑多半坏了：" + uploaded);

        String security = Files.readString(SECURITY_CONFIG);
        Set<String> whitelisted = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"/api/files/([a-z]+)/\\*\\*\"").matcher(security);
        while (m.find()) {
            whitelisted.add(m.group(1));
        }

        Set<String> missing = new LinkedHashSet<>(uploaded);
        missing.removeAll(whitelisted);
        missing.removeAll(INTENTIONALLY_PRIVATE);

        assertTrue(missing.isEmpty(),
                "这些前缀上传得上去、却读不出来（取图会 401，表现为裂图）："
                        + missing + "；需要在 SecurityConfig 里放行 /api/files/{前缀}/**");
    }

    /** 扫描源码里所有 upload(file, "xxx") 的前缀字面量。 */
    private static Set<String> collectUploadPrefixes() throws IOException {
        Pattern call = Pattern.compile("upload(?:File)?\\(\\s*\\w+\\s*,\\s*\"([a-z]+)/?\"");
        Set<String> prefixes = new LinkedHashSet<>();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path p : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(p));
                while (m.find()) {
                    prefixes.add(m.group(1));
                }
            }
        }
        return prefixes;
    }
}
