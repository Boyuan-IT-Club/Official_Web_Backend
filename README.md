# Official 项目

### 目标用户
- 管理员
- 普通用户

### 核心功能
- 用户管理：注册、登录、修改信息、重置密码
- 认证与权限：基于 Spring Security 的 JWT 认证机制
- 奖项经验管理：管理员可管理用户的奖项经验信息
- 邮件服务：用于发送验证码或通知
- Redis 缓存：用于验证码、Token 等缓存管理

## 技术架构

### 后端技术栈
- Spring Boot 3.5.3
- MyBatis 3.5.15 + MySQL 8.0.33
- Spring Security + JWT (jjwt 0.11.5)
- Redis
- Lombok, Validation, Mail, Configuration Processor
- Java 17

### 架构模式
- MVC 模式：Spring Boot 默认使用 MVC 架构
- 模板方法模式：在全局异常处理中使用
- 工厂模式：Spring IOC 容器管理 Bean 创建
- 过滤器模式：JWT 认证通过自定义过滤器实现

### 主要组件交互
- Controller 接收请求，调用 Service 层处理业务逻辑
- Service 层调用 Mapper 层访问数据库
- Redis 用于缓存 Token 和验证码
- SecurityConfig 配置安全策略，JwtAuthenticationFilter 实现 Token 校验

## 开发环境

### 必需工具
- JDK 17
- Maven 3.x
- IDE（如 IntelliJ IDEA）

### 可选工具
- Postman（接口测试）
- Redis Desktop Manager（缓存调试）

### 项目目录结构
```
src/
├── main/
│   ├── java/
│   │   └── club/
│   │       └── boyuan/
│   │           └── official/
│   │               ├── config/          # 配置类
│   │               ├── controller/      # REST API 接口
│   │               ├── dto/             # 数据传输对象
│   │               ├── entity/          # 实体类
│   │               ├── exception/       # 异常处理
│   │               ├── filter/          # 自定义过滤器
│   │               ├── mapper/          # MyBatis Mapper
│   │               ├── service/         # 业务逻辑接口及实现
│   │               └── utils/           # 工具类
│   └── resources/
│       ├── mapper/                      # MyBatis XML 映射文件
│       └── application.yml              # 主配置文件
└── test/                                # 测试代码
```

## 安装与运行

### 环境准备
1. 安装 JDK 17
2. 安装 Maven 3.x
3. 安装 MySQL 8.0+
4. 安装 Redis


## 安全说明

### 认证机制
项目使用 JWT (JSON Web Token) 实现无状态认证，用户登录成功后会获得一个 token，后续请求需要在 Header 中携带该 token。

### 权限控制
- 普通用户只能操作自己的数据
- 管理员用户可以操作所有用户的数据
- 敏感操作（如角色修改）需要管理员权限

### 密码策略
- 密码长度必须在8-20个字符之间
- 密码必须包含大小写字母、数字和特殊字符中的至少三种
- 密码在数据库中使用 BCrypt 加密存储
- 注册时需要确认密码
- 重置密码时需要验证码验证

示例强密码：
- `MyPassword123!`
- `SecurePass@2023`
- `P@ssw0rd2023#`

不符合要求的密码示例：
- `password` (缺少大写字母、数字和特殊字符)
- `PASSWORD123` (缺少小写字母和特殊字符)
- `Password` (缺少数字和特殊字符)
- `Pass123` (长度不足且缺少特殊字符)

### 其他安全措施
- 敏感操作需要通过验证码验证身份
- 所有密码都经过加密存储
- 通过日志记录关键操作，便于审计

