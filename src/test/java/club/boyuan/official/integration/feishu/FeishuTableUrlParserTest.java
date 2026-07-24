package club.boyuan.official.integration.feishu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeishuTableUrlParserTest {

    @Test
    void parse_standardBaseUrl() {
        FeishuTableUrlParser.ParsedTable parsed = FeishuTableUrlParser.parse(
                "https://example.feishu.cn/base/BascnABCD1234?table=tblXYZ5678&view=vewAAA");
        assertEquals("BascnABCD1234", parsed.appToken());
        assertEquals("tblXYZ5678", parsed.tableId());
    }
}
