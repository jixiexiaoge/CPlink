package com.example.carrotamap.utils

import com.example.carrotamap.core.ErrorReporterInstance

/**
 * 统一的应用日志工具
 * 
 * 功能：
 * - 统一的日志接口
 * - Debug/Release 分离
 * - 自动错误上报
 * - 敏感信息脱敏
 */
object AppLogger {
    
    @PublishedApi
    internal const val DEFAULT_TAG = "CarrotAmap"
    
    // 简单的debug模式判断
    private val isDebug: Boolean = try {
        Class.forName("${this::class.java.`package`?.name}.BuildConfig")
            .getField("DEBUG")
            .getBoolean(null)
    } catch (e: Exception) {
        true // 默认为debug模式
    }
    
    /**
     * Debug 日志（仅 Debug 版本输出）
     */
    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (isDebug) {
            timber.log.Timber.tag(tag).d(message)
        }
    }
    
    /**
     * Info 日志
     */
    fun i(tag: String = DEFAULT_TAG, message: String) {
        timber.log.Timber.tag(tag).i(message)
    }
    
    /**
     * Warning 日志
     */
    fun w(tag: String = DEFAULT_TAG, message: String) {
        timber.log.Timber.tag(tag).w(message)
    }
    
    /**
     * Error 日志（自动上报）
     */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        timber.log.Timber.tag(tag).e(throwable, message)
        
        // Release 版本自动上报错误
        if (!isDebug && throwable is Exception) {
            ErrorReporterInstance.reporter.reportException(throwable, tag)
        }
    }
    
    /**
     * 网络日志（自动脱敏）
     */
    fun network(tag: String = DEFAULT_TAG, action: String, details: String) {
        if (isDebug) {
            val sanitized = sanitizeData(details)
            timber.log.Timber.tag(tag).d("🌐 Network: $action -> $sanitized")
        }
    }
    
    /**
     * 性能日志
     */
    fun perf(tag: String = DEFAULT_TAG, operation: String, durationMs: Long) {
        if (isDebug) {
            timber.log.Timber.tag(tag).d("⚡ Perf: $operation took ${durationMs}ms")
        }
    }
    
    /**
     * 业务逻辑日志（带Emoji标记）
     */
    fun business(tag: String = DEFAULT_TAG, event: String, details: String = "") {
        val message = if (details.isNotEmpty()) {
            "📊 Business: $event - $details"
        } else {
            "📊 Business: $event"
        }
        timber.log.Timber.tag(tag).i(message)
    }
    
    /**
     * 脱敏处理
     */
    private fun sanitizeData(data: String): String {
        return data
            .replace(Regex("(password|token|secret)\\s*[=:]\\s*\\S+", RegexOption.IGNORE_CASE), "$1=***")
            .replace(Regex("\\d{11}"), "***") // 手机号
            .replace(Regex("\\d{15,18}"), "***") // 身份证号
    }

    /**
     * 测量代码块执行时间
     */
    inline fun <T> measurePerf(tag: String = DEFAULT_TAG, operation: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block()
        } finally {
            val end = System.currentTimeMillis()
            perf(tag, operation, end - start)
        }
    }
}

/**
 * 扩展函数 - 简化使用
 */
fun Any.logd(message: String) {
    AppLogger.d(this::class.simpleName ?: "Unknown", message)
}

fun Any.logi(message: String) {
    AppLogger.i(this::class.simpleName ?: "Unknown", message)
}

fun Any.loge(message: String, throwable: Throwable? = null) {
    AppLogger.e(this::class.simpleName ?: "Unknown", message, throwable)
}
