package club.boyuan.official.domain.evaluation;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.evaluation.dto.TrendPoint;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class EvaluationUserServiceImpl implements IEvaluationUserService {

    private final EvaluationSubmissionMapper submissionMapper;

    @Override
    public PageResultDTO<EvaluationSubmission> page(Integer userId, int page, int size) {
        Page<EvaluationSubmission> p = submissionMapper.selectPage(new Page<>(page + 1, size),
                new LambdaQueryWrapper<EvaluationSubmission>()
                        .eq(EvaluationSubmission::getUserId, userId)
                        .orderByDesc(EvaluationSubmission::getEvaluatedAt)
                        .orderByDesc(EvaluationSubmission::getId));
        long total = p.getTotal();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResultDTO<>(p.getRecords(), total, totalPages, page, size,
                p.getCurrent() <= 1, page >= totalPages - 1);
    }

    @Override
    public EvaluationSubmission latest(Integer userId) {
        return submissionMapper.selectOne(new LambdaQueryWrapper<EvaluationSubmission>()
                .eq(EvaluationSubmission::getUserId, userId)
                .orderByDesc(EvaluationSubmission::getEvaluatedAt)
                .orderByDesc(EvaluationSubmission::getId)
                .last("limit 1"));
    }

    @Override
    public List<TrendPoint> trend(Integer userId) {
        return submissionMapper.selectList(new LambdaQueryWrapper<EvaluationSubmission>()
                        .eq(EvaluationSubmission::getUserId, userId)
                        .orderByAsc(EvaluationSubmission::getEvaluatedAt)
                        .orderByAsc(EvaluationSubmission::getId))
                .stream()
                .map(s -> {
                    TrendPoint tp = new TrendPoint();
                    tp.setEvaluatedAt(s.getEvaluatedAt());
                    tp.setTotalScore(s.getTotalScore());
                    return tp;
                })
                .collect(Collectors.toList());
    }
}