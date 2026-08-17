# 本地运行手册(评测功能联调)

> 面向本地把整个官网(后端 + 前端)跑起来、看 Autograder 评测功能。代码在 `dev` 分支。

## 后端需要三个中间件

Spring Boot 启动依赖 **MySQL + Redis + RabbitMQ**,缺一个起不来。

**方案 A:Docker Desktop(推荐,最省事)**
```bash
cd Official_Web_Backend
docker compose up mysql redis rabbitmq -d   # 只起中间件,应用由下面 mvnw 跑
# 首次会自动建库?否——若 official 库不存在,先建:
docker exec -it official-mysql mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS official;"
```

**方案 B:无 Docker,本机装**
- MySQL 8:`3307` 端口,建库 `official`,账号 `root/root`(或改 env `DB_USERNAME/DB_PASSWORD`)
- Redis:`6379`
- RabbitMQ:`5672`,账号 `root/root`

## 启动后端

```bash
cd Official_Web_Backend
git checkout dev
cp .env.example .env   # 已有则跳过
# 关键 env:JWT_SECRET(必须,否则登录签发失败)、DB_*、REDIS_PORT
set -a; source .env; set +a          # 或手动 export JWT_SECRET=...
./mvnw spring-boot:run
```
启动时 Flyway 自动跑 V1–V11 迁移(含 evaluation_submission 表 + evaluation:view 权限)。

## 注入评测种子数据(模拟 Actions 推送)

后端起来后,另开终端:
```bash
./scripts/seed/seed-evaluation.sh http://localhost:8080
# 注入 7 份加密报告:alice×4(205→265→310→340)、bob、carol、dave
```
> 这些是**工具仓真跑生成的加密报告**,走的就是生产同一条 POST /api/public/evaluations 链路。

## 启动前端(两个产物都要可看评测)

```bash
cd Official_Web_Frontend
git checkout dev
npm install
```
**用户端**(看"我的评测"):默认 dev 构建指向 `https://official.boyuan.club`,本地要指到后端:
```bash
REACT_APP_API_URL=http://localhost:8080 npm start   # 开发服务器,热更新
# 或:REACT_APP_API_URL=http://localhost:8080 CI=false npm run build:user && serve -s build-user
```
**管理端**(看 autograding 菜单/总览/榜单/认领):
```bash
REACT_APP_API_URL=http://localhost:8080 npm run start:admin   # 若 package.json 无此脚本,用 REACT_APP_MODE 区分,见 craco.config.js
```

## 看评测功能

1. **管理端** `/evaluations`(autograding 菜单):看到 alice/bob/carol/dave 4 个候选人,都是"未认领";榜单按最高分排序(bob 380 第一);点进行看提交历史 + 报告明细;可用"认领"把 alice 关联到某用户。
2. **用户端** `/main/evaluations`:登录一个账号 → `/main/person` 绑定 GitHub 账号为 `alice` → 回 `/main/evaluations` 看最新卡 + 趋势图(205→265→310→340)+ 历史(行展开看检查项)。绑定会自动回填认领 alice 的 4 份提交。

## 常见问题

- **429**:限频规则 IP 60/60s,连发太多会拒;等 1 分钟或重启 Redis 清 key。
- **登录 401**:`JWT_SECRET` 未设或与签发不一致。
- **RabbitMQ 连不上**:应用启动即失败,先 `docker compose up rabbitmq`。
- **前端 404/接口不通**:确认 `REACT_APP_API_URL` 指向 8080,且后端已起。