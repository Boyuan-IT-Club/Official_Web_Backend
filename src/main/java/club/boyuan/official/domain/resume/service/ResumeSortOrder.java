package club.boyuan.official.domain.resume.service;

import java.util.Locale;
import java.util.Map;

/**
 * 简历列表的 ORDER BY 子句，完全由服务端按白名单拼出来。
 *
 * 以前 mapper 里是 ORDER BY ${sortBy} ${sortOrder}，把请求参数原样拼进 SQL
 * （${} 不转义，#{} 才参数化）——持 resume:view 的人可以借排序参数注入任意 SQL。
 * 现在请求里的 sortBy 只用来查表，查不到就用默认排序；方向只认 ASC / DESC。
 * 拼进 SQL 的每一个字符都来自本类的常量。
 */
public final class ResumeSortOrder {

    /** 结果集是 SELECT r.* FROM resume r LEFT JOIN user u，列要带表前缀，否则 created_at 这类会歧义 */
    private static final Map<String, String> COLUMNS = Map.of(
            "submitted_at", "r.submitted_at",
            "created_at", "r.created_at",
            "name", "u.name",
            "resume_score", "r.resume_score");

    private static final String DEFAULT = "r.created_at DESC";

    /*
     * 平局裁决：同分的人很多（打分是整数），没有稳定次序时 MySQL 每次翻页
     * 返回的顺序可能不同，第 2 页会重复第 1 页的人、或漏掉一些人。
     */
    private static final String TIE_BREAKER = ", r.resume_id DESC";

    private ResumeSortOrder() {
    }

    public static String orderBy(String sortBy, String sortOrder) {
        String column = sortBy == null ? null : COLUMNS.get(sortBy.trim().toLowerCase(Locale.ROOT));
        if (column == null) {
            return DEFAULT + TIE_BREAKER;
        }
        String direction = "ASC".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim()) ? "ASC" : "DESC";

        if ("r.resume_score".equals(column)) {
            /*
             * 按分数排时，未打分的一律垫底 —— 不论升序降序。
             * resume_score 列 NOT NULL DEFAULT 0，没打过分的也是 0，
             * 和真打了 0 分（= 未通过初筛）混在一起就分不清了。
             * 「有没有打过分」看署名与时间（与 displayScore 同一口径）。
             */
            return "(r.scored_by IS NULL AND r.scored_at IS NULL) ASC, r.resume_score " + direction + TIE_BREAKER;
        }
        return column + " " + direction + TIE_BREAKER;
    }
}
