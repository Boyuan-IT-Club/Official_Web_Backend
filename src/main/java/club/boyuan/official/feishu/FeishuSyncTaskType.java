package club.boyuan.official.feishu;

/**
 * 飞书异步任务类型。
 */
public enum FeishuSyncTaskType {

    /** 平台 → 飞书（写出面试数据） */
    PUSH_TO_FEISHU,

    /** 飞书 → 平台（拉回面试结果） */
    PULL_FROM_FEISHU
}
