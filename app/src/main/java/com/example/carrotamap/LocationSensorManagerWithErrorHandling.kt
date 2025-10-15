package com.example.carrotamap

import android.content.Context
import android.location.Location
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * 带错误处理的位置传感器管理器
 * 增强原LocationSensorManager的错误处理能力
 */
class LocationSensorManagerWithErrorHandling(
    private val context: Context,
    private val carrotManFields: androidx.compose.runtime.MutableState<CarrotManFields>
) {
    companion object {
        private const val TAG = "LocationSensorManagerWithErrorHandling"
    }
    
    private val locationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isLocationUpdatesRunning = false
    private var retryCount = 0
    private val maxRetryCount = 3
    private var lastValidLocation: Location? = null
    private var consecutiveInvalidLocations = 0
    private val maxConsecutiveInvalidLocations = 5
    
    /**
     * 启动位置更新（带错误处理）
     */
    suspend fun startLocationUpdatesWithErrorHandling(): Boolean = withContext(Dispatchers.Main) {
        try {
            Log.i(TAG, "📍 启动位置更新服务...")
            
            if (isLocationUpdatesRunning) {
                Log.w(TAG, "⚠️ 位置更新服务已在运行")
                return@withContext true
            }
            
            // 检查位置权限
            if (!checkLocationPermissions()) {
                val errorResult = ErrorHandler.handlePermissionError("位置权限")
                Log.e(TAG, "❌ 位置权限检查失败: ${errorResult.message}")
                return@withContext false
            }
            
            // 启动位置更新
            val result = startLocationUpdatesInternal()
            if (result) {
                isLocationUpdatesRunning = true
                retryCount = 0
                consecutiveInvalidLocations = 0
                Log.i(TAG, "✅ 位置更新服务启动成功")
                true
            } else {
                Log.e(TAG, "❌ 位置更新服务启动失败")
                false
            }
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.handleLocationError(e, "位置更新服务启动")
            Log.e(TAG, "❌ 位置更新服务启动异常: ${errorResult.message}", e)
            
            // 根据错误类型决定是否重试
            if (errorResult.shouldRetry && retryCount < maxRetryCount) {
                retryCount++
                Log.w(TAG, "🔄 准备重试位置更新服务启动 (第${retryCount}次)")
                kotlinx.coroutines.delay(2000L * retryCount)
                startLocationUpdatesWithErrorHandling()
            } else {
                Log.e(TAG, "❌ 位置更新服务启动失败，已达到最大重试次数")
                false
            }
        }
    }
    
    /**
     * 内部位置更新启动方法
     */
    private suspend fun startLocationUpdatesInternal(): Boolean = withContext(Dispatchers.Main) {
        try {
            // 这里可以添加具体的位置更新逻辑
            // 使用LocationManager或FusedLocationProviderClient
            
            // 模拟位置更新
            locationScope.launch {
                while (isLocationUpdatesRunning) {
                    try {
                        // 获取位置数据
                        val location = getCurrentLocation()
                        if (location != null) {
                            updateCarrotManFieldsWithLocation(location)
                        }
                        
                        kotlinx.coroutines.delay(1000) // 1秒更新一次
                        
                    } catch (e: Exception) {
                        val errorResult = ErrorHandler.handleLocationError(e, "位置数据获取")
                        Log.e(TAG, "❌ 位置数据获取异常: ${errorResult.message}", e)
                        
                        if (errorResult.shouldRetry) {
                            kotlinx.coroutines.delay(2000)
                        } else {
                            break
                        }
                    }
                }
            }
            
            true
            
        } catch (e: Exception) {
            ErrorHandler.handleLocationError(e, "位置更新内部启动")
            false
        }
    }
    
    /**
     * 获取当前位置
     */
    private suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            // 这里可以添加具体的位置获取逻辑
            // 使用LocationManager或FusedLocationProviderClient
            
            // 模拟位置数据
            val location = Location("mock")
            location.latitude = 39.916527
            location.longitude = 116.397128
            location.accuracy = 5.0f
            location.time = System.currentTimeMillis()
            
            // 验证位置数据
            if (isValidLocation(location)) {
                lastValidLocation = location
                consecutiveInvalidLocations = 0
                Log.d(TAG, "✅ 获取到有效位置: ${location.latitude}, ${location.longitude}")
                location
            } else {
                consecutiveInvalidLocations++
                Log.w(TAG, "⚠️ 获取到无效位置 (连续${consecutiveInvalidLocations}次)")
                
                if (consecutiveInvalidLocations >= maxConsecutiveInvalidLocations) {
                    Log.e(TAG, "❌ 连续获取无效位置次数过多，停止位置更新")
                    isLocationUpdatesRunning = false
                }
                
                null
            }
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.handleLocationError(e, "位置获取")
            Log.e(TAG, "❌ 位置获取异常: ${errorResult.message}", e)
            null
        }
    }
    
    /**
     * 验证位置数据
     */
    private fun isValidLocation(location: Location): Boolean {
        return try {
            // 检查坐标是否有效
            if (location.latitude == 0.0 && location.longitude == 0.0) {
                Log.w(TAG, "⚠️ 位置坐标为(0,0)，可能无效")
                return false
            }
            
            // 检查坐标是否在中国范围内
            if (location.latitude < 3.0 || location.latitude > 54.0 ||
                location.longitude < 73.0 || location.longitude > 136.0) {
                Log.w(TAG, "⚠️ 位置坐标超出中国范围: ${location.latitude}, ${location.longitude}")
                return false
            }
            
            // 检查精度
            if (location.accuracy > 100.0f) {
                Log.w(TAG, "⚠️ 位置精度过低: ${location.accuracy}m")
                return false
            }
            
            // 检查时间戳
            val currentTime = System.currentTimeMillis()
            val locationTime = location.time
            if (currentTime - locationTime > 30000) { // 30秒
                Log.w(TAG, "⚠️ 位置数据过期: ${(currentTime - locationTime) / 1000}秒前")
                return false
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 位置验证异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * 更新CarrotManFields位置数据
     */
    private fun updateCarrotManFieldsWithLocation(location: Location) {
        try {
            val currentFields = carrotManFields.value
            
            // 更新位置数据
            carrotManFields.value = currentFields.copy(
                goalPosX = location.longitude,
                goalPosY = location.latitude,
                dataQuality = "good"
            )
            
            Log.d(TAG, "✅ 位置数据已更新: ${location.latitude}, ${location.longitude}")
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.handleLocationError(e, "位置数据更新")
            Log.e(TAG, "❌ 位置数据更新异常: ${errorResult.message}", e)
        }
    }
    
    /**
     * 检查位置权限
     */
    private fun checkLocationPermissions(): Boolean {
        return try {
            // 这里可以添加具体的权限检查逻辑
            // 使用ContextCompat.checkSelfPermission
            
            Log.d(TAG, "🔐 检查位置权限...")
            true // 模拟权限已授予
            
        } catch (e: Exception) {
            ErrorHandler.handlePermissionError("位置权限")
            false
        }
    }
    
    /**
     * 停止位置更新（带错误处理）
     */
    fun stopLocationUpdatesWithErrorHandling() {
        try {
            Log.i(TAG, "🛑 停止位置更新服务...")
            
            if (!isLocationUpdatesRunning) {
                Log.w(TAG, "⚠️ 位置更新服务未运行")
                return
            }
            
            isLocationUpdatesRunning = false
            retryCount = 0
            consecutiveInvalidLocations = 0
            
            Log.i(TAG, "✅ 位置更新服务停止成功")
            
        } catch (e: Exception) {
            val errorResult = ErrorHandler.handleLocationError(e, "位置更新服务停止")
            Log.e(TAG, "❌ 位置更新服务停止异常: ${errorResult.message}", e)
        }
    }
    
    /**
     * 获取位置状态
     */
    fun getLocationStatus(): LocationStatus {
        return LocationStatus(
            isRunning = isLocationUpdatesRunning,
            retryCount = retryCount,
            maxRetryCount = maxRetryCount,
            hasError = retryCount >= maxRetryCount,
            consecutiveInvalidLocations = consecutiveInvalidLocations,
            lastValidLocation = lastValidLocation
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            stopLocationUpdatesWithErrorHandling()
            locationScope.cancel()
            Log.i(TAG, "🧹 位置传感器管理器资源清理完成")
        } catch (e: Exception) {
            ErrorHandler.logAndGetUserMessage(e, "位置传感器管理器清理")
        }
    }
}

/**
 * 位置状态数据类
 */
data class LocationStatus(
    val isRunning: Boolean,
    val retryCount: Int,
    val maxRetryCount: Int,
    val hasError: Boolean,
    val consecutiveInvalidLocations: Int,
    val lastValidLocation: Location?
) {
    val isHealthy: Boolean
        get() = isRunning && !hasError && consecutiveInvalidLocations < 5
}
