package club.boyuan.official.infra.config;

import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 自定义 JavaMailSender：按端口区分 587（STARTTLS）与 465（SSL）。
 * <p>
 * 声明本 Bean 后 Spring Boot 的 Mail 自动配置会退避，需手动启用 {@link MailProperties}。
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailSenderConfig {

    private static final int PORT_SMTPS = 465;

    @Bean
    public JavaMailSender javaMailSender(MailProperties mailProperties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.getHost());
        sender.setPort(mailProperties.getPort() != null ? mailProperties.getPort() : 587);
        sender.setUsername(mailProperties.getUsername());
        sender.setPassword(mailProperties.getPassword());
        sender.setDefaultEncoding(
                mailProperties.getDefaultEncoding() != null
                        ? mailProperties.getDefaultEncoding().name()
                        : StandardCharsets.UTF_8.name());
        sender.setJavaMailProperties(buildJavaMailProperties(mailProperties));
        return sender;
    }

    private Properties buildJavaMailProperties(MailProperties mailProperties) {
        Properties props = new Properties();
        if (mailProperties.getProperties() != null) {
            props.putAll(mailProperties.getProperties());
        }

        int port = mailProperties.getPort() != null ? mailProperties.getPort() : 587;
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");

        if (port == PORT_SMTPS) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.socketFactory.port", String.valueOf(port));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.remove("mail.smtp.socketFactory.class");
            props.remove("mail.smtp.socketFactory.port");
        }

        if (StringUtils.hasText(mailProperties.getHost()) && mailProperties.getHost().contains("gmail")) {
            props.put("mail.smtp.ssl.trust", mailProperties.getHost());
        }

        return props;
    }
}
