package com.example.carrotamap

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*

/**
 * 应用状态管理器
 * 负责管理应用的整体状态，分离MainActivity的职责
 */
class AppStateManager(
    private val context: Context,
    private val carrotManFields: MutableState<CarrotManFields>
) {
    companion object {
        private const val TAG = "AppStateManager"
    }
    
    // 应用状态
    val isInitialized = mutableStateOf(false)
    val initializationProgress = mutableStateOf(0)
    val currentError = mutableStateOf<String?>(null)
    val isNetworkConnected = mutableStateOf(false)
    val isLocationEnabled = mutableStateOf(false)
    
    // 协程作用域
    private val stateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 初始化应用状态
     */
    suspend fun initializeApp(): Boolean = withContext(Dispatchers.Main) {
        try {
            Log.i(TAG, "🚀 开始初始化应用状态...")
            updateProgress(10, "开始初始化")
            
            // 检查权限状态
            updateProgress(20, "检查权限状态")
            val permissionStatus = checkPermissions()
            if (!permissionStatus) {
                setError("权限检查失败")
                return@withContext false
            }
            
            // 检查网络状态
            updateProgress(40, "检查网络状态")
            val networkStatus = checkNetworkStatus()
            isNetworkConnected.value = networkStatus
            
            // 检查位置服务
            updateProgress(60, "检查位置服务")
            val locationStatus = checkLocationService()
            isLocationEnabled.value = locationStatus
            
            // 初始化完成
            updateProgress(100, "初始化完成")
            isInitialized.value = true
            clearError()
            
            Log.i(TAG, "✅ 应用状态初始化完成")
            true
            
        } catch (e: Exception) {
            val errorMessage = ErrorHandler.logAndGetUserMessage(e, "应用状态初始化")
            setError(errorMessage)
            Log.e(TAG, "❌ 应用状态初始化失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 检查权限状态
     */
    private suspend fun checkPermissions(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 这里可以添加具体的权限检查逻辑
            Log.d(TAG, "🔐 检查权限状态")
            true
        } catch (e: Exception) {
            ErrorHandler.handlePermissionError("位置权限")
            false
        }
    }
    
    /**
     * 检查网络状态
     */
    private suspend fun checkNetworkStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 这里可以添加具体的网络状态检查逻辑
            Log.d(TAG, "🌐 检查网络状态")
            true
        } catch (e: Exception) {
            ErrorHandler.handleNetworkError(e, "网络状态检查")
            false
        }
    }
    
    /**
     * 检查位置服务状态
     */
    private suspend fun checkLocationService(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 这里可以添加具体的位置服务检查逻辑
            Log.d(TAG, "📍 检查位置服务状态")
            true
        } catch (e: Exception) {
            ErrorHandler.handleLocationError(e, "位置服务检查")
            false
        }
    }
    
    /**
     * 更新初始化进度
     */
    private fun updateProgress(progress: Int, message: String) {
        initializationProgress.value = progress
        Log.i(TAG, "📊 初始化进度: $progress% - $message")
    }
    
    /**
     * 设置错误状态
     */
    fun setError(message: String) {
        currentError.value = message
        Log.e(TAG, "❌ 应用错误: $message")
    }
    
    /**
     * 清除错误状态
     */
    fun clearError() {
        currentError.value = null
        Log.d(TAG, "✅ 错误状态已清除")
    }
    
    /**
     * 重置应用状态
     */
    fun resetState() {
        isInitialized.value = false
        initializationProgress.value = 0
        currentError.value = null
        isNetworkConnected.value = false
        isLocationEnabled.value = false
        Log.i(TAG, "🔄 应用状态已重置")
    }
    
    /**
     * 获取应用健康状态
     */
    fun getAppHealthStatus(): AppHealthStatus {
        return AppHealthStatus(
            isInitialized = isInitialized.value,
            progress = initializationProgress.value,
            hasError = currentError.value != null,
            errorMessage = currentError.value,
            isNetworkConnected = isNetworkConnected.value,
            isLocationEnabled = isLocationEnabled.value
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stateScope.cancel()
        Log.i(TAG, "🧹 应用状态管理器已清理")
    }
}

/**
 * 应用健康状态数据类
 */
data class AppHealthStatus(
    val isInitialized: Boolean,
    val progress: Int,
    val hasError: Boolean,
    val errorMessage: String?,
    val isNetworkConnected: Boolean,
    val isLocationEnabled: Boolean
) {
    val isHealthy: Boolean
        get() = isInitialized && !hasError && isNetworkConnected && isLocationEnabled
}
