FROM node:20-alpine

WORKDIR /app

# 先装依赖再拷源码，改代码时不必重新下载依赖
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev

COPY src ./src

ENV NODE_ENV=production
EXPOSE 3100

# 以非 root 运行：协同服务直连 MySQL，权限越小越好
USER node

CMD ["node", "src/index.js"]
