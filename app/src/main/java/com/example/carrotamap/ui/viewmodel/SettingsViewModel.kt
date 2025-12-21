package com.example.carrotamap.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carrotamap.core.ErrorCode
import com.example.carrotamap.core.Result
import com.example.carrotamap.core.runSafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置页面 ViewModel 示例
 * 
 * 这是一个示例 ViewModel，展示如何使用现代 Android 架构：
 * - ViewModel 管理 UI 状态
 * - StateFlow 提供响应式数据流
 * - 单向数据流（UDF）架构
 * 
 * 注意：这是可选的升级路径示例
 * 现有页面可以继续使用旧架构，不强制迁移
 */
class SettingsViewModel(private val repository: com.example.carrotamap.data.PreferenceRepository) : ViewModel() {
    
    // ===============================
    // UI 状态定义
    // ===============================
    
    /**
     * 设置页面的 UI 状态
     */
    data class SettingsUiState(
        val overtakeMode: OvertakeMode = OvertakeMode.DISABLED,
        val minOvertakeSpeed: Int = 65,  // km/h
        val speedDiffThreshold: Int = 20,  // km/h
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val successMessage: String? = null
    )
    
    /**
     * 超车模式枚举
     */
    enum class OvertakeMode(val value: Int, val displayName: String) {
        DISABLED(0, "禁止超车"),
        MANUAL(1, "拨杆超车"),
        AUTO(2, "自动超车")
    }
    
    // ===============================
    // 状态管理
    // ===============================
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // ===============================
    // 事件处理
    // ===============================
    
    /**
     * UI 事件密封类
     * 定义所有可能的用户交互
     */
    sealed class SettingsEvent {
        data class UpdateOvertakeMode(val mode: OvertakeMode) : SettingsEvent()
        data class UpdateMinSpeed(val speed: Int) : SettingsEvent()
        data class UpdateSpeedThreshold(val threshold: Int) : SettingsEvent()
        object SaveSettings : SettingsEvent()
        object DismissError : SettingsEvent()
        object DismissSuccess : SettingsEvent()
    }
    
    /**
     * 处理 UI 事件
     * 单一入口点，便于测试和维护
     */
    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.UpdateOvertakeMode -> updateOvertakeMode(event.mode)
            is SettingsEvent.UpdateMinSpeed -> updateMinSpeed(event.speed)
            is SettingsEvent.UpdateSpeedThreshold -> updateSpeedThreshold(event.threshold)
            is SettingsEvent.SaveSettings -> saveSettings()
            is SettingsEvent.DismissError -> dismissError()
            is SettingsEvent.DismissSuccess -> dismissSuccess()
        }
    }
    
    // ===============================
    // 业务逻辑
    // ===============================
    
    private fun updateOvertakeMode(mode: OvertakeMode) {
        _uiState.update { it.copy(overtakeMode = mode) }
    }
    
    private fun updateMinSpeed(speed: Int) {
        _uiState.update { it.copy(minOvertakeSpeed = speed) }
    }
    
    private fun updateSpeedThreshold(threshold: Int) {
        _uiState.update { it.copy(speedDiffThreshold = threshold) }
    }
    
    /**
     * 保存设置到 SharedPreferences
     * 使用协程和 Result 封装错误处理
     */
    private fun saveSettings() {
        viewModelScope.launch {
            // 显示加载状态
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            // 执行保存操作
            val result = saveSettingsToPreferences()
            
            // 更新 UI 状态
            _uiState.update { currentState ->
                when (result) {
                    is Result.Success -> {
                        currentState.copy(
                            isLoading = false,
                            successMessage = "设置保存成功"
                        )
                    }
                    is Result.Error -> {
                        currentState.copy(
                            isLoading = false,
                            errorMessage = "保存失败: ${result.message}"
                        )
                    }
                    is Result.Loading -> currentState  // 不应该到达这里
                }
            }
        }
    }
    
    /**
     * 实际保存逻辑
     */
    private suspend fun saveSettingsToPreferences(): Result<Unit> {
        return runSafely(ErrorCode.DATA_INVALID) {
            repository.setOvertakeMode(uiState.value.overtakeMode.value)
            repository.setMinOvertakeSpeed(uiState.value.minOvertakeSpeed.toFloat())
            repository.setSpeedDiffThreshold(uiState.value.speedDiffThreshold.toFloat())
        }
    }
    
    private fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    private fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

/**
 * 使用示例（在 Composable 中）：
 * 
 * ```kotlin
 * @Composable
 * fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
 *     val uiState by viewModel.uiState.collectAsState()
 *     
 *     Column {
 *         // 超车模式选择器
 *         OvertakeModeSelector(
 *             selectedMode = uiState.overtakeMode,
 *             onModeSelected = { mode ->
 *                 viewModel.onEvent(SettingsEvent.UpdateOvertakeMode(mode))
 *             }
 *         )
 *         
 *         // 最小速度滑块
 *         SpeedSlider(
 *             value = uiState.minOvertakeSpeed,
 *             onValueChange = { speed ->
 *                 viewModel.onEvent(SettingsEvent.UpdateMinSpeed(speed))
 *             }
 *         )
 *         
 *         // 保存按钮
 *         Button(
 *             onClick = { viewModel.onEvent(SettingsEvent.SaveSettings) },
 *             enabled = !uiState.isLoading
 *         ) {
 *             if (uiState.isLoading) {
 *                 CircularProgressIndicator()
 *             } else {
 *                 Text("保存设置")
 *             }
 *         }
 *         
 *         // 错误提示
 *         uiState.errorMessage?.let { error ->
 *             ErrorSnackbar(
 *                 message = error,
 *                 onDismiss = { viewModel.onEvent(SettingsEvent.DismissError) }
 *             )
 *         }
 *     }
 * }
 * ```
 * 
 * 优势：
 * 1. 状态管理清晰 - 所有状态集中在 UiState
 * 2. 易于测试 - ViewModel 不依赖 Android 框架
 * 3. 生命周期安全 - StateFlow 自动处理配置更改
 * 4. 单向数据流 - 数据流向明确，易于调试
 */
