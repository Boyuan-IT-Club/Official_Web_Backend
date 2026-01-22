package club.boyuan.official.service;

import club.boyuan.official.entity.Department;
import club.boyuan.official.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * Department的业务层
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 21:35
 * @since 2026
 */
public interface DepartmentService extends IService<Department> {
    /**
     * 创建部门
     * @param department 部门对象
     * @return 创建成功的部门对象
     * @throws BusinessException 业务异常
     */
    Department createDepartment(Department department) throws BusinessException;
    
    /**
     * 更新部门
     * @param department 部门对象
     * @return 更新后的部门对象
     * @throws BusinessException 业务异常
     */
    Department updateDepartment(Department department) throws BusinessException;
    
    /**
     * 删除部门
     * @param deptId 部门ID
     * @return 是否删除成功
     * @throws BusinessException 业务异常
     */
    boolean deleteDepartment(int deptId) throws BusinessException;
    
    /**
     * 根据ID获取部门详情
     * @param deptId 部门ID
     * @return 部门对象
     * @throws BusinessException 业务异常
     */
    Department getDepartmentById(int deptId) throws BusinessException;
    
    /**
     * 根据部门编码获取部门
     * @param deptCode 部门编码
     * @return 部门对象
     * @throws BusinessException 业务异常
     */
    Department getDepartmentByCode(String deptCode) throws BusinessException;
    
    /**
     * 分页获取部门列表
     * @param status 部门状态
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页大小
     * @param sort 排序字段
     * @return 部门列表
     * @throws BusinessException 业务异常
     */
    List<Department> getDepartments(int status, String keyword, int page, int size, String sort) throws BusinessException;
    
    /**
     * 获取所有启用的部门（不分页）
     * @return 启用的部门列表
     * @throws BusinessException 业务异常
     */
    List<Department> getEnabledDepartments() throws BusinessException;
}