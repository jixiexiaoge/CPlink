package com.example.carrotamap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.DataInputStream
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * 小鸽数据接收器
 * 通过TCP连接到7711端口，接收数据包，解析数据，存储到内存，自动清理过期数据
 * ✅ 已更新：从UDP广播改为TCP连接模式，适配Python端的TCP服务器
 */
class XiaogeDataReceiver(
    private val context: Context,
    private val onDataReceived: (XiaogeVehicleData?) -> Unit,
    private val onDeviceIPDetected: ((String) -> Unit)? = null  // 🆕 设备IP检测回调（用于通知其他模块）
) {
    companion object {
        private const val TAG = "XiaogeDataReceiver"
        private const val TCP_PORT = 7711  // TCP 端口号（已从UDP 7701改为TCP 7711）
        private const val MAX_PACKET_SIZE = 4096
        private const val MIN_DATA_LENGTH = 20 // 最小数据长度（至少需要包含基本 JSON 结构）
        private const val DATA_TIMEOUT_MS = 15000L // 15秒超时清理（增加容错时间，应对网络波动和Python端重启）
        private const val CLEANUP_INTERVAL_MS = 1000L // 1秒检查一次
        private const val RECONNECT_DELAY_MS = 2000L // Socket错误后重连延迟（2秒）
        private const val MAX_RECONNECT_ATTEMPTS = 0 // 最大重连尝试次数（0=无限重试，只要在局域网就持续尝试）
        private const val SOCKET_TIMEOUT_MS = 30000  // Socket读取超时（30秒，给Python端足够时间发送数据或心跳）
        private const val IP_CHECK_INTERVAL_MS = 3000L // 定期检查NetworkManager IP的间隔（3秒）
    }

    private var _isRunning = false
    private var tcpSocket: Socket? = null  // TCP Socket连接
    private var dataInputStream: DataInputStream? = null  // 数据输入流
    private var dataOutputStream: java.io.DataOutputStream? = null // 数据输出流（用于发送心跳）
    private var listenJob: Job? = null
    private var cleanupJob: Job? = null
    private var heartbeatJob: Job? = null // 心跳任务
    private var networkScope: CoroutineScope? = null  // 优化：改为可空类型，支持重新创建
    private var ipCheckJob: Job? = null  // 🆕 IP检查任务
    
    private var lastDataTime: Long = 0
    private var reconnectAttempts = 0  // 重连尝试次数
    private var serverIP: String? = null  // 服务器IP地址（需要从外部获取或通过发现机制获取）
    private var networkManager: NetworkManager? = null  // 🆕 NetworkManager引用（用于自动获取设备IP）
    private var lastDetectedIP: String? = null  // 🆕 上次检测到的IP（用于避免重复触发回调）
    
    /**
     * 检查接收器是否正在运行
     */
    val isRunning: Boolean
        get() = _isRunning

    /**
     * 🆕 设置NetworkManager引用（用于自动获取设备IP）
     * @param networkManager NetworkManager实例
     */
    fun setNetworkManager(networkManager: NetworkManager?) {
        this.networkManager = networkManager
        Log.d(TAG, "🔗 已设置NetworkManager引用: ${if (networkManager != null) "已设置" else "已清除"}")
    }

    /**
     * 启动数据接收服务
     * @param serverIP 服务器IP地址（可选，如果为null则尝试自动发现）
     * 优化：每次启动时重新创建 networkScope，支持多次启动/停止
     */
    fun start(serverIP: String? = null) {
        if (_isRunning) {
            Log.w(TAG, "⚠️ 数据接收服务已在运行")
            return
        }

        // 🆕 如果没有传入serverIP，尝试从NetworkManager获取
        val initialIP = serverIP ?: tryGetDeviceIPFromNetworkManager()
        this.serverIP = initialIP
        
        Log.i(TAG, "🚀 启动小鸽数据接收服务 - TCP端口: $TCP_PORT, 服务器IP: ${initialIP ?: "自动发现（将从NetworkManager获取）"}")
        _isRunning = true

        try {
            // 优化：重新创建 networkScope，支持多次启动/停止
            networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            startListener()
            startCleanupTask()
            startIPCheckTask()  // 🆕 启动IP检查任务
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动数据接收服务失败: ${e.message}", e)
            _isRunning = false
            networkScope?.cancel()
            networkScope = null
        }
    }
    
    /**
     * 🆕 尝试从NetworkManager获取设备IP
     * @return 设备IP地址，如果获取失败则返回null
     */
    private fun tryGetDeviceIPFromNetworkManager(): String? {
        return try {
            val ip = networkManager?.getCurrentDeviceIP()
            if (ip != null && ip.isNotEmpty()) {
                Log.i(TAG, "✅ 从NetworkManager获取到设备IP: $ip")
                ip
            } else {
                Log.d(TAG, "ℹ️ NetworkManager中暂无设备IP，将定期检查")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 从NetworkManager获取设备IP失败: ${e.message}")
            null
        }
    }
    
    /**
     * 🆕 启动IP检查任务
     * 定期从NetworkManager获取设备IP，如果获取到且当前serverIP为空，则自动设置
     */
    private fun startIPCheckTask() {
        ipCheckJob?.cancel()
        ipCheckJob = networkScope?.launch {
            while (_isRunning) {
                try {
                    delay(IP_CHECK_INTERVAL_MS)
                    
                    // 如果当前没有serverIP，尝试从NetworkManager获取
                    if (serverIP.isNullOrEmpty()) {
                        val deviceIP = tryGetDeviceIPFromNetworkManager()
                        if (deviceIP != null && deviceIP.isNotEmpty()) {
                            Log.i(TAG, "🔗 定期检查发现设备IP: $deviceIP，自动设置并触发连接")
                            setServerIP(deviceIP)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ IP检查任务异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 停止数据接收服务
     * 优化：取消 networkScope 并置空，支持重新启动
     */
    fun stop() {
        if (!_isRunning) {
            return
        }

        Log.i(TAG, "🛑 停止小鸽数据接收服务")
        _isRunning = false

        listenJob?.cancel()
        cleanupJob?.cancel()
        heartbeatJob?.cancel() // 停止心跳
        ipCheckJob?.cancel()  // 🆕 停止IP检查任务
        closeSocket()
        networkScope?.cancel()  // 优化：安全取消
        networkScope = null  // 优化：置空，支持重新创建

        lastDataTime = 0
        reconnectAttempts = 0  // 重置重连计数
        lastDetectedIP = null  // 🆕 重置检测到的IP
        onDataReceived(null)
    }
    
    /**
     * 设置服务器IP地址（用于TCP连接）
     * 当检测到设备IP时，可以调用此方法更新服务器IP
     */
    fun setServerIP(ip: String) {
        if (ip.isEmpty()) {
            Log.w(TAG, "⚠️ 尝试设置空的服务器IP，忽略")
            return
        }
        
        if (serverIP != ip) {
            Log.i(TAG, "📍 更新服务器IP: ${serverIP ?: "null"} -> $ip")
            serverIP = ip
            // 如果正在运行，关闭旧连接（startListener会自动重连）
            if (_isRunning) {
                closeSocket()
            }
        } else {
            Log.d(TAG, "ℹ️ 服务器IP未变化: $ip")
        }
    }

    /**
     * 连接到TCP服务器
     * @return true 如果连接成功，false 如果失败
     */
    private fun connectToServer(): Boolean {
        val ip = serverIP
        if (ip.isNullOrEmpty()) {
            Log.w(TAG, "⚠️ 服务器IP未设置，无法连接")
            return false
        }
        
        return try {
            closeSocket()  // 先关闭旧连接
            
            Log.i(TAG, "🔌 正在连接到TCP服务器: $ip:$TCP_PORT")
            tcpSocket = Socket(ip, TCP_PORT).apply {
                soTimeout = SOCKET_TIMEOUT_MS
                tcpNoDelay = true  // 禁用Nagle算法，降低延迟
            }
            dataInputStream = DataInputStream(tcpSocket!!.getInputStream())
            dataOutputStream = java.io.DataOutputStream(tcpSocket!!.getOutputStream())
            
            // 🆕 优化：只在IP变化时才通知回调，避免重复触发
            if (ip != lastDetectedIP) {
                lastDetectedIP = ip
                onDeviceIPDetected?.invoke(ip)
                Log.d(TAG, "🔗 连接时检测到新的设备IP: $ip")
            }
            
            Log.i(TAG, "✅ TCP连接成功 - 服务器: $ip:$TCP_PORT")
            
            // 连接成功后启动心跳任务
            startHeartbeatTask()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ TCP连接失败: ${e.message}", e)
            closeSocket()
            false
        }
    }
    
    /**
     * 关闭Socket连接
     */
    private fun closeSocket() {
        heartbeatJob?.cancel()
        
        try {
            dataOutputStream?.close()
            dataOutputStream = null
        } catch (e: Exception) {
            // ignore
        }
        
        try {
            dataInputStream?.close()
            dataInputStream = null
        } catch (e: Exception) {
            Log.w(TAG, "关闭输入流时出错: ${e.message}")
        }
        
        try {
            tcpSocket?.close()
            tcpSocket = null
        } catch (e: Exception) {
            Log.w(TAG, "关闭Socket时出错: ${e.message}")
        }
    }
    
    /**
     * 启动心跳任务
     * 每5秒发送一次心跳包 (CMD 2)
     */
    private fun startHeartbeatTask() {
        heartbeatJob?.cancel()
        heartbeatJob = networkScope?.launch {
            while (_isRunning && tcpSocket?.isConnected == true) {
                try {
                    delay(5000) // 每5秒发送一次
                    
                    val outputStream = dataOutputStream
                    if (outputStream != null) {
                        // 发送心跳包：4字节 CMD 2
                        // 注意：这里使用 writeInt (big-endian)
                        outputStream.writeInt(2)
                        outputStream.flush()
                        // Log.v(TAG, "💓 发送心跳包")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "发送心跳失败: ${e.message}")
                    // 发送失败通常意味着连接断开，会在 listenJob 中处理
                    break
                }
            }
        }
    }
    
    /**
     * 获取Android设备的IP地址（用于调试）
     */
    private fun getDeviceIPAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress ?: "未知"
                        }
                    }
                }
            }
            "未获取"
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取设备IP地址失败: ${e.message}")
            "获取失败"
        }
    }

    /**
     * 启动监听任务
     * 增强：添加自动重连机制，确保只要在局域网就能自动连接
     */
    private fun startListener() {
        listenJob = networkScope?.launch {
            Log.i(TAG, "✅ 启动TCP数据接收任务")
            
            var packetCount = 0L
            var successCount = 0L
            var failCount = 0L
            var heartbeatCount = 0L  // 心跳包计数
            
            while (_isRunning) {
                try {
                    // 检查 socket 是否已连接
                    val socket = tcpSocket
                    val inputStream = dataInputStream
                    
                    if (socket == null || socket.isClosed || inputStream == null) {
                        Log.w(TAG, "⚠️ TCP连接已断开，尝试重新连接...")
                        if (connectToServer()) {
                            reconnectAttempts = 0  // 重置重连计数
                            Log.i(TAG, "✅ TCP重新连接成功，继续接收数据")
                            continue
                        } else {
                            // 重连失败，等待后重试
                            reconnectAttempts++
                            if (MAX_RECONNECT_ATTEMPTS == 0 || reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                                Log.w(TAG, "⚠️ TCP重连失败，${RECONNECT_DELAY_MS}ms后重试 (尝试 $reconnectAttempts/${if (MAX_RECONNECT_ATTEMPTS == 0) "∞" else MAX_RECONNECT_ATTEMPTS})")
                                delay(RECONNECT_DELAY_MS)
                            } else {
                                Log.e(TAG, "❌ 达到最大重连次数，停止重连")
                                break
                            }
                            continue
                        }
                    }
                    
                    // TCP数据包格式：先读取4字节长度
                    val packetSize = try {
                        inputStream.readInt()  // 读取数据包长度（网络字节序，big-endian）
                    } catch (e: IOException) {
                        if (_isRunning) {
                            Log.w(TAG, "⚠️ 读取数据包长度失败: ${e.message}")
                            closeSocket()
                        }
                        continue
                    }
                    
                    packetCount++
                    reconnectAttempts = 0  // 成功接收数据，重置重连计数
                    
                    // 处理心跳包（长度为0）
                    if (packetSize == 0) {
                        heartbeatCount++
                        if (heartbeatCount % 100 == 0L) {
                            Log.d(TAG, "💓 收到心跳包 #$heartbeatCount")
                        }
                        continue  // 跳过心跳包，继续接收下一个数据包
                    }
                    
                    // 验证数据包大小
                    if (packetSize < 8 || packetSize > MAX_PACKET_SIZE) {
                        Log.w(TAG, "⚠️ 无效的数据包大小: $packetSize bytes (有效范围: 8 - $MAX_PACKET_SIZE)")
                        failCount++
                        continue
                    }
                    
                    // 读取完整数据包
                    val packetBytes = ByteArray(packetSize)
                    var bytesRead = 0
                    while (bytesRead < packetSize) {
                        val read = inputStream.read(packetBytes, bytesRead, packetSize - bytesRead)
                        if (read == -1) {
                            throw IOException("连接已关闭")
                        }
                        bytesRead += read
                    }
                    
                    // 首次收到数据包时详细记录
                    if (successCount == 0L && packetCount == 1L) {
                        Log.i(TAG, "🎉 首次收到TCP数据包！")
                        Log.i(TAG, "   📍 服务器IP: ${serverIP}")
                        Log.i(TAG, "   📦 数据包大小: $packetSize bytes")
                    }
                    
                    // 解析数据包
                    val data = parsePacket(packetBytes)
                    if (data != null) {
                        // ✅ 只在解析成功时更新 lastDataTime
                        successCount++
                        lastDataTime = System.currentTimeMillis()
                        onDataReceived(data)
                        // 降低日志频率：每50个数据包或首次打印一次
                        if (successCount % 50 == 0L || successCount == 1L) {
                            Log.i(TAG, "✅ 解析成功 #$successCount: sequence=${data.sequence}, size=$packetSize bytes, serverIP=${serverIP}, receiveTime=${data.receiveTime}")
                        }
                    } else {
                        // ❌ 解析失败时不更新 lastDataTime，让超时机制正常工作
                        failCount++
                        // 解析失败时总是记录日志（前10次详细记录，之后降低频率）
                        if (failCount <= 10 || failCount % 50 == 0L) {
                            Log.w(TAG, "❌ 解析失败 #$failCount: size=$packetSize bytes, serverIP=${serverIP}，请查看上面的错误日志")
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // 超时处理：如果连接正常，继续等待；如果连接异常，重连
                    val socket = tcpSocket
                    if (socket != null && socket.isConnected && !socket.isClosed) {
                        // 连接正常，只是没有数据，继续等待（Python端可能没有数据或openpilot未启动）
                        Log.d(TAG, "⏱️ Socket读取超时（连接正常，可能Python端暂无数据），继续等待...")
                        // 不关闭连接，继续循环
                    } else {
                        // 连接异常，关闭并重连
                        Log.w(TAG, "⏱️ Socket读取超时且连接异常，尝试重连...")
                        closeSocket()
                    }
                } catch (e: SocketException) {
                    // Socket 错误，尝试重新连接
                    if (_isRunning) {
                        Log.w(TAG, "⚠️ Socket错误: ${e.message}，尝试重新连接...")
                        closeSocket()
                        delay(RECONNECT_DELAY_MS)
                    }
                } catch (e: IOException) {
                    // IO错误，通常是连接断开
                    if (_isRunning) {
                        Log.w(TAG, "⚠️ IO错误: ${e.message}，连接可能已断开")
                        closeSocket()
                        delay(RECONNECT_DELAY_MS)
                    }
                } catch (e: Exception) {
                    if (_isRunning) {
                        Log.w(TAG, "⚠️ 接收数据异常: ${e.message}", e)
                        delay(100) // 短暂延迟后重试
                    }
                }
            }
            Log.i(TAG, "TCP数据接收任务已停止 - 总计: $packetCount, 成功: $successCount, 失败: $failCount, 心跳: $heartbeatCount")
        }
    }

    /**
     * 启动自动清理任务
     */
    private fun startCleanupTask() {
        cleanupJob = networkScope?.launch {
            while (_isRunning) {
                delay(CLEANUP_INTERVAL_MS)
                
                val now = System.currentTimeMillis()
                if (lastDataTime > 0 && (now - lastDataTime) > DATA_TIMEOUT_MS) {
                    Log.w(TAG, "🧹 数据超时，清理数据（${now - lastDataTime}ms未更新）")
                    lastDataTime = 0
                    onDataReceived(null)
                }
            }
        }
    }

    /**
     * 解析数据包
     * TCP数据包格式: [4字节长度][JSON数据]
     * 注意：TCP外层已经读取了长度，这里接收的是完整的JSON数据
     * 
     * @param packetBytes JSON数据字节数组
     * @return 解析后的车辆数据，如果解析失败则返回 null
     */
    private fun parsePacket(packetBytes: ByteArray): XiaogeVehicleData? {
        if (packetBytes.isEmpty()) {
            return null
        }

        try {
            // 解析JSON
            val jsonString = String(packetBytes, Charsets.UTF_8)
            val json = JSONObject(jsonString)
            
            return parseJsonData(json)
        } catch (e: Exception) {
            Log.w(TAG, "解析数据包失败: ${e.message}, 数据包大小: ${packetBytes.size}", e)
            return null
        }
    }

    /**
     * 解析JSON数据
     */
    private fun parseJsonData(json: JSONObject): XiaogeVehicleData? {
        try {
            val dataObj = json.optJSONObject("data")
            if (dataObj == null) {
                Log.w(TAG, "JSON中缺少 'data' 字段, sequence=${json.optLong("sequence", -1)}")
                return null
            }
            
            val sequence = json.optLong("sequence", 0)
            val timestamp = json.optDouble("timestamp", 0.0)
            val ip = json.optString("ip", "") // 解析设备IP，如果不存在则返回空字符串
            
            // 🆕 优化：只在IP变化时才触发回调，避免频繁触发
            val ipValue = if (ip.isNotEmpty()) ip else null
            if (ipValue != null && ipValue != lastDetectedIP) {
                lastDetectedIP = ipValue
                onDeviceIPDetected?.invoke(ipValue)
                Log.d(TAG, "🔗 检测到新的设备IP: $ipValue（首次或IP变化）")
            }
            
            return XiaogeVehicleData(
                sequence = sequence,
                timestamp = timestamp,
                ip = ipValue,
                receiveTime = System.currentTimeMillis(), // Android端接收时间（毫秒）
                carState = parseCarState(dataObj.optJSONObject("carState")),
                modelV2 = parseModelV2(dataObj.optJSONObject("modelV2")),
                systemState = parseSystemState(dataObj.optJSONObject("systemState")),
                overtakeStatus = parseOvertakeStatus(dataObj.optJSONObject("overtakeStatus"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析JSON数据失败: ${e.message}", e)
            return null
        }
    }

    private fun parseCarState(json: JSONObject?): CarStateData? {
        if (json == null) return null
        return CarStateData(
            vEgo = json.optDouble("vEgo", 0.0).toFloat(),
            steeringAngleDeg = json.optDouble("steeringAngleDeg", 0.0).toFloat(),
            leftLatDist = json.optDouble("leftLatDist", 0.0).toFloat(),
            leftBlindspot = json.optBoolean("leftBlindspot", false),
            rightBlindspot = json.optBoolean("rightBlindspot", false)
        )
    }

    /**
     * 解析模型数据 (modelV2)
     * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
     * Python 端修复：
     * - modelVEgo: 优先使用 carState.vEgo（来自CAN总线，更准确）
     * - laneWidth: 使用插值方法在指定距离处计算，而不是使用固定索引
     * - 所有字段都经过验证和优化
     */
    private fun parseModelV2(json: JSONObject?): ModelV2Data? {
        if (json == null) return null
        
        val lead0Obj = json.optJSONObject("lead0")
        val leadLeftObj = json.optJSONObject("leadLeft")
        val leadRightObj = json.optJSONObject("leadRight")
        val metaObj = json.optJSONObject("meta")
        val curvatureObj = json.optJSONObject("curvature")
        val laneLineProbsArray = json.optJSONArray("laneLineProbs")
        
        // 解析车道线置信度数组 [左车道线置信度, 右车道线置信度]
        val laneLineProbs = mutableListOf<Float>()
        if (laneLineProbsArray != null) {
            for (i in 0 until laneLineProbsArray.length()) {
                laneLineProbs.add(laneLineProbsArray.optDouble(i, 0.0).toFloat())
            }
        }
        
        return ModelV2Data(
            lead0 = parseLeadData(lead0Obj),  // 第一前车
            leadLeft = parseSideLeadDataExtended(leadLeftObj),  // 左侧车辆（纯视觉方案）
            leadRight = parseSideLeadDataExtended(leadRightObj), // 右侧车辆（纯视觉方案）
            laneLineProbs = laneLineProbs,  // [左车道线置信度, 右车道线置信度]
            meta = parseMetaData(metaObj),  // 车道宽度和变道状态
            curvature = parseCurvatureData(curvatureObj)  // 曲率信息（用于判断弯道）
        )
    }

    private fun parseLeadData(json: JSONObject?): LeadData? {
        if (json == null) return null
        // 简化版：只保留超车决策必需的字段
        return LeadData(
            x = json.optDouble("x", 0.0).toFloat(),  // 相对于相机的距离 (m)
            y = json.optDouble("y", 0.0).toFloat(),  // 横向位置（用于返回原车道判断）
            v = json.optDouble("v", 0.0).toFloat(),  // 速度 (m/s)
            prob = json.optDouble("prob", 0.0).toFloat()  // 置信度
        )
    }

    private fun parseMetaData(json: JSONObject?): MetaData? {
        if (json == null) return null
        return MetaData(
            laneWidthLeft = json.optDouble("laneWidthLeft", 0.0).toFloat(),
            laneWidthRight = json.optDouble("laneWidthRight", 0.0).toFloat(),
            laneChangeState = json.optInt("laneChangeState", 0),
            laneChangeDirection = json.optInt("laneChangeDirection", 0)
        )
    }

    /**
     * 解析曲率数据
     * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
     * Python 端修复：改进空列表检查逻辑，使代码更清晰
     */
    private fun parseCurvatureData(json: JSONObject?): CurvatureData? {
        if (json == null) return null
        return CurvatureData(
            maxOrientationRate = json.optDouble("maxOrientationRate", 0.0).toFloat()  // 最大方向变化率 (rad/s)，方向可从符号推导（>0=左转，<0=右转）
        )
    }


    /**
     * 解析扩展的侧方车辆数据（纯视觉方案）
     * 简化版：只保留超车决策必需的字段
     */
    private fun parseSideLeadDataExtended(json: JSONObject?): SideLeadDataExtended? {
        if (json == null) return null
        return SideLeadDataExtended(
            dRel = json.optDouble("dRel", 0.0).toFloat(), // 相对于雷达的距离
            vRel = json.optDouble("vRel", 0.0).toFloat(), // 相对速度 (m/s)
            status = json.optBoolean("status", false)  // 是否有车辆
        )
    }


    private fun parseSystemState(json: JSONObject?): SystemStateData? {
        if (json == null) return null
        return SystemStateData(
            enabled = json.optBoolean("enabled", false),
            active = json.optBoolean("active", false)
        )
    }

    /**
     * 🆕 解析超车状态数据
     * 从 JSON 中解析超车状态信息，用于在 UI 中显示
     * 注意：此数据由 Android 端的 AutoOvertakeManager 生成，Python 端不发送此数据
     * 如果 Python 端未来发送此数据，此函数可以正确解析
     */
    private fun parseOvertakeStatus(json: JSONObject?): OvertakeStatusData? {
        if (json == null) return null
        
        val lastDirectionStr = json.optString("lastDirection", "")
        val blockingReasonStr = json.optString("blockingReason", "")
        
        return OvertakeStatusData(
            statusText = json.optString("statusText", "监控中"),
            canOvertake = json.optBoolean("canOvertake", false),
            cooldownRemaining = if (json.has("cooldownRemaining")) {
                json.optLong("cooldownRemaining", 0)
            } else {
                null
            },
            lastDirection = lastDirectionStr.takeIf { it.isNotEmpty() },
            blockingReason = blockingReasonStr.takeIf { it.isNotEmpty() }
        )
    }
}

/**
 * 小鸽车辆数据结构
 */
data class XiaogeVehicleData(
    val sequence: Long,
    val timestamp: Double,  // Python端时间戳（秒）
    val ip: String?,        // 设备IP地址
    val receiveTime: Long = 0L,  // Android端接收时间（毫秒），用于计算数据年龄
    val carState: CarStateData?,
    val modelV2: ModelV2Data?,
    val systemState: SystemStateData?,
    val overtakeStatus: OvertakeStatusData? = null  // 超车状态（可选，由 AutoOvertakeManager 更新）
)

/**
 * 超车状态数据
 * 用于在 UI 中显示超车系统的实时状态
 * 注意：此数据需要在 openpilot 端的数据发送器中包含超车状态信息
 */
data class OvertakeStatusData(
    val statusText: String,           // 状态文本描述："监控中"/"可超车"/"冷却中"
    val canOvertake: Boolean,         // 是否可以超车
    val cooldownRemaining: Long?,     // 剩余冷却时间（毫秒），可选
    val lastDirection: String?,       // 上次超车方向（LEFT/RIGHT），可选
    val blockingReason: String? = null // 🆕 阻止超车的原因（可选）
)

data class CarStateData(
    val vEgo: Float,              // 本车速度 (m/s)
    val steeringAngleDeg: Float,  // 方向盘角度
    val leftLatDist: Float,       // 到左车道线距离（返回原车道）
    val leftBlindspot: Boolean,   // 左盲区
    val rightBlindspot: Boolean   // 右盲区
)

/**
 * 模型数据 (modelV2)
 * 简化版：只保留超车决策必需的字段
 */
data class ModelV2Data(
    val lead0: LeadData?,         // 第一前车
    val leadLeft: SideLeadDataExtended?,  // 左侧车辆（纯视觉方案）
    val leadRight: SideLeadDataExtended?, // 右侧车辆（纯视觉方案）
    val laneLineProbs: List<Float>, // [左车道线置信度, 右车道线置信度]
    val meta: MetaData?,          // 车道宽度和变道状态
    val curvature: CurvatureData? // 曲率信息（用于判断弯道）
)

/**
 * 前车数据（lead0）
 * 简化版：只保留超车决策必需的字段
 */
data class LeadData(
    val x: Float,    // 距离 (m) - 相对于相机的距离
    val y: Float,    // 横向位置（用于返回原车道判断）
    val v: Float,    // 速度 (m/s)
    val prob: Float  // 置信度
)

data class MetaData(
    val laneWidthLeft: Float,
    val laneWidthRight: Float,
    val laneChangeState: Int,
    val laneChangeDirection: Int
)

data class CurvatureData(
    val maxOrientationRate: Float  // 曲率 (rad/s)，方向可从符号推导（>0=左转，<0=右转）
)

/**
 * 扩展的侧方车辆数据（纯视觉方案）
 * 简化版：只保留超车决策必需的字段
 */
data class SideLeadDataExtended(
    val dRel: Float,           // 相对于雷达的距离
    val vRel: Float,           // 相对速度 (m/s)
    val status: Boolean        // 是否有车辆
)

data class SystemStateData(
    val enabled: Boolean,
    val active: Boolean
)
