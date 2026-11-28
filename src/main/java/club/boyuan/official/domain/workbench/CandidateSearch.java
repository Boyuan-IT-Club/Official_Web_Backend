package club.boyuan.official.domain.workbench;

/** 候选搜索的入参。空字符串表示不查全站。 */
public final class CandidateSearch {
    public record Query(String text, Integer exactId, int page, int size, boolean blank) {}

    private CandidateSearch() {}

    public static Query parse(String raw, int page, int size) {
        String text = raw == null ? "" : raw.trim();
        if (text.codePointCount(0, text.length()) > 50) {
            throw new WorkbenchMembershipException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "搜索词过长");
        }
        int resolvedPage = page < 1 ? 1 : page;
        int resolvedSize = size < 1 ? 20 : Math.min(size, 50);
        Integer exact = text.matches("[1-9]\\d{0,8}") ? Integer.valueOf(text) : null;
        return new Query(text, exact, resolvedPage, resolvedSize, text.isEmpty());
    }

    public static String like(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
