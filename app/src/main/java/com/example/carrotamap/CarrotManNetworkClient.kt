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
import java.io.DataOutputStream
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
        
        // 网络通信端口配置 - 基于逆向分析的准确配置
        private const val BROADCAST_PORT = 7705  // 固定监听端口（接收设备广播）
        private const val MAIN_DATA_PORT = 7706  // 默认发送端口（动态配置）
        private const val TCP_VERTEX_PORT = 7709 // TCP端口（用于Vertex数据）
        private const val COMMAND_PORT = 7706    // 命令端口
        
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
    
    // 动态端口配置（基于逆向分析）
    private var dynamicSendPort: Int = MAIN_DATA_PORT  // 从广播数据动态获取
    private var deviceIP: String? = null               // 从广播数据动态获取
    private var phoneIP: String = ""                   // 手机IP地址
    
    // Socket连接管理
    private var listenSocket: DatagramSocket? = null
    private var dataSocket: DatagramSocket? = null
    private var tcpSocket: Socket? = null  // TCP连接（用于Vertex数据）
    
    // 协程任务管理
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null
    private var dataSendJob: Job? = null
    private var autoSendJob: Job? = null
    private var deviceCheckJob: Job? = null
    
    // 数据统计管理
    private var carrotIndex = 0L
    private var totalPacketsSent = 0
    private var lastSendTime = 0L
    private var lastDataReceived = 0L
    private var lastNoConnectionLogTime = 0L // 添加无连接日志时间控制
    
    // 连接稳定性监控
    private var connectionSwitchCount = 0
    private var lastConnectionSwitchTime = 0L
    private var connectionStabilityThreshold = 10000L // 10秒内切换超过3次认为不稳定

    // ATC状态跟踪（用于日志记录）
    private var lastAtcPausedState: Boolean? = null
    
    // 后台状态追踪 - 用于调整网络策略
    private var isInBackground = false
    
    // 移除数据去重机制，恢复简单发送逻辑
    
    // 事件回调接口
    private var onDeviceDiscovered: ((DeviceInfo) -> Unit)? = null
    private var onConnectionStatusChanged: ((Boolean, String) -> Unit)? = null
    private var onDataSent: ((Int) -> Unit)? = null
    
    /**
     * 设置后台状态
     * @param inBackground 是否在后台运行
     */
    fun setBackgroundState(inBackground: Boolean) {
        isInBackground = inBackground
        // 手动 Log.d(TAG, "🔄 CarrotManNetworkClient后台状态更新: $inBackground")
    }
    private var onOpenpilotStatusReceived: ((String) -> Unit)? = null
    
    // Comma3设备信息数据类（增强设备确认机制）
    data class DeviceInfo(
        val ip: String,          // 设备IP地址
        val port: Int,           // 通信端口号
        val version: String,     // 设备版本信息
        val lastSeen: Long = System.currentTimeMillis(),  // 最后发现时间
        val deviceId: String = "",  // 设备唯一标识
        val capabilities: List<String> = emptyList(),  // 设备能力列表
        val connectionQuality: Float = 0.0f,  // 连接质量评分
        val responseTime: Long = 0L,  // 响应时间
        val isVerified: Boolean = false  // 是否已验证
    ) {
        override fun toString(): String = "$ip:$port (v$version) [${if (isVerified) "✓" else "?"}]"
        
        fun isActive(): Boolean {
            return System.currentTimeMillis() - lastSeen < DEVICE_TIMEOUT
        }
        
        fun isReliable(): Boolean {
            return isVerified && connectionQuality > 0.5f && responseTime < 1000L
        }
    }
    
    // 启动 CarrotMan 网络服务
    fun start() {
        if (isRunning) {
            Log.w(TAG, "网络服务已在运行中，忽略重复启动请求")
            return
        }
        
        Log.i(TAG, "启动 CarrotMan 网络客户端服务")
        isRunning = true
        
        try {
            // 获取手机IP地址
            phoneIP = getPhoneIPAddress()
            Log.i(TAG, "📱 手机IP地址: $phoneIP")
            
            initializeSockets()
            startDeviceListener()
            startDeviceHealthCheck()
            onConnectionStatusChanged?.invoke(false, "")
            Log.i(TAG, "CarrotMan 网络服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动网络服务失败: ${e.message}", e)
            onConnectionStatusChanged?.invoke(false, "")
            stop()
        }
    }
    
    // 停止 CarrotMan 网络服务（基于逆向分析的完整清理）
    fun stop() {
        // 手动 Log.i(TAG, "停止 CarrotMan 网络客户端服务")
        isRunning = false
        
        // 取消所有协程任务
        listenJob?.cancel()
        dataSendJob?.cancel()
        autoSendJob?.cancel()
        deviceCheckJob?.cancel()
        
        // 关闭所有Socket连接
        listenSocket?.close()
        dataSocket?.close()
        tcpSocket?.close()
        
        // 清理Socket引用
        listenSocket = null
        dataSocket = null
        tcpSocket = null
        
        // 清理设备状态
        currentTargetDevice = null
        discoveredDevices.clear()
        
        // 重置动态配置
        dynamicSendPort = MAIN_DATA_PORT
        deviceIP = null
        
        // 重置统计信息
        carrotIndex = 0L
        totalPacketsSent = 0
        lastSendTime = 0L
        lastDataReceived = 0L
        
        onConnectionStatusChanged?.invoke(false, "")
        // 手动 Log.i(TAG, "CarrotMan 网络服务已完全停止")
    }
    
    // 初始化UDP Socket连接
    private fun initializeSockets() {
        try {
            // 手动 Log.d(TAG, "开始初始化UDP Socket连接...")

            listenSocket = DatagramSocket(BROADCAST_PORT).apply {
                soTimeout = 1000 // 1秒超时，更频繁地检查isRunning状态
                reuseAddress = true
                broadcast = true // 启用广播接收
                // 手动 Log.d(TAG, "监听Socket已创建，端口: $BROADCAST_PORT，超时: 1000ms")
            }

            dataSocket = DatagramSocket().apply {
                soTimeout = SOCKET_TIMEOUT
                // 手动 Log.d(TAG, "数据发送Socket已创建，端口: ${localPort}")
            }

            // 手动 Log.i(TAG, "Socket初始化成功 - 监听端口: $BROADCAST_PORT (广播模式)")

        } catch (e: Exception) {
            Log.e(TAG, "Socket初始化失败: ${e.message}", e)
            listenSocket?.close()
            dataSocket?.close()
            listenSocket = null
            dataSocket = null
            throw e
        }
    }
    
    // 启动设备广播监听服务（基于逆向分析的持续监听模式）
    private fun startDeviceListener() {
        listenJob = networkScope.launch {
            // 手动 Log.i(TAG, "启动设备广播监听服务 - 端口: $BROADCAST_PORT")

            while (isRunning) {
                try {
                    // 持续监听设备广播（基于逆向分析的实现）
                    listenForDeviceBroadcasts()
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "设备广播监听异常: ${e.message}", e)
                        // 短暂延迟后重试，避免快速失败循环
                        delay(1000)
                    }
                }

                if (isRunning) {
                    delay(100) // 短暂延迟，避免CPU占用过高
                }
            }
            // 手动 Log.d(TAG, "设备广播监听服务已停止")
        }
    }
    
    // 持续监听设备广播消息
    private suspend fun listenForDeviceBroadcasts() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        val packet = DatagramPacket(buffer, buffer.size)

        // 手动 Log.d(TAG, "开始监听UDP广播数据，端口: $BROADCAST_PORT")

        try {
            // 单次接收广播数据
            listenSocket?.receive(packet)
            val receivedData = String(packet.data, 0, packet.length)
            val deviceIP = packet.address.hostAddress ?: "unknown"

            Log.i(TAG, "📡 收到设备广播: [$receivedData] from $deviceIP")
            Log.d(TAG, "📊 当前状态: 已发现设备=${discoveredDevices.size}, 当前连接=${currentTargetDevice?.ip ?: "无"}")

            lastDataReceived = System.currentTimeMillis()
            parseDeviceBroadcast(receivedData, deviceIP)

        } catch (e: SocketTimeoutException) {
            // 超时是正常的，不需要特殊处理
            // 手动 Log.v(TAG, "广播监听超时，继续等待...")
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
            Log.i(TAG, "🔍 解析设备广播数据: $broadcastData from $deviceIP")

            // 过滤掉手机自己的IP地址
            if (deviceIP == phoneIP) {
                Log.d(TAG, "🚫 过滤手机IP地址: $deviceIP")
                return
            }

            if (broadcastData.trim().startsWith("{")) {
                val jsonBroadcast = JSONObject(broadcastData)

                // 检查是否为OpenpPilot状态数据（基于逆向分析的字段）
                if (isOpenpilotStatusData(jsonBroadcast)) {
                    Log.d(TAG, "📡 检测到OpenpPilot状态数据 from $deviceIP")
                    onOpenpilotStatusReceived?.invoke(broadcastData)

                    // 从JSON数据中获取正确的设备IP和端口
                    val jsonIP = jsonBroadcast.optString("ip", "")
                    val jsonPort = jsonBroadcast.optInt("port", MAIN_DATA_PORT)
                    
                    Log.i(TAG, "🎯 从JSON数据提取IP: jsonIP='$jsonIP', jsonPort=$jsonPort, packetIP='$deviceIP'")
                    
                    // 使用JSON中的IP地址（这是正确的设备IP）
                    val correctIP = if (jsonIP.isNotEmpty()) jsonIP else deviceIP
                    val version = "openpilot"
                    
                    Log.i(TAG, "✅ 确定设备IP: $correctIP (来源: ${if (jsonIP.isNotEmpty()) "JSON数据" else "UDP包"})")
                    
                    // 更新动态端口配置
                    dynamicSendPort = jsonPort
                    this.deviceIP = correctIP
                    
                    // 创建设备信息（简化：有JSON广播就是有效设备）
                    val device = DeviceInfo(
                        ip = correctIP,
                        port = jsonPort,
                        version = version,
                        deviceId = generateDeviceId(correctIP, jsonPort),
                        capabilities = listOf("openpilot", "autopilot", "navigation"),
                        connectionQuality = 1.0f,
                        responseTime = 0L,
                        isVerified = true  // 有JSON广播就是有效设备
                    )
                    
                    addDiscoveredDevice(device)
                    
                    // 解析并处理OpenpPilot状态信息
                    parseOpenpilotStatus(jsonBroadcast)
                    return
                }

                // 处理标准设备发现数据
                val jsonIP = jsonBroadcast.optString("ip", "")
                val jsonPort = jsonBroadcast.optInt("port", MAIN_DATA_PORT)
                val version = jsonBroadcast.optString("version", "unknown")
                
                Log.i(TAG, "🎯 从标准JSON数据提取IP: jsonIP='$jsonIP', jsonPort=$jsonPort, packetIP='$deviceIP'")
                
                // 使用JSON中的IP地址
                val correctIP = if (jsonIP.isNotEmpty()) jsonIP else deviceIP
                
                Log.i(TAG, "✅ 确定标准设备IP: $correctIP (来源: ${if (jsonIP.isNotEmpty()) "JSON数据" else "UDP包"})")
                
                // 更新动态端口配置
                dynamicSendPort = jsonPort
                this.deviceIP = correctIP

                // 创建设备信息（简化：有JSON广播就是有效设备）
                val device = DeviceInfo(
                    ip = correctIP,
                    port = jsonPort,
                    version = version,
                    deviceId = generateDeviceId(correctIP, jsonPort),
                    capabilities = detectDeviceCapabilities(DeviceInfo(correctIP, jsonPort, version)),
                    connectionQuality = 1.0f,
                    responseTime = 0L,
                    isVerified = true  // 有JSON广播就是有效设备
                )
                
                addDiscoveredDevice(device)

            } else {
                // 手动 Log.d(TAG, "收到简单格式广播，使用默认配置: $deviceIP")
                val device = DeviceInfo(deviceIP, MAIN_DATA_PORT, "detected")
                addDiscoveredDevice(device)
            }

        } catch (e: Exception) {
            Log.w(TAG, "广播解析失败，回退到默认模式: $broadcastData - ${e.message}")
            val device = DeviceInfo(deviceIP, MAIN_DATA_PORT, "fallback")
            addDiscoveredDevice(device)
        }
    }
    
    // 解析OpenpPilot状态数据（基于逆向分析的BroadcastData字段）
    private fun parseOpenpilotStatus(jsonData: JSONObject) {
        try {
            // 基于逆向分析的完整字段解析
            val isOnRoad = jsonData.optBoolean("IsOnroad", false)
            val carrotRouteActive = jsonData.optBoolean("CarrotRouteActive", false)
            val active = jsonData.optBoolean("active", false)
            val xState = jsonData.optInt("xState", 0)
            val trafficState = jsonData.optInt("trafficState", 0)
            val vEgoKph = jsonData.optInt("v_ego_kph", 0)
            val vCruiseKph = jsonData.optInt("v_cruise_kph", 0)
            val tbtDist = jsonData.optInt("tbt_dist", 0)
            val sdiDist = jsonData.optInt("sdi_dist", 0)
            val logCarrot = jsonData.optString("log_carrot", "")
            val carrot2 = jsonData.optString("Carrot2", "")
            
            Log.d(TAG, "📊 OpenpPilot状态: 在路上=$isOnRoad, 路线激活=$carrotRouteActive, 活跃=$active")
            Log.d(TAG, "📊 状态码: xState=$xState, 交通=$trafficState, 速度=${vEgoKph}km/h")
            Log.d(TAG, "📊 距离: TBT=${tbtDist}m, SDI=${sdiDist}m")
            
        } catch (e: Exception) {
            Log.w(TAG, "解析OpenpPilot状态失败: ${e.message}")
        }
    }
    

    // 检查JSON数据是否为OpenpPilot状态数据
    private fun isOpenpilotStatusData(jsonObject: JSONObject): Boolean {
        // OpenpPilot状态数据的特征字段
        val hasCarrot2 = jsonObject.has("Carrot2")
        val hasIsOnroad = jsonObject.has("IsOnroad")
        val hasVEgoKph = jsonObject.has("v_ego_kph")
        val hasActive = jsonObject.has("active")
        val hasXState = jsonObject.has("xState")
        
        val isOpenpilot = hasCarrot2 || hasIsOnroad || hasVEgoKph || hasActive || hasXState
        
        Log.d(TAG, "🔍 检查OpenpPilot数据: Carrot2=$hasCarrot2, IsOnroad=$hasIsOnroad, v_ego_kph=$hasVEgoKph, active=$hasActive, xState=$hasXState -> $isOpenpilot")
        
        return isOpenpilot
    }
    
    // 添加新发现的设备到设备列表（基于逆向分析的智能连接策略）
    private fun addDiscoveredDevice(device: DeviceInfo) {
        val deviceKey = "${device.ip}:${device.port}"

        // 手动 Log.d(TAG, "🔍 尝试添加设备: $device, 设备键: $deviceKey")

        if (!discoveredDevices.containsKey(deviceKey)) {
            discoveredDevices[deviceKey] = device
            Log.i(TAG, "🎯 发现新的Comma3设备: $device")
            onDeviceDiscovered?.invoke(device)

            // 基于逆向分析的智能设备连接逻辑
            evaluateDeviceConnection(device)
        } else {
            // 更新设备活跃时间
            discoveredDevices[deviceKey] = device.copy(lastSeen = System.currentTimeMillis())
            // 手动 Log.v(TAG, "🔄 更新设备活跃时间: $deviceKey")
            
            // 如果这是当前连接的设备，也更新其活跃时间
            if (currentTargetDevice?.ip == device.ip && currentTargetDevice?.port == device.port) {
                currentTargetDevice = currentTargetDevice?.copy(lastSeen = System.currentTimeMillis())
            }
        }
    }
    
    // 智能设备连接评估（简化逻辑：有JSON广播就连接）
    private fun evaluateDeviceConnection(newDevice: DeviceInfo) {
        Log.i(TAG, "🔍 评估设备连接: 新设备=$newDevice, 当前设备=${currentTargetDevice?.toString()}")
        
        // 简化逻辑：如果新设备IP与当前设备不同，就切换连接
        if (currentTargetDevice == null || newDevice.ip != currentTargetDevice?.ip) {
            Log.i(TAG, "🔄 切换设备连接: ${currentTargetDevice?.ip ?: "无"} -> ${newDevice.ip}")
            connectToDevice(newDevice)
        } else {
            Log.d(TAG, "✅ 设备IP相同，保持当前连接: ${newDevice.ip}")
            // 更新设备活跃时间
            val deviceKey = "${newDevice.ip}:${newDevice.port}"
            if (discoveredDevices.containsKey(deviceKey)) {
                discoveredDevices[deviceKey] = newDevice.copy(lastSeen = System.currentTimeMillis())
            }
        }
    }
    
    
    
    // 连接到指定的Comma3设备（简化逻辑：直接连接）
    fun connectToDevice(device: DeviceInfo) {
        Log.i(TAG, "🔗 连接到设备: $device")

        // 直接连接，不需要复杂验证
        currentTargetDevice = device
        // 强制更新deviceIP字段，确保使用正确的IP地址
        deviceIP = device.ip
        Log.i(TAG, "🔧 设置deviceIP: ${device.ip}")
        
        dataSendJob?.cancel()
        startDataTransmission()

        Log.i(TAG, "✅ 设备连接成功: ${device.ip}")
        onConnectionStatusChanged?.invoke(true, "")
    }
    
    // 生成设备ID
    private fun generateDeviceId(ip: String, port: Int): String {
        return "${ip.replace(".", "")}_${port}_${System.currentTimeMillis() % 10000}"
    }
    
    // 检测设备能力
    private fun detectDeviceCapabilities(device: DeviceInfo): List<String> {
        val capabilities = mutableListOf<String>()
        
        when (device.version) {
            "openpilot" -> {
                capabilities.add("openpilot")
                capabilities.add("autopilot")
                capabilities.add("navigation")
            }
            "comma3" -> {
                capabilities.add("comma3")
                capabilities.add("navigation")
            }
            else -> {
                capabilities.add("basic")
            }
        }
        
        return capabilities
    }
    
    // 启动数据传输任务
    private fun startDataTransmission() {
        dataSendJob = networkScope.launch {
            // 手动 Log.i(TAG, "启动数据传输任务 - 设备: ${currentTargetDevice?.ip}")
            
            while (isRunning && currentTargetDevice != null) {
                try {
                    sendHeartbeat()
                } catch (e: Exception) {
                    Log.e(TAG, "发送心跳包失败: ${e.message}", e)
                    // 短暂延迟后重试
                    delay(1000)
                }
                
                delay(DATA_SEND_INTERVAL)
            }
            // 手动 Log.d(TAG, "数据传输任务已停止")
        }
    }
    
    
    // 启动设备健康检查服务（基于逆向分析的完善健康检查）
    private fun startDeviceHealthCheck() {
        deviceCheckJob = networkScope.launch {
            // 手动 Log.i(TAG, "启动设备健康检查服务，检查间隔: ${DISCOVER_CHECK_INTERVAL}ms")
            
            while (isRunning) {
                try {
                    // 如果在后台模式，跳过健康检查
                    if (isInBackground) {
                        // 手动 Log.v(TAG, "⏸️ 后台模式，跳过设备健康检查")
                        delay(DISCOVER_CHECK_INTERVAL)
                        continue
                    }
                    
                    performDeviceHealthCheck()
                    delay(DISCOVER_CHECK_INTERVAL)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 设备健康检查失败: ${e.message}", e)
                    delay(5000)
                }
            }
            // 手动 Log.d(TAG, "设备健康检查服务已停止")
        }
    }
    
    // 简化的设备健康检查
    private suspend fun performDeviceHealthCheck() {
        val currentTime = System.currentTimeMillis()
        
        // 清理长时间未活跃的设备
        val timeout = DEVICE_TIMEOUT * 2
        val inactiveDevices = discoveredDevices.values.filter { device ->
            currentTime - device.lastSeen > timeout
        }
        
        inactiveDevices.forEach { device ->
            val deviceKey = "${device.ip}:${device.port}"
            discoveredDevices.remove(deviceKey)
            Log.d(TAG, "移除离线设备: $device")
        }
        
        // 如果当前设备离线，断开连接
        currentTargetDevice?.let { device ->
            if (!device.isActive() && currentTime - device.lastSeen > timeout) {
                Log.w(TAG, "当前设备离线，断开连接: $device")
                currentTargetDevice = null
                dataSendJob?.cancel()
                onConnectionStatusChanged?.invoke(false, "设备离线")
            }
        }
    }
    
    // 发送心跳包维持连接
    private suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val heartbeatData = JSONObject().apply {
            put("carrotIndex", ++carrotIndex)
            put("epochTime", currentTime / 1000)
            put("timestamp", currentTime / 1000.0)
            put("timezone", "Asia/Shanghai")
            put("carrotCmd", "heartbeat")
            put("carrotArg", "")
            put("source", "android_app")
        }
        
        sendDataPacket(heartbeatData)
    }
    
    // 发送CarrotMan导航数据包
    fun sendCarrotManData(carrotFields: CarrotManFields) {
        if (!isRunning || currentTargetDevice == null) {
            return
        }

        networkScope.launch {
            try {
                val jsonData = convertCarrotFieldsToJson(carrotFields)
                sendDataPacket(jsonData)
                onDataSent?.invoke(++totalPacketsSent)
            } catch (e: Exception) {
                Log.e(TAG, "CarrotMan数据发送失败: ${e.message}", e)
            }
        }
    }
    
    // 转换CarrotManFields为JSON协议格式
    private fun convertCarrotFieldsToJson(fields: CarrotManFields): JSONObject {
        // 获取远程IP地址 (基于Python update_navi逻辑)
        val remoteIP = currentTargetDevice?.ip ?: ""
        val currentTime = System.currentTimeMillis()

        return JSONObject().apply {
            // 协议控制字段 (基于Python carrot_man.py逻辑) - 统一时间戳
            put("carrotIndex", ++carrotIndex)
            put("epochTime", currentTime / 1000)
            put("timestamp", currentTime / 1000.0) // 统一时间戳格式，避免时间差
            put("timezone", fields.timezone.ifEmpty { "Asia/Shanghai" })
            put("heading", fields.heading.takeIf { it != 0.0 } ?: fields.bearing)
            put("carrotCmd", "navigation_data")
            put("carrotArg", "")
            // 冗余字段已移除 (source, remote)

            // 目标位置信息字段
            put("goalPosX", fields.goalPosX)
            put("goalPosY", fields.goalPosY)
            put("szGoalName", fields.szGoalName)

            // 道路限速信息字段
            put("nRoadLimitSpeed", fields.nRoadLimitSpeed)
            
            // 添加限速变化检测日志
            if (fields.nRoadLimitSpeed > 0) {
                // 手动 Log.v(TAG, "📤 发送道路限速: ${fields.nRoadLimitSpeed}km/h")
            }

            // 速度控制字段已移除 - Python内部计算

            // SDI摄像头信息字段 (完整字段)
            put("nSdiType", fields.nSdiType)
            put("nSdiSpeedLimit", fields.nSdiSpeedLimit)
            put("nSdiSection", fields.nSdiSection)
            put("nSdiDist", fields.nSdiDist)
            put("nSdiBlockType", fields.nSdiBlockType)
            put("nSdiBlockSpeed", fields.nSdiBlockSpeed)
            put("nSdiBlockDist", fields.nSdiBlockDist)
            put("nSdiPlusType", fields.nSdiPlusType)
            put("nSdiPlusSpeedLimit", fields.nSdiPlusSpeedLimit)
            put("nSdiPlusDist", fields.nSdiPlusDist)
            put("nSdiPlusBlockType", fields.nSdiPlusBlockType)
            put("nSdiPlusBlockSpeed", fields.nSdiPlusBlockSpeed)
            put("nSdiPlusBlockDist", fields.nSdiPlusBlockDist)
            put("roadcate", fields.roadcate)
            put("nLaneCount", fields.laneCount)  // 车道数量

            // TBT转弯引导信息字段 (完整字段)
            put("nTBTDist", fields.nTBTDist)
            put("nTBTTurnType", fields.nTBTTurnType)
            put("szTBTMainText", fields.szTBTMainText)
            put("szNearDirName", fields.szNearDirName)
            put("szFarDirName", fields.szFarDirName)
            put("nTBTNextRoadWidth", fields.nTBTNextRoadWidth)
            put("nTBTDistNext", fields.nTBTDistNext)
            put("nTBTTurnTypeNext", fields.nTBTTurnTypeNext)
            put("szTBTMainTextNext", fields.szTBTMainTextNext)

            // 导航类型和转弯字段已移除 - Python内部计算

            // 位置和导航状态字段
            put("nGoPosDist", fields.nGoPosDist)
            put("nGoPosTime", fields.nGoPosTime)
            put("szPosRoadName", fields.szPosRoadName)

            // 🚀 GPS数据字段 (完整字段) - 关键：这些字段决定Comma3设备的位置显示
            put("latitude", fields.latitude)                 // GPS纬度
            put("longitude", fields.longitude)               // GPS经度
            put("heading", fields.heading)                   // 方向角
            put("accuracy", fields.accuracy)                 // GPS精度
            put("gps_speed", fields.gps_speed)               // GPS速度 (m/s)

            // 🚀 导航位置字段 (comma3需要的兼容字段) - 必须包含
            put("vpPosPointLat", fields.vpPosPointLat)       // 导航纬度
            put("vpPosPointLon", fields.vpPosPointLon)       // 导航经度
            put("nPosAngle", fields.nPosAngle)               // 导航方向角
            put("nPosSpeed", fields.nPosSpeed)               // 导航速度
            
            // 🔍 调试日志：记录发送的GPS坐标
            if (fields.latitude != 0.0 && fields.longitude != 0.0) {
                Log.v(TAG, "📍 发送GPS坐标: lat=${fields.latitude}, lon=${fields.longitude}, vp_lat=${fields.vpPosPointLat}, vp_lon=${fields.vpPosPointLon}")
            }

            // 倒计时字段已移除 - Python内部计算
            // 导航状态字段 (可选)
            put("isNavigating", fields.isNavigating)

            // CarrotMan命令字段
            put("carrotCmd", fields.carrotCmd)
            put("carrotArg", fields.carrotArg)

        }
    }
    
    
    // 发送UDP数据包到目标设备（使用动态端口配置）
    private suspend fun sendDataPacket(jsonData: JSONObject) = withContext(Dispatchers.IO) {
        val device = currentTargetDevice ?: return@withContext
        
        try {
            val dataBytes = jsonData.toString().toByteArray(Charsets.UTF_8)
            
            if (dataBytes.size > MAX_PACKET_SIZE) {
                Log.w(TAG, "数据包过大: ${dataBytes.size} bytes (最大: $MAX_PACKET_SIZE)")
                return@withContext
            }
            
            // 使用动态端口配置（基于逆向分析）
            val targetPort = if (dynamicSendPort != MAIN_DATA_PORT) dynamicSendPort else device.port
            val targetIP = deviceIP ?: device.ip
            
            val packet = DatagramPacket(
                dataBytes,
                dataBytes.size,
                InetAddress.getByName(targetIP),
                targetPort
            )
            
            dataSocket?.send(packet)
            lastSendTime = System.currentTimeMillis()
            
            // 手动 Log.v(TAG, "UDP数据包发送成功 -> $targetIP:$targetPort (${dataBytes.size} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "UDP数据包发送失败: ${e.message}", e)
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
        // 优先使用从JSON数据中获取的deviceIP
        if (deviceIP != null && currentTargetDevice != null) {
            // 如果deviceIP和currentTargetDevice都存在，返回使用deviceIP的设备信息
            return currentTargetDevice!!.copy(ip = deviceIP!!)
        }
        return currentTargetDevice
    }
    
    // 获取手机IP地址
    private fun getPhoneIPAddress(): String {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            
            // 将int类型的IP地址转换为字符串格式
            val ip = String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
            
            return ip
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取手机IP地址失败: ${e.message}")
            return ""
        }
    }

    // 获取设备IP地址（优先使用从JSON数据中解析的IP）
    fun getDeviceIP(): String? {
        // 优先返回从JSON数据中解析的deviceIP
        val ip = deviceIP ?: currentTargetDevice?.ip
        Log.i(TAG, "🔍 获取设备IP: deviceIP=$deviceIP, currentTargetDevice.ip=${currentTargetDevice?.ip}, 最终IP=$ip")
        Log.i(TAG, "📊 设备状态: 运行状态=$isRunning, 发现设备数=${discoveredDevices.size}, 当前设备=${currentTargetDevice?.toString()}")
        return ip
    }

    // 获取手机IP地址
    fun getPhoneIP(): String {
        return phoneIP.ifEmpty { "未获取" }
    }
    
    // 设置设备发现事件回调
    fun setOnDeviceDiscovered(callback: (DeviceInfo) -> Unit) {
        onDeviceDiscovered = callback
        // 手动 Log.d(TAG, "设备发现回调已设置")
    }
    
    // 设置连接状态变化事件回调
    fun setOnConnectionStatusChanged(callback: (Boolean, String) -> Unit) {
        onConnectionStatusChanged = callback
        // 手动 Log.d(TAG, "连接状态回调已设置")
    }
    
    // 设置数据发送完成事件回调
    fun setOnDataSent(callback: (Int) -> Unit) {
        onDataSent = callback
        // 手动 Log.d(TAG, "数据发送回调已设置")
    }

    // 设置OpenpPilot状态数据接收回调
    fun setOnOpenpilotStatusReceived(callback: (String) -> Unit) {
        onOpenpilotStatusReceived = callback
        // 手动 Log.d(TAG, "OpenpPilot状态接收回调已设置")
    }
    
    // 清理网络客户端资源
    fun cleanup() {
        // 手动 Log.i(TAG, "开始清理CarrotMan网络客户端资源")
        
        stop()
        networkScope.cancel()
        discoveredDevices.clear()
        currentTargetDevice = null
        
        carrotIndex = 0L
        totalPacketsSent = 0
        lastSendTime = 0L
        lastDataReceived = 0L
        
        // 数据去重状态已移除
        
        // 手动 Log.i(TAG, "CarrotMan网络客户端资源清理完成")
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
        // 手动 Log.i(TAG, "📡 启动自动数据发送任务(客户端)…")

        // 若已有任务在运行，先取消
        autoSendJob?.cancel()

        autoSendJob = networkScope.launch {
            var lastSendTime = 0L
            while (isRunning) {
                try {
                    if (autoSendEnabled.value && System.currentTimeMillis() - lastSendTime > sendInterval) {
                    val currentFields = carrotManFieldsState.value
                        // 只在有连接设备时记录详细日志
                        if (currentTargetDevice != null) {
                                // 手动 Log.d(TAG, "📤 准备自动发送数据包:")
                            // 手动 Log.d(TAG, "   位置: lat=${currentFields.latitude}, lon=${currentFields.longitude}")
                            // 手动 Log.d(TAG, "  🛣️ 道路: ${currentFields.szPosRoadName}")
                            // 手动 Log.d(TAG, "  🎯 目标: ${currentFields.szGoalName}")
                            // 手动 Log.d(TAG, "  🧭 导航状态: ${currentFields.isNavigating}")
                        }

                        sendCarrotManData(currentFields)
                        lastSendTime = System.currentTimeMillis()

                        // 只在有连接设备时记录成功日志
                        if (currentTargetDevice != null) {
                            // 手动 Log.i(TAG, "✅ 自动发送数据包完成")
                        }
                    } else {
                        // 手动 Log.v(TAG, "⏸️ 自动发送跳过: enabled=${autoSendEnabled.value}, 时间间隔=${System.currentTimeMillis() - lastSendTime}ms")
                    }
                    delay(sendInterval)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 自动数据发送失败: ${'$'}{e.message}", e)
                    delay(1000)
                }
            }
        }
    }

    
    
}