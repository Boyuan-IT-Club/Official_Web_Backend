package club.boyuan.official;

import club.boyuan.official.persistence.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户接口载荷契约。不需要 Spring 上下文,所以在没有 MySQL/Redis 的环境也能跑。
 *
 * 守两类曾经真实发生过的问题:
 *   1. 实体缺字段 / getter 命名不对 -> 接口 JSON 少键 -> 管理端「部门」列恒显示非社员。
 *      特别是 Boolean 的读方法若写成 isMember(),Jackson 会把属性名截成 member。
 *   2. SQL 明确 SELECT 了某列,但 UserResultMap 没映射,MyBatis 静默丢弃。
 *      这正是 dept / is_member 丢失的原因,而且不报任何错。
 */
class UserPayloadContractTest {

    private static final Path MAPPER_XML = Path.of("src/main/resources/mapper/UserMapper.xml");

    @Test
    @DisplayName("User 序列化后必须带 isMember 与 dept,且绝不带 password")
    void userJsonExposesMembershipAndHidesPassword() throws Exception {
        User u = new User();
        u.setUserId(1);
        u.setUsername("88888888888");
        u.setPassword("$2a$10$should-never-be-serialized");
        u.setDept("技术部");
        u.setIsMember(true);

        String json = new ObjectMapper().writeValueAsString(u);

        assertTrue(json.contains("\"isMember\""),
                "缺少 isMember 键。若 getter 写成 isMember() 而非 getIsMember(),"
                        + "Jackson 会输出 member,前端读 isMember 就恒为 undefined。实际: " + json);
        assertTrue(json.contains("\"dept\""), "缺少 dept 键。实际: " + json);
        assertFalse(json.contains("password"),
                "响应不得包含 password:GET /api/admin/users 会返回全部用户,"
                        + "带上哈希等于对每个能开该页的账号泄露全站密码。实际: " + json);
        assertFalse(json.contains("should-never-be-serialized"), "密码明文/哈希泄漏到了 JSON");
    }

    @Test
    @DisplayName("列表 SQL 选出的每一列都必须在 UserResultMap 里有映射")
    void everySelectedColumnIsMapped() throws IOException {
        String xml = Files.readString(MAPPER_XML);

        Set<String> mapped = new LinkedHashSet<>();
        Matcher m = Pattern.compile("column=\"([a-z_]+)\"").matcher(xml);
        while (m.find()) {
            mapped.add(m.group(1));
        }

        String select = extractStatement(xml, "findByRoleAndDeptAndStatus");
        String columnList = select.substring(select.indexOf("SELECT") + "SELECT".length(),
                select.indexOf("FROM"));

        Set<String> unmapped = new LinkedHashSet<>();
        for (String raw : columnList.split(",")) {
            String col = raw.trim();
            if (col.isEmpty() || col.equals("*") || col.endsWith(".*")) {
                continue;
            }
            if (!mapped.contains(col)) {
                unmapped.add(col);
            }
        }

        assertTrue(unmapped.isEmpty(),
                "这些列被 SQL 选出但 UserResultMap 没有映射,MyBatis 会静默丢弃,"
                        + "接口 JSON 里就不会有它们: " + unmapped);
    }

    /** 取出指定 id 的语句体,避免把整个 XML 当成一条 SQL 解析。 */
    private static String extractStatement(String xml, String id) {
        int start = xml.indexOf("id=\"" + id + "\"");
        assertTrue(start > 0, "UserMapper.xml 里找不到语句 " + id);
        int end = xml.indexOf("</select>", start);
        assertTrue(end > start, id + " 语句没有结束标签");
        return xml.substring(start, end);
    }
}
