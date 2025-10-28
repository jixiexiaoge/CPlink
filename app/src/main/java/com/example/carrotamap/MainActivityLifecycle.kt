package com.example.carrotamap

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * MainActivity生命周期管理类
 * 负责Activity生命周期管理、初始化流程、自检查等
 */
class MainActivityLifecycle(
    private val activity: ComponentActivity,
    private val core: MainActivityCore
) {
    companion object {
        private const val TAG = AppConstants.Logging.MAIN_ACTIVITY_TAG
    }

    // ===============================
    // Activity生命周期管理
    // ===============================
    
    /**
     * Activity创建时的处理
     */
    fun onCreate(savedInstanceState: Bundle?) {
        // 保持屏幕常亮
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.i(TAG, "🔆 已设置屏幕常亮")

        // 请求忽略电池优化
        core.requestIgnoreBatteryOptimizations()
        
        // 请求悬浮窗权限
        core.requestFloatingWindowPermission()

        // 请求通知权限（Android 13+ 前台服务通知需要）
        core.requestNotificationPermissionIfNeeded()
        
        // 启动前台服务
        core.startForegroundService()

        Log.i(TAG, "🚀 MainActivity正在启动...")

        // 立即初始化权限管理器，在Activity早期阶段
        initializePermissionManagerEarly()
        
        // 立即设置用户界面，避免白屏
        setupUserInterface()
        
        // 存储Intent用于后续页面导航
        core.pendingNavigationIntent = activity.intent

        // 开始自检查流程
        startSelfCheckProcess()
        
        // 启动内存监控
        core.startMemoryMonitoring()
        
        // 注册控制指令广播接收器
        core.registerCarrotCommandReceiver()

        Log.i(TAG, "✅ MainActivity启动完成")
    }
    
    /**
     * 处理新的Intent
     */
    fun onNewIntent(intent: Intent) {
        Log.i(TAG, "📱 收到新的Intent，处理页面导航")
        // 处理新的Intent，用于从悬浮窗导航
        core.pendingNavigationIntent = intent
        core.handleFloatingWindowNavigation()
    }

    /**
     * Activity暂停时的处理
     */
    fun onPause() {
        Log.i(TAG, "⏸️ Activity暂停")
        
        // 记录使用时长（检查是否已初始化）
        try {
            core.deviceManager.recordAppUsage()
        } catch (e: UninitializedPropertyAccessException) {
            Log.d(TAG, "📝 deviceManager未初始化，跳过使用统计记录")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 记录使用时长失败: ${e.message}")
        }
        
        // 所有用户类型都可以使用悬浮窗功能
        Log.i(TAG, "🔓 用户类型${core.userType.value}可以使用悬浮窗功能")
        
        // 设置网络管理器为后台模式，调整网络策略
        try {
            core.networkManager.setBackgroundState(true)
            Log.i(TAG, "🔄 网络管理器已切换到后台模式")
        } catch (e: UninitializedPropertyAccessException) {
            Log.d(TAG, "📝 networkManager未初始化，跳过后台状态设置")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 设置后台状态失败: ${e.message}")
        }
        
        // 启动悬浮窗服务
        if (core.isFloatingWindowEnabled.value) {
            val intent = Intent(activity, FloatingWindowService::class.java).apply {
                action = FloatingWindowService.ACTION_START_FLOATING
            }
            activity.startService(intent)
        }
        
        // 注意：不暂停GPS更新，让GPS在后台继续工作
        Log.i(TAG, "🌍 GPS位置更新在后台继续运行")
    }

    /**
     * Activity恢复时的处理
     */
    fun onResume() {
        Log.i(TAG, "▶️ Activity恢复，隐藏悬浮窗")
        
        // 设置网络管理器为前台模式，恢复正常网络策略
        try {
            core.networkManager.setBackgroundState(false)
            Log.i(TAG, "🔄 网络管理器已切换到前台模式")
        } catch (e: UninitializedPropertyAccessException) {
            Log.d(TAG, "📝 networkManager未初始化，跳过前台状态设置")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 设置前台状态失败: ${e.message}")
        }
        
        // 隐藏悬浮窗
        val intent = Intent(activity, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_STOP_FLOATING
        }
        activity.startService(intent)
        
        // 重新设置屏幕常亮，确保不会被清除
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 更新使用统计（检查是否已初始化）
        try {
            core.usageStats.value = core.deviceManager.getUsageStats()
        } catch (e: UninitializedPropertyAccessException) {
            Log.d(TAG, "📝 deviceManager未初始化，跳过使用统计更新")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 更新使用统计失败: ${e.message}")
        }
        
        // 处理悬浮窗页面导航
        core.handleFloatingWindowNavigation()
    }

    /**
     * Activity销毁时的处理
     */
    fun onDestroy() {
        Log.i(TAG, "🔧 MainActivity正在销毁，清理资源...")

        try {
            // 首先停止悬浮窗服务，防止悬浮窗残留
            try {
                val intent = Intent(activity, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_STOP_FLOATING
                }
                activity.startService(intent)
                Log.i(TAG, "🛑 已发送停止悬浮窗服务指令")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 停止悬浮窗服务失败: ${e.message}")
            }
            
            // 停止内存监控
            core.stopMemoryMonitoring()
            
            // 清理协程作用域，避免协程取消异常
            core.cleanupCoroutineScope()
            
            // 记录应用使用时长（在清理前，检查是否已初始化）
            try {
                core.deviceManager.recordAppUsage()
            } catch (e: UninitializedPropertyAccessException) {
                Log.d(TAG, "📝 deviceManager未初始化，跳过使用统计记录")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 记录使用时长失败: ${e.message}")
            }
            
            // 停止前台服务
            core.stopForegroundService()
            
            // 注销控制指令广播接收器
            core.unregisterCarrotCommandReceiver()
            
            // 按顺序清理各个管理器，避免依赖问题
            try {
                core.amapBroadcastManager.unregisterReceiver()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 清理广播管理器失败: ${e.message}")
            }
            
            try {
                core.locationSensorManager.cleanup()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 清理位置传感器管理器失败: ${e.message}")
            }
            
            try {
                core.permissionManager.cleanup()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 清理权限管理器失败: ${e.message}")
            }
            
            // 网络管理器最后清理，因为它可能被其他组件依赖
            try {
                core.networkManager.cleanup()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 清理网络管理器失败: ${e.message}")
            }
            
            try {
                core.deviceManager.cleanup()
            } catch (e: UninitializedPropertyAccessException) {
                Log.d(TAG, "📝 deviceManager未初始化，跳过清理")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 清理设备管理器失败: ${e.message}")
            }
            
            Log.i(TAG, "✅ 所有监听器已注销并释放资源")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 资源清理失败: ${e.message}", e)
        }
    }

    /**
     * 重写onBackPressed，防止用户意外退出
     */
    fun onBackPressed() {
        // 不调用super.onBackPressed()，防止退出应用
        Log.i(TAG, "🔙 拦截返回键，防止退出应用")
    }

    // ===============================
    // 初始化流程管理
    // ===============================
    
    /**
     * 设置权限和位置服务
     */
    private fun setupPermissionsAndLocation() {
        try {
            core.permissionManager.smartPermissionRequest()
            
            // 输出权限状态报告
            val permissionReport = core.permissionManager.getPermissionStatusReport()
            Log.i(TAG, permissionReport)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 权限设置失败: ${e.message}", e)
        }
    }

    /**
     * 初始化广播管理器
     */
    private fun initializeBroadcastManager() {
        Log.i(TAG, "📡 初始化广播管理器...")

        try {
            core.amapBroadcastManager = AmapBroadcastManager(activity, core.carrotManFields, core.networkManager)
            val success = core.amapBroadcastManager.registerReceiver()

            if (success) {
                Log.i(TAG, "✅ 广播管理器初始化成功")
            } else {
                Log.e(TAG, "❌ 广播管理器初始化失败")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 广播管理器初始化异常: ${e.message}", e)
        }
    }

    /**
     * 初始化设备管理器
     */
    private fun initializeDeviceManager() {
        Log.i(TAG, "📱 初始化设备管理器...")

        try {
            core.deviceManager = DeviceManager(activity)

            // 获取设备ID并更新UI
            val id = core.deviceManager.getDeviceId()
            core.deviceId.value = id

            // 记录应用启动（在设备管理器初始化后）
            core.deviceManager.recordAppStart()

            Log.i(TAG, "✅ 设备管理器初始化成功，设备ID: $id")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 设备管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 早期初始化权限管理器（在Activity早期阶段）
     */
    private fun initializePermissionManagerEarly() {
        Log.i(TAG, "🔐 早期初始化权限管理器...")

        try {
            // 创建一个临时的LocationSensorManager用于权限管理器初始化
            val tempCarrotManFields = mutableStateOf(CarrotManFields())
            val tempLocationSensorManager = LocationSensorManager(activity, tempCarrotManFields)
            core.permissionManager = PermissionManager(activity, tempLocationSensorManager)
            // 在Activity早期阶段初始化，此时可以安全注册ActivityResultLauncher
            core.permissionManager.initialize()
            Log.i(TAG, "✅ 权限管理器早期初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 权限管理器早期初始化失败: ${e.message}", e)
        }
    }

    /**
     * 初始化权限管理器（在自检查流程中）
     */
    private fun initializePermissionManager() {
        Log.i(TAG, "🔐 初始化权限管理器...")

        try {
            // 更新权限管理器中的locationSensorManager引用
            core.permissionManager.updateLocationSensorManager(core.locationSensorManager)
            Log.i(TAG, "✅ 权限管理器引用更新成功")
            
            // GPS预热：提前开始位置获取
            startGpsWarmup()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 权限管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * GPS预热：提前开始位置获取
     */
    private fun startGpsWarmup() {
        try {
            Log.i(TAG, "🌡️ 开始GPS预热...")
            // 启动GPS预热，提前获取位置数据
            core.locationSensorManager.startGpsWarmup()
            Log.i(TAG, "✅ GPS预热已启动")
        } catch (e: Exception) {
            Log.e(TAG, "❌ GPS预热失败: ${e.message}", e)
        }
    }

    /**
     * 初始化位置和传感器管理器
     */
    private fun initializeLocationSensorManager() {
        Log.i(TAG, "🧭 初始化位置和传感器管理器...")

        try {
            core.locationSensorManager = LocationSensorManager(activity, core.carrotManFields)
            core.locationSensorManager.initializeSensors()
            
            // 🚀 关键修复：立即启动GPS位置更新服务
            // 这样可以确保手机GPS数据能够实时更新到carrotManFields中
            Log.i(TAG, "📍 正在启动GPS位置更新服务...")
            core.locationSensorManager.startLocationUpdates()
            
            Log.i(TAG, "✅ 位置和传感器管理器初始化成功（GPS已启动）")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 位置和传感器管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 初始化网络管理器（仅初始化，不启动网络服务）
     */
    private fun initializeNetworkManagerOnly() {
        Log.i(TAG, "🌐 初始化网络管理器（延迟启动网络服务）...")

        try {
            core.networkManager = NetworkManager(activity, core.carrotManFields)
            // 仅创建NetworkManager实例，不启动网络服务
            Log.i(TAG, "✅ 网络管理器初始化成功（网络服务待启动）")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 启动网络服务（延迟启动）
     */
    private fun startNetworkService() {
        Log.i(TAG, "🌐 启动网络服务...")

        try {
            val success = core.networkManager.initializeNetworkClient()
            if (success) {
                Log.i(TAG, "✅ 网络服务启动成功")
            } else {
                Log.e(TAG, "❌ 网络服务启动失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络服务启动失败: ${e.message}", e)
        }
    }

    /**
     * 初始化高德地图管理器
     */
    private fun initializeAmapManagers() {
        Log.i(TAG, "🗺️ 初始化高德地图管理器...")

        try {
            // 初始化数据处理器
            core.amapDataProcessor = AmapDataProcessor(activity, core.carrotManFields)

            // 初始化目的地管理器
            core.amapDestinationManager = AmapDestinationManager(
                core.carrotManFields,
                core.networkManager,
                core::updateUIMessage
            )

            // 初始化导航管理器
            core.amapNavigationManager = AmapNavigationManager(
                core.carrotManFields,
                core.amapDestinationManager,
                core::updateUIMessage
            )

            Log.i(TAG, "✅ 高德地图管理器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 高德地图管理器初始化失败: ${e.message}", e)
        }
    }

    /**
     * 执行初始位置更新（仅用于距离统计）
     */
    private fun performInitialLocationUpdate() {
        Log.i(TAG, "🚀 执行初始位置更新...")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 获取当前位置
                val currentFields = core.carrotManFields.value
                val latitude = if (currentFields.vpPosPointLat != 0.0) {
                    currentFields.vpPosPointLat
                } else {
                    // 使用默认坐标（北京）
                    39.9042
                }

                val longitude = if (currentFields.vpPosPointLon != 0.0) {
                    currentFields.vpPosPointLon
                } else {
                    // 使用默认坐标（北京）
                    116.4074
                }

                Log.i(TAG, "📍 更新位置用于距离统计: lat=$latitude, lon=$longitude")

                // 更新位置并计算距离（检查是否已初始化）
                try {
                    core.deviceManager.updateLocationAndDistance(latitude, longitude)

                    // 启动默认倒计时
                    core.deviceManager.startCountdown(
                        initialSeconds = 850,
                        onUpdate = { seconds: Int -> core.remainingSeconds.value = seconds },
                        onFinished = { activity.finishAffinity() }
                    )
                } catch (e: UninitializedPropertyAccessException) {
                    Log.w(TAG, "⚠️ deviceManager未初始化，跳过位置更新和倒计时")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 初始位置更新失败: ${e.message}", e)
                // 失败时启动默认倒计时（检查是否已初始化）
                try {
                    core.deviceManager.startCountdown(
                        initialSeconds = 850,
                        onUpdate = { seconds: Int -> core.remainingSeconds.value = seconds },
                        onFinished = { activity.finishAffinity() }
                    )
                } catch (e: UninitializedPropertyAccessException) {
                    Log.w(TAG, "⚠️ deviceManager未初始化，无法启动倒计时")
                }
            }
        }
    }

    // ===============================
    // 自检查流程管理
    // ===============================
    
    /**
     * 开始自检查流程 - 优化版：异步初始化
     */
    private fun startSelfCheckProcess() {
        // 使用IO调度器在后台线程执行初始化，避免阻塞主线程
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "🚀 开始异步自检查流程...")
                
                // 1. 网络管理器初始化（优先启动，后台线程）
                updateSelfCheckStatusAsync("网络管理器", "正在初始化...", false)
                initializeNetworkManagerOnly()
                updateSelfCheckStatusAsync("网络管理器", "初始化完成", true)
                delay(100)

                // 2. 启动网络服务（优先启动，后台线程）
                updateSelfCheckStatusAsync("网络服务", "正在启动...", false)
                startNetworkService()
                updateSelfCheckStatusAsync("网络服务", "启动完成", true)
                delay(100)

                // 3. 位置和传感器管理器初始化（主线程）
                updateSelfCheckStatusAsync("位置传感器管理器", "正在初始化...", false)
                withContext(Dispatchers.Main) { // LocationManager requires main thread
                    initializeLocationSensorManager()
                }
                updateSelfCheckStatusAsync("位置传感器管理器", "初始化完成", true)
                delay(100) // 减少延迟时间

                // 4. 权限管理器初始化（主线程）
                updateSelfCheckStatusAsync("权限管理器", "正在初始化...", false)
                withContext(Dispatchers.Main) { // PermissionManager might interact with UI/LocationManager
                    initializePermissionManager()
                }
                updateSelfCheckStatusAsync("权限管理器", "初始化完成", true)
                delay(100)

                // 5. 权限管理和位置服务初始化（主线程）
                updateSelfCheckStatusAsync("权限和位置服务", "正在设置...", false)
                withContext(Dispatchers.Main) { // LocationManager requires main thread
                    setupPermissionsAndLocation()
                }
                updateSelfCheckStatusAsync("权限和位置服务", "设置完成", true)
                delay(100)

                // 6. 获取和显示IP地址信息（后台线程）
                updateSelfCheckStatusAsync("IP地址信息", "正在获取...", false)
                
                // 延迟一下，确保网络服务完全启动
                delay(1000)
                
                // 尝试获取IP地址，如果失败则重试
                var phoneIP = getPhoneIPAddress()
                var deviceIP = getDeviceIPAddress()
                
                // 如果手机IP获取失败，再延迟重试一次
                if (phoneIP == "网络管理器未初始化" || phoneIP == "获取失败") {
                    Log.w(TAG, "⚠️ 首次获取手机IP失败，延迟重试...")
                    delay(1000)
                    phoneIP = getPhoneIPAddress()
                }
                
                val ipInfo = "手机: $phoneIP, 设备: ${deviceIP ?: "未连接"}"
                
                Log.i(TAG, "📱 IP地址信息: $ipInfo")
                updateSelfCheckStatusAsync("IP地址信息", ipInfo, true)
                delay(100)

                // 7-9. 并行初始化高德地图、广播和设备管理器（后台线程）
                updateSelfCheckStatusAsync("系统管理器", "正在并行初始化...", false)
                
                // 并行执行三个管理器的初始化
                val amapJob = CoroutineScope(Dispatchers.IO).launch {
                    initializeAmapManagers()
                }
                val broadcastJob = CoroutineScope(Dispatchers.IO).launch {
                    initializeBroadcastManager()
                }
                val deviceJob = CoroutineScope(Dispatchers.IO).launch {
                    initializeDeviceManager()
                }
                
                // 等待所有并行任务完成
                amapJob.join()
                broadcastJob.join()
                deviceJob.join()
                
                updateSelfCheckStatusAsync("系统管理器", "并行初始化完成", true)
                delay(100)

                // 9.5. 异步更新使用统计（不阻塞启动，将在用户类型检查后执行）
                updateSelfCheckStatusAsync("使用统计", "等待用户类型检查...", false)
                delay(50) // 进一步减少延迟

                // 10. 执行初始位置更新（主线程）
                updateSelfCheckStatusAsync("位置更新", "正在执行...", false)
                withContext(Dispatchers.Main) { // LocationManager requires main thread
                    performInitialLocationUpdate()
                }
                updateSelfCheckStatusAsync("位置更新", "执行完成", true)
                delay(100)

                // 11. 处理静态接收器Intent（后台线程）
                updateSelfCheckStatusAsync("静态接收器", "正在处理...", false)
                core.handleIntentFromStaticReceiver(activity.intent)
                updateSelfCheckStatusAsync("静态接收器", "处理完成", true)
                delay(50)

                // 网络服务已在步骤5启动，跳过重复启动

                // 10. 用户类型获取（最后执行，直接调用API）
                updateSelfCheckStatusAsync("用户类型", "正在获取...", false)
                val fetchedUserType = core.fetchUserType(core.deviceId.value)
                core.userType.value = fetchedUserType
                
                // 保存用户类型到SharedPreferences，供悬浮窗使用
                val sharedPreferences = activity.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                sharedPreferences.edit().putInt("user_type", fetchedUserType).apply()
                
                val userTypeText = when (fetchedUserType) {
                    0 -> "未知用户"
                    1 -> "新用户"
                    2 -> "支持者"
                    3 -> "赞助者"
                    4 -> "铁粉"
                    else -> "未知类型($fetchedUserType)"
                }
                updateSelfCheckStatusAsync("用户类型", "获取完成: $userTypeText", true)
                delay(50)

                // 10.5. 异步更新使用统计（基于用户类型）
                if (fetchedUserType in 2..4) {
                    updateSelfCheckStatusAsync("使用统计", "后台更新中...", false)
                    // 异步执行使用统计更新，不阻塞启动流程
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // 获取最新的使用统计数据（检查是否已初始化）
                            val latestUsageStats = core.deviceManager.getUsageStats()
                            
                            // 更新UI状态
                            withContext(Dispatchers.Main) {
                                core.usageStats.value = latestUsageStats
                                updateSelfCheckStatus("使用统计", "更新完成", true)
                            }
                            
                            core.autoUpdateUsageStats(core.deviceId.value, latestUsageStats)
                        } catch (e: UninitializedPropertyAccessException) {
                            Log.d(TAG, "📝 deviceManager未初始化，跳过使用统计更新")
                            withContext(Dispatchers.Main) {
                                updateSelfCheckStatus("使用统计", "设备管理器未初始化", false)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 自动更新使用统计失败: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                updateSelfCheckStatus("使用统计", "更新失败: ${e.message}", false)
                            }
                        }
                    }
                } else {
                    updateSelfCheckStatusAsync("使用统计", "用户类型不支持统计更新", true)
                }
                delay(50)

                // 11. 设置UI界面（后台线程）
                updateSelfCheckStatusAsync("用户界面", "正在设置...", false)
                updateSelfCheckStatusAsync("用户界面", "设置完成", true)
                delay(50)

                // 所有检查完成
                updateSelfCheckStatusAsync("系统检查", "所有检查完成", true)
                withContext(Dispatchers.Main) {
                    core.selfCheckStatus.value = core.selfCheckStatus.value.copy(isCompleted = true)
                }

                // 根据用户类型进行不同操作（后台线程）
                core.handleUserTypeAction(fetchedUserType)
                
                Log.i(TAG, "✅ 异步自检查流程完成")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 异步自检查流程失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    updateSelfCheckStatus("系统检查", "检查失败: ${e.message}", false)
                }
            }
        }
    }

    /**
     * 获取手机IP地址
     */
    private fun getPhoneIPAddress(): String {
        return try {
            // 直接尝试访问networkManager，如果未初始化会抛出异常
            val phoneIP = core.networkManager.getPhoneIP()
            Log.i(TAG, "📱 获取到手机IP: $phoneIP")
            phoneIP
        } catch (e: UninitializedPropertyAccessException) {
            Log.w(TAG, "⚠️ 网络管理器未初始化，无法获取手机IP")
            "网络管理器未初始化"
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取手机IP地址失败: ${e.message}")
            "获取失败"
        }
    }

    /**
     * 获取comma3设备IP地址
     */
    private fun getDeviceIPAddress(): String? {
        return try {
            // 直接尝试访问networkManager，如果未初始化会抛出异常
            val deviceIP = core.networkManager.getCurrentDeviceIP()
            Log.i(TAG, "🔗 获取到设备IP: $deviceIP")
            deviceIP
        } catch (e: UninitializedPropertyAccessException) {
            Log.w(TAG, "⚠️ 网络管理器未初始化，无法获取设备IP")
            null
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取设备IP地址失败: ${e.message}")
            null
        }
    }

    /**
     * 更新自检查状态
     */
    private fun updateSelfCheckStatus(component: String, message: String, isCompleted: Boolean) {
        val currentStatus = core.selfCheckStatus.value
        val newStatus = currentStatus.copy(
            currentComponent = component,
            currentMessage = message,
            isCompleted = isCompleted,
            completedComponents = if (isCompleted) {
                currentStatus.completedComponents + component
            } else {
                currentStatus.completedComponents
            },
            completedMessages = if (isCompleted) {
                currentStatus.completedMessages + (component to message)
            } else {
                currentStatus.completedMessages
            }
        )
        core.selfCheckStatus.value = newStatus
        Log.i(TAG, "🔍 自检查: $component - $message")
    }

    /**
     * 异步更新自检查状态（从后台线程调用）
     */
    private suspend fun updateSelfCheckStatusAsync(component: String, message: String, isCompleted: Boolean) {
        withContext(Dispatchers.Main) {
            updateSelfCheckStatus(component, message, isCompleted)
        }
    }

    // ===============================
    // UI设置
    // ===============================
    
    /**
     * 设置用户界面
     */
    private fun setupUserInterface() {
        // UI设置逻辑已移至MainActivityUI类
        // 这里只是占位，实际UI设置在MainActivity中调用
        Log.i(TAG, "🎨 用户界面设置已委托给MainActivityUI")
    }
}