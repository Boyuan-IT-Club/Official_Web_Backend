package club.boyuan.official.service.impl;

import club.boyuan.official.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import jakarta.annotation.Resource;
import club.boyuan.official.service.SmsService;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现类
 * 负责生成、发送和验证邮箱验证码和手机验证码
 */
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    /**
     * Redis模板，用于存储验证码
     */
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 邮件发送器，用于发送邮箱验证码
     */
    @Autowired
    private JavaMailSender mailSender;

    /**
     * 短信服务，用于发送手机验证码
     */
    @Autowired
    private SmsService smsService;

    /**
     * 发件人邮箱地址
     */
    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 发送邮箱验证码
     * @param email 目标邮箱地址
     */
    @Override
    public void sendEmailCode(String email) {
        String code = generateCode();
        // 发送邮件
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("登录验证码");
        message.setText("您的登录验证码为: " + code + "，有效期5分钟。");
        mailSender.send(message);

        // 存储验证码到Redis，有效期5分钟
        redisTemplate.opsForValue().set("email:code:" + email, code, 5, TimeUnit.MINUTES);
    }

    /**
     * 发送手机验证码
     * @param phone 目标手机号码
     */
    @Override
    public void sendSmsCode(String phone) {
        String code = generateCode();
        // 调用短信服务发送验证码
        smsService.sendVerificationCode(phone);

        // 存储验证码到Redis，有效期5分钟
        redisTemplate.opsForValue().set("phone:code:" + phone, code, 5, TimeUnit.MINUTES);
    }

    /**
     * 验证邮箱验证码
     * @param email 邮箱地址
     * @param code 用户输入的验证码
     * @return 验证结果：true-验证成功，false-验证失败
     */
    @Override
    public boolean verifyEmailCode(String email, String code) {
        return verifyCode("email:code:" + email, code);
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

    /**
     * 生成6位数字验证码
     * @return 6位数字字符串
     */
    private String generateCode() {
        // 生成6位数字验证码
        return String.format("%06d", new Random().nextInt(999999));
    }

    private boolean verifyCode(String key, String code) {
        String storedCode = (String) redisTemplate.opsForValue().get(key);
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
}