package club.boyuan.official.domain.resume;

import club.boyuan.official.domain.resume.service.ResumeSortOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历列表的排序子句。以前 mapper 里是 ORDER BY ${sortBy} ${sortOrder}，
 * 请求参数原样拼进 SQL——注入。现在只认白名单。
 */
class ResumeSortOrderTest {

    @Test
    @DisplayName("注入尝试一律落回默认排序，一个字符都进不了 SQL")
    void injectionFallsBackToDefault() {
        String[] attacks = {
                "(SELECT SLEEP(5))",
                "r.resume_id; DROP TABLE resume",
                "if(1=1,r.resume_id,r.user_id)",
                "submitted_at,(select 1 from user where username='admin' and password like 'a%')",
        };
        for (String a : attacks) {
            String sql = ResumeSortOrder.orderBy(a, "DESC");
            assertEquals("r.created_at DESC, r.resume_id DESC", sql, a);
        }
    }

    @Test
    @DisplayName("方向只认 ASC / DESC，其余一律当 DESC")
    void directionNormalized() {
        assertEquals("r.submitted_at ASC, r.resume_id DESC", ResumeSortOrder.orderBy("submitted_at", "asc"));
        String sql = ResumeSortOrder.orderBy("submitted_at", "ASC, (SELECT SLEEP(5))");
        assertEquals("r.submitted_at DESC, r.resume_id DESC", sql);
        assertFalse(sql.contains("SLEEP"));
    }

    @Test
    @DisplayName("白名单字段映射到带表前缀的列（结果集 JOIN 了 user，不带前缀会歧义）")
    void whitelistedColumns() {
        assertEquals("u.name ASC, r.resume_id DESC", ResumeSortOrder.orderBy("name", "ASC"));
        assertEquals("r.created_at DESC, r.resume_id DESC", ResumeSortOrder.orderBy("created_at", "DESC"));
    }

    @Test
    @DisplayName("按分数排：未打分的一律垫底，不和真打了 0 分的人混在一起")
    void scoreSortPutsUnscoredLast() {
        String desc = ResumeSortOrder.orderBy("resume_score", "DESC");
        String asc = ResumeSortOrder.orderBy("resume_score", "ASC");
        String unscoredLast = "(r.scored_by IS NULL AND r.scored_at IS NULL) ASC";
        assertTrue(desc.startsWith(unscoredLast), desc);
        assertTrue(asc.startsWith(unscoredLast), "升序时未打分的也在最后：" + asc);
        assertTrue(desc.contains("r.resume_score DESC"));
        assertTrue(asc.contains("r.resume_score ASC"));
    }

    @Test
    @DisplayName("永远带平局裁决 —— 同分的人很多，没有稳定次序翻页会重复或漏人")
    void alwaysHasTieBreaker() {
        for (String by : new String[] {"resume_score", "name", "submitted_at", null, "garbage"}) {
            assertTrue(ResumeSortOrder.orderBy(by, "DESC").endsWith(", r.resume_id DESC"), String.valueOf(by));
        }
    }

    @Test
    @DisplayName("空值走默认")
    void nullsUseDefault() {
        assertEquals("r.created_at DESC, r.resume_id DESC", ResumeSortOrder.orderBy(null, null));
        assertEquals("r.created_at DESC, r.resume_id DESC", ResumeSortOrder.orderBy("", ""));
    }

    @Test
    @DisplayName("盲评视角：我打过的在前并按分排，我没打过的垫底且组内不按分数排")
    void blindScoreSortHidesOthersRanking() {
        String desc = ResumeSortOrder.orderBy("resume_score", "DESC", 7);
        String mine = "EXISTS (SELECT 1 FROM resume_score_entry e WHERE e.resume_id = r.resume_id AND e.scorer_id = 7)";
        assertEquals("(CASE WHEN " + mine + " THEN 0 ELSE 1 END) ASC, (CASE WHEN " + mine
                + " THEN r.resume_score END) DESC, r.resume_id DESC", desc);
        assertTrue(ResumeSortOrder.orderBy("resume_score", "ASC", 7).contains("THEN r.resume_score END) ASC"));
    }

    @Test
    @DisplayName("盲评参数只影响按分数排序；为 null 时与原逻辑一致")
    void blindScorerOnlyAffectsScoreSort() {
        assertEquals(ResumeSortOrder.orderBy("name", "ASC"), ResumeSortOrder.orderBy("name", "ASC", 7));
        assertEquals(ResumeSortOrder.orderBy("resume_score", "DESC"), ResumeSortOrder.orderBy("resume_score", "DESC", null));
    }
}
