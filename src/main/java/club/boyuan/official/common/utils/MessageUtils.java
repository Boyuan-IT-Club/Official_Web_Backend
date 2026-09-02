package club.boyuan.official.common.utils;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.MessagingException;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.regex.Pattern;
import club.boyuan.official.domain.user.service.SmsService;

/**
 * 消息发送工具类
 * 处理邮箱和手机验证码发送及格式验证
 */
@Component
public class MessageUtils {

    private static final Logger logger = LoggerFactory.getLogger(MessageUtils.class);

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
            throw new BusinessException(BusinessExceptionEnum.PHONE_FORMAT_ERROR);
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
     * 发送 HTML 邮件。
     *
     * 与上面的纯文本版并存而不是替换：验证码之外还有些内部通知用纯文本更合适，
     * 而且真出问题时可以逐个回退，不必一次全切。
     *
     * @param plainFallback 纯文本兜底。不给的话，关闭了 HTML 的客户端（以及
     *                      部分邮件预览、无障碍读屏）只会看到一片空白
     */
    public void sendHtmlEmail(String to, String subject, String html, String plainFallback) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true 才能同时带纯文本与 HTML 两份正文
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainFallback == null ? "" : plainFallback, html);
            mailSender.send(message);
        } catch (MessagingException e) {
            logger.error("HTML 邮件发送失败，收件人: {}, 主题: {}", to, subject, e);
            throw new BusinessException(BusinessExceptionEnum.EMAIL_SEND_FAILED);
        }
    }

    /**
     * 发送短信
     * @param phone 收件人手机号
     */
    public void sendSms(String phone) {
        smsService.sendSms(phone);
    }
}