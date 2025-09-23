package club.boyuan.official.service;

import club.boyuan.official.dto.PageResultDTO;
import club.boyuan.official.dto.ResumeDTO;
import club.boyuan.official.dto.ResumeFieldValueDTO;
import club.boyuan.official.dto.SimpleResumeFieldDTO;
import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.ResumeFieldValue;

import java.util.List;

/**
 * 简历服务接口
 */
public interface IResumeService {
    
    /**
     * 根据用户ID和招聘年份ID获取简历
     * @param userId 用户ID
     * @param cycleId 招聘年份ID
     * @return 简历
     */
    Resume getResumeByUserIdAndCycleId(Integer userId, Integer cycleId);
    
    /**
     * 根据简历ID获取简历
     * @param resumeId 简历ID
     * @return 简历
     */
    Resume getResumeById(Integer resumeId);
    
    /**
     * 根据用户ID获取简历列表
     * @param userId 用户ID
     * @return 简历列表
     */
    List<Resume> getResumesByUserId(Integer userId);
    
    /**
     * 创建简历
     * @param resume 简历实体
     * @return 创建后的简历
     */
    Resume createResume(Resume resume);
    
    /**
     * 更新简历
     * @param resume 简历实体
     * @return 更新后的简历
     */
    Resume updateResume(Resume resume);
    
    /**
     * 删除简历
     * @param resumeId 简历ID
     */
    void deleteResume(Integer resumeId);
    
    /**
     * 提交简历
     * @param resumeId 简历ID
     * @return 提交后的简历
     */
    Resume submitResume(Integer resumeId);
    
    /**
     * 保存简历字段值
     * @param fieldValues 字段值列表
     */
    void saveFieldValues(List<ResumeFieldValue> fieldValues);
    
    /**
     * 根据简历ID获取字段值列表
     * @param resumeId 简历ID
     * @return 字段值列表
     */
    List<ResumeFieldValue> getFieldValuesByResumeId(Integer resumeId);
    
    /**
     * 根据简历ID获取字段值列表（包含字段定义信息）
     * @param resumeId 简历ID
     * @return 字段值DTO列表
     */
    List<ResumeFieldValueDTO> getFieldValuesWithDefinitionsByResumeId(Integer resumeId);
    
    /**
     * 根据用户ID和招聘年份ID获取简历及字段值信息
     * @param userId 用户ID
     * @param cycleId 招聘年份ID
     * @return 简历DTO（包含字段值及字段定义信息）
     */
    ResumeDTO getResumeWithFieldValues(Integer userId, Integer cycleId);
    
    /**
     * 根据简历ID获取简历及字段值信息
     * @param resumeId 简历ID
     * @return 简历DTO（包含字段值及字段定义信息）
     */
    ResumeDTO getResumeWithFieldValuesById(Integer resumeId);
    
    /**
     * 条件查询简历列表
     * @param name 姓名（可选）
     * @param major 专业（可选）
     * @param expectedDepartment 期望部门（可选）
     * @param cycleId 年份ID（可选）
     * @param status 简历状态（可选），支持多个状态，用逗号分隔，如"2,3,4,5"
     * @return 简历DTO列表
     */
    List<ResumeDTO> queryResumes(String name, String major, String expectedDepartment, Integer cycleId, String status);
    
    /**
     * 条件查询简历列表（分页）
     * @param name 姓名（可选）
     * @param major 专业（可选）
     * @param expectedDepartment 期望部门（可选）
     * @param cycleId 年份ID（可选）
     * @param status 简历状态（可选），支持多个状态，用逗号分隔，如"2,3,4,5"
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页结果DTO
     */
    PageResultDTO<ResumeDTO> queryResumesWithPagination(String name, String major, String expectedDepartment, Integer cycleId, String status, int page, int size);
    
    /**
     * 根据cycleId获取所有简历
     * @param cycleId 招募周期ID
     * @return 简历实体列表
     */
    List<Resume> getAllResumesByCycleId(Integer cycleId);
}