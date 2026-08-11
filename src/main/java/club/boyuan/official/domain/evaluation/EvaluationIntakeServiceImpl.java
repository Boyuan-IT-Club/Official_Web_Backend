package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.common.utils.GitHubAccountUtil;
import club.boyuan.official.domain.evaluation.dto.EvaluationIntakeRequest;
import club.boyuan.official.domain.evaluation.dto.Report;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class EvaluationIntakeServiceImpl implements IEvaluationIntakeService {

    private final EvaluationSubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final RecruitmentCycleMapper cycleMapper;

    @Override
    @Transactional
    public EvaluationSubmission ingest(EvaluationIntakeRequest request) {
        if (request == null || request.getReport() == null || request.getReport().isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "report 不能为空");
        }
        if (request.getGithubUsername() == null || request.getGithubUsername().isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "github_username 不能为空");
        }

        byte[] reportBytes;
        try {
            reportBytes = Base64.getDecoder().decode(request.getReport());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_REPORT, "report 不是合法 base64");
        }

        // 幂等键:sha256(报告单原始字节)
        String sha = sha256Hex(reportBytes);
        EvaluationSubmission existing = submissionMapper.selectOne(
                new LambdaQueryWrapper<EvaluationSubmission>().eq(EvaluationSubmission::getReportSha, sha));
        if (existing != null) {
            log.debug("评测提交已存在,幂等返回: report_sha={}", sha);
            return existing;
        }

        String envelopeJson = new String(reportBytes, StandardCharsets.UTF_8);
        String plainJson = ReportDecryptor.decrypt(envelopeJson);
        Report report = ReportParser.parse(plainJson);

        // 身份归属:github_username 归一化后匹配 user.github
        String normalizedGh = GitHubAccountUtil.normalize(request.getGithubUsername());
        Integer userId = null;
        if (normalizedGh != null) {
            User matched = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getGithub, normalizedGh)
                    .last("limit 1"));
            if (matched != null) {
                userId = matched.getUserId();
            }
        }

        // 周期归属:当前活跃周期
        Integer cycleId = null;
        List<RecruitmentCycle> activeCycles = cycleMapper.findByIsActive(1);
        if (activeCycles != null && !activeCycles.isEmpty()) {
            cycleId = activeCycles.get(0).getCycleId();
        }

        EvaluationSubmission submission = new EvaluationSubmission();
        submission.setGithubUsername(normalizedGh != null ? normalizedGh : request.getGithubUsername());
        submission.setUserId(userId);
        submission.setCycleId(cycleId);
        submission.setReportSha(sha);
        submission.setAuthor(report.getAuthor());
        submission.setEvaluatedAt(parseTimestamp(report.getTimestamp()));
        submission.setTotalScore(report.getTotalScore());
        submission.setMaxScore(report.computeMaxScore());
        submission.setTask1Score(report.taskScore("task1"));
        submission.setTask2Score(report.taskScore("task2"));
        submission.setTask3Score(report.taskScore("task3"));
        submission.setTask4Score(report.taskScore("task4"));
        submission.setReportJson(plainJson);
        submission.setRepository(request.getRepository());
        submission.setCommitSha(request.getCommitSha());

        submissionMapper.insert(submission);
        log.info("评测提交入库: report_sha={}, github={}, userId={}, cycleId={}, total={}",
                sha, submission.getGithubUsername(), userId, cycleId, report.getTotalScore());
        return submission;
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        String s = raw.trim();
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
            log.warn("无法解析报告单 timestamp: {}", raw);
            return LocalDateTime.now();
        }
    }
}