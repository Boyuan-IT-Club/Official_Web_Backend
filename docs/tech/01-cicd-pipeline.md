# CI/CD 流水线配置技术方案

## 目标

为后端项目建立稳定的自动化流水线，覆盖以下环节：

1. PR 阶段自动校验代码可编译、测试可运行。
2. 合并到主分支后自动构建 Docker 镜像并推送到 Docker Hub。
3. 可选：自动部署到服务器或至少生成可部署镜像版本。
4. 敏感配置不进入仓库，全部通过 GitHub Secrets 或服务器环境变量注入。

## 当前现状

仓库已有 `.github/workflows/docker-push.yml`，当前能力如下：

- 触发条件：`main` 分支 push、`v*.*.*` tag push、手动触发。
- 构建步骤：checkout、JDK 17、`./mvnw clean package -DskipTests`。
- 镜像步骤：Docker Buildx、登录 Docker Hub、构建并推送 `boyuanclub/official-core-api:latest` 和语义化版本号。
- 验证步骤：通过 Docker Hub API 检查版本 tag 是否存在。

现有不足：

- PR 阶段没有强制跑编译和测试。
- 主分支构建跳过测试，无法提前发现回归。
- 版本号依赖 Docker Hub 查询和自增，容易受到 Docker Hub API 结果影响。
- 没有自动部署步骤。
- `docker-compose.yml` 当前仍存在默认密钥示例，生产环境必须改为 Secrets/服务器环境变量。

## 建议流水线拆分

建议拆成 3 条 workflow：

1. `ci.yml`：PR 和 push 都运行，负责质量校验。
2. `docker-publish.yml`：只在 main/tag 上运行，负责镜像构建和推送。
3. `deploy.yml`：手动或 main/tag 后运行，负责服务器部署。

## PR 校验流水线

触发：

```yaml
on:
  pull_request:
    branches: [ main, feat/feishu-sync-notification ]
  push:
    branches: [ main, feat/**, fix/** ]
```

建议步骤：

```yaml
jobs:
  backend-ci:
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8
        env:
          MYSQL_ROOT_PASSWORD: root
          MYSQL_DATABASE: official
        ports:
          - 3307:3306
        options: >-
          --health-cmd="mysqladmin ping -h 127.0.0.1 -uroot -proot"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=10
      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
      rabbitmq:
        image: rabbitmq:3-management-alpine
        env:
          RABBITMQ_DEFAULT_USER: root
          RABBITMQ_DEFAULT_PASS: root
        ports:
          - 5672:5672

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: temurin
          cache: maven
      - run: chmod +x mvnw
      - name: Compile
        run: ./mvnw -DskipTests compile
      - name: Test
        run: ./mvnw test
        env:
          MYSQL_PORT: 3307
          DB_USERNAME: root
          DB_PASSWORD: root
          JWT_SECRET: ${{ secrets.CI_JWT_SECRET }}
          FEISHU_APP_ID: ''
          FEISHU_APP_SECRET: ''
```

如果当前测试仍依赖本地数据库或存在不稳定测试，第一阶段可以先强制 `compile`，再逐步修复测试后打开 `test` 作为必选门禁。

## Docker 镜像发布

保留现有 `docker-push.yml` 的主体逻辑，但建议调整：

- `./mvnw clean package -DskipTests` 改为依赖 PR CI 成功后再运行，或者在发布阶段使用 `./mvnw clean package`。
- tag 规则建议使用：
  - tag push：`v1.2.3` -> 镜像 tag `1.2.3` 和 `latest`
  - main push：`main-${{ github.sha }}` 和 `latest`
  - PR：只构建不推送
- Docker Hub 凭证只使用 `DOCKERHUB_TOKEN`。

示例 tag：

```yaml
tags: |
  boyuanclub/official-core-api:latest
  boyuanclub/official-core-api:${{ github.sha }}
```

## 部署流水线

推荐方式：GitHub Actions 通过 SSH 登录服务器，执行：

```bash
cd /srv/official
docker compose pull official
docker compose up -d official
docker image prune -f
```

需要的 GitHub Secrets：

- `DOCKERHUB_TOKEN`
- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_SSH_KEY`
- `PROD_ENV_FILE` 或服务器本地维护 `.env`

生产服务器 `.env` 至少包含：

```env
DB_ROOT_PASSWORD=...
DB_USERNAME=root
DB_PASSWORD=...
JWT_SECRET=...
MAIL_USERNAME=...
MAIL_PASSWORD=...
RABBITMQ_USERNAME=...
RABBITMQ_PASSWORD=...
FEISHU_APP_ID=...
FEISHU_APP_SECRET=...
OUTBOX_ENABLED=true
```

## 完成清单

- 新增 `ci.yml`，先跑 `compile`，条件成熟后跑 `test`。
- 调整 `docker-push.yml` 的 tag 策略和测试策略。
- 为生产部署准备 `.env.example`，真实 `.env` 不入库。
- 清理 `docker-compose.yml` 中的真实默认密钥，改为无默认值或占位值。
- 给 main 分支开启 branch protection：PR 必须通过 CI 才能合并。
- 可选：增加 Dependabot、CodeQL、镜像漏洞扫描。
