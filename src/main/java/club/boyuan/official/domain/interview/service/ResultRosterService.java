package club.boyuan.official.domain.interview.service;

import club.boyuan.official.persistence.mapper.InterviewResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 录取结果名单的自动补齐。
 *
 * 原先名单只能靠管理员在「录取结果」页手动点「从面试安排生成名单」，忘了点，预录取页就是空的、
 * 后来排上面试或补交简历的人也不会进来。现在打开结果、预录取、统计、通知、流程进度这些页面时
 * 自动补齐，口径与原来的生成完全一致（本届已提交、非初筛未通过的简历，有生效安排的带上安排）。
 *
 * 幂等靠一条 INSERT ... SELECT ... ON DUPLICATE KEY UPDATE（no-op）：两台机器、两个管理员同时
 * 打开页面也只会多出一行；双机部署下进程内节流无效，所以不做节流，直接依赖 SQL 本身。
 *
 * 独立短事务（REQUIRES_NEW）：调用方可能处在只读事务里；失败只记日志，不能拖垮读接口。
 * 不在排期写入的事务里调用：新事务看不到外层未提交的安排，还可能等外层持有的锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultRosterService {

    private final InterviewResultMapper resultMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int ensure(Integer cycleId) {
        if (cycleId == null) {
            return 0;
        }
        try {
            int inserted = resultMapper.ensureRoster(cycleId);
            if (inserted > 0) {
                log.info("周期 {} 自动补齐结果名单 {} 行", cycleId, inserted);
            }
            return inserted;
        } catch (Exception e) {
            log.warn("周期 {} 自动补齐结果名单失败，不影响本次读取：{}", cycleId, e.getMessage());
            return 0;
        }
    }
}
