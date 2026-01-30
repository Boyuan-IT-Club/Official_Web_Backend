package club.boyuan.official.service.impl;

import club.boyuan.official.entity.Activity;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.ActivityMapper;
import club.boyuan.official.service.IActivityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Activity的业务层实现
 */
@Service
@AllArgsConstructor
@Transactional
public class ActivityServiceImpl implements IActivityService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityServiceImpl.class);

    private final ActivityMapper activityMapper;

    @Override
    public Activity createActivity(Activity activity) throws BusinessException {
        logger.info("创建活动，活动标题: {}", activity.getTitle());
        try {
            // 参数验证
            if (activity.getTitle() == null || activity.getTitle().trim().isEmpty()) {
                logger.warn("创建活动失败，活动标题为空");
                throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "活动标题不能为空");
            }

            // 设置创建时间
            activity.setCreatedAt(LocalDateTime.now());
            activity.setUpdatedAt(LocalDateTime.now());

            // 插入数据
            activityMapper.insert(activity);

            logger.info("成功创建活动，活动ID: {}", activity.getActivityId());
            return activity;
        } catch (Exception e) {
            logger.error("创建活动失败，活动标题: {}", activity.getTitle(), e);
            throw new BusinessException(BusinessExceptionEnum.ACTIVITY_CREATE_FAILED, "创建活动失败");
        }
    }

    @Override
    public Activity updateActivity(Activity activity) throws BusinessException {
        logger.info("更新活动，活动ID: {}", activity.getActivityId());
        try {
            // 检查活动是否存在
            Activity existingActivity = activityMapper.selectById(activity.getActivityId());
            if (existingActivity == null) {
                logger.warn("更新活动失败，活动不存在，活动ID: {}", activity.getActivityId());
                throw new BusinessException(BusinessExceptionEnum.ACTIVITY_NOT_FOUND, "活动不存在");
            }

            // 设置更新时间
            activity.setUpdatedAt(LocalDateTime.now());

            // 更新数据
            activityMapper.updateById(activity);

            logger.info("成功更新活动，活动ID: {}", activity.getActivityId());
            return activity;
        } catch (BusinessException e) {
            throw e; // 重新抛出业务异常
        } catch (Exception e) {
            logger.error("更新活动失败，活动ID: {}", activity.getActivityId(), e);
            throw new BusinessException(BusinessExceptionEnum.ACTIVITY_UPDATE_FAILED, "更新活动失败");
        }
    }

    @Override
    public boolean deleteActivity(Integer activityId) throws BusinessException {
        logger.info("删除活动，活动ID: {}", activityId);
        try {
            // 检查活动是否存在
            Activity existingActivity = activityMapper.selectById(activityId);
            if (existingActivity == null) {
                logger.warn("删除活动失败，活动不存在，活动ID: {}", activityId);
                throw new BusinessException(BusinessExceptionEnum.ACTIVITY_NOT_FOUND, "活动不存在");
            }

            // 删除数据
            int deletedRows = activityMapper.deleteById(activityId);

            boolean result = deletedRows > 0;
            logger.info("删除活动完成，活动ID: {}，删除结果: {}", activityId, result);
            return result;
        } catch (BusinessException e) {
            throw e; // 重新抛出业务异常
        } catch (Exception e) {
            logger.error("删除活动失败，活动ID: {}", activityId, e);
            throw new BusinessException(BusinessExceptionEnum.ACTIVITY_DELETE_FAILED, "删除活动失败");
        }
    }

    @Override
    public Activity getActivityById(Integer activityId) throws BusinessException {
        logger.debug("根据ID获取活动，活动ID: {}", activityId);
        try {
            Activity activity = activityMapper.selectById(activityId);
            if (activity == null) {
                logger.warn("活动不存在，活动ID: {}", activityId);
                throw new BusinessException(BusinessExceptionEnum.ACTIVITY_NOT_FOUND, "活动不存在");
            }
            logger.debug("获取活动成功，活动ID: {}", activityId);
            return activity;
        } catch (BusinessException e) {
            throw e; // 重新抛出业务异常
        } catch (Exception e) {
            logger.error("获取活动失败，活动ID: {}", activityId, e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED, "获取活动失败");
        }
    }

    @Override
    public List<Activity> getAllActivities() throws BusinessException {
        logger.debug("获取所有活动");
        try {
            List<Activity> activities = activityMapper.selectList(null);
            logger.info("获取所有活动完成，活动数量: {}", activities.size());
            return activities;
        } catch (Exception e) {
            logger.error("获取所有活动失败", e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED, "获取活动列表失败");
        }
    }

    @Override
    public List<Activity> getActivitiesByCategory(String category) throws BusinessException {
        logger.debug("根据分类获取活动，分类: {}", category);
        try {
            // 使用 MyBatis-Plus 的条件查询
            LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Activity::getCategory, category);
            List<Activity> activities = activityMapper.selectList(wrapper);
            logger.info("根据分类获取活动完成，分类: {}，活动数量: {}", category, activities.size());
            return activities;
        } catch (Exception e) {
            logger.error("根据分类获取活动失败，分类: {}", category, e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED, "获取分类活动失败");
        }
    }

    @Override
    public List<Activity> getActiveActivities() throws BusinessException {
        logger.debug("获取进行中的活动");
        try {
            // 查询开始时间小于等于当前时间且结束时间大于等于当前时间的活动
            LocalDateTime now = LocalDateTime.now();
            LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
            wrapper.le(Activity::getStartTime, now)  // 开始时间 <= 当前时间
                    .ge(Activity::getEndTime, now);   // 结束时间 >= 当前时间
            List<Activity> activities = activityMapper.selectList(wrapper);
            logger.info("获取进行中的活动完成，活动数量: {}", activities.size());
            return activities;
        } catch (Exception e) {
            logger.error("获取进行中的活动失败", e);
            throw new BusinessException(BusinessExceptionEnum.DATABASE_QUERY_FAILED, "获取进行中活动失败");
        }
    }
}
