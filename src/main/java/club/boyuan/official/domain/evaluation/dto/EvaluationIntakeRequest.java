package club.boyuan.official.domain.evaluation.dto;

import lombok.Data;

/**
 * Actions 推送载荷(契约见嵌入模板仓的 issue #6):
 * report = base64(autograding_report.json 原始字节);github_username 来自 GitHub context。
 */
@Data
public class EvaluationIntakeRequest {

    /** base64 编码的加密报告单原始字节 */
    private String report;

    /** GitHub 登录名(来自 github.actor),可靠身份键 */
    private String githubUsername;

    /** 候选人生成仓 URL(如 github.com/<login>/interview-autograding-template) */
    private String repository;

    /** 触发推送的 commit sha */
    private String commitSha;
}