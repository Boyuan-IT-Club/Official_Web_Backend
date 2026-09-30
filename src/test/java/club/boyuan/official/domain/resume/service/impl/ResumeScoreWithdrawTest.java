package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.domain.resume.dto.ResumeDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 撤销打分（误触时用）。只删自己那一票；平均分、署名、初筛结论随之重算。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeScoreWithdrawTest {

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

    private final List<ResumeScoreEntry> rows = new ArrayList<>();
    private final AtomicInteger idGen = new AtomicInteger(1);
    private Resume resume;

    /** 简历表上被显式写过的列（含写成 null 的），按写入顺序累计：后写的覆盖先写的 */
    private final Map<String, Object> written = new HashMap<>();

    private static final Pattern SET_COL = Pattern.compile("(\\w+)\\s*=\\s*#\\{[^}]*\\.(MPGENVAL\\d+)");

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ResumeScoreEntry.class);
        TableInfoHelper.initTableInfo(assistant, Resume.class);

        resume = new Resume();
        resume.setResumeId(RESUME_ID);
        resume.setUserId(100);
        resume.setCycleId(14);
        resume.setStatus(ResumeServiceImpl.STATUS_SUBMITTED);
        when(resumeMapper.selectById(RESUME_ID)).thenReturn(resume);

        when(resumeScoreEntryMapper.selectOne(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<?> w = inv.getArgument(0);
            String seg = w.getSqlSegment();
            Matcher m = Pattern.compile("scorer_id\\s*=\\s*#\\{[^}]*\\.(MPGENVAL\\d+)").matcher(seg);
            Object scorer = m.find() ? w.getParamNameValuePairs().get(m.group(1)) : null;
            return rows.stream().filter(r -> Objects.equals(r.getScorerId(), scorer)).findFirst().orElse(null);
        });
        when(resumeScoreEntryMapper.deleteById(any(Integer.class))).thenAnswer(inv -> {
            Integer id = inv.getArgument(0);
            return rows.removeIf(r -> Objects.equals(r.getId(), id)) ? 1 : 0;
        });
        when(resumeScoreEntryMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(rows));

        when(resumeMapper.update(any(), any())).thenAnswer(inv -> {
            LambdaUpdateWrapper<?> w = inv.getArgument(1);
            String set = w.getSqlSet();
            w.getSqlSegment();
            Matcher m = SET_COL.matcher(set);
            while (m.find()) {
                written.put(m.group(1), w.getParamNameValuePairs().get(m.group(2)));
            }
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

    private void vote(int scorer, int score, int minutesAgo) {
        LocalDateTime t = LocalDateTime.now().minusMinutes(minutesAgo);
        rows.add(new ResumeScoreEntry().setId(idGen.getAndIncrement()).setResumeId(RESUME_ID)
                .setScorerId(scorer).setScore(score).setCreatedAt(t).setUpdatedAt(t));
    }

    @Test
    @DisplayName("只删自己那一票，别人的分不动；平均分按剩下的重算")
    void onlyMyVoteRemoved() {
        vote(1, 80, 30);
        vote(2, 90, 20);
        vote(3, 60, 10);

        ResumeDTO dto = service.withdrawResumeScore(RESUME_ID, 3);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().noneMatch(r -> r.getScorerId() == 3));
        assertEquals(85, dto.getResumeScore());          // (80+90)/2
        assertEquals(85, written.get("resume_score"));
        assertEquals(2, dto.getScoreEntries().size());
    }

    @Test
    @DisplayName("署名换成剩下的人里最近打分的那一位")
    void scoredByFallsBackToLatestRemaining() {
        vote(1, 80, 30);
        vote(2, 90, 5);     // 剩下的人里最近的
        vote(3, 70, 1);     // 撤销的是最近那位

        service.withdrawResumeScore(RESUME_ID, 3);

        assertEquals(2, written.get("scored_by"));
    }

    @Test
    @DisplayName("撤掉最后一票：回到「未打分」—— 署名与时间清空，列表里显示「未评分」")
    void lastVoteBackToUnscored() {
        vote(1, 80, 10);

        ResumeDTO dto = service.withdrawResumeScore(RESUME_ID, 1);

        assertNull(dto.getResumeScore(), "对外展示为未打分");
        assertTrue(dto.getScoreEntries().isEmpty());
        assertTrue(written.containsKey("scored_by"));
        assertNull(written.get("scored_by"));
        assertTrue(written.containsKey("scored_at"));
        assertNull(written.get("scored_at"));
        assertEquals(0, written.get("resume_score"));    // 列 NOT NULL，只能归 0
    }

    @Test
    @DisplayName("误触打了 0 分（自动判为未通过）→ 撤销后收回「未通过」")
    void withdrawZeroRevertsAutoRejection() {
        vote(1, 0, 10);
        resume.setStatus(ResumeServiceImpl.STATUS_SCREEN_REJECTED);

        ResumeDTO dto = service.withdrawResumeScore(RESUME_ID, 1);

        assertEquals(ResumeServiceImpl.STATUS_SUBMITTED, dto.getStatus());
        assertEquals(ResumeServiceImpl.STATUS_SUBMITTED, written.get("status"));
    }

    @Test
    @DisplayName("手动标的「未通过」：撤销一个 80 分不能把人放出来")
    void manualRejectionSurvivesWithdrawOfNonZero() {
        vote(1, 80, 10);
        resume.setStatus(ResumeServiceImpl.STATUS_SCREEN_REJECTED);

        ResumeDTO dto = service.withdrawResumeScore(RESUME_ID, 1);

        assertEquals(ResumeServiceImpl.STATUS_SCREEN_REJECTED, dto.getStatus());
        assertFalse(written.containsKey("status"));
    }

    @Test
    @DisplayName("撤销后剩下的平均分是 0：判为未通过，与改分时同一条规则")
    void remainingAverageZeroRejects() {
        vote(1, 0, 20);
        vote(2, 90, 10);

        ResumeDTO dto = service.withdrawResumeScore(RESUME_ID, 2);

        assertEquals(0, dto.getResumeScore());
        assertEquals(ResumeServiceImpl.STATUS_SCREEN_REJECTED, dto.getStatus());
    }

    @Test
    @DisplayName("没打过分的人点撤销：明确报错，不去动别人的分")
    void withdrawWithoutVoteFails() {
        vote(1, 80, 10);

        BusinessException e = assertThrows(BusinessException.class, () -> service.withdrawResumeScore(RESUME_ID, 9));

        assertTrue(e.getMessage().contains("还没有给这份简历打过分"), e.getMessage());
        assertEquals(1, rows.size());
        assertTrue(written.isEmpty());
    }

    @Test
    @DisplayName("初筛结论规则表")
    void statusRules() {
        int rejected = ResumeServiceImpl.STATUS_SCREEN_REJECTED;
        int submitted = ResumeServiceImpl.STATUS_SUBMITTED;
        int passed = ResumeServiceImpl.STATUS_SCREEN_PASSED;
        // 没人打分了
        assertEquals(submitted, ResumeServiceImpl.statusAfterWithdraw(null, rejected, true));
        assertNull(ResumeServiceImpl.statusAfterWithdraw(null, rejected, false));
        assertNull(ResumeServiceImpl.statusAfterWithdraw(null, passed, true));
        // 还有人打分
        assertEquals(rejected, ResumeServiceImpl.statusAfterWithdraw(0, submitted, false));
        assertEquals(submitted, ResumeServiceImpl.statusAfterWithdraw(75, rejected, false));
        assertNull(ResumeServiceImpl.statusAfterWithdraw(75, passed, false));   // 手动「通过」不动
    }
}
