# Docker 部署指南

本文档介绍如何使用 Docker Compose 在服务器上部署本项目（后端 + MySQL + Nginx/前端）。

## 目录结构

```
capstone/
├─ docker-compose.yml
├─ docker/
│  └─ Dockerfile
├─ deploy/
│  ├─ nginx.conf
│  └─ env.example  (复制为 .env 使用)
├─ src/main/resources/logback-spring.xml
└─ frontend-dist/  (放置前端构建产物 dist 内容)
```

## 一、准备 .env

复制 `deploy/env.example` 为 `.env` 并填入实际值（与 `docker-compose.yml` 同目录）。

```
cp deploy/env.example .env
# 编辑 .env
```

.env 变量：
- OPENAI_API_KEY=sk-xxxx
- MYSQL_ROOT_PASSWORD=StrongPassw0rd!
- MYSQL_DATABASE=ragdb
- MYSQL_USER=rag
- MYSQL_PASSWORD=ragpass

## 二、准备前端静态文件

在本地构建前端后，将 `dist` 内容上传到服务器 `frontend-dist/`：

```
# 本地
cd frontend
npm ci && npm run build

# 服务器项目根目录下确保有 frontend-dist
mkdir -p frontend-dist
# 将本地 dist/* 同步到服务器 frontend-dist
```

## 三、一键启动

在服务器项目根目录执行：

```
docker compose up -d --build
```

首次会：
- 拉取 `mysql:8.0`，初始化数据库
- 按 Dockerfile 多阶段构建后端镜像并运行
- 启动 Nginx，托管前端并反代 `/api`

## 四、持久化目录

- MySQL 数据：`./data/mysql`
- 上传目录：`./data/uploads`
- 日志：`./data/logs/app.log`（按天滚动）

## 五、常用运维命令

```
# 查看状态
docker compose ps

# 查看日志
docker compose logs -f backend

docker compose logs -f mysql

docker compose logs -f nginx

# 进入容器
docker exec -it rag-backend sh

# 重启服务
docker compose restart backend

# 停止/启动
docker compose down

docker compose up -d
```

## 六、环境变量覆盖说明

- `APP_RAG_UPLOAD_DIR` 默认为 `/app/uploads`，可通过环境变量覆盖。
- `SPRING_DATASOURCE_*` 通过环境变量注入，覆盖 `application.yml`。
- 日志由 `logback-spring.xml` 输出到 `/app/logs/app.log` 并按天滚动。

## 七、访问方式

- 前端：http://<服务器IP或域名>
- 后端 API：`http://<服务器IP或域名>/api/...`

## 八、HTTPS（可选）

建议为 Nginx 配置 HTTPS：
- 使用 certbot 申请证书并配置到 Nginx（修改 `deploy/nginx.conf`）
- 将 443 端口映射到宿主机

---
如需将健康检查改为 `/actuator/health`，请在 `pom.xml` 增加 `spring-boot-starter-actuator` 依赖，并在 compose 中加入 backend 健康检查。


