package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.common.utils.GitHubAccountUtil;
import club.boyuan.official.domain.evaluation.dto.CandidateRow;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class EvaluationAdminServiceImpl implements IEvaluationAdminService {

    private static final String SORT_LATEST = "latest";
    private static final String SORT_MAX = "maxScore";
    private static final String SORT_COUNT = "submissionCount";

    private final EvaluationSubmissionMapper submissionMapper;
    private final UserMapper userMapper;

    @Override
    public PageResultDTO<CandidateRow> candidates(Integer cycleId, Integer deptId,
                                                  Integer minScore, Integer maxScore,
                                                  String claimed, String sortBy, int page, int size) {
        String c = claimed == null ? "all" : claimed;
        String sort = SORT_MAX.equals(sortBy) || SORT_COUNT.equals(sortBy) ? sortBy : SORT_LATEST;
        int offset = page * size;

        List<CandidateRow> rows = submissionMapper.selectCandidates(
                cycleId, deptId, minScore, maxScore, c, sort, offset, size);
        long total = submissionMapper.countCandidates(cycleId, deptId, minScore, maxScore, c);
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResultDTO<>(rows, total, totalPages, page, size,
                page == 0, page >= totalPages - 1);
    }

    @Override
    public List<EvaluationSubmission> submissions(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "key 不能为空");
        }
        // 数字 → 按用户 id;否则按 github_username(归一化)
        try {
            Integer userId = Integer.valueOf(key);
            return submissionMapper.selectByUserId(userId);
        } catch (NumberFormatException ignored) {
            String gh = GitHubAccountUtil.normalize(key);
            if (gh == null) {
                throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "key 无法识别");
            }
            return submissionMapper.selectByGithub(gh);
        }
    }

    @Override
    public EvaluationSubmission detail(Long id) {
        EvaluationSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(BusinessExceptionEnum.EVALUATION_SUBMISSION_NOT_FOUND);
        }
        return submission;
    }

    @Override
    @Transactional
    public EvaluationSubmission claim(Long id, Integer userId) {
        if (userId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "userId 不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        EvaluationSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(BusinessExceptionEnum.EVALUATION_SUBMISSION_NOT_FOUND);
        }
        submission.setUserId(userId);
        submissionMapper.updateById(submission);
        log.info("评测提交 {}(github={}) 已认领到用户 {}", id, submission.getGithubUsername(), userId);
        return submission;
    }
}