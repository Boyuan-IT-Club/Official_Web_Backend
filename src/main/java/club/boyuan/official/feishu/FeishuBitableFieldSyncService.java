package club.boyuan.official.feishu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 导出前对齐飞书多维表格表头：已有列保留，缺失列自动创建。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeishuBitableFieldSyncService {

    private final FeishuBitableClient feishuBitableClient;

    /** 同一 JVM 内按 tableUrl 缓存已确认的列名，避免同表多桶重复 list/create */
    private final ConcurrentMap<String, Set<String>> confirmedFieldsByTableUrl = new ConcurrentHashMap<>();

    /**
     * 确保导出所需列在飞书表中存在（平台 → 飞书 PUSH）。
     */
    public void ensurePushExportFields(String tableUrl) {
        ensureFields(tableUrl, FeishuBitableExportSchema.PUSH_FIELDS);
    }

    public void ensureFields(String tableUrl, List<FeishuBitableFieldSpec> requiredFields) {
        if (requiredFields == null || requiredFields.isEmpty()) {
            return;
        }
        Set<String> known = confirmedFieldsByTableUrl.computeIfAbsent(tableUrl, url -> new HashSet<>());
        synchronized (known) {
            Set<String> existing = feishuBitableClient.listFieldNames(tableUrl);
            known.addAll(existing);

            int created = 0;
            for (FeishuBitableFieldSpec spec : requiredFields) {
                if (known.contains(spec.fieldName())) {
                    continue;
                }
                feishuBitableClient.createField(tableUrl, spec.fieldName(), spec.type());
                known.add(spec.fieldName());
                created++;
                log.info("飞书表已新增列 tableUrl={}, fieldName={}, type={}",
                        tableUrl, spec.fieldName(), spec.type());
            }
            if (created > 0) {
                log.info("飞书表头对齐完成 tableUrl={}, 新增 {} 列, 当前共 {} 列",
                        tableUrl, created, known.size());
            } else {
                log.debug("飞书表头已齐全 tableUrl={}, 列数={}", tableUrl, known.size());
            }
        }
    }

    /** 测试或强制刷新时清除缓存 */
    public void evictCache(String tableUrl) {
        if (tableUrl != null) {
            confirmedFieldsByTableUrl.remove(tableUrl);
        }
    }
}
