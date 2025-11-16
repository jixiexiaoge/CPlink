package com.example.carrotamap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * 小鸽数据接收器
 * 监听7701端口UDP广播，解析数据包，存储到内存，自动清理过期数据
 */
class XiaogeDataReceiver(
    private val context: Context,
    private val onDataReceived: (XiaogeVehicleData?) -> Unit
) {
    companion object {
        private const val TAG = "XiaogeDataReceiver"
        private const val LISTEN_PORT = 7701
        private const val MAX_PACKET_SIZE = 4096
        private const val MIN_DATA_LENGTH = 20 // 最小数据长度（至少需要包含基本 JSON 结构）
        private const val DATA_TIMEOUT_MS = 15000L // 15秒超时清理（增加容错时间，应对网络波动和Python端重启）
        private const val CLEANUP_INTERVAL_MS = 1000L // 1秒检查一次
        private const val LOG_INTERVAL = 100L // 每100个数据包打印一次日志
    }

    private var isRunning = false
    private var listenSocket: DatagramSocket? = null
    private var listenJob: Job? = null
    private var cleanupJob: Job? = null
    private var networkScope: CoroutineScope? = null  // 优化：改为可空类型，支持重新创建
    
    private var lastDataTime: Long = 0

    /**
     * 启动数据接收服务
     * 优化：每次启动时重新创建 networkScope，支持多次启动/停止
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "⚠️ 数据接收服务已在运行")
            return
        }

        Log.i(TAG, "🚀 启动小鸽数据接收服务 - 端口: $LISTEN_PORT")
        isRunning = true

        try {
            // 优化：重新创建 networkScope，支持多次启动/停止
            networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            initializeSocket()
            startListener()
            startCleanupTask()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动数据接收服务失败: ${e.message}", e)
            isRunning = false
            networkScope?.cancel()
            networkScope = null
        }
    }

    /**
     * 停止数据接收服务
     * 优化：取消 networkScope 并置空，支持重新启动
     */
    fun stop() {
        if (!isRunning) {
            return
        }

        Log.i(TAG, "🛑 停止小鸽数据接收服务")
        isRunning = false

        listenJob?.cancel()
        cleanupJob?.cancel()
        listenSocket?.close()
        listenSocket = null
        networkScope?.cancel()  // 优化：安全取消
        networkScope = null  // 优化：置空，支持重新创建

        lastDataTime = 0
        onDataReceived(null)
    }

    /**
     * 初始化UDP Socket
     * 优化：超时时间从 500ms 增加到 1000ms，更稳定，减少网络波动时的频繁超时
     */
    private fun initializeSocket() {
        try {
            listenSocket = DatagramSocket(LISTEN_PORT).apply {
                soTimeout = 1000 // 优化：1秒超时，更稳定，减少网络波动时的频繁超时
                reuseAddress = true
                broadcast = true
            }
            Log.i(TAG, "✅ Socket初始化成功 - 监听端口: $LISTEN_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Socket初始化失败: ${e.message}", e)
            listenSocket?.close()
            listenSocket = null
            throw e
        }
    }

    /**
     * 启动监听任务
     */
    private fun startListener() {
        listenJob = networkScope?.launch {
            Log.i(TAG, "✅ 启动数据监听任务")
            val buffer = ByteArray(MAX_PACKET_SIZE)
            val packet = DatagramPacket(buffer, buffer.size)

            var packetCount = 0L
            var successCount = 0L
            var failCount = 0L
            
            while (isRunning) {
                try {
                    listenSocket?.receive(packet)
                    packetCount++
                    
                    // 性能优化：直接传递 packet.data, offset, length，避免不必要的数组复制
                    // 在 20Hz 频率下，每次复制 1000+ 字节会产生不必要的内存分配和复制开销
                    val data = parsePacket(packet.data, packet.offset, packet.length)
                    if (data != null) {
                        // ✅ 只在解析成功时更新 lastDataTime
                        successCount++
                        lastDataTime = System.currentTimeMillis()
                        onDataReceived(data)
                        // 降低日志频率：每50个数据包或每5秒打印一次
                        if (successCount % 50 == 0L || successCount == 1L) {
                            Log.d(TAG, "✅ 解析成功 #$successCount: sequence=${data.sequence}, size=${packet.length} bytes")
                        }
                    } else {
                        // ❌ 解析失败时不更新 lastDataTime，让超时机制正常工作
                        failCount++
                        // 解析失败时总是记录日志
                        Log.w(TAG, "❌ 解析失败 #$failCount: size=${packet.length} bytes，请查看上面的错误日志")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // 超时是正常的，继续循环（不记录日志，避免刷屏）
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.w(TAG, "⚠️ 接收数据异常: ${e.message}", e)
                        delay(100) // 短暂延迟后重试
                    }
                }
            }
            Log.i(TAG, "数据监听任务已停止 - 总计: $packetCount, 成功: $successCount, 失败: $failCount")
        }
    }

    /**
     * 启动自动清理任务
     */
    private fun startCleanupTask() {
        cleanupJob = networkScope?.launch {
            while (isRunning) {
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
     * 格式: [CRC32校验(4字节)][数据长度(4字节)][JSON数据]
     * 
     * 性能优化：接受 offset 和 length 参数，避免不必要的数组复制
     * 在 20Hz 频率下，每次复制 1000+ 字节会产生不必要的内存分配和复制开销
     * 
     * @param packetBytes 数据包字节数组
     * @param offset 数据包在数组中的起始偏移量
     * @param length 数据包长度
     * @return 解析后的车辆数据，如果解析失败则返回 null
     */
    private fun parsePacket(packetBytes: ByteArray, offset: Int, length: Int): XiaogeVehicleData? {
        if (length < 8) {
            Log.w(TAG, "数据包太小: $length bytes (需要至少8字节)")
            return null
        }

        try {
            // 优化：使用 offset 和 length 创建 ByteBuffer，避免数组复制
            val buffer = ByteBuffer.wrap(packetBytes, offset, length).order(ByteOrder.BIG_ENDIAN)
            
            // 读取CRC32校验和
            val receivedChecksum = buffer.int.toLong() and 0xFFFFFFFFL
            
            // 读取数据长度
            val dataLength = buffer.int
            
            // 数据包大小检查
            if (dataLength < MIN_DATA_LENGTH || dataLength > MAX_PACKET_SIZE - 8) {
                Log.w(TAG, "无效的数据长度: $dataLength (有效范围: $MIN_DATA_LENGTH - ${MAX_PACKET_SIZE - 8}), 数据包总大小: $length")
                return null
            }

            // 检查剩余数据是否足够
            if (buffer.remaining() < dataLength) {
                Log.w(TAG, "数据包不完整: 需要 $dataLength 字节，但只有 ${buffer.remaining()} 字节，数据包总大小: $length")
                return null
            }

            // 读取JSON数据
            val jsonBytes = ByteArray(dataLength)
            buffer.get(jsonBytes)
            
            // 验证CRC32
            val crc32 = CRC32()
            crc32.update(jsonBytes)
            val calculatedChecksum = crc32.value and 0xFFFFFFFFL
            
            if (receivedChecksum != calculatedChecksum) {
                Log.w(TAG, "CRC32校验失败: 接收=0x${receivedChecksum.toString(16)}, 计算=0x${calculatedChecksum.toString(16)}, 数据长度=$dataLength")
                return null
            }

            // 解析JSON
            val jsonString = String(jsonBytes, Charsets.UTF_8)
            val json = JSONObject(jsonString)
            
            // Python端已移除心跳包，直接解析数据
            return parseJsonData(json)
        } catch (e: Exception) {
            Log.w(TAG, "解析数据包失败: ${e.message}, 数据包大小: $length", e)
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
            
            return XiaogeVehicleData(
                sequence = sequence,
                timestamp = timestamp,
                receiveTime = System.currentTimeMillis(), // Android端接收时间（毫秒）
                carState = parseCarState(dataObj.optJSONObject("carState")),
                modelV2 = parseModelV2(dataObj.optJSONObject("modelV2")),
                radarState = parseRadarState(dataObj.optJSONObject("radarState")),
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
            brakePressed = json.optBoolean("brakePressed", false),
            leftLatDist = json.optDouble("leftLatDist", 0.0).toFloat(),
            rightLatDist = json.optDouble("rightLatDist", 0.0).toFloat(),
            leftLaneLine = json.optInt("leftLaneLine", 0),
            rightLaneLine = json.optInt("rightLaneLine", 0),
            leftBlindspot = json.optBoolean("leftBlindspot", false),
            rightBlindspot = json.optBoolean("rightBlindspot", false),
            standstill = json.optBoolean("standstill", false)
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
        val lead1Obj = json.optJSONObject("lead1")
        val leadLeftObj = json.optJSONObject("leadLeft")
        val leadRightObj = json.optJSONObject("leadRight")
        val cutinObj = json.optJSONObject("cutin")
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
            lead1 = parseLeadData(lead1Obj),  // 第二前车
            leadLeft = parseSideLeadDataExtended(leadLeftObj),  // 左侧车辆（纯视觉方案，已修复）
            leadRight = parseSideLeadDataExtended(leadRightObj), // 右侧车辆（纯视觉方案，已修复）
            cutin = parseCutinData(cutinObj),  // Cut-in 检测数据（已修复）
            modelVEgo = if (json.has("modelVEgo")) json.optDouble("modelVEgo", 0.0).toFloat() else null, // 修复：优先使用 carState.vEgo
            laneWidth = if (json.has("laneWidth")) json.optDouble("laneWidth", 0.0).toFloat() else null, // 修复：使用插值方法计算
            laneLineProbs = laneLineProbs,  // [左车道线置信度, 右车道线置信度]
            meta = parseMetaData(metaObj),  // 车道宽度和变道状态
            curvature = parseCurvatureData(curvatureObj)  // 曲率信息（用于判断弯道）
        )
    }

    private fun parseLeadData(json: JSONObject?): LeadData? {
        if (json == null) return null
        // ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
        // Python 端修复：优先使用 carState.vEgo 计算相对速度，确保数据准确性
        // 注意：lead0 和 lead1 都包含 a 字段（加速度）
        // 使用 optDouble 安全解析，如果字段不存在则返回默认值 0.0
        return LeadData(
            x = json.optDouble("x", 0.0).toFloat(),  // 相对于相机的距离 (m)
            dRel = json.optDouble("dRel", json.optDouble("x", 0.0)).toFloat(), // 相对于雷达的距离（已考虑 RADAR_TO_CAMERA 偏移）
            y = json.optDouble("y", 0.0).toFloat(),  // 横向位置（modelV2.leadsV3[i].y）
            yRel = json.optDouble("yRel", -json.optDouble("y", 0.0)).toFloat(), // 相对于相机的横向位置（yRel = -y）
            v = json.optDouble("v", 0.0).toFloat(),  // 速度 (m/s)
            a = json.optDouble("a", 0.0).toFloat(),  // 加速度 (m/s²) - lead0 和 lead1 都包含此字段
            vRel = json.optDouble("vRel", 0.0).toFloat(), // 相对速度 (m/s) - 使用更准确的 carState.vEgo 计算
            dPath = json.optDouble("dPath", 0.0).toFloat(), // 路径偏移（相对于规划路径的横向偏移）- 修复了符号问题
            inLaneProb = json.optDouble("inLaneProb", 1.0).toFloat(), // 车道内概率 - 修复了符号计算问题
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
     * 解析雷达状态数据（向后兼容）
     * 注意：Python 端已改为纯视觉方案，不再发送 radarState
     * 此函数保留用于向后兼容，但数据可能为空
     */
    private fun parseRadarState(json: JSONObject?): RadarStateData? {
        if (json == null) return null
        return RadarStateData(
            leadOne = parseLeadOneData(json.optJSONObject("leadOne")),
            leadLeft = parseSideLeadData(json.optJSONObject("leadLeft")),
            leadRight = parseSideLeadData(json.optJSONObject("leadRight"))
        )
    }

    private fun parseLeadOneData(json: JSONObject?): LeadOneData? {
        if (json == null) return null
        // 只保留 vRel（前车相对速度），其他字段与 modelV2.lead0 重复
        return LeadOneData(
            vRel = json.optDouble("vRel", 0.0).toFloat()
        )
    }

    private fun parseSideLeadData(json: JSONObject?): SideLeadData? {
        if (json == null) return null
        return SideLeadData(
            dRel = json.optDouble("dRel", 0.0).toFloat(),
            vRel = json.optDouble("vRel", 0.0).toFloat(),
            status = json.optBoolean("status", false)
        )
    }

    /**
     * 解析扩展的侧方车辆数据（纯视觉方案）
     * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
     * Python 端修复：
     * - 横向速度计算：使用历史数据中最近两帧的差值（修复了逻辑错误）
     * - 时间滤波：只复制需要滤波的字段，避免复制不必要的字段
     * - 车道内概率符号：修复了 yRel 和 center_y 的计算符号
     */
    private fun parseSideLeadDataExtended(json: JSONObject?): SideLeadDataExtended? {
        if (json == null) return null
        return SideLeadDataExtended(
            x = json.optDouble("x", 0.0).toFloat(),  // 距离 (m) - 相对于相机的距离（已滤波）
            dRel = json.optDouble("dRel", 0.0).toFloat(), // 相对于雷达的距离（使用原始值，不过滤）
            y = json.optDouble("y", 0.0).toFloat(),  // 横向位置（已滤波）
            yRel = json.optDouble("yRel", 0.0).toFloat(), // 相对于相机的横向位置（已滤波）
            v = json.optDouble("v", 0.0).toFloat(),  // 速度 (m/s)（已滤波）
            vRel = json.optDouble("vRel", 0.0).toFloat(), // 相对速度 (m/s)（已滤波，使用更准确的 carState.vEgo）
            yvRel = json.optDouble("yvRel", 0.0).toFloat(), // 横向速度 (m/s) - 修复：使用历史数据计算
            dPath = json.optDouble("dPath", 0.0).toFloat(), // 路径偏移（已滤波，修复了符号问题）
            inLaneProb = json.optDouble("inLaneProb", 0.0).toFloat(), // 车道内概率（当前时刻，修复了符号计算）
            inLaneProbFuture = json.optDouble("inLaneProbFuture", 0.0).toFloat(), // 未来车道内概率（用于 Cut-in 检测）
            prob = json.optDouble("prob", 0.0).toFloat(), // 置信度
            status = json.optBoolean("status", false)  // 是否有车辆
        )
    }

    /**
     * 解析 Cut-in 检测数据
     * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
     * Python 端修复：
     * - 使用 CUTIN_PROB_THRESHOLD 类常量配置检测阈值（可配置化）
     * - 使用 inLaneProbFuture > 0.1 检测可能切入的车辆
     */
    private fun parseCutinData(json: JSONObject?): CutinData? {
        if (json == null) return null
        return CutinData(
            x = json.optDouble("x", 0.0).toFloat(),  // 距离 (m)
            dRel = json.optDouble("dRel", 0.0).toFloat(), // 相对于雷达的距离
            v = json.optDouble("v", 0.0).toFloat(),  // 速度 (m/s)
            y = json.optDouble("y", 0.0).toFloat(),  // 横向位置
            vRel = json.optDouble("vRel", 0.0).toFloat(), // 相对速度 (m/s) - 使用更准确的 carState.vEgo
            dPath = json.optDouble("dPath", 0.0).toFloat(), // 路径偏移（修复了符号问题）
            inLaneProb = json.optDouble("inLaneProb", 0.0).toFloat(), // 车道内概率（当前时刻，修复了符号计算）
            inLaneProbFuture = json.optDouble("inLaneProbFuture", 0.0).toFloat(), // 未来车道内概率（用于 Cut-in 检测）
            prob = json.optDouble("prob", 0.0).toFloat(), // 置信度
            status = json.optBoolean("status", false)  // 是否有切入车辆
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
    val receiveTime: Long = 0L,  // Android端接收时间（毫秒），用于计算数据年龄
    val carState: CarStateData?,
    val modelV2: ModelV2Data?,
    val radarState: RadarStateData?,
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
    val brakePressed: Boolean,    // 刹车状态
    val leftLatDist: Float,       // 到左车道线距离
    val rightLatDist: Float,      // 到右车道线距离
    val leftLaneLine: Int,        // 左车道线类型
    val rightLaneLine: Int,       // 右车道线类型
    val leftBlindspot: Boolean,   // 左盲区
    val rightBlindspot: Boolean,  // 右盲区
    val standstill: Boolean
)

/**
 * 模型数据 (modelV2)
 * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
 * Python 端修复：
 * - modelVEgo: 优先使用 carState.vEgo（来自CAN总线，更准确），如果不可用则使用模型估计
 * - laneWidth: 使用插值方法在指定距离(20米)处计算，而不是使用固定索引
 * - 所有字段都经过验证和优化
 */
data class ModelV2Data(
    val lead0: LeadData?,         // 第一前车（已修复）
    val lead1: LeadData?,         // 第二前车（已修复）
    val leadLeft: SideLeadDataExtended?,  // 左侧车辆（纯视觉方案，已修复）
    val leadRight: SideLeadDataExtended?, // 右侧车辆（纯视觉方案，已修复）
    val cutin: CutinData?,        // Cut-in 检测数据（已修复）
    val modelVEgo: Float?,        // 自车速度 - 修复：优先使用 carState.vEgo
    val laneWidth: Float?,         // 实际车道宽度（从车道线计算，修复：使用插值方法）
    val laneLineProbs: List<Float>, // [左车道线置信度, 右车道线置信度]
    val meta: MetaData?,          // 车道宽度和变道状态
    val curvature: CurvatureData? // 曲率信息（用于判断弯道）
)

/**
 * 前车数据（lead0/lead1）
 * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
 * Python 端修复：
 * - vRel: 使用更准确的 carState.vEgo 计算相对速度（而非模型估计）
 * - dPath: 修复了符号计算问题（yRel - path_y 而非 yRel + path_y）
 * - inLaneProb: 修复了符号计算问题（yRel - center_y 而非 yRel + center_y）
 */
data class LeadData(
    val x: Float,    // 距离 (m) - 相对于相机的距离
    val dRel: Float, // 相对于雷达的距离（已考虑 RADAR_TO_CAMERA 偏移）
    val y: Float,    // 横向位置（modelV2.leadsV3[i].y）
    val yRel: Float, // 相对于相机的横向位置（yRel = -y）
    val v: Float,    // 速度 (m/s)
    val a: Float,    // 加速度 (m/s²)
    val vRel: Float, // 相对速度 (m/s) - 修复：使用更准确的 carState.vEgo
    val dPath: Float, // 路径偏移（相对于规划路径的横向偏移）- 修复了符号
    val inLaneProb: Float, // 车道内概率 - 修复了符号计算
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

data class RadarStateData(
    val leadOne: LeadOneData?,
    val leadLeft: SideLeadData?,
    val leadRight: SideLeadData?
)

data class LeadOneData(
    val vRel: Float     // 前车相对速度（唯一不重复的字段，其他字段与 modelV2.lead0 重复）
)

data class SideLeadData(
    val dRel: Float,
    val vRel: Float,
    val status: Boolean
)

/**
 * 扩展的侧方车辆数据（纯视觉方案）
 * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
 * Python 端修复：
 * - yvRel: 修复了横向速度计算逻辑（使用历史数据中最近两帧的差值，而非当前值与历史值的差值）
 * - 时间滤波：只复制需要滤波的字段（x, v, y, vRel, dPath, yRel），避免复制不必要的字段
 * - dPath: 修复了符号计算问题（yRel - path_y）
 * - inLaneProb: 修复了符号计算问题（yRel - center_y）
 * - 使用类常量配置：SIDE_VEHICLE_MIN_DISTANCE, SIDE_VEHICLE_MAX_DPATH 等参数可配置化
 */
data class SideLeadDataExtended(
    val x: Float,              // 距离 (m) - 相对于相机的距离（已滤波）
    val dRel: Float,           // 相对于雷达的距离（已考虑 RADAR_TO_CAMERA 偏移，使用原始值，不过滤）
    val y: Float,              // 横向位置（已滤波）
    val yRel: Float,            // 相对于相机的横向位置（已滤波）
    val v: Float,              // 速度 (m/s)（已滤波）
    val vRel: Float,           // 相对速度 (m/s)（已滤波，使用更准确的 carState.vEgo）
    val yvRel: Float,          // 横向速度 (m/s) - 修复：使用历史数据计算
    val dPath: Float,          // 路径偏移（相对于规划路径的横向偏移，已滤波，修复了符号）
    val inLaneProb: Float,     // 车道内概率（当前时刻，修复了符号计算）
    val inLaneProbFuture: Float, // 未来车道内概率（用于 Cut-in 检测）
    val prob: Float,           // 置信度
    val status: Boolean        // 是否有车辆
)

/**
 * Cut-in 检测数据
 * ✅ 已更新：与修复后的 Python 端 (xiaoge_data.py) 完全匹配
 * Python 端修复：
 * - 使用 CUTIN_PROB_THRESHOLD 类常量配置检测阈值（可配置化）
 * - 使用 inLaneProbFuture > 0.1 检测可能切入的车辆
 * - vRel: 使用更准确的 carState.vEgo 计算相对速度
 * - dPath: 修复了符号计算问题
 * - inLaneProb: 修复了符号计算问题
 */
data class CutinData(
    val x: Float,              // 距离 (m)
    val dRel: Float,           // 相对于雷达的距离
    val v: Float,              // 速度 (m/s)
    val y: Float,              // 横向位置
    val vRel: Float,           // 相对速度 (m/s) - 使用更准确的 carState.vEgo
    val dPath: Float,          // 路径偏移（修复了符号问题）
    val inLaneProb: Float,     // 车道内概率（当前时刻，修复了符号计算）
    val inLaneProbFuture: Float, // 未来车道内概率（用于 Cut-in 检测）
    val prob: Float,           // 置信度
    val status: Boolean        // 是否有切入车辆
)

data class SystemStateData(
    val enabled: Boolean,
    val active: Boolean
)
