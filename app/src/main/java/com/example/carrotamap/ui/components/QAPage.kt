package com.example.carrotamap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

/**
 * 消息数据类
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AI对话页面组件
 */
@Composable
fun QAPage() {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 初始化欢迎消息
    LaunchedEffect(Unit) {
        messages = listOf(
            ChatMessage(
                content = "你好！我是CP搭子AI帮助小助理。我可以为你解答关于CarrotPilot、CarrotAmap以及CP搭子app的相关问题。有什么可以帮助你的吗？",
                isUser = false
            )
        )
    }
    
    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // 标题栏
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3B82F6)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
        Text(
                    text = "CP搭子 AI帮助小助理",
                    fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // 清除对话按钮
                if (messages.size > 1) {
                    IconButton(
                        onClick = {
                            messages = listOf(
                                ChatMessage(
                                    content = "你好！我是CP搭子AI帮助小助理。我可以为你解答关于CarrotPilot、CarrotAmap以及CP搭子app的相关问题。有什么可以帮助你的吗？",
                                    isUser = false
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除对话",
                            tint = Color.White
                        )
                    }
                }
            }
        }
        
        // 消息列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = scrollState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
            
            // 加载指示器
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE5E7EB)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF6B7280)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI正在思考中...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 输入框
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入你的问题...", color = Color(0xFF9CA3AF)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFFD1D5DB)
                    ),
                    enabled = !isLoading
                )
                
                Spacer(modifier = Modifier.width(6.dp))
                
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val userMessage = ChatMessage(
                                content = inputText.trim(),
                                isUser = true
                            )
                            messages = messages + userMessage
                            val currentInput = inputText.trim()
                            inputText = ""
                            
                            coroutineScope.launch {
                                isLoading = true
                                try {
                                    val aiResponse = callDeepSeekAPI(currentInput, messages)
                                    val aiMessage = ChatMessage(
                                        content = aiResponse,
                                        isUser = false
                                    )
                                    messages = messages + aiMessage
                                } catch (e: Exception) {
                                    val errorMessage = ChatMessage(
                                        content = "服务异常，请稍后再试",
                                        isUser = false
                                    )
                                    messages = messages + errorMessage
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp),
                    containerColor = if (inputText.isNotBlank() && !isLoading) Color(0xFF3B82F6) else Color(0xFF9CA3AF)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 消息气泡组件
 */
@Composable
private fun MessageBubble(message: ChatMessage) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val maxWidth = (screenWidth * 0.8f).coerceAtLeast(200.dp).coerceAtMost(400.dp)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) Color(0xFF3B82F6) else Color(0xFFF8FAFC)
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = maxWidth),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (message.isUser) {
                        // 用户消息简单显示
                        Text(
                            text = message.content,
                            fontSize = 14.sp,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                    } else {
                        // AI消息支持Markdown格式
                        MarkdownText(
                            text = message.content
                        )
                    }
                }
            }
        }
    }
}

/**
 * Markdown文本组件
 */
@Composable
private fun MarkdownText(text: String) {
    val annotatedString = buildAnnotatedString {
        val lines = text.split("\n")
        
        for (line in lines) {
            when {
                line.startsWith("# ") -> {
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1E293B)
                    )) {
                        append(line.substring(2))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E293B)
                    )) {
                        append(line.substring(3))
                    }
                }
                line.startsWith("### ") -> {
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )) {
                        append(line.substring(4))
                    }
                }
                line.startsWith("- ") || line.startsWith("• ") -> {
                    withStyle(style = SpanStyle(
                        color = Color(0xFF374151)
                    )) {
                        append("• ${line.substring(2)}")
                    }
                }
                line.startsWith("**") && line.endsWith("**") -> {
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )) {
                        append(line.substring(2, line.length - 2))
                    }
                }
                line.startsWith("`") && line.endsWith("`") -> {
                    withStyle(style = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFFF1F5F9),
                        color = Color(0xFF1E293B)
                    )) {
                        append(line.substring(1, line.length - 1))
                    }
                }
                line.trim().isEmpty() -> {
                    append("\n")
                }
                else -> {
                    withStyle(style = SpanStyle(
                        color = Color(0xFF374151)
                    )) {
                        append(line)
                    }
                }
            }
            if (line != lines.last()) {
                append("\n")
            }
        }
    }
                
                Text(
        text = annotatedString,
        fontSize = 13.sp,
                    lineHeight = 20.sp
                )
}

/**
 * 调用DeepSeek API - 支持多轮对话
 * 基于官方文档: https://api-docs.deepseek.com/zh-cn/
 */
private suspend fun callDeepSeekAPI(userMessage: String, conversationHistory: List<ChatMessage>): String {
    return withContext(Dispatchers.IO) {
        try {
            // 使用官方文档中的正确端点
            val url = URL("https://api.deepseek.com/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer sk-b8cee692518847a7b0a5aceef0fe74de")
            connection.setRequestProperty("User-Agent", "Android-App/1.0")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            // 构建对话历史 - 按照官方文档格式
            val messages = JSONArray()
            
            // 添加系统消息
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", "你是CP搭子AI技术顾问，专门为CarrotPilot和CP搭子app用户提供专业技术支持。\n\n【技术背景】\n• CarrotPilot：openpilot的完全中文化分支，业界唯一支持高德地图导航实现NOO（Navigate on Openpilot）功能的开源项目\n• CP搭子：Carrot AMAP的升级版本，通过高德地图数据与comma3设备深度集成，实现智能驾驶辅助\n\n【核心功能】\n• 智能巡航：基于高德地图数据的精确速度控制\n• 自动变道：根据导航路径智能选择变道时机\n• 导航辅助：岔路自动选择、路口智能转弯\n• 车型兼容：支持所有openpilot车型 + 国产车型（吉利宾悦、长安欧尚等）\n\n【项目特色】\n• 开源项目，由社区粉丝赞助开发\n• 专为中国道路环境优化\n• 与高德地图深度集成\n\n请用简体中文专业、简洁地回答用户问题，仅处理CarrotPilot、CP搭子、openpilot相关技术问题。")
            })
            
            // 添加历史对话（只保留最近的8轮对话，避免token过多）
            val recentHistory = conversationHistory.takeLast(8)
            for (message in recentHistory) {
                messages.put(JSONObject().apply {
                    put("role", if (message.isUser) "user" else "assistant")
                    put("content", message.content)
                })
            }
            
            // 添加当前用户消息
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
            
            // 按照官方文档的请求格式
            val requestBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", messages)
                put("stream", false)
                put("max_tokens", 1500)
                put("temperature", 0.7)
            }
            
            val requestBodyString = requestBody.toString()
            
            connection.outputStream.use { outputStream ->
                outputStream.write(requestBodyString.toByteArray())
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.has("choices")) {
                        val choices = jsonResponse.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            if (firstChoice.has("message")) {
                                val message = firstChoice.getJSONObject("message")
                                if (message.has("content")) {
                                    return@withContext message.getString("content")
                                }
                            }
                        }
                    }
                "服务异常，请稍后再试"
            } catch (e: Exception) {
                "服务异常，请稍后再试"
            }
        } else {
            "服务异常，请稍后再试"
        }
    } catch (e: Exception) {
        "服务异常，请稍后再试"
    }
    }
}
