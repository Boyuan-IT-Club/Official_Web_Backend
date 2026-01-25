package club.boyuan.official.mapper;

import club.boyuan.official.entity.Department;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Department的mapper层
 *
 * @author zewang
 * @version 1.0
 * @description: 部门数据访问层
 * @email "Zewang0217@outlook.com"
 * @date 2026/01/22 21:30
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
    
    /**
     * 查询所有启用的部门
     * @return 启用的部门列表
     */
    @Select("SELECT * FROM department WHERE status = 1 ORDER BY dept_name")
    List<Department> selectEnabledDepartments();
    
    /**
     * 根据部门编码查询部门
     * @param deptCode 部门编码
     * @return 部门对象
     */
    @Select("SELECT * FROM department WHERE dept_code = #{deptCode}")
    Department selectByDeptCode(String deptCode);
}