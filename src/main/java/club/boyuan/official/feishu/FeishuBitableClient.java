package club.boyuan.official.feishu;

import club.boyuan.official.config.FeishuProperties;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 飞书多维表格 API 客户端（tenant_access_token + batch_create）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeishuBitableClient {

    private final FeishuProperties feishuProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    private record CachedToken(String token, Instant expireAt) {
        boolean valid() {
            return StringUtils.hasText(token) && expireAt.isAfter(Instant.now().plusSeconds(60));
        }
    }

    public int batchCreateRecords(String tableUrl, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        ensureConfigured();
        FeishuTableUrlParser.ParsedTable parsed = FeishuTableUrlParser.parse(tableUrl);
        String token = getTenantAccessToken();

        int imported = 0;
        int batchSize = Math.max(1, Math.min(feishuProperties.getBatchSize(), 500));
        for (int i = 0; i < rows.size(); i += batchSize) {
            List<Map<String, Object>> chunk = rows.subList(i, Math.min(i + batchSize, rows.size()));
            imported += doBatchCreate(parsed, token, chunk);
        }
        return imported;
    }

    private int doBatchCreate(FeishuTableUrlParser.ParsedTable parsed, String token, List<Map<String, Object>> chunk) {
        List<Map<String, Object>> records = new ArrayList<>(chunk.size());
        for (Map<String, Object> fields : chunk) {
            records.add(Map.of("fields", fields));
        }
        Map<String, Object> body = Map.of("records", records);

        String url = feishuProperties.getApiBaseUrl()
                + "/open-apis/bitable/v1/apps/" + parsed.appToken()
                + "/tables/" + parsed.tableId()
                + "/records/batch_create";

        String responseBody = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("未知错误");
                log.error("飞书 batch_create 失败 code={}, msg={}, body={}", code, msg, responseBody);
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, msg);
            }
            return chunk.size();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("解析飞书 batch_create 响应失败", ex);
            throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "飞书 API 响应解析失败");
        }
    }

    private String getTenantAccessToken() {
        CachedToken current = cachedToken.get();
        if (current != null && current.valid()) {
            return current.token();
        }
        synchronized (this) {
            current = cachedToken.get();
            if (current != null && current.valid()) {
                return current.token();
            }
            String url = feishuProperties.getApiBaseUrl() + "/open-apis/auth/v3/tenant_access_token/internal";
            Map<String, String> body = Map.of(
                    "app_id", feishuProperties.getAppId(),
                    "app_secret", feishuProperties.getAppSecret());

            String responseBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            try {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.path("code").asInt(-1) != 0) {
                    throw new BusinessException(BusinessExceptionEnum.FEISHU_AUTH_FAILED,
                            root.path("msg").asText("获取 tenant_access_token 失败"));
                }
                String token = root.path("tenant_access_token").asText();
                int expireSeconds = root.path("expire").asInt(7200);
                cachedToken.set(new CachedToken(token, Instant.now().plusSeconds(expireSeconds)));
                return token;
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BusinessException(BusinessExceptionEnum.FEISHU_AUTH_FAILED, "飞书鉴权响应解析失败");
            }
        }
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(feishuProperties.getAppId())
                || !StringUtils.hasText(feishuProperties.getAppSecret())) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_NOT_CONFIGURED,
                    "请配置环境变量 FEISHU_APP_ID 与 FEISHU_APP_SECRET（飞书开放平台企业自建应用）");
        }
    }
}
