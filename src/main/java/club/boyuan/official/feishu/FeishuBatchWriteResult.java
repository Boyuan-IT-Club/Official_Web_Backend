package club.boyuan.official.feishu;

import java.util.List;

/**
 * 飞书批量写入结果：record_id 与请求行顺序一一对应。
 */
public record FeishuBatchWriteResult(int count, List<String> recordIds) {
}
