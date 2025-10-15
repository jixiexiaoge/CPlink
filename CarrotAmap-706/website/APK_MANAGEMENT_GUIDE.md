# APK版本管理功能使用指南

## 功能概述

APK版本管理功能允许管理员上传、管理和分发Android应用的APK文件，同时提供API接口供应用检查更新。

## 主要功能

### 1. APK文件上传
- 支持上传APK文件（最大100MB）
- 填写版本号（如：250909）
- 填写版本名称（如：v2.5.9，可选）
- 填写更新说明
- 自动将新版本设为当前活跃版本

### 2. 版本管理
- 查看所有历史版本
- 下载任意版本的APK文件
- 删除不需要的版本
- 自动管理版本状态（活跃/非活跃）

### 3. API接口
- 提供版本检查API：`GET /api/apk/version`
- 返回最新版本信息，包括下载链接
- 支持应用自动更新检查

## 使用步骤

### 管理员操作

1. **登录管理员面板**
   - 访问：https://app.mspa.shop/admin/login
   - 密码：1533

2. **上传APK文件**
   - 点击"上传APK"按钮
   - 填写版本号（必填）
   - 填写版本名称（可选）
   - 填写更新说明（必填）
   - 选择APK文件
   - 点击"上传APK"

3. **管理版本**
   - 在APK版本管理区域查看所有版本
   - 点击"下载APK"下载文件
   - 点击删除按钮删除不需要的版本

### 应用集成

#### Android应用中的版本检查

```kotlin
// 检查应用更新
fun checkForUpdates() {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://app.mspa.shop/api/apk/version")
        .build()
    
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            // 处理网络错误
        }
        
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val json = response.body?.string()
                val gson = Gson()
                val versionInfo = gson.fromJson(json, VersionInfo::class.java)
                
                // 比较版本号
                val currentVersion = getCurrentVersionCode()
                val latestVersion = versionInfo.versionCode.toInt()
                
                if (latestVersion > currentVersion) {
                    // 显示更新提示
                    showUpdateDialog(versionInfo)
                }
            }
        }
    })
}

// 版本信息数据类
data class VersionInfo(
    val status: String,
    val version_code: String,
    val version_name: String,
    val update_notes: String,
    val download_url: String,
    val file_size: Long,
    val upload_time: String
)
```

#### 下载并安装APK

```kotlin
// 下载APK文件
fun downloadApk(downloadUrl: String) {
    val request = Request.Builder()
        .url(downloadUrl)
        .build()
    
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            // 处理下载失败
        }
        
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val apkFile = File(getExternalFilesDir(null), "update.apk")
                val sink = apkFile.sink().buffer()
                sink.writeAll(response.body!!.source())
                sink.close()
                
                // 安装APK
                installApk(apkFile)
            }
        }
    })
}

// 安装APK
private fun installApk(apkFile: File) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.setDataAndType(
        FileProvider.getUriForFile(this, "${packageName}.provider", apkFile),
        "application/vnd.android.package-archive"
    )
    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    startActivity(intent)
}
```

## API接口详情

### 版本检查接口

**URL**: `GET /api/apk/version`

**响应示例**:
```json
{
    "status": "success",
    "version_code": "250909",
    "version_name": "v2.5.9",
    "update_notes": "修复了已知问题，优化了用户体验，增加了新功能",
    "download_url": "https://app.mspa.shop/apks/app_v250909_app.apk",
    "file_size": 15728640,
    "upload_time": "2024-01-01 12:00:00"
}
```

**错误响应**:
```json
{
    "error": "暂无可用版本"
}
```

## 测试方法

### 1. 使用测试脚本
```bash
python test_apk_api.py
```

### 2. 使用curl命令
```bash
# 检查版本
curl -k https://app.mspa.shop/api/apk/version

# 下载APK
curl -k -O https://app.mspa.shop/apks/app_v250909_app.apk
```

### 3. 浏览器测试
- 直接访问：https://app.mspa.shop/api/apk/version
- 查看返回的JSON数据

## 注意事项

1. **版本号格式**：建议使用数字格式，如250909
2. **文件大小**：APK文件最大支持100MB
3. **版本管理**：上传新版本后，之前的版本自动设为非活跃状态
4. **安全性**：APK文件存储在服务器上，确保服务器安全
5. **备份**：定期备份APK文件和数据库

## 故障排除

### 常见问题

1. **上传失败**
   - 检查文件格式是否为.apk
   - 检查文件大小是否超过100MB
   - 检查网络连接

2. **API返回错误**
   - 确认服务器正在运行
   - 检查数据库连接
   - 查看服务器日志

3. **下载失败**
   - 检查APK文件是否存在
   - 检查文件权限
   - 检查网络连接

### 日志查看

查看Flask应用日志以获取详细错误信息：
```bash
# 如果使用systemd服务
journalctl -u your-app-service -f

# 如果直接运行
tail -f app.log
```

## 扩展功能建议

1. **版本比较**：添加版本号比较逻辑
2. **增量更新**：支持增量更新包
3. **强制更新**：支持强制更新标记
4. **更新统计**：记录下载和安装统计
5. **多应用支持**：支持多个应用的版本管理

---

如有问题，请检查服务器日志或联系开发者。
