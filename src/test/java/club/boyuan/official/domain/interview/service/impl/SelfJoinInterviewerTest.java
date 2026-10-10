package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.persistence.entity.InterviewSession;
import club.boyuan.official.persistence.entity.SessionInterviewer;
import club.boyuan.official.persistence.mapper.InterviewSessionMapper;
import club.boyuan.official.persistence.mapper.SessionInterviewerMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 面试当天自助顶班：把自己补进某一场的面试官。
 *
 * <p>必须是增量。场次管理页那个 {@code bindInterviewers} 是覆盖式的（先 delete
 * 整场再逐条 insert）—— 当天误用它顶班，会把同场其他面试官连同他们的评分列一起删掉。
 */
class SelfJoinInterviewerTest {

    private static final int SESSION = 29;
    private static final int ME = 3;
    private static final int ALREADY_THERE = 7;

    private SessionInterviewerMapper sessionInterviewerMapper;
    private InterviewSessionMapper interviewSessionMapper;
    private SessionInterviewerServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionInterviewerMapper = mock(SessionInterviewerMapper.class);
        interviewSessionMapper = mock(InterviewSessionMapper.class);
        service = new SessionInterviewerServiceImpl(
                sessionInterviewerMapper, interviewSessionMapper, mock(UserMapper.class));

        InterviewSession session = new InterviewSession();
        session.setSessionId(SESSION);
        when(interviewSessionMapper.selectById(SESSION)).thenReturn(session);
    }

    @SuppressWarnings("unchecked")
    private void alreadyBound(boolean bound) {
        when(sessionInterviewerMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(bound);
    }

    @SuppressWarnings("unchecked")
    private void currentlyBound(Integer... userIds) {
        when(sessionInterviewerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(
                java.util.Arrays.stream(userIds)
                        .map(id -> new SessionInterviewer().setSessionId(SESSION).setUserId(id))
                        .toList());
    }

    @Test
    @DisplayName("加进去的是自己，且只 insert 一条")
    void insertsOnlySelf() {
        alreadyBound(false);
        currentlyBound(ALREADY_THERE, ME);

        List<Integer> after = service.joinAsInterviewer(SESSION, ME);

        ArgumentCaptor<SessionInterviewer> captor = ArgumentCaptor.forClass(SessionInterviewer.class);
        verify(sessionInterviewerMapper).insert(captor.capture());
        assertEquals(ME, captor.getValue().getUserId());
        assertEquals(SESSION, captor.getValue().getSessionId());
        assertEquals(List.of(ALREADY_THERE, ME), after);
    }

    @Test
    @DisplayName("绝不删除同场其他面试官 —— 这是与覆盖式绑定最要命的区别")
    void neverDeletesOthers() {
        alreadyBound(false);
        currentlyBound(ALREADY_THERE, ME);

        service.joinAsInterviewer(SESSION, ME);

        verify(sessionInterviewerMapper, never()).delete(any());
    }

    @Test
    @DisplayName("重复点不报错，也不写第二条")
    void idempotent() {
        alreadyBound(true);
        currentlyBound(ALREADY_THERE, ME);

        assertEquals(List.of(ALREADY_THERE, ME), service.joinAsInterviewer(SESSION, ME));
        verify(sessionInterviewerMapper, never()).insert(any(SessionInterviewer.class));
    }

    @Test
    @DisplayName("场次不存在时拒绝，不留下悬空绑定")
    void unknownSessionRejected() {
        when(interviewSessionMapper.selectById(999)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.joinAsInterviewer(999, ME));
        verify(sessionInterviewerMapper, never()).insert(any(SessionInterviewer.class));
    }
}
