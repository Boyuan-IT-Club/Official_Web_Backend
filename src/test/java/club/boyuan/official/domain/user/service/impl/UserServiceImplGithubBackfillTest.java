package club.boyuan.official.domain.user.service.impl;

import club.boyuan.official.common.converter.UserConverter;
import club.boyuan.official.common.utils.JwtTokenUtil;
import club.boyuan.official.domain.user.dto.UserDTO;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.AwardExperienceMapper;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import club.boyuan.official.persistence.mapper.ResumeFieldValueMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import club.boyuan.official.persistence.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplGithubBackfillTest {

    @Mock private UserMapper userMapper;
    @Mock private AwardExperienceMapper awardExperienceMapper;
    @Mock private ResumeMapper resumeMapper;
    @Mock private ResumeFieldValueMapper resumeFieldValueMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private EvaluationSubmissionMapper evaluationSubmissionMapper;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private JwtTokenUtil jwtTokenUtil;
    @Mock private UserConverter userConverter;

    @Test
    void githubBindBackfillsUnclaimedSubmissions() {
        UserServiceImpl service = new UserServiceImpl(userMapper, awardExperienceMapper, resumeMapper,
                resumeFieldValueMapper, userRoleMapper, evaluationSubmissionMapper,
                passwordEncoder, jwtTokenUtil, userConverter);

        User user = new User();
        user.setUserId(7);
        when(userMapper.selectById(7)).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);

        UserDTO dto = new UserDTO();
        dto.setUserId(7);
        dto.setGithub("https://github.com/Alice");

        service.edit(dto);

        // 归一化后回填:update submission set user_id=7 where github_username='alice' and user_id is null
        verify(evaluationSubmissionMapper).update(any(EvaluationSubmission.class), any(LambdaUpdateWrapper.class));
    }
}