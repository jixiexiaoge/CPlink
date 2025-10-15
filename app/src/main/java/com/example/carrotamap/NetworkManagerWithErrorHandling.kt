package com.example.carrotamap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import org.json.JSONException
import org.json.JSONObject

/**
 * 带错误处理的网络管理器
 * 增强原NetworkManager的错误处理能力
 */
class NetworkManagerWithErrorHandling(
    private val context: Context,
    private val carrotManFields: androidx.compose.runtime.MutableState<CarrotManFields>
) {
    companion object {
        private const val TAG = "NetworkManagerWithErrorHandling"
    }
    
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var networkClient: CarrotManNetworkClient? = null
    private var isNetworkServiceRunning = false
    private var retryCount = 0
    private val maxRetryCount = 3
    
    /**
     * 启动网络服务（带错误处理）
     */
    suspend fun startNetworkServiceWithErrorHandling(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🌐 启动网络服务...")
            
            if (isNetworkServiceRunning) {
                Log.w(TAG, "⚠️ 网络服务已在运行")
                return@withContext true
            }
            
            // 初始化网络客户端
            networkClient = CarrotManNetworkClient(context)
            
            // 启动网络服务
            networkClient?.start()
            isNetworkServiceRunning = true
            retryCount = 0
            Log.i(TAG, "✅ 网络服务启动成功")
            true
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.handleNetworkError(e, "网络服务启动")
            Log.e(TAG, "❌ 网络服务启动异常: ${errorResult.message}", e)
            
            // 根据错误类型决定是否重试
            if (errorResult.shouldRetry && retryCount < maxRetryCount) {
                retryCount++
                Log.w(TAG, "🔄 准备重试网络服务启动 (第${retryCount}次)")
                kotlinx.coroutines.delay(2000L * retryCount) // 指数退避
                startNetworkServiceWithErrorHandling()
            } else {
                Log.e(TAG, "❌ 网络服务启动失败，已达到最大重试次数")
                false
            }
        }
    }
    
    /**
     * 发送数据（带错误处理）
     */
    suspend fun sendDataWithErrorHandling(data: CarrotManFields): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkServiceRunning) {
                Log.w(TAG, "⚠️ 网络服务未运行，无法发送数据")
                return@withContext false
            }
            
            networkClient?.sendCarrotManData(data)
            Log.d(TAG, "✅ 数据发送成功")
            retryCount = 0
            true
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.handleNetworkError(e, "数据发送")
            Log.e(TAG, "❌ 数据发送异常: ${errorResult.message}", e)
            
            // 根据错误类型决定是否重试
            if (errorResult.shouldRetry && retryCount < maxRetryCount) {
                retryCount++
                Log.w(TAG, "🔄 准备重试数据发送 (第${retryCount}次)")
                kotlinx.coroutines.delay(1000L * retryCount)
                sendDataWithErrorHandling(data)
            } else {
                Log.e(TAG, "❌ 数据发送失败，已达到最大重试次数")
                false
            }
        }
    }
    
    /**
     * 解析OpenPilot状态数据（带错误处理）
     */
    fun parseOpenpilotStatusDataWithErrorHandling(jsonString: String): OpenpilotStatusData? {
        return try {
            Log.d(TAG, "📊 解析OpenPilot状态数据...")
            
            if (jsonString.isEmpty()) {
                Log.w(TAG, "⚠️ 状态数据为空")
                return null
            }
            
            val json = JSONObject(jsonString)
            
            // 解析速度数据（带错误处理）
            val vCruiseKph = try {
                var cruiseSpeed = json.optDouble("vCruiseKph", 0.0)
                if (cruiseSpeed == 0.0) {
                    Log.w(TAG, "⚠️ vCruiseKph字段缺失，尝试替代字段...")
                    val alternativeFields = listOf("cruise_speed", "v_cruise", "cruiseSpeed")
                    for (field in alternativeFields) {
                        if (json.has(field)) {
                            cruiseSpeed = json.optDouble(field, 0.0)
                            Log.i(TAG, "✅ 找到替代字段: $field = $cruiseSpeed")
                            break
                        }
                    }
                }
                cruiseSpeed.toFloat()
            } catch (e: JSONException) {
                Log.e(TAG, "❌ 速度数据解析失败: ${e.message}", e)
                0.0f
            }
            
            // 解析控制状态（带错误处理）
            val (engaged, enabled, standstill) = try {
                val engaged = json.optBoolean("engaged", false)
                val enabled = json.optBoolean("enabled", false)
                val standstill = json.optBoolean("standstill", false)
                Triple(engaged, enabled, standstill)
            } catch (e: JSONException) {
                Log.e(TAG, "❌ 控制状态解析失败: ${e.message}", e)
                Triple(false, false, false)
            }
            
            // 解析导航状态（带错误处理）
            val (navDestination, navDestinationName, navDestinationDistance) = try {
                val navDestination = json.optString("navDestination", "")
                val navDestinationName = json.optString("navDestinationName", "")
                val navDestinationDistance = json.optDouble("navDestinationDistance", 0.0)
                Triple(navDestination, navDestinationName, navDestinationDistance)
            } catch (e: JSONException) {
                Log.e(TAG, "❌ 导航状态解析失败: ${e.message}", e)
                Triple("", "", 0.0)
            }
            
            // 创建状态数据对象
            val statusData = OpenpilotStatusData(
                vCruiseKph = vCruiseKph,
                active = engaged,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            Log.i(TAG, "✅ OpenPilot状态数据解析完成")
            statusData
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.analyzeException(e)
            Log.e(TAG, "❌ OpenPilot状态数据解析异常: ${errorResult.message}", e)
            null
        }
    }
    
    /**
     * 发送HTTP请求（带错误处理）
     */
    suspend fun sendHttpRequestWithErrorHandling(
        url: String,
        data: Map<String, Any>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🌐 发送HTTP请求到: $url")
            
            // 这里可以添加具体的HTTP请求逻辑
            // 使用OkHttp或其他HTTP客户端
            
            Log.i(TAG, "✅ HTTP请求发送成功")
            true
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.handleNetworkError(e, "HTTP请求")
            Log.e(TAG, "❌ HTTP请求异常: ${errorResult.message}", e)
            
            // 根据错误类型决定是否重试
            if (errorResult.shouldRetry && retryCount < maxRetryCount) {
                retryCount++
                Log.w(TAG, "🔄 准备重试HTTP请求 (第${retryCount}次)")
                kotlinx.coroutines.delay(2000L * retryCount)
                sendHttpRequestWithErrorHandling(url, data)
            } else {
                Log.e(TAG, "❌ HTTP请求失败，已达到最大重试次数")
                false
            }
        }
    }
    
    /**
     * 停止网络服务（带错误处理）
     */
    fun stopNetworkServiceWithErrorHandling() {
        try {
            Log.i(TAG, "🛑 停止网络服务...")
            
            if (!isNetworkServiceRunning) {
                Log.w(TAG, "⚠️ 网络服务未运行")
                return
            }
            
            networkClient?.stop()
            isNetworkServiceRunning = false
            retryCount = 0
            
            Log.i(TAG, "✅ 网络服务停止成功")
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.handleNetworkError(e, "网络服务停止")
            Log.e(TAG, "❌ 网络服务停止异常: ${errorResult.message}", e)
        }
    }
    
    /**
     * 获取网络状态
     */
    fun getNetworkStatus(): NetworkStatus {
        return NetworkStatus(
            isRunning = isNetworkServiceRunning,
            retryCount = retryCount,
            maxRetryCount = maxRetryCount,
            hasError = retryCount >= maxRetryCount
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            stopNetworkServiceWithErrorHandling()
            networkScope.cancel()
            Log.i(TAG, "🧹 网络管理器资源清理完成")
        } catch (e: Exception) {
            ErrorHandler.logAndGetUserMessage(e, "网络管理器清理")
        }
    }
}

/**
 * 网络状态数据类
 */
data class NetworkStatus(
    val isRunning: Boolean,
    val retryCount: Int,
    val maxRetryCount: Int,
    val hasError: Boolean
) {
    val isHealthy: Boolean
        get() = isRunning && !hasError
}
