package club.boyuan.official.service;

import club.boyuan.official.entity.Activity;
import club.boyuan.official.exception.BusinessException;

import java.util.List;

/**
 * Activity的业务层接口
 */
public interface IActivityService {

    /**
     * 创建活动
     * @param activity 活动对象
     * @return 创建成功的活动对象
     * @throws BusinessException 业务异常
     */
    Activity createActivity(Activity activity) throws BusinessException;

    /**
     * 更新活动
     * @param activity 活动对象
     * @return 更新后的活动对象
     * @throws BusinessException 业务异常
     */
    Activity updateActivity(Activity activity) throws BusinessException;

    /**
     * 根据ID删除活动
     * @param activityId 活动ID
     * @return 是否删除成功
     * @throws BusinessException 业务异常
     */
    boolean deleteActivity(Integer activityId) throws BusinessException;

    /**
     * 根据ID获取活动详情
     * @param activityId 活动ID
     * @return 活动对象
     * @throws BusinessException 业务异常
     */
    Activity getActivityById(Integer activityId) throws BusinessException;

    /**
     * 获取所有活动
     * @return 活动列表
     * @throws BusinessException 业务异常
     */
    List<Activity> getAllActivities() throws BusinessException;

    /**
     * 根据分类获取活动
     * @param category 活动分类
     * @return 活动列表
     * @throws BusinessException 业务异常
     */
    List<Activity> getActivitiesByCategory(String category) throws BusinessException;

    /**
     * 获取进行中的活动
     * @return 活动列表
     * @throws BusinessException 业务异常
     */
    List<Activity> getActiveActivities() throws BusinessException;
}
