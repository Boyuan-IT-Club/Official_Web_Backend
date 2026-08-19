package club.boyuan.official.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 面试通知定时任务与开关配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "interview.notification")
public class InterviewNotificationProperties {

    /** 是否启用定时提醒 */
    private boolean enabled = true;

    /** 面试前一天 12:00（Asia/Shanghai） */
    private String eveReminderCron = "0 0 12 * * ?";

}
