package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.domain.interview.dto.LocationTableConfigDTO;
import club.boyuan.official.domain.interview.dto.SaveLocationTableRequestDTO;
import club.boyuan.official.domain.interview.service.ILocationTableService;
import club.boyuan.official.persistence.entity.CycleLocationFeishuTable;
import club.boyuan.official.persistence.entity.InterviewSchedule;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.mapper.CycleLocationFeishuTableMapper;
import club.boyuan.official.persistence.mapper.InterviewScheduleMapper;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationTableServiceImpl implements ILocationTableService {

    /** 场次没填地点时的兜底键，与 FeishuImportExecutor 的分桶键保持一致 */
    public static final String FALLBACK_LOCATION = "未指定地点";

    private final CycleLocationFeishuTableMapper mapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewScheduleMapper scheduleMapper;

    /**
     * 统一的地点键规整：去空格；空值归到兜底键。
     * 推送分桶、配置保存、列表展示都必须用同一个函数，否则"教三-301 "和"教三-301"
     * 会被当成两个地点，配了链接的那个推不出去。
     */
    public static String normalize(String location) {
        return StringUtils.hasText(location) ? location.trim() : FALLBACK_LOCATION;
    }

    @Override
    public List<LocationTableConfigDTO> listByCycle(Integer cycleId) {
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>().eq(InterviewSession::getCycleId, cycleId));
        List<InterviewSchedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<InterviewSchedule>().eq(InterviewSchedule::getCycleId, cycleId));

        Map<Integer, String> locationBySession = sessions.stream()
                .collect(Collectors.toMap(InterviewSession::getSessionId, s -> normalize(s.getLocation()),
                        (a, b) -> a));
        Map<String, String> urlMap = urlMapOf(cycleId);
        Map<String, String> remarkMap = listRaw(cycleId).stream()
                .collect(Collectors.toMap(r -> normalize(r.getLocation()),
                        r -> r.getRemark() == null ? "" : r.getRemark(), (a, b) -> a));

        // 以场次里出现过的地点为骨架，保证"还没配链接的地点"也能在管理端看到
        Map<String, LocationTableConfigDTO> result = new LinkedHashMap<>();
        for (InterviewSession session : sessions) {
            String key = normalize(session.getLocation());
            LocationTableConfigDTO dto = result.computeIfAbsent(key, k -> new LocationTableConfigDTO()
                    .setLocation(k)
                    .setFeishuTableUrl(urlMap.get(k))
                    .setRemark(remarkMap.get(k)));
            dto.setSessionCount(dto.getSessionCount() + 1);
        }

        for (InterviewSchedule schedule : schedules) {
            String key = locationBySession.get(schedule.getSessionId());
            if (key == null) {
                // 没有 session_id 的历史排期（方案A 遗留）归到兜底键，避免统计凭空少人
                key = FALLBACK_LOCATION;
            }
            LocationTableConfigDTO dto = result.computeIfAbsent(key, k -> new LocationTableConfigDTO()
                    .setLocation(k)
                    .setFeishuTableUrl(urlMap.get(k))
                    .setRemark(remarkMap.get(k)));
            dto.setScheduleCount(dto.getScheduleCount() + 1);
            if (schedule.getSyncStatus() == null || schedule.getSyncStatus() == 0) {
                dto.setPendingCount(dto.getPendingCount() + 1);
            }
        }

        // 配过链接但当前已无场次的地点也列出来，否则用户会以为配置丢了
        urlMap.forEach((key, url) -> result.computeIfAbsent(key, k -> new LocationTableConfigDTO()
                .setLocation(k)
                .setFeishuTableUrl(url)
                .setRemark(remarkMap.get(k))));

        return new ArrayList<>(result.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Integer cycleId, SaveLocationTableRequestDTO request) {
        String location = normalize(request.getLocation());
        CycleLocationFeishuTable existing = mapper.selectOne(
                new LambdaQueryWrapper<CycleLocationFeishuTable>()
                        .eq(CycleLocationFeishuTable::getCycleId, cycleId)
                        .eq(CycleLocationFeishuTable::getLocation, location));

        String url = request.getFeishuTableUrl() == null ? null : request.getFeishuTableUrl().trim();
        if (!StringUtils.hasText(url)) {
            // 留空即清除配置：该地点之后会在推送时被跳过（并在响应里说明原因）
            if (existing != null) {
                mapper.deleteById(existing.getId());
                log.info("清除地点飞书表格配置，cycleId={}，location={}", cycleId, location);
            }
            return;
        }

        if (existing == null) {
            mapper.insert(new CycleLocationFeishuTable()
                    .setCycleId(cycleId)
                    .setLocation(location)
                    .setFeishuTableUrl(url)
                    .setRemark(request.getRemark()));
        } else {
            mapper.updateById(existing
                    .setFeishuTableUrl(url)
                    .setRemark(request.getRemark()));
        }
        log.info("保存地点飞书表格配置，cycleId={}，location={}", cycleId, location);
    }

    @Override
    public Map<String, String> urlMapOf(Integer cycleId) {
        return listRaw(cycleId).stream()
                .filter(r -> StringUtils.hasText(r.getFeishuTableUrl()))
                .collect(Collectors.toMap(r -> normalize(r.getLocation()),
                        CycleLocationFeishuTable::getFeishuTableUrl, (a, b) -> a));
    }

    @Override
    public List<CycleLocationFeishuTable> listRaw(Integer cycleId) {
        return mapper.selectList(new LambdaQueryWrapper<CycleLocationFeishuTable>()
                        .eq(CycleLocationFeishuTable::getCycleId, cycleId))
                .stream().filter(Objects::nonNull).collect(Collectors.toList());
    }
}
