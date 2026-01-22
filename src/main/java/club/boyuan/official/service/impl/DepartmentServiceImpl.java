package club.boyuan.official.service.impl;

import club.boyuan.official.entity.Department;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.DepartmentMapper;
import club.boyuan.official.service.DepartmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Department的业务层实现
 *
 * @author zewang
 * @version 1.0
 * @date 2026-01-22 21:35
 * @since 2026
 */
@Service
@AllArgsConstructor
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements DepartmentService {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentServiceImpl.class);

    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Department createDepartment(Department department) throws BusinessException {
        if (department == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "部门对象不能为空");
        }
        
        // 验证必填字段
        if (StringUtils.isBlank(department.getDeptName())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "部门名称不能为空");
        }
        
        if (StringUtils.isBlank(department.getDeptCode())) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "部门编码不能为空");
        }
        
        // 检查部门编码是否已存在
        if (departmentMapper.selectByDeptCode(department.getDeptCode()) != null) {
            throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "部门编码已存在");
        }
        
        // 设置默认值
        if (department.getStatus() == null) {
            department.setStatus(1); // 默认启用
        }
        
        // 保存部门
        departmentMapper.insert(department);
        logger.info("成功创建部门，部门ID: {}, 部门名称: {}", department.getDeptId(), department.getDeptName());
        return department;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Department updateDepartment(Department department) throws BusinessException {
        if (department == null || department.getDeptId() == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "部门ID不能为空");
        }
        
        // 检查部门是否存在
        Department existingDepartment = departmentMapper.selectById(department.getDeptId());
        if (existingDepartment == null) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND, "部门不存在");
        }
        
        // 如果修改了部门编码，检查新编码是否已存在
        if (StringUtils.isNotBlank(department.getDeptCode()) && 
                !department.getDeptCode().equals(existingDepartment.getDeptCode())) {
            if (departmentMapper.selectByDeptCode(department.getDeptCode()) != null) {
                throw new BusinessException(BusinessExceptionEnum.RESOURCE_CONFLICT, "部门编码已存在");
            }
        }
        
        // 更新部门
        departmentMapper.updateById(department);
        logger.info("成功更新部门，部门ID: {}, 部门名称: {}", department.getDeptId(), department.getDeptName());
        return departmentMapper.selectById(department.getDeptId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDepartment(int deptId) throws BusinessException {
        if (deptId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "部门ID不能为空");
        }
        
        // 检查部门是否存在
        Department department = departmentMapper.selectById(deptId);
        if (department == null) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND, "部门不存在");
        }
        
        // 逻辑删除：将状态设置为禁用
        department.setStatus(0);
        int result = departmentMapper.updateById(department);
        
        boolean success = result > 0;
        if (success) {
            logger.info("成功删除部门，部门ID: {}, 部门名称: {}", deptId, department.getDeptName());
        }
        
        return success;
    }

    @Override
    public Department getDepartmentById(int deptId) throws BusinessException {
        if (deptId <= 0) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "部门ID不能为空");
        }
        
        Department department = departmentMapper.selectById(deptId);
        if (department == null) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND, "部门不存在");
        }
        return department;
    }

    @Override
    public Department getDepartmentByCode(String deptCode) throws BusinessException {
        if (StringUtils.isBlank(deptCode)) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "部门编码不能为空");
        }
        
        Department department = departmentMapper.selectByDeptCode(deptCode);
        if (department == null) {
            throw new BusinessException(BusinessExceptionEnum.DEPARTMENT_NOT_FOUND, "部门不存在");
        }
        return department;
    }

    @Override
    public List<Department> getDepartments(int status, String keyword, int page, int size, String sort) throws BusinessException {
        // 创建Lambda查询包装器
        LambdaQueryWrapper<Department> queryWrapper = new LambdaQueryWrapper<>();
        
        // 状态条件
        if (status != 0) {
            queryWrapper.eq(Department::getStatus, status);
        }
        
        // 关键字条件（模糊匹配部门名称或编码）
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Department::getDeptName, keyword)
                    .or()
                    .like(Department::getDeptCode, keyword));
        }
        
        // 排序处理
        if (StringUtils.isNotBlank(sort)) {
            if (sort.startsWith("-")) {
                // 倒序
                String column = sort.substring(1);
                switch (column) {
                    case "deptName" -> queryWrapper.orderByDesc(Department::getDeptName);
                    case "deptCode" -> queryWrapper.orderByDesc(Department::getDeptCode);
                    case "createTime" -> queryWrapper.orderByDesc(Department::getCreateTime);
                    default -> queryWrapper.orderByDesc(Department::getDeptId);
                }
            } else {
                // 正序
                switch (sort) {
                    case "deptName" -> queryWrapper.orderByAsc(Department::getDeptName);
                    case "deptCode" -> queryWrapper.orderByAsc(Department::getDeptCode);
                    case "createTime" -> queryWrapper.orderByAsc(Department::getCreateTime);
                    default -> queryWrapper.orderByAsc(Department::getDeptId);
                }
            }
        } else {
            // 默认按部门名称正序
            queryWrapper.orderByAsc(Department::getDeptName);
        }
        
        // 分页处理
        Page<Department> pageObj = new Page<>(page, size);
        List<Department> departments = departmentMapper.selectPage(pageObj, queryWrapper).getRecords();
        
        logger.info("查询部门列表成功，状态: {}, 关键字: {}, 页码: {}, 每页大小: {}, 部门数量: {}", 
                status, keyword, page, size, departments.size());
        return departments;
    }

    @Override
    public List<Department> getEnabledDepartments() throws BusinessException {
        List<Department> departments = departmentMapper.selectEnabledDepartments();
        logger.info("获取所有启用的部门成功，部门数量: {}", departments.size());
        return departments;
    }
}