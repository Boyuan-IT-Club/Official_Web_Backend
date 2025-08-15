# Official 项目

## 项目简介

Official 是一个基于 Spring Boot 的后端服务系统，第一阶段目标是实现社团介绍和简历投递的基本功能。

主要功能包括：
- 用户管理：注册、登录、修改信息、重置密码
- 认证与权限：基于 Spring Security 的 JWT 认证机制
- 简历投递：支持学生使用特定邮箱验证并投递简历
- 面试安排：自动或手动分配面试时间
- 奖项经验管理：管理员可管理用户的奖项经验信息
- 全局搜索：支持用户和奖项信息的全局搜索

## 技术架构

### 后端技术栈
- Spring Boot 3.5.3
- MyBatis 3.5.15 + MySQL 8.0+
- Spring Security + JWT (jjwt 0.11.5)
- Redis
- Java 17

### 开发环境
- JDK 17
- Maven 3.x
- IDE（如 IntelliJ IDEA）

## 安装与运行

### 开发环境运行

#### 使用Makefile (推荐，特别适用于前端开发人员)

```bash
# 启动依赖服务 (MySQL和Redis)
make dev-up

# 查看服务状态
make status

# 停止服务
make dev-down
```

然后在IDE中运行 [OfficialApplication.java](file:///C:/Users/35183/IdeaProjects/Official/src/main/java/club/boyuan/official/OfficialApplication.java) 文件中的 main 方法启动应用。

#### 传统方式
```bash
# 构建项目
./mvnw clean package

# 运行项目
./mvnw spring-boot:run
```

或者直接运行 [OfficialApplication.java](file:///C:/Users/35183/IdeaProjects/Official/src/main/java/club/boyuan/official/OfficialApplication.java) 文件中的 main 方法

## API 接口文档

本项目使用 Apifox 管理 API 接口文档，不再通过依赖方式生成文档。

## 部署说明

### Docker 部署（推荐）
```bash
# 构建项目
./mvnw clean package -DskipTests

# 启动服务
docker-compose up -d
```

### 传统部署
1. 安装 JDK 17、MySQL 8.0+、Redis 6.0+
2. 创建数据库：CREATE DATABASE official CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
3. 修改配置文件 application-prod.yml
4. 构建项目：./mvnw clean package -DskipTests
5. 运行应用：java -jar target/Official-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

## 第一阶段功能说明

### 简历投递功能
- 仅支持特定邮箱验证
- 简历包含个人简介、获奖情况等板块
- 支持自动分配面试时间或手动调度

### 面试安排
- 系统可根据规则自动分配面试时间
- 支持管理员手动调整面试时间
- 面试时间确认后通知相关人员