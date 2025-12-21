package com.example.carrotamap.network

import com.example.carrotamap.core.ErrorCode
import com.example.carrotamap.core.Result
import com.example.carrotamap.core.runSafely
import kotlinx.coroutines.delay
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络请求封装工具
 * 提供重试、超时、错误处理等功能
 */
object NetworkRequest {
    
    /**
     * 带重试的网络请求
     * 
     * @param maxRetries 最大重试次数
     * @param retryDelay 初始重试延迟（毫秒）
     * @param block 网络请求代码块
     * @return Result<T>
     * 
     * 使用示例：
     * ```kotlin
     * val result = NetworkRequest.withRetry(maxRetries = 3) {
     *     apiCall()
     * }
     * ```
     */
    suspend fun <T> withRetry(
        maxRetries: Int = 3,
        retryDelay: Long = 1000L,
        block: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            val result = runSafely(ErrorCode.NETWORK_CONNECTION_FAILED) {
                block()
            }
            
            when (result) {
                is Result.Success -> {
                    if (attempt > 0) {
                        Timber.i("✅ 请求成功（重试 $attempt 次后）")
                    }
                    return result
                }
                is Result.Error -> {
                    lastException = result.exception
                    
                    // 某些错误不需要重试
                    if (!shouldRetry(result.exception)) {
                        Timber.w("❌ 请求失败（不可重试）: ${result.message}")
                        return result
                    }
                    
                    // 非最后一次尝试时延迟
                    if (attempt < maxRetries - 1) {
                        val currentDelay = retryDelay * (attempt + 1) // 线性退避
                        Timber.d("⏳ 请求失败，${currentDelay}ms 后重试（第 ${attempt + 1}/${maxRetries} 次）")
                        delay(currentDelay)
                    }
                }
                else -> {}
            }
        }
        
        Timber.e("❌ 请求最终失败（已重试 $maxRetries 次）")
        return Result.Error(
            lastException ?: Exception("Unknown error"),
            ErrorCode.NETWORK_CONNECTION_FAILED,
            "请求失败（已重试${maxRetries}次）"
        )
    }
    
    /**
     * 判断是否应该重试
     */
    private fun shouldRetry(exception: Exception): Boolean {
        return when (exception) {
            is SocketTimeoutException -> true  // 超时可重试
            is UnknownHostException -> false   // 主机不存在不重试
            else -> exception.message?.contains("timeout", ignoreCase = true) == true
        }
    }
}

/**
 * 网络请求统计
 */
data class NetworkRequestStats(
    var totalRequests: Int = 0,
    var successfulRequests: Int = 0,
    var failedRequests: Int = 0,
    var retriedRequests: Int = 0,
    var totalRetries: Int = 0
) {
    fun getSuccessRate(): Float {
        return if (totalRequests > 0) {
            (successfulRequests.toFloat() / totalRequests) * 100
        } else 0f
    }
    
    fun getAverageRetries(): Float {
        return if (retriedRequests > 0) {
            totalRetries.toFloat() / retriedRequests
        } else 0f
    }
    
    fun formatStats(): String {
        return """
            总请求: $totalRequests
            成功: $successfulRequests (${String.format("%.1f", getSuccessRate())}%)
            失败: $failedRequests
            需重试: $retriedRequests (平均 ${String.format("%.1f", getAverageRetries())} 次)
        """.trimIndent()
    }
}
