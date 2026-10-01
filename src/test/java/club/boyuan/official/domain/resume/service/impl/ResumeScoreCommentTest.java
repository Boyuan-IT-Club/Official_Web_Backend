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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 打分评语：挂在「我这一票」上。null = 不改动原评语（打分舞台只改分），
 * 空白 = 清空，其余去首尾空白保存，≤ 500 字。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeScoreCommentTest {

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

    private ResumeScoreEntry rowOf(int scorer) {
        return rows.stream().filter(r -> r.getScorerId() == scorer).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("打分时附评语：去首尾空白保存，明细里带出评语")
    void saveScoreWithComment() {
        ResumeDTO dto = service.updateResumeScore(RESUME_ID, 80, "  项目经历扎实，表达清楚  ", 1);
        assertEquals("项目经历扎实，表达清楚", rowOf(1).getComment());
        assertEquals("项目经历扎实，表达清楚", dto.getScoreEntries().get(0).getComment());
    }

    @Test
    @DisplayName("只改分不带评语（打分舞台）：原评语保留")
    void scoreOnlyKeepsComment() {
        service.updateResumeScore(RESUME_ID, 80, "写得认真", 1);
        ResumeDTO dto = service.updateResumeScore(RESUME_ID, 90, 1);
        assertEquals(90, rowOf(1).getScore());
        assertEquals("写得认真", rowOf(1).getComment());
        assertEquals("写得认真", dto.getScoreEntries().get(0).getComment());
    }

    @Test
    @DisplayName("传空串或纯空白：清空评语")
    void blankClearsComment() {
        service.updateResumeScore(RESUME_ID, 80, "写得认真", 1);
        service.updateResumeScore(RESUME_ID, 80, "", 1);
        assertNull(rowOf(1).getComment());
        service.updateResumeScore(RESUME_ID, 80, "再写一次", 1);
        service.updateResumeScore(RESUME_ID, 80, "   ", 1);
        assertNull(rowOf(1).getComment());
    }

    @Test
    @DisplayName("首次打分带空评语：落库为 null 而不是空串")
    void firstScoreWithEmptyComment() {
        service.updateResumeScore(RESUME_ID, 70, "", 1);
        assertNull(rowOf(1).getComment());
    }

    @Test
    @DisplayName("评语上限 500 字：恰好 500 可以，501 拒绝且不落库")
    void commentLengthLimit() {
        String ok = "好".repeat(500);
        service.updateResumeScore(RESUME_ID, 70, ok, 1);
        assertEquals(ok, rowOf(1).getComment());
        assertThrows(club.boyuan.official.common.exception.BusinessException.class,
                () -> service.updateResumeScore(RESUME_ID, 70, "好".repeat(501), 2));
        assertEquals(1, rows.size());
    }

    @Test
    @DisplayName("各人的评语互不影响")
    void commentsArePerScorer() {
        service.updateResumeScore(RESUME_ID, 80, "甲的看法", 1);
        ResumeDTO dto = service.updateResumeScore(RESUME_ID, 60, "乙的看法", 2);
        assertEquals("甲的看法", rowOf(1).getComment());
        assertEquals(List.of("甲的看法", "乙的看法"), dto.getScoreEntries().stream()
                .map(ResumeScoreEntryDTO::getComment).collect(Collectors.toList()));
    }
}
