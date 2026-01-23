package club.boyuan.official.controller;

import club.boyuan.official.dto.DepartmentDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.Department;
import club.boyuan.official.service.DepartmentService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理Controller
 *
 * @author zewan
 * @version 1.0
 * @date 2026-01-22 22:00
 * @since 2026
 */
@RestController
@RequestMapping("/api/departments")
@AllArgsConstructor
public class DepartmentController {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentController.class);
    private final DepartmentService departmentService;

    /**
     * 创建部门
     * @param departmentDTO 部门DTO对象
     * @return 创建成功的部门DTO
     */
    @PostMapping
    @PreAuthorize("hasAuthority('dept:manage')")
    public ResponseMessage<DepartmentDTO> createDepartment(@Validated @RequestBody DepartmentDTO departmentDTO) {
        logger.info("创建部门: {}", departmentDTO.getName());
        DepartmentDTO createdDepartment = departmentService.createDepartment(departmentDTO);
        logger.info("部门创建成功: {}", createdDepartment.getDeptId());
        return ResponseMessage.success(createdDepartment);
    }

    /**
     * 更新部门
     * @param deptId 部门ID
     * @param departmentDTO 部门DTO对象
     * @return 更新后的部门DTO
     */
    @PutMapping("/{deptId}")
    @PreAuthorize("hasAuthority('dept:manage')")
    public ResponseMessage<DepartmentDTO> updateDepartment(@PathVariable int deptId, @Validated @RequestBody DepartmentDTO departmentDTO) {
        logger.info("更新部门: {}", deptId);
        departmentDTO.setDeptId(deptId);
        DepartmentDTO updatedDepartment = departmentService.updateDepartment(departmentDTO);
        logger.info("部门更新成功: {}", deptId);
        return ResponseMessage.success(updatedDepartment);
    }

    /**
     * 删除部门
     * @param deptId 部门ID
     * @return 成功响应
     */
    @DeleteMapping("/{deptId}")
    @PreAuthorize("hasAuthority('dept:manage')")
    public ResponseMessage<Void> deleteDepartment(@PathVariable int deptId) {
        logger.info("删除部门: {}", deptId);
        boolean deleted = departmentService.deleteDepartment(deptId);
        if (deleted) {
            logger.info("部门删除成功: {}", deptId);
            return ResponseMessage.success();
        } else {
            logger.warn("部门删除失败，部门不存在: {}", deptId);
            return ResponseMessage.error(404, "部门不存在");
        }
    }

    /**
     * 获取部门详情
     * @param deptId 部门ID
     * @return 部门详情DTO
     */
    @GetMapping("/{deptId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<DepartmentDTO> getDepartmentById(@PathVariable int deptId) {
        logger.info("获取部门详情: {}", deptId);
        DepartmentDTO department = departmentService.getDepartmentById(deptId);
        return ResponseMessage.success(department);
    }

    /**
     * 获取部门列表
     * @param status 状态
     * @param keyword 关键词
     * @param page 页码
     * @param size 每页大小
     * @param sort 排序
     * @return 部门DTO列表
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<List<DepartmentDTO>> getDepartments(
            @RequestParam(required = false, defaultValue = "0") int status,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "deptId,asc") String sort) {
        logger.info("获取部门列表，状态: {}, 关键词: {}, 页码: {}, 每页大小: {}, 排序: {}", status, keyword, page, size, sort);
        List<DepartmentDTO> departments = departmentService.getDepartments(status, keyword, page, size, sort);
        return ResponseMessage.success(departments);
    }

    /**
     * 获取所有启用的部门
     * @return 启用的部门DTO列表
     */
    @GetMapping("/enabled")
    @PreAuthorize("isAuthenticated()")
    public ResponseMessage<List<DepartmentDTO>> getEnabledDepartments() {
        logger.info("获取所有启用的部门");
        List<DepartmentDTO> departments = departmentService.getEnabledDepartments();
        return ResponseMessage.success(departments);
    }
}