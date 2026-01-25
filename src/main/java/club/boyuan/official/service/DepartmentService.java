package club.boyuan.official.service;

import club.boyuan.official.dto.DepartmentDTO;
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
     * @param departmentDTO 部门DTO对象
     * @return 创建成功的部门DTO
     * @throws BusinessException 业务异常
     */
    DepartmentDTO createDepartment(DepartmentDTO departmentDTO) throws BusinessException;
    
    /**
     * 更新部门
     * @param departmentDTO 部门DTO对象
     * @return 更新后的部门DTO
     * @throws BusinessException 业务异常
     */
    DepartmentDTO updateDepartment(DepartmentDTO departmentDTO) throws BusinessException;
    
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
     * @return 部门DTO
     * @throws BusinessException 业务异常
     */
    DepartmentDTO getDepartmentById(int deptId) throws BusinessException;
    
    /**
     * 根据部门编码获取部门
     * @param deptCode 部门编码
     * @return 部门DTO
     * @throws BusinessException 业务异常
     */
    DepartmentDTO getDepartmentByCode(String deptCode) throws BusinessException;
    
    /**
     * 分页获取部门列表
     * @param status 部门状态
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页大小
     * @param sort 排序字段
     * @return 部门DTO列表
     * @throws BusinessException 业务异常
     */
    List<DepartmentDTO> getDepartments(int status, String keyword, int page, int size, String sort) throws BusinessException;
    
    /**
     * 获取所有启用的部门（不分页）
     * @return 启用的部门DTO列表
     * @throws BusinessException 业务异常
     */
    List<DepartmentDTO> getEnabledDepartments() throws BusinessException;
}