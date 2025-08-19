# Official 项目 Makefile
# 用于前端开发人员快速启动后端测试环境

# 项目名称，默认为official
PROJECT_NAME ?= official
DOCKERHUB_USERNAME ?= boyuanclub

# 默认目标
.PHONY: help
help: ## 显示帮助信息
	@echo "Official 后端测试环境 Makefile"
	@echo ""
	@echo "使用方法:"
	@echo "  make [target]"
	@echo ""
	@echo "目标列表:"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.PHONY: dev-up
dev-up: ## 启动开发环境 (Docker方式)
	PROJECT_NAME=${PROJECT_NAME} docker-compose up -d mysql redis
	@echo "MySQL和Redis已启动，端口分别为3306和6379"
	@echo "请使用IDE运行OfficialApplication.java启动应用"

.PHONY: dev-down
dev-down: ## 停止开发环境
	PROJECT_NAME=${PROJECT_NAME} docker-compose down
	@echo "开发环境已停止"

.PHONY: dev-logs
dev-logs: ## 查看开发环境日志
	PROJECT_NAME=${PROJECT_NAME} docker-compose logs -f

.PHONY: test-up
test-up: ## 启动完整测试环境 (包含应用)
	./mvnw clean package -DskipTests
	PROJECT_NAME=${PROJECT_NAME} DOCKERHUB_USERNAME=${DOCKERHUB_USERNAME} docker-compose up -d
	@echo "完整测试环境已启动，后端服务运行在8080端口"

.PHONY: test-down
test-down: ## 停止测试环境
	PROJECT_NAME=${PROJECT_NAME} DOCKERHUB_USERNAME=${DOCKERHUB_USERNAME} docker-compose down
	@echo "测试环境已停止"

.PHONY: local-up
local-up: ## 本地启动应用 (需要本地安装JDK等环境)
	./mvnw spring-boot:run
	@echo "应用已在本地启动，运行在8080端口"

.PHONY: build
build: ## 构建项目
	./mvnw clean package -DskipTests
	@echo "项目构建完成"

.PHONY: status
status: ## 查看服务状态
	PROJECT_NAME=${PROJECT_NAME} DOCKERHUB_USERNAME=${DOCKERHUB_USERNAME} docker-compose ps

.PHONY: init-db
init-db: ## 初始化数据库 (执行schema.sql和data.sql)
	@echo "请确保MySQL服务正在运行"
	@echo "执行数据库初始化脚本..."
	PROJECT_NAME=${PROJECT_NAME} docker exec ${PROJECT_NAME}-mysql mysql -u root -proot ${DB_NAME:-official} < ./db/official.sql
	@echo "数据库初始化完成"

# 新增部署相关命令
.PHONY: deploy
deploy: ## 部署应用到Docker Hub (需要先docker login)
	@echo "正在构建并部署镜像到Docker Hub..."
	DOCKERHUB_USERNAME=${DOCKERHUB_USERNAME} ./deploy.sh

.PHONY: deploy-tag
deploy-tag: ## 使用指定标签部署应用到Docker Hub (需要先docker login)
	@echo "请输入标签名称:"
	@read tag; \
	 DOCKERHUB_USERNAME=${DOCKERHUB_USERNAME} ./deploy.sh $$tag

.PHONY: update
update: ## 在服务器上更新应用 (从Docker Hub拉取最新镜像)
	@echo "正在从Docker Hub更新应用..."
	DOCKERHUB_USERNAME=${DOCKERHUB_USERNAME} ./update.sh