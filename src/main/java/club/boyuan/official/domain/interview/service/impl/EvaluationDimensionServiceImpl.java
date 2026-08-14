package club.boyuan.official.domain.interview.service.impl;

import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.domain.interview.dto.EvaluationDimensionDTO;
import club.boyuan.official.domain.interview.dto.SaveEvaluationDimensionsRequestDTO;
import club.boyuan.official.domain.interview.service.IEvaluationDimensionService;
import club.boyuan.official.persistence.entity.EvaluationDimension;
import club.boyuan.official.persistence.entity.RecruitmentCycle;
import club.boyuan.official.persistence.mapper.EvaluationDimensionMapper;
import club.boyuan.official.persistence.mapper.RecruitmentCycleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试评分维度模板实现。
 *
 * @author dhy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationDimensionServiceImpl implements IEvaluationDimensionService {

    private final EvaluationDimensionMapper evaluationDimensionMapper;
    private final RecruitmentCycleMapper recruitmentCycleMapper;

    @Override
    public List<EvaluationDimensionDTO> listByCycle(Integer cycleId) {
        return listEntitiesByCycle(cycleId).stream()
                .map(EvaluationDimensionServiceImpl::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EvaluationDimensionDTO> replaceDimensions(Integer cycleId,
                                                          SaveEvaluationDimensionsRequestDTO request) {
        requireCycle(cycleId);

        List<SaveEvaluationDimensionsRequestDTO.DimensionItem> items = request.getDimensions();
        Set<String> names = new HashSet<>();
        for (SaveEvaluationDimensionsRequestDTO.DimensionItem item : items) {
            if (!names.add(item.getName().trim())) {
                throw new BusinessException(BusinessExceptionEnum.EVALUATION_DIMENSION_DUPLICATE,
                        "评分维度名称重复：" + item.getName().trim());
            }
        }

        List<EvaluationDimension> existing = listEntitiesByCycle(cycleId);
        Set<Integer> existingIds = existing.stream()
                .map(EvaluationDimension::getDimensionId)
                .collect(Collectors.toSet());
        Set<Integer> keptIds = items.stream()
                .map(SaveEvaluationDimensionsRequestDTO.DimensionItem::getDimensionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // 请求里带了本周期不存在的维度ID，说明前端拿的是过期数据，直接拒绝而不是静默新增
        for (Integer keptId : keptIds) {
            if (!existingIds.contains(keptId)) {
                throw new BusinessException(BusinessExceptionEnum.EVALUATION_DIMENSION_NOT_FOUND,
                        "评分维度不存在或不属于该周期：" + keptId);
            }
        }

        List<Integer> removedIds = existingIds.stream()
                .filter(id -> !keptIds.contains(id))
                .collect(Collectors.toList());
        if (!removedIds.isEmpty()) {
            evaluationDimensionMapper.deleteBatchIds(removedIds);
            log.info("周期 {} 移除评分维度 {}", cycleId, removedIds);
        }

        int order = 0;
        for (SaveEvaluationDimensionsRequestDTO.DimensionItem item : items) {
            order++;
            EvaluationDimension entity = new EvaluationDimension()
                    .setCycleId(cycleId)
                    .setName(item.getName().trim())
                    .setMaxScore(item.getMaxScore())
                    .setWeight(item.getWeight())
                    .setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order);
            if (item.getDimensionId() != null) {
                entity.setDimensionId(item.getDimensionId());
                evaluationDimensionMapper.updateById(entity);
            } else {
                evaluationDimensionMapper.insert(entity);
            }
        }

        return listByCycle(cycleId);
    }

    private List<EvaluationDimension> listEntitiesByCycle(Integer cycleId) {
        return evaluationDimensionMapper.selectList(new LambdaQueryWrapper<EvaluationDimension>()
                .eq(EvaluationDimension::getCycleId, cycleId)
                .orderByAsc(EvaluationDimension::getSortOrder)
                .orderByAsc(EvaluationDimension::getDimensionId));
    }

    private void requireCycle(Integer cycleId) {
        RecruitmentCycle cycle = recruitmentCycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BusinessException(BusinessExceptionEnum.RECRUITMENT_CYCLE_NOT_FOUND);
        }
    }

    static EvaluationDimensionDTO toDTO(EvaluationDimension entity) {
        EvaluationDimensionDTO dto = new EvaluationDimensionDTO();
        dto.setDimensionId(entity.getDimensionId());
        dto.setName(entity.getName());
        dto.setMaxScore(entity.getMaxScore());
        dto.setWeight(entity.getWeight());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }

    /**
     * 新建周期时的默认维度，避免开表时一列都没有。
     */
    static List<EvaluationDimension> defaultDimensions(Integer cycleId) {
        List<EvaluationDimension> defaults = new ArrayList<>();
        String[] names = {"技术能力", "沟通表达", "学习潜力", "意愿匹配"};
        for (int i = 0; i < names.length; i++) {
            defaults.add(new EvaluationDimension()
                    .setCycleId(cycleId)
                    .setName(names[i])
                    .setMaxScore(10)
                    .setWeight(java.math.BigDecimal.ONE)
                    .setSortOrder(i + 1));
        }
        return defaults;
    }
}
