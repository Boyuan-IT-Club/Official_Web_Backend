# 消息队列用处说明

## 当前使用目标

项目使用 RabbitMQ 解决以下问题：

1. 高并发面试预约的异步落库，削峰填谷。
2. 邮件发送异步化，避免 SMTP 阻塞 HTTP 请求。
3. 飞书同步异步化，避免飞书 API 慢请求阻塞管理端。
4. 面试通知异步化，避免通知失败影响主业务事务。
5. 配合 Outbox 保证“业务落库”和“消息投递”最终一致。

## 队列与用途

当前在 `RabbitMQConfig` 中声明 4 个业务队列：

| 队列 | 用途 |
| --- | --- |
| `official.email.verification` | 邮箱验证码发送 |
| `official.interview.booking.persist` | 秒杀预约异步落库 |
| `official.interview.notification` | 面试预约成功、提醒、录取/未录取邮件 |
| `official.feishu.sync` | 飞书 PUSH/PULL 同步任务 |

同时为每个业务队列创建 DLQ：

```text
official.email.verification.dlq
official.interview.booking.persist.dlq
official.interview.notification.dlq
official.feishu.sync.dlq
```

死信交换机：

```text
official.dlx
```

当消费者重试耗尽，并且 `default-requeue-rejected=false` 时，消息进入对应 DLQ。

## 统一投递入口

当前统一通过 `ReliableMessagePublisher` 投递业务消息：

- `publishBookingPersist`
- `publishFeishuSync`
- `publishInterviewNotification`
- `publishEmailVerification`

它根据 `outbox.enabled` 决定：

- `false`：直接投递 RabbitMQ。
- `true`：先写 `message_outbox`，由定时 relay 投递 RabbitMQ。

## Transactional Outbox

### 解决的问题

如果业务事务成功但 MQ 投递失败，会出现“数据库已更新但异步任务没执行”的不一致。

Outbox 模式把消息作为一条数据库记录写入同一个事务：

```text
业务表更新 + message_outbox 插入
```

事务提交后，`MessageOutboxRelay` 定时扫描 `PENDING` 记录并投递 RabbitMQ。

### 表结构

Flyway `V2__message_outbox.sql` 创建表：

- `aggregate_type`
- `aggregate_id`
- `event_type`
- `payload`
- `status`
- `retry_count`
- `last_error`
- `created_at`
- `sent_at`

唯一键：

```text
uk_outbox_event (aggregate_type, aggregate_id, event_type)
```

用于消息幂等。

### 状态

```text
0 = PENDING
1 = SENT
2 = FAILED
```

## 消费者说明

### 邮箱验证码

生产者：`EmailVerificationProducer`
消费者：`EmailVerificationConsumer`

特点：

- 消息带 `messageId`。
- Redis `email:verify:processed:{messageId}` 做消费幂等。
- 发送失败会删除幂等标记，让消息重试可重新发送。

### 面试预约落库

生产者：`InterviewBookingProducer`
消费者：`InterviewBookingConsumer`

特点：

- 秒杀入口先用 Redis Lua 预扣库存。
- MQ 消费者批量消费，最多 100 条一批。
- `InterviewBookingAsyncPersistenceService.persistBatch` 批量落库，降低 `interview_slot` 行锁压力。
- Redis `booking:seckill:processed:{requestId}` 做消费幂等。
- 成功后更新 request 状态为 `SUCCESS`，失败后回滚 Redis 预扣库存。

### 面试通知

生产者：`InterviewNotificationProducer`
消费者：`InterviewNotificationConsumer`

用途：

- 预约成功通知。
- 面试前一天提醒。
- 面试当天提醒。
- 录取/未录取通知。

通知日志表：`interview_notification_log`，防止重复发送。

### 飞书同步

生产者：`FeishuSyncProducer`
消费者：`FeishuSyncConsumer`

消息只携带 `taskId`，具体任务参数存 Redis：

```text
official:feishu:sync:task:{taskId}
```

消费者拿到 taskId 后调用 `InterviewFeishuImportService.runImportTask`。

## 配置项

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
          initial-interval: 2000ms
          multiplier: 2

outbox:
  enabled: false
  relay-interval-ms: 1000
  batch-size: 50
  max-retries: 8
```

生产建议：

```yaml
outbox:
  enabled: true
```

## 使用说明

本地启动依赖：

```bash
docker compose up -d mysql redis rabbitmq
```

RabbitMQ 管理后台：

```text
http://localhost:15672
```

检查重点：

- 业务队列是否堆积。
- DLQ 是否有消息。
- Outbox 表是否有大量 `PENDING` 或 `FAILED`。

## 可完善点

- 为 DLQ 增加管理端重放接口。
- 给每类消息增加统一 traceId，贯穿 HTTP、Outbox、MQ、消费者日志。
- 对飞书同步任务增加更细粒度错误分类。
- Outbox relay 支持多实例并发安全锁或数据库 `FOR UPDATE SKIP LOCKED`。
- 将 RabbitMQ 队列、DLQ、重试参数写入运维文档和告警规则。
