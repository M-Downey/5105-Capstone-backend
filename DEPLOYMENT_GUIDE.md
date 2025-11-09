# 项目部署到服务器完整步骤

## 一、服务器准备

### 1.1 安装 Docker 和 Docker Compose

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 安装 Docker Compose
sudo apt install docker-compose-plugin -y

# 将当前用户添加到 docker 组（避免每次使用 sudo）
sudo usermod -aG docker $USER
newgrp docker

# 验证安装
docker --version
docker compose version
```

### 1.2 创建项目目录

```bash
# 创建项目目录
sudo mkdir -p /opt/rag-app
sudo chown $USER:$USER /opt/rag-app
cd /opt/rag-app
```

## 二、同步项目文件到服务器

### 2.1 从本地同步代码（使用 rsync 或 scp）

```bash
# 从本地项目根目录执行（排除 node_modules、target 等）
rsync -avz --exclude 'node_modules' --exclude 'target' --exclude '.git' \
  --exclude 'frontend/node_modules' --exclude 'frontend/dist' \
  /Users/tony/Documents/00-DSS/04-DSS5105/capstone/ ubuntu@<服务器IP>:/opt/rag-app/

# 或者使用 scp（需要先打包）
tar -czf rag-app.tar.gz --exclude='node_modules' --exclude='target' \
  --exclude='.git' --exclude='frontend/node_modules' --exclude='frontend/dist' .
scp rag-app.tar.gz ubuntu@<服务器IP>:/opt/rag-app/
# 在服务器上解压
ssh ubuntu@<服务器IP> "cd /opt/rag-app && tar -xzf rag-app.tar.gz"
```

### 2.2 或者使用 Git（推荐）

```bash
# 在服务器上
cd /opt/rag-app
git clone <你的仓库地址> .
# 或从本地推送后拉取
```

## 三、配置环境变量

### 3.1 创建 .env 文件

```bash
cd /opt/rag-app
cat > .env << 'EOF'
# MySQL 配置
MYSQL_ROOT_PASSWORD=YourStrongRootPassword123!
MYSQL_DATABASE=rag_chat
MYSQL_USER=rag_user
MYSQL_PASSWORD=YourStrongPassword123!

# OpenAI API Key
OPENAI_API_KEY=sk-proj-your-openai-api-key-here

# 代理配置（如需要）
# HTTP_PROXY=http://proxy.example.com:8080
# HTTPS_PROXY=http://proxy.example.com:8080
# NO_PROXY=localhost,127.0.0.1,backend,mysql,rag-mysql
EOF

# 设置权限
chmod 600 .env
```

## 四、创建持久化数据目录

```bash
cd /opt/rag-app

# 创建数据目录
sudo mkdir -p /data/mysql /data/uploads /data/logs

# 设置权限（确保 Docker 容器可以写入）
sudo chown -R 999:999 /data/mysql  # MySQL 用户
sudo chown -R $USER:$USER /data/uploads /data/logs
sudo chmod -R 755 /data/uploads /data/logs
```

## 五、构建和部署前端

### 5.1 在服务器上构建前端（使用 Docker）

```bash
cd /opt/rag-app

# 使用 Node.js Docker 镜像构建前端
docker run --rm -v "$(pwd)/frontend:/app" -w /app node:20-alpine \
  sh -c "npm ci && npm run build"

# 验证构建结果
ls -la frontend/dist/
```

### 5.2 或者在本地构建后同步

```bash
# 本地构建
cd frontend
npm ci && npm run build

# 同步到服务器
rsync -avz frontend/dist/ ubuntu@<服务器IP>:/opt/rag-app/frontend/dist/
```

## 六、启动 Docker 服务

### 6.1 构建并启动所有服务

```bash
cd /opt/rag-app

# 构建并启动（后台运行）
docker compose up -d --build

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f
```

### 6.2 验证服务运行

```bash
# 检查容器状态
docker compose ps

# 检查后端日志
docker compose logs --tail=100 backend

# 检查 MySQL 日志
docker compose logs --tail=50 mysql

# 检查 Nginx 日志
docker compose logs --tail=50 nginx

# 测试后端 API
curl http://localhost:8080/api/health || echo "Backend not ready yet"

# 测试前端
curl http://localhost:80 | head -20
```

## 七、常用运维命令

### 7.1 服务管理

```bash
# 启动服务
docker compose up -d

# 停止服务
docker compose down

# 重启服务
docker compose restart

# 重启特定服务
docker compose restart backend
docker compose restart nginx

# 查看服务状态
docker compose ps

# 查看资源使用
docker stats
```

### 7.2 日志管理

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f backend
docker compose logs -f mysql
docker compose logs -f nginx

# 查看最近 200 行日志
docker compose logs --tail=200 backend

# 查看实时日志
docker compose logs --follow --tail=50 backend
```

### 7.3 进入容器调试

```bash
# 进入后端容器
docker exec -it rag-backend sh

# 进入 MySQL 容器
docker exec -it rag-mysql mysql -u root -p

# 进入 Nginx 容器
docker exec -it rag-nginx sh
```

### 7.4 数据备份

```bash
# 备份 MySQL 数据
docker exec rag-mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} rag_chat > backup_$(date +%Y%m%d).sql

# 备份上传的文件
tar -czf uploads_backup_$(date +%Y%m%d).tar.gz /data/uploads

# 备份日志
tar -czf logs_backup_$(date +%Y%m%d).tar.gz /data/logs
```

## 八、更新部署

### 8.1 更新后端代码

```bash
cd /opt/rag-app

# 拉取最新代码
git pull  # 或重新同步文件

# 重新构建并重启后端
docker compose up -d --build backend

# 查看日志确认启动成功
docker compose logs -f backend
```

### 8.2 更新前端代码

```bash
cd /opt/rag-app

# 重新构建前端
docker run --rm -v "$(pwd)/frontend:/app" -w /app node:20-alpine \
  sh -c "npm ci && npm run build"

# 重启 Nginx
docker compose restart nginx

# 清除浏览器缓存（用户端）
```

### 8.3 更新数据库（Flyway 迁移）

```bash
# Flyway 会在后端启动时自动执行迁移
# 如果需要手动执行，可以重启后端
docker compose restart backend

# 查看迁移日志
docker compose logs backend | grep -i flyway
```

## 九、故障排查

### 9.1 服务无法启动

```bash
# 检查容器状态
docker compose ps -a

# 查看错误日志
docker compose logs backend
docker compose logs mysql
docker compose logs nginx

# 检查端口占用
sudo netstat -tulpn | grep -E '80|3306|8080'

# 检查磁盘空间
df -h

# 检查 Docker 资源
docker system df
```

### 9.2 数据库连接问题

```bash
# 测试 MySQL 连接
docker exec rag-mysql mysql -u rag_user -p${MYSQL_PASSWORD} rag_chat -e "SELECT 1;"

# 检查 MySQL 日志
docker compose logs mysql | tail -50

# 检查后端数据库连接配置
docker compose logs backend | grep -i "datasource\|mysql\|connection"
```

### 9.3 前端无法访问

```bash
# 检查 Nginx 配置
docker exec rag-nginx cat /etc/nginx/conf.d/default.conf

# 检查前端文件是否存在
ls -la frontend/dist/

# 测试 Nginx
docker exec rag-nginx nginx -t

# 重启 Nginx
docker compose restart nginx
```

### 9.4 OpenAI API 调用失败

```bash
# 检查 API Key 是否配置
docker exec rag-backend env | grep OPENAI_API_KEY

# 检查网络连接（从容器内）
docker exec rag-backend wget -O- https://api.openai.com/v1/models

# 如果使用代理，检查代理配置
docker exec rag-backend env | grep -i proxy

# 查看后端日志中的 API 错误
docker compose logs backend | grep -i "openai\|api\|error"
```

## 十、安全配置

### 10.1 AWS 安全组配置

- 开放端口：
  - `80` (HTTP) - 允许所有来源或特定 IP
  - `443` (HTTPS) - 如果配置了 SSL
  - `22` (SSH) - 仅允许管理员 IP

- 限制端口：
  - `3306` (MySQL) - 不对外开放，仅内部访问
  - `8080` (Backend) - 不对外开放，仅 Nginx 代理访问

### 10.2 防火墙配置（UFW）

```bash
# 允许 SSH
sudo ufw allow 22/tcp

# 允许 HTTP/HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# 启用防火墙
sudo ufw enable

# 查看状态
sudo ufw status
```

### 10.3 SSL/HTTPS 配置（可选）

```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-nginx -y

# 申请证书（需要域名）
sudo certbot --nginx -d yourdomain.com

# 自动续期
sudo certbot renew --dry-run
```

## 十一、监控和维护

### 11.1 查看系统资源

```bash
# 查看容器资源使用
docker stats

# 查看磁盘使用
df -h
du -sh /data/*

# 查看内存使用
free -h

# 查看 CPU 使用
top
```

### 11.2 日志轮转

```bash
# 后端日志由 logback 自动按天滚动
# 清理旧日志（保留 30 天）
find /data/logs -name "*.log.*" -mtime +30 -delete

# 清理 Docker 日志
docker system prune -a --volumes
```

### 11.3 定期备份

```bash
# 创建备份脚本
cat > /opt/rag-app/backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/opt/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# 备份数据库
docker exec rag-mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} rag_chat > $BACKUP_DIR/db_$DATE.sql

# 备份上传文件
tar -czf $BACKUP_DIR/uploads_$DATE.tar.gz /data/uploads

# 删除 7 天前的备份
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete
find $BACKUP_DIR -name "*.tar.gz" -mtime +7 -delete
EOF

chmod +x /opt/rag-app/backup.sh

# 添加到 crontab（每天凌晨 2 点执行）
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/rag-app/backup.sh") | crontab -
```

## 十二、快速部署检查清单

- [ ] Docker 和 Docker Compose 已安装
- [ ] 项目文件已同步到服务器 `/opt/rag-app`
- [ ] `.env` 文件已创建并配置正确
- [ ] 持久化目录已创建 (`/data/mysql`, `/data/uploads`, `/data/logs`)
- [ ] 前端已构建 (`frontend/dist/` 存在)
- [ ] `docker-compose.yml` 配置正确
- [ ] `frontend/nginx.conf` 存在且配置正确
- [ ] 所有服务已启动 (`docker compose ps`)
- [ ] 后端日志无错误 (`docker compose logs backend`)
- [ ] 前端可以访问 (`curl http://localhost`)
- [ ] API 可以访问 (`curl http://localhost/api/...`)
- [ ] AWS 安全组已配置（开放 80/443 端口）
- [ ] 防火墙已配置（如使用 UFW）

## 十三、完整部署命令（一键执行）

```bash
#!/bin/bash
# 完整部署脚本（需要在项目根目录执行）

set -e

echo "=== 开始部署 RAG Chat 应用 ==="

# 1. 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "错误: Docker 未安装"
    exit 1
fi

# 2. 创建数据目录
echo "创建数据目录..."
sudo mkdir -p /data/mysql /data/uploads /data/logs
sudo chown -R 999:999 /data/mysql
sudo chown -R $USER:$USER /data/uploads /data/logs

# 3. 检查 .env 文件
if [ ! -f .env ]; then
    echo "错误: .env 文件不存在，请先创建"
    exit 1
fi

# 4. 构建前端
echo "构建前端..."
docker run --rm -v "$(pwd)/frontend:/app" -w /app node:20-alpine \
  sh -c "npm ci && npm run build"

# 5. 构建并启动服务
echo "启动 Docker 服务..."
docker compose up -d --build

# 6. 等待服务启动
echo "等待服务启动..."
sleep 10

# 7. 检查服务状态
echo "检查服务状态..."
docker compose ps

# 8. 查看日志
echo "查看后端日志..."
docker compose logs --tail=50 backend

echo "=== 部署完成 ==="
echo "访问地址: http://$(hostname -I | awk '{print $1}')"
```

---

**注意事项：**

1. 所有路径都基于 `/opt/rag-app` 项目目录
2. 数据持久化在 `/data/` 目录，不会因容器重启而丢失
3. 前端构建产物在 `frontend/dist/`，需要先构建才能访问
4. `.env` 文件包含敏感信息，不要提交到 Git
5. 定期备份数据库和上传文件
6. 监控日志和系统资源使用情况

