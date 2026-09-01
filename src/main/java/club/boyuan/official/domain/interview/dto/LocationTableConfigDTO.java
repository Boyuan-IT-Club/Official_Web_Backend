package club.boyuan.official.domain.interview.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 「按地点分桶」的一行配置状态，供管理端的飞书同步页展示：
 * 这个地点有多少人待推、配没配链接、上次同步到哪一步。
 */
@Data
@Accessors(chain = true)
public class LocationTableConfigDTO {

    /** 面试地点（取自 interview_session.location） */
    private String location;

    /** 已配置的飞书多维表格链接；null 表示尚未配置，推送时该地点会被跳过 */
    private String feishuTableUrl;

    private String remark;

    /** 该地点下的场次数 */
    private int sessionCount;

    /** 该地点已分配的候选人数 */
    private int scheduleCount;

    /** 其中尚未同步到飞书的人数（sync_status = 0） */
    private int pendingCount;
}
