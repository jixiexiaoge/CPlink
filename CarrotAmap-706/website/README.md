# 反馈管理系统

一个基于Flask和SQLite的反馈管理系统，支持图片上传、管理员功能和响应式设计。

## 功能特性

### 🎯 核心功能
- **API接口**: 接收来自APP的POST请求，包含ID、时间、反馈内容和1-2张图片
- **数据存储**: 使用SQLite数据库存储反馈信息
- **图片管理**: 支持图片上传、存储和预览
- **APK管理**: 支持APK文件上传、版本管理和自动更新检查
- **响应式设计**: 完美支持手机和电脑访问

### 🔧 管理员功能
- **身份验证**: 管理员密码 `1533`
- **数据管理**: 删除反馈条目
- **备注功能**: 为反馈添加管理员备注
- **数据导出**: 导出所有数据为CSV格式
- **图片查看**: 点击预览上传的图片
- **APK管理**: 上传、删除APK版本，管理更新说明

### 📱 用户体验
- **现代化界面**: 使用Bootstrap 5构建的美观界面
- **移动端优化**: 完美适配手机和平板设备
- **实时反馈**: 操作成功/失败的即时提示
- **图片预览**: 点击图片即可全屏查看

## 技术栈

- **后端**: Flask 2.3.3
- **数据库**: SQLite
- **前端**: Bootstrap 5 + Font Awesome
- **文件处理**: Werkzeug
- **响应式设计**: CSS3 + JavaScript

## 快速开始

### 🌐 在线体验
- **Web界面**: https://app.mspa.shop
- **管理员面板**: https://app.mspa.shop/admin/login (密码: 1533)
- **API测试**: 使用提供的测试脚本

### 📱 Android集成
1. **快速集成**：使用 `android_example/` 文件夹中的完整示例代码
2. **代码示例**：复制README中的Android代码示例
3. **配置服务器**：修改服务器地址为 `https://app.mspa.shop`
4. **测试功能**：运行应用并测试反馈提交功能

> 💡 **提示**：`android_example/` 文件夹包含完整的Android项目示例，包括Activity、API服务、图片处理工具等，可以直接复制使用。

## 安装和运行

### 方法一：使用启动脚本（推荐）

1. 双击运行 `run.bat` 文件
2. 脚本会自动：
   - 检查Python环境
   - 创建虚拟环境
   - 安装依赖包
   - 启动Flask应用

### 方法二：手动安装

1. **安装Python依赖**
   ```bash
   pip install -r requirements.txt
   ```

2. **运行应用**
   ```bash
   python app.py
   ```

3. **访问应用**
   - 本地测试: http://localhost:5000
   - 生产环境: https://app.mspa.shop
   - 管理员登录: https://app.mspa.shop/admin/login

## 项目结构

```
website/
├── app.py                 # Flask主应用文件
├── requirements.txt       # Python依赖包
├── run.bat               # Windows启动脚本
├── README.md             # 项目说明文档
├── test_apk_api.py       # APK版本API测试脚本
├── feedback.db           # SQLite数据库（运行后自动生成）
├── uploads/              # 图片上传目录（运行后自动生成）
├── templates/            # HTML模板文件夹
│   ├── base.html         # 基础模板
│   ├── index.html        # 主页模板
│   ├── admin_login.html  # 管理员登录页面
│   └── admin_panel.html  # 管理员面板
├── static/               # 静态文件文件夹
│   ├── style.css         # 自定义样式
│   └── script.js         # JavaScript功能
└── android_example/      # Android集成示例
    ├── README.md         # Android集成说明
    ├── FeedbackActivity.kt      # 反馈Activity
    ├── FeedbackApiService.kt    # API服务类
    ├── ImageUtils.kt            # 图片处理工具
    ├── activity_feedback.xml    # 布局文件
    ├── build.gradle             # 依赖配置
    └── AndroidManifest.xml      # 权限配置
```

## API接口说明

### 提交反馈接口

**URL**: `POST /api/feedback`

**参数**:
- `id`: 用户ID（字符串）
- `time`: 时间戳（字符串）
- `feedback`: 反馈内容（字符串）
- `images`: 图片文件（1-2张，可选）

**响应**:
```json
{
    "status": "success",
    "message": "反馈提交成功"
}
```

**示例**:
```bash
# 本地测试
curl -X POST http://localhost:5000/api/feedback \
  -F "id=user123" \
  -F "time=2024-01-01 12:00:00" \
  -F "feedback=这是一个测试反馈" \
  -F "images=@image1.jpg" \
  -F "images=@image2.jpg"

# 生产环境
curl -k -X POST https://app.mspa.shop/api/feedback \
  -F "id=user123" \
  -F "time=2024-01-01 12:00:00" \
  -F "feedback=这是一个测试反馈" \
  -F "images=@image1.jpg" \
  -F "images=@image2.jpg"
```

### APK版本检查接口

**URL**: `GET /api/apk/version`

**说明**: 获取最新APK版本信息，用于APP自动更新检查

**响应**:
```json
{
    "status": "success",
    "version_code": "250909",
    "version_name": "v2.5.9",
    "update_notes": "修复了已知问题，优化了用户体验",
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

**示例**:
```bash
# 检查最新版本
curl -k https://app.mspa.shop/api/apk/version

# 下载APK文件
curl -k -O https://app.mspa.shop/apks/app_v250909_app.apk
```

## 数据库结构

### feedback表
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | INTEGER | 主键，自增 |
| user_id | TEXT | 用户ID |
| time | TEXT | 反馈时间 |
| feedback | TEXT | 反馈内容 |
| images | TEXT | 图片路径（JSON格式） |
| note | TEXT | 管理员备注 |
| created_at | TIMESTAMP | 创建时间 |

### apk_versions表
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | INTEGER | 主键，自增 |
| version_code | TEXT | 版本号（如250909） |
| version_name | TEXT | 版本名称（如v2.5.9） |
| update_notes | TEXT | 更新说明 |
| download_url | TEXT | APK下载链接 |
| file_size | INTEGER | 文件大小（字节） |
| upload_time | TIMESTAMP | 添加时间 |
| is_active | BOOLEAN | 是否为当前活跃版本 |

## 管理员功能说明

### 登录
- 本地测试: http://localhost:5000/admin/login
- 生产环境: https://app.mspa.shop/admin/login
- 密码: `1533`

### 管理操作
1. **删除反馈**: 点击反馈条目右侧的删除按钮
2. **添加备注**: 在备注框中输入内容，点击保存
3. **导出数据**: 点击"导出数据"按钮下载CSV文件
4. **查看图片**: 点击图片缩略图查看大图
5. **添加APK版本**: 点击"添加APK版本"按钮，填写版本信息和下载链接
6. **管理APK版本**: 查看、下载、删除APK版本

## 安全注意事项

1. **生产环境部署**:
   - 修改 `app.secret_key`
   - 更改管理员密码
   - 配置HTTPS
   - 设置文件上传限制

2. **文件安全**:
   - 上传的图片存储在 `uploads/` 目录
   - 支持的文件格式: png, jpg, jpeg, gif, webp
   - 最大文件大小: 16MB

## 常见问题

### Q: 如何修改管理员密码？
A: 在 `app.py` 文件中修改 `ADMIN_PASSWORD` 变量。

### Q: 如何更改上传文件大小限制？
A: 在 `app.py` 中修改 `MAX_CONTENT_LENGTH` 配置。

### Q: 如何备份数据？
A: 复制 `feedback.db` 文件即可备份所有数据。

### Q: 如何部署到生产环境？
A: 使用 Gunicorn 或 uWSGI 等WSGI服务器，配置Nginx反向代理。

### Q: 如何测试APK版本管理功能？
A: 运行 `python test_apk_api.py` 脚本测试APK版本检查API。

### Q: 如何添加APK版本？
A: 登录管理员面板，点击"添加APK版本"按钮，填写版本号、更新说明和下载链接。

### Q: APK文件如何管理？
A: 管理员只需要提供APK的下载链接，系统不存储APK文件本身，简化了管理流程。

## Android应用集成指南

### 在Android应用中集成反馈功能

#### 1. 添加网络权限
在 `AndroidManifest.xml` 中添加网络权限：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

#### 2. 添加依赖库
在 `build.gradle` (Module: app) 中添加：
```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.10.0'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

#### 3. 创建反馈数据类
```kotlin
data class FeedbackData(
    val id: String,
    val time: String,
    val feedback: String,
    val images: List<String> = emptyList()
)
```

#### 4. 创建API服务类
```kotlin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FeedbackApiService {
    private val client = OkHttpClient()
    private val baseUrl = "https://app.mspa.shop"
    
    fun submitFeedback(
        userId: String,
        feedback: String,
        images: List<File>? = null,
        callback: (Boolean, String) -> Unit
    ) {
        try {
            val formBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("id", userId)
                .addFormDataPart("time", getCurrentTime())
                .addFormDataPart("feedback", feedback)
            
            // 添加图片
            images?.forEach { imageFile ->
                val requestFile = imageFile.asRequestBody("image/*".toMediaType())
                formBuilder.addFormDataPart(
                    "images",
                    imageFile.name,
                    requestFile
                )
            }
            
            val requestBody = formBuilder.build()
            
            val request = Request.Builder()
                .url("$baseUrl/api/feedback")
                .post(requestBody)
                .build()
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(false, "网络错误: ${e.message}")
                }
                
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        callback(true, "反馈提交成功")
                    } else {
                        callback(false, "提交失败: $responseBody")
                    }
                }
            })
        } catch (e: Exception) {
            callback(false, "提交异常: ${e.message}")
        }
    }
    
    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
}
```

#### 5. 在Activity中使用
```kotlin
class FeedbackActivity : AppCompatActivity() {
    private val apiService = FeedbackApiService()
    private val selectedImages = mutableListOf<File>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)
        
        // 设置提交按钮点击事件
        submitButton.setOnClickListener {
            submitFeedback()
        }
        
        // 设置图片选择按钮
        selectImageButton.setOnClickListener {
            selectImages()
        }
    }
    
    private fun submitFeedback() {
        val userId = userIdEditText.text.toString()
        val feedback = feedbackEditText.text.toString()
        
        if (userId.isEmpty() || feedback.isEmpty()) {
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 显示加载状态
        submitButton.isEnabled = false
        submitButton.text = "提交中..."
        
        apiService.submitFeedback(
            userId = userId,
            feedback = feedback,
            images = selectedImages.takeIf { it.isNotEmpty() }
        ) { success, message ->
            runOnUiThread {
                submitButton.isEnabled = true
                submitButton.text = "提交反馈"
                
                if (success) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    // 清空表单
                    feedbackEditText.text.clear()
                    selectedImages.clear()
                    updateImagePreview()
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun selectImages() {
        // 使用图片选择库，如 ImagePicker
        // 这里使用系统默认的图片选择器
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGES)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SELECT_IMAGES && resultCode == RESULT_OK) {
            data?.let { intent ->
                val clipData = intent.clipData
                if (clipData != null) {
                    // 多选图片
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        val file = File(uri.path ?: "")
                        selectedImages.add(file)
                    }
                } else {
                    // 单选图片
                    intent.data?.let { uri ->
                        val file = File(uri.path ?: "")
                        selectedImages.add(file)
                    }
                }
                updateImagePreview()
            }
        }
    }
    
    private fun updateImagePreview() {
        // 更新图片预览UI
        // 显示选中的图片数量和缩略图
    }
    
    companion object {
        private const val REQUEST_CODE_SELECT_IMAGES = 1001
    }
}
```

#### 6. 添加图片压缩功能（可选）
```kotlin
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class ImageUtils {
    companion object {
        fun compressImage(inputFile: File, maxSizeKB: Int = 500): File {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
            
            var quality = 100
            var outputStream: FileOutputStream
            
            do {
                val outputFile = File(inputFile.parent, "compressed_${inputFile.name}")
                outputStream = FileOutputStream(outputFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                outputStream.close()
                quality -= 10
            } while (outputFile.length() > maxSizeKB * 1024 && quality > 10)
            
            return outputFile
        }
    }
}
```

#### 7. 完整的布局文件示例
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <EditText
        android:id="@+id/userIdEditText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="用户ID"
        android:inputType="text" />

    <EditText
        android:id="@+id/feedbackEditText"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:gravity="top"
        android:hint="请输入反馈内容..."
        android:inputType="textMultiLine"
        android:minLines="5" />

    <Button
        android:id="@+id/selectImageButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="选择图片 (最多2张)" />

    <TextView
        android:id="@+id/imageCountText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="已选择 0 张图片"
        android:visibility="gone" />

    <Button
        android:id="@+id/submitButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="提交反馈" />

</LinearLayout>
```

### 测试Android应用
1. 在模拟器或真机上运行应用
2. 填写用户ID和反馈内容
3. 选择1-2张图片（可选）
4. 点击提交按钮
5. 检查服务器端是否收到数据

### Android开发最佳实践

#### 网络请求优化
```kotlin
// 添加网络超时设置
private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
```

#### 图片处理优化
```kotlin
// 图片压缩和格式转换
fun compressAndResizeImage(file: File): File {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 800, 600, true)
    
    val outputFile = File(file.parent, "compressed_${file.name}")
    val outputStream = FileOutputStream(outputFile)
    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
    outputStream.close()
    
    return outputFile
}
```

#### 错误处理
```kotlin
// 添加详细的错误处理
apiService.submitFeedback(userId, feedback, images) { success, message ->
    runOnUiThread {
        when {
            success -> {
                Toast.makeText(this, "反馈提交成功", Toast.LENGTH_SHORT).show()
                // 清空表单
                clearForm()
            }
            message.contains("网络错误") -> {
                Toast.makeText(this, "网络连接失败，请检查网络设置", Toast.LENGTH_LONG).show()
            }
            message.contains("超时") -> {
                Toast.makeText(this, "请求超时，请重试", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "提交失败: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
```

#### 用户体验优化
```kotlin
// 添加进度指示器
private fun showProgressDialog() {
    progressDialog = ProgressDialog(this)
    progressDialog?.setMessage("正在提交反馈...")
    progressDialog?.setCancelable(false)
    progressDialog?.show()
}

private fun hideProgressDialog() {
    progressDialog?.dismiss()
    progressDialog = null
}

// 表单验证
private fun validateForm(): Boolean {
    val userId = userIdEditText.text.toString().trim()
    val feedback = feedbackEditText.text.toString().trim()
    
    when {
        userId.isEmpty() -> {
            userIdEditText.error = "请输入用户ID"
            return false
        }
        feedback.isEmpty() -> {
            feedbackEditText.error = "请输入反馈内容"
            return false
        }
        feedback.length < 10 -> {
            feedbackEditText.error = "反馈内容至少10个字符"
            return false
        }
        selectedImages.size > 2 -> {
            Toast.makeText(this, "最多只能选择2张图片", Toast.LENGTH_SHORT).show()
            return false
        }
    }
    return true
}
```

### Android项目结构建议
```
app/src/main/java/com/yourpackage/
├── ui/
│   └── feedback/
│       ├── FeedbackActivity.kt
│       └── FeedbackViewModel.kt
├── network/
│   ├── FeedbackApiService.kt
│   └── ApiConstants.kt
├── utils/
│   ├── ImageUtils.kt
│   └── NetworkUtils.kt
└── data/
    └── FeedbackData.kt
```

### 权限配置
在 `AndroidManifest.xml` 中添加完整权限：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.CAMERA" />

<!-- 如果需要从相册选择图片 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

## 开发说明

### 添加新功能
1. 在 `app.py` 中添加路由
2. 在 `templates/` 中创建对应的HTML模板
3. 在 `static/` 中添加CSS和JavaScript

### 自定义样式
- 修改 `static/style.css` 文件
- 使用Bootstrap 5的CSS类
- 支持响应式设计

## 许可证

本项目仅供学习和个人使用。

## 联系方式

如有问题或建议，请联系开发者。

---

**注意**: 首次运行时会自动创建数据库和上传文件夹，无需手动创建。
