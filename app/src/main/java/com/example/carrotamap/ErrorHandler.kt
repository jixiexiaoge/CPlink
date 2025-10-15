package com.example.carrotamap

import android.util.Log
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import org.json.JSONException

/**
 * 错误处理器
 * 统一处理应用中的各种异常和错误
 */
object ErrorHandler {
    private const val TAG = "ErrorHandler"
    
    /**
     * 错误类型枚举
     */
    enum class ErrorType {
        NETWORK_TIMEOUT,      // 网络超时
        NETWORK_CONNECTION,   // 网络连接错误
        JSON_PARSE,          // JSON解析错误
        PERMISSION_DENIED,   // 权限被拒绝
        LOCATION_ERROR,      // 位置服务错误
        UNKNOWN_ERROR        // 未知错误
    }
    
    /**
     * 错误处理结果
     */
    data class ErrorResult(
        val type: ErrorType,
        val message: String,
        val shouldRetry: Boolean,
        val userMessage: String
    )
    
    /**
     * 分析异常并返回处理结果
     */
    fun analyzeException(exception: Exception): ErrorResult {
        return when (exception) {
            is SocketTimeoutException -> ErrorResult(
                type = ErrorType.NETWORK_TIMEOUT,
                message = "网络连接超时: ${exception.message}",
                shouldRetry = true,
                userMessage = "网络连接超时，请检查网络连接后重试"
            )
            is UnknownHostException -> ErrorResult(
                type = ErrorType.NETWORK_CONNECTION,
                message = "无法解析主机地址: ${exception.message}",
                shouldRetry = true,
                userMessage = "网络连接失败，请检查网络设置"
            )
            is ConnectException -> ErrorResult(
                type = ErrorType.NETWORK_CONNECTION,
                message = "连接被拒绝: ${exception.message}",
                shouldRetry = true,
                userMessage = "无法连接到服务器，请稍后重试"
            )
            is JSONException -> ErrorResult(
                type = ErrorType.JSON_PARSE,
                message = "JSON解析错误: ${exception.message}",
                shouldRetry = false,
                userMessage = "数据格式错误，请联系技术支持"
            )
            is SecurityException -> ErrorResult(
                type = ErrorType.PERMISSION_DENIED,
                message = "权限被拒绝: ${exception.message}",
                shouldRetry = false,
                userMessage = "权限不足，请在设置中授予必要权限"
            )
            else -> ErrorResult(
                type = ErrorType.UNKNOWN_ERROR,
                message = "未知错误: ${exception.message}",
                shouldRetry = false,
                userMessage = "发生未知错误，请重启应用"
            )
        }
    }
    
    /**
     * 处理网络错误
     */
    fun handleNetworkError(exception: Exception, operation: String): ErrorResult {
        val result = analyzeException(exception)
        Log.e(TAG, "网络操作失败: $operation - ${result.message}", exception)
        return result
    }
    
    /**
     * 处理位置服务错误
     */
    fun handleLocationError(exception: Exception, operation: String): ErrorResult {
        val result = when (exception) {
            is SecurityException -> ErrorResult(
                type = ErrorType.PERMISSION_DENIED,
                message = "位置权限被拒绝: ${exception.message}",
                shouldRetry = false,
                userMessage = "位置权限被拒绝，请在设置中开启位置权限"
            )
            else -> ErrorResult(
                type = ErrorType.LOCATION_ERROR,
                message = "位置服务错误: ${exception.message}",
                shouldRetry = true,
                userMessage = "位置服务异常，请检查GPS设置"
            )
        }
        Log.e(TAG, "位置服务操作失败: $operation - ${result.message}", exception)
        return result
    }
    
    /**
     * 处理权限错误
     */
    fun handlePermissionError(permission: String): ErrorResult {
        val result = ErrorResult(
            type = ErrorType.PERMISSION_DENIED,
            message = "权限被拒绝: $permission",
            shouldRetry = false,
            userMessage = "权限被拒绝，请在设置中授予权限"
        )
        Log.w(TAG, "权限检查失败: $permission")
        return result
    }
    
    /**
     * 记录错误并返回用户友好的消息
     */
    fun logAndGetUserMessage(exception: Exception, context: String): String {
        val result = analyzeException(exception)
        Log.e(TAG, "错误发生在: $context", exception)
        return result.userMessage
    }
    
    /**
     * 检查是否应该重试操作
     */
    fun shouldRetry(exception: Exception): Boolean {
        return analyzeException(exception).shouldRetry
    }
    
    /**
     * 获取错误类型
     */
    fun getErrorType(exception: Exception): ErrorType {
        return analyzeException(exception).type
    }
}
