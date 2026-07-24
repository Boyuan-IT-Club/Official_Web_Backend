package club.boyuan.official.integration.feishu;

import club.boyuan.official.infra.config.FeishuProperties;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 飞书多维表格 API 客户端（鉴权缓存 + 分页读取 + 批量新增/更新，带重试与熔断）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeishuBitableClient {

    private final FeishuProperties feishuProperties;
    private final ObjectMapper objectMapper;
    private final FeishuApiInvoker feishuApiInvoker;

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    private record CachedToken(String token, Instant expireAt) {
        boolean valid() {
            return StringUtils.hasText(token) && expireAt.isAfter(Instant.now().plusSeconds(60));
        }
    }

    public record RecordUpdate(String recordId, Map<String, Object> fields) {
    }

    /**
     * 分页读取多维表格全部记录（用于从飞书导入平台）。
     */
    public List<FeishuBitableRecord> listAllRecords(String tableUrl) {
        ensureConfigured();
        FeishuTableUrlParser.ParsedTable parsed = FeishuTableUrlParser.parse(tableUrl);
        String token = getTenantAccessToken();

        List<FeishuBitableRecord> all = new ArrayList<>();
        String pageToken = null;
        int pageSize = Math.max(1, Math.min(feishuProperties.getListPageSize(), 500));
        do {
            PageSlice slice = listRecordsPage(parsed, token, pageSize, pageToken);
            all.addAll(slice.records());
            pageToken = slice.hasMore() ? slice.nextPageToken() : null;
        } while (pageToken != null);

        return all;
    }

    private record PageSlice(List<FeishuBitableRecord> records, boolean hasMore, String nextPageToken) {
    }

    private PageSlice listRecordsPage(
            FeishuTableUrlParser.ParsedTable parsed,
            String token,
            int pageSize,
            String pageToken) {
        StringBuilder url = new StringBuilder(feishuProperties.getApiBaseUrl())
                .append("/open-apis/bitable/v1/apps/").append(parsed.appToken())
                .append("/tables/").append(parsed.tableId())
                .append("/records?page_size=").append(pageSize);
        if (StringUtils.hasText(pageToken)) {
            url.append("&page_token=").append(pageToken);
        }

        String responseBody = feishuApiInvoker.get(url.toString(), token);
        return parseListPage(responseBody);
    }

    private PageSlice parseListPage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("未知错误");
                log.error("飞书 list records 失败 code={}, msg={}", code, msg);
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, msg);
            }
            JsonNode data = root.path("data");
            List<FeishuBitableRecord> records = new ArrayList<>();
            for (JsonNode item : data.path("items")) {
                String recordId = item.path("record_id").asText(null);
                JsonNode fields = item.path("fields");
                records.add(FeishuBitableRecordParser.parse(recordId, fields));
            }
            boolean hasMore = data.path("has_more").asBoolean(false);
            String next = data.path("page_token").asText(null);
            return new PageSlice(records, hasMore, next);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("解析飞书 list records 响应失败", ex);
            throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "飞书 API 响应解析失败");
        }
    }

    /**
     * 批量新增行，返回与入参顺序一致的 record_id 列表。
     */
    public FeishuBatchWriteResult batchCreateRecords(String tableUrl, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new FeishuBatchWriteResult(0, List.of());
        }
        ensureConfigured();
        FeishuTableUrlParser.ParsedTable parsed = FeishuTableUrlParser.parse(tableUrl);
        String token = getTenantAccessToken();

        List<String> allRecordIds = new ArrayList<>();
        int total = 0;
        int batchSize = Math.max(1, Math.min(feishuProperties.getBatchSize(), 500));
        for (int i = 0; i < rows.size(); i += batchSize) {
            List<Map<String, Object>> chunk = rows.subList(i, Math.min(i + batchSize, rows.size()));
            FeishuBatchWriteResult chunkResult = doBatchCreate(parsed, token, chunk);
            total += chunkResult.count();
            allRecordIds.addAll(chunkResult.recordIds());
        }
        return new FeishuBatchWriteResult(total, allRecordIds);
    }

    /**
     * 按 record_id 批量更新已有行。
     */
    public FeishuBatchWriteResult batchUpdateRecords(String tableUrl, List<RecordUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return new FeishuBatchWriteResult(0, List.of());
        }
        ensureConfigured();
        FeishuTableUrlParser.ParsedTable parsed = FeishuTableUrlParser.parse(tableUrl);
        String token = getTenantAccessToken();

        List<String> allRecordIds = new ArrayList<>();
        int total = 0;
        int batchSize = Math.max(1, Math.min(feishuProperties.getBatchSize(), 500));
        for (int i = 0; i < updates.size(); i += batchSize) {
            List<RecordUpdate> chunk = updates.subList(i, Math.min(i + batchSize, updates.size()));
            FeishuBatchWriteResult chunkResult = doBatchUpdate(parsed, token, chunk);
            total += chunkResult.count();
            allRecordIds.addAll(chunkResult.recordIds());
        }
        return new FeishuBatchWriteResult(total, allRecordIds);
    }

    private FeishuBatchWriteResult doBatchCreate(
            FeishuTableUrlParser.ParsedTable parsed, String token, List<Map<String, Object>> chunk) {
        List<Map<String, Object>> records = new ArrayList<>(chunk.size());
        for (Map<String, Object> fields : chunk) {
            records.add(Map.of("fields", fields));
        }
        Map<String, Object> body = Map.of("records", records);

        String url = feishuProperties.getApiBaseUrl()
                + "/open-apis/bitable/v1/apps/" + parsed.appToken()
                + "/tables/" + parsed.tableId()
                + "/records/batch_create";

        String responseBody = feishuApiInvoker.postJson(url, token, body);
        return parseWriteResponse(responseBody, chunk.size());
    }

    private FeishuBatchWriteResult doBatchUpdate(
            FeishuTableUrlParser.ParsedTable parsed, String token, List<RecordUpdate> chunk) {
        List<Map<String, Object>> records = new ArrayList<>(chunk.size());
        for (RecordUpdate update : chunk) {
            Map<String, Object> record = new HashMap<>();
            record.put("record_id", update.recordId());
            record.put("fields", update.fields());
            records.add(record);
        }
        Map<String, Object> body = Map.of("records", records);

        String url = feishuProperties.getApiBaseUrl()
                + "/open-apis/bitable/v1/apps/" + parsed.appToken()
                + "/tables/" + parsed.tableId()
                + "/records/batch_update";

        String responseBody = feishuApiInvoker.postJson(url, token, body);
        return parseWriteResponse(responseBody, chunk.size());
    }

    private FeishuBatchWriteResult parseWriteResponse(String responseBody, int fallbackCount) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("未知错误");
                log.error("飞书 batch 写入失败 code={}, msg={}", code, msg);
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, msg);
            }
            List<String> recordIds = new ArrayList<>();
            for (JsonNode item : root.path("data").path("records")) {
                String id = item.path("record_id").asText(null);
                if (StringUtils.hasText(id)) {
                    recordIds.add(id);
                }
            }
            int count = recordIds.isEmpty() ? fallbackCount : recordIds.size();
            return new FeishuBatchWriteResult(count, recordIds);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("解析飞书 batch 写入响应失败", ex);
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

            String responseBody = feishuApiInvoker.postJson(url, null, body);
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
