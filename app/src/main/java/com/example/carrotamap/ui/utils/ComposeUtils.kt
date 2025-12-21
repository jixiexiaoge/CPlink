package com.example.carrotamap.ui.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Compose UI 性能优化工具
 */

/**
 * 记住 Preference 值并自动监听变化
 * 避免每次重组都读取 SharedPreferences
 * 
 * @param key Preference key
 * @param defaultValue 默认值
 * @return State<Int> 响应式状态
 * 
 * 使用示例：
 * ```kotlin
 * @Composable
 * fun MyComponent() {
 *     val overtakeMode by rememberPreference("overtake_mode", 0)
 *     // overtakeMode 自动更新
 * }
 * ```
 */
@Composable
fun rememberPreference(key: String, defaultValue: Int): State<Int> {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
    }
    
    val state = remember { mutableStateOf(prefs.getInt(key, defaultValue)) }
    
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = prefs.getInt(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    return state
}

/**
 * 记住 String Preference
 */
@Composable
fun rememberStringPreference(key: String, defaultValue: String): State<String> {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
    }
    
    val state = remember { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
    
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = prefs.getString(key, defaultValue) ?: defaultValue
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    return state
}

/**
 * 记住 Boolean Preference
 */
@Composable
fun rememberBooleanPreference(key: String, defaultValue: Boolean): State<Boolean> {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
    }
    
    val state = remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = prefs.getBoolean(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    return state
}
/**
 * 记住 Float Preference
 */
@Composable
fun rememberFloatPreference(key: String, defaultValue: Float): State<Float> {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("CarrotAmap", Context.MODE_PRIVATE)
    }
    
    val state = remember { mutableStateOf(prefs.getFloat(key, defaultValue)) }
    
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = prefs.getFloat(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    return state
}
