# 项目结构和组织

## 根目录结构

```
CPlink/
├── app/                    # Android应用主模块
├── web/                    # Flask Web后台
├── gradle/                 # Gradle配置文件
├── .kiro/                  # Kiro AI助手配置
├── .git/                   # Git版本控制
├── .gradle/                # Gradle缓存
├── .idea/                  # IntelliJ IDEA配置
├── .kotlin/                # Kotlin编译缓存
├── .venv/                  # Python虚拟环境
└── 构建和部署脚本
```

## Android应用结构 (`app/`)

```
app/
├── src/                    # 源代码目录
│   ├── main/              # 主要源码
│   ├── test/              # 单元测试
│   └── androidTest/       # Android测试
├── build.gradle.kts       # 应用级Gradle配置
├── proguard-rules.pro     # ProGuard混淆规则
├── security-config.pro    # 安全配置规则
├── release.keystore       # 发布签名密钥
└── dictionary.txt         # 混淆字典
```

### 源码组织原则
- 使用Kotlin作为主要开发语言
- 遵循Android Architecture Components模式
- Jetpack Compose UI组件化开发
- 按功能模块组织包结构

## Web后台结构 (`web/`)

```
web/
├── app.py                 # Flask主应用文件
├── templates/             # Jinja2模板文件
├── database.db           # SQLite数据库文件
├── requirements*.txt     # Python依赖文件
├── Dockerfile            # Docker容器配置
├── docker-compose.yml    # Docker Compose配置
├── deploy.sh             # 部署脚本
├── *.sh                  # 各种修复和安装脚本
└── README.md             # Web应用文档
```

### 数据库表结构
- **users**: 用户信息和统计数据
- **logs**: 操作日志记录
- **videos**: 视频资源管理

## 配置文件组织

### Gradle配置
- `build.gradle.kts` - 项目级配置
- `app/build.gradle.kts` - 应用级配置
- `gradle/libs.versions.toml` - 版本目录
- `settings.gradle.kts` - 项目设置

### 环境配置
- `local.properties` - 本地开发配置
- `gradle.properties` - Gradle属性配置
- `.gitignore` - Git忽略规则

## 构建和部署脚本

### Windows批处理脚本
- `build_encrypted_apk.bat` - 加密APK构建
- `generate_keystore.bat` - 密钥生成
- `gradlew.bat` - Gradle包装器

### Linux Shell脚本
- `web/deploy.sh` - Web应用部署
- `web/ultimate-fix.sh` - 依赖修复
- `web/force-install.sh` - 强制安装

## 开发规范

### 文件命名
- Kotlin文件使用PascalCase
- 资源文件使用snake_case
- 配置文件使用kebab-case

### 目录组织
- 按功能模块分组，不按文件类型
- 共享组件放在common包中
- 测试文件与源文件结构对应

### 版本控制
- 使用语义化版本号
- 基于日期的构建版本 (YYMMDD)
- Git分支策略：main/develop/feature

## 特殊文件说明

### 安全相关
- `app/release.keystore` - 发布签名密钥
- `app/security-config.pro` - 安全混淆配置
- `app/proguard-rules.pro` - 代码混淆规则

### 多语言支持
- `使用说明.txt` - 中文使用说明
- `web/README.md` - 英文技术文档
- 支持中英文混合开发环境

### 开发工具集成
- `.idea/` - IntelliJ IDEA项目配置
- `.kiro/` - AI助手配置和规则
- `.vscode/` - VS Code配置（如果使用）