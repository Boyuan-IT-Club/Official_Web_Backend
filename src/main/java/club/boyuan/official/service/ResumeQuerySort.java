package club.boyuan.official.service;

import org.springframework.util.StringUtils;

/**
 * 简历列表排序字段白名单，防止 SQL 注入。
 */
public final class ResumeQuerySort {

    private ResumeQuerySort() {
    }

    public static String resolveSortBy(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return "r.created_at";
        }
        return switch (sortBy.trim()) {
            case "created_at", "r.created_at" -> "r.created_at";
            case "submitted_at", "r.submitted_at" -> "r.submitted_at";
            case "resume_score", "r.resume_score" -> "r.resume_score";
            case "status", "r.status" -> "r.status";
            case "updated_at", "r.updated_at" -> "r.updated_at";
            default -> "r.created_at";
        };
    }

    public static String resolveSortOrder(String sortOrder) {
        if (!StringUtils.hasText(sortOrder)) {
            return "DESC";
        }
        return "ASC".equalsIgnoreCase(sortOrder.trim()) ? "ASC" : "DESC";
    }
}
