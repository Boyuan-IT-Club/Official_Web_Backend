package club.boyuan.official.integration.feishu;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从飞书多维表格分享链接解析 app_token 与 table_id。
 * <p>
 * 示例：https://xxx.feishu.cn/base/AppTokenxxxx?table=tblxxxx
 */
public final class FeishuTableUrlParser {

    private static final Pattern APP_TOKEN_PATTERN = Pattern.compile("/(?:base|wiki)/([A-Za-z0-9]+)");
    private static final Pattern TABLE_ID_PATTERN = Pattern.compile("[?&]table=([A-Za-z0-9]+)");

    private FeishuTableUrlParser() {
    }

    public record ParsedTable(String appToken, String tableId) {
    }

    public static ParsedTable parse(String tableUrl) {
        if (!StringUtils.hasText(tableUrl)) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_TABLE_URL_INVALID, "飞书表格 URL 不能为空");
        }
        String normalized = tableUrl.trim();
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_TABLE_URL_INVALID, "飞书表格 URL 格式不正确");
        }

        String path = uri.getPath() == null ? normalized : uri.getPath();
        Matcher appMatcher = APP_TOKEN_PATTERN.matcher(path);
        if (!appMatcher.find()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_TABLE_URL_INVALID,
                    "无法从 URL 解析 app_token，请使用 /base/ 或 /wiki/ 形式的多维表格链接");
        }
        String appToken = appMatcher.group(1);

        String query = uri.getQuery();
        if (!StringUtils.hasText(query)) {
            query = normalized.contains("?") ? normalized.substring(normalized.indexOf('?') + 1) : "";
        }
        Matcher tableMatcher = TABLE_ID_PATTERN.matcher("?" + query);
        if (!tableMatcher.find()) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_TABLE_URL_INVALID,
                    "无法从 URL 解析 table_id，链接需包含 ?table=tblxxx 参数");
        }
        return new ParsedTable(appToken, tableMatcher.group(1));
    }
}
