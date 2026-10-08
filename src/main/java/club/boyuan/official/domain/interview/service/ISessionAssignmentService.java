package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.SessionAssignmentResultDTO;
import club.boyuan.official.domain.interview.dto.UpdateInterviewTimeResponseDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试场次分配服务（方案B）：结构化志愿部门 + 场次容量 + 一志愿降级二志愿 + 精确时间细分。
 */
public interface ISessionAssignmentService {

    /**
     * 为指定周期批量分配面试场次。仅处理"已提交简历 + 已填志愿 + 尚未分配"的候选人，可重复执行。
     */
    SessionAssignmentResultDTO assign(Integer cycleId);

    /**
     * 待人工调剂名单：已填志愿但算法未能分配到场次的候选人。
     */
    List<SessionAssignmentResultDTO.UnassignedItem> listUnassigned(Integer cycleId);

    /**
     * 人工调剂：把某位候选人一键分配 / 再分配到另一个有空的场次。
     * 已有安排则从原场次释放名额后转入目标场次；没有则新建安排。
     */
    SessionAssignmentResultDTO.AssignedItem manualAssign(Integer resumeId, Integer targetSessionId);

    /**
     * 把人排成线上面试（或把已排好的改成线上）。
     * <p>
     * 和 manualAssign 并列的另一条出口：manualAssign 只能排进实体场次，
     * 于是「同意改期后进了待调剂池、但他要的本来就是线上」这种人无处安放——
     * 管理员只能把他塞回某个教室，或者干脆在系统外私下约。
     *
     * @param resumeId      候选人简历
     * @param interviewTime 面试时间；为空时沿用这条安排上已有的时间，两者都没有则报错
     * @return 与人工调剂同形的结果，便于前端复用渲染
     */
    SessionAssignmentResultDTO.AssignedItem assignOnline(Integer resumeId, java.time.LocalDateTime interviewTime);

    /**
     * 管理员手动把某条面试安排的 interview_time 调整到精确的几点几分。
     * <p>该操作标记此条时间为「人工指定」({@code timeOverridden=1})，并把
     * sync_status / notif_status 重置为 0，以触发飞书重新同步与后续提醒。
     * 指定时间超出场次时间窗、或同场次他人已占该时刻时只告警不拒绝。</p>
     */
    UpdateInterviewTimeResponseDTO updateInterviewTime(Integer scheduleId, LocalDateTime interviewTime);

    /**
     * 手动调整面试时间，并可同时换场次。
     *
     * @param targetSessionId 目标场次；为空表示不换场。方案B 的面试地点属于场次，
     *                        改地点即换场次，故与改时间同一入口。目标场次已满则拒绝。
     */
    UpdateInterviewTimeResponseDTO updateInterviewTime(
            Integer scheduleId, LocalDateTime interviewTime, Integer targetSessionId);
}
