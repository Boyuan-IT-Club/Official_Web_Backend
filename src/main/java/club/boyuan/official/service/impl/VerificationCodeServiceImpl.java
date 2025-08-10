package club.boyuan.official.service.impl;

import club.boyuan.official.service.IVerificationCodeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import jakarta.annotation.Resource;
import club.boyuan.official.service.SmsService;

/**
 * 验证码服务实现类
 * 负责生成、发送和验证邮箱验证码和手机验证码
 */
@Service
@AllArgsConstructor
public class VerificationCodeServiceImpl implements IVerificationCodeService {

    /**
     * Redis模板，用于存储验证码
     */
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 短信服务，用于发送手机验证码
     */
    private final SmsService smsService;

    /**
     * 验证邮箱验证码
     * @param email 邮箱地址
     * @param code 用户输入的验证码
     * @return 验证结果：true-验证成功，false-验证失败
     */
    @Override
    public boolean verifyEmailCode(String email, String code) {
        // 统一使用LoginServiceImpl中的键格式
        String key = "verification_code:" + email;
        Object storedValue = redisTemplate.opsForValue().get(key);
        String storedCode = storedValue != null ? storedValue.toString() : null;
        if (storedCode == null) {
            return false;
        }

        // 验证验证码
        if (storedCode.equals(code)) {
            redisTemplate.delete(key);
            return true;
        }

        return false;
    }

    /**
     * 验证手机验证码
     * @param phone 手机号码
     * @param code 用户输入的验证码
     * @return 验证结果：true-验证成功，false-验证失败
     */
    @Override
    public boolean verifyPhoneCode(String phone, String code) {
        return smsService.verifyCode(phone, code);
    }
}