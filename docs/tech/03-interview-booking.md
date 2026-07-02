# 面试预约说明

## 当前结论

当前面试预约已经改为：学生可以提交一个或多个可面试大时段，后端根据简历里的志愿部门，在这些候选时段中分配最终面试时间和地点。

最终仍然只生成一条有效 `interview_schedule`，不会因为学生选择多个候选时段而占用多个名额。

分配优先级：

1. 在学生提交的 `slotIds` 中，优先找 `interview_slot.dept_id` 匹配第一志愿部门的 slot。
2. 第一志愿没有可用名额时，继续匹配第二志愿等后续志愿。
3. 志愿部门 slot 都不可用时，尝试 `dept_id IS NULL` 的共享/调剂 slot。
4. 仍不可用时，才在学生提交的候选 slot 中调剂到任意有容量的 slot。
5. 所有候选 slot 都满了，则返回“所选面试时段均已约满”。

地点仍来自最终命中的 `interview_slot.location`，精确到场时间仍由 `InterviewFineSlotTimeService` 按 slot 容量均分计算。

## 核心数据模型

### interview_slot

表示可预约大时段，包含：

- `slot_id`
- `cycle_id`
- `interview_date`
- `start_time`
- `end_time`
- `location`
- `dept_id`
- `interview_type`
- `meeting_link`
- `max_capacity`
- `current_occupied`
- `feishu_table_url`
- `status`

`dept_id` 表示该面试场归属部门。为空时表示共享/调剂面试场。

### interview_schedule

表示学生最终面试安排，包含：

- `schedule_id`
- `resume_id`
- `cycle_id`
- `slot_id`
- `preferred_slot_ids`
- `assigned_dept_id`
- `interview_time`
- `status`
- `notes`
- `sync_status`
- `notif_status`
- `feishu_record_id`

其中：

- `slot_id` 是最终分配到的面试场。
- `preferred_slot_ids` 保存学生提交的候选时段，JSON 数组字符串。
- `assigned_dept_id` 保存本次安排按哪个部门志愿分配。
- `interview_time` 是后端按最终 slot 容量均分后得到的精确到场时间。

## 接口

### 查询可预约时段

```http
GET /api/interview/booking/cycles/{cycleId}/segments?resumeSubmittedOnly=true
Authorization: Bearer <token>
```

返回每个 slot 的日期、时间、地点、部门、容量和是否约满。

### 提交预约

兼容旧格式，只传一个 `slotId`：

```http
POST /api/interview/booking
Authorization: Bearer <token>

{
  "cycleId": 2,
  "slotId": 55,
  "notes": "可选备注"
}
```

新格式，提交多个候选时段：

```http
POST /api/interview/booking
Authorization: Bearer <token>

{
  "cycleId": 2,
  "slotIds": [55, 56, 60],
  "notes": "可选备注"
}
```

后端会读取简历字段 `expected_departments`，按志愿部门从 `slotIds` 中选择最终 slot。

响应中的关键字段：

```json
{
  "scheduleId": 1001,
  "cycleId": 2,
  "slotId": 56,
  "preferredSlotIds": [55, 56, 60],
  "assignedDeptId": 3,
  "interviewTime": "2026-03-27T10:12:00",
  "location": "技术部面试场",
  "slotDeptId": 3
}
```

### 秒杀预约

```http
POST /api/interview/booking/seckill
```

秒杀预约仍只支持单个 `slotId`。原因是秒杀链路使用 Redis Lua 对一个具体 slot 做库存预扣，不适合同时提交多个候选时段。

普通 `POST /api/interview/booking` 在 `booking.seckill.enabled=true` 时：

- 单个 slot：仍走秒杀异步链路。
- 多个 slot：走新的同步分配逻辑。

### 查询本人预约

```http
GET /api/interview/booking/my?cycleId=2
```

未预约时 `data=null`。

### 改期

兼容旧格式：

```http
PUT /api/interview/booking/{bookingId}

{
  "slotId": 56,
  "notes": "可选备注"
}
```

新格式：

```http
PUT /api/interview/booking/{bookingId}

{
  "slotIds": [56, 60, 61],
  "notes": "可选备注"
}
```

改期会重新按志愿部门选择最终 slot。若新候选 slot 都不可用，原预约不会主动取消。

### 取消预约

```http
DELETE /api/interview/booking/{bookingId}
```

取消后释放最终 `slot_id` 的名额，`interview_schedule.status=2`。

## 分配实现

核心代码在：

- `InterviewBookingServiceImpl#createOrUpdateBooking`
- `InterviewBookingServiceImpl#buildAssignmentPlan`
- `InterviewBookingServiceImpl#orderCandidatesByDepartment`
- `InterviewBookingServiceImpl#resolvePreferredDepartmentIds`

部门解析逻辑：

1. 从 `resume_field_definition` 找 `期望部门` 字段。
2. 从 `resume_field_value` 读取该简历的志愿部门值。
3. 支持按 `department.dept_id`、`department.dept_name`、`department.dept_code` 匹配。
4. 忽略已禁用部门。

最终占坑逻辑：

1. 对排序后的候选 slot 逐个尝试占用。
2. 使用 `InterviewSlotInventoryService` 执行 DB 原子占坑。
3. 占坑成功后写入 `interview_schedule`。
4. 用最终 slot 的 `current_occupied` 计算 `interview_time`。

## 精确时间计算

预约粒度仍是大时段。例如：

- 日期：2026-03-27
- 开始：09:00
- 结束：12:00
- 容量：30

后端按容量均分小时间段：

```text
interview_time = startTime + (endTime - startTime) / maxCapacity * (占坑序号 - 1)
```

这样第 1 个预约是 09:00，第 2 个预约按间隔后移，避免所有人同一时间到场。

## 管理端配置要求

管理端创建/编辑 slot 时需要配置：

- `location`：面试地点，例如“技术部面试场”。
- `dept_id`：该地点归属部门。
- `dept_id=null`：共享/调剂场。

如果 slot 没有配置 `dept_id`，后端无法判断它属于哪个志愿部门，只会把它当共享/调剂候选。

## 可完善点

- 如果一个 slot 需要服务多个部门，可后续新增 `interview_slot_department` 关联表。
- 可在查询可预约时段时增加“推荐 slot”字段，提示学生哪些时段更匹配自己的志愿。
- 秒杀链路如需支持多候选 slot，需要把 Redis 预扣从单 key 改成候选集合原子选择。
- 飞书导出可增加 `assigned_dept_id` 对应的部门名称、候选时段等字段。
