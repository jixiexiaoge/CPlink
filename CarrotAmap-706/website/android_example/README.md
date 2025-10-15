# Android反馈功能集成示例

这个文件夹包含了完整的Android反馈功能集成示例，可以直接复制到你的Android项目中使用。

## 文件说明

### 核心文件
- `FeedbackActivity.kt` - 反馈提交界面Activity
- `FeedbackApiService.kt` - API服务类，处理网络请求
- `ImageUtils.kt` - 图片处理工具类
- `activity_feedback.xml` - 反馈界面布局文件

### 配置文件
- `build.gradle` - 依赖配置
- `AndroidManifest.xml` - 权限配置

## 快速集成步骤

### 1. 复制文件
将以下文件复制到你的Android项目中：
```
app/src/main/java/com/yourpackage/
├── FeedbackActivity.kt
├── FeedbackApiService.kt
└── ImageUtils.kt

app/src/main/res/layout/
└── activity_feedback.xml
```

### 2. 添加依赖
在 `app/build.gradle` 中添加依赖：
```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'
    implementation 'com.google.android.material:material:1.9.0'
}
```

### 3. 配置权限
在 `AndroidManifest.xml` 中添加权限：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

### 4. 修改包名
将示例代码中的包名 `com.example.feedback` 替换为你的项目包名。

### 5. 启动Activity
在你的应用中添加启动反馈Activity的代码：
```kotlin
val intent = Intent(this, FeedbackActivity::class.java)
startActivity(intent)
```

## 功能特性

### ✅ 已实现功能
- 用户ID和反馈内容输入
- 图片选择（最多2张）
- 图片压缩和格式优化
- 网络请求和错误处理
- 进度指示器
- 表单验证
- 响应式UI设计

### 🎨 UI特性
- Material Design风格
- 响应式布局
- 友好的用户提示
- 图片预览功能
- 错误状态显示

### 🔧 技术特性
- OkHttp网络请求
- 图片压缩优化
- 内存管理
- 异常处理
- 超时设置

## 自定义配置

### 修改服务器地址
在 `FeedbackApiService.kt` 中修改：
```kotlin
private val baseUrl = "https://app.mspa.shop"  // 你的服务器地址
```

### 调整图片压缩参数
在 `ImageUtils.kt` 中修改：
```kotlin
fun compressAndResizeImage(
    inputFile: File,
    maxWidth: Int = 800,      // 最大宽度
    maxHeight: Int = 600,     // 最大高度
    quality: Int = 80,        // 压缩质量
    maxSizeKB: Int = 500      // 最大文件大小
)
```

### 自定义UI样式
修改 `activity_feedback.xml` 中的样式和颜色。

## 测试方法

### 1. 单元测试
```kotlin
@Test
fun testImageCompression() {
    val inputFile = File("test_image.jpg")
    val compressedFile = ImageUtils.compressAndResizeImage(inputFile)
    assertTrue(compressedFile.exists())
    assertTrue(compressedFile.length() < inputFile.length())
}
```

### 2. 网络测试
```kotlin
val apiService = FeedbackApiService()
apiService.testConnection { success, message ->
    if (success) {
        Log.d("Network", "连接成功: $message")
    } else {
        Log.e("Network", "连接失败: $message")
    }
}
```

### 3. 集成测试
1. 在真机或模拟器上运行应用
2. 填写测试数据
3. 选择测试图片
4. 提交反馈
5. 检查服务器端是否收到数据

## 常见问题

### Q: 图片上传失败怎么办？
A: 检查以下几点：
- 网络连接是否正常
- 图片文件是否过大（超过16MB）
- 服务器地址是否正确
- 权限是否已授予

### Q: 如何添加更多图片格式支持？
A: 在 `ImageUtils.kt` 的 `isValidImageFile` 方法中添加更多格式检查。

### Q: 如何自定义错误提示？
A: 在 `FeedbackActivity.kt` 的 `handleError` 方法中修改错误处理逻辑。

### Q: 如何添加更多表单字段？
A: 在布局文件中添加新的输入控件，在Activity中处理对应的数据。

## 扩展功能建议

### 1. 添加图片预览
```kotlin
// 在updateImagePreview()方法中添加图片缩略图显示
private fun showImagePreview(imageFile: File) {
    // 使用Glide或其他图片加载库显示缩略图
}
```

### 2. 添加反馈历史
```kotlin
// 创建本地数据库存储反馈历史
class FeedbackDatabase : RoomDatabase() {
    // 实现本地存储逻辑
}
```

### 3. 添加离线支持
```kotlin
// 在网络不可用时保存到本地，网络恢复时自动提交
class OfflineFeedbackManager {
    // 实现离线存储和同步逻辑
}
```

### 4. 添加用户认证
```kotlin
// 集成用户登录系统
class UserManager {
    // 实现用户认证逻辑
}
```

## 性能优化建议

1. **图片处理**：使用后台线程处理图片压缩
2. **内存管理**：及时释放Bitmap资源
3. **网络优化**：添加请求缓存和重试机制
4. **UI优化**：使用异步加载和懒加载

## 安全注意事项

1. **数据验证**：在客户端和服务端都进行数据验证
2. **文件安全**：限制上传文件类型和大小
3. **网络安全**：使用HTTPS进行数据传输
4. **权限管理**：只请求必要的权限

---

如有问题，请参考主README文档或联系开发者。
