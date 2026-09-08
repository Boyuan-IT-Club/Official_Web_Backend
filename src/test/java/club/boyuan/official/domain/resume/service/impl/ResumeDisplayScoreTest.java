package club.boyuan.official.domain.resume.service.impl;

import club.boyuan.official.persistence.entity.Resume;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * resume_score 列是 NOT NULL DEFAULT 0：没打过分的简历分数也是 0。
 * 线上表现：列表页正常显示「未评分」（列表查询此前漏映射该列，拿到 null），
 * 一点开详情却显示「已打分：0」——详情走完整实体，0 被当成真实分数。
 * 「打没打过分」的事实在署名列上，展示分数必须以署名为准。
 */
class ResumeDisplayScoreTest {

    @Test
    @DisplayName("没有署名（没打过分）→ 展示 null，即使列值是默认 0")
    void unscoredResumeShowsNull() {
        Resume r = new Resume();
        r.setResumeScore(0);   // 数据库默认值
        assertNull(ResumeServiceImpl.displayScore(r));
    }

    @Test
    @DisplayName("有署名 → 展示真实分数，打 0 分也是 0 分")
    void scoredZeroIsARealZero() {
        Resume r = new Resume();
        r.setResumeScore(0);
        r.setScoredBy(2);
        r.setScoredAt(LocalDateTime.now());
        assertEquals(0, ResumeServiceImpl.displayScore(r));
    }

    @Test
    @DisplayName("正常打分原样展示")
    void scoredResumeShowsScore() {
        Resume r = new Resume();
        r.setResumeScore(88);
        r.setScoredBy(2);
        r.setScoredAt(LocalDateTime.now());
        assertEquals(88, ResumeServiceImpl.displayScore(r));
    }
}
