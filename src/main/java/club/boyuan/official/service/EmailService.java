package club.boyuan.official.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class EmailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 生成指定长度的随机验证码
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
     * 发送邮箱验证码
     */
    public void sendVerificationCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("登录验证码");
        message.setText("您的登录验证码是: " + code + "，有效期5分钟，请妥善保管。");
        mailSender.send(message);
    }

    /**
     * 验证验证码是否正确
     */
    public boolean verifyCode(String inputCode, String storedCode) {
        if (inputCode == null || storedCode == null) {
            return false;
        }
        return inputCode.equals(storedCode);
    }
}