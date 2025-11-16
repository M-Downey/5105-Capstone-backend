# Complete Server Deployment Guide

## I. Server Preparation

### 1.1 Install Docker and Docker Compose

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Install Docker Compose
sudo apt install docker-compose-plugin -y

# Add current user to docker group (to avoid using sudo each time)
sudo usermod -aG docker $USER
newgrp docker

# Verify installation
docker --version
docker compose version
```

### 1.2 Create Project Directory

```bash
# Create project directory
sudo mkdir -p /opt/rag-app
sudo chown $USER:$USER /opt/rag-app
cd /opt/rag-app
```

## II. Sync Project Files to Server

### 2.1 Sync Code from Local (using rsync or scp)

```bash
# Execute from local project root (exclude node_modules, target, etc.)
rsync -avz --exclude 'node_modules' --exclude 'target' --exclude '.git' \
  --exclude 'frontend/node_modules' --exclude 'frontend/dist' \
  /Users/tony/Documents/00-DSS/04-DSS5105/capstone/ ubuntu@<server-ip>:/opt/rag-app/

# Or use scp (requires packaging first)
tar -czf rag-app.tar.gz --exclude='node_modules' --exclude='target' \
  --exclude='.git' --exclude='frontend/node_modules' --exclude='frontend/dist' .
scp rag-app.tar.gz ubuntu@<server-ip>:/opt/rag-app/
# Extract on server
ssh ubuntu@<server-ip> "cd /opt/rag-app && tar -xzf rag-app.tar.gz"
```

### 2.2 Or Use Git (Recommended)

```bash
# On server
cd /opt/rag-app
git clone <your-repository-url> .
# Or pull after pushing from local
```

## III. Configure Environment Variables

### 3.1 Create .env File

```bash
cd /opt/rag-app
cat > .env << 'EOF'
# MySQL Configuration
MYSQL_ROOT_PASSWORD=YourStrongRootPassword123!
MYSQL_DATABASE=rag_chat
MYSQL_USER=rag_user
MYSQL_PASSWORD=YourStrongPassword123!

# OpenAI API Key
OPENAI_API_KEY=sk-proj-your-openai-api-key-here

# Proxy Configuration (if needed)
# HTTP_PROXY=http://proxy.example.com:8080
# HTTPS_PROXY=http://proxy.example.com:8080
# NO_PROXY=localhost,127.0.0.1,backend,mysql,rag-mysql
EOF

# Set permissions
chmod 600 .env
```

## IV. Create Persistent Data Directories

```bash
cd /opt/rag-app

# Create data directories
sudo mkdir -p /data/mysql /data/uploads /data/logs

# Set permissions (ensure Docker containers can write)
sudo chown -R 999:999 /data/mysql  # MySQL user
sudo chown -R $USER:$USER /data/uploads /data/logs
sudo chmod -R 755 /data/uploads /data/logs
```

## V. Build and Deploy Frontend

### 5.1 Build Frontend on Server (using Docker)

```bash
cd /opt/rag-app

# Build frontend using Node.js Docker image
docker run --rm -v "$(pwd)/frontend:/app" -w /app node:20-alpine \
  sh -c "npm ci && npm run build"

# Verify build result
ls -la frontend/dist/
```

### 5.2 Or Build Locally and Sync

```bash
# Build locally
cd frontend
npm ci && npm run build

# Sync to server
rsync -avz frontend/dist/ ubuntu@<server-ip>:/opt/rag-app/frontend/dist/
```

## VI. Start Docker Services

### 6.1 Build and Start All Services

```bash
cd /opt/rag-app

# Build and start (run in background)
docker compose up -d --build

# Check service status
docker compose ps

# View logs
docker compose logs -f
```

### 6.2 Verify Service Status

```bash
# Check container status
docker compose ps

# Check backend logs
docker compose logs --tail=100 backend

# Check MySQL logs
docker compose logs --tail=50 mysql

# Check Nginx logs
docker compose logs --tail=50 nginx

# Test backend API
curl http://localhost:8080/api/health || echo "Backend not ready yet"

# Test frontend
curl http://localhost:80 | head -20
```

## VII. Common Operations Commands

### 7.1 Service Management

```bash
# Start services
docker compose up -d

# Stop services
docker compose down

# Restart services
docker compose restart

# Restart specific service
docker compose restart backend
docker compose restart nginx

# Check service status
docker compose ps

# View resource usage
docker stats
```

### 7.2 Log Management

```bash
# View all service logs
docker compose logs -f

# View specific service logs
docker compose logs -f backend
docker compose logs -f mysql
docker compose logs -f nginx

# View last 200 lines of logs
docker compose logs --tail=200 backend

# View real-time logs
docker compose logs --follow --tail=50 backend
```

### 7.3 Enter Container for Debugging

```bash
# Enter backend container
docker exec -it rag-backend sh

# Enter MySQL container
docker exec -it rag-mysql mysql -u root -p

# Enter Nginx container
docker exec -it rag-nginx sh
```

### 7.4 Data Backup

```bash
# Backup MySQL data
docker exec rag-mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} rag_chat > backup_$(date +%Y%m%d).sql

# Backup uploaded files
tar -czf uploads_backup_$(date +%Y%m%d).tar.gz /data/uploads

# Backup logs
tar -czf logs_backup_$(date +%Y%m%d).tar.gz /data/logs
```

## VIII. Update Deployment

### 8.1 Update Backend Code

```bash
cd /opt/rag-app

# Pull latest code
git pull  # or re-sync files

# Rebuild and restart backend
docker compose up -d --build backend

# View logs to confirm successful startup
docker compose logs -f backend
```

### 8.2 Update Frontend Code

```bash
cd /opt/rag-app

# Rebuild frontend
docker run --rm -v "$(pwd)/frontend:/app" -w /app node:20-alpine \
  sh -c "npm ci && npm run build"

# Restart Nginx
docker compose restart nginx

# Clear browser cache (client-side)
```

### 8.3 Update Database (Flyway Migration)

```bash
# Flyway will automatically execute migrations on backend startup
# If manual execution is needed, restart backend
docker compose restart backend

# View migration logs
docker compose logs backend | grep -i flyway
```

## IX. Troubleshooting

### 9.1 Services Cannot Start

```bash
# Check container status
docker compose ps -a

# View error logs
docker compose logs backend
docker compose logs mysql
docker compose logs nginx

# Check port usage
sudo netstat -tulpn | grep -E '80|3306|8080'

# Check disk space
df -h

# Check Docker resources
docker system df
```

### 9.2 Database Connection Issues

```bash
# Test MySQL connection
docker exec rag-mysql mysql -u rag_user -p${MYSQL_PASSWORD} rag_chat -e "SELECT 1;"

# Check MySQL logs
docker compose logs mysql | tail -50

# Check backend database connection configuration
docker compose logs backend | grep -i "datasource\|mysql\|connection"
```

### 9.3 Frontend Cannot Be Accessed

```bash
# Check Nginx configuration
docker exec rag-nginx cat /etc/nginx/conf.d/default.conf

# Check if frontend files exist
ls -la frontend/dist/

# Test Nginx
docker exec rag-nginx nginx -t

# Restart Nginx
docker compose restart nginx
```

### 9.4 OpenAI API Call Failures

```bash
# Check if API Key is configured
docker exec rag-backend env | grep OPENAI_API_KEY

# Check network connection (from container)
docker exec rag-backend wget -O- https://api.openai.com/v1/models

# If using proxy, check proxy configuration
docker exec rag-backend env | grep -i proxy

# View API errors in backend logs
docker compose logs backend | grep -i "openai\|api\|error"
```

## X. Security Configuration

### 10.1 AWS Security Group Configuration

- Open ports:
  - `80` (HTTP) - Allow all sources or specific IPs
  - `443` (HTTPS) - If SSL is configured
  - `22` (SSH) - Only allow administrator IPs

- Restricted ports:
  - `3306` (MySQL) - Not exposed externally, internal access only
  - `8080` (Backend) - Not exposed externally, only accessible via Nginx proxy

### 10.2 Firewall Configuration (UFW)

```bash
# Allow SSH
sudo ufw allow 22/tcp

# Allow HTTP/HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Enable firewall
sudo ufw enable

# Check status
sudo ufw status
```

### 10.3 SSL/HTTPS Configuration (Optional)

```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx -y

# Request certificate (requires domain name)
sudo certbot --nginx -d yourdomain.com

# Auto-renewal
sudo certbot renew --dry-run
```

## XI. Monitoring and Maintenance

### 11.1 View System Resources

```bash
# View container resource usage
docker stats

# View disk usage
df -h
du -sh /data/*

# View memory usage
free -h

# View CPU usage
top
```

### 11.2 Log Rotation

```bash
# Backend logs are automatically rotated daily by logback
# Clean old logs (keep 30 days)
find /data/logs -name "*.log.*" -mtime +30 -delete

# Clean Docker logs
docker system prune -a --volumes
```

### 11.3 Regular Backups

```bash
# Create backup script
cat > /opt/rag-app/backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/opt/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# Backup database
docker exec rag-mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} rag_chat > $BACKUP_DIR/db_$DATE.sql

# Backup uploaded files
tar -czf $BACKUP_DIR/uploads_$DATE.tar.gz /data/uploads

# Delete backups older than 7 days
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete
find $BACKUP_DIR -name "*.tar.gz" -mtime +7 -delete
EOF

chmod +x /opt/rag-app/backup.sh

# Add to crontab (execute daily at 2 AM)
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/rag-app/backup.sh") | crontab -
```

## XII. Quick Deployment Checklist

- [ ] Docker and Docker Compose are installed
- [ ] Project files are synced to server `/opt/rag-app`
- [ ] `.env` file is created and configured correctly
- [ ] Persistent directories are created (`/data/mysql`, `/data/uploads`, `/data/logs`)
- [ ] Frontend is built (`frontend/dist/` exists)
- [ ] `docker-compose.yml` is configured correctly
- [ ] `frontend/nginx.conf` exists and is configured correctly
- [ ] All services are started (`docker compose ps`)
- [ ] Backend logs have no errors (`docker compose logs backend`)
- [ ] Frontend is accessible (`curl http://localhost`)
- [ ] API is accessible (`curl http://localhost/api/...`)
- [ ] AWS security group is configured (open ports 80/443)
- [ ] Firewall is configured (if using UFW)

## XIII. Complete Deployment Script (One-Click Execution)

```bash
#!/bin/bash
# Complete deployment script (must be executed in project root directory)

set -e

echo "=== Starting RAG Chat Application Deployment ==="

# 1. Check Docker
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed"
    exit 1
fi

# 2. Create data directories
echo "Creating data directories..."
sudo mkdir -p /data/mysql /data/uploads /data/logs
sudo chown -R 999:999 /data/mysql
sudo chown -R $USER:$USER /data/uploads /data/logs

# 3. Check .env file
if [ ! -f .env ]; then
    echo "Error: .env file does not exist, please create it first"
    exit 1
fi

# 4. Build frontend
echo "Building frontend..."
docker run --rm -v "$(pwd)/frontend:/app" -w /app node:20-alpine \
  sh -c "npm ci && npm run build"

# 5. Build and start services
echo "Starting Docker services..."
docker compose up -d --build

# 6. Wait for services to start
echo "Waiting for services to start..."
sleep 10

# 7. Check service status
echo "Checking service status..."
docker compose ps

# 8. View logs
echo "Viewing backend logs..."
docker compose logs --tail=50 backend

echo "=== Deployment Complete ==="
echo "Access URL: http://$(hostname -I | awk '{print $1}')"
```

---

**Important Notes:**

1. All paths are based on `/opt/rag-app` project directory
2. Data is persisted in `/data/` directory and will not be lost on container restart
3. Frontend build artifacts are in `frontend/dist/`, must be built before access
4. `.env` file contains sensitive information, do not commit to Git
5. Regularly backup database and uploaded files
6. Monitor logs and system resource usage

