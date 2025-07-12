package club.boyuan.official;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("club.boyuan.official.mapper")
public class OfficialApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfficialApplication.class, args);
    }

}
