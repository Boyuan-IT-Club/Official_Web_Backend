package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.LocationTableConfigDTO;
import club.boyuan.official.domain.interview.dto.SaveLocationTableRequestDTO;
import club.boyuan.official.persistence.entity.CycleLocationFeishuTable;

import java.util.List;
import java.util.Map;

/**
 * 「周期 × 地点 → 飞书多维表格」的配置管理。
 */
public interface ILocationTableService {

    /**
     * 列出该周期的所有面试地点及其链接配置与待推人数。
     * 地点取自 interview_session（方案B 的地点真源），没配链接的地点也会列出来，
     * 便于管理端看出"哪个地点还没配、配了会推多少人"。
     */
    List<LocationTableConfigDTO> listByCycle(Integer cycleId);

    /** 保存/更新某地点的链接；链接留空表示清除配置。 */
    void save(Integer cycleId, SaveLocationTableRequestDTO request);

    /** 地点 → 链接，供推送分桶时查询。 */
    Map<String, String> urlMapOf(Integer cycleId);

    List<CycleLocationFeishuTable> listRaw(Integer cycleId);
}
