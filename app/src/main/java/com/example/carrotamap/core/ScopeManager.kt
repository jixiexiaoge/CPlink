package com.example.carrotamap.core

import kotlinx.coroutines.*
import timber.log.Timber

/**
 * 协程作用域管理器
 * 统一管理应用中的所有协程作用域，确保结构化并发
 */
object ScopeManager {
    
    private val TAG = "ScopeManager"
    
    /**
     * 应用级作用域 - 生命周期与应用相同
     * 使用 SupervisorJob 确保子协程失败不影响其他协程
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * 网络作用域 - 专用于后台网络请求
     */
    val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * UI全局作用域 - 跨页面的UI后台逻辑
     */
    val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    /**
     * 创建临时受监督的作用域
     */
    fun createTemporaryScope(dispatcher: CoroutineDispatcher = Dispatchers.Default): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcher)
    }
    
    /**
     * 取消所有活跃的作用域
     * 通常在应用退出或系统回收时调用
     */
    fun cancelAll() {
        try {
            Timber.i("🧹 正在取消所有全局协程作用域...")
            applicationScope.cancel()
            networkScope.cancel()
            uiScope.cancel()
        } catch (e: Exception) {
            Timber.e(e, "❌ 取消作用域失败")
        }
    }
}
