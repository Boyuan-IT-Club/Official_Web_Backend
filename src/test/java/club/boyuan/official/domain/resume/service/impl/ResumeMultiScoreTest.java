package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.dto.ResumeScoreEntryDTO;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.ResumeScoreEntry;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.ResumeScoreEntryMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 多人打分：每人一票、平均分写回聚合列、明细可追溯。
 *
 * 用内存假表模拟明细表——普通 mock 返回固定值分不出「插入新票」和
 * 「更新旧票」，也验不了平均分是从全部明细算出来的。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeMultiScoreTest {

    @Mock private ResumeMapper resumeMapper;
    @Mock private club.boyuan.official.persistence.mapper.RecruitmentCycleMapper recruitmentCycleMapper;
    @Mock private UserMapper userMapper;
    @Mock private club.boyuan.official.persistence.mapper.ResumeFieldValueMapper resumeFieldValueMapper;
    @Mock private club.boyuan.official.domain.resume.service.IResumeFieldDefinitionService fieldDefinitionService;
    @Mock private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    @Mock private ResumeScoreEntryMapper resumeScoreEntryMapper;

    @InjectMocks
    private ResumeServiceImpl service;

    private static final int RESUME_ID = 7;

    /** 从 wrapper 的 SQL 段里按列名取绑定值：位置取参在不同 MP 版本下顺序不稳 */
    private static Object paramOf(String sqlFragment, java.util.Map<String, Object> params, String column) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(column + "\\s*=\\s*#\\{[^}]*\\.(MPGENVAL\\d+)")
                .matcher(sqlFragment);
        if (!m.find()) {
            return null;
        }
        return params.get(m.group(1));
    }

    /** 内存里的明细假表 */
    private final List<ResumeScoreEntry> rows = new ArrayList<>();
    private final AtomicInteger idGen = new AtomicInteger(1);

    /** 聚合列的最新写回值 */
    private Integer writtenAverage;
    private Integer writtenScoredBy;

    @BeforeEach
    void setUp() {
        // 纯单测没有 Spring，MyBatis-Plus 不认识实体列名，手动注册
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ResumeScoreEntry.class);
        TableInfoHelper.initTableInfo(assistant, Resume.class);

        Resume resume = new Resume();
        resume.setResumeId(RESUME_ID);
        resume.setUserId(100);
        resume.setCycleId(6);
        resume.setStatus(2);
        when(resumeMapper.selectById(RESUME_ID)).thenReturn(resume);

        when(resumeScoreEntryMapper.selectOne(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<?> w = inv.getArgument(0);
            String seg = w.getSqlSegment();   // 参数是构建 SQL 段时才填充的，先触发一次
            Object scorer = paramOf(seg, w.getParamNameValuePairs(), "scorer_id");
            return rows.stream()
                    .filter(r -> Objects.equals(r.getScorerId(), scorer))
                    .findFirst().orElse(null);
        });
        when(resumeScoreEntryMapper.insert(any(ResumeScoreEntry.class))).thenAnswer(inv -> {
            ResumeScoreEntry e = inv.getArgument(0);
            e.setId(idGen.getAndIncrement());
            rows.add(e);
            return 1;
        });
        when(resumeScoreEntryMapper.updateById(any(ResumeScoreEntry.class))).thenAnswer(inv -> {
            ResumeScoreEntry e = inv.getArgument(0);
            rows.removeIf(r -> Objects.equals(r.getId(), e.getId()));
            rows.add(e);
            return 1;
        });
        when(resumeScoreEntryMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(rows));

        when(resumeMapper.update(any(), any())).thenAnswer(inv -> {
            LambdaUpdateWrapper<?> w = inv.getArgument(1);
            String set = w.getSqlSet();
            w.getSqlSegment();
            writtenAverage = (Integer) paramOf(set, w.getParamNameValuePairs(), "resume_score");
            writtenScoredBy = (Integer) paramOf(set, w.getParamNameValuePairs(), "scored_by");
            return 1;
        });

        lenient().when(userMapper.selectUsersByIds(anyList())).thenAnswer(inv -> {
            List<Integer> ids = inv.getArgument(0);
            return ids.stream().map(id -> {
                User u = new User();
                u.setUserId(id);
                u.setName("面试官" + id);
                return u;
            }).collect(Collectors.toList());
        });
    }

    @Test
    @DisplayName("两个人各打一票：平均分是两票的均值，明细两条都在")
    void twoScorersAverage() {
        service.updateResumeScore(RESUME_ID, 80, 1);
        ResumeDTO dto = service.updateResumeScore(RESUME_ID, 90, 2);

        assertEquals(85, dto.getResumeScore());
        assertEquals(85, writtenAverage);
        assertEquals(2, dto.getScoreEntries().size());
        List<String> names = dto.getScoreEntries().stream()
                .map(ResumeScoreEntryDTO::getScorerName).collect(Collectors.toList());
        assertEquals(List.of("面试官1", "面试官2"), names);
    }

    @Test
    @DisplayName("同一个人再打是改自己那票，不新增明细")
    void samePersonUpdatesOwnVote() {
        service.updateResumeScore(RESUME_ID, 80, 1);
        service.updateResumeScore(RESUME_ID, 90, 2);
        ResumeDTO dto = service.updateResumeScore(RESUME_ID, 60, 1);   // 1 号改分

        assertEquals(2, rows.size());
        assertEquals(75, dto.getResumeScore());   // (60+90)/2
        assertEquals(1, writtenScoredBy);          // 最近打分人
    }

    @Test
    @DisplayName("平均分四舍五入取整")
    void averageIsRounded() {
        service.updateResumeScore(RESUME_ID, 80, 1);
        service.updateResumeScore(RESUME_ID, 85, 2);
        ResumeDTO dto = service.updateResumeScore(RESUME_ID, 82, 3);
        // (80+85+82)/3 = 82.33 → 82
        assertEquals(82, dto.getResumeScore());
    }
}
