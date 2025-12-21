package com.example.carrotamap.utils

import android.content.SharedPreferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * 生命周期感知的工具类
 * 防止内存泄漏
 */

/**
 * 生命周期感知的 SharedPreferences 监听器
 * 自动在 onDestroy 时注销，防止内存泄漏
 * 
 * 使用示例：
 * ```kotlin
 * lifecycle.addObserver(
 *     LifecycleAwarePreferenceListener(prefs, listener)
 * )
 * ```
 */
class LifecycleAwarePreferenceListener(
    private val prefs: SharedPreferences,
    private val listener: SharedPreferences.OnSharedPreferenceChangeListener
) : DefaultLifecycleObserver {
    
    override fun onCreate(owner: LifecycleOwner) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        timber.log.Timber.d("✅ SharedPreferences 监听器已注册")
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        timber.log.Timber.d("🧹 SharedPreferences 监听器已注销")
    }
}

/**
 * 自动管理的资源持有者
 * 在 Lifecycle 销毁时自动清理资源
 */
class LifecycleAwareResource<T>(
    private val resource: T,
    private val cleanup: (T) -> Unit
) : DefaultLifecycleObserver {
    
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup(resource)
        timber.log.Timber.d("🧹 Resource cleaned up: ${resource!!::class.simpleName}")
    }
}

/**
 * Lifecycle 扩展函数 - 简化资源管理
 */
fun Lifecycle.addManagedResource(
    prefs: SharedPreferences,
    listener: SharedPreferences.OnSharedPreferenceChangeListener
) {
    addObserver(LifecycleAwarePreferenceListener(prefs, listener))
}
