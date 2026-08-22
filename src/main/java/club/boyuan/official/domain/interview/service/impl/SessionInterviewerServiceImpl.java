package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.service.ISessionInterviewerService;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.SessionInterviewer;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试场次与面试官绑定实现。
 *
 * @author dhy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionInterviewerServiceImpl implements ISessionInterviewerService {

    private final SessionInterviewerMapper sessionInterviewerMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Integer> bindInterviewers(Integer sessionId, List<Integer> userIds) {
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(BusinessExceptionEnum.INTERVIEW_SESSION_NOT_FOUND);
        }

        Set<Integer> distinctIds = new LinkedHashSet<>(userIds == null ? Collections.emptyList() : userIds);
        if (!distinctIds.isEmpty()) {
            // 必须走自定义的 selectUsersByIds：User 实体带着已不存在的 role 列，
            // MyBatis-Plus 的通用查询会拼出「Unknown column 'role'」
            List<User> users = userMapper.selectUsersByIds(new ArrayList<>(distinctIds));
            Set<Integer> foundIds = users.stream().map(User::getUserId).collect(Collectors.toSet());
            List<Integer> missing = distinctIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());
            if (!missing.isEmpty()) {
                throw new BusinessException(BusinessExceptionEnum.EVALUATION_INTERVIEWER_NOT_FOUND,
                        "以下面试官用户不存在：" + missing);
            }
        }

        sessionInterviewerMapper.delete(new LambdaQueryWrapper<SessionInterviewer>()
                .eq(SessionInterviewer::getSessionId, sessionId));
        for (Integer userId : distinctIds) {
            sessionInterviewerMapper.insert(new SessionInterviewer()
                    .setSessionId(sessionId)
                    .setUserId(userId));
        }
        log.info("场次 {} 绑定面试官 {}", sessionId, distinctIds);
        return new ArrayList<>(distinctIds);
    }

    @Override
    public List<Integer> listInterviewerIds(Integer sessionId) {
        return sessionInterviewerMapper.selectList(new LambdaQueryWrapper<SessionInterviewer>()
                        .eq(SessionInterviewer::getSessionId, sessionId)
                        .orderByAsc(SessionInterviewer::getId))
                .stream()
                .map(SessionInterviewer::getUserId)
                .collect(Collectors.toList());
    }
}
