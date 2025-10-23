package com.example.carrotamap.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 视频数据类
 */
data class VideoItem(
    val id: Int,
    val title: String,
    val videoLink: String
)

/**
 * 常见问题数据类
 */
data class FAQItem(
    val question: String,
    val answer: String
)

/**
 * 帮助页面组件
 */
@Composable
fun HelpPage() {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 折叠状态
    var isFAQExpanded by remember { mutableStateOf(false) }
    var isVideoExpanded by remember { mutableStateOf(false) }
    
    // 全屏浏览器弹窗状态
    var showFullscreenBrowser by remember { mutableStateOf(false) }
    
    // 获取视频数据
    LaunchedEffect(Unit) {
        try {
            videos = fetchVideos()
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "获取视频列表失败: ${e.message}"
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 页面标题
        Text(
            text = "帮助中心",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 常见问题卡片 - 放在前面
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFAQExpanded = !isFAQExpanded }
                        .padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "常见问题",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "常见问题",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isFAQExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isFAQExpanded) "收起" else "展开",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // 常见问题列表 - 根据折叠状态显示
                if (isFAQExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        getFAQItems().forEach { faq ->
                            FAQItemCard(faq = faq)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 视频教程卡片 - 放在后面，可折叠
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isVideoExpanded = !isVideoExpanded }
                        .padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "视频教程",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "视频教程",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isVideoExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isVideoExpanded) "收起" else "展开",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // 视频教程内容 - 根据折叠状态显示
                if (isVideoExpanded) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF3B82F6)
                                )
                            }
                        }
                        errorMessage != null -> {
                            Text(
                                text = errorMessage!!,
                                color = Color.Red,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        videos.isEmpty() -> {
                            Text(
                                text = "暂无视频教程",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        else -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                videos.forEach { video ->
                                    VideoItemCard(
                                        video = video,
                                        onClick = { videoUrl ->
                                            openVideoInBrowser(context, videoUrl)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 查看排行榜按钮 - 简化版
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            // 查看排行榜按钮
            Button(
                onClick = { showFullscreenBrowser = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(10.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "查看排行榜",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // 底部间距，确保内容可以完全滚动
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // 全屏浏览器弹窗
    if (showFullscreenBrowser) {
        FullscreenBrowserDialog(
            onDismiss = { showFullscreenBrowser = false }
        )
    }
}

/**
 * 视频卡片组件
 */
@Composable
private fun VideoItemCard(
    video: VideoItem,
    onClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(video.videoLink) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放视频",
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B),
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "点击观看视频教程",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "打开链接",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 获取视频列表
 */
private suspend fun fetchVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://app.mspa.shop//api/videos")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "CP搭子/1.0")
        }
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            
            if (jsonObject.getBoolean("success")) {
                val dataArray = jsonObject.getJSONArray("data")
                val videos = mutableListOf<VideoItem>()
                
                for (i in 0 until dataArray.length()) {
                    val videoObject = dataArray.getJSONObject(i)
                    val video = VideoItem(
                        id = videoObject.getInt("id"),
                        title = videoObject.getString("video_title"),
                        videoLink = videoObject.getString("video_link")
                    )
                    videos.add(video)
                }
                
                videos
            } else {
                throw Exception("API返回失败: ${jsonObject.optString("message", "未知错误")}")
            }
        } else {
            throw Exception("HTTP错误: $responseCode")
        }
    } catch (e: Exception) {
        android.util.Log.e("HelpPage", "获取视频列表失败", e)
        throw e
    }
}

/**
 * FAQ 卡片组件
 */
@Composable
private fun FAQItemCard(faq: FAQItem) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "问题",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = faq.question,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f),
                    lineHeight = 20.sp
                )
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = faq.answer,
                    fontSize = 14.sp,
                    color = Color(0xFF475569),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * 获取常见问题列表
 */
private fun getFAQItems(): List<FAQItem> {
    return listOf(
        FAQItem(
            question = "CarrotAmap 和 CP搭子 有什么区别？",
            answer = "CP搭子仅供赞助用户使用，持续更新维护，功能更稳定；CarrotAmap 已停止维护，基本可用但存在少量 bug。CP搭子是在 CarrotAmap 基础上的改进版本，提供更好的用户体验。"
        ),
        FAQItem(
            question = "支持哪些车型？",
            answer = "理论上支持官方 OpenPilot 的 300 多个车型，再加上国内几乎所有比亚迪车型。额外还支持吉利缤越、长安欧尚等热门车型。具体支持列表会根据社区反馈持续更新。"
        ),
        FAQItem(
            question = "为什么非要用 CP？SP、DP、FP 为什么不行？",
            answer = "CP 是最早使用 Waze 和 TMAP 实现外挂 NOO（Navigate on OpenPilot）功能的项目，代码已公开，其他分支可以借鉴并实现类似功能。CP 在导航集成方面有先发优势和技术积累。"
        ),
        FAQItem(
            question = "只能使用高德车机版吗？",
            answer = "现阶段高德车机版比较方便，通过查看 SDK 文档，理论上百度车机版、腾讯导航等主流导航软件也是可以开发的。并且 iOS 系统理论上也可以开发实现相同的功能。"
        ),
        FAQItem(
            question = "自动超车功能为什么没有实现？",
            answer = "每个车型的硬件配置不同，有的车型没有盲区检测功能。自动超车的代码逻辑相对简单，但前提是必须确保隔壁车道前后及旁边都安全时才能进行自动变道。另外，自动变道需要自动打转向灯，每个车型的转向灯控制方式也不一样，需要针对不同车型进行适配。"
        )
    )
}

/**
 * 在浏览器中打开视频链接
 */
private fun openVideoInBrowser(context: android.content.Context, videoUrl: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        android.util.Log.d("HelpPage", "打开视频链接: $videoUrl")
    } catch (e: Exception) {
        android.util.Log.e("HelpPage", "打开视频链接失败", e)
    }
}

/**
 * 全屏浏览器弹窗组件
 */
@Composable
private fun FullscreenBrowserDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部工具栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 返回按钮
                    IconButton(
                        onClick = {
                            webView?.let { wv ->
                                if (wv.canGoBack()) {
                                    wv.goBack()
                                } else {
                                    onDismiss()
                                }
                            } ?: onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = if (canGoBack) Icons.Default.ArrowBack else Icons.Default.Close,
                            contentDescription = if (canGoBack) "返回" else "关闭",
                            tint = Color(0xFF1E293B)
                        )
                    }
                    
                    // 标题
                    Text(
                        text = "CP搭子排行榜",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    
                    // 前进按钮
                    IconButton(
                        onClick = {
                            webView?.let { wv ->
                                if (wv.canGoForward()) {
                                    wv.goForward()
                                }
                            }
                        },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "前进",
                            tint = if (canGoForward) Color(0xFF1E293B) else Color(0xFF94A3B8)
                        )
                    }
                    
                    // 刷新按钮
                    IconButton(
                        onClick = {
                            webView?.reload()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = Color(0xFF1E293B)
                        )
                    }
                }
                
                // WebView 容器
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webView = this
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                            }
                            
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                databaseEnabled = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                textZoom = 100
                                loadsImagesAutomatically = true
                                blockNetworkImage = false
                            }
                            
                            // 滚动设置
                            isVerticalScrollBarEnabled = true
                            isHorizontalScrollBarEnabled = true
                            scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
                            isClickable = true
                            isFocusable = true
                            isFocusableInTouchMode = true
                            overScrollMode = android.view.View.OVER_SCROLL_ALWAYS
                            isNestedScrollingEnabled = true
                            setScrollContainer(true)
                            isLongClickable = true
                            
                            loadUrl("https://app.mspa.shop/")
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }
}