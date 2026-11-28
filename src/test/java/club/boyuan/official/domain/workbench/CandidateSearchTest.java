package club.boyuan.official.domain.workbench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CandidateSearchTest {
    @Test
    void blankQueryDoesNotSearchTheWholeSite() {
        var query = CandidateSearch.parse("  ", 1, 20);
        assertTrue(query.blank());
        assertEquals("", query.text());
    }

    @Test
    void keepsASingleCharacterAndAnExactAccountId() {
        var name = CandidateSearch.parse(" 陈 ", 0, 0);
        assertEquals("陈", name.text());
        assertNull(name.exactId());
        assertEquals(1, name.page());
        assertEquals(20, name.size());

        var id = CandidateSearch.parse("102", 2, 80);
        assertEquals(102, id.exactId());
        assertEquals(2, id.page());
        assertEquals(50, id.size());
        assertEquals("%1\\%2\\_3%", CandidateSearch.like("1%2_3"));
    }

    @Test
    void rejectsAnOverlongQuery() {
        var error = assertThrows(WorkbenchMembershipException.class, () -> CandidateSearch.parse("啊".repeat(51), 1, 20));
        assertEquals(HttpStatus.BAD_REQUEST, error.status());
    }
}
