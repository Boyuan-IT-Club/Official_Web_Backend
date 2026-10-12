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

    /**
     * 面试当天 08:00（Asia/Shanghai）。
     *
     * 双机集群没有 leader 选举，两台都会起定时任务 —— Node B 把这两个 cron
     * 的环境变量设成 "-"（Spring 的 Scheduled.CRON_DISABLED）来关掉，
     * 改默认值时别忘了那边也要对上。
     */
    private String dayReminderCron = "0 0 8 * * ?";

}
