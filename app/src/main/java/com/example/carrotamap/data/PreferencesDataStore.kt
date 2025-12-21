package com.example.carrotamap.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore 使用示例
 * 
 * DataStore 是 SharedPreferences 的现代替代方案，提供：
 * - 类型安全
 * - 异步API（基于Kotlin Flow）
 * - 数据一致性保证
 * - 更好的错误处理
 * 
 * 这是一个示例实现，展示如何使用DataStore。
 * 可以根据需要逐步迁移SharedPreferences到DataStore。
 */

// Context扩展属性，创建DataStore实例
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置数据存储管理器
 * 
 * 使用示例：
 * ```kotlin
 * // 注入或创建实例
 * val prefsDataStore = PreferencesDataStore(context)
 * 
 * // 读取数据（Flow）
 * prefsDataStore.overtakeMode.collect { mode ->
 *     println("当前超车模式: $mode")
 * }
 * 
 * // 写入数据（挂起函数）
 * viewModelScope.launch {
 *     prefsDataStore.setOvertakeMode(2)
 * }
 * ```
 */
class PreferencesDataStore(private val context: Context) {
    
    private val dataStore = context.settingsDataStore
    
    companion object {
        // 定义所有的Preferences Keys
        val OVERTAKE_MODE = intPreferencesKey("overtake_mode")
        val MIN_OVERTAKE_SPEED = intPreferencesKey("min_overtake_speed_kph")
        val SPEED_DIFF_THRESHOLD = intPreferencesKey("speed_diff_threshold_kph")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val USER_TYPE = intPreferencesKey("user_type")
        val LAST_CONNECTED_DEVICE = stringPreferencesKey("last_connected_device")
        val AUTO_SEND_ENABLED = booleanPreferencesKey("auto_send_enabled")
    }
    
    // ===============================
    // 读取数据（Flow）
    // ===============================
    
    /**
     * 超车模式
     * 0 = 禁用，1 = 拨杆超车，2 = 自动超车
     */
    val overtakeMode: Flow<Int> = dataStore.data.map { prefs ->
        prefs[OVERTAKE_MODE] ?: 0
    }
    
    /**
     * 最小超车速度（km/h）
     */
    val minOvertakeSpeed: Flow<Int> = dataStore.data.map { prefs ->
        prefs[MIN_OVERTAKE_SPEED] ?: 65
    }
    
    /**
     * 速度差阈值（km/h）
     */
    val speedDiffThreshold: Flow<Int> = dataStore.data.map { prefs ->
        prefs[SPEED_DIFF_THRESHOLD] ?: 20
    }
    
    /**
     * 设备ID
     */
    val deviceId: Flow<String> = dataStore.data.map { prefs ->
        prefs[DEVICE_ID] ?: ""
    }
    
    /**
     * 用户类型
     */
    val userType: Flow<Int> = dataStore.data.map { prefs ->
        prefs[USER_TYPE] ?: 0
    }
    
    /**
     * 上次连接的设备
     */
    val lastConnectedDevice: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_CONNECTED_DEVICE] ?: ""
    }
    
    /**
     * 自动发送是否启用
     */
    val autoSendEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_SEND_ENABLED] ?: true
    }
    
    // ===============================
    // 写入数据（挂起函数）
    // ===============================
    
    /**
     * 设置超车模式
     */
    suspend fun setOvertakeMode(mode: Int) {
        dataStore.edit { prefs ->
            prefs[OVERTAKE_MODE] = mode
        }
    }
    
    /**
     * 设置最小超车速度
     */
    suspend fun setMinOvertakeSpeed(speed: Int) {
        dataStore.edit { prefs ->
            prefs[MIN_OVERTAKE_SPEED] = speed
        }
    }
    
    /**
     * 设置速度差阈值
     */
    suspend fun setSpeedDiffThreshold(threshold: Int) {
        dataStore.edit { prefs ->
            prefs[SPEED_DIFF_THRESHOLD] = threshold
        }
    }
    
    /**
     * 设置设备ID
     */
    suspend fun setDeviceId(id: String) {
        dataStore.edit { prefs ->
            prefs[DEVICE_ID] = id
        }
    }
    
    /**
     * 设置用户类型
     */
    suspend fun setUserType(type: Int) {
        dataStore.edit { prefs ->
            prefs[USER_TYPE] = type
        }
    }
    
    /**
     * 设置上次连接的设备
     */
    suspend fun setLastConnectedDevice(device: String) {
        dataStore.edit { prefs ->
            prefs[LAST_CONNECTED_DEVICE] = device
        }
    }
    
    /**
     * 设置自动发送启用状态
     */
    suspend fun setAutoSendEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_SEND_ENABLED] = enabled
        }
    }
    
    /**
     * 批量更新设置
     */
    suspend fun updateSettings(
        overtakeMode: Int? = null,
        minSpeed: Int? = null,
        speedDiff: Int? = null
    ) {
        dataStore.edit { prefs ->
            overtakeMode?.let { prefs[OVERTAKE_MODE] = it }
            minSpeed?.let { prefs[MIN_OVERTAKE_SPEED] = it }
            speedDiff?.let { prefs[SPEED_DIFF_THRESHOLD] = it }
        }
    }
    
    /**
     * 清除所有设置
     */
    suspend fun clearAllSettings() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

/**
 * DataStore vs SharedPreferences 对比
 * 
 * | 特性 | SharedPreferences | DataStore |
 * |------|-------------------|-----------|
 * | API类型 | 同步 | 异步（协程） |
 * | 类型安全 | ❌ 弱 | ✅ 强 |
 * | 错误处理 | ❌ 运行时异常 | ✅ 可处理异常 |
 * | 数据一致性 | ⚠️ 可能不一致 | ✅ 保证一致性 |
 * | 主线程安全 | ❌ 可能ANR | ✅ 完全安全 |
 * | 响应式 | ❌ 需要监听器 | ✅ Flow自动更新 |
 * 
 * 迁移建议：
 * 1. 新功能直接使用DataStore
 * 2. 旧代码保持SharedPreferences（向后兼容）
 * 3. 逐步迁移关键设置到DataStore
 * 4. 两者可以并存，不冲突
 */
