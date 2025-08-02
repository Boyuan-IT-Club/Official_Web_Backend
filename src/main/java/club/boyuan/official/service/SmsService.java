package club.boyuan.official.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import jakarta.annotation.Resource;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 短信服务类
 * 负责生成手机验证码、发送验证码并存储到Redis、验证验证码有效性
 */
@Service
public class SmsService {
    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);

    /**
     * Redis模板，用于存储和验证验证码
     */
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 短信服务访问密钥
     */
    @Value("${sms.access-key}")
    private String accessKey;

    /**
     * 短信服务密钥
     */
    @Value("${sms.secret-key}")
    private String secretKey;

    /**
     * 生成6位随机数字验证码
     * @return 6位随机数字字符串
     */
    public String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 生成100000-999999之间的随机数
        logger.debug("生成验证码: {}", code);
        return String.valueOf(code);
    }

    /**
     * 发送手机验证码并存储到Redis
     * 实际环境中会调用短信服务商API发送短信
     * @param phoneNumber 目标手机号码
     */
    public void sendVerificationCode(String phoneNumber) {
        String code = generateVerificationCode();
        // 实际项目中应调用短信服务商API发送短信
        // 这里仅做模拟，并将验证码存入缓存
        // 存储验证码到Redis，并设置5分钟过期时间
        redisTemplate.opsForValue().set("sms:code:" + phoneNumber, code, 5, TimeUnit.MINUTES);
        logger.info("向手机{}发送验证码: {}，有效期5分钟", phoneNumber, code);
    }

    /**
     * 发送短信验证码（兼容旧API）
     * @param phoneNumber 目标手机号码
     */
    public void sendSms(String phoneNumber) {
        logger.debug("调用兼容方法发送短信验证码，手机号: {}", phoneNumber);
        sendVerificationCode(phoneNumber);
    }

    public boolean verifyCode(String phoneNumber, String inputCode) {
        if (phoneNumber == null || inputCode == null) {
            logger.warn("验证码验证失败：手机号或验证码为空");
            return false;
        }
        Object storedValue = redisTemplate.opsForValue().get("sms:code:" + phoneNumber);
        String storedCode = storedValue != null ? storedValue.toString() : null;
        
        if (storedCode == null) {
            logger.warn("验证码验证失败：未找到手机号{}的验证码", phoneNumber);
            return false;
        }
        
        boolean isValid = storedCode.equals(inputCode);
        if (isValid) {
            redisTemplate.delete("sms:code:" + phoneNumber);
            logger.info("验证码验证成功，手机号: {}", phoneNumber);
        } else {
            logger.warn("验证码验证失败，手机号: {}, 输入验证码: {}, 存储验证码: {}", phoneNumber, inputCode, storedCode);
        }
        
        return isValid;
    }
}