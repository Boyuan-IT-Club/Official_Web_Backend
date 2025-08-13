package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 提供系统运行状态检查接口
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 健康检查接口
     * @return 系统健康状态信息
     */
    @GetMapping
    public ResponseEntity<ResponseMessage<Map<String, Object>>> healthCheck() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", System.currentTimeMillis());
        data.put("service", "Official Application");
        
        // 检查数据库连接状态
        data.put("database", checkDatabaseStatus());
        
        // 检查Redis状态（通过检查一个简单的值）
        data.put("redis", checkRedisStatus());
        
        return ResponseEntity.ok(new ResponseMessage<>(200, "服务正常", data));
    }
    
    /**
     * 检查数据库连接状态
     * @return 数据库状态信息
     */
    private Map<String, Object> checkDatabaseStatus() {
        Map<String, Object> dbStatus = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(2);
            dbStatus.put("status", isValid ? "UP" : "DOWN");
            dbStatus.put("message", isValid ? "数据库连接正常" : "数据库连接异常");
        } catch (SQLException e) {
            dbStatus.put("status", "DOWN");
            dbStatus.put("message", "数据库连接失败: " + e.getMessage());
        }
        return dbStatus;
    }
    
    /**
     * 检查Redis状态
     * @return Redis状态信息
     */
    private Map<String, Object> checkRedisStatus() {
        Map<String, Object> redisStatus = new HashMap<>();
        try {
            String result = jdbcTemplate.queryForObject("SELECT 1", String.class);
            boolean isOk = "1".equals(result);
            redisStatus.put("status", isOk ? "UP" : "DOWN");
            redisStatus.put("message", isOk ? "Redis连接正常" : "Redis连接异常");
        } catch (Exception e) {
            redisStatus.put("status", "DOWN");
            redisStatus.put("message", "Redis连接失败: " + e.getMessage());
        }
        return redisStatus;
    }
}