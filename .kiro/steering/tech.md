# 技术栈和构建系统

## Android 应用技术栈

### 核心框架
- **Kotlin**: 主要开发语言
- **Jetpack Compose**: 现代化UI框架
- **Android SDK**: Target SDK 35, Min SDK 26
- **Gradle**: 构建系统 (Kotlin DSL)

### 主要依赖库
- **AndroidX Core KTX**: Android核心扩展
- **Lifecycle & ViewModel**: 生命周期管理
- **Activity Compose**: Compose Activity集成
- **Material3**: Material Design 3组件
- **OkHttp3**: HTTP客户端 (v4.12.0)
- **Gson**: JSON序列化 (v2.10.1)
- **ExoPlayer**: 视频播放 (Media3 v1.2.1)

### 构建配置
- **Java版本**: Java 11
- **编译SDK**: 35
- **应用ID**: com.example.cplink
- **版本管理**: 基于日期的版本号格式 (v250929)

## Web 后台技术栈

### 核心框架
- **Python 3.x**: 后端开发语言
- **Flask 2.3.3**: Web框架
- **SQLAlchemy**: ORM数据库操作
- **SQLite**: 数据库存储

### 主要依赖
- **Flask-SQLAlchemy 3.0.5**: Flask数据库集成
- **Werkzeug 2.3.7**: WSGI工具库
- **Jinja2**: 模板引擎

## 常用构建命令

### Android 构建
```bash
# 构建Debug版本
./gradlew assembleDebug

# 构建Release版本 (加密APK)
./gradlew assembleRelease

# 运行测试
./gradlew test

# 清理构建
./gradlew clean

# 使用加密构建脚本
build_encrypted_apk.bat
```

### Web 应用部署
```bash
# 安装依赖
pip install -r requirements.txt

# 本地开发运行
python app.py

# 生产环境部署
gunicorn -w 4 -b 0.0.0.0:5000 app:app

# Docker部署
docker-compose up -d

# 使用部署脚本
chmod +x deploy.sh && ./deploy.sh
```

### 开发工具
```bash
# 生成签名密钥
generate_keystore.bat

# 清理测试视频
python web/clean_test_videos.py

# 测试依赖导入
python web/test_imports.py

# 依赖修复脚本
./web/ultimate-fix.sh
```

## 安全和混淆配置

### ProGuard/R8 优化
- 代码混淆和压缩
- 资源优化和压缩
- 调试信息移除
- 字符串和类名混淆

### 签名配置
- 发布版本使用release.keystore签名
- 密钥别名: cplink_key
- 自动签名配置

### 包优化
- 排除不必要的META-INF文件
- 资源压缩和去重
- APT优化配置