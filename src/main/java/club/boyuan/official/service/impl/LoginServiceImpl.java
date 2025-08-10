package club.boyuan.official.service.impl;

import club.boyuan.official.dto.LoginDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.ILoginService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.service.IVerificationCodeService;
import club.boyuan.official.utils.JwtTokenUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 登录服务实现类
 * 负责处理多种登录方式的验证逻辑，包括邮箱密码、邮箱验证码、手机密码、手机验证码和用户名密码登录
 */
@Service
@AllArgsConstructor
public class LoginServiceImpl implements ILoginService {

    /**
     * 用户服务，用于获取用户信息
     */
    private final IUserService userService;

    /**
     * JWT工具类，用于生成和验证令牌
     */
    private final JwtTokenUtil jwtTokenUtil;
    

    /**
     * 验证码服务，用于验证邮箱和手机验证码
     */
    private final IVerificationCodeService IVerificationCodeService;

    /**
     * Redis模板，用于操作Redis
     */
    private final RedisTemplate<String, String> redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 通过邮箱和密码登录
     * @param email 用户邮箱
     * @param password 用户密码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByEmailPassword(String email, String password) {
        User user = userService.getUserByEmail(email);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ResponseMessage.error(401, "邮箱或密码错误");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 通过邮箱和验证码登录
     * @param email 用户邮箱
     * @param code 邮箱验证码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByEmailCode(String email, String code) {
        if (!verifyVerificationCode(email, code)) {
            return ResponseMessage.error(401, "邮箱验证码错误或已过期");
        }
        User user = userService.getUserByEmail(email);
        if (user == null) {
            return ResponseMessage.error(401, "用户不存在");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 通过手机号和密码登录
     * @param phone 用户手机号
     * @param password 用户密码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByPhonePassword(String phone, String password) {
        User user = userService.getUserByPhone(phone);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ResponseMessage.error(401, "手机号或密码错误");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 通过手机号和验证码登录
     * @param phone 用户手机号
     * @param code 手机验证码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByPhoneCode(String phone, String code) {
        if (!verifyVerificationCode(phone, code)) {
            return ResponseMessage.error(401, "手机验证码错误或已过期");
        }
        User user = userService.getUserByPhone(phone);
        if (user == null) {
            return ResponseMessage.error(401, "用户不存在");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 通过用户名和密码登录
     * @param username 用户名
     * @param password 用户密码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByUsernamePassword(String username, String password) {
        User user = userService.getUserByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ResponseMessage.error(401, "用户名或密码错误");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 生成登录成功响应
     * @param user 登录用户信息
     * @return 包含令牌的成功响应
     */
    private ResponseMessage<?> generateLoginSuccessResponse(User user) {
        // 检查用户是否被冻结
        if (!user.getStatus()) {
            return ResponseMessage.error(403, "账号已被冻结，无法登录");
        }
        String token = jwtTokenUtil.generateToken(user.getUsername(), user.getUserId(), Collections.singletonList(user.getRole()));
        return ResponseMessage.success(new TokenVO(token));
    }

    // 内部类用于封装返回的令牌信息
    @Override
    public String generateVerificationCode(String identifier) {
        // 生成6位数字验证码
        Random random = new Random();
        StringBuilder codeBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            codeBuilder.append(random.nextInt(10));
        }
        return codeBuilder.toString();
    }

    @Override
    public void saveVerificationCode(String identifier, String code, long expireSeconds) {
        // 使用标识符作为Redis键，验证码作为值，设置过期时间
        redisTemplate.opsForValue().set("verification_code:" + identifier, code, expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 验证验证码
     * @param identifier 标识符（手机号或邮箱）
     * @param code 待验证的验证码
     * @return 如果验证码有效则返回true，否则返回false
     */
    @Override
    public boolean verifyVerificationCode(String identifier, String code) {
        String key = "verification_code:" + identifier;
        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null || !storedCode.equals(code)) {
            return false;
        }
        // 验证成功后删除验证码，防止重复使用
        redisTemplate.delete(key);
        return true;
    }

    @Setter
    @Getter
    public static class TokenVO {
        private String token;

        public TokenVO(String token) {
            this.token = token;
        }

    }
}