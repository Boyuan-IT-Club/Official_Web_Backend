package club.boyuan.official.persistence;

import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 回归保护:解绑必须显式 UPDATE ... SET github = NULL(MyBatis-Plus updateById 默认
 * NOT_NULL 策略忽略 null 字段 —— 线上事故:解绑返回 200 但 DB 值不变)。
 * 本测试直接验证 edit() 解绑路径使用的显式 UpdateWrapper 在真库生效。
 */
@SpringBootTest
class GithubNullUpdateIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void explicitSetNullActuallyClearsGithub() {
        String original = userMapper.selectById(1).getGithub();
        try {
            userMapper.update(null, new UpdateWrapper<User>()
                    .eq("user_id", 1)
                    .set("github", null));
            assertNull(userMapper.selectById(1).getGithub());
        } finally {
            userMapper.update(null, new UpdateWrapper<User>()
                    .eq("user_id", 1)
                    .set("github", original));
        }
    }
}
