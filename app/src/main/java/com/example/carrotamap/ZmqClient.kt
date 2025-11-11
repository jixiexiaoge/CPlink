package com.example.carrotamap

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZMQ客户端服务类
 * 用于与Comma3设备的ZMQ服务器（端口7710）进行通信
 * 
 * 基于逆向分析实现：
 * - 端口: 7710
 * - 协议: ZMQ REP (Request-Reply)
 * - 支持命令: echo_cmd
 */
class ZmqClient {
    companion object {
        private const val TAG = "ZmqClient"
        private const val DEFAULT_PORT = 7710
        private const val REQUEST_TIMEOUT = 5000L // 5秒超时
    }

    private var context: ZContext? = null
    private var socket: ZMQ.Socket? = null
    private val isConnected = AtomicBoolean(false)
    private var currentDeviceIP: String? = null

    /**
     * 连接到ZMQ服务器
     * @param deviceIP 设备IP地址
     * @param port ZMQ端口，默认7710
     * @return 是否连接成功
     */
    suspend fun connect(deviceIP: String, port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        try {
            // 如果已连接到同一设备，直接返回
            if (isConnected.get() && currentDeviceIP == deviceIP) {
                Log.d(TAG, "✅ 已连接到设备: $deviceIP:$port")
                return@withContext true
            }

            // 断开旧连接
            disconnect()

            // 创建新的ZMQ上下文和Socket
            context = ZContext()
            socket = context?.createSocket(SocketType.REQ)
            
            // 设置超时
            socket?.setReceiveTimeOut(REQUEST_TIMEOUT.toInt())
            
            // 连接到服务器
            val address = "tcp://$deviceIP:$port"
            socket?.connect(address)
            
            currentDeviceIP = deviceIP
            isConnected.set(true)
            
            Log.i(TAG, "✅ ZMQ连接成功: $address")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ ZMQ连接失败: ${e.message}", e)
            disconnect()
            false
        }
    }

    /**
     * 断开ZMQ连接
     */
    fun disconnect() {
        try {
            socket?.close()
            context?.close()
            socket = null
            context = null
            isConnected.set(false)
            currentDeviceIP = null
            Log.d(TAG, "🔌 ZMQ连接已断开")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 断开连接时出错: ${e.message}", e)
        }
    }

    /**
     * 执行Shell命令
     * @param command Shell命令字符串
     * @return 命令执行结果，包含exitStatus、result、error
     */
    suspend fun executeCommand(command: String): ZmqCommandResult = withContext(Dispatchers.IO) {
        if (!isConnected.get() || socket == null) {
            return@withContext ZmqCommandResult(
                success = false,
                exitStatus = -1,
                result = "",
                error = "未连接到设备，请先连接"
            )
        }

        try {
            // 构建请求JSON
            val request = JSONObject().apply {
                put("echo_cmd", command)
            }

            // 发送请求
            val requestBytes = request.toString().toByteArray(Charsets.UTF_8)
            socket?.send(requestBytes, 0)

            // 接收响应
            val responseBytes = socket?.recv(0)
            if (responseBytes == null || responseBytes.isEmpty()) {
                return@withContext ZmqCommandResult(
                    success = false,
                    exitStatus = -1,
                    result = "",
                    error = "未收到响应或响应超时"
                )
            }

            // 解析响应
            val responseStr = String(responseBytes, Charsets.UTF_8)
            val response = JSONObject(responseStr)

            ZmqCommandResult(
                success = true,
                exitStatus = response.optInt("exitStatus", -1),
                result = response.optString("result", ""),
                error = response.optString("error", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 执行命令失败: ${e.message}", e)
            ZmqCommandResult(
                success = false,
                exitStatus = -1,
                result = "",
                error = "执行命令时出错: ${e.message}"
            )
        }
    }

    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean = isConnected.get()

    /**
     * 获取当前连接的设备IP
     */
    fun getCurrentDeviceIP(): String? = currentDeviceIP
}

/**
 * ZMQ命令执行结果数据类
 */
data class ZmqCommandResult(
    val success: Boolean,
    val exitStatus: Int,
    val result: String,
    val error: String
) {
    /**
     * 获取格式化的输出文本
     */
    fun getFormattedOutput(): String {
        val output = StringBuilder()
        if (result.isNotEmpty()) {
            output.append("输出:\n$result")
        }
        if (error.isNotEmpty()) {
            if (output.isNotEmpty()) output.append("\n\n")
            output.append("错误:\n$error")
        }
        if (output.isEmpty()) {
            output.append("无输出")
        }
        return output.toString()
    }
}
