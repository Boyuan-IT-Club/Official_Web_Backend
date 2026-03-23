package club.boyuan.official.service;

import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.ResumeFieldDefinition;

import java.util.List;
import java.util.Map;

/**
 * 简历数据服务接口
 * 专门处理简历字段定义和字段值解析相关功能
 */
public interface ResumeDataService {
    
    /**
     * 获取指定周期的面试时间字段定义
     * @param cycleId 招募周期ID
     * @return 面试时间字段定义，未找到返回null
     */
    ResumeFieldDefinition getInterviewTimeFieldDefinition(Integer cycleId);
    
    /**
     * 获取指定周期的期望部门字段定义
     * @param cycleId 招募周期ID
     * @return 期望部门字段定义，未找到返回null
     */
    ResumeFieldDefinition getExpectedDepartmentsFieldDefinition(Integer cycleId);
    
    /**
     * 批量获取用户面试时间偏好
     * @param resumes 简历列表
     * @param fieldId 时间字段ID
     * @return Map<用户ID, 时间偏好列表>
     */
    Map<Integer, List<String>> getUserPreferredTimes(List<Resume> resumes, Integer fieldId);
    
    /**
     * 批量获取用户期望部门
     * @param resumes 简历列表
     * @param fieldId 部门字段ID
     * @return Map<用户ID, 部门偏好列表>
     */
    Map<Integer, List<String>> getUserPreferredDepartments(List<Resume> resumes, Integer fieldId);
    
    /**
     * 从简历中获取姓名字段值
     * @param resume 简历对象
     * @return 姓名字段值
     */
    String getResumeName(Resume resume);
    
    /**
     * 从简历中获取邮箱字段值
     * @param resume 简历对象
     * @return 邮箱字段值
     */
    String getResumeEmail(Resume resume);
    
    /**
     * 从简历中获取专业字段值
     * @param resume 简历对象
     * @return 专业字段值
     */
    String getResumeMajor(Resume resume);
    
    /**
     * 从简历中获取年级字段值
     * @param resume 简历对象
     * @return 年级字段值
     */
    String getResumeGrade(Resume resume);
}
