package club.boyuan.official.utils;

import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.regex.Pattern;
import club.boyuan.official.service.SmsService;

/**
 * 消息发送工具类
 * 处理邮箱和手机验证码发送及格式验证
 */
@Component
public class MessageUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{11}$");

    @Autowired
    private JavaMailSender mailSender;
    // 从配置文件中获取发件人邮箱地址
    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    private SmsService smsService;

    /**
     * 验证邮箱格式
     * @param email 邮箱地址
     * @throws BusinessException 邮箱格式不正确时抛出
     */
    public void validateEmail(String email) {
        if (email == null || email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_EMAIL_FORMAT);
        }
    }

    /**
     * 验证手机号格式
     * @param phone 手机号
     * @throws BusinessException 手机号格式不正确时抛出
     */
    public void validatePhone(String phone) {
        if (phone == null || phone.isEmpty() || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_PHONE_FORMAT);
        }
    }

    /**
     * 发送邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    public void sendEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    /**
     * 发送短信
     * @param phone 收件人手机号
     */
    public void sendSms(String phone) {
        smsService.sendSms(phone);
    }
}