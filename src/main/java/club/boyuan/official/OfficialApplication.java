package club.boyuan.official;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@MapperScan("club.boyuan.official.mapper")
public class OfficialApplication {

    public static void main(String[] args) {
        // 检查关键环境变量是否已设置
        System.out.println("=== 环境变量检查 ===");
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");
        String jwtSecret = System.getenv("JWT_SECRET");
        
        System.out.println("DB_USERNAME: " + (dbUsername != null ? "已设置" : "未设置"));
        System.out.println("DB_PASSWORD: " + (dbPassword != null ? "已设置" : "未设置"));
        System.out.println("JWT_SECRET: " + (jwtSecret != null ? "已设置" : "未设置"));
        System.out.println("==================");
        
        SpringApplication.run(OfficialApplication.class, args);
    }

}
