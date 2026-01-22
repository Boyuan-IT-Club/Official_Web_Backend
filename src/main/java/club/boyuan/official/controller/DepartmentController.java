package club.boyuan.official.controller;

import club.boyuan.official.dto.DepartmentDTO;
import club.boyuan.official.entity.Department;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.service.DepartmentService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final DepartmentService departmentService;

    /**
     * 创建部门
     * @param departmentDTO 部门DTO对象
     * @return 创建成功的部门DTO
     */
    @PostMapping
    @PreAuthorize("hasAuthority('dept:manage')")
    public ResponseEntity<DepartmentDTO> createDepartment(@Validated @RequestBody DepartmentDTO departmentDTO) {
        try {
            DepartmentDTO createdDepartment = departmentService.createDepartment(departmentDTO);
            return new ResponseEntity<>(createdDepartment, HttpStatus.CREATED);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 更新部门
     * @param deptId 部门ID
     * @param departmentDTO 部门DTO对象
     * @return 更新后的部门DTO
     */
    @PutMapping("/{deptId}")
    @PreAuthorize("hasAuthority('dept:manage')")
    public ResponseEntity<DepartmentDTO> updateDepartment(@PathVariable int deptId, @Validated @RequestBody DepartmentDTO departmentDTO) {
        try {
            departmentDTO.setDeptId(deptId);
            DepartmentDTO updatedDepartment = departmentService.updateDepartment(departmentDTO);
            return ResponseEntity.ok(updatedDepartment);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 删除部门
     * @param deptId 部门ID
     * @return 成功响应
     */
    @DeleteMapping("/{deptId}")
    @PreAuthorize("hasAuthority('dept:manage')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable int deptId) {
        try {
            boolean deleted = departmentService.deleteDepartment(deptId);
            return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取部门详情
     * @param deptId 部门ID
     * @return 部门详情DTO
     */
    @GetMapping("/{deptId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DepartmentDTO> getDepartmentById(@PathVariable int deptId) {
        try {
            DepartmentDTO department = departmentService.getDepartmentById(deptId);
            return ResponseEntity.ok(department);
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
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
    public ResponseEntity<List<DepartmentDTO>> getDepartments(
            @RequestParam(required = false, defaultValue = "0") int status,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "deptId,asc") String sort) {
        try {
            List<DepartmentDTO> departments = departmentService.getDepartments(status, keyword, page, size, sort);
            return ResponseEntity.ok(departments);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 获取所有启用的部门
     * @return 启用的部门DTO列表
     */
    @GetMapping("/enabled")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DepartmentDTO>> getEnabledDepartments() {
        try {
            List<DepartmentDTO> departments = departmentService.getEnabledDepartments();
            return ResponseEntity.ok(departments);
        } catch (BusinessException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}