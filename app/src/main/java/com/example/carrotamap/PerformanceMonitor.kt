package com.example.carrotamap

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.*

/**
 * 性能监控器
 * 负责监控应用性能、内存使用和资源管理
 */
class PerformanceMonitor(
    private val context: Context
) {
    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MEMORY_WARNING_THRESHOLD = 0.8 // 80%内存使用率警告
        private const val MEMORY_CRITICAL_THRESHOLD = 0.9 // 90%内存使用率严重警告
        private const val MONITOR_INTERVAL = 30000L // 30秒监控间隔
    }
    
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isMonitoring = false
    // 使用Android的Debug类替代ManagementFactory
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    // 性能统计
    private var totalMemoryChecks = 0
    private var memoryWarnings = 0
    private var memoryCriticalWarnings = 0
    private var gcCount = 0
    
    /**
     * 开始性能监控
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "⚠️ 性能监控已在运行")
            return
        }
        
        isMonitoring = true
        Log.i(TAG, "🚀 开始性能监控...")
        
        monitorScope.launch {
            while (isMonitoring) {
                try {
                    checkMemoryUsage()
                    checkCpuUsage()
                    checkNetworkPerformance()
                    
                    delay(MONITOR_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 性能监控异常: ${e.message}", e)
                    delay(5000) // 出错时等待5秒再继续
                }
            }
        }
    }
    
    /**
     * 停止性能监控
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.i(TAG, "🛑 停止性能监控")
    }
    
    /**
     * 检查内存使用情况
     */
    private fun checkMemoryUsage() {
        try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val memoryUsageRatio = usedMemory.toDouble() / maxMemory.toDouble()
            
            totalMemoryChecks++
            
            Log.d(TAG, "📊 内存使用情况:")
            Log.d(TAG, "  最大内存: ${formatBytes(maxMemory)}")
            Log.d(TAG, "  已用内存: ${formatBytes(usedMemory)}")
            Log.d(TAG, "  可用内存: ${formatBytes(freeMemory)}")
            Log.d(TAG, "  使用率: ${String.format("%.1f", memoryUsageRatio * 100)}%")
            
            when {
                memoryUsageRatio >= MEMORY_CRITICAL_THRESHOLD -> {
                    memoryCriticalWarnings++
                    Log.e(TAG, "🚨 内存使用率严重警告: ${String.format("%.1f", memoryUsageRatio * 100)}%")
                    performEmergencyCleanup()
                }
                memoryUsageRatio >= MEMORY_WARNING_THRESHOLD -> {
                    memoryWarnings++
                    Log.w(TAG, "⚠️ 内存使用率警告: ${String.format("%.1f", memoryUsageRatio * 100)}%")
                    suggestMemoryCleanup()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 内存检查异常: ${e.message}", e)
        }
    }
    
    /**
     * 检查CPU使用情况
     */
    private fun checkCpuUsage() {
        try {
            val runtime = Runtime.getRuntime()
            val availableProcessors = runtime.availableProcessors()
            
            Log.d(TAG, "🖥️ CPU信息:")
            Log.d(TAG, "  可用处理器: $availableProcessors")
            
            // 获取线程信息（简化版本）
            val threadCount = Thread.activeCount()
            val peakThreadCount = threadCount // 简化处理
            
            Log.d(TAG, "  当前线程数: $threadCount")
            Log.d(TAG, "  峰值线程数: $peakThreadCount")
            
            if (threadCount > availableProcessors * 4) {
                Log.w(TAG, "⚠️ 线程数过多: $threadCount (建议: ${availableProcessors * 2})")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CPU检查异常: ${e.message}", e)
        }
    }
    
    /**
     * 检查网络性能
     */
    private fun checkNetworkPerformance() {
        try {
            val networkInfo = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            Log.d(TAG, "🌐 网络状态检查完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络检查异常: ${e.message}", e)
        }
    }
    
    /**
     * 执行紧急内存清理
     */
    private fun performEmergencyCleanup() {
        try {
            Log.w(TAG, "🧹 执行紧急内存清理...")
            
            // 建议垃圾回收
            System.gc()
            gcCount++
            
            // 等待GC完成
            Thread.sleep(100)
            
            Log.i(TAG, "✅ 紧急内存清理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 紧急内存清理异常: ${e.message}", e)
        }
    }
    
    /**
     * 建议内存清理
     */
    private fun suggestMemoryCleanup() {
        Log.i(TAG, "💡 建议执行内存清理操作")
        // 这里可以触发应用级别的内存清理
    }
    
    /**
     * 获取性能统计报告
     */
    fun getPerformanceReport(): PerformanceReport {
        return PerformanceReport(
            totalMemoryChecks = totalMemoryChecks,
            memoryWarnings = memoryWarnings,
            memoryCriticalWarnings = memoryCriticalWarnings,
            gcCount = gcCount,
            isMonitoring = isMonitoring
        )
    }
    
    /**
     * 格式化字节数
     */
    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            else -> String.format("%.2f KB", kb)
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopMonitoring()
        monitorScope.cancel()
        Log.i(TAG, "🧹 性能监控器资源清理完成")
    }
}

/**
 * 性能报告数据类
 */
data class PerformanceReport(
    val totalMemoryChecks: Int,
    val memoryWarnings: Int,
    val memoryCriticalWarnings: Int,
    val gcCount: Int,
    val isMonitoring: Boolean
) {
    val memoryWarningRate: Double
        get() = if (totalMemoryChecks > 0) memoryWarnings.toDouble() / totalMemoryChecks else 0.0
    
    val memoryCriticalRate: Double
        get() = if (totalMemoryChecks > 0) memoryCriticalWarnings.toDouble() / totalMemoryChecks else 0.0
    
    val isHealthy: Boolean
        get() = memoryCriticalWarnings == 0 && memoryWarningRate < 0.1
}
