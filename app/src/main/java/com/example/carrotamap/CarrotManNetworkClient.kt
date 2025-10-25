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
    
    // 启动 CarrotMan 网络服务（基于逆向分析的完整启动流程）
    fun start() {
        if (isRunning) {
            Log.w(TAG, "网络服务已在运行中，忽略重复启动请求")
            return
        }
        
        // 手动 Log.i(TAG, "启动 CarrotMan 网络客户端服务")
        isRunning = true
        
        try {
            initializeSockets()
            startDeviceListener()
            startDeviceHealthCheck()
            startDeviceDiscovery()  // 添加主动设备发现
            onConnectionStatusChanged?.invoke(false, "")
            // 手动 Log.i(TAG, "CarrotMan 网络服务启动成功")
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

            // 手动 Log.i(TAG, "📡 收到设备广播: [$receivedData] from $deviceIP")
            // 手动 Log.d(TAG, "📊 当前状态: 已发现设备=${discoveredDevices.size}, 当前连接=${currentTargetDevice?.ip ?: "无"}")

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
    
    // 解析收到的设备广播数据（基于UDP广播JSON数据确认设备）
    private fun parseDeviceBroadcast(broadcastData: String, deviceIP: String) {
        try {
            // 手动 Log.i(TAG, "🔍 解析设备广播数据: $broadcastData from $deviceIP")

            if (broadcastData.trim().startsWith("{")) {
                val jsonBroadcast = JSONObject(broadcastData)

                // 检查是否为OpenpPilot状态数据（基于逆向分析的字段）
                if (isOpenpilotStatusData(jsonBroadcast)) {
                    // 手动 Log.d(TAG, "📡 检测到OpenpPilot状态数据 from $deviceIP")
                    onOpenpilotStatusReceived?.invoke(broadcastData)

                    // 从JSON数据中获取正确的设备IP和端口
                    val jsonIP = jsonBroadcast.optString("ip", "")
                    val jsonPort = jsonBroadcast.optInt("port", MAIN_DATA_PORT)
                    
                    // 使用JSON中的IP地址（这是正确的设备IP）
                    val correctIP = if (jsonIP.isNotEmpty()) jsonIP else deviceIP
                    val version = "openpilot"
                    
                    // 更新动态端口配置
                    dynamicSendPort = jsonPort
                    this.deviceIP = correctIP
                    
                    // 创建已验证的设备信息
                    val device = DeviceInfo(
                        ip = correctIP,
                        port = jsonPort,
                        version = version,
                        deviceId = generateDeviceId(correctIP, jsonPort),
                        capabilities = listOf("openpilot", "autopilot", "navigation"),
                        connectionQuality = 1.0f,  // 广播数据表示设备活跃
                        responseTime = 0L,
                        isVerified = true  // 基于广播数据验证
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
                
                // 使用JSON中的IP地址
                val correctIP = if (jsonIP.isNotEmpty()) jsonIP else deviceIP
                
                // 更新动态端口配置
                dynamicSendPort = jsonPort
                this.deviceIP = correctIP

                // 创建已验证的设备信息
                val device = DeviceInfo(
                    ip = correctIP,
                    port = jsonPort,
                    version = version,
                    deviceId = generateDeviceId(correctIP, jsonPort),
                    capabilities = detectDeviceCapabilities(DeviceInfo(correctIP, jsonPort, version)),
                    connectionQuality = 0.8f,  // 基于广播数据的基础质量
                    responseTime = 0L,
                    isVerified = true  // 基于广播数据验证
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
            
            // 手动 Log.d(TAG, "📊 OpenpPilot状态: 在路上=$isOnRoad, 路线激活=$carrotRouteActive, 活跃=$active")
            // 手动 Log.d(TAG, "📊 状态码: xState=$xState, 交通=$trafficState, 速度=${vEgoKph}km/h")
            // 手动 Log.d(TAG, "📊 距离: TBT=${tbtDist}m, SDI=${sdiDist}m")
            
        } catch (e: Exception) {
            Log.w(TAG, "解析OpenpPilot状态失败: ${e.message}")
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
    
    // 添加新发现的设备到设备列表（基于逆向分析的智能连接策略）
    private fun addDiscoveredDevice(device: DeviceInfo) {
        val deviceKey = "${device.ip}:${device.port}"

        // 手动 Log.d(TAG, "🔍 尝试添加设备: $device, 设备键: $deviceKey")

        if (!discoveredDevices.containsKey(deviceKey)) {
            discoveredDevices[deviceKey] = device
            // 手动 Log.i(TAG, "🎯 发现新的Comma3设备: $device")
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
    
    // 智能设备连接评估（保守连接策略，减少频繁切换）
    private fun evaluateDeviceConnection(newDevice: DeviceInfo) {
        when {
            // 情况1：没有当前连接设备，直接连接
            currentTargetDevice == null -> {
                // 手动 Log.i(TAG, "🔄 更新状态: 发现设备 ${newDevice.ip}，正在连接...")
                onConnectionStatusChanged?.invoke(false, "发现设备 ${newDevice.ip}，正在连接...")
                connectToDevice(newDevice)
            }
            
            // 情况2：当前设备长时间不活跃（超过30秒），切换到新设备
            !currentTargetDevice!!.isActive() && 
            (System.currentTimeMillis() - currentTargetDevice!!.lastSeen > 30000) -> {
                // 手动 Log.i(TAG, "🔄 当前设备长时间不活跃，切换到新设备: ${newDevice.ip}")
                connectToDevice(newDevice)
            }
            
            // 情况3：新设备是OpenpPilot且当前设备不是，且当前设备连接质量很差
            newDevice.version == "openpilot" && 
            currentTargetDevice?.version != "openpilot" &&
            (currentTargetDevice?.connectionQuality ?: 1.0f) < 0.3f -> {
                // 手动 Log.i(TAG, "🔄 发现OpenpPilot设备且当前设备质量差，切换连接: ${newDevice.ip}")
                connectToDevice(newDevice)
            }
            
            // 情况4：保持当前连接（更保守的策略）
            else -> {
                // 手动 Log.d(TAG, "⚠️ 已有活跃连接设备 ${currentTargetDevice?.ip}，保持当前连接")
                // 更新设备活跃时间，避免误判为离线
                val deviceKey = "${newDevice.ip}:${newDevice.port}"
                if (discoveredDevices.containsKey(deviceKey)) {
                    discoveredDevices[deviceKey] = newDevice.copy(lastSeen = System.currentTimeMillis())
                }
            }
        }
    }
    
    // 判断是否应该切换到新设备（修复频繁切换问题）
    private fun shouldSwitchToNewDevice(newDevice: DeviceInfo): Boolean {
        val currentDevice = currentTargetDevice ?: return true
        
        // 如果当前设备仍然活跃，避免频繁切换
        if (currentDevice.isActive()) {
            // 只有在明显优势时才切换
            return when {
                // 新设备是OpenpPilot设备，当前不是（明显优势）
                newDevice.version == "openpilot" && currentDevice.version != "openpilot" -> true
                
                // 新设备连接质量显著更好（时间差超过10秒）
                newDevice.lastSeen > currentDevice.lastSeen + 10000 -> true
                
                else -> false
            }
        }
        
        // 当前设备不活跃时，允许切换
        return true
    }
    
    // 判断网络拓扑优先级（基于IP地址的简单判断）
    private fun isBetterNetworkTopology(newIP: String, currentIP: String): Boolean {
        // 简单的网络拓扑判断：优先选择更小的IP地址（通常是更稳定的设备）
        return try {
            val newIPParts = newIP.split(".").map { it.toInt() }
            val currentIPParts = currentIP.split(".").map { it.toInt() }
            
            // 比较IP地址的数值大小
            for (i in 0..3) {
                when {
                    newIPParts[i] < currentIPParts[i] -> return true
                    newIPParts[i] > currentIPParts[i] -> return false
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
    
    
    // 连接到指定的Comma3设备（基于广播数据快速连接）
    fun connectToDevice(device: DeviceInfo) {
        val currentTime = System.currentTimeMillis()
        
        // 检查连接稳定性
        if (currentTime - lastConnectionSwitchTime < connectionStabilityThreshold) {
            connectionSwitchCount++
            if (connectionSwitchCount > 3) {
                Log.w(TAG, "⚠️ 连接频繁切换，可能存在网络不稳定问题")
            }
        } else {
            connectionSwitchCount = 1
        }
        lastConnectionSwitchTime = currentTime
        
        // 手动 Log.i(TAG, "🔗 开始连接到Comma3设备: $device")

        // 基于广播数据验证的设备直接连接（无需额外验证）
        if (device.isVerified) {
            currentTargetDevice = device
            dataSendJob?.cancel()
            startDataTransmission()

            // 手动 Log.i(TAG, "✅ 更新连接状态: 已连接到设备 ${device.ip}")
            onConnectionStatusChanged?.invoke(true, "")
            // 手动 Log.i(TAG, "🎉 设备连接建立成功: ${device.ip}")
        } else {
            // 对于未验证的设备，进行快速验证（2秒内完成）
            networkScope.launch {
                val verifiedDevice = verifyDeviceConnection(device)
                if (verifiedDevice != null) {
                    currentTargetDevice = verifiedDevice
                    dataSendJob?.cancel()
                    startDataTransmission()

                    // 手动 Log.i(TAG, "✅ 更新连接状态: 已连接到设备 ${device.ip}")
                    onConnectionStatusChanged?.invoke(true, "")
                    // 手动 Log.i(TAG, "🎉 设备连接建立成功: ${device.ip}")
                } else {
                    Log.w(TAG, "❌ 设备验证失败: $device")
                    onConnectionStatusChanged?.invoke(false, "设备验证失败")
                }
            }
        }
    }
    
    // 验证设备连接质量（新增设备确认机制）
    private suspend fun verifyDeviceConnection(device: DeviceInfo): DeviceInfo? = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            
            // 1. 发送验证ping
            val pingResult = sendVerificationPing(device)
            val responseTime = System.currentTimeMillis() - startTime
            
            if (pingResult) {
                // 2. 测试数据传输
                val dataTestResult = testDataTransmission(device)
                
                // 3. 计算连接质量
                val quality = calculateConnectionQuality(responseTime, dataTestResult)
                
                // 4. 返回验证后的设备信息
                return@withContext device.copy(
                    isVerified = true,
                    responseTime = responseTime,
                    connectionQuality = quality,
                    deviceId = generateDeviceId(device.ip, device.port),
                    capabilities = detectDeviceCapabilities(device)
                )
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "设备验证失败: ${e.message}", e)
            return@withContext null
        }
    }
    
    // 发送验证ping
    private suspend fun sendVerificationPing(device: DeviceInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val pingData = JSONObject().apply {
                put("type", "ping")
                put("timestamp", System.currentTimeMillis())
                put("source", "android_app")
            }
            
            val dataBytes = pingData.toString().toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                dataBytes,
                dataBytes.size,
                InetAddress.getByName(device.ip),
                device.port
            )
            
            dataSocket?.send(packet)
            // 手动 Log.d(TAG, "发送验证ping到: ${device.ip}:${device.port}")
            
            // 快速验证（2秒超时）
            delay(2000)
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "发送验证ping失败: ${e.message}", e)
            return@withContext false
        }
    }
    
    // 测试数据传输
    private suspend fun testDataTransmission(device: DeviceInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val testData = JSONObject().apply {
                put("type", "test")
                put("timestamp", System.currentTimeMillis())
                put("data", "connection_test")
            }
            
            val dataBytes = testData.toString().toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                dataBytes,
                dataBytes.size,
                InetAddress.getByName(device.ip),
                device.port
            )
            
            dataSocket?.send(packet)
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "数据传输测试失败: ${e.message}", e)
            return@withContext false
        }
    }
    
    // 计算连接质量
    private fun calculateConnectionQuality(responseTime: Long, dataTestResult: Boolean): Float {
        var quality = 1.0f
        
        // 响应时间评分
        when {
            responseTime < 100 -> quality *= 1.0f
            responseTime < 500 -> quality *= 0.8f
            responseTime < 1000 -> quality *= 0.6f
            else -> quality *= 0.3f
        }
        
        // 数据传输测试评分
        if (!dataTestResult) {
            quality *= 0.5f
        }
        
        return quality.coerceIn(0.0f, 1.0f)
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
    
    // 执行设备健康检查（修复连接稳定性问题）
    private suspend fun performDeviceHealthCheck() {
        val currentTime = System.currentTimeMillis()
        val initialDeviceCount = discoveredDevices.size
        
        // 更长的超时时间，减少误判
        val timeout = if (isInBackground) DEVICE_TIMEOUT * 5 else DEVICE_TIMEOUT * 2
        
        // 1. 清理离线设备（更保守的策略）
        val removedDevices = discoveredDevices.values.filter { device ->
            currentTime - device.lastSeen > timeout
        }
        
        removedDevices.forEach { device ->
            val deviceKey = "${device.ip}:${device.port}"
            discoveredDevices.remove(deviceKey)
            // 手动 Log.i(TAG, "移除离线设备: $device")
        }
        
        // 2. 检查当前连接设备状态（修复频繁断开问题）
        currentTargetDevice?.let { device ->
            val deviceKey = "${device.ip}:${device.port}"
            
            // 只有在设备真正从发现列表中移除时才断开连接
            if (!discoveredDevices.containsKey(deviceKey)) {
                Log.w(TAG, "当前连接设备已从发现列表移除: $device")
                handleCurrentDeviceDisconnection()
            } else {
                // 更新设备活跃时间（避免频繁断开）
                val updatedDevice = discoveredDevices[deviceKey]?.copy(lastSeen = currentTime)
                if (updatedDevice != null) {
                    discoveredDevices[deviceKey] = updatedDevice
                }
                
                // 只有在设备真正不活跃时才断开（增加容错时间）
                if (!device.isActive() && currentTime - device.lastSeen > timeout / 2) {
                    Log.w(TAG, "当前设备长时间不活跃: $device")
                    handleCurrentDeviceDisconnection()
                }
            }
        }
        
        // 3. 自动选择最佳设备（如果没有当前连接）
        if (currentTargetDevice == null && discoveredDevices.isNotEmpty()) {
            selectBestAvailableDevice()
        }
        
        // 4. 更新连接状态
        updateConnectionStatus()
        
        if (removedDevices.isNotEmpty()) {
            // 手动 Log.d(TAG, "健康检查完成 - 设备数量: $initialDeviceCount -> ${discoveredDevices.size}")
        }
    }
    
    // 处理当前设备断开连接（修复协程取消异常）
    private suspend fun handleCurrentDeviceDisconnection() {
        try {
            currentTargetDevice = null
            
            // 安全取消数据传输任务
            dataSendJob?.cancel()
            dataSendJob = null
            
            // 尝试自动切换到备用设备
            selectBestAvailableDevice()
        } catch (e: Exception) {
            Log.e(TAG, "处理设备断开连接时发生异常: ${e.message}", e)
        }
    }
    
    // 选择最佳可用设备（基于逆向分析的设备选择策略）
    private suspend fun selectBestAvailableDevice() {
        val activeDevices = discoveredDevices.values.filter { it.isActive() }
        
        if (activeDevices.isNotEmpty()) {
            // 基于逆向分析的设备优先级选择
            val bestDevice = selectDeviceByPriority(activeDevices)
            // 手动 Log.i(TAG, "自动选择最佳设备: $bestDevice")
            connectToDevice(bestDevice)
        } else {
            Log.w(TAG, "没有可用的备用设备")
            onConnectionStatusChanged?.invoke(false, "没有可用设备")
        }
    }
    
    // 基于优先级选择设备（增强设备确认机制）
    private fun selectDeviceByPriority(devices: List<DeviceInfo>): DeviceInfo {
        return devices.sortedWith(compareBy<DeviceInfo> { device ->
            // 优先级1：已验证且可靠的设备
            when {
                device.isReliable() -> 0
                device.isVerified -> 1
                else -> 2
            }
        }.thenBy { device ->
            // 优先级2：连接质量评分
            -device.connectionQuality
        }.thenBy { device ->
            // 优先级3：OpenpPilot设备优先
            when (device.version) {
                "openpilot" -> 0
                "comma3" -> 1
                else -> 2
            }
        }.thenBy { device ->
            // 优先级4：响应时间
            device.responseTime
        }.thenBy { device ->
            // 优先级5：更近期的活跃时间
            -device.lastSeen
        }).first()
    }
    
    // 更新连接状态（基于逆向分析的状态管理）
    private fun updateConnectionStatus() {
        when {
            currentTargetDevice != null -> {
                // 有活跃连接
                onConnectionStatusChanged?.invoke(true, "")
            }
            discoveredDevices.isNotEmpty() -> {
                // 有发现设备但未连接
                onConnectionStatusChanged?.invoke(false, "发现设备但未连接")
            }
            else -> {
                // 没有发现任何设备
                onConnectionStatusChanged?.invoke(false, "未发现设备")
            }
        }
    }
    
    // 发送心跳包维持连接 - 恢复简单发送逻辑
    private suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val heartbeatData = JSONObject().apply {
            put("carrotIndex", ++carrotIndex)
            put("epochTime", currentTime / 1000)
            put("timestamp", currentTime / 1000.0) // 统一时间戳格式
            put("timezone", "Asia/Shanghai")
            put("carrotCmd", "heartbeat")
            put("carrotArg", "")
            put("source", "android_app")
        }
        
        // 直接发送心跳包，不做去重检查
        sendDataPacket(heartbeatData)
        // 手动 Log.v(TAG, "心跳包已发送，索引: $carrotIndex")
    }
    
    // 发送CarrotMan导航数据包 - 恢复简单发送逻辑，移除数据去重
    fun sendCarrotManData(carrotFields: CarrotManFields) {
        if (!isRunning || currentTargetDevice == null) {
            // 降低无连接时的日志级别，避免日志刷屏
            if (System.currentTimeMillis() - lastNoConnectionLogTime > 10000) { // 10秒记录一次
                Log.w(TAG, "发送CarrotMan数据 - 服务未运行或无连接设备")
                // 手动 Log.d(TAG, "状态检查 - 运行状态: $isRunning, 连接设备: $currentTargetDevice")
                lastNoConnectionLogTime = System.currentTimeMillis()
            }
            return
        }

        networkScope.launch {
            try {
                val jsonData = convertCarrotFieldsToJson(carrotFields)
                
                // 直接发送数据，不做去重检查
                sendDataPacket(jsonData)
                onDataSent?.invoke(++totalPacketsSent)
                // 手动 Log.v(TAG, "CarrotMan数据包发送成功 #$totalPacketsSent")
            } catch (e: Exception) {
                Log.e(TAG, "CarrotMan数据发送失败: ${e.message}", e)
                // 发送失败时短暂延迟，避免快速重试
                delay(500)
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

            // GPS数据字段 (完整字段)
            put("latitude", fields.latitude)                 // GPS纬度
            put("longitude", fields.longitude)               // GPS经度
            put("heading", fields.heading)                   // 方向角
            put("accuracy", fields.accuracy)                 // GPS精度
            put("gps_speed", fields.gps_speed)               // GPS速度 (m/s)

            // 导航位置字段 (comma3需要的兼容字段)
            put("vpPosPointLat", fields.vpPosPointLat)       // 导航纬度
            put("vpPosPointLon", fields.vpPosPointLon)       // 导航经度
            put("nPosAngle", fields.nPosAngle)               // 导航方向角
            put("nPosSpeed", fields.nPosSpeed)               // 导航速度

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
    
    // 发送TCP数据包（用于Vertex数据，基于逆向分析）
    private suspend fun sendTcpDataPacket(vertexData: List<Pair<Float, Float>>) = withContext(Dispatchers.IO) {
        val device = currentTargetDevice ?: return@withContext
        
        try {
            // 创建TCP连接
            tcpSocket = Socket(device.ip, TCP_VERTEX_PORT).apply {
                soTimeout = SOCKET_TIMEOUT
            }
            
            val outputStream = tcpSocket?.getOutputStream() as DataOutputStream
            
            // 写入顶点数量（基于逆向分析的格式）
            outputStream.writeInt(vertexData.size * 8)  // 每个顶点8字节（2个float）
            
            // 写入顶点坐标
            for ((x, y) in vertexData) {
                outputStream.writeFloat(x)
                outputStream.writeFloat(y)
            }
            
            outputStream.flush()
            // 手动 Log.v(TAG, "TCP Vertex数据发送成功 -> ${device.ip}:$TCP_VERTEX_PORT (${vertexData.size} 顶点)")
            
        } catch (e: Exception) {
            Log.e(TAG, "TCP Vertex数据发送失败: ${e.message}", e)
            throw e
        } finally {
            tcpSocket?.close()
            tcpSocket = null
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

                // 手动 Log.i(TAG, "🚦 交通灯状态更新已发送: 状态=$trafficState, 倒计时=${leftSec}s")
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

                // 手动 Log.i(TAG, "🔍 DETECT命令已发送: carrotArg='$stateString,$x,$y,$confidence', 距离=${distance}m")
                onDataSent?.invoke(totalPacketsSent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送DETECT命令失败: ${e.message}", e)
            }
        }
    }

    // 发送CarrotMan数据到Comma3设备（实时发送）
    suspend fun sendCarrotManData(carrotData: CarrotManData) {
        try {
            if (currentTargetDevice == null) {
                Log.w(TAG, "⚠️ 没有连接的设备，无法发送CarrotMan数据")
                return
            }

            val currentTime = System.currentTimeMillis()
            
            // 构建CarrotMan数据包（基于逆向分析的完整结构）
            val dataPacket = JSONObject().apply {
                put("type", "carrotman_data")
                put("timestamp", currentTime)
                put("carrotIndex", carrotData.carrotIndex)
                
                // 导航信息
                put("nTBTTurnType", carrotData.nTBTTurnType)
                put("nTBTDist", carrotData.nTBTDist)
                put("szTBTMainText", carrotData.szTBTMainText)
                put("szNearDirName", carrotData.szNearDirName)
                put("szFarDirName", carrotData.szFarDirName)
                
                // 位置信息
                put("vpPosPointLat", carrotData.vpPosPointLat)
                put("vpPosPointLon", carrotData.vpPosPointLon)
                put("vpPosPointLatNavi", carrotData.vpPosPointLatNavi)
                put("vpPosPointLonNavi", carrotData.vpPosPointLonNavi)
                
                // 目的地信息
                put("goalPosX", carrotData.goalPosX)
                put("goalPosY", carrotData.goalPosY)
                put("szGoalName", carrotData.szGoalName)
                
                // 道路信息
                put("roadcate", carrotData.roadcate)
                put("nRoadLimitSpeed", carrotData.nRoadLimitSpeed)
                
                // SDI信息
                put("nSdiType", carrotData.nSdiType)
                put("nSdiSpeedLimit", carrotData.nSdiSpeedLimit)
                put("nSdiDist", carrotData.nSdiDist)
                
                // 系统状态
                put("active_carrot", carrotData.active_carrot)
                put("isNavigating", carrotData.isNavigating)
                put("source", "android_app")
            }

            // 发送到动态端口
            val targetPort = if (dynamicSendPort != MAIN_DATA_PORT) dynamicSendPort else currentTargetDevice!!.port
            val targetIP = deviceIP ?: currentTargetDevice!!.ip
            
            val dataBytes = dataPacket.toString().toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                dataBytes,
                dataBytes.size,
                InetAddress.getByName(targetIP),
                targetPort
            )

            dataSocket?.send(packet)
            totalPacketsSent++
            lastSendTime = currentTime

            Log.d(TAG, "📤 CarrotMan数据已发送: 转弯类型=${carrotData.nTBTTurnType}, 距离=${carrotData.nTBTDist}m")
            onDataSent?.invoke(totalPacketsSent)
            
        } catch (e: Exception) {
            Log.e(TAG, "发送CarrotMan数据失败: ${e.message}", e)
            throw e
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
            
            // 手动 Log.i(TAG, "目的地更新消息已发送: $szGoalName ($goalPosY, $goalPosX)")
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

    /**
     * 发送Vertex数据（TCP方式，基于逆向分析）
     * @param vertexData 顶点坐标列表
     */
    fun sendVertexData(vertexData: List<Pair<Float, Float>>) {
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "网络客户端未运行或设备未连接，无法发送Vertex数据")
            return
        }

        networkScope.launch {
            try {
                sendTcpDataPacket(vertexData)
                totalPacketsSent++
                onDataSent?.invoke(totalPacketsSent)
            } catch (e: Exception) {
                Log.e(TAG, "发送Vertex数据失败: ${e.message}", e)
            }
        }
    }

    /**
     * 发送自定义JSON数据包（用于控制指令等）
     * @param jsonData 要发送的JSON数据
     */
    fun sendCustomDataPacket(jsonData: JSONObject) {
        // 手动 Log.d(TAG, "📦 CarrotManNetworkClient.sendCustomDataPacket: ${jsonData.toString()}")
        
        if (!isRunning || currentTargetDevice == null) {
            Log.w(TAG, "⚠️ 网络服务未运行或无连接设备，无法发送自定义数据包")
            Log.w(TAG, "⚠️ 状态检查 - 运行状态: $isRunning, 连接设备: $currentTargetDevice")
            return
        }

        networkScope.launch {
            try {
                // 手动 Log.d(TAG, "📡 开始发送自定义数据包到设备: ${currentTargetDevice?.ip}:${currentTargetDevice?.port}")
                sendDataPacket(jsonData)
                totalPacketsSent++
                
                // 手动 Log.i(TAG, "✅ 自定义数据包发送成功 #$totalPacketsSent")
                // 手动 Log.d(TAG, "📦 数据内容: ${jsonData.toString()}")
                
                onDataSent?.invoke(totalPacketsSent)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送自定义数据包失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 启动设备发现服务（基于逆向分析的主动发现机制）
     */
    private fun startDeviceDiscovery() {
        // 手动 Log.i(TAG, "🔍 启动设备发现服务...")
        
        networkScope.launch {
            while (isRunning) {
                try {
                    // 主动设备发现：发送发现广播
                    sendDeviceDiscoveryBroadcast()
                    
                    // 检查已发现设备的活跃状态
                    checkDiscoveredDevices()
                    
                    // 自动选择最佳设备
                    autoSelectBestDevice()
                    
                    delay(5000) // 5秒发现间隔
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 设备发现失败: ${e.message}", e)
                    delay(5000)
                }
            }
        }
    }
    
    // 发送设备发现广播（基于逆向分析的主动发现）
    private suspend fun sendDeviceDiscoveryBroadcast() = withContext(Dispatchers.IO) {
        try {
            val discoveryMessage = JSONObject().apply {
                put("type", "device_discovery")
                put("source", "android_app")
                put("timestamp", System.currentTimeMillis())
                put("version", "1.0")
            }
            
            val dataBytes = discoveryMessage.toString().toByteArray(Charsets.UTF_8)
            val broadcastPacket = DatagramPacket(
                dataBytes,
                dataBytes.size,
                InetAddress.getByName("255.255.255.255"),  // 广播地址
                BROADCAST_PORT
            )
            
            dataSocket?.send(broadcastPacket)
            // 手动 Log.v(TAG, "设备发现广播已发送")
            
        } catch (e: Exception) {
            // 手动 Log.w(TAG, "发送设备发现广播失败: ${e.message}")
        }
    }
    
    // 移除设备发现广播，简化连接逻辑
    
    /**
     * 检查已发现设备的活跃状态
     */
    private fun checkDiscoveredDevices() {
        val currentTime = System.currentTimeMillis()
        val inactiveDevices = mutableListOf<String>()
        
        discoveredDevices.forEach { (deviceId, device) ->
            if (!device.isActive()) {
                inactiveDevices.add(deviceId)
                // 手动 Log.d(TAG, "⏰ 设备已离线: $device")
            }
        }
        
        // 移除离线设备
        inactiveDevices.forEach { deviceId ->
            discoveredDevices.remove(deviceId)
            // 手动 Log.i(TAG, "🗑️ 移除离线设备: $deviceId")
        }
        
        // 如果当前目标设备离线，清除目标
        if (currentTargetDevice != null && !currentTargetDevice!!.isActive()) {
            Log.w(TAG, "⚠️ 当前目标设备已离线，清除目标")
            currentTargetDevice = null
        }
    }
    
    /**
     * 简化设备连接逻辑 - 发现设备后立即连接
     */
    private fun autoSelectBestDevice() {
        if (currentTargetDevice != null && currentTargetDevice!!.isActive()) {
            return // 当前设备仍然活跃
        }
        
        // 简化逻辑：选择第一个活跃设备
        val activeDevice = discoveredDevices.values.firstOrNull { it.isActive() }
        if (activeDevice != null) {
            // 手动 Log.d(TAG, "🎯 连接发现的设备: $activeDevice")
            connectToDevice(activeDevice)
        }
    }
    
    // 移除数据去重检查函数，恢复简单发送逻辑
    
    // 移除增量更新函数，恢复简单发送逻辑
    
}

/* =====================================================
   通用目的地与地理计算工具函数 (顶层)  
   提供目的地合法性校验、更新判定以及两点间距离计算，
   抽离自 MainActivity 以减少其代码体积。
   ===================================================== */

/**
 * 验证目的地坐标与名称的合法性。
 * 支持全球导航，只验证坐标和名称的基本有效性。
 */
fun validateDestination(longitude: Double, latitude: Double, name: String): Boolean {
    val isValidLongitude = longitude in -180.0..180.0    // 全球经度范围
    val isValidLatitude = latitude in -90.0..90.0        // 全球纬度范围
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



