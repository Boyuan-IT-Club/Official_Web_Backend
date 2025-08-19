#!/bin/bash

# 部署脚本 - 构建并推送 Docker 镜像到 Docker Hub
# 使用方法:
# ./deploy.sh                    # 使用 latest 标签
# ./deploy.sh v1.0.0             # 使用指定标签

set -e  # 遇到错误时停止执行

# 设置默认值
DOCKERHUB_USERNAME=${DOCKERHUB_USERNAME:-boyuanclub}
IMAGE_NAME="official"
TAG=${1:-latest}

echo "========== 开始部署流程 =========="
echo "Docker Hub 用户名: $DOCKERHUB_USERNAME"
echo "镜像名称: $IMAGE_NAME"
echo "标签: $TAG"
echo "================================"

# 1. 清理并打包项目
echo "步骤 1: 清理并打包项目..."
./mvnw clean package -DskipTests

# 2. 构建 Docker 镜像（Dockerfile中会从target目录复制JAR包）
echo "步骤 2: 构建 Docker 镜像..."
docker build -t $DOCKERHUB_USERNAME/$IMAGE_NAME:$TAG .

# 3. 推送到 Docker Hub
echo "步骤 3: 推送到 Docker Hub..."
docker push $DOCKERHUB_USERNAME/$IMAGE_NAME:$TAG

echo "========== 部署完成 =========="
echo "镜像已成功推送到 Docker Hub: $DOCKERHUB_USERNAME/$IMAGE_NAME:$TAG"
echo "在服务器上运行以下命令以更新应用:"
echo "DOCKERHUB_USERNAME=$DOCKERHUB_USERNAME docker-compose pull && DOCKERHUB_USERNAME=$DOCKERHUB_USERNAME docker-compose up -d"
echo "=============================="