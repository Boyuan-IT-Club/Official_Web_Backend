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

    /** 飞书字段类型（实测自社团模板表）:1=文本 2=数字 3=单选 4=多选 7=复选框 */
    private static final int FIELD_TYPE_TEXT = 1;
    private static final int FIELD_TYPE_NUMBER = 2;
    private static final int FIELD_TYPE_SINGLE_SELECT = 3;
    private static final int FIELD_TYPE_MULTI_SELECT = 4;
    private static final int FIELD_TYPE_CHECKBOX = 7;

    /** 多选值的分隔符:formatIntendedDepartments 产出 "技术部 / 项目部",也容忍逗号 */
    private static final java.util.regex.Pattern MULTI_SPLIT = java.util.regex.Pattern.compile("[/,、]");

    /** 建列时按表串行,避免"先读后建"竞态在同表建出重复列 */
    private static final java.util.concurrent.ConcurrentMap<String, Object> TABLE_LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();

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
    /**
     * 确保这些列在目标表里都存在,缺的按文本类型自动补建。
     *
     * 飞书多维表格 API 不会在写入时自动建列 —— 写不存在的列名会整批返回
     * FieldNameNotFound。管理员往往只给一张空表(默认只有一个「文本」字段)的链接,
     * 于是推送整批失败,界面还只说「同步失败」。推送前先对齐表结构。
     *
     * 只新增、不改类型、不删多余列:全部按文本(type=1)建,足够承载导出内容,
     * 也不会动管理员在飞书里已经建好的其它列。
     *
     * @return 表内全部列的 名称 -> 飞书字段类型,供写入前把值转成该列能接受的类型
     */
    public java.util.Map<String, Integer> ensureFieldsExist(
            String tableUrl, java.util.Collection<String> requiredFieldNames) {
        ensureConfigured();
        if (requiredFieldNames == null || requiredFieldNames.isEmpty()) {
            return java.util.Map.of();
        }
        FeishuTableUrlParser.ParsedTable parsed = FeishuTableUrlParser.parse(tableUrl);
        String token = getTenantAccessToken();

        // 同表串行:并发推送时两个任务各自"先读后建",都读到不存在就会各建一遍,
        // 在生产表上实测建出了 6 个重复列(姓名/年级/预选/是否调剂/第二、三类问题)。
        // 跨节点仍有极小窗口,靠飞书自身的 FieldNameDuplicated 兜底。
        Object lock = TABLE_LOCKS.computeIfAbsent(parsed.tableId(), k -> new Object());
        synchronized (lock) {
            return ensureFieldsLocked(parsed, token, requiredFieldNames);
        }
    }

    private java.util.Map<String, Integer> ensureFieldsLocked(
            FeishuTableUrlParser.ParsedTable parsed, String token,
            java.util.Collection<String> requiredFieldNames) {
        java.util.Map<String, Integer> existing = listFieldTypes(parsed, token);
        String base = feishuProperties.getApiBaseUrl()
                + "/open-apis/bitable/v1/apps/" + parsed.appToken()
                + "/tables/" + parsed.tableId() + "/fields";

        int created = 0;
        for (String name : requiredFieldNames) {
            if (name == null || name.isBlank() || existing.containsKey(name)) {
                continue;
            }
            // type=1 多行文本；用 Map 而非拼串,交给 postJson 序列化转义
            String resp = feishuApiInvoker.postJson(base, token, Map.of("field_name", name, "type", 1));
            JsonNode root;
            try {
                root = objectMapper.readTree(resp);
            } catch (Exception e) {
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED,
                        "创建飞书列「" + name + "」时响应解析失败");
            }
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                // 并发下别的推送可能刚建过同名列,这种重复报错忽略即可
                String msg = root.path("msg").asText("");
                // 飞书对已存在的列名返回 FieldNameDuplicated（实测 code 1254014）；
                // 并发推送下可能刚被别的任务建过,视为成功。
                if (msg.contains("Duplicated") || msg.contains("exist") || msg.contains("已存在")) {
                    // 重复说明列已在（并发推送），但本次没读到它的类型，按文本处理
                    existing.putIfAbsent(name, FIELD_TYPE_TEXT);
                    continue;
                }
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED,
                        "自动创建飞书列「" + name + "」失败: " + msg);
            }
            existing.put(name, FIELD_TYPE_TEXT);
            created++;
        }
        if (created > 0) {
            log.info("飞书表 {} 自动补建 {} 个缺失列", parsed.tableId(), created);
        }
        return existing;
    }

    /**
     * 把整行的值转成各列实际类型能接受的形式。
     *
     * 起因:自动建的列都是文本(type=1),而 resumeScore 是 int —— 往文本列写数字,
     * 飞书返回 TextFieldConvFail(code 1254060),整批失败。
     * 按列的真实类型转换而不是一律 toString:管理员可能手动把「简历评分」改成
     * 数字列,那时要写数字才对。未知类型不动,管理员自己清楚他建的是什么。
     */
    public static Map<String, Object> coerceRow(Map<String, Object> row, java.util.Map<String, Integer> fieldTypes) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        Map<String, Object> out = new HashMap<>(row.size());
        row.forEach((k, v) -> {
            int type = fieldTypes == null ? FIELD_TYPE_TEXT : fieldTypes.getOrDefault(k, FIELD_TYPE_TEXT);
            Object coerced = coerceValue(v, type);
            // OMIT 表示这一格不写 —— 复选框/单选/多选不接受空串,
            // 写空串会整批报 XxxFieldConvFail;省略等于"留空不动"。
            if (coerced != OMIT) {
                out.put(k, coerced);
            }
        });
        return out;
    }

    /** 读取表的全部字段名与类型(建列对齐 + 写入前值类型转换都要用) */
    private java.util.Map<String, Integer> listFieldTypes(FeishuTableUrlParser.ParsedTable parsed, String token) {
        String url = feishuProperties.getApiBaseUrl()
                + "/open-apis/bitable/v1/apps/" + parsed.appToken()
                + "/tables/" + parsed.tableId() + "/fields?page_size=100";
        String resp = feishuApiInvoker.get(url, token);
        java.util.Map<String, Integer> names = new java.util.HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(resp);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED,
                        "读取飞书表字段失败: " + root.path("msg").asText("未知错误"));
            }
            for (JsonNode it : root.path("data").path("items")) {
                String n = it.path("field_name").asText(null);
                if (n != null) {
                    names.put(n, it.path("type").asInt(FIELD_TYPE_TEXT));
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.FEISHU_IMPORT_FAILED, "读取飞书表字段响应解析失败");
        }
        return names;
    }

    /** 省略标记:该格不写入(区别于"写一个空值") */
    private static final Object OMIT = new Object();

    private static Object coerceValue(Object v, int type) {
        String raw = v == null ? "" : String.valueOf(v).trim();
        switch (type) {
            case FIELD_TYPE_TEXT:
                return v == null ? "" : String.valueOf(v);
            case FIELD_TYPE_NUMBER:
                if (raw.isEmpty()) {
                    return OMIT;
                }
                if (v instanceof Number) {
                    return v;
                }
                try {
                    return Double.valueOf(raw);
                } catch (NumberFormatException e) {
                    return OMIT;   // 非数字内容不写，别让整批连坐
                }
            case FIELD_TYPE_MULTI_SELECT: {
                if (raw.isEmpty()) {
                    return OMIT;
                }
                // "技术部 / 项目部" -> ["技术部","项目部"]；多选列只接受数组
                List<String> parts = new ArrayList<>();
                for (String part : MULTI_SPLIT.split(raw)) {
                    String t = part.trim();
                    if (!t.isEmpty()) {
                        parts.add(t);
                    }
                }
                return parts.isEmpty() ? OMIT : parts;
            }
            case FIELD_TYPE_SINGLE_SELECT:
                return raw.isEmpty() ? OMIT : raw;
            case FIELD_TYPE_CHECKBOX:
                if (raw.isEmpty()) {
                    return OMIT;   // 留空即未勾选，写 "" 会 CheckboxFieldConvFail
                }
                if (v instanceof Boolean) {
                    return v;
                }
                return "是".equals(raw) || "true".equalsIgnoreCase(raw) || "1".equals(raw) || "y".equalsIgnoreCase(raw);
            default:
                // 日期/人员/附件等：空值不写，非空原样交给飞书校验
                return raw.isEmpty() ? OMIT : v;
        }
    }

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
