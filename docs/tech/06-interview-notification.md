# 面试通知说明

## 当前能力

当前面试通知通过 HTML 邮件发送，覆盖三类场景：

1. 预约成功通知：学生完成面试预约后发送。
2. 面试提醒：面试前一天中午、面试当天早上定时发送。
3. 面试结果通知：录取或未录取结果发送。

通知链路是异步的：

```text
业务操作
  -> ReliableMessagePublisher
  -> Outbox 或 RabbitMQ
  -> official.interview.notification
  -> InterviewNotificationConsumer
  -> InterviewNotificationServiceImpl.deliver
  -> MessageUtils.sendHtmlEmail
  -> interview_notification_log
```

`application.yml` 中的 `spring.mail.username` / `MAIL_USERNAME` 是发件邮箱账号，不是收件人邮箱。收件人邮箱来自简历字段，兜底才使用用户表邮箱。

## 通知类型

代码枚举：`InterviewNotificationType`

| 类型 | 触发方式 | 说明 |
| --- | --- | --- |
| `BOOKING_SUCCESS` | 预约成功后投递 | 面试预约成功通知 |
| `EVE_REMINDER` | 定时任务 | 面试前一天提醒 |
| `DAY_REMINDER` | 定时任务 | 面试当天提醒 |
| `ADMISSION` | 结果通知 | 录取通知 |
| `REJECTION` | 结果通知 | 未录取通知 |

## 收件人邮箱来源

预约成功和提醒通知：

```text
interview_schedule.resume_id
  -> resume
  -> resume_field_definition.field_label = "邮箱"
  -> resume_field_value.field_value
```

如果简历邮箱为空，则 `ResumeDataServiceImpl#getResumeEmail` 会兜底读取：

```text
user.email
```

结果通知也是同样逻辑：

```text
优先 resume 中的邮箱
否则 user.email
```

## 发件配置

邮件发送使用 Spring `JavaMailSender`，发件账号通过环境变量注入：

```yaml
spring:
  mail:
    host: ${MAIL_HOST:}
    port: ${MAIL_PORT:}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
```

端口加密策略在 `MailSenderConfig` 中处理：

- `465`：SSL
- `587`：STARTTLS

生产环境不要把真实邮箱密码写入 `application.yml`，应通过环境变量或部署平台 Secret 注入。

## HTML 邮件模板

面试通知模板集中在：

```text
InterviewNotificationEmailBuilder
```

当前每种通知都有独立 HTML 样式：

| 类型 | 样式重点 |
| --- | --- |
| `BOOKING_SUCCESS` | 蓝色强调，突出“预约已确认” |
| `EVE_REMINDER` | 紫色强调，突出“明日面试提醒” |
| `DAY_REMINDER` | 橙色强调，突出“今日面试提醒” |
| `ADMISSION` | 绿色强调，突出“录取通知”和分配部门 |
| `REJECTION` | 灰色中性样式，表达感谢和结果 |

模板中会把面试信息渲染为表格卡片：

- 面试时间
- 面试地点
- 线上会议链接
- 备注

用户姓名、备注、会议链接、自定义正文等动态内容会做 HTML 转义，避免把用户输入直接当 HTML 注入。

## 预约成功通知

触发点：

- 普通预约成功后：`InterviewBookingServiceImpl`
- 秒杀异步落库成功后：`InterviewBookingConsumer`

调用：

```text
interviewNotificationService.enqueueBookingSuccess(scheduleId, requestId)
```

发送内容来自：

```text
InterviewNotificationEmailBuilder.htmlBody(BOOKING_SUCCESS, ...)
```

邮件包含：

- 面试时间
- 面试地点
- 线上会议链接
- 备注

发送成功后会更新：

```text
interview_schedule.notif_status = 1
```

## 面试提醒

定时任务：`InterviewReminderScheduler`

默认配置：

```yaml
interview:
  notification:
    enabled: true
    eve-reminder-cron: 0 0 12 * * ?
    day-reminder-cron: 0 0 8 * * ?
```

执行时间固定使用：

```text
Asia/Shanghai
```

提醒逻辑：

```text
EVE_REMINDER:
  查询明天 00:00:00 - 23:59:59 的 interview_schedule

DAY_REMINDER:
  查询今天 00:00:00 - 23:59:59 的 interview_schedule
```

只处理：

```text
interview_schedule.status = 1
interview_schedule.interview_time IS NOT NULL
```

每条有效安排会投递一条 `InterviewNotificationMessage` 到 MQ。

## 面试结果通知

入口：

```http
POST /api/interview/result/send-notifications
```

结果通知读取：

```text
interview_result.result_id
interview_result.decision
interview_result.assigned_dept_id
interview_result.schedule_id
```

默认模板只支持：

| decision | 通知类型 |
| --- | --- |
| `1` | `ADMISSION` |
| `2` | `REJECTION` |

如果传了 `customMessage`，会使用自定义正文，并由 `customHtmlBody` 包装成统一 HTML 邮件。自定义内容会被转义，不会作为原始 HTML 执行。邮件主题固定为：

```text
【博远信息技术社】面试结果通知
```

录取通知会带上 `assigned_dept_id` 对应的部门名称。

## 幂等与防重复

发送前会先写入：

```text
interview_notification_log
```

关键字段：

- `notification_type`
- `schedule_id`
- `result_id`
- `recipient_email`
- `sent_at`

唯一索引：

```text
uk_type_schedule(notification_type, schedule_id)
uk_type_result(notification_type, result_id)
```

含义：

- 同一个预约的同一类通知只发送一次。
- 同一个面试结果的同一类通知只发送一次。

如果插入日志时遇到唯一冲突，说明其它线程或之前任务已经发送/占用，当前发送会跳过。

如果邮件发送失败，会删除刚插入的日志，让后续重试有机会再次发送。

## MQ 与失败处理

通知队列：

```text
official.interview.notification
```

死信队列：

```text
official.interview.notification.dlq
```

RabbitMQ 配置中：

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
```

消费者抛异常后会按 RabbitMQ listener 配置重试。重试耗尽后，由 DLX 进入 DLQ。

如果 `outbox.enabled=true`，通知消息会先写入 `message_outbox`，再由 `MessageOutboxRelay` 投递到 RabbitMQ，降低业务事务已提交但 MQ 投递失败的风险。

## 当前限制

- 只有邮件通知，短信/站内信/企业微信还没有实现。
- `interview_schedule.notif_status` 只在预约成功通知后置为 1，提醒和结果通知不更新这个字段。
- 定时提醒在多实例部署时，每个实例都会执行定时任务；依赖通知日志唯一索引避免重复发送，但仍会有重复扫描和重复投递。
- 结果通知接口当前缺少细粒度权限注解，应补 `resume:audit` 或独立通知权限。
- 当前没有通知管理页，DLQ 中失败消息需要运维手动查看。
- 邮件发送日志只记录成功占位，不记录失败原因、SMTP 响应码、重试次数。

## 可完善点

1. 增加通知中心表，记录 `PENDING/SENT/FAILED`、失败原因、重试次数。
2. 增加管理端通知页面，可查看发送状态、失败原因、手动重发。
3. 多实例定时任务加分布式锁，避免重复扫描。
4. 结果通知和提醒通知也更新对应状态字段，或统一迁移到通知中心表。
5. 支持可配置 HTML 模板，允许运营在后台编辑不同通知类型的文案。
6. 增加发送前预览接口，管理员确认后再批量发送结果通知。
7. 增加权限：`interview:notification:send`、`interview:notification:view`、`interview:notification:retry`。
8. 增加邮件退信/失败告警，DLQ 堆积时通知管理员。
