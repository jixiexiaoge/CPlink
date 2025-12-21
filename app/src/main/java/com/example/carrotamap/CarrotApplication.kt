package com.example.carrotamap

import android.app.Application
import android.util.Log
import com.example.carrotamap.core.ErrorReporterInstance
import com.example.carrotamap.core.LocalErrorReporter
import com.example.carrotamap.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * CarrotMap 应用程序类
 * 
 * 职责：
 * 1. 初始化 Koin 依赖注入框架
 * 2. 初始化全局配置（错误上报、日志等）
 * 3. 应用级别的生命周期管理
 * 
 * 注意：这是可选的升级路径，不影响现有功能
 * 旧代码可以继续使用直接实例化的方式
 */
class CarrotApplication : Application() {
    
    companion object {
        private const val TAG = "CarrotApplication"
        
        /**
         * Feature Flag: 是否启用新架构
         * 设置为 false 时，Koin 仅初始化但不强制使用
         */
        const val USE_DI_ARCHITECTURE = false
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "🚀 CarrotApplication 初始化...")
        
        // 初始化 Timber 日志系统
        initializeTimber()
        
        // 初始化错误上报
        initializeErrorReporting()
        
        // 初始化 Koin 依赖注入（可选）
        if (USE_DI_ARCHITECTURE) {
            initializeKoin()
        } else {
            Log.i(TAG, "⚠️ 依赖注入未启用（USE_DI_ARCHITECTURE = false）")
            Log.i(TAG, "   旧代码路径继续使用，新架构作为可选项")
        }
        
        Log.i(TAG, "✅ CarrotApplication 初始化完成")
    }
    
    /**
     * 初始化 Timber 日志系统
     * Debug 版本输出详细日志，Release 版本只记录错误
     */
    private fun initializeTimber() {
        try {
            // 移除所有已有的 Tree
            timber.log.Timber.uprootAll()
            
            // Debug 版本：输出所有日志
            timber.log.Timber.plant(timber.log.Timber.DebugTree())
            
            // 也可以添加自定义的 Release Tree
            // if (!BuildConfig.DEBUG) {
            //     Timber.plant(CrashReportingTree())
            // }
            
            timber.log.Timber.d("✅ Timber 日志系统初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Timber 初始化失败", e)
        }
    }
    
    /**
     * 初始化错误上报机制
     */
    private fun initializeErrorReporting() {
        try {
            // 设置本地错误上报器
            ErrorReporterInstance.setReporter(LocalErrorReporter())
            
            // TODO: 后续可替换为 Firebase Crashlytics
            // if (BuildConfig.USE_CRASHLYTICS) {
            //     ErrorReporterInstance.setReporter(FirebaseCrashlyticsReporter())
            // }
            
            Log.d(TAG, "✅ 错误上报机制初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 错误上报初始化失败", e)
        }
    }
    
    /**
     * 初始化 Koin 依赖注入框架
     */
    private fun initializeKoin() {
        try {
            startKoin {
                // 日志级别（可以通过配置控制）
                androidLogger(Level.INFO)  // Release 版本可设为 Level.NONE
                
                // Android Context
                androidContext(this@CarrotApplication)
                
                // 加载模块
                modules(appModule)
            }
            
            Log.i(TAG, "✅ Koin 依赖注入初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Koin 初始化失败", e)
            // 不抛出异常，让应用继续运行（降级到旧架构）
        }
    }
}
