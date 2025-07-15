package club.boyuan.official.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import jakarta.annotation.Resource;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信服务类
 * 负责生成手机验证码、发送验证码并存储到Redis、验证验证码有效性
 */
@Service
public class SmsService {

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
     * 生成指定长度的随机数字验证码
     * @param length 验证码长度，建议6位
     * @return 随机数字字符串
     */
    public String generateVerificationCode(int length) {
        String digits = "0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(digits.charAt(random.nextInt(digits.length())));
        }
        return code.toString();
    }

    /**
     * 发送手机验证码并存储到Redis
     * 实际环境中会调用短信服务商API发送短信
     * @param phoneNumber 目标手机号码
     */
    public void sendVerificationCode(String phoneNumber) {
        String code = generateVerificationCode(6);
        // 实际项目中应调用短信服务商API发送短信
        // 这里仅做模拟，并将验证码存入缓存
        // 存储验证码到Redis，并设置5分钟过期时间
        redisTemplate.opsForValue().set("sms:code:" + phoneNumber, code, 5, java.util.concurrent.TimeUnit.MINUTES);
        System.out.println("向手机" + phoneNumber + "发送验证码: " + code + "，有效期5分钟");
    }

    /**
     * 验证手机验证码是否正确
     * 验证成功后会从Redis删除验证码防止重复使用
     * @param phoneNumber 手机号码
     * @param inputCode 用户输入的验证码
     * @return 验证结果：true-验证成功，false-验证失败
     */
    public boolean verifyCode(String phoneNumber, String inputCode) {
        if (phoneNumber == null || inputCode == null) {
            return false;
        }
        String storedCode = (String) redisTemplate.opsForValue().get("sms:code:" + phoneNumber);
        if (storedCode == null) {
            return false;
        }
        // 验证成功后移除验证码，防止重复使用
        if (storedCode.equals(inputCode)) {
            redisTemplate.delete("sms:code:" + phoneNumber);
            return true;
        }
        return false;
    }
}