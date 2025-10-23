package com.example.carrotamap

// Android 系统相关导入
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

// 协程相关导入
import kotlinx.coroutines.*

// JSON数据处理导入
import org.json.JSONObject

// Java 网络和IO相关导入
import java.io.IOException
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.Timer
import java.util.TimerTask
import kotlin.collections.HashMap

// Compose相关导入
import androidx.compose.runtime.MutableState

// CarrotMan 网络客户端类 - 负责与 Comma3 OpenPilot 设备进行 UDP 网络通信
class CarrotManNetworkClient(
    private val context: Context
) {
    
    companion object {
        private const val TAG = AppConstants.Logging.NETWORK_CLIENT_TAG
        
        // 网络通信端口配置 - 使用统一的常量管理
        private const val BROADCAST_PORT = AppConstants.Network.BROADCAST_PORT
        private const val MAIN_DATA_PORT = AppConstants.Network.MAIN_DATA_PORT
        private const val COMMAND_PORT = AppConstants.Network.COMMAND_PORT
        
        // 通信时间参数配置 - 使用统一的常量管理
        private const val DISCOVER_CHECK_INTERVAL = AppConstants.Network.DISCOVER_CHECK_INTERVAL
        private const val DATA_SEND_INTERVAL = AppConstants.Network.DATA_SEND_INTERVAL
        private const val SOCKET_TIMEOUT = AppConstants.Network.SOCKET_TIMEOUT
        private const val DEVICE_TIMEOUT = AppConstants.Network.DEVICE_TIMEOUT
        
        // 网络数据配置 - 使用统一的常量管理
        private const val MAX_PACKET_SIZE = AppConstants.Network.MAX_PACKET_SIZE
    }
    
    // 网络状态管理
    private var isRunning = false
    private var discoveredDevices = mutableMapOf<String, DeviceInfo>()
    private var currentTargetDevice: DeviceInfo? = null
    
    // 设备发现增强
    private var deviceDiscoveryEnabled = true
    private var lastDeviceDiscoveryTime = 0L
    private val deviceDiscoveryInterval = 5000L // 5秒发现间隔
    
    // Socket连接管理
    private var listenSocket: DatagramSocket? = null
    private var dataSocket: DatagramSocket? = null
    
    // 协程任务管理
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null
    private var dataSendJob: Job? = null
    private var autoSendJob: Job? = null
    private var deviceCheckJob: Job? = null
    
    // 心跳管理 - 改为在数据发送中处理，避免Socket冲突
    private var lastHeartbeatTime = 0L
    private val heartbeatInterval = 1000L // 1秒心跳间隔
    
    // 数据统计管理
    private var carrotIndex = 0L
    private var totalPacketsSent = 0
    private var lastSendTime = 0L
    private var lastDataReceived = 0L
    private var lastNoConnectionLogTime = 0L // 添加无连接日志时间控制
    private var lastNetworkErrorLogTime = 0L // 添加网络错误日志时间控制

    // 网络错误处理和重连机制 - 增强版
    private var consecutiveNetworkErrors = 0
    private var maxConsecutiveErrors = 3 // 降低阈值，更快触发恢复
    private var lastNetworkErrorTime = 0L
    private var networkErrorThreshold = 5000L // 5秒内连续错误阈值
    private var isNetworkRecovering = false
    
    // 智能重连策略
    private var reconnectAttempts = 0
    private var maxReconnectAttempts = 3
    private var lastReconnectTime = 0L
    private var reconnectDelay = 2000L // 2秒重连延迟
    private var lastSuccessfulSendTime = 0L
    
    // 网络稳定性增强参数
    private var networkStabilityCheckInterval = 30000L // 30秒检查一次网络稳定性
    private var lastNetworkStabilityCheck = 0L
    private var networkQualityScore = 100 // 网络质量评分 (0-100)
    private var successfulSendsInWindow = 0
    private var totalSendsInWindow = 0

    // ATC状态跟踪（用于日志记录）
    private var lastAtcPausedState: Boolean? = null
    
    // 事件回调接口
    private var onDeviceDiscovered: ((DeviceInfo) -> Unit)? = null
    private var onConnectionStatusChanged: ((Boolean, String) -> Unit)? = null
    private var onDataSent: ((Int) -> Unit)? = null
    private var onOpenpilotStatusReceived: ((String) -> Unit)? = null
    
    // Comma3设备信息数据类
    data class DeviceInfo(
        val ip: String,          // 设备IP地址
        val port: Int,           // 通信端口号
        val version: String,     // 设备版本信息
        val lastSeen: Long = System.currentTimeMillis()  // 最后发现时间
    ) {
        override fun toString(): String = "$ip:$port (v$version)"
        
        fun isActive(): Boolean {
            return System.currentTimeMillis() - lastSeen < DEVICE_TIMEOUT
        }
    }
    
    // 启动 CarrotMan 网络服务
    fun start() {
        if (isRunning) {
            Log.w(TAG, "网络服务已在运行中，忽略重复启动请求")
            return
        }
        
        Log.i(TAG, "启动 CarrotMan 网络客户端服务")
        
        // 禁用系统调试输出以减少日志噪音
        disableSystemDebugOutput()
        
        isRunning = true
        
        try {
            initializeSockets()
            startDeviceListener()
            startDeviceHealthCheck()
            startDeviceDiscovery() // 启动设备发现服务
            startHeartbeatTask() // 启动心跳任务而不是定时器
            onConnectionStatusChanged?.invoke(false, "")
            Log.i(TAG, "CarrotMan 网络服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动网络服务失败: ${e.message}", e)
            onConnectionStatusChanged?.invoke(false, "")
            stop()
        }
    }
    
    // 停止 CarrotMan 网络服务
    fun stop() {
        Log.i(TAG, "停止 CarrotMan 网络客户端服务")
        isRunning = false
        
        listenJob?.cancel()
        dataSendJob?.cancel()
        autoSendJob?.cancel()
        deviceCheckJob?.cancel()
        
        // 心跳任务由协程管理，无需单独停止
        
        listenSocket?.close()
        dataSocket?.close()
        
        listenSocket = null
        dataSocket = null
        currentTargetDevice = null
        
        // 保存停止状态到SharedPreferences
        saveNetworkStatus(false, "")
        
        onConnectionStatusChanged?.invoke(false, "")
        Log.i(TAG, "CarrotMan 网络服务已完全停止")
    }
    
    // 初始化UDP Socket连接
    private fun initializeSockets() {
        try {
            Log.d(TAG, "开始初始化UDP Socket连接...")

            listenSocket = DatagramSocket(BROADCAST_PORT).apply {
                soTimeout = 1000 // 1秒超时，更频繁地检查isRunning状态
                reuseAddress = true
                broadcast = true // 启用广播接收
                Log.d(TAG, "监听Socket已创建，端口: $BROADCAST_PORT，超时: 1000ms")
            }

            dataSocket = DatagramSocket().apply {
                soTimeout = SOCKET_TIMEOUT
                Log.d(TAG, "数据发送Socket已创建，端口: ${localPort}")
            }

            Log.i(TAG, "Socket初始化成功 - 监听端口: $BROADCAST_PORT (广播模式)")

        } catch (e: Exception) {
            Log.e(TAG, "Socket初始化失败: ${e.message}", e)
            listenSocket?.close()
            dataSocket?.close()
            listenSocket = null
            dataSocket = null
            throw e
        }
    }
    
    // 启动设备广播监听服务
    private fun startDeviceListener() {
        listenJob = networkScope.launch {
            Log.i(TAG, "✅ 启动设备广播监听服务 - 端口: $BROADCAST_PORT")

            while (isRunning) {
                try {
                    // 持续监听设备广播
                    listenForDeviceBroadcasts()
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "❌ 设备广播监听异常: ${e.message}", e)

                        // 短暂延迟后重试，避免快速失败循环
                        delay(1000)
                    }
                }

                if (isRunning) {
                    delay(100) // 短暂延迟，避免CPU占用过高
                }
            }
            Log.d(TAG, "设备广播监听服务已停止")
        }
    }
    
    // 持续监听设备广播消息
    private suspend fun listenForDeviceBroadcasts() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        val packet = DatagramPacket(buffer, buffer.size)

        //Log.d(TAG, "开始监听UDP广播数据，端口: $BROADCAST_PORT")

        try {
            // 单次接收广播数据
            listenSocket?.receive(packet)
            val receivedData = String(packet.data, 0, packet.length)
            val deviceIP = packet.address.hostAddress ?: "unknown"

            //Log.i(TAG, "📡 收到设备广播: [$receivedData] from $deviceIP")
            //Log.d(TAG, "📊 当前状态: 已发现设备=${discoveredDevices.size}, 当前连接=${currentTargetDevice?.ip ?: "无"}")

            lastDataReceived = System.currentTimeMillis()
            parseDeviceBroadcast(receivedData, deviceIP)

        } catch (e: SocketTimeoutException) {
            // 超时是正常的，不需要特殊处理
            //Log.v(TAG, "广播监听超时，继续等待...")
        } catch (e: Exception) {
            if (isRunning) {
                Log.w(TAG, "接收广播数据异常: ${e.message}")
                throw e // 重新抛出异常，由上层处理
            }
        }
    }
    
    // 解析收到的设备广播数据
    private fun parseDeviceBroadcast(broadcastData: String, deviceIP: String) {
        try {
            //Log.i(TAG, "🔍 解析设备广播数据: $broadcastData from $deviceIP")
            //Log.d(TAG, "📊 解析前状态: 已发现设备=${discoveredDevices.size}, 当前连接=${currentTargetDevice?.ip ?: "无"}")

            if (broadcastData.trim().startsWith("{")) {
                val jsonBroadcast = JSONObject(broadcastData)

                // 检查是否为OpenpPilot状态数据
                if (isOpenpilotStatusData(jsonBroadcast)) {
                    //Log.d(TAG, "📡 检测到OpenpPilot状态数据 from $deviceIP")
                    onOpenpilotStatusReceived?.invoke(broadcastData)

                    // OpenpPilot状态数据也表示设备存在，需要添加到设备列表
                    val ip = jsonBroadcast.optString("ip", deviceIP)
                    val port = jsonBroadcast.optInt("port", MAIN_DATA_PORT)
                    val version = "openpilot"
                    val device = DeviceInfo(ip, port, version)
                    addDiscoveredDevice(device)
                    //Log.d(TAG, "从OpenpPilot状态数据中发现设备: $device")
                    return
                }

                // 处理设备发现数据
                val ip = jsonBroadcast.optString("ip", deviceIP)
                val port = jsonBroadcast.optInt("port", MAIN_DATA_PORT)
                val version = jsonBroadcast.optString("version", "unknown")

                val device = DeviceInfo(ip, port, version)
                addDiscoveredDevice(device)
                //Log.d(TAG, "JSON格式设备信息解析成功: $device")

            } else {
                //Log.d(TAG, "收到简单格式广播，使用默认配置: $deviceIP")
                val device = DeviceInfo(deviceIP, MAIN_DATA_PORT, "detected")
                addDiscoveredDevice(device)
            }

        } catch (e: Exception) {
            Log.w(TAG, "广播解析失败，回退到默认模式: $broadcastData - ${e.message}")
            val device = DeviceInfo(deviceIP, MAIN_DATA_PORT, "fallback")
            addDiscoveredDevice(device)
        }
    }

    // 检查JSON数据是否为OpenpPilot状态数据
    private fun isOpenpilotStatusData(jsonObject: JSONObject): Boolean {
        // OpenpPilot状态数据的特征字段
        return jsonObject.has("Carrot2") ||
               jsonObject.has("IsOnroad") ||
               jsonObject.has("v_ego_kph") ||
               jsonObject.has("active") ||
               jsonObject.has("xState")
    }
    
    // 添加新发现的设备到设备列表
    private fun addDiscoveredDevice(device: DeviceInfo) {
        val deviceKey = "${device.ip}:${device.port}"

        //Log.d(TAG, "🔍 尝试添加设备: $device, 设备键: $deviceKey")
        //Log.d(TAG, "📊 当前设备列表: ${discoveredDevices.keys}")

        if (!discoveredDevices.containsKey(deviceKey)) {
            discoveredDevices[deviceKey] = device
            //Log.i(TAG, "🎯 发现新的Comma3设备: $device")
            onDeviceDiscovered?.invoke(device)

            // 更新状态为发现设备
            if (currentTargetDevice == null) {
                Log.i(TAG, "🔄 更新状态: 发现设备 ${device.ip}，正在连接...")
                onConnectionStatusChanged?.invoke(false, "发现设备 ${device.ip}，正在连接...")
                //Log.i(TAG, "🚀 自动连接到第一个发现的设备")
                connectToDevice(device)
            } else {
                //Log.d(TAG, "⚠️ 已有连接设备 ${currentTargetDevice?.ip}，不自动连接新设备")
            }
        } else {
            discoveredDevices[deviceKey] = device.copy(lastSeen = System.currentTimeMillis())
            //Log.v(TAG, "🔄 更新设备活跃时间: $deviceKey")
        }

        //Log.d(TAG, "📊 添加后状态: 已发现设备=${discoveredDevices.size}, 当前连接=${currentTargetDevice?.ip ?: "无"}")
    }
    
    // 连接到指定的Comma3设备
    fun connectToDevice(device: DeviceInfo) {
        //Log.i(TAG, "🔗 开始连接到Comma3设备: $device")

        currentTargetDevice = device
        dataSendJob?.cancel()
        
        // 重置心跳时间，让心跳任务开始工作
        lastHeartbeatTime = 0L
        
        startDataTransmission()

        // 保存连接状态到SharedPreferences
        saveNetworkStatus(true, device.toString())

        //Log.i(TAG, "✅ 更新连接状态: 已连接到设备 ${device.ip}")
        onConnectionStatusChanged?.invoke(true, "")
        Log.i(TAG, "🎉 设备连接建立成功: ${device.ip}")
    }
    
    // 启动数据传输任务（心跳已移至独立定时器）
    private fun startDataTransmission() {
        dataSendJob = networkScope.launch {
            Log.i(TAG, "✅ 启动数据传输任务 - 设备: ${currentTargetDevice?.ip}")
            
            // 数据传输任务现在主要用于其他数据发送
            // 心跳由独立定时器处理
            while (isRunning && currentTargetDevice != null) {
                delay(DATA_SEND_INTERVAL)
            }
            Log.d(TAG, "数据传输任务已停止")
        }
    }
    
    /**
     * 启动心跳任务 - 使用协程避免Socket冲突
     */
    private fun startHeartbeatTask() {
        networkScope.launch {
            Log.i(TAG, "💓 启动心跳任务")
            
            while (isRunning) {
                try {
                    if (currentTargetDevice != null) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastHeartbeatTime >= heartbeatInterval) {
                            sendHeartbeat()
                            lastHeartbeatTime = currentTime
                        }
                    }
                    delay(100) // 100ms检查一次，避免过于频繁
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 心跳任务异常: ${e.message}", e)
                    delay(1000) // 异常时等待1秒再继续
                }
            }
            Log.d(TAG, "💓 心跳任务已停止")
        }
    }
    
    // 启动设备健康检查服务
    private fun startDeviceHealthCheck() {
        deviceCheckJob = networkScope.launch {
            Log.i(TAG, "启动设备健康检查服务，检查间隔: ${DISCOVER_CHECK_INTERVAL}ms")
            
            while (isRunning) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val initialDeviceCount = discoveredDevices.size
                    
                    val removedDevices = discoveredDevices.values.filter { device ->
                        currentTime - device.lastSeen > DEVICE_TIMEOUT
                    }
                    
                    removedDevices.forEach { device ->
                        val deviceKey = "${device.ip}:${device.port}"
                        discoveredDevices.remove(deviceKey)
                        Log.i(TAG, "移除离线设备: $device")
                    }
                    
                    currentTargetDevice?.let { device ->
                        val deviceKey = "${device.ip}:${device.port}"
                        
                        if (!discoveredDevices.containsKey(deviceKey)) {
                            Log.w(TAG, "当前连接设备已离线: $device")
                            
                            currentTargetDevice = null
                            dataSendJob?.cancel()
                            
                            // 保存断开连接状态
                            saveNetworkStatus(false, "")
                            
                            discoveredDevices.values.firstOrNull()?.let { newDevice ->
                                Log.i(TAG, "自动切换到备用设备: $newDevice")
                                connectToDevice(newDevice)
                            } ?: run {
                                Log.w(TAG, "没有可用的备用设备")
                                onConnectionStatusChanged?.invoke(false, "")
                            }
                        }
                    }
                    
                    if (removedDevices.isNotEmpty()) {
                        Log.d(TAG, "健康检查完成 - 设备数量: $initialDeviceCount -> ${discoveredDevices.size}")
                    }

                    // 检查是否需要更新连接状态
                    if (currentTargetDevice == null && discoveredDevices.isEmpty()) {
                        onConnectionStatusChanged?.invoke(false, "")
                    } else if (currentTargetDevice == null && discoveredDevices.isNotEmpty()) {
                        onConnectionStatusChanged?.invoke(false, "")
                    }

                    delay(DISCOVER_CHECK_INTERVAL)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 设备健康检查失败: ${e.message}", e)
                    delay(5000)
                }
            }
            Log.d(TAG, "设备健康检查服务已停止")
        }
    }
    
    // 发送心跳包维持连接
    private suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        val heartbeatData = JSONObject().apply {
            put("carrotIndex", ++carrotIndex)
            put("epochTime", System.currentTimeMillis() / 1000)
            put("timezone", "Asia/Shanghai")
            put("carrotCmd", "heartbeat")
            put("carrotArg", "")
            put("source", "android_app")
        }
        
        sendDataPacket(heartbeatData)
        //Log.v(TAG, "心跳包已发送，索引: $carrotIndex")
    }
    
    // 发送CarrotMan导航数据包
    fun sendCarrotManData(carrotFields: CarrotManFields) {
        if (!isRunning || currentTargetDevice == null) {
            // 降低无连接时的日志级别，避免日志刷屏
            if (System.currentTimeMillis() - lastNoConnectionLogTime > 10000) { // 10秒记录一次
                Log.w(TAG, "⚠️ 发送CarrotMan数据 - 服务未运行或无连接设备")
                Log.d(TAG, "状态检查 - 运行状态: $isRunning, 连接设备: $currentTargetDevice")
                lastNoConnectionLogTime = System.currentTimeMillis()
            }
            return
        }

        // 如果正在网络恢复中，跳过发送
        if (isNetworkRecovering) {
            Log.d(TAG, "⏸️ 网络恢复中，跳过CarrotMan数据发送")
            return
        }
        
        // 检查网络稳定性
        checkNetworkStability()

        // 发送完整导航数据（许可证系统已移除）
        //Log.d(TAG, "发送完整导航数据")

        networkScope.launch {
            try {
                val jsonData = convertCarrotFieldsToJson(carrotFields)
                sendDataPacket(jsonData)
                
                // 记录成功发送
                recordSuccessfulSend()
                onDataSent?.invoke(++totalPacketsSent)
                //Log.v(TAG, "CarrotMan数据包发送成功 #$totalPacketsSent")
            } catch (e: Exception) {
                // 记录失败发送
                recordFailedSend()
                // 使用新的错误处理机制
                handleNetworkError(e, "CarrotMan数据发送")
                
                // 控制CarrotMan数据发送错误日志频率
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastNetworkErrorLogTime > 5000) { // 5秒记录一次
                    Log.w(TAG, "⚠️ CarrotMan数据发送失败: ${e.message}")
                    if (e.message?.contains("ENETUNREACH") == true) {
                        Log.w(TAG, "💡 建议：检查设备连接状态和网络配置")
                    }
                    lastNetworkErrorLogTime = currentTime
                }
            }
        }
    }
    
    // 数据变化检测变量
    private var lastRoadLimitSpeed = 0
    private var lastSdiType = -1
    private var lastSdiDist = 0
    private var lastTbtType = -1
    private var lastTbtDist = 0
    private var lastLatitude = 0.0
    private var lastLongitude = 0.0
    private var lastHeading = 0.0
    private var lastUpdateTime = 0L
    
    // 转换CarrotManFields为JSON协议格式
    private fun convertCarrotFieldsToJson(fields: CarrotManFields): JSONObject {
        // 获取远程IP地址 (基于Python update_navi逻辑)
        val remoteIP = currentTargetDevice?.ip ?: ""
        
        // 数据验证和变化检测
        val hasSignificantChanges = checkForSignificantChanges(fields)
        val currentTime = System.currentTimeMillis()
        
        // 如果数据没有显著变化且距离上次发送时间不足1秒，跳过发送
        if (!hasSignificantChanges && (currentTime - lastUpdateTime) < 1000) {
            Log.v(TAG, "⏸️ 数据无显著变化，跳过发送")
            return JSONObject() // 返回空JSON，调用方会跳过发送
        }
        
        // 更新最后发送时间
        lastUpdateTime = currentTime

        return JSONObject().apply {
            // ========== 基础通信字段 ==========
            put("carrotIndex", ++carrotIndex)
            put("epochTime", if (fields.epochTime > 0) fields.epochTime else System.currentTimeMillis() / 1000)
            put("timezone", fields.timezone.ifEmpty { "Asia/Shanghai" })

            // ========== GPS定位字段（必需） ==========
            // 🔍 根据Python代码分析，这些字段是Comma3设备必需的：
            if (fields.latitude != 0.0 && fields.longitude != 0.0) {
                put("latitude", fields.latitude)               // GPS纬度 (WGS84)
                put("longitude", fields.longitude)             // GPS经度 (WGS84)
                put("heading", fields.heading)                 // 方向角 (0-360度)
                put("accuracy", fields.accuracy)               // GPS精度 (米)
                put("gps_speed", fields.gps_speed)             // GPS速度 (m/s)
            }

            // ========== 导航位置字段（兼容字段） ==========
            // 🔍 根据Python代码期望的字段名，修正映射：
            put("vpPosPointLat", fields.vpPosPointLatNavi)   // 导航纬度 (Python期望此字段名)
            put("vpPosPointLon", fields.vpPosPointLonNavi)   // 导航经度 (Python期望此字段名)
            put("nPosAngle", fields.nPosAngle)               // 导航方向角
            put("nPosSpeed", fields.nPosSpeed)               // 导航速度

            // ========== 目的地信息字段 ==========
            put("goalPosX", fields.goalPosX)                 // 目标经度
            put("goalPosY", fields.goalPosY)                 // 目标纬度
            put("szGoalName", fields.szGoalName)             // 目标名称

            // ========== 道路信息字段 ==========
            put("nRoadLimitSpeed", fields.nRoadLimitSpeed)   // 道路限速 (km/h)
            put("roadcate", fields.roadcate)                 // 道路类别 (10/11=高速，其它非高速)
            put("szPosRoadName", fields.szPosRoadName)       // 当前道路名称
            
            // 添加限速变化检测日志
            //if (fields.nRoadLimitSpeed > 0) {
            //    Log.v(TAG, "📤 发送道路限速: ${fields.nRoadLimitSpeed}km/h")
            //}

            // ========== SDI速度检测字段 ==========
            put("nSdiType", fields.nSdiType)                 // SDI类型
            put("nSdiSpeedLimit", fields.nSdiSpeedLimit)     // 测速限速 (km/h)
            put("nSdiDist", fields.nSdiDist)                 // 到测速点距离 (m)
            put("nSdiSection", fields.nSdiSection)           // 区间测速ID
            put("nSdiBlockType", fields.nSdiBlockType)       // 区间状态 (1=开始,2=中,3=结束)
            put("nSdiBlockSpeed", fields.nSdiBlockSpeed)     // 区间限速
            put("nSdiBlockDist", fields.nSdiBlockDist)       // 区间距离

            // ========== SDI Plus扩展字段 ==========
            put("nSdiPlusType", fields.nSdiPlusType)         // Plus类型 (22=减速带)
            put("nSdiPlusSpeedLimit", fields.nSdiPlusSpeedLimit) // Plus限速
            put("nSdiPlusDist", fields.nSdiPlusDist)         // Plus距离
            put("nSdiPlusBlockType", fields.nSdiPlusBlockType)   // Plus区间类型
            put("nSdiPlusBlockSpeed", fields.nSdiPlusBlockSpeed) // Plus区间限速
            put("nSdiPlusBlockDist", fields.nSdiPlusBlockDist)   // Plus区间距离

            // ========== TBT转弯导航字段 ==========
            put("nTBTDist", fields.nTBTDist)                 // 转弯距离 (m)
            put("nTBTTurnType", fields.nTBTTurnType)         // 转弯类型
            put("szTBTMainText", fields.szTBTMainText)       // 主要指令文本
            put("szNearDirName", fields.szNearDirName)       // 近处方向名
            put("szFarDirName", fields.szFarDirName)         // 远处方向名
            put("nTBTNextRoadWidth", fields.nTBTNextRoadWidth) // 下一道路宽度 (车道数)
            put("nTBTDistNext", fields.nTBTDistNext)         // 下一转弯距离
            put("nTBTTurnTypeNext", fields.nTBTTurnTypeNext) // 下一转弯类型
            put("szTBTMainTextNext", fields.szTBTMainTextNext) // 下一转弯指令

            // ========== 目的地剩余字段 ==========
            put("nGoPosDist", fields.nGoPosDist)             // 剩余距离 (m)
            put("nGoPosTime", fields.nGoPosTime)             // 剩余时间 (s)

            // ========== 导航状态字段 ==========
            put("isNavigating", fields.isNavigating)         // 是否正在导航

            // ========== 命令控制字段 ==========
            put("carrotCmd", fields.carrotCmd)               // 命令类型
            put("carrotArg", fields.carrotArg)               // 命令参数

            // 🔍 GPS字段已恢复，记录GPS数据日志
            if (fields.latitude != 0.0 && fields.longitude != 0.0) {
                Log.v(TAG, "📤 发送GPS数据: lat=${String.format("%.6f", fields.latitude)}, lon=${String.format("%.6f", fields.longitude)}")
            }
        }
    }
    
    /**
     * 检查数据是否有显著变化
     */
    private fun checkForSignificantChanges(fields: CarrotManFields): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // 检查关键字段变化
        val roadLimitChanged = fields.nRoadLimitSpeed != lastRoadLimitSpeed
        val sdiChanged = fields.nSdiType != lastSdiType || fields.nSdiDist != lastSdiDist
        val tbtChanged = fields.nTBTTurnType != lastTbtType || fields.nTBTDist != lastTbtDist
        val gpsChanged = kotlin.math.abs(fields.latitude - lastLatitude) > 0.0001 || 
                         kotlin.math.abs(fields.longitude - lastLongitude) > 0.0001 ||
                         kotlin.math.abs(fields.heading - lastHeading) > 1.0
        
        // 检查导航状态变化
        val navigationChanged = fields.isNavigating != (lastUpdateTime > 0)
        
        // 检查目的地信息变化
        val destinationChanged = fields.goalPosX != 0.0 || fields.goalPosY != 0.0
        
        // 检查命令变化
        val commandChanged = fields.carrotCmd.isNotEmpty() || fields.carrotArg.isNotEmpty()
        
        // 如果任何关键字段发生变化，标记为需要发送
        val hasChanges = roadLimitChanged || sdiChanged || tbtChanged || gpsChanged || 
                        navigationChanged || destinationChanged || commandChanged
        
        if (hasChanges) {
            Log.d(TAG, "🔄 检测到数据变化: 道路限速=$roadLimitChanged, SDI=$sdiChanged, TBT=$tbtChanged, GPS=$gpsChanged")
            
            // 更新缓存值
            lastRoadLimitSpeed = fields.nRoadLimitSpeed
            lastSdiType = fields.nSdiType
            lastSdiDist = fields.nSdiDist
            lastTbtType = fields.nTBTTurnType
            lastTbtDist = fields.nTBTDist
            lastLatitude = fields.latitude
            lastLongitude = fields.longitude
            lastHeading = fields.heading
        }
        
        return hasChanges
    }
    
    /**
     * 验证GPS数据有效性
     */
    private fun validateGpsData(fields: CarrotManFields): Boolean {
        // 检查坐标有效性
        if (fields.latitude == 0.0 && fields.longitude == 0.0) {
            Log.w(TAG, "⚠️ GPS坐标无效 (0,0)")
            return false
        }
        
        // 检查坐标范围
        if (fields.latitude < -90.0 || fields.latitude > 90.0 || 
            fields.longitude < -180.0 || fields.longitude > 180.0) {
            Log.w(TAG, "⚠️ GPS坐标超出有效范围: lat=${fields.latitude}, lon=${fields.longitude}")
            return false
        }
        
        // 检查精度
        if (fields.accuracy > 100.0) {
            Log.w(TAG, "⚠️ GPS精度过低: ${fields.accuracy}m")
            return false
        }
        
        return true
    }
    
    // 发送UDP数据包到目标设备
    private suspend fun sendDataPacket(jsonData: JSONObject) = withContext(Dispatchers.IO) {
        val device = currentTargetDevice ?: return@withContext
        
        // 如果JSON为空（数据无变化），跳过发送
        if (jsonData.length() == 0) {
            return@withContext
        }
        
        // 如果正在网络恢复中，跳过发送
        if (isNetworkRecovering) {
            Log.d(TAG, "⏸️ 网络恢复中，跳过数据发送")
            return@withContext
        }
        
        try {
            val dataBytes = jsonData.toString().toByteArray(Charsets.UTF_8)
            
            if (dataBytes.size > MAX_PACKET_SIZE) {
                Log.w(TAG, "数据包过大: ${dataBytes.size} bytes (最大: $MAX_PACKET_SIZE)")
                return@withContext
            }
            
            Log.d(TAG, "📡 发送UDP数据包到 ${device.ip}:${device.port}, 大小: ${dataBytes.size} bytes")
            
            val packet = DatagramPacket(
                dataBytes,
                dataBytes.size,
                InetAddress.getByName(device.ip),
                device.port
            )
            
            dataSocket?.send(packet)
            lastSendTime = System.currentTimeMillis()
            
            Log.d(TAG, "✅ UDP数据包发送成功")
            
            // 记录成功发送
            recordSuccessfulSend()
            
            //Log.v(TAG, "UDP数据包发送成功 -> ${device.ip}:${device.port} (${dataBytes.size} bytes)")
            
        } catch (e: Exception) {
            // 使用新的错误处理机制
            val shouldReconnect = handleNetworkError(e, "数据包发送")
            
            // 控制网络错误日志频率，避免刷屏
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNetworkErrorLogTime > 5000) { // 5秒记录一次网络错误
                Log.w(TAG, "⚠️ 网络发送失败: ${e.message}")
                if (e.message?.contains("ENETUNREACH") == true) {
                    Log.w(TAG, "💡 网络不可达 - 请检查：1)设备是否在线 2)WiFi连接 3)网络配置")
                }
                lastNetworkErrorLogTime = currentTime
            }
            
            // 如果不需要重连，则抛出异常
            if (!shouldReconnect) {
                throw e
            }
        }
    }
    
    // 发送交通灯状态更新到comma3设备
    fun sendTrafficLightUpdate(trafficState: Int, leftSec: Int) {
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "网络客户端未运行或设备未连接，无法发送交通灯状态")
            return
        }

        networkScope.launch {
            try {
                val trafficLightMessage = JSONObject().apply {
                    // 基础协议字段 (基于逆向文档)
                    put("carrotIndex", ++carrotIndex)
                    put("epochTime", System.currentTimeMillis() / 1000)
                    put("timezone", "Asia/Shanghai")
                    put("carrotCmd", "traffic_light_update")
                    put("carrotArg", "")
                    put("source", "android_amap")

                    // 交通灯状态字段 (基于逆向文档协议)
                    put("trafficState", trafficState)  // 协议标准字段名
                    put("leftSec", leftSec)           // 协议标准字段名
                    put("traffic_state", trafficState) // 内部兼容字段
                    put("left_sec", leftSec)          // 内部兼容字段

                    // 远程IP地址
                    put("remote", currentTargetDevice?.ip ?: "")
                }

                sendDataPacket(trafficLightMessage)
                totalPacketsSent++

                Log.i(TAG, "🚦 交通灯状态更新已发送: 状态=$trafficState, 倒计时=${leftSec}s")
                onDataSent?.invoke(totalPacketsSent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送交通灯状态更新失败: ${e.message}", e)
            }
        }
    }

    // 发送DETECT命令到comma3设备（只在前方120m内有红灯时发送）
    fun sendDetectCommand(trafficState: Int, leftSec: Int, distance: Int, gpsLat: Double = 0.0, gpsLon: Double = 0.0) {
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "网络客户端未运行或设备未连接，无法发送DETECT命令")
            return
        }

        networkScope.launch {
            try {
                // 🎯 修复：按照Python端期望的格式构造carrotArg
                // 格式: "状态,x坐标,y坐标,置信度"
                val stateString = when (trafficState) {
                    1 -> "Red Light"        // 普通红灯
                    4 -> "Red Light"        // 左转红灯（也映射为红灯）
                    2 -> "Green Light"      // 绿灯
                    3 -> "Yellow Light"     // 黄灯
                    else -> "Red Light"     // 默认红灯
                }
                
                // 🎯 使用真实GPS坐标和高置信度（高德地图数据可信度较高）
                val x = gpsLat  // x坐标 - 使用真实GPS纬度
                val y = gpsLon  // y坐标 - 使用真实GPS经度  
                val confidence = 0.9  // 置信度 - 高德地图数据可信度较高
                
                val detectMessage = JSONObject().apply {
                    // 基础协议字段
                    put("carrotIndex", ++carrotIndex)
                    put("epochTime", System.currentTimeMillis() / 1000)
                    put("timezone", "Asia/Shanghai")
                    put("carrotCmd", "DETECT")
                    
                    put("carrotArg", "$stateString,$x,$y,$confidence")
                    put("source", "android_amap")

                    // 保留用于调试的额外字段
                    put("leftSec", leftSec)           // 剩余倒计时
                    put("distance", distance)         // 距离信息
                    put("androidTrafficState", trafficState) // Android内部状态值

                    // 远程IP地址
                    put("remote", currentTargetDevice?.ip ?: "")
                }

                sendDataPacket(detectMessage)
                totalPacketsSent++

                Log.i(TAG, "🔍 DETECT命令已发送: carrotArg='$stateString,$x,$y,$confidence', 距离=${distance}m")
                onDataSent?.invoke(totalPacketsSent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送DETECT命令失败: ${e.message}", e)
            }
        }
    }

    // 发送专门的目的地更新消息到comma3
    suspend fun sendDestinationUpdate(
        goalPosX: Double,
        goalPosY: Double,
        szGoalName: String,
        goalAddress: String = "",
        priority: String = "high"
    ) {
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "网络客户端未运行或设备未连接，无法发送目的地更新")
            return
        }
        
        try {
            val destinationMessage = JSONObject().apply {
                put("carrotIndex", ++carrotIndex)
                put("epochTime", System.currentTimeMillis() / 1000)
                put("timezone", "Asia/Shanghai")
                put("carrotCmd", "destination_update")
                put("carrotArg", "navigation_destination")
                put("source", "android_amap")
                put("priority", priority)
                
                put("goalPosX", goalPosX)
                put("goalPosY", goalPosY)
                put("szGoalName", szGoalName)
                put("goalAddress", goalAddress)
                
                put("destinationUpdateTime", System.currentTimeMillis())
                put("isNavigating", true)
                put("active_carrot", 1)
                put("dataQuality", "destination_update")
                
                put("coordinateSystem", "WGS84")
                put("coordinatePrecision", 6)
            }
            
            sendDataPacket(destinationMessage)
            totalPacketsSent++
            
            Log.i(TAG, "目的地更新消息已发送: $szGoalName ($goalPosY, $goalPosX)")
            onDataSent?.invoke(totalPacketsSent)
            
        } catch (e: Exception) {
            Log.e(TAG, "发送目的地更新失败: ${e.message}", e)
            throw e
        }
    }

    // 获取网络连接状态信息
    fun getConnectionStatus(): Map<String, Any> {
        return mapOf(
            "isRunning" to isRunning,
            "discoveredDevices" to discoveredDevices.size,
            "currentDevice" to (currentTargetDevice?.toString() ?: "无连接"),
            "totalPacketsSent" to totalPacketsSent,
            "lastSendTime" to lastSendTime,
            "lastDataReceived" to lastDataReceived,
            "carrotIndex" to carrotIndex,
            "deviceList" to discoveredDevices.values.map { it.toString() }
        )
    }
    
    // 获取发现的设备列表
    fun getDiscoveredDevices(): List<DeviceInfo> {
        return discoveredDevices.values.toList()
    }
    
    // 获取当前连接的设备信息
    fun getCurrentDevice(): DeviceInfo? {
        return currentTargetDevice
    }
    
    /**
     * 禁用系统调试输出
     * 减少System.out的调试信息输出
     */
    private fun disableSystemDebugOutput() {
        try {
            // 重定向System.out到空输出流
            System.setOut(object : java.io.PrintStream(java.io.OutputStream.nullOutputStream()) {
                override fun println(x: String?) {
                    // 静默处理，不输出
                }
                override fun print(s: String?) {
                    // 静默处理，不输出
                }
            })
        } catch (e: Exception) {
            // 忽略设置失败，不影响主要功能
        }
    }

    /**
     * 检查网络连接状态
     */
    fun checkNetworkStatus(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val hasConnection = currentTargetDevice != null && isRunning
        val lastErrorTime = if (lastNetworkErrorLogTime > 0) currentTime - lastNetworkErrorLogTime else -1
        
        return mapOf(
            "isRunning" to isRunning,
            "hasConnection" to hasConnection,
            "currentDevice" to (currentTargetDevice?.toString() ?: "无连接"),
            "discoveredDevices" to discoveredDevices.size,
            "lastSendTime" to lastSendTime,
            "lastDataReceived" to lastDataReceived,
            "lastErrorTime" to lastErrorTime,
            "networkQuality" to when {
                hasConnection && lastErrorTime > 30000 -> "优秀"
                hasConnection && lastErrorTime > 10000 -> "良好"
                hasConnection -> "一般"
                else -> "断开"
            }
        )
    }
    
    /**
     * 获取网络状态报告
     */
    fun getNetworkStatusReport(): String {
        val status = checkNetworkStatus()
        return buildString {
            appendLine("🌐 网络状态报告:")
            appendLine("  🔗 连接状态: ${if (status["hasConnection"] as Boolean) "已连接" else "未连接"}")
            appendLine("  📱 当前设备: ${status["currentDevice"]}")
            appendLine("  🔍 发现设备: ${status["discoveredDevices"]}个")
            appendLine("  📊 网络质量: ${status["networkQuality"]}")
            appendLine("  ⏰ 最后发送: ${if (status["lastSendTime"] as Long > 0) "${(System.currentTimeMillis() - status["lastSendTime"] as Long) / 1000}秒前" else "从未发送"}")
            appendLine("  📡 最后接收: ${if (status["lastDataReceived"] as Long > 0) "${(System.currentTimeMillis() - status["lastDataReceived"] as Long) / 1000}秒前" else "从未接收"}")
            if (status["lastErrorTime"] as Long > 0) {
                appendLine("  ⚠️ 最后错误: ${(status["lastErrorTime"] as Long) / 1000}秒前")
            }
            appendLine("  🔄 连续错误: $consecutiveNetworkErrors/$maxConsecutiveErrors")
            appendLine("  🛠️ 恢复状态: ${if (isNetworkRecovering) "正在恢复" else "正常"}")
        }
    }

    /**
     * 处理网络错误并决定是否重连 - 增强版
     */
    private fun handleNetworkError(exception: Exception, operation: String): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // 检查是否在错误阈值时间内
        if (currentTime - lastNetworkErrorTime < networkErrorThreshold) {
            consecutiveNetworkErrors++
        } else {
            consecutiveNetworkErrors = 1
        }
        
        lastNetworkErrorTime = currentTime
        
        // 控制错误日志频率
        if (currentTime - lastNetworkErrorLogTime > 3000) { // 减少到3秒
            Log.w(TAG, "⚠️ 网络错误 [$operation]: ${exception.message}")
            lastNetworkErrorLogTime = currentTime
        }
        
        Log.w(TAG, "🔄 连续错误计数: $consecutiveNetworkErrors/$maxConsecutiveErrors")
        
        // 达到错误阈值时启动智能恢复流程
        if (consecutiveNetworkErrors >= maxConsecutiveErrors) {
            Log.w(TAG, "🚨 达到连续错误阈值，启动智能网络恢复")
            startIntelligentNetworkRecovery()
        }
        
        return consecutiveNetworkErrors >= maxConsecutiveErrors
    }

    /**
     * 启动智能网络恢复流程
     */
    private fun startIntelligentNetworkRecovery() {
        if (isNetworkRecovering) {
            Log.d(TAG, "🔄 网络恢复已在进行中，跳过重复启动")
            return
        }
        
        isNetworkRecovering = true
        reconnectAttempts = 0
        
        networkScope.launch {
            performIntelligentNetworkRecovery()
        }
    }
    
    /**
     * 执行智能网络恢复流程
     */
    private suspend fun performIntelligentNetworkRecovery() {
        try {
            Log.i(TAG, "🔄 开始智能网络恢复流程...")
            
            // 1. 重置当前连接
            currentTargetDevice = null
            onConnectionStatusChanged?.invoke(false, "智能恢复中...")
            
            // 2. 重新初始化Socket
            try {
                dataSocket?.close()
                dataSocket = null
                
                dataSocket = DatagramSocket().apply {
                    soTimeout = SOCKET_TIMEOUT
                }
                Log.i(TAG, "✅ Socket重新初始化成功")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Socket重新初始化失败: ${e.message}", e)
            }
            
            // 3. 智能重连策略
            while (reconnectAttempts < maxReconnectAttempts && isRunning) {
                reconnectAttempts++
                val currentTime = System.currentTimeMillis()
                
                // 检查重连间隔
                if (currentTime - lastReconnectTime < reconnectDelay) {
                    val waitTime = reconnectDelay - (currentTime - lastReconnectTime)
                    Log.d(TAG, "⏳ 等待重连间隔: ${waitTime}ms")
                    delay(waitTime)
                }
                
                lastReconnectTime = System.currentTimeMillis()
                
                Log.i(TAG, "🔍 重新扫描可用设备... (尝试 $reconnectAttempts/$maxReconnectAttempts)")
                
                // 4. 重新扫描设备
                val availableDevices = discoveredDevices.values.filter { it.isActive() }
                
                if (availableDevices.isNotEmpty()) {
                    val targetDevice = availableDevices.first()
                    Log.i(TAG, "🎯 发现可用设备，尝试重连: $targetDevice")
                    
                    // 尝试连接
                    try {
                        connectToDevice(targetDevice)
                        
                        // 等待连接稳定
                        delay(1000)
                        
                        // 验证连接是否成功
                        if (currentTargetDevice != null) {
                            // 重置错误计数
                            consecutiveNetworkErrors = 0
                            isNetworkRecovering = false
                            lastSuccessfulSendTime = System.currentTimeMillis()
                            
                            Log.i(TAG, "✅ 智能网络恢复成功")
                            onConnectionStatusChanged?.invoke(true, "")
                            return
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ 重连尝试失败: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "⚠️ 未发现可用设备，等待设备上线...")
                }
                
                // 增加重连延迟
                reconnectDelay = minOf(reconnectDelay * 2, 10000L) // 最大10秒
            }
            
            // 所有重连尝试失败
            Log.w(TAG, "❌ 智能网络恢复失败，已达到最大重连次数")
            isNetworkRecovering = false
            onConnectionStatusChanged?.invoke(false, "网络恢复失败")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 智能网络恢复异常: ${e.message}", e)
            isNetworkRecovering = false
        }
    }
    
    /**
     * 检查网络稳定性
     */
    private fun checkNetworkStability() {
        val currentTime = System.currentTimeMillis()
        
        // 定期检查网络质量
        if (currentTime - lastNetworkStabilityCheck > networkStabilityCheckInterval) {
            lastNetworkStabilityCheck = currentTime
            
            // 计算网络质量评分
            if (totalSendsInWindow > 0) {
                val successRate = (successfulSendsInWindow.toDouble() / totalSendsInWindow) * 100
                networkQualityScore = successRate.toInt()
                
                // 根据网络质量调整重连策略
                when {
                    networkQualityScore < 50 -> {
                        // 网络质量差，降低错误阈值
                        maxConsecutiveErrors = 2
                        Log.w(TAG, "📉 网络质量较差 (${networkQualityScore}%)，降低错误阈值")
                    }
                    networkQualityScore < 80 -> {
                        // 网络质量一般，使用默认阈值
                        maxConsecutiveErrors = 3
                        Log.i(TAG, "📊 网络质量一般 (${networkQualityScore}%)，使用默认阈值")
                    }
                    else -> {
                        // 网络质量良好，提高错误阈值
                        maxConsecutiveErrors = 5
                        Log.d(TAG, "📈 网络质量良好 (${networkQualityScore}%)，提高错误阈值")
                    }
                }
                
                // 重置统计窗口
                successfulSendsInWindow = 0
                totalSendsInWindow = 0
            }
        }
    }
    
    /**
     * 记录成功发送
     */
    private fun recordSuccessfulSend() {
        successfulSendsInWindow++
        totalSendsInWindow++
        lastSuccessfulSendTime = System.currentTimeMillis()
        
        // 重置连续错误计数
        if (consecutiveNetworkErrors > 0) {
            consecutiveNetworkErrors = 0
            Log.d(TAG, "✅ 网络连接恢复，重置错误计数")
        }
    }
    
    /**
     * 记录失败发送
     */
    private fun recordFailedSend() {
        totalSendsInWindow++
    }

    
    // 设置设备发现事件回调
    fun setOnDeviceDiscovered(callback: (DeviceInfo) -> Unit) {
        onDeviceDiscovered = callback
        Log.d(TAG, "设备发现回调已设置")
    }
    
    // 设置连接状态变化事件回调
    fun setOnConnectionStatusChanged(callback: (Boolean, String) -> Unit) {
        onConnectionStatusChanged = callback
        Log.d(TAG, "连接状态回调已设置")
    }
    
    // 设置数据发送完成事件回调
    fun setOnDataSent(callback: (Int) -> Unit) {
        onDataSent = callback
        Log.d(TAG, "数据发送回调已设置")
    }

    // 设置OpenpPilot状态数据接收回调
    fun setOnOpenpilotStatusReceived(callback: (String) -> Unit) {
        onOpenpilotStatusReceived = callback
        Log.d(TAG, "OpenpPilot状态接收回调已设置")
    }

    // 保存网络状态到SharedPreferences
    private fun saveNetworkStatus(isRunning: Boolean, currentDevice: String) {
        try {
            val sharedPreferences = context.getSharedPreferences("network_status", Context.MODE_PRIVATE)
            sharedPreferences.edit().apply {
                putBoolean("is_running", isRunning)
                putString("current_device", currentDevice)
                putLong("last_update", System.currentTimeMillis())
                apply()
            }
            Log.d(TAG, "网络状态已保存: running=$isRunning, device=$currentDevice")
        } catch (e: Exception) {
            Log.e(TAG, "保存网络状态失败: ${e.message}", e)
        }
    }


    
    // 清理网络客户端资源
    fun cleanup() {
        //Log.i(TAG, "开始清理CarrotMan网络客户端资源")
        
        stop()
        networkScope.cancel()
        discoveredDevices.clear()
        currentTargetDevice = null
        
        carrotIndex = 0L
        totalPacketsSent = 0
        lastSendTime = 0L
        lastDataReceived = 0L
        
        Log.i(TAG, "CarrotMan网络客户端资源清理完成")
    }

    /**
     * 启动自动发送 CarrotMan 导航数据的后台任务
     * @param autoSendEnabled 是否启用自动发送的可变状态
     * @param carrotManFieldsState 当前 CarrotMan 字段的状态容器
     * @param sendInterval      发送间隔，默认为 200ms
     */
    fun startAutoDataSending(
        autoSendEnabled: MutableState<Boolean>,
        carrotManFieldsState: MutableState<CarrotManFields>,
        sendInterval: Long = 200L
    ) {
        Log.i(TAG, "📡 启动自动数据发送任务(客户端)…")

        // 若已有任务在运行，先取消
        autoSendJob?.cancel()

        autoSendJob = networkScope.launch {
            var lastSendTime = 0L
            while (isRunning) {
                try {
                    val currentFields = carrotManFieldsState.value
                    val shouldSend = autoSendEnabled.value && (
                        System.currentTimeMillis() - lastSendTime > sendInterval || 
                        currentFields.needsImmediateSend
                    )
                    
                    if (shouldSend) {
                        // 只在有连接设备时记录详细日志
                        if (currentTargetDevice != null) {
                            if (currentFields.needsImmediateSend) {
                                Log.i(TAG, "🚀 立即发送数据包 (限速变化):")
                            } else {
                                Log.d(TAG, "📤 准备自动发送数据包:")
                            }
                            Log.d(TAG, "   位置: lat=${currentFields.latitude}, lon=${currentFields.longitude}")
                            Log.d(TAG, "  🛣️ 道路: ${currentFields.szPosRoadName}")
                            Log.d(TAG, "  🚦 限速: ${currentFields.nRoadLimitSpeed}km/h")
                            Log.d(TAG, "  🎯 目标: ${currentFields.szGoalName}")
                            Log.d(TAG, "  🧭 导航状态: ${currentFields.isNavigating}")
                            Log.d(TAG, "  🔄 转向信息: 类型=${currentFields.nTBTTurnType}, 距离=${currentFields.nTBTDist}m, 指令=${currentFields.szTBTMainText}")
                            Log.d(TAG, "  🔄 下一转向: 类型=${currentFields.nTBTTurnTypeNext}, 距离=${currentFields.nTBTDistNext}m")
                            Log.d(TAG, "  📏 X系列距离: 转弯=${currentFields.xDistToTurn}m, 下一转弯=${currentFields.xDistToTurnNext}m")
                        }

                        sendCarrotManData(currentFields)
                        lastSendTime = System.currentTimeMillis()
                        
                        // 重置立即发送标记
                        if (currentFields.needsImmediateSend) {
                            carrotManFieldsState.value = currentFields.copy(needsImmediateSend = false)
                        }

                        // 只在有连接设备时记录成功日志
                        if (currentTargetDevice != null) {
                            if (currentFields.needsImmediateSend) {
                                Log.i(TAG, "✅ 立即发送数据包完成 (限速已更新)")
                            } else {
                                //Log.i(TAG, "✅ 自动发送数据包完成")
                            }
                        }
                    } else {
                        //Log.v(TAG, "⏸️ 自动发送跳过: enabled=${autoSendEnabled.value}, 时间间隔=${System.currentTimeMillis() - lastSendTime}ms, 立即发送=${currentFields.needsImmediateSend}")
                    }
                    delay(sendInterval)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 自动数据发送失败: ${'$'}{e.message}", e)
                    delay(1000)
                }
            }
        }
    }

    /**
     * 发送自定义JSON数据包（用于控制指令等）
     * @param jsonData 要发送的JSON数据
     */
    fun sendCustomDataPacket(jsonData: JSONObject) {
        Log.d(TAG, "📦 CarrotManNetworkClient.sendCustomDataPacket: ${jsonData.toString()}")
        
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "⚠️ 网络服务未运行或无连接设备，无法发送自定义数据包")
            Log.w(TAG, "⚠️ 状态检查 - 运行状态: $isRunning, 连接设备: $currentTargetDevice")
            return
        }

        networkScope.launch {
            try {
                Log.d(TAG, "📡 开始发送自定义数据包到设备: ${currentTargetDevice?.ip}:${currentTargetDevice?.port}")
                sendDataPacket(jsonData)
                totalPacketsSent++
                
                Log.i(TAG, "✅ 自定义数据包发送成功 #$totalPacketsSent")
                Log.d(TAG, "📦 数据内容: ${jsonData.toString()}")
                
                onDataSent?.invoke(totalPacketsSent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送自定义数据包失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 启动设备发现服务
     */
    private fun startDeviceDiscovery() {
        if (!deviceDiscoveryEnabled) return
        
        Log.i(TAG, "🔍 启动设备发现服务...")
        
        networkScope.launch {
            while (isRunning && deviceDiscoveryEnabled) {
                try {
                    performDeviceDiscovery()
                    delay(deviceDiscoveryInterval)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 设备发现失败: ${e.message}", e)
                    delay(deviceDiscoveryInterval)
                }
            }
        }
    }
    
    /**
     * 执行设备发现
     */
    private suspend fun performDeviceDiscovery() {
        try {
            val currentTime = System.currentTimeMillis()
            
            // 发送广播发现请求
            sendDiscoveryBroadcast()
            
            // 检查已发现设备的活跃状态
            checkDiscoveredDevices()
            
            // 自动选择最佳设备
            autoSelectBestDevice()
            
            lastDeviceDiscoveryTime = currentTime
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 执行设备发现失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送设备发现广播
     */
    private fun sendDiscoveryBroadcast() {
        try {
            val discoveryMessage = "CARROT_DISCOVERY_REQUEST"
            val packet = DatagramPacket(
                discoveryMessage.toByteArray(),
                discoveryMessage.length,
                InetAddress.getByName("255.255.255.255"),
                BROADCAST_PORT
            )
            
            dataSocket?.send(packet)
            Log.d(TAG, "📡 发送设备发现广播")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送发现广播失败: ${e.message}", e)
        }
    }
    
    /**
     * 检查已发现设备的活跃状态
     */
    private fun checkDiscoveredDevices() {
        val currentTime = System.currentTimeMillis()
        val inactiveDevices = mutableListOf<String>()
        
        discoveredDevices.forEach { (deviceId, device) ->
            if (!device.isActive()) {
                inactiveDevices.add(deviceId)
                Log.d(TAG, "⏰ 设备已离线: $device")
            }
        }
        
        // 移除离线设备
        inactiveDevices.forEach { deviceId ->
            discoveredDevices.remove(deviceId)
            Log.i(TAG, "🗑️ 移除离线设备: $deviceId")
        }
        
        // 如果当前目标设备离线，清除目标
        if (currentTargetDevice != null && !currentTargetDevice!!.isActive()) {
            Log.w(TAG, "⚠️ 当前目标设备已离线，清除目标")
            currentTargetDevice = null
        }
    }
    
    /**
     * 自动选择最佳设备
     */
    private fun autoSelectBestDevice() {
        if (currentTargetDevice != null && currentTargetDevice!!.isActive()) {
            return // 当前设备仍然活跃
        }
        
        val activeDevices = discoveredDevices.values.filter { it.isActive() }
        if (activeDevices.isEmpty()) {
            Log.d(TAG, "📭 没有发现活跃设备")
            return
        }
        
        // 选择最活跃的设备（最近发现的）
        val bestDevice = activeDevices.maxByOrNull { it.lastSeen }
        if (bestDevice != null) {
            currentTargetDevice = bestDevice
            Log.i(TAG, "🎯 自动选择设备: $bestDevice")
            onConnectionStatusChanged?.invoke(true, "已连接到设备: ${bestDevice.ip}")
        }
    }
}

/* =====================================================
   通用目的地与地理计算工具函数 (顶层)  
   提供目的地合法性校验、更新判定以及两点间距离计算，
   抽离自 MainActivity 以减少其代码体积。
   ===================================================== */

/**
 * 验证目的地坐标与名称的合法性。
 * 保证坐标在中国大陆范围内且名称有效。
 */
fun validateDestination(longitude: Double, latitude: Double, name: String): Boolean {
    val isValidLongitude = longitude in 73.0..135.0      // 中国经度范围
    val isValidLatitude = latitude in 18.0..54.0         // 中国纬度范围
    val isValidName = name.isNotEmpty() && name.length <= 100
    val isNonZeroCoordinates = longitude != 0.0 && latitude != 0.0

    return isValidLongitude && isValidLatitude && isValidName && isNonZeroCoordinates
}

/**
 * 判断是否需要更新目的地，避免因坐标微小变化频繁刷新。
 * 若名称不同或距离超过 100 米，或之前目的地尚未设置，则返回 true。
 */
fun shouldUpdateDestination(
    currentLon: Double,
    currentLat: Double,
    currentName: String,
    newLon: Double,
    newLat: Double,
    newName: String,
    distanceThreshold: Double = 100.0
): Boolean {
    val distance = haversineDistance(currentLat, currentLon, newLat, newLon)
    return currentName != newName || distance > distanceThreshold ||
            (currentLon == 0.0 && currentLat == 0.0)
}

/**
 * 计算两点间距离（哈弗辛公式），单位：米。
 */
fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0 // 地球半径（米）
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return R * c
}


