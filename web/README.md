# CPlink 管理系统

基于Flask和SQLite的车辆数据管理和用户分析平台。

## 功能特性

### 数据库表结构
- **用户信息表 (users)**: 设备ID、使用次数、使用时长、累计距离、修改时间、赞助金额、用户等级、车型信息、微信名
- **日志表 (logs)**: 设备ID、日志时间、操作记录
- **视频表 (videos)**: 视频标题、视频链接

### 用户等级系统
- **-1 - 管理员专用**: 只有管理员在后台可以设置
- **0 - 未知用户**: 未知类型的用户
- **1 - 新用户**: 新注册的用户
- **2 - 支持者**: 支持项目的用户
- **3 - 赞助者**: 赞助项目的用户
- **4 - 铁粉**: 忠实粉丝用户

### 网页功能
- **首页**: 显示排行榜和统计信息

### 后台管理功能
- **管理员登录**: 使用密码 "Flow2025" 登录后台
- **仪表板**: 系统统计和最近数据概览
- **用户管理**: 用户数据的增删改查操作
- **日志管理**: 日志记录的增删改查操作
- **视频管理**: 视频资源的增删改查操作

### API接口
- **用户信息登记与确认**: `/api/user/register` (POST)
  - 新用户: 返回 `user_type=0, time=100`
  - 已登记用户: 返回 `user_type=1, time=200`
- **获取用户数据**: `/api/user/<device_id>` (GET)
- **更新用户数据**: `/api/user/update` (POST)
- **获取所有视频**: `/api/videos` (GET)
- **获取单个视频**: `/api/videos/<video_id>` (GET)

## 安装和运行

### 本地开发环境

#### 1. 安装依赖
```bash
pip install -r requirements.txt
```

#### 2. 运行应用
```bash
python app.py
```

#### 3. 访问系统
- 网页界面: http://localhost:5000
- 后台管理: http://localhost:5000/admin/login (密码: Flow2025)
- API接口: http://localhost:5000/api/user/register

### Linux服务器部署

#### 方法1: 直接部署
```bash
# 更新pip
pip install --upgrade pip

# 安装依赖
pip install -r requirements.txt

# 运行应用
python app.py
```

#### 方法1.1: 如果遇到Flask-SQLAlchemy安装问题

**方案A: 使用终极修复脚本（最推荐）**
```bash
chmod +x ultimate-fix.sh
./ultimate-fix.sh
```

**方案B: 使用Python安装脚本**
```bash
python install_deps.py
```

**方案C: 使用强制安装脚本**
```bash
chmod +x force-install.sh
./force-install.sh
```

**方案D: 使用修复脚本**
```bash
chmod +x fix-deploy.sh
./fix-deploy.sh
```

**方案E: 使用Linux专用依赖文件**
```bash
pip install -r requirements-linux.txt
```

**方案F: 使用最小依赖文件**
```bash
pip install -r requirements-minimal.txt
```

**方案G: 手动修复**
```bash
# 清理所有相关包
pip uninstall -y Flask-SQLAlchemy flask-sqlalchemy Flask Werkzeug SQLAlchemy

# 强制重新安装
pip install --force-reinstall --no-cache-dir Flask==2.3.3
pip install --force-reinstall --no-cache-dir Flask-SQLAlchemy==3.0.5
pip install --force-reinstall --no-cache-dir Werkzeug==2.3.7
pip install --force-reinstall --no-cache-dir SQLAlchemy>=1.4.18
```

**验证安装是否成功**
```bash
python test_imports.py
```

#### 方法2: 使用部署脚本
```bash
# 给脚本执行权限
chmod +x deploy.sh

# 运行部署脚本
./deploy.sh
```

#### 方法3: 使用Docker部署
```bash
# 构建镜像
docker build -t cplink-web .

# 运行容器
docker run -p 5000:5000 cplink-web
```

#### 方法4: 使用Docker Compose部署
```bash
# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

### 生产环境部署建议

1. **使用虚拟环境**:
```bash
python -m venv venv
source venv/bin/activate  # Linux/Mac
# 或
venv\Scripts\activate  # Windows
```

2. **使用Gunicorn** (推荐):
```bash
pip install gunicorn
gunicorn -w 4 -b 0.0.0.0:5000 app:app
```

3. **使用Nginx反向代理**:
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    location / {
        proxy_pass http://127.0.0.1:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## API使用示例

### 1. 用户信息登记与确认
```bash
curl -X POST http://localhost:5000/api/user/register \
  -H "Content-Type: application/json" \
  -d '{
    "device_id": "DEVICE123456",
    "usage_count": 10,
    "usage_duration": 25.5,
    "total_distance": 1200.0,
    "wechat_name": "用户微信名"
  }'
```

**响应示例:**
- 新用户: `{"user_type": 0, "time": 100, "message": "新用户已登记"}`
- 已登记用户: `{"user_type": 1, "time": 200, "message": "用户信息已更新"}`

### 2. 获取用户数据
```bash
curl -X GET http://localhost:5000/api/user/DEVICE001
```

**响应示例:**
```json
{
  "success": true,
  "data": {
    "device_id": "DEVICE001",
    "usage_count": 25,
    "usage_duration": 45.5,
    "total_distance": 1200.0,
    "modify_time": "2024-10-06T08:00:00",
    "sponsor_amount": 100.0,
    "user_type": 2,
    "car_model": "特斯拉 Model 3",
    "wechat_name": "特斯拉车主小王"
  },
  "message": "用户数据获取成功"
}
```

### 3. 更新用户数据
```bash
curl -X POST http://localhost:5000/api/user/update \
  -H "Content-Type: application/json" \
  -d '{
    "device_id": "DEVICE001",
    "usage_count": 30,
    "usage_duration": 50.0,
    "total_distance": 1300.0,
    "sponsor_amount": 150.0,
    "car_model": "特斯拉 Model 3 升级版",
    "wechat_name": "特斯拉车主小王"
  }'
```

**响应示例:**
```json
{
  "success": true,
  "data": {
    "device_id": "DEVICE001",
    "usage_count": 30,
    "usage_duration": 50.0,
    "total_distance": 1300.0,
    "modify_time": "2024-10-06T08:30:00",
    "sponsor_amount": 150.0,
    "user_type": 2,
    "car_model": "特斯拉 Model 3 升级版",
    "wechat_name": "特斯拉车主小王"
  },
  "message": "用户数据更新成功"
}
```

### 4. 获取所有视频数据
```bash
curl -X GET http://localhost:5000/api/videos
```

**响应示例:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "video_title": "CPlink使用教程 - 基础操作",
      "video_link": "https://example.com/video1"
    },
    {
      "id": 2,
      "video_title": "高级功能演示 - 语音控制",
      "video_link": "https://example.com/video2"
    }
  ],
  "count": 2,
  "message": "视频数据获取成功"
}
```

### 5. 获取单个视频数据
```bash
curl -X GET http://localhost:5000/api/videos/1
```

**响应示例:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "video_title": "CPlink使用教程 - 基础操作",
    "video_link": "https://example.com/video1"
  },
  "message": "视频数据获取成功"
}
```

## 数据库初始化

系统启动时会自动创建SQLite数据库文件 `database.db` 和所有必需的表结构。

## 技术栈

- **后端**: Flask 2.3.3
- **数据库**: SQLite + SQLAlchemy
- **前端**: Bootstrap 5 + Font Awesome
- **模板引擎**: Jinja2
