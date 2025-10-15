package com.example.carrotamap

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.ui.theme.CarrotAmapTheme
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONException
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// 高德地图广播数据实体类
data class BroadcastData(
    val keyType: Int,                       // 广播类型键
    val dataType: String,                   // 数据类型描述
    val timestamp: Long,                    // 接收时间戳
    val rawExtras: Map<String, String>,     // 原始额外数据
    val parsedContent: String               // 解析后的内容
)

// Comma3 CarrotMan字段映射数据类 - 完全基于用户提供的Python代码中的字段定义
data class CarrotManFields(
    // 基础参数和计数器
    var nRoadLimitSpeed: Int = 30,              // 道路限速 (km/h)
    var nRoadLimitSpeed_last: Int = 30,         // 上次道路限速 (km/h)
    var nRoadLimitSpeed_counter: Int = 0,       // 限速变化计数器
    var active_carrot: Int = 0,                 // CarrotMan激活状态
    var active_count: Int = 0,                  // 激活计数器
    var active_sdi_count: Int = 0,              // SDI激活计数器
    var active_sdi_count_max: Int = 200,        // SDI最大激活计数 (20秒)
    var active_kisa_count: Int = 0,             // KISA激活计数器
    
    // SDI摄像头信息 (主要)
    var nSdiType: Int = -1,                     // SDI类型
    var nSdiSpeedLimit: Int = 0,                // SDI限速 (km/h)
    var nSdiSection: Int = 0,                   // SDI区间长度 (m)
    var nSdiDist: Int = 0,                      // SDI距离 (m)
    var nSdiBlockType: Int = -1,                // SDI区间类型
    var nSdiBlockSpeed: Int = 0,                // SDI区间限速 (km/h)
    var nSdiBlockDist: Int = 0,                 // SDI区间距离 (m)
    
    // TBT转弯引导信息
    var nTBTDist: Int = 0,                      // 转弯距离 (m)
    var nTBTTurnType: Int = -1,                 // 转弯类型
    var szTBTMainText: String = "",             // 转弯主指令文本
    var szNearDirName: String = "",             // 近方向道路名称
    var szFarDirName: String = "",              // 远方向道路名称
    var nTBTNextRoadWidth: Int = 0,             // 下一条道路宽度 (m)
    var nTBTDistNext: Int = 0,                  // 下一个转弯距离 (m)
    var nTBTTurnTypeNext: Int = -1,             // 下一个转弯类型
    var szTBTMainTextNext: String = "",         // 下一个转弯指令文本
    
    // 目标和路线信息
    var nGoPosDist: Int = 0,                    // 到达目标距离 (m)
    var nGoPosTime: Int = 0,                    // 到达目标时间 (s)
    var szPosRoadName: String = "",             // 当前道路名称
    
    // SDI Plus摄像头信息 (次要)
    var nSdiPlusType: Int = -1,                 // SDI Plus类型
    var nSdiPlusSpeedLimit: Int = 0,            // SDI Plus限速 (km/h)
    var nSdiPlusDist: Int = 0,                  // SDI Plus距离 (m)
    var nSdiPlusBlockType: Int = -1,            // SDI Plus区间类型
    var nSdiPlusBlockSpeed: Int = 0,            // SDI Plus区间限速 (km/h)
    var nSdiPlusBlockDist: Int = 0,             // SDI Plus区间距离 (m)
    
    // 目标位置信息
    var goalPosX: Double = 0.0,                 // 目标X坐标 (经度)
    var goalPosY: Double = 0.0,                 // 目标Y坐标 (纬度)
    var szGoalName: String = "",                // 目标名称
    
    // GPS位置信息
    var vpPosPointLatNavi: Double = 0.0,        // 导航模式纬度
    var vpPosPointLonNavi: Double = 0.0,        // 导航模式经度
    var vpPosPointLat: Double = 0.0,            // 通用纬度
    var vpPosPointLon: Double = 0.0,            // 通用经度
    var roadcate: Int = 8,                      // 道路类别
    
    // 速度和角度信息
    var nPosSpeed: Double = 0.0,                // 当前速度 (km/h)
    var nPosAngle: Double = 0.0,                // 位置角度 (度)
    var nPosAnglePhone: Double = 0.0,           // 手机角度 (度)
    
    // GPS和定位相关
    var diff_angle_count: Int = 0,              // 角度差计数器
    var last_calculate_gps_time: Long = 0,      // 最后GPS计算时间
    var last_update_gps_time: Long = 0,         // 最后GPS更新时间
    var last_update_gps_time_phone: Long = 0,   // 最后手机GPS更新时间
    var last_update_gps_time_navi: Long = 0,    // 最后导航GPS更新时间
    var bearing_offset: Double = 0.0,           // 方位偏移量 (度)
    var bearing_measured: Double = 0.0,         // 测量方位角 (度)
    var bearing: Double = 0.0,                  // 方位角 (度)
    var gps_valid: Boolean = false,             // GPS是否有效
    var gps_accuracy_phone: Double = 0.0,       // 手机GPS精度 (m)
    var gps_accuracy_device: Double = 0.0,      // 设备GPS精度 (m)
    
    // 距离和限速信息
    var totalDistance: Int = 0,                 // 总距离 (m)
    var xSpdLimit: Int = 0,                     // X系列限速 (km/h)
    var xSpdDist: Int = 0,                      // X系列限速距离 (m)
    var xSpdType: Int = -1,                     // X系列限速类型
    
    // 转弯信息 (X系列)
    var xTurnInfo: Int = -1,                    // X系列转弯信息
    var xDistToTurn: Int = 0,                   // X系列转弯距离 (m)
    var xTurnInfoNext: Int = -1,                // X系列下一转弯信息
    var xDistToTurnNext: Int = 0,               // X系列下一转弯距离 (m)
    // 导航类型和修饰符
    var navType: String = "invalid",            // 导航类型
    var navModifier: String = "",               // 导航修饰符
    var navTypeNext: String = "invalid",        // 下一导航类型
    var navModifierNext: String = "",           // 下一导航修饰符
    // CarrotMan命令和索引
    var carrotIndex: Long = 0,                  // CarrotMan索引
    var carrotCmdIndex: Int = 0,                // CarrotMan命令索引
    var carrotCmd: String = "",                 // CarrotMan命令
    var carrotArg: String = "",                 // CarrotMan命令参数
    var carrotCmdIndex_last: Int = 0,           // 上次CarrotMan命令索引
    // 交通灯信息
    var traffic_light_count: Int = -1,          // 红绿灯数量
    var traffic_state: Int = 0,                 // 交通状态
    // 时间相关
    var left_spd_sec: Int = 0,                  // 剩余速度秒数
    var left_tbt_sec: Int = 0,                  // 剩余TBT秒数
    var left_sec: Int = 100,                    // 红绿灯剩余秒数
    var max_left_sec: Int = 100,                // 最大剩余秒数
    var carrot_left_sec: Int = 100,             // CarrotMan剩余秒数
    var sdi_inform: Boolean = false,            // SDI是否已通知
    // ATC和控制相关
    var atc_paused: Boolean = false,            // ATC是否暂停
    var atc_activate_count: Int = 0,            // ATC激活计数
    var gas_override_speed: Int = 0,            // 油门覆盖速度 (km/h)
    var gas_pressed_state: Boolean = false,     // 油门是否按下
    var source_last: String = "none",           // 最后数据源
    // 调试信息
    var debugText: String = "",                 // 调试文本
    // 系统状态 (UI辅助字段)
    var isNavigating: Boolean = false,          // 是否正在导航
    var lastUpdateTime: Long = System.currentTimeMillis(), // 最后更新时间
    var dataQuality: String = "good"            // 数据质量
)

// 高德地图广播静态接收器 - 用于接收高德地图发送的广播，即使应用未启动
class amapAutoStaticReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AmapAutoStaticReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        try {
            val action = intent.action
            Log.d(TAG, "收到静态广播: $action")
            
            if (action == "AUTONAVI_STANDARD_BROADCAST_SEND" || 
                action == "AMAP_BROADCAST_SEND" || 
                action == "AUTONAVI_BROADCAST_SEND" ||
                action == "AMAP_NAVI_ACTION_UPDATE" ||
                action == "AMAP_NAVI_ACTION_TURN" ||
                action == "AMAP_NAVI_ACTION_ROUTE" ||
                action == "AMAP_NAVI_ACTION_LOCATION") {
                // 启动主Activity处理广播
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtras(intent)
                }
                context.startActivity(launchIntent)
                
                // 记录广播数据
                val keyType = intent.getIntExtra("KEY_TYPE", -1)
                Log.i(TAG, "接收到高德地图广播: KEY_TYPE=$keyType")
                
                // 记录所有额外数据
                intent.extras?.let { bundle ->
                    for (key in bundle.keySet()) {
                        val value: String = try {
                            when {
                                bundle.getString(key) != null -> bundle.getString(key) ?: "null"
                                bundle.getInt(key, Int.MIN_VALUE) != Int.MIN_VALUE -> bundle.getInt(key).toString()
                                bundle.getLong(key, Long.MIN_VALUE) != Long.MIN_VALUE -> bundle.getLong(key).toString()
                                bundle.getDouble(key, Double.NaN).let { !it.isNaN() } -> bundle.getDouble(key).toString()
                                bundle.getFloat(key, Float.NaN).let { !it.isNaN() } -> bundle.getFloat(key).toString()
                                bundle.getBoolean(key, false) -> bundle.getBoolean(key).toString()
                                else -> "未知类型"
                            }
                        } catch (e: Exception) {
                            "获取失败: ${e.message}"
                        }
                        Log.v(TAG, "   $key = $value")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理广播失败: ${e.message}", e)
        }
    }
}

// 主Activity - 集成所有功能：UI显示、传感器管理、广播处理、CarrotMan映射、网络通信、地图控制
class MainActivity : ComponentActivity(), SensorEventListener {
    
    companion object {
        private const val TAG = AppConstants.Logging.MAIN_ACTIVITY_TAG
        // 应用所需的权限列表 - 优化后只请求必需的权限
        private val REQUIRED_PERMISSIONS = AppConstants.Permissions.ALL_PERMISSIONS
        // 核心权限 - GPS功能必需的权限
        private val CORE_PERMISSIONS = AppConstants.Permissions.CORE_PERMISSIONS
        // GPS测试权限 - 仅包含位置权限，用于GPS功能测试
        private val GPS_TEST_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        // 高德地图广播Action常量 - 使用统一的常量管理
        const val ACTION_AMAP_SEND = AppConstants.AmapBroadcast.ACTION_AMAP_SEND
        const val ACTION_AMAP_RECV = AppConstants.AmapBroadcast.ACTION_AMAP_RECV
        const val ACTION_AMAP_LEGACY = AppConstants.AmapBroadcast.ACTION_AMAP_LEGACY
        const val ACTION_AUTONAVI = AppConstants.AmapBroadcast.ACTION_AUTONAVI
        // 核心导航广播类型常量 - 使用统一的常量管理
        const val KEY_TYPE_MAP_STATE = AppConstants.AmapBroadcast.Navigation.MAP_STATE
        const val KEY_TYPE_GUIDE_INFO = AppConstants.AmapBroadcast.Navigation.GUIDE_INFO
        const val KEY_TYPE_LOCATION_INFO = AppConstants.AmapBroadcast.Navigation.LOCATION_INFO
        const val KEY_TYPE_TURN_INFO = AppConstants.AmapBroadcast.Navigation.TURN_INFO
        const val KEY_TYPE_NAVIGATION_STATUS = AppConstants.AmapBroadcast.Navigation.NAVIGATION_STATUS
        const val KEY_TYPE_ROUTE_INFO = AppConstants.AmapBroadcast.Navigation.ROUTE_INFO
        
        // 限速和摄像头信息
        const val KEY_TYPE_SPEED_LIMIT = AppConstants.AmapBroadcast.SpeedCamera.SPEED_LIMIT
        const val KEY_TYPE_CAMERA_INFO = AppConstants.AmapBroadcast.SpeedCamera.CAMERA_INFO
        const val KEY_TYPE_CAMERA_INFO_V2 = AppConstants.AmapBroadcast.SpeedCamera.CAMERA_INFO_V2
        const val KEY_TYPE_SPEED_LIMIT_NEW = AppConstants.AmapBroadcast.SpeedCamera.SPEED_LIMIT_NEW
        const val KEY_TYPE_SDI_PLUS_INFO = AppConstants.AmapBroadcast.SpeedCamera.SDI_PLUS_INFO
        
        // 地图和位置信息
        const val KEY_TYPE_FAVORITE_RESULT = AppConstants.AmapBroadcast.MapLocation.FAVORITE_RESULT
        const val KEY_TYPE_ADMIN_AREA = AppConstants.AmapBroadcast.MapLocation.ADMIN_AREA
        const val KEY_TYPE_NAVI_STATUS = AppConstants.AmapBroadcast.MapLocation.NAVI_STATUS
        const val KEY_TYPE_TRAFFIC_INFO = AppConstants.AmapBroadcast.MapLocation.TRAFFIC_INFO
        const val KEY_TYPE_NAVI_SITUATION = AppConstants.AmapBroadcast.MapLocation.NAVI_SITUATION
        const val KEY_TYPE_NEXT_INTERSECTION = AppConstants.AmapBroadcast.MapLocation.NEXT_INTERSECTION
        const val KEY_TYPE_SAPA_INFO = AppConstants.AmapBroadcast.MapLocation.SAPA_INFO
        const val KEY_TYPE_TRAFFIC_LIGHT = AppConstants.AmapBroadcast.MapLocation.TRAFFIC_LIGHT
        const val KEY_TYPE_ROUTE_INFO_QUERY = AppConstants.AmapBroadcast.MapLocation.ROUTE_INFO_QUERY
        
        // ===============================
        // 智能限速相关常量 - 使用统一的常量管理
        // ===============================
        private const val AUTO_NAVI_SPEED_BUMP_SPEED = AppConstants.SmartSpeedControl.SPEED_BUMP_SPEED
        private const val AUTO_TURN_CONTROL_SPEED_TURN = AppConstants.SmartSpeedControl.TURN_CONTROL_SPEED
        private const val AUTO_ROAD_SPEED_LIMIT_OFFSET = AppConstants.SmartSpeedControl.ROAD_SPEED_LIMIT_OFFSET

        // ===============================
        // 导航控制相关常量 - 使用统一的常量管理
        // ===============================
        const val KEY_TYPE_SIMULATE_NAVIGATION = AppConstants.AmapBroadcast.NavigationControl.SIMULATE_NAVIGATION
        const val KEY_TYPE_ROUTE_PLANNING = AppConstants.AmapBroadcast.NavigationControl.ROUTE_PLANNING
        const val KEY_TYPE_START_NAVIGATION = AppConstants.AmapBroadcast.NavigationControl.START_NAVIGATION
        const val KEY_TYPE_STOP_NAVIGATION = AppConstants.AmapBroadcast.NavigationControl.STOP_NAVIGATION
        const val KEY_TYPE_HOME_COMPANY_NAVIGATION = AppConstants.AmapBroadcast.NavigationControl.HOME_COMPANY_NAVIGATION
    }

    // ===============================
    // 属性声明区域 - Properties Declaration
    // ===============================
    
    // ===============================
    // 广播数据存储相关 - Broadcast Data Storage
    // ===============================
    
    /** 存储接收到的广播数据列表 */
    private val broadcastDataList = mutableStateListOf<BroadcastData>()
    
    /** Comma3 CarrotMan字段映射数据 */
    private val carrotManFields = mutableStateOf(CarrotManFields())
    
    /** 广播接收器状态信息 */
    private val receiverStatus = mutableStateOf("等待广播数据...")
    
    /** 总广播接收计数 */
    private val totalBroadcastCount = mutableIntStateOf(0)

    /** 最后更新时间戳 */
    private val lastUpdateTime = mutableLongStateOf(0L)
    
    // 位置和传感器管理器
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    
    // 传感器数据存储
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    
    // 智能数据变化检测
    private var lastSpeedLimit: Int? = null
    private var lastRoadName: String? = null
    private var lastSpeedLimitSendTime: Long = 0L
    private val speedLimitSendInterval = 2000L
    
    // 限速信息数据类 - 用于数据缓存和去重
    private data class SpeedLimitInfo(
        val speedLimit: Int,        // 限速值
        val roadName: String,       // 道路名称
        val sendTime: Long          // 发送时间
    )

    // OpenpPilot状态数据类 - 用于接收7705端口的JSON数据
    data class OpenpilotStatusData(
        val carrot2: String = "",           // OpenpPilot版本信息
        val isOnroad: Boolean = false,      // 是否在道路上行驶
        val carrotRouteActive: Boolean = false, // 导航路线是否激活
        val ip: String = "",                // 设备IP地址
        val port: Int = 0,                  // 通信端口号
        val logCarrot: String = "",         // CarrotMan状态日志
        val vCruiseKph: Float = 0.0f,       // 巡航设定速度(km/h)
        val vEgoKph: Int = 0,               // 当前实际车速(km/h)
        val tbtDist: Int = 0,               // 到下个转弯距离(米)
        val sdiDist: Int = 0,               // 到速度限制点距离(米)
        val active: Boolean = false,        // 自动驾驶控制激活状态
        val xState: Int = 0,                // 纵向控制状态码
        val trafficState: Int = 0,          // 交通灯状态
        val lastUpdateTime: Long = System.currentTimeMillis() // 最后更新时间
    )
    
    // 网络通信相关
    private lateinit var carrotNetworkClient: CarrotManNetworkClient
    private val networkConnectionStatus = mutableStateOf("未连接")
    private val discoveredDevicesList = mutableStateListOf<CarrotManNetworkClient.DeviceInfo>()
    private val networkStatistics = mutableStateOf(mapOf<String, Any>())
    private val autoSendEnabled = mutableStateOf(true)
    private var lastDataSendTime = 0L
    private val dataSendInterval = 200L
    
    // 异步处理相关
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // OpenpPilot状态数据
    private val openpilotStatusData = mutableStateOf(OpenpilotStatusData())
    private val showOpenpilotCard = mutableStateOf(true) // 控制OpenpPilot卡片显示/隐藏

    // 移除车辆数据显示状态，现在使用独立Activity
    
    // GPS位置变化监听器 - 监听GPS位置变化，更新CarrotMan字段中的位置信息
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            runOnUiThread {
                try {
                    val currentTime = System.currentTimeMillis()

                    carrotManFields.value = carrotManFields.value.copy(
                        // 更新手机GPS坐标到vpPosPointLat/Lon字段
                        vpPosPointLat = location.latitude,
                        vpPosPointLon = location.longitude,

                        // 更新GPS相关信息
                        nPosSpeed = if (location.hasSpeed()) location.speed * 3.6 else carrotManFields.value.nPosSpeed, // 转换为km/h
                        nPosAngle = if (location.hasBearing()) location.bearing.toDouble() else carrotManFields.value.nPosAngle,
                        nPosAnglePhone = if (location.hasBearing()) location.bearing.toDouble() else carrotManFields.value.nPosAnglePhone,

                        // GPS精度和状态
                        gps_accuracy_phone = location.accuracy.toDouble(),
                        gps_valid = true,

                        // 时间戳更新
                        last_update_gps_time = location.time,
                        last_update_gps_time_phone = location.time,
                        lastUpdateTime = currentTime
                    )

                    // 🔍 详细GPS数据日志
                    Log.i(TAG, "🌍 GPS位置更新接收:")
                    Log.i(TAG, "  📍 坐标: lat=${String.format("%.6f", location.latitude)}, lon=${String.format("%.6f", location.longitude)}")
                    Log.i(TAG, "  🚀 速度: ${if (location.hasSpeed()) "${String.format("%.1f", location.speed * 3.6)} km/h" else "无速度数据"}")
                    Log.i(TAG, "  🧭 方向: ${if (location.hasBearing()) "${String.format("%.1f", location.bearing)}°" else "无方向数据"}")
                    Log.i(TAG, "  📡 精度: ${location.accuracy}m")
                    Log.i(TAG, "  🔧 提供者: ${location.provider}")
                    Log.i(TAG, "  ⏰ 时间: ${System.currentTimeMillis() - location.time}ms前")

                    // 验证坐标有效性
                    if (location.latitude == 0.0 && location.longitude == 0.0) {
                        Log.w(TAG, "⚠️ 接收到无效GPS坐标 (0,0)，跳过更新")
                        return@runOnUiThread
                    }

                    // 更新后验证
                    Log.i(TAG, "✅ GPS字段更新完成:")
                    Log.i(TAG, "  📍 vpPosPointLat: ${carrotManFields.value.vpPosPointLat} -> ${location.latitude}")
                    Log.i(TAG, "  📍 vpPosPointLon: ${carrotManFields.value.vpPosPointLon} -> ${location.longitude}")
                    Log.i(TAG, "  🔄 gps_valid: ${carrotManFields.value.gps_valid} -> true")

                } catch (e: Exception) {
                    Log.e(TAG, "GPS位置更新失败: ${e.message}", e)
                }
            }
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            val statusText = when(status) {
                android.location.LocationProvider.AVAILABLE -> "可用"
                android.location.LocationProvider.OUT_OF_SERVICE -> "服务外"
                android.location.LocationProvider.TEMPORARILY_UNAVAILABLE -> "暂时不可用"
                else -> "未知($status)"
            }
            Log.i(TAG, "📡 位置提供者状态变化: $provider -> $statusText")
        }

        override fun onProviderEnabled(provider: String) {
            Log.i(TAG, "✅ 位置提供者已启用: $provider")
            checkLocationProviderStatus()
        }

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "⚠️ 位置提供者已禁用: $provider")
            checkLocationProviderStatus()
        }
    }

    // 增强版高德地图广播接收器 - 接收各类导航广播，解析并更新CarrotMan字段映射
    private val enhancedAmapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            
            try {
                val action = intent.action
                Log.d(TAG, "📡 收到广播: $action")
                
                when (action) {
                    ACTION_AMAP_SEND, ACTION_AMAP_LEGACY, ACTION_AUTONAVI -> {
                        handleAmapSendBroadcast(intent)
                    }
                    ACTION_AMAP_RECV -> {
                        Log.v(TAG, "收到发送给高德的广播数据")
                        logAllExtras(intent)
                    }
                    "AMAP_NAVI_ACTION_UPDATE", "AMAP_NAVI_ACTION_TURN",
                    "AMAP_NAVI_ACTION_ROUTE", "AMAP_NAVI_ACTION_LOCATION" -> {
                        handleAlternativeAmapBroadcast(intent)
                    }
                    else -> {
                        Log.v(TAG, "未知广播action: $action")
                        logAllExtras(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理广播数据失败: ${e.message}", e)
            }
        }
    }

    // Activity创建时回调 - 完成应用的初始化工作
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Log.i(TAG, "🚀 MainActivity正在启动...")
        
        initializeSensors()                 // 1. 传感器系统初始化
        setupPermissionsAndLocation()       // 2. 权限管理和位置服务初始化
        initializeNetworkClient()           // 3. 网络客户端初始化
        registerBroadcastReceiver()         // 4. 广播接收器注册
        // 5. UI界面设置
        // ===============================
        setupUserInterface()
        
        // ===============================
        // 6. 处理来自静态接收器的Intent
        // ===============================
        handleIntentFromStaticReceiver(intent)
        
        Log.i(TAG, "✅ MainActivity启动完成")
    }
    
    /**
     * 设置权限和位置服务
     */
    private fun setupPermissionsAndLocation() {
        // 首先尝试简化的GPS权限请求
        setupGpsPermissionsOnly()
    }

    /**
     * 仅设置GPS相关权限 - 简化版本用于测试GPS功能
     */
    private fun setupGpsPermissionsOnly() {
        Log.i(TAG, "🔍 开始GPS权限设置（简化版本）")

        // 注册GPS权限请求回调
        val gpsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            Log.i(TAG, "🔍 GPS权限请求结果:")
            permissions.forEach { (permission, granted) ->
                Log.i(TAG, "  ${if (granted) "✅" else "❌"} $permission: ${if (granted) "已授予" else "被拒绝"}")
            }

            val hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (hasLocationPermission) {
                Log.i(TAG, "✅ GPS权限已获取，启动位置更新")
                startLocationUpdates()
                startGpsStatusMonitoring()
            } else {
                Log.e(TAG, "❌ GPS权限被拒绝，无法启动GPS功能")
                Log.e(TAG, "💡 请在设置中手动授予位置权限")
            }
        }

        // 检查GPS权限状态
        val fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        Log.i(TAG, "📍 当前GPS权限状态:")
        Log.i(TAG, "  ${if (fineLocationGranted) "✅" else "❌"} ACCESS_FINE_LOCATION: ${if (fineLocationGranted) "已授予" else "需要请求"}")
        Log.i(TAG, "  ${if (coarseLocationGranted) "✅" else "❌"} ACCESS_COARSE_LOCATION: ${if (coarseLocationGranted) "已授予" else "需要请求"}")

        if (fineLocationGranted || coarseLocationGranted) {
            Log.i(TAG, "✅ GPS权限检查通过，直接启动位置更新")
            startLocationUpdates()
            startGpsStatusMonitoring()
        } else {
            Log.i(TAG, "⚠️ 需要请求GPS权限")
            gpsPermissionLauncher.launch(GPS_TEST_PERMISSIONS)
        }
    }

    /**
     * 设置完整权限 - 包含所有功能权限
     */
    private fun setupFullPermissions() {
        // 注册权限请求回调
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            Log.i(TAG, "🔍 权限请求结果:")
            permissions.forEach { (permission, granted) ->
                Log.i(TAG, "  ${if (granted) "✅" else "❌"} $permission: ${if (granted) "已授予" else "被拒绝"}")
            }

            val grantedPermissions = permissions.filter { it.value }
            val deniedPermissions = permissions.filter { !it.value }

            Log.i(TAG, "📊 权限统计: ${grantedPermissions.size}/${permissions.size} 已授予")

            if (permissions.all { it.value }) {
                Log.i(TAG, "✅ 所有权限已获取，启动位置更新")
                startLocationUpdates()
                startGpsStatusMonitoring()
            } else {
                Log.w(TAG, "⚠️ 部分权限未获取，功能可能受限")
                Log.w(TAG, "❌ 被拒绝的权限: ${deniedPermissions.keys.joinToString(", ")}")

                // 检查核心权限是否都被授予
                val corePermissionsGranted = CORE_PERMISSIONS.all { corePermission ->
                    permissions[corePermission] == true
                }

                if (corePermissionsGranted) {
                    Log.i(TAG, "✅ 核心权限已获取，启动位置更新")
                    startLocationUpdates()
                    startGpsStatusMonitoring()
                } else {
                    Log.e(TAG, "❌ 核心权限被拒绝，无法启动GPS功能")
                    val deniedCorePermissions = CORE_PERMISSIONS.filter { permissions[it] != true }
                    Log.e(TAG, "❌ 被拒绝的核心权限: ${deniedCorePermissions.joinToString(", ")}")
                }
            }
        }
        
        // 检查并请求权限
        Log.i(TAG, "🔍 检查当前权限状态:")

        // 首先检查核心权限
        val corePermissionStatus = CORE_PERMISSIONS.map { permission ->
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "  ${if (granted) "✅" else "❌"} [核心] $permission: ${if (granted) "已授予" else "需要请求"}")
            permission to granted
        }.toMap()

        // 然后检查所有权限
        val allPermissionStatus = REQUIRED_PERMISSIONS.map { permission ->
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            if (!CORE_PERMISSIONS.contains(permission)) {
                Log.i(TAG, "  ${if (granted) "✅" else "❌"} [可选] $permission: ${if (granted) "已授予" else "需要请求"}")
            }
            permission to granted
        }.toMap()

        val coreGrantedCount = corePermissionStatus.values.count { it }
        val allGrantedCount = allPermissionStatus.values.count { it }
        Log.i(TAG, "📊 核心权限状态: $coreGrantedCount/${CORE_PERMISSIONS.size} 已授予")
        Log.i(TAG, "📊 总权限状态: $allGrantedCount/${REQUIRED_PERMISSIONS.size} 已授予")

        // 如果核心权限都已授予，直接启动GPS功能
        if (corePermissionStatus.all { it.value }) {
            Log.i(TAG, "✅ 核心权限检查通过，直接启动位置更新")
            startLocationUpdates()
            startGpsStatusMonitoring()

            // 如果还有其他权限未授予，可以在后台请求
            if (!allPermissionStatus.all { it.value }) {
                Log.i(TAG, "📝 后台请求剩余权限以获得完整功能")
                val missingPermissions = allPermissionStatus.filter { !it.value }.keys.toTypedArray()
                permissionLauncher.launch(missingPermissions)
            }
        } else {
            Log.i(TAG, "⚠️ 需要请求核心权限")
            val missingCorePermissions = corePermissionStatus.filter { !it.value }.keys.toTypedArray()
            Log.i(TAG, "📝 需要请求的核心权限: ${missingCorePermissions.joinToString(", ")}")
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }
    
    /**
     * 注册增强版广播接收器
     */
    private fun registerBroadcastReceiver() {
        val intentFilter = createIntentFilter()
        try {
            ContextCompat.registerReceiver(
                this,
                enhancedAmapReceiver,
                intentFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.i(TAG, "✅ 增强版广播接收器注册成功")
            receiverStatus.value = "增强版接收器已启动，等待广播数据..."
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 广播接收器注册失败: ${e.message}", e)
            receiverStatus.value = "接收器注册失败: ${e.message}"
        }
    }
    
    /**
     * 设置用户界面
     */
    private fun setupUserInterface() {
        setContent {
            CarrotAmapTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // ===============================
                    // OpenpPilot状态卡片区域 - OpenpPilot Status Card (置顶显示，可切换)
                    // ===============================
                    if (showOpenpilotCard.value) {
                        OpenpilotStatusCard(
                            statusData = openpilotStatusData.value
                        )
                        Spacer(modifier = Modifier.height(6.dp)) // 减少间距
                    }

                    // ===============================
                    // 状态信息卡片区域 - Status Info Card
                    // ===============================
                    CompactStatusCard(
                        receiverStatus = receiverStatus.value,
                        totalBroadcastCount = totalBroadcastCount.intValue,
                        carrotManFields = carrotManFields.value,
                        networkStatus = networkConnectionStatus.value,
                        networkStats = networkStatistics.value,
                        onClearDataClick = {
                            // 清空广播数据列表
                            broadcastDataList.clear()
                            totalBroadcastCount.intValue = 0
                            receiverStatus.value = "数据已清空，等待新的广播..."
                            Log.i(TAG, "🗑️ 用户手动清空数据")
                        },

                    )

                    Spacer(modifier = Modifier.height(6.dp)) // 减少间距
                        
                    // ===============================
                    // 主内容区域 - 字段映射表格
                    // ===============================
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            // 表格头部
                            TableHeader()

                            Spacer(modifier = Modifier.height(4.dp))

                            // 字段数据 - 分组显示，支持滚动
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                // 基础状态和激活信息
                                item { TableSectionHeader("基础状态") }
                                items(getBasicStatusFields(carrotManFields.value)) { fieldData ->
                                    TableRow(fieldData.first, fieldData.second, fieldData.third)
                                }

                                // 道路和限速信息
                                item { TableSectionHeader("道路限速") }
                                items(getRoadSpeedFields(carrotManFields.value)) { fieldData ->
                                    TableRow(fieldData.first, fieldData.second, fieldData.third)
                                }

                                // GPS和位置信息
                                item { TableSectionHeader("GPS位置") }
                                items(getGpsLocationFields(carrotManFields.value)) { fieldData ->
                                    TableRow(fieldData.first, fieldData.second, fieldData.third)
                                }

                                // 转弯引导信息
                                item { TableSectionHeader("转弯引导") }
                                items(getTurnGuidanceFields(carrotManFields.value)) { fieldData ->
                                    TableRow(fieldData.first, fieldData.second, fieldData.third)
                                }

                                // 目标和路线信息
                                item { TableSectionHeader("目标路线") }
                                items(getRouteTargetFields(carrotManFields.value)) { fieldData ->
                                    TableRow(fieldData.first, fieldData.second, fieldData.third)
                                }

                                // SDI摄像头信息
                                item { TableSectionHeader("摄像头信息") }
                                items(getSdiCameraFields(carrotManFields.value)) { fieldData ->
                                    TableRow(fieldData.first, fieldData.second, fieldData.third)
                                }

                                // 交通和时间信息
                                item { TableSectionHeader("交通时间") }
                                items(getTrafficTimeFields(carrotManFields.value)) { fieldData ->
                                    TableRow(fieldData.first, fieldData.second, fieldData.third)
                                }

                                // CarrotMan命令信息
                                item { TableSectionHeader("CarrotMan命令") }
                                items(getCarrotManCommandFields(carrotManFields.value)) { fieldData ->
                                    TableRow(fieldData.first, fieldData.second, fieldData.third)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationButtons(
                        onNavigateToHome = { navigateToHome() },
                        onNavigateToCompany = { navigateToCompany() },
                        onToggleOpenpilotCard = {
                            showOpenpilotCard.value = !showOpenpilotCard.value
                            Log.i(TAG, "🔄 切换OpenpPilot卡片显示: ${showOpenpilotCard.value}")
                        }
                    )
                }
            }
        }
    }
    
    // Activity销毁时回调 - 清理所有资源，防止内存泄漏
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🔧 MainActivity正在销毁，清理资源...")
        
        try {
            unregisterReceiver(enhancedAmapReceiver)    // 1. 取消注册广播接收器
            receiverScope.cancel()                      // 2. 取消协程作用域
            sensorManager.unregisterListener(this)     // 3. 取消注册传感器监听器
            locationManager.removeUpdates(locationListener) // 4. 取消位置更新
            if (::carrotNetworkClient.isInitialized) { // 5. 清理网络客户端
                carrotNetworkClient.cleanup()
            }
            // 车辆数据ViewModel已移除，现在使用独立Activity
            Log.i(TAG, "✅ 所有监听器已注销并释放资源")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 资源清理失败: ${e.message}", e)
        }
    }

    // 处理从静态接收器启动的Intent
    private fun handleIntentFromStaticReceiver(intent: Intent?) {
        intent?.let {
            if (it.action == ACTION_AMAP_SEND) {
                Log.i(TAG, "📨 从静态接收器启动，处理Intent数据")
                handleAmapSendBroadcast(it)
            }
        }
    }

    // 初始化传感器系统 - 设置加速度传感器、磁力传感器和旋转向量传感器
    private fun initializeSensors() {
        Log.i(TAG, "🧭 初始化传感器系统...")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        Log.i(TAG, "✅ 传感器系统初始化完成")
    }

    // 初始化CarrotMan网络客户端 - 设置网络通信、设备发现和自动数据发送功能
    private fun initializeNetworkClient() {
        Log.i(TAG, "🌐 初始化CarrotMan网络客户端...")
        
        try {
            carrotNetworkClient = CarrotManNetworkClient(this)
            
            carrotNetworkClient.setOnDeviceDiscovered { device ->
                receiverScope.launch(Dispatchers.Main) {
                    discoveredDevicesList.add(device)
                    Log.i(TAG, "🎯 发现Comma3设备: $device")
                }
            }
            
            carrotNetworkClient.setOnConnectionStatusChanged { connected, message ->
                receiverScope.launch(Dispatchers.Main) {
                    networkConnectionStatus.value = if (connected) "✅ $message" else "❌ $message"
                    Log.i(TAG, "🌐 网络状态变化: $message")
                }
            }
            
            carrotNetworkClient.setOnDataSent { packetCount ->
                receiverScope.launch(Dispatchers.Main) {
                    networkStatistics.value = carrotNetworkClient.getConnectionStatus()
                }
            }

                    carrotNetworkClient.setOnOpenpilotStatusReceived { jsonData ->
                receiverScope.launch(Dispatchers.Main) {
                    parseOpenpilotStatusData(jsonData)
                }
            }
            
            // ===============================
            // 启动网络服务和自动数据发送
            // ===============================
            carrotNetworkClient.start()
            carrotNetworkClient.startAutoDataSending(autoSendEnabled, carrotManFields)

            // OpenpPilot状态数据接收已集成到CarrotManNetworkClient中

            Log.i(TAG, "✅ CarrotMan网络客户端初始化成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 网络客户端初始化失败: ${e.message}", e)
            networkConnectionStatus.value = "❌ 初始化失败: ${e.message}"
        }
    }
    
    /**
     * 开始自动数据发送
     * 在后台协程中持续发送CarrotMan数据到Comma3设备
     */
    private fun startAutoDataSending() {
        // 功能已迁移至 CarrotManNetworkClient.startAutoDataSending
    }

    // OpenpPilot状态数据接收功能已迁移到CarrotManNetworkClient中

    /**
     * 映射xState枚举值到中文描述
     */
    private fun mapXStateToDescription(xState: Int): String {
        return when (xState) {
            0 -> "跟车模式"      // lead
            1 -> "巡航模式"      // cruise
            2 -> "端到端巡航"    // e2eCruise
            3 -> "端到端停车"    // e2eStop
            4 -> "端到端准备"    // e2ePrepare
            5 -> "端到端已停"    // e2eStopped
            else -> "未知状态($xState)"
        }
    }

    /**
     * 解析OpenpPilot状态JSON数据
     */
    private fun parseOpenpilotStatusData(jsonData: String) {
        try {
            Log.d(TAG, "🔍 开始解析OpenpPilot JSON数据: ${jsonData.take(200)}...")

            val jsonObject = JSONObject(jsonData)

            // 记录接收到的关键字段
            val vEgo = jsonObject.optInt("v_ego_kph", 0)
            val isActive = jsonObject.optBoolean("active", false)
            val isOnroad = jsonObject.optBoolean("IsOnroad", false)

            Log.d(TAG, "🚗 解析关键数据: 车速=${vEgo}km/h, 激活=${isActive}, 在路上=${isOnroad}")

            val statusData = OpenpilotStatusData(
                carrot2 = jsonObject.optString("Carrot2", ""),
                isOnroad = isOnroad,
                carrotRouteActive = jsonObject.optBoolean("CarrotRouteActive", false),
                ip = jsonObject.optString("ip", ""),
                port = jsonObject.optInt("port", 0),
                logCarrot = jsonObject.optString("log_carrot", ""),
                vCruiseKph = jsonObject.optDouble("v_cruise_kph", 0.0).toFloat(),
                vEgoKph = vEgo,
                tbtDist = jsonObject.optInt("tbt_dist", 0),
                sdiDist = jsonObject.optInt("sdi_dist", 0),
                active = isActive,
                xState = jsonObject.optInt("xState", 0),
                trafficState = jsonObject.optInt("trafficState", 0),
                lastUpdateTime = System.currentTimeMillis() // 设置当前时间为更新时间
            )

            // 在主线程更新UI状态
            runOnUiThread {
                val oldData = openpilotStatusData.value
                openpilotStatusData.value = statusData

                Log.i(TAG, "✅ OpenpPilot状态已更新到UI: 车速=${statusData.vEgoKph}km/h, 激活=${statusData.active}, 在路上=${statusData.isOnroad}")

                // 如果是重要状态变化，记录详细日志
                if (oldData.vEgoKph != statusData.vEgoKph || oldData.active != statusData.active) {
                    Log.i(TAG, "🔄 状态变化: 车速 ${oldData.vEgoKph} -> ${statusData.vEgoKph}, 激活 ${oldData.active} -> ${statusData.active}")
                }
            }

        } catch (e: JSONException) {
            Log.e(TAG, "JSON解析失败: ${e.message}, 原始数据: $jsonData", e)
        } catch (e: Exception) {
            Log.e(TAG, "解析OpenpPilot状态数据失败: ${e.message}, 原始数据: $jsonData", e)
        }
    }

    /**
     * 获取OpenpPilot状态字段数据
     * 返回三元组：(字段名称, 中文名称, 数据值)
     */
    private fun getOpenpilotStatusFields(statusData: OpenpilotStatusData): List<Triple<String, String, String>> {
        return listOf(
            // 基础信息
            Triple("Carrot2", "版本信息", statusData.carrot2.ifEmpty { "未知" }),
            Triple("ip", "设备IP", statusData.ip.ifEmpty { "未连接" }),
            Triple("port", "通信端口", statusData.port.toString()),

            // 系统状态
            Triple("IsOnroad", "道路状态", if (statusData.isOnroad) "在路上" else "未上路"),
            Triple("active", "自动驾驶", if (statusData.active) "激活" else "未激活"),
            Triple("CarrotRouteActive", "导航状态", if (statusData.carrotRouteActive) "导航中" else "未导航"),
            Triple("log_carrot", "系统日志", statusData.logCarrot.ifEmpty { "无日志" }),

            // 速度信息
            Triple("v_ego_kph", "当前车速", "${statusData.vEgoKph} km/h"),
            Triple("v_cruise_kph", "巡航速度", "${statusData.vCruiseKph} km/h"),

            // 导航距离
            Triple("tbt_dist", "转弯距离", "${statusData.tbtDist} m"),
            Triple("sdi_dist", "限速距离", "${statusData.sdiDist} m"),

            // 控制状态
            Triple("xState", "纵向状态", mapXStateToDescription(statusData.xState)),
            Triple("trafficState", "交通状态", getTrafficStateDescription(statusData.trafficState)),

            // 时间信息
            Triple("lastUpdateTime", "更新时间", formatTimestamp(statusData.lastUpdateTime))
        )
    }

    /**
     * 获取交通状态描述
     */
    private fun getTrafficStateDescription(trafficState: Int): String {
        return when (trafficState) {
            0 -> "无信号"
            1 -> "红灯"
            2 -> "绿灯"
            3 -> "左转"
            else -> "未知($trafficState)"
        }
    }

    /**
     * 格式化时间戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 检查位置提供者状态
     */
    private fun checkLocationProviderStatus() {
        try {
            Log.i(TAG, "🔍 检查位置提供者状态:")

            // 检查GPS提供者
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            Log.i(TAG, "  📡 GPS提供者: ${if (isGpsEnabled) "✅ 启用" else "❌ 禁用"}")

            // 检查网络提供者
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            Log.i(TAG, "  🌐 网络提供者: ${if (isNetworkEnabled) "✅ 启用" else "❌ 禁用"}")

            // 检查被动提供者
            val isPassiveEnabled = locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
            Log.i(TAG, "  📱 被动提供者: ${if (isPassiveEnabled) "✅ 启用" else "❌ 禁用"}")

            // 获取所有提供者
            val allProviders = locationManager.allProviders
            Log.i(TAG, "  📋 所有提供者: $allProviders")

            // 获取启用的提供者
            val enabledProviders = locationManager.getProviders(true)
            Log.i(TAG, "  ✅ 启用的提供者: $enabledProviders")

            // 尝试获取最后已知位置
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val lastGpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                Log.i(TAG, "  📍 最后GPS位置: ${lastGpsLocation?.let { "lat=${String.format("%.6f", it.latitude)}, lon=${String.format("%.6f", it.longitude)}, ${System.currentTimeMillis() - it.time}ms前" } ?: "无"}")
                Log.i(TAG, "  🌐 最后网络位置: ${lastNetworkLocation?.let { "lat=${String.format("%.6f", it.latitude)}, lon=${String.format("%.6f", it.longitude)}, ${System.currentTimeMillis() - it.time}ms前" } ?: "无"}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查位置提供者状态失败: ${e.message}", e)
        }
    }

    /**
     * 启动位置更新服务
     * 启用GPS和网络定位来获取当前位置
     * ✅ 重新启用：用于更新vpPosPointLat和vpPosPointLon字段
     */
    private fun startLocationUpdates() {
        Log.i(TAG, "📍 启动GPS位置更新服务...")

        // 首先检查系统位置设置
        checkAndPromptLocationSettings()

        // 然后检查位置提供者状态
        checkLocationProviderStatus()

        try {
            // 检查位置权限
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                // 启用GPS定位 - 高精度
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L, // 1秒更新一次
                        1f,    // 1米移动距离触发更新
                        locationListener
                    )
                    Log.i(TAG, "✅ GPS定位已启动")
                } else {
                    Log.w(TAG, "⚠️ GPS提供者未启用，跳过GPS定位")
                }

                // 启用网络定位 - 作为备选方案
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000L, // 2秒更新一次（网络定位频率稍低）
                        5f,    // 5米移动距离触发更新
                        locationListener
                    )
                    Log.i(TAG, "✅ 网络定位已启动")
                } else {
                    Log.w(TAG, "⚠️ 网络提供者未启用，跳过网络定位")
                }

                Log.i(TAG, "✅ 位置更新服务启动完成")

                // 立即请求一次位置更新来测试
                requestImmediateLocationUpdate()

            } else {
                Log.w(TAG, "⚠️ 缺少位置权限，无法启动位置更新")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动位置更新失败: ${e.message}", e)
        }
    }

    /**
     * 立即请求一次位置更新来测试GPS功能
     */
    private fun requestImmediateLocationUpdate() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "🔍 请求立即位置更新测试...")

                // 尝试获取最后已知位置
                val lastGpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNetworkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (lastGpsLocation != null) {
                    Log.i(TAG, "📍 使用最后GPS位置进行测试更新")
                    locationListener.onLocationChanged(lastGpsLocation)
                } else if (lastNetworkLocation != null) {
                    Log.i(TAG, "🌐 使用最后网络位置进行测试更新")
                    locationListener.onLocationChanged(lastNetworkLocation)
                } else {
                    Log.w(TAG, "⚠️ 没有可用的最后已知位置")
                }

                // 请求单次位置更新
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null)
                    Log.i(TAG, "📡 已请求GPS单次位置更新")
                }

                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null)
                    Log.i(TAG, "🌐 已请求网络单次位置更新")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 请求立即位置更新失败: ${e.message}", e)
        }
    }

    /**
     * 启动GPS状态监控
     */
    private fun startGpsStatusMonitoring() {
        // 使用Handler定期检查GPS状态
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                logCurrentGpsStatus()
                handler.postDelayed(this, 10000) // 每10秒检查一次
            }
        }
        handler.postDelayed(runnable, 5000) // 5秒后开始第一次检查
        Log.i(TAG, "🔍 GPS状态监控已启动")
    }

    /**
     * 记录当前GPS状态
     */
    private fun logCurrentGpsStatus() {
        try {
            val currentFields = carrotManFields.value
            Log.i(TAG, "📊 当前GPS字段状态:")
            Log.i(TAG, "  📍 vpPosPointLat: ${String.format("%.6f", currentFields.vpPosPointLat)}")
            Log.i(TAG, "  📍 vpPosPointLon: ${String.format("%.6f", currentFields.vpPosPointLon)}")
            Log.i(TAG, "  📍 vpPosPointLatNavi: ${String.format("%.6f", currentFields.vpPosPointLatNavi)}")
            Log.i(TAG, "  📍 vpPosPointLonNavi: ${String.format("%.6f", currentFields.vpPosPointLonNavi)}")
            Log.i(TAG, "  🔄 gps_valid: ${currentFields.gps_valid}")
            Log.i(TAG, "  📡 gps_accuracy_phone: ${currentFields.gps_accuracy_phone}")
            Log.i(TAG, "  ⏰ last_update_gps_time: ${if (currentFields.last_update_gps_time > 0) "${System.currentTimeMillis() - currentFields.last_update_gps_time}ms前" else "从未更新"}")

            // 检查是否所有GPS字段都是零
            if (currentFields.vpPosPointLat == 0.0 && currentFields.vpPosPointLon == 0.0 &&
                currentFields.vpPosPointLatNavi == 0.0 && currentFields.vpPosPointLonNavi == 0.0) {
                Log.w(TAG, "⚠️ 所有GPS坐标字段仍为零，GPS可能未正常工作")
                checkLocationProviderStatus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 记录GPS状态失败: ${e.message}", e)
        }
    }

    /**
     * 检查并提示启用位置服务
     */
    private fun checkAndPromptLocationSettings() {
        try {
            val locationMode = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )

            Log.i(TAG, "🔍 系统位置模式: $locationMode")

            when (locationMode) {
                Settings.Secure.LOCATION_MODE_OFF -> {
                    Log.w(TAG, "⚠️ 位置服务已关闭")
                    // 可以在这里提示用户开启位置服务
                }
                Settings.Secure.LOCATION_MODE_SENSORS_ONLY -> {
                    Log.i(TAG, "📡 位置模式: 仅设备传感器(GPS)")
                }
                Settings.Secure.LOCATION_MODE_BATTERY_SAVING -> {
                    Log.i(TAG, "🔋 位置模式: 省电模式(网络定位)")
                }
                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY -> {
                    Log.i(TAG, "🎯 位置模式: 高精度模式(GPS+网络)")
                }
                else -> {
                    Log.i(TAG, "❓ 位置模式: 未知($locationMode)")
                }
            }

            // 检查位置服务是否完全启用
            val isLocationEnabled = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    locationManager.isLocationEnabled
                } else {
                    locationMode != Settings.Secure.LOCATION_MODE_OFF
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查位置服务状态失败: ${e.message}")
                false
            }

            Log.i(TAG, "📍 位置服务总体状态: ${if (isLocationEnabled) "✅ 启用" else "❌ 禁用"}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查位置设置失败: ${e.message}", e)
        }
    }



    // ===============================
    // 传感器事件处理方法区域 - Sensor Event Handling Methods
    // ===============================
    
    /**
     * 传感器数据变化回调
     * 处理加速度传感器、磁力传感器和旋转向量传感器的数据
     * @param event 传感器事件数据
     */
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            // 加速度传感器数据处理
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                updateOrientationAngles()
            }
            
            // 磁力传感器数据处理
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                updateOrientationAngles()
            }
            
            // 旋转向量传感器数据处理（推荐方式）
            Sensor.TYPE_ROTATION_VECTOR -> {
                // 直接使用旋转向量计算方向角
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientationAngles = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                
                // 计算方位角并归一化到0-360度
                val azimuth = Math.toDegrees(orientationAngles[0].toDouble())
                val normalizedAzimuth = ((azimuth + 360) % 360)
                
                // 更新CarrotMan字段中的方向相关数据
                carrotManFields.value = carrotManFields.value.copy(
                    nPosAnglePhone = normalizedAzimuth,     // 手机方向角
                    bearing_measured = normalizedAzimuth,   // 测量方位角
                    bearing = normalizedAzimuth             // 当前方位角
                )
            }
        }
    }

    /**
     * 使用加速度计和磁力计数据更新方向角
     * 作为旋转向量传感器的备选方案
     */
    private fun updateOrientationAngles() {
        // 使用加速度计和磁力计数据计算旋转矩阵
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            // 获取设备方向角度
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            
            // 计算方位角并归一化
            val azimuth = Math.toDegrees(orientationAngles[0].toDouble())
            val normalizedAzimuth = ((azimuth + 360) % 360)
            
            // 更新手机方向角和测量方位角
            carrotManFields.value = carrotManFields.value.copy(
                nPosAnglePhone = normalizedAzimuth,
                bearing_measured = normalizedAzimuth
            )
            
            // 计算方位角偏移量
            val currentBearing = carrotManFields.value.bearing
            val diff = abs(normalizedAzimuth - currentBearing)
            
            // 处理角度跨越0度的情况
            val offset = if (diff > 180) 360 - diff else diff
            
            carrotManFields.value = carrotManFields.value.copy(
                bearing_offset = offset,
                diff_angle_count = carrotManFields.value.diff_angle_count + 1
            )
        }
    }

    /**
     * 传感器精度变化回调
     * @param sensor 传感器对象
     * @param accuracy 精度等级
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 记录传感器精度变化
        sensor?.let {
            when (accuracy) {
                SensorManager.SENSOR_STATUS_NO_CONTACT -> 
                    Log.w(TAG, "传感器${it.name}无接触")
                SensorManager.SENSOR_STATUS_UNRELIABLE -> 
                    Log.w(TAG, "传感器${it.name}数据不可靠")
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 
                    Log.d(TAG, "传感器${it.name}精度低")
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 
                    Log.d(TAG, "传感器${it.name}精度中等")
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 
                    Log.d(TAG, "传感器${it.name}精度高")
            }
        }
    }

    /**
     * 🎯 处理高德地图发送的广播数据 - 核心方法
     */
    private fun handleAmapSendBroadcast(intent: Intent) {
        val keyType = intent.getIntExtra("KEY_TYPE", -1)
        Log.d(TAG, "📦 处理KEY_TYPE: $keyType")
        
        try {
            // 🔧 解析基础广播数据
            val broadcastData = parseBroadcastData(intent)
            
            // 🚀 异步处理数据更新，避免阻塞UI
            receiverScope.launch {
                // 通知UI更新
                updateBroadcastData(broadcastData)
                
                // 根据具体类型处理数据
                when (keyType) {
                    KEY_TYPE_MAP_STATE -> handleMapState(intent)
                    KEY_TYPE_GUIDE_INFO -> handleGuideInfo(intent)
                    KEY_TYPE_LOCATION_INFO -> handleLocationInfo(intent)
                    KEY_TYPE_TURN_INFO -> handleTurnInfo(intent)
                    KEY_TYPE_NAVIGATION_STATUS -> handleNavigationStatus(intent)
                    KEY_TYPE_ROUTE_INFO -> handleRouteInfo(intent)
                    KEY_TYPE_SPEED_LIMIT -> handleSpeedLimit(intent)
                    KEY_TYPE_CAMERA_INFO -> handleCameraInfo(intent)
                    KEY_TYPE_CAMERA_INFO_V2 -> handleCameraInfoV2(intent)
                    KEY_TYPE_FAVORITE_RESULT -> handleFavoriteResult(intent)
                    KEY_TYPE_HOME_COMPANY_NAVIGATION -> handleHomeCompanyNavigation(intent)
                    KEY_TYPE_ADMIN_AREA -> handleAdminArea(intent)
                    KEY_TYPE_NAVI_STATUS -> handleNaviStatus(intent)
                    KEY_TYPE_TRAFFIC_INFO -> handleTrafficInfo(intent)
                    KEY_TYPE_NAVI_SITUATION -> handleNaviSituation(intent)
                    KEY_TYPE_NEXT_INTERSECTION -> handleNextIntersection(intent)
                    KEY_TYPE_SPEED_LIMIT_NEW -> handleSpeedLimitNew(intent)
                    KEY_TYPE_SAPA_INFO -> handleSapaInfo(intent)
                    KEY_TYPE_TRAFFIC_LIGHT -> handleTrafficLightInfo(intent)
                    KEY_TYPE_SDI_PLUS_INFO -> handleSdiPlusInfo(intent)
                    KEY_TYPE_ROUTE_INFO_QUERY -> handleRouteInfoQuery(intent)

                    // 🎯 新增：路线规划和导航控制类型
                    KEY_TYPE_ROUTE_PLANNING -> handleRoutePlanning(intent)
                    KEY_TYPE_START_NAVIGATION -> handleStartNavigation(intent)
                    KEY_TYPE_STOP_NAVIGATION -> handleStopNavigation(intent)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理KEY_TYPE $keyType 失败: ${e.message}", e)
        }
    }

    /**
     * 🔧 记录所有Intent额外数据（调试用）
     */
    private fun logAllExtras(intent: Intent) {
        val extras = intent.extras
        if (extras != null) {
            Log.v(TAG, "Intent包含的所有数据:")
            for (key in extras.keySet()) {
                val value: String = try {
                    when {
                        extras.getString(key) != null -> extras.getString(key) ?: "null"
                        extras.getInt(key, Int.MIN_VALUE) != Int.MIN_VALUE -> extras.getInt(key).toString()
                        extras.getLong(key, Long.MIN_VALUE) != Long.MIN_VALUE -> extras.getLong(key).toString()
                        extras.getDouble(key, Double.NaN).let { !it.isNaN() } -> extras.getDouble(key).toString()
                        extras.getFloat(key, Float.NaN).let { !it.isNaN() } -> extras.getFloat(key).toString()
                        extras.getBoolean(key, false) -> extras.getBoolean(key).toString()
                        else -> "未知类型"
                    }
                } catch (e: Exception) {
                    "获取失败: ${e.message}"
                }
                Log.v(TAG, "   $key = $value")
            }
        }
    }

    // 处理其他格式的高德地图广播
    private fun handleAlternativeAmapBroadcast(intent: Intent) {
        Log.i(TAG, "🔄 处理其他格式高德广播: ${intent.action}")
        logAllExtras(intent)
        extractBasicNavigationInfo(intent)
    }

    // 从未识别的广播中提取基础导航信息
    private fun extractBasicNavigationInfo(intent: Intent) {
        Log.d(TAG, "🔍 尝试从未识别广播中提取基础导航信息...")
        // 提取常见的导航相关字段
        intent.extras?.let { bundle ->
            var hasUpdate = false
            
            // 提取位置信息
            val lat = bundle.getDouble("latitude", 0.0).takeIf { it != 0.0 }
                ?: bundle.getDouble("lat", 0.0)
            val lon = bundle.getDouble("longitude", 0.0).takeIf { it != 0.0 }
                ?: bundle.getDouble("lon", 0.0)
            
            if (lat != 0.0 && lon != 0.0) {
                carrotManFields.value = carrotManFields.value.copy(
                    vpPosPointLat = lat,
                    vpPosPointLon = lon,
                    gps_valid = true,
                    last_update_gps_time = System.currentTimeMillis()
                )
                hasUpdate = true
            }
            
            // 提取速度信息
            bundle.getInt("speed", -1).takeIf { it >= 0 }?.let { speed ->
                carrotManFields.value = carrotManFields.value.copy(
                    nPosSpeed = speed.toDouble()
                )
                hasUpdate = true
            }
            
            // 提取方向信息
            bundle.getInt("bearing", -1).takeIf { it >= 0 }?.let { bearing ->
                carrotManFields.value = carrotManFields.value.copy(
                    nPosAngle = bearing.toDouble()
                )
                hasUpdate = true
            }
            
            if (hasUpdate) {
                Log.d(TAG, "✅ 成功提取基础导航信息")
            }
        }
    }

    // 创建广播接收器的IntentFilter
    private fun createIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(ACTION_AMAP_SEND)   // 主要的高德广播
            addAction(ACTION_AMAP_RECV)   // 发送给高德的数据
           // addAction(ACTION_AMAP_LEGACY) // 兼容旧版
           // addAction(ACTION_AUTONAVI)    // 另一种格式
            
            // 其他可能的高德导航广播
        //    addAction("AMAP_NAVI_ACTION_UPDATE")
        //    addAction("AMAP_NAVI_ACTION_TURN")
        //    addAction("AMAP_NAVI_ACTION_ROUTE")
        //    addAction("AMAP_NAVI_ACTION_LOCATION")
            
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
    }

    // 解析广播数据
    private fun parseBroadcastData(intent: Intent): BroadcastData {
        val keyType = intent.getIntExtra("KEY_TYPE", -1)
        val timestamp = System.currentTimeMillis()
        val extras = mutableMapOf<String, String>()
        
        intent.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                try {
                    // 使用完全类型安全的方法获取值，避免所有类型转换异常
                    @Suppress("DEPRECATION")
                    val rawValue = bundle.get(key)
                    val value = when (rawValue) {
                        is String -> rawValue
                        is Int -> rawValue.toString()
                        is Long -> rawValue.toString()
                        is Double -> rawValue.toString()
                        is Float -> rawValue.toString()
                        is Boolean -> rawValue.toString()
                        null -> "null"
                        else -> rawValue.toString()
                    }
                    extras[key] = value
                } catch (e: Exception) {
                    Log.w(TAG, "解析键 '$key' 时出错: ${e.message}")
                    extras[key] = "解析异常: ${e.message}"
                }
            }
        }
        
        return BroadcastData(
            keyType = keyType,
            dataType = getDataTypeDescription(keyType),
            timestamp = timestamp,
            rawExtras = extras,
            parsedContent = parseSpecificData(keyType, intent)
        )
    }

    // 根据KEY_TYPE获取数据类型描述
    private fun getDataTypeDescription(keyType: Int): String {
        return when (keyType) {
            KEY_TYPE_GUIDE_INFO -> "导航引导信息 ⭐"
            KEY_TYPE_LOCATION_INFO -> "定位信息 ⭐"
            KEY_TYPE_TURN_INFO -> "转向信息 ⭐"
            KEY_TYPE_NAVIGATION_STATUS -> "导航状态 ⭐"
            KEY_TYPE_ROUTE_INFO -> "路线信息 ⭐"
            KEY_TYPE_SPEED_LIMIT -> "限速信息 ⭐"
            KEY_TYPE_MAP_STATE -> "地图状态"
            KEY_TYPE_CAMERA_INFO -> "摄像头信息 ⭐"
            KEY_TYPE_FAVORITE_RESULT -> "收藏点结果 ⭐"
            KEY_TYPE_ADMIN_AREA -> "行政区域信息 ⭐"
            KEY_TYPE_NAVI_STATUS -> "导航状态变化 ⭐"
            KEY_TYPE_TRAFFIC_INFO -> "路况信息 ⭐"
            KEY_TYPE_NAVI_SITUATION -> "导航态势信息 ⭐"
            KEY_TYPE_NEXT_INTERSECTION -> "下一路口信息 ⭐"
            KEY_TYPE_SPEED_LIMIT_NEW -> "新版限速信息 ⭐"
            KEY_TYPE_SAPA_INFO -> "服务区信息 ⭐"
            KEY_TYPE_TRAFFIC_LIGHT -> "红绿灯信息 ⭐"
            KEY_TYPE_SDI_PLUS_INFO -> "SDI Plus 信息 ⭐"
            KEY_TYPE_ROUTE_INFO_QUERY -> "路线信息查询结果 ⭐"
            KEY_TYPE_HOME_COMPANY_NAVIGATION -> "家庭/公司导航 ⭐"
            KEY_TYPE_ROUTE_PLANNING -> "路线规划 ⭐"
            KEY_TYPE_START_NAVIGATION -> "开始导航 ⭐"
            KEY_TYPE_STOP_NAVIGATION -> "停止导航 ⭐"
            else -> "未知类型($keyType)"
        }
    }

    // 解析特定类型的数据内容
    private fun parseSpecificData(keyType: Int, intent: Intent): String {
        return when (keyType) {
            KEY_TYPE_GUIDE_INFO -> parseGuideInfoContent(intent)
            KEY_TYPE_LOCATION_INFO -> parseLocationInfoContent(intent)
            KEY_TYPE_TURN_INFO -> parseTurnInfoContent(intent)
            KEY_TYPE_NAVIGATION_STATUS -> parseNavigationStatusContent(intent)
            KEY_TYPE_ROUTE_INFO -> parseRouteInfoContent(intent)
            KEY_TYPE_SPEED_LIMIT -> parseSpeedLimitContent(intent)
            KEY_TYPE_MAP_STATE -> parseMapStateContent(intent)
            KEY_TYPE_CAMERA_INFO -> parseCameraInfoContent(intent)
            KEY_TYPE_FAVORITE_RESULT -> parseFavoriteResultContent(intent)
            KEY_TYPE_ADMIN_AREA -> parseAdminAreaContent(intent)
            KEY_TYPE_NAVI_STATUS -> parseNaviStatusContent(intent)
            KEY_TYPE_TRAFFIC_INFO -> parseTrafficInfoContent(intent)
            KEY_TYPE_NAVI_SITUATION -> parseNaviSituationContent(intent)
            KEY_TYPE_NEXT_INTERSECTION -> parseNextIntersectionContent(intent)
            KEY_TYPE_SPEED_LIMIT_NEW -> parseSpeedLimitNewContent(intent)
            KEY_TYPE_SAPA_INFO -> parseSapaInfoContent(intent)
            KEY_TYPE_TRAFFIC_LIGHT -> parseTrafficLightContent(intent)
            KEY_TYPE_SDI_PLUS_INFO -> parseSdiPlusInfoContent(intent)
            KEY_TYPE_ROUTE_INFO_QUERY -> parseRouteInfoQueryContent(intent)
            else -> "原始数据: ${intent.extras?.toString() ?: "无数据"}"
        }
    }

    // 处理地图状态广播
    private fun handleMapState(intent: Intent) {
        val state = intent.getIntExtra("EXTRA_STATE", -1)
        val stateDesc = when (state) {
            0 -> "开始运行"
            1 -> "初始化完成"
            2 -> "运行结束"
            3 -> "进入前台"
            4 -> "进入后台"
            5 -> "开始算路"
            6 -> "算路完成，成功"
            7 -> "算路完成，失败"
            8 -> "开始导航"
            9 -> "结束导航"
            39 -> "到达目的地"  // 🎯 新增：到达目的地状态
            else -> "未知状态($state)"
        }
        Log.i(TAG, "🗺️ 地图状态: $stateDesc")

        // 🎯 特殊处理：到达目的地时更新CarrotMan转弯信息
        if (state == AppConstants.AmapBroadcast.NavigationState.ARRIVE_DESTINATION) {
            Log.i(TAG, "🏁 检测到到达目的地，更新CarrotMan转弯信息")

            carrotManFields.value = carrotManFields.value.copy(
                nTBTTurnType = 201,           // 到达目的地转弯类型
                nTBTDist = 0,                 // 距离设为0
                szTBTMainText = "到达目的地",   // 主要文本
                szNearDirName = "目的地",      // 附近方向名称
                szFarDirName = "",            // 远方向名称清空
                xTurnInfo = 8,                // xTurnInfo设为8(到达目的地)
                xDistToTurn = 0,              // 转弯距离设为0
                debugText = stateDesc,
                source_last = "amap",
                lastUpdateTime = System.currentTimeMillis(),
                dataQuality = "good"
            )
        } else {
            carrotManFields.value = carrotManFields.value.copy(
                debugText = stateDesc,
                source_last = "amap",
                lastUpdateTime = System.currentTimeMillis(),
                dataQuality = "good"
            )
        }
    }

    /**
     * 🎯 处理导航引导信息
     */
    private fun handleGuideInfo(intent: Intent) {
        // 基础信息
        val currentRoad = intent.getStringExtra("CUR_ROAD_NAME") ?: ""
        val nextRoad = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
        val nextNextRoad = intent.getStringExtra("NEXT_NEXT_ROAD_NAME") ?: ""
        val speedLimit = intent.getIntExtra("LIMITED_SPEED", 0)
        val currentSpeed = intent.getIntExtra("CUR_SPEED", 0)
        val carDirection = intent.getIntExtra("CAR_DIRECTION", 0)
        
        // 距离和时间
        val remainDistance = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
        val remainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)
        val remainTimeString = intent.getStringExtra("ROUTE_REMAIN_TIME_STRING") ?: ""
        val routeAllDis = intent.getIntExtra("ROUTE_ALL_DIS", 0)
        val routeAllTime = intent.getIntExtra("ROUTE_ALL_TIME", 0)
        val etaText = intent.getStringExtra("ROUTE_REMAIN_TIME_AUTO") ?: ""
        val segRemainDis = intent.getIntExtra("SEG_REMAIN_DIS", 0)
        val segRemainTime = intent.getIntExtra("SEG_REMAIN_TIME", 0)
        val nextSegRemainDis = intent.getIntExtra("NEXT_SEG_REMAIN_DIS", 0)
        val nextSegRemainTime = intent.getIntExtra("NEXT_SEG_REMAIN_TIME", 0)
        val curSegNum = intent.getIntExtra("CUR_SEG_NUM", 0)
        val curPointNum = intent.getIntExtra("CUR_POINT_NUM", 0)
        
        // 转向图标和环岛信息
        val icon = intent.getIntExtra("ICON", -1)
        val newIcon = intent.getIntExtra("NEW_ICON", -1)
        val nextNextTurnIcon = intent.getIntExtra("NEXT_NEXT_TURN_ICON", -1)
        val roundAboutNum = intent.getIntExtra("ROUND_ABOUT_NUM", 0)
        val roundAllNum = intent.getIntExtra("ROUND_ALL_NUM", 0)
        
        // 位置信息
        val carLatitude = intent.getDoubleExtra("CAR_LATITUDE", 0.0)
        val carLongitude = intent.getDoubleExtra("CAR_LONGITUDE", 0.0)
        
        // 服务区和电子眼信息
        val sapaDist = intent.getIntExtra("SAPA_DIST", 0)
        val sapaType = intent.getIntExtra("SAPA_TYPE", -1)
        val sapaNum = intent.getIntExtra("SAPA_NUM", 0)
        val sapaName = intent.getStringExtra("SAPA_NAME") ?: ""
        val cameraDist = intent.getIntExtra("CAMERA_DIST", 0)
        val cameraType = intent.getIntExtra("CAMERA_TYPE", -1)
        val cameraSpeed = intent.getIntExtra("CAMERA_SPEED", 0)
        val cameraIndex = intent.getIntExtra("CAMERA_INDEX", -1)
        
        // 导航类型和其他信息
        val naviType = intent.getIntExtra("TYPE", 0)
        val trafficLightNum = intent.getIntExtra("TRAFFIC_LIGHT_NUM", 0)
        
        // 获取道路类型并映射到 roadcate
        val roadType = intent.getIntExtra("ROAD_TYPE", 8) // 默认为8（未知）
        val mappedRoadcate = mapRoadTypeToRoadcate(roadType)
        
        // 目的地信息
        val endPOIName = intent.getStringExtra("endPOIName") ?: ""
        val endPOIAddr = intent.getStringExtra("endPOIAddr") ?: ""
        val endPOILatitude = intent.getDoubleExtra("endPOILatitude", 0.0)
        val endPOILongitude = intent.getDoubleExtra("endPOILongitude", 0.0)
        
        // 自动转换的距离和时间信息
        val routeRemainDisAuto = intent.getStringExtra("ROUTE_REMAIN_DIS_AUTO") ?: ""
        val routeRemainTimeAuto = intent.getStringExtra("ROUTE_REMAIN_TIME_AUTO") ?: ""
        val sapaDistAuto = intent.getStringExtra("SAPA_DIST_AUTO") ?: ""
        val segRemainDisAuto = intent.getStringExtra("SEG_REMAIN_DIS_AUTO") ?: ""
        
        // 🎯 根据 NEW_ICON 优先，其次使用 ICON 来映射 CarrotMan 转弯类型
        val primaryIcon = if (newIcon != -1) newIcon else icon
        val carrotTurnType = if (primaryIcon != -1) {
            val mappedType = mapAmapIconToCarrotTurn(primaryIcon)

            // 🎯 隧道调试日志
            if (primaryIcon == 16 || primaryIcon == 19) {
                Log.w(TAG, "🚇 隧道检测: 高德图标=$primaryIcon -> CarrotMan类型=$mappedType")
                Log.w(TAG, "🚇 隧道动作: ${mapTurnIconToAction(primaryIcon)}")
                Log.w(TAG, "🚇 xTurnInfo: ${getXTurnInfo(mappedType)} (0=通知,不触发转弯)")
            }

            mappedType
        } else {
            carrotManFields.value.nTBTTurnType
        }
        
        // 🎯 根据 NEXT_NEXT_TURN_ICON 映射下一个转弯类型
        val carrotNextTurnType = if (nextNextTurnIcon != -1) {
            mapAmapIconToCarrotTurn(nextNextTurnIcon)
        } else {
            carrotManFields.value.nTBTTurnTypeNext
        }
        
        // 🎯 获取导航类型和修饰符 (当前转弯)
        val (navType, navModifier) = getTurnTypeAndModifier(carrotTurnType)

        // 🎯 隧道限速处理 - 确保隧道进入时使用广播中的限速
        if (primaryIcon == 16 || primaryIcon == 19) {
            val tunnelSpeedLimit = intent.getIntExtra("LIMITED_SPEED", -1)
            if (tunnelSpeedLimit > 0) {
                Log.w(TAG, "🚇 隧道限速: ${tunnelSpeedLimit}km/h (来自广播数据)")
                updateRoadSpeedLimit(tunnelSpeedLimit)
                updateSpeedControl()
            }
        }

        // 🎯 获取下一个导航类型和修饰符
        val (navTypeNext, navModifierNext) = getTurnTypeAndModifier(carrotNextTurnType)
        
        // 🎯 根据转弯类型生成主转向指令文本
        val szTBTMainText = if (carrotTurnType != -1) {
            generateTurnInstructionText(carrotTurnType, segRemainDis, nextRoad)
        } else {
            carrotManFields.value.szTBTMainText
        }
        
        // 🎯 根据下一个转弯类型生成下一转向指令文本
        val szTBTMainTextNext = if (carrotNextTurnType != -1) {
            generateTurnInstructionText(carrotNextTurnType, nextSegRemainDis, nextNextRoad)
        } else {
            carrotManFields.value.szTBTMainTextNext
        }
        
        // 日志输出
        Log.i(TAG, "🧭 导航引导信息:")
        Log.d(TAG, "   当前道路: $currentRoad")
        Log.d(TAG, "   下一道路: $nextRoad")
        Log.d(TAG, "   下下个道路: $nextNextRoad")
        Log.d(TAG, "   剩余距离: ${remainDistance}米")
        Log.d(TAG, "   剩余时间: ${remainTime}秒")
        Log.d(TAG, "   剩余时间(文本): $remainTimeString")
        Log.d(TAG, "   当前速度: ${currentSpeed}km/h")
        Log.d(TAG, "   车辆方向: ${carDirection}°")
        Log.d(TAG, "   总距离: ${routeAllDis}米")
        Log.d(TAG, "   总时间: ${routeAllTime}秒")
        Log.d(TAG, "   预计到达: $etaText")
        Log.d(TAG, "   当前路段剩余: ${segRemainDis}米")
        Log.d(TAG, "   当前路段时间: ${segRemainTime}秒")
        Log.d(TAG, "   下一路段剩余: ${nextSegRemainDis}米")
        Log.d(TAG, "   下一路段时间: ${nextSegRemainTime}秒")
        Log.d(TAG, "   当前段号: $curSegNum")
        Log.d(TAG, "   当前点号: $curPointNum")
        Log.d(TAG, "   转弯图标: ${mapTurnIconToAction(icon)}")
        Log.d(TAG, "   新转弯图标: ${mapTurnIconToAction(newIcon)}")
        Log.d(TAG, "   下下个转弯图标: ${mapTurnIconToAction(nextNextTurnIcon)}")
        Log.d(TAG, "   主转向指令: $szTBTMainText")
        Log.d(TAG, "   下一转向指令: $szTBTMainTextNext")
        Log.d(TAG, "   环岛出口: $roundAboutNum/$roundAllNum")
        Log.d(TAG, "   道路类型: ${getRoadTypeDescription(roadType)} (roadcate=$mappedRoadcate)")
        Log.d(TAG, "   导航类型: ${getNaviTypeDescription(naviType)}")
        Log.d(TAG, "   红绿灯数量: $trafficLightNum")
        Log.d(TAG, "   转弯类型映射: Icon($primaryIcon) -> CarrotTurn($carrotTurnType) -> NavType($navType, $navModifier)")
        Log.d(TAG, "   下一转弯映射: Icon($nextNextTurnIcon) -> CarrotTurn($carrotNextTurnType) -> NavType($navTypeNext, $navModifierNext)")
        
        if (endPOIName.isNotEmpty()) {
            Log.d(TAG, "   目的地名称: $endPOIName")
            Log.d(TAG, "   目的地地址: $endPOIAddr")
            Log.d(TAG, "   目的地坐标: ($endPOILatitude, $endPOILongitude)")
        }
        
        if (sapaDist > 0) {
            Log.d(TAG, "   服务区距离: ${sapaDist}米")
            Log.d(TAG, "   服务区类型: ${mapSapaType(sapaType)}")
            Log.d(TAG, "   服务区数量: $sapaNum")
            Log.d(TAG, "   服务区名称: $sapaName")
        }
        
        if (cameraDist > 0) {
            Log.d(TAG, "   电子眼距离: ${cameraDist}米")
            Log.d(TAG, "   电子眼类型: ${mapCameraType(cameraType)}")
            Log.d(TAG, "   电子眼限速: ${cameraSpeed}km/h")
            Log.d(TAG, "   电子眼编号: $cameraIndex")
        }
        
        if (speedLimit > 0) {
            Log.d(TAG, "   道路限速: ${speedLimit}km/h")
            updateRoadSpeedLimit(speedLimit)
        }
        
        // 🎯 记录坐标数据分离信息
        if (carLatitude != 0.0 && carLongitude != 0.0) {
            Log.d(TAG, "🗺️ 高德导航坐标 -> vpPosPointLatNavi: $carLatitude, vpPosPointLonNavi: $carLongitude")
        }
        
        // 更新CarrotMan字段
        carrotManFields.value = carrotManFields.value.copy(
            // 基础导航信息
            szPosRoadName = currentRoad.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szPosRoadName,
            szNearDirName = nextRoad.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szNearDirName,
            szFarDirName = nextNextRoad.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szFarDirName, // 🎯 修复：从 NEXT_NEXT_ROAD_NAME 获取
            nRoadLimitSpeed = speedLimit.takeIf { it > 0 } ?: carrotManFields.value.nRoadLimitSpeed,
            nGoPosDist = remainDistance.takeIf { it > 0 } ?: carrotManFields.value.nGoPosDist,
            nGoPosTime = remainTime.takeIf { it > 0 } ?: carrotManFields.value.nGoPosTime,
            nPosSpeed = currentSpeed.toDouble(),
            nPosAngle = carDirection.toDouble(),
            totalDistance = routeAllDis,
            
            // 🎯 修复：转向和导航段信息，使用正确映射的 CarrotMan 转弯类型
            nTBTDist = segRemainDis, // 当前转弯距离
            nTBTTurnType = carrotTurnType, // 🎯 修复：使用映射后的 CarrotMan 转弯类型
            nTBTDistNext = nextSegRemainDis, // 🎯 修复：下一转弯距离，从 NEXT_SEG_REMAIN_DIS 获取
            nTBTTurnTypeNext = carrotNextTurnType, // 🎯 修复：使用映射后的下一个 CarrotMan 转弯类型
            xTurnInfo = getXTurnInfo(carrotTurnType), // 基于 CarrotMan 转弯类型计算 xTurnInfo
            xDistToTurn = segRemainDis,
            xTurnInfoNext = getXTurnInfo(carrotNextTurnType), // 下一个 xTurnInfo
            xDistToTurnNext = nextSegRemainDis,
            
            // 🎯 修复：基于图标生成的转向指令文本
            szTBTMainText = szTBTMainText,
            szTBTMainTextNext = szTBTMainTextNext,
            
            // 🎯 修复：导航类型和修饰符
            navType = navType,
            navModifier = navModifier,
            navTypeNext = navTypeNext,
            navModifierNext = navModifierNext,
            
            // 🎯 修复：位置信息分离 - 高德导航坐标专用于Navi字段
            vpPosPointLatNavi = carLatitude.takeIf { it != 0.0 } ?: carrotManFields.value.vpPosPointLatNavi,
            vpPosPointLonNavi = carLongitude.takeIf { it != 0.0 } ?: carrotManFields.value.vpPosPointLonNavi,
            // vpPosPointLat 和 vpPosPointLon 保留给手机GPS数据，不在这里更新
            
            // 🎯 目的地信息通过专门的函数处理，这里不重复更新
            goalPosX = carrotManFields.value.goalPosX,
            goalPosY = carrotManFields.value.goalPosY, 
            szGoalName = carrotManFields.value.szGoalName,
            
            // 道路和导航状态
            roadcate = mappedRoadcate,
            isNavigating = true,
            active_carrot = if (remainDistance > 0 || speedLimit > 0) 1 else carrotManFields.value.active_carrot,
            
            // SDI摄像头信息
            nSdiType = cameraType.takeIf { it >= 0 } ?: carrotManFields.value.nSdiType,
            nSdiSpeedLimit = cameraSpeed.takeIf { it > 0 } ?: carrotManFields.value.nSdiSpeedLimit,
            nSdiDist = cameraDist.takeIf { it > 0 } ?: carrotManFields.value.nSdiDist,
            
            // 🎯 修复：红绿灯数量信息
            traffic_light_count = trafficLightNum.takeIf { it >= 0 } ?: carrotManFields.value.traffic_light_count,
            
            // 导航GPS时间戳更新
            last_update_gps_time_navi = System.currentTimeMillis(),
            
            // 时间戳更新
            lastUpdateTime = System.currentTimeMillis(),
            dataQuality = "good"
        )
        
        // 🎯 更新各种倒计时计算
        updateTrafficCountdowns(segRemainDis, segRemainTime, remainDistance, remainTime, currentSpeed.toDouble())
        
        // 🎯 处理目的地信息并自动发送给comma3
        if (endPOIName.isNotEmpty() || endPOILatitude != 0.0 || endPOILongitude != 0.0) {
            handleDestinationInfo(intent)
        }
        
        updateSpeedControl()
    }

    /**
     * 🎯 根据转向图标生成详细的转向指令文本
     * 结合图标、距离和道路名称生成人性化的导航指令
     */
    private fun generateTurnInstructionText(iconCode: Int, distance: Int, roadName: String): String {
        val action = getTurnIconDescription(iconCode)
        val distanceText = when {
            distance <= 0 -> ""
            distance < 100 -> "${distance}米后"
            distance < 1000 -> "${distance}米后"
            else -> "${String.format(Locale.getDefault(), "%.1f", distance / 1000.0)}公里后"
        }
        
        val roadText = if (roadName.isNotEmpty()) "驶入$roadName" else ""
        
        return when {
            distanceText.isNotEmpty() && roadText.isNotEmpty() -> "$distanceText$action，$roadText"
            distanceText.isNotEmpty() -> "$distanceText$action"
            roadText.isNotEmpty() -> "$action，$roadText"
            else -> action
        }
    }

    /**
     * 🎯 获取导航类型描述
     */
    private fun getNaviTypeDescription(type: Int): String {
        return when (type) {
            0 -> "GPS导航"
            1 -> "模拟导航"
            2 -> "巡航"
            else -> "未知类型($type)"
        }
    }
    
    /**
     * 🎯 映射电子眼类型到描述 (基于高德地图官方CAMERA TYPE资料)
     */
    private fun mapCameraType(type: Int): String {
        return when (type) {
            0 -> "测速摄像头(限速拍照)"
            1 -> "监控摄像头(治安监控)"
            2 -> "闯红灯拍照(红绿灯路口)"
            3 -> "违章拍照(压线/禁停等)"
            4 -> "公交专用道摄像头(公交车道监控)"
            else -> "未知类型($type)"
        }
    }

    /**
     * 🎯 映射导航图标编号到描述 (基于高德地图官方ICON编号资料)
     */
    private fun mapNavigationIcon(iconId: Int): String {
        return when (iconId) {
            // 基础转向
            1 -> "直行"
            2 -> "左转"
            3 -> "右转"
            4 -> "左前方"
            5 -> "右前方"
            6 -> "左后方"
            7 -> "右后方"
            8 -> "左转掉头"
            9 -> "直行"
            10 -> "环岛经过"

            // 环岛系列
            11 -> "进入环岛(使用中)"
            12 -> "驶出环岛(使用中)"
            17 -> "进入环岛(废弃)"
            18 -> "驶出环岛(废弃)"

            // 特殊场景
            13 -> "到达服务区"
            14 -> "到达收费站"
            15 -> "到达目的地"
            16 -> "进入隧道"
            19 -> "右转掉头"
            20 -> "原可用后"

            // 红绿灯
            21 -> "靠右行驶"
            22 -> "靠右行驶"
            23 -> "左转+直行合并"
            24 -> "右转+直行合并"
            25 -> "四路入口"

            else -> "未知图标($iconId)"
        }
    }

    /**
     * 🎯 处理定位信息
     */
    private fun handleLocationInfo(intent: Intent) {
        // 从Intent中获取定位信息JSON字符串
        val locationInfoJson = intent.getStringExtra("EXTRA_LOCATION_INFO")
        if (locationInfoJson != null) {
            try {
                val json = JSONObject(locationInfoJson)
                val bearing = json.optDouble("bearing", 0.0)  // 方向角
                val accuracy = json.optDouble("accuracy", 0.0)  // 精度
                val speed = json.optDouble("speed", 0.0)  // 速度
                val time = json.optLong("time", 0L)  // 时间戳
                val provider = json.optString("provider", "")  // 定位提供者
                
                // 构建定位信息文本
                val locationInfo = buildString {
                    append("方向: ${bearing.toInt()}°")
                    if (speed > 0) {
                        append(" 速度: ${(speed * 3.6).toInt()}km/h")  // 转换为km/h
                    }
                    if (accuracy > 0) {
                        append(" 精度: ${accuracy.toInt()}m")
                    }
                    append(" 来源: $provider")
                }
                
                Log.i(TAG, "📍 定位信息: $locationInfo")
                
                // 更新UI和状态
                updateUI { locationInfo }
                
                // 更新CarrotMan字段
                carrotManFields.value = carrotManFields.value.copy(
                    // GPS位置相关字段
                    nPosAngle = bearing,  // 方向角
                    nPosSpeed = speed * 3.6,  // 速度，转换为km/h
                    gps_accuracy_device = accuracy,  // 精度
                    last_update_gps_time = time,  // 时间戳
                    source_last = provider,  // 定位提供者
                    gps_valid = true,  // GPS有效
                    // 更新最后更新时间和数据质量
                    lastUpdateTime = System.currentTimeMillis(),
                    dataQuality = if (accuracy <= 10) "good" else "fair"  // 根据精度判断数据质量
                )
            } catch (e: Exception) {
                Log.e(TAG, "解析定位信息失败", e)
            }
        }
    }

    /**
     * 🎯 处理转向信息
     */
    private fun handleTurnInfo(intent: Intent) {
        val turnType = intent.getIntExtra("TURN_TYPE", -1)
        val remainDis = intent.getIntExtra("REMAIN_DIS", 0)
        val nextRoadName = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
        val icon = intent.getIntExtra("ICON", -1)
        val segRemainDis = intent.getIntExtra("SEG_REMAIN_DIS", 0)
        val segRemainTime = intent.getIntExtra("SEG_REMAIN_TIME", 0)
        val naviAction = intent.getStringExtra("NAVI_ACTION") ?: ""

        // 取得 Amap 转向图标 ID
        val turnIcon = if (icon != -1) icon else turnType

        // 转成 CarrotMan 需要的 turnType 代码
        val carrotTurnCode = mapTurnIconToCarrotCode(turnIcon)

        val turnDesc = getTurnIconDescription(turnIcon)
        val distance = if (segRemainDis > 0) segRemainDis else remainDis

        Log.i(TAG, "↩️ 转向信息: $turnDesc 距离${distance}m")

        val turnText = "$turnDesc" // 可扩展加距离

        // 🎯 修复：使用更精确的 CarrotMan 映射，并保护现有的导航指令
        val carrotNavType = getTurnTypeAndModifier(carrotTurnCode)
        val carrotXTurnInfo = getXTurnInfo(carrotTurnCode)

        carrotManFields.value = carrotManFields.value.copy(
            // 距离
            nTBTDist = distance,
            xDistToTurn = distance,

            // 🎯 修复：使用正确的 CarrotMan 转弯类型和映射
            nTBTTurnType = carrotTurnCode,
            xTurnInfo = carrotXTurnInfo,
            navType = carrotNavType.first,
            navModifier = carrotNavType.second,

            // 🎯 修复：只在没有更详细指令时才使用简单转向文本
            szTBTMainText = if (carrotManFields.value.szTBTMainText.isEmpty() || 
                              carrotManFields.value.szTBTMainText.length < 10) turnText 
                           else carrotManFields.value.szTBTMainText,
            szNearDirName = nextRoadName.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szNearDirName,
            isNavigating = true,
            active_carrot = if (distance > 0) 1 else carrotManFields.value.active_carrot,
            lastUpdateTime = System.currentTimeMillis()
        )
        updateSpeedControl()
    }

    /**
     * 将 Amap ICON 映射到 CarrotMan 使用的 nTBTTurnType 代码
     * 仅覆盖常用转向，其余保持原值以便后续调试
     */
    private fun mapTurnIconToCarrotCode(amapIcon: Int): Int {
        return when (amapIcon) {
            1 -> 51               // 直行 -> notification straight
            2 -> 12               // 左转（修正）
            3 -> 13               // 右转（修正）
            4 -> 102              // 左前方 -> off ramp slight left
            5 -> 101              // 右前方 -> off ramp slight right
            65 -> 1006            // 左辅道
            8, 9 -> 14            // 掉头
            6 -> 17               // 左后方 (近似)
            7 -> 19               // 右后方 (近似)
            else -> amapIcon      // 其余保持原 ID
        }
    }

    /**
     * 获取 Amap ICON 的中文描述
     */
    private fun getTurnIconDescription(icon: Int): String {
        return when (icon) {
            0 -> "通知"
            1 -> "直行"
            2 -> "左转"
            3 -> "右转"
            4 -> "左前方转弯"
            5 -> "右前方转弯"
            6 -> "左后方转弯"
            7 -> "右后方转弯"
            8 -> "左转掉头"
            9 -> "右转掉头"
            10 -> "靠左行驶"
            11 -> "靠右行驶"
            12 -> "进入十字路口"
            13 -> "离开十字路口"
            14 -> "高架入口"
            15 -> "过街天桥"
            16 -> "隧道"
            17 -> "通过广场"
            18 -> "其他"
            19 -> "隧道"
            20 -> "桥梁"
            21 -> "收费站"
            22 -> "服务区"
            23 -> "加油站"
            24 -> "停车场"
            65 -> "向左进入辅道"
            101 -> "向右进入辅道"
            1006 -> "靠左行驶"  // CarrotMan转弯类型1006: off ramp left
            1007 -> "靠右行驶"  // CarrotMan转弯类型1007: off ramp right
            else -> "未知($icon)"
        }
    }

    /**
     * 🎯 处理导航状态
     */
    private fun handleNavigationStatus(intent: Intent) {
        val naviState = intent.getIntExtra("NAVI_STATE", -1)
        val stateDesc = when (naviState) {
            1 -> "准备导航"
            2 -> "导航中"
            3 -> "导航暂停"
            4 -> "导航结束"
            else -> "未知($naviState)"
        }
        
        Log.i(TAG, "🚗 导航状态: $stateDesc")
        
        carrotManFields.value = carrotManFields.value.copy(
            isNavigating = naviState == 2,
            active_carrot = if (naviState == 2) 1 else 0,
            // 🎯 修复：保持现有的导航指令和类型，只更新状态调试信息
            debugText = if (carrotManFields.value.debugText.startsWith("🚦")) 
                       carrotManFields.value.debugText + " | 导航状态: $stateDesc" 
                       else "导航状态: $stateDesc",
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * 🎯 处理路线信息
     */
    private fun handleRouteInfo(intent: Intent) {
        val routeRemainDis = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
        val routeRemainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)
        
        if (routeRemainDis > 0 || routeRemainTime > 0) {
            Log.i(TAG, "🛣️ 路线信息: 剩余${routeRemainDis}米, ${routeRemainTime}秒")
            
            carrotManFields.value = carrotManFields.value.copy(
                nGoPosDist = routeRemainDis,
                nGoPosTime = routeRemainTime,
                isNavigating = true,
                active_carrot = 1,
                lastUpdateTime = System.currentTimeMillis()
            )
        }
    }

    /**
     * 🎯 处理路线信息查询结果 (KEY_TYPE: 10056)
     */
    private fun handleRouteInfoQuery(intent: Intent) {
        val routeInfoJson = intent.getStringExtra("EXTRA_ROAD_INFO")

        if (routeInfoJson != null) {
            // 🎯 首先输出完整的原始 JSON 数据
            Log.i(TAG, "🗺️ KEY_TYPE: 10056 完整原始 JSON 数据:")
            Log.i(TAG, "=====================================")
            Log.i(TAG, routeInfoJson)
            Log.i(TAG, "=====================================")

            try {
                val json = JSONObject(routeInfoJson)

                // 解析起点信息
                val fromPoiName = json.optString("FromPoiName", "")
                val fromPoiAddr = json.optString("FromPoiAddr", "")
                val fromPoiLat = json.optDouble("FromPoiLatitude", 0.0)
                val fromPoiLon = json.optDouble("FromPoiLongitude", 0.0)

                // 解析终点信息
                val toPoiName = json.optString("ToPoiName", "")
                val toPoiAddr = json.optString("ToPoiAddr", "")
                val toPoiLat = json.optDouble("ToPoiLatitude", 0.0)
                val toPoiLon = json.optDouble("ToPoiLongitude", 0.0)

                // 解析路线基本信息
                val pathNum = json.optInt("pathNum", 0)
                val midPoisNum = json.optInt("midPoisNum", 0)

                // 解析途经点信息
                val midPoiArray = json.optJSONArray("midPoiArray")
                val midPoiList = mutableListOf<String>()
                if (midPoiArray != null) {
                    for (i in 0 until midPoiArray.length()) {
                        val midPoi = midPoiArray.getJSONObject(i)
                        val name = midPoi.optString("name", "")
                        val lat = midPoi.optDouble("latitude", 0.0)
                        val lon = midPoi.optDouble("longitude", 0.0)
                        if (name.isNotEmpty()) {
                            midPoiList.add("$name($lat,$lon)")
                        }
                    }
                }

                // 解析路线详情 (选择第一个推荐路线)
                var totalDistance = 0
                var totalTime = 0
                var streetNames = listOf<String>()
                var routeMethod = ""

                val pathInfoArray = json.optJSONArray("path_info")
                if (pathInfoArray != null && pathInfoArray.length() > 0) {
                    val firstPath = pathInfoArray.getJSONObject(0)
                    totalDistance = firstPath.optInt("distance", 0)
                    totalTime = firstPath.optInt("time", 0)
                    routeMethod = firstPath.optString("method", "")

                    val streetNamesArray = firstPath.optJSONArray("streetNames")
                    if (streetNamesArray != null) {
                        val streetList = mutableListOf<String>()
                        for (i in 0 until streetNamesArray.length()) {
                            streetList.add(streetNamesArray.getString(i))
                        }
                        streetNames = streetList
                    }
                }

                Log.i(TAG, "🗺️ 路线信息查询结果:")
                Log.i(TAG, "   起点: $fromPoiName ($fromPoiAddr)")
                Log.i(TAG, "   终点: $toPoiName ($toPoiAddr)")
                Log.i(TAG, "   路线方案: $pathNum 个, 推荐方案: $routeMethod")
                Log.i(TAG, "   总距离: ${totalDistance}米, 总时间: ${totalTime}秒")
                Log.i(TAG, "   中途点数: $midPoisNum")
                if (midPoiList.isNotEmpty()) {
                    Log.i(TAG, "   途经点: ${midPoiList.joinToString(", ")}")
                }
                if (streetNames.isNotEmpty()) {
                    Log.i(TAG, "   主要道路: ${streetNames.joinToString(" → ")}")
                }

                // 更新 CarrotMan 字段
                carrotManFields.value = carrotManFields.value.copy(
                    // 目标位置信息
                    goalPosY = toPoiLat,
                    goalPosX = toPoiLon,
                    szGoalName = toPoiName,

                    // 路线信息
                    nGoPosDist = totalDistance,
                    nGoPosTime = totalTime,

                    // 起点信息 (如果需要)
                    vpPosPointLat = if (fromPoiLat != 0.0) fromPoiLat else carrotManFields.value.vpPosPointLat,
                    vpPosPointLon = if (fromPoiLon != 0.0) fromPoiLon else carrotManFields.value.vpPosPointLon,

                    // 道路信息 (使用第一条主要道路)
                    szPosRoadName = if (streetNames.isNotEmpty()) streetNames[0] else carrotManFields.value.szPosRoadName,

                    // 更新时间
                    lastUpdateTime = System.currentTimeMillis()
                )

            } catch (e: Exception) {
                Log.e(TAG, "解析路线信息查询结果失败: ${e.message}", e)
                Log.e(TAG, "原始JSON数据: $routeInfoJson")
            }
        } else {
            Log.w(TAG, "路线信息查询结果为空")
        }
    }

    /**
     * 🎯 处理限速信息
     */
    private fun handleSpeedLimit(intent: Intent) {
        val speedLimit = intent.getIntExtra("LIMITED_SPEED", -1)
        if (speedLimit > 0) {
            Log.i(TAG, "🚸 限速信息: ${speedLimit}km/h")
            updateRoadSpeedLimit(speedLimit)
            updateSpeedControl()
        }
    }

    /**
     * 🎯 处理摄像头信息
     */
    private fun handleCameraInfo(intent: Intent) {
        // 首先尝试直接从Intent中获取CAMERA_DIST
        var cameraDist = intent.getIntExtra("CAMERA_DIST", -1)
        var sdiType = intent.getIntExtra("SDI_TYPE", -1)
        var speedLimit = intent.getIntExtra("SPEED_LIMIT", 0)
        
        // 解析JSON格式的摄像头信息
        val cameraInfoJson = intent.getStringExtra("CAMERA_INFO")
        if (cameraInfoJson != null) {
            try {
                val json = JSONObject(cameraInfoJson)
                // 如果直接获取失败，则从JSON中获取
                if (cameraDist == -1) {
                    cameraDist = json.optInt("distance", 0)
                }
                if (sdiType == -1) {
                    sdiType = json.optInt("type", -1)
                }
                if (speedLimit == 0) {
                    speedLimit = json.optInt("speed_limit", 0)
                }
                val isSection = json.optBoolean("is_section", false)
                val sectionLength = json.optInt("section_length", 0)
                
                // 构建摄像头信息文本
                val cameraInfo = buildString {
                    append(getSdiTypeDescription(sdiType))
                    if (cameraDist > 0) {
                        append(" ${cameraDist}米")
                    }
                    if (speedLimit > 0) {
                        append(" 限速${speedLimit}km/h")
                    }
                    if (isSection) {
                        append(" 区间测速")
                        if (sectionLength > 0) {
                            append(" 长度${sectionLength}米")
                        }
                    }
                }
                
                Log.i(TAG, "📸 摄像头信息: $cameraInfo")
                
                // 更新UI和状态
                updateUI { cameraInfo }
                
                // 更新状态字段
                carrotManFields.value = carrotManFields.value.copy(
                    nSdiType = sdiType,
                    nSdiSpeedLimit = speedLimit,
                    nSdiDist = cameraDist,
                    nSdiSection = if (isSection) sectionLength else 0,
                    lastUpdateTime = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "解析摄像头信息失败", e)
            }
        }
    }

    /**
     * 🎯 处理红绿灯信息
     */
    private fun handleTrafficLightInfo(intent: Intent) {
        // 从Intent中直接获取红绿灯信息，兼容多种字段名称
        val status = when {
            intent.hasExtra("trafficLightStatus") -> intent.getIntExtra("trafficLightStatus", 0)
            intent.hasExtra("LIGHT_STATUS") -> intent.getIntExtra("LIGHT_STATUS", 0)
            else -> 0
        }

        val direction = when {
            intent.hasExtra("dir") -> intent.getIntExtra("dir", 0)
            intent.hasExtra("LIGHT_DIRECTION") -> intent.getIntExtra("LIGHT_DIRECTION", 0)
            intent.hasExtra("direction") -> intent.getIntExtra("direction", 0)
            else -> 0
        }

        val countdown = when {
            intent.hasExtra("redLightCountDownSeconds") -> intent.getIntExtra("redLightCountDownSeconds", 0)
            intent.hasExtra("COUNTDOWN") -> intent.getIntExtra("COUNTDOWN", 0)
            else -> 0
        }

        val distance = when {
            intent.hasExtra("TRAFFIC_LIGHT_DIST") -> intent.getIntExtra("TRAFFIC_LIGHT_DIST", 0)
            intent.hasExtra("DISTANCE") -> intent.getIntExtra("DISTANCE", 0)
            else -> 0
        }
        
        // 用于最终展示的变量，可被 JSON 数据补全
        var jsonStatus = status
        var jsonDirection = direction
        var jsonCountdown = countdown
        var jsonDistance = distance
        
        // 如果 JSON 数据存在, 尝试补全字段
        intent.getStringExtra("TRAFFIC_LIGHT_INFO")?.let { jsonStr ->
            try {
                val json = JSONObject(jsonStr)
                if (status == 0) jsonStatus = json.optInt("status", status)
                if (direction == 0) jsonDirection = json.optInt("direction", direction)
                if (countdown == 0) jsonCountdown = json.optInt("countdown", countdown)
                if (distance == 0) jsonDistance = json.optInt("distance", distance)
            } catch (_: Exception) { /* ignore */ }
        }

        // 构建描述文本
        val trafficLightInfo = buildString {
            append(getTrafficLightStatusDescription(jsonStatus))
            if (jsonDirection > 0) {
                append(" ${getTrafficLightDirectionDescription(jsonDirection)}")
            }
            if (jsonCountdown > 0) {
                append(" ${jsonCountdown}秒")
            }
            if (jsonDistance > 0) {
                append(" ${jsonDistance}米")
            }
        }

        Log.i(TAG, "🚦 红绿灯信息: $trafficLightInfo")

        // 更新UI展示
        updateUI { trafficLightInfo }

        // 根据 CarrotMan 协议将状态值映射到 1=红灯/黄灯, 2=绿灯, 3=左转箭头
        val carrotTrafficState = when (jsonStatus) {
            1 -> 1          // 红灯
            2, -1 -> 1     // 黄灯
            4, 0 -> 2      // 绿灯
            else -> 0       // 其它/未知
        }

        // 修正高德广播天然 1~2 s 延迟，预测当前剩余秒
        val correctedCountdown = (jsonCountdown - 1).coerceAtLeast(0)

        // 🎯 修复：更新状态字段，但不覆盖 szTBTMainText（保留导航指令）
        carrotManFields.value = carrotManFields.value.copy(
            // 🎯 修复：正确设置倒计时字段
            left_sec = correctedCountdown,               // 综合倒计时
            carrot_left_sec = correctedCountdown,        // CarrotMan倒计时
            left_tbt_sec = correctedCountdown,           // 交通灯倒计时
            max_left_sec = if (correctedCountdown > carrotManFields.value.max_left_sec) correctedCountdown else carrotManFields.value.max_left_sec,
            
            // 🎯 修复：交通状态，但保持红绿灯数量不变（应该来自TRAFFIC_LIGHT_NUM）
            traffic_state = carrotTrafficState,
            // traffic_light_count 保持现有值，不用倒计时覆盖
            
            // 🎯 修复：不覆盖导航距离，只有在没有导航距离时才使用红绿灯距离
            nTBTDist = if (jsonDistance > 0 && carrotManFields.value.nTBTDist <= 0) jsonDistance else carrotManFields.value.nTBTDist,
            xDistToTurn = if (jsonDistance > 0 && carrotManFields.value.xDistToTurn <= 0) jsonDistance else carrotManFields.value.xDistToTurn,
            
            // 🎯 修复：不覆盖 szTBTMainText，将红绿灯信息存储到 debugText 中以便调试
            debugText = "🚦 $trafficLightInfo",
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * 🎯 获取SDI类型描述 (基于高德地图官方CAMERA TYPE资料)
     */
    private fun getSdiTypeDescription(type: Int): String {
        return when (type) {
            0 -> "测速摄像头(限速拍照)"
            1 -> "监控摄像头(治安监控)"
            2 -> "闯红灯拍照(红绿灯路口)"
            3 -> "违章拍照(压线/禁停等)"
            4 -> "公交专用道摄像头(公交车道监控)"
            -1 -> "无摄像头"
            else -> "类型$type"
        }
    }

    /**
     * 🎯 获取红绿灯状态描述
     */
    private fun getTrafficLightStatusDescription(status: Int): String {
        return when (status) {
            1 -> "红灯"        // Red
            2 -> "黄灯"        // Yellow
            4 -> "绿灯"        // Light off 表示通行
            0 -> "绿灯"        // 有些版本用 0 表示 Green
            -1 -> "黄灯"       // 未知(-1) 时常用作 Yellow
            3 -> "闪烁"        // Flashing
            else -> "未知($status)"
        }
    }

    /**
     * 🎯 获取红绿灯方向描述
     */
    private fun getTrafficLightDirectionDescription(direction: Int): String {
        return when (direction) {
            0 -> "未知"
            1 -> "直行"
            2 -> "右转"
            3 -> "左转"
            4 -> "左转掉头"
            5 -> "右转掉头"
            else -> "方向$direction"
        }
    }

    /**
     * 🎯 解析导航引导内容
     */
    private fun parseGuideInfoContent(intent: Intent): String {
        val currentRoad = intent.getStringExtra("CUR_ROAD_NAME") ?: ""
        val nextRoad = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
        val remainDistance = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
        val remainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)
        val speedLimit = intent.getIntExtra("LIMITED_SPEED", -1)
        
        return buildString {
            if (currentRoad.isNotEmpty()) appendLine("当前道路: $currentRoad")
            if (nextRoad.isNotEmpty()) appendLine("下一道路: $nextRoad")
            if (remainDistance > 0) appendLine("剩余距离: ${remainDistance}米")
            if (remainTime > 0) appendLine("剩余时间: ${remainTime}秒")
            if (speedLimit > 0) appendLine("道路限速: ${speedLimit}km/h")
        }.trimEnd()
    }

    /**
     * 🎯 解析定位信息内容
     */
    private fun parseLocationInfoContent(intent: Intent): String {
        val latitude = intent.getDoubleExtra("CAR_LATITUDE", 0.0)
        val longitude = intent.getDoubleExtra("CAR_LONGITUDE", 0.0)
        val bearing = intent.getIntExtra("CAR_DIRECTION", -1)
        val speed = intent.getIntExtra("CUR_SPEED", 0)
        
        return buildString {
            if (latitude != 0.0 && longitude != 0.0) {
                appendLine("位置坐标: ($latitude, $longitude)")
            }
            if (bearing > 0) appendLine("车头方向: ${bearing}°")
            if (speed > 0) appendLine("当前速度: ${speed}km/h")
        }.trimEnd().takeIf { it.isNotEmpty() } ?: "位置信息为空"
    }

    /**
     * 🎯 解析转向信息内容
     */
    private fun parseTurnInfoContent(intent: Intent): String {
        val turnType = intent.getIntExtra("TURN_TYPE", -1)
        val remainDis = intent.getIntExtra("REMAIN_DIS", 0)
        val nextRoadName = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
        val icon = intent.getIntExtra("ICON", -1)
        val segRemainDis = intent.getIntExtra("SEG_REMAIN_DIS", 0)
        val segRemainTime = intent.getIntExtra("SEG_REMAIN_TIME", 0)
        val naviAction = intent.getStringExtra("NAVI_ACTION") ?: ""
        
        return buildString {
            // 获取转弯动作
            val turnAction = if (naviAction.isNotEmpty()) naviAction else mapTurnTypeToAction(turnType)
            val distance = if (segRemainDis > 0) segRemainDis else remainDis
            
            // 构建转弯提示
            if (distance > 0) {
                appendLine("距离: ${distance}米后")
            }
            appendLine("动作: $turnAction")
            
            // 添加道路信息
            if (nextRoadName.isNotEmpty()) {
                appendLine("进入: $nextRoadName")
            }
            
            // 添加时间信息
            if (segRemainTime > 0) {
                appendLine("预计用时: ${segRemainTime}秒")
            }
        }.trimEnd()
    }

    /**
     * 🎯 解析导航状态内容
     */
    private fun parseNavigationStatusContent(intent: Intent): String {
        val naviState = intent.getIntExtra("NAVI_STATE", -1)
        return when (naviState) {
            1 -> "导航状态: 准备导航"
            2 -> "导航状态: 导航中"
            3 -> "导航状态: 导航暂停"
            4 -> "导航状态: 导航结束"
            else -> "导航状态: 未知($naviState)"
        }
    }

    // 解析路线信息内容
    private fun parseRouteInfoContent(intent: Intent): String {
        val routeRemainDis = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
        val routeRemainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)
        val destinationName = intent.getStringExtra("DESTINATION_NAME") ?: ""
        
        return buildString {
            if (routeRemainDis > 0) appendLine("剩余距离: ${routeRemainDis}米")
            if (routeRemainTime > 0) appendLine("剩余时间: ${routeRemainTime}秒")
            if (destinationName.isNotEmpty()) appendLine("目的地: $destinationName")
        }.trimEnd().takeIf { it.isNotEmpty() } ?: "路线信息"
    }

    // 解析限速信息内容
    private fun parseSpeedLimitContent(intent: Intent): String {
        val limitedSpeed = intent.getIntExtra("LIMITED_SPEED", 0)
        return if (limitedSpeed > 0) "限速: ${limitedSpeed}km/h" else "限速信息"
    }

    // 解析地图状态内容
    private fun parseMapStateContent(intent: Intent): String {
        val state = intent.getIntExtra("EXTRA_STATE", -1)
        return when (state) {
            0 -> "开始运行"
            1 -> "初始化完成"
            2 -> "运行结束"
            3 -> "进入前台"
            4 -> "进入后台"
            5 -> "开始算路"
            6 -> "算路完成，成功"
            7 -> "算路完成，失败"
            8 -> "开始导航"
            9 -> "结束导航"
            39 -> "到达目的地"  // 🎯 新增：到达目的地状态
            else -> "未知状态($state)"
        }
    }

    // 解析电子眼信息内容
    private fun parseCameraInfoContent(intent: Intent): String {
        val cameraInfoJson = intent.getStringExtra("CAMERA_INFO")
        return if (cameraInfoJson != null) {
            try {
                val json = JSONObject(cameraInfoJson)
                val cameraType = json.optInt("type", -1)
                val speedLimit = json.optInt("speed_limit", 0)
                val distance = json.optInt("distance", 0)
                val isSection = json.optBoolean("is_section", false)
                val sectionLength = json.optInt("section_length", 0)
                
                buildString {
                    appendLine("类型: ${when(cameraType) {
                        0 -> "测速摄像头(限速拍照)"
                        1 -> "监控摄像头(治安监控)"
                        2 -> "闯红灯拍照(红绿灯路口)"
                        3 -> "违章拍照(压线/禁停等)"
                        4 -> "公交专用道摄像头(公交车道监控)"
                        else -> "未知类型($cameraType)"
                    }}")
                    if (speedLimit > 0) appendLine("限速: ${speedLimit}km/h")
                    if (distance > 0) appendLine("距离: ${distance}m")
                    if (isSection) appendLine("区间长度: ${sectionLength}m")
                }.trimEnd()
            } catch (e: Exception) {
                "解析电子眼信息失败: ${e.message}"
            }
        } else "无电子眼信息"
    }

    // 映射转弯类型到动作描述
    private fun mapTurnTypeToAction(turnType: Int): String {
        return when (turnType) {
            1 -> "直行"
            2 -> "左转"
            3 -> "右转"
            4 -> "掉头"
            5 -> "左前方"
            6 -> "右前方"
            7 -> "左后方"
            8 -> "右后方"
            9 -> "进入环岛"
            10 -> "驶出环岛"
            11 -> "进入主路"
            12 -> "进入辅路"
            13 -> "靠左行驶"
            14 -> "靠右行驶"
            15 -> "向左前方行驶"
            16 -> "向右前方行驶"
            17 -> "向左后方行驶"
            18 -> "向右后方行驶"
            19 -> "保持直行"
            20 -> "到达目的地"
            21 -> "经过收费站"
            22 -> "经过服务区"
            23 -> "经过加油站"
            24 -> "经过隧道"
            25 -> "经过人行横道"
            26 -> "经过过街天桥"
            27 -> "到达途经点"
            -1 -> "无转弯"
            else -> "继续行驶"
        }
    }

    // 更新广播数据到UI
    private fun updateBroadcastData(data: BroadcastData) {
        runOnUiThread {
            try {
                totalBroadcastCount.intValue += 1
                lastUpdateTime.longValue = System.currentTimeMillis()
                receiverStatus.value = "活跃 - 已接收 ${totalBroadcastCount.intValue} 条广播"
                broadcastDataList.add(0, data)
                if (broadcastDataList.size > 50) {
                    while (broadcastDataList.size > 50) {
                        broadcastDataList.removeAt(broadcastDataList.size - 1)
                    }
                }
                Log.d(TAG, "✅ 广播数据已更新: KEY_TYPE=${data.keyType}, 类型=${data.dataType}")
            } catch (e: Exception) {
                Log.e(TAG, "UI更新失败: ${e.message}", e)
            }
        }
    }

    // 处理收藏点结果 - handleFavoriteResult 已移至下方新版本实现

    // 处理行政区域信息
    private fun handleAdminArea(intent: Intent) {
        val adminAreaJson = intent.getStringExtra("EXTRA_ADMIN_AREA")
        if (adminAreaJson != null) {
            try {
                val json = JSONObject(adminAreaJson)
                val province = json.optString("province", "")
                val city = json.optString("city", "")
                val district = json.optString("district", "")
                val roadName = json.optString("road_name", "")
                
                Log.i(TAG, "🗺️ 行政区域信息:")
                Log.d(TAG, "   省份: $province")
                Log.d(TAG, "   城市: $city")
                Log.d(TAG, "   区县: $district")
                if (roadName.isNotEmpty()) Log.d(TAG, "   道路: $roadName")
                
                carrotManFields.value = carrotManFields.value.copy(
                    szPosRoadName = roadName.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szPosRoadName,
                    lastUpdateTime = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "解析行政区域信息失败: ${e.message}", e)
            }
        }
    }

    // 处理导航状态变化
    private fun handleNaviStatus(intent: Intent) {
        val status = intent.getIntExtra("NAVI_STATUS", -1)
        val remainDistance = intent.getIntExtra("REMAIN_DISTANCE", 0)
        val remainTime = intent.getIntExtra("REMAIN_TIME", 0)
        
        Log.i(TAG, "🚗 导航状态变化:")
        Log.d(TAG, "   状态: $status")
        Log.d(TAG, "   剩余距离: ${remainDistance}米")
        Log.d(TAG, "   剩余时间: ${remainTime}秒")
        
        carrotManFields.value = carrotManFields.value.copy(
            nGoPosDist = remainDistance.takeIf { it > 0 } ?: carrotManFields.value.nGoPosDist,
            nGoPosTime = remainTime.takeIf { it > 0 } ?: carrotManFields.value.nGoPosTime,
            isNavigating = status == 1,
            active_carrot = if (status == 1) 1 else 0,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * 🎯 处理路况信息
     */
    private fun handleTrafficInfo(intent: Intent) {
        val trafficStatus = intent.getIntExtra("TRAFFIC_STATUS", 0)
        val speedLimit = intent.getIntExtra("SPEED_LIMIT", 0)
        
        Log.i(TAG, "🚦 路况信息:")
        Log.d(TAG, "   交通状态: ${mapTrafficStatus(trafficStatus)}")
        if (speedLimit > 0) Log.d(TAG, "   限速: ${speedLimit}km/h")
        
        carrotManFields.value = carrotManFields.value.copy(
            traffic_state = trafficStatus,
            // nRoadLimitSpeed 交由 updateRoadSpeedLimit 统一处理
            lastUpdateTime = System.currentTimeMillis()
        )

        // 调用统一限速更新逻辑
        updateRoadSpeedLimit(speedLimit)
        updateSpeedControl()
    }

    // 处理导航态势信息
    private fun handleNaviSituation(intent: Intent) {
        val naviInfoJson = intent.getStringExtra("EXTRA_NAVI_INFO")
        if (naviInfoJson != null) {
            try {
                val json = JSONObject(naviInfoJson)
                val currentRoadSpeed = json.optInt("currentRoadSpeed", 0)
                val restDistance = json.optInt("restDistance", 0)
                val restTime = json.optInt("restTime", 0)
                val nextRoadName = json.optString("nextRoadName", "")
                val nextTurnType = json.optInt("nextTurnType", -1)
                val nextTurnDistance = json.optInt("nextTurnDistance", 0)
                
                Log.i(TAG, "🧭 导航态势信息:")
                Log.d(TAG, "   当前道路限速: ${currentRoadSpeed}km/h")
                Log.d(TAG, "   剩余距离: ${restDistance}米")
                Log.d(TAG, "   剩余时间: ${restTime}秒")
                Log.d(TAG, "   下一道路: $nextRoadName")
                Log.d(TAG, "   下一转弯类型: $nextTurnType")
                Log.d(TAG, "   下一转弯距离: ${nextTurnDistance}米")
                
                carrotManFields.value = carrotManFields.value.copy(
                    nRoadLimitSpeed = currentRoadSpeed.takeIf { it > 0 } ?: carrotManFields.value.nRoadLimitSpeed,
                    nGoPosDist = restDistance.takeIf { it > 0 } ?: carrotManFields.value.nGoPosDist,
                    nGoPosTime = restTime.takeIf { it > 0 } ?: carrotManFields.value.nGoPosTime,
                    szNearDirName = nextRoadName.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szNearDirName,
                    nTBTTurnTypeNext = nextTurnType.takeIf { it >= 0 } ?: carrotManFields.value.nTBTTurnTypeNext,
                    nTBTDistNext = nextTurnDistance.takeIf { it > 0 } ?: carrotManFields.value.nTBTDistNext,
                    isNavigating = true,
                    active_carrot = 1,
                    lastUpdateTime = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "解析导航态势信息失败: ${e.message}", e)
            }
        }
    }

    // 处理下一路口信息
    private fun handleNextIntersection(intent: Intent) {
        val nextRoadName = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
        val naviAction = intent.getStringExtra("NAVI_ACTION") ?: ""
        val naviIconId = intent.getIntExtra("NAVI_ICON_ID", -1)
        val naviDistance = intent.getIntExtra("NAVI_DISTANCE", -1)
        
        Log.i(TAG, "🛣️ 下一路口信息:")
        Log.d(TAG, "   道路名称: $nextRoadName")
        Log.d(TAG, "   导航动作: $naviAction")
        Log.d(TAG, "   图标ID: $naviIconId")
        Log.d(TAG, "   距离: ${naviDistance}米")
        
        val shouldUpdateMainText = naviAction.isNotEmpty() && 
                                  (carrotManFields.value.szTBTMainText.isEmpty() || 
                                   carrotManFields.value.szTBTMainText.length < naviAction.length)
        
        carrotManFields.value = carrotManFields.value.copy(
            szNearDirName = nextRoadName.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szNearDirName,
            szTBTMainText = if (shouldUpdateMainText) naviAction else carrotManFields.value.szTBTMainText,
            nTBTTurnType = naviIconId.takeIf { it >= 0 } ?: carrotManFields.value.nTBTTurnType,
            nTBTDist = naviDistance.takeIf { it >= 0 } ?: carrotManFields.value.nTBTDist,
            isNavigating = true,
            active_carrot = if (naviDistance > 0) 1 else carrotManFields.value.active_carrot,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    // 处理新版限速信息
    private fun handleSpeedLimitNew(intent: Intent) {
        val currentSpeedLimit = intent.getIntExtra("CURRENT_SPEED_LIMIT", -1)
        val currentRoadSegmentSpeedLimit = intent.getIntExtra("CURRENT_ROAD_SEGMENT_SPEED_LIMIT", -1)
        
        Log.i(TAG, "🚸 新版限速信息:")
        Log.d(TAG, "   当前限速: ${currentSpeedLimit}km/h")
        Log.d(TAG, "   当前路段限速: ${currentRoadSegmentSpeedLimit}km/h")
        
        val newSpeedLimit = currentSpeedLimit.takeIf { it > 0 } 
            ?: currentRoadSegmentSpeedLimit.takeIf { it > 0 } 
            ?: carrotManFields.value.nRoadLimitSpeed
        
        updateRoadSpeedLimit(newSpeedLimit)
        updateSpeedControl()
    }

    // 映射交通状态
    private fun mapTrafficStatus(status: Int): String {
        return when (status) {
            0 -> "未知"
            1 -> "畅通"
            2 -> "缓行"
            3 -> "拥堵"
            4 -> "严重拥堵"
            else -> "状态$status"
        }
    }

    // 解析收藏点结果内容
    private fun parseFavoriteResultContent(intent: Intent): String {
        val favoriteData = intent.getStringExtra("EXTRA_FAVORITE_MY_LOCATION")
        return if (favoriteData != null) {
            try {
                val json = JSONObject(favoriteData)
                val latitude = json.optDouble("latitude", 0.0)
                val longitude = json.optDouble("longitude", 0.0)
                val name = json.optString("name", "")
                buildString {
                    appendLine("名称: $name")
                    appendLine("位置: ($latitude, $longitude)")
                }.trimEnd()
            } catch (e: Exception) {
                "解析收藏点数据失败: ${e.message}"
            }
        } else "无收藏点数据"
    }

    // 解析行政区域信息内容
    private fun parseAdminAreaContent(intent: Intent): String {
        val adminAreaJson = intent.getStringExtra("EXTRA_ADMIN_AREA")
        return if (adminAreaJson != null) {
            try {
                val json = JSONObject(adminAreaJson)
                val province = json.optString("province", "")
                val city = json.optString("city", "")
                val district = json.optString("district", "")
                val roadName = json.optString("road_name", "")
                buildString {
                    appendLine("省份: $province")
                    appendLine("城市: $city")
                    appendLine("区县: $district")
                    if (roadName.isNotEmpty()) appendLine("道路: $roadName")
                }.trimEnd()
            } catch (e: Exception) {
                "解析行政区域信息失败: ${e.message}"
            }
        } else "无行政区域信息"
    }

    // 解析导航状态变化内容
    private fun parseNaviStatusContent(intent: Intent): String {
        val status = intent.getIntExtra("NAVI_STATUS", -1)
        val remainDistance = intent.getIntExtra("REMAIN_DISTANCE", 0)
        val remainTime = intent.getIntExtra("REMAIN_TIME", 0)
        return buildString {
            appendLine("状态: ${if (status == 1) "导航中" else "未导航"}")
            if (remainDistance > 0) appendLine("剩余距离: ${remainDistance}米")
            if (remainTime > 0) appendLine("剩余时间: ${remainTime}秒")
        }.trimEnd()
    }

    // 解析路况信息内容
    private fun parseTrafficInfoContent(intent: Intent): String {
        val trafficStatus = intent.getIntExtra("TRAFFIC_STATUS", 0)
        val speedLimit = intent.getIntExtra("SPEED_LIMIT", 0)
        return buildString {
            appendLine("交通状态: ${mapTrafficStatus(trafficStatus)}")
            if (speedLimit > 0) appendLine("限速: ${speedLimit}km/h")
        }.trimEnd()
    }

    // 解析导航态势信息内容
    private fun parseNaviSituationContent(intent: Intent): String {
        val naviInfoJson = intent.getStringExtra("EXTRA_NAVI_INFO")
        return if (naviInfoJson != null) {
            try {
                val json = JSONObject(naviInfoJson)
                val currentRoadSpeed = json.optInt("currentRoadSpeed", 0)
                val restDistance = json.optInt("restDistance", 0)
                val restTime = json.optInt("restTime", 0)
                val nextRoadName = json.optString("nextRoadName", "")
                buildString {
                    if (currentRoadSpeed > 0) appendLine("当前道路限速: ${currentRoadSpeed}km/h")
                    if (restDistance > 0) appendLine("剩余距离: ${restDistance}米")
                    if (restTime > 0) appendLine("剩余时间: ${restTime}秒")
                    if (nextRoadName.isNotEmpty()) appendLine("下一道路: $nextRoadName")
                }.trimEnd()
            } catch (e: Exception) {
                "解析导航态势信息失败: ${e.message}"
            }
        } else "无导航态势信息"
    }

    /**
     * 🎯 解析下一路口信息内容
     */
    private fun parseNextIntersectionContent(intent: Intent): String {
        val nextRoadName = intent.getStringExtra("NEXT_ROAD_NAME") ?: ""
        val naviAction = intent.getStringExtra("NAVI_ACTION") ?: ""
        val naviDistance = intent.getIntExtra("NAVI_DISTANCE", -1)
        return buildString {
            if (nextRoadName.isNotEmpty()) appendLine("道路名称: $nextRoadName")
            if (naviAction.isNotEmpty()) appendLine("导航动作: $naviAction")
            if (naviDistance >= 0) appendLine("距离: ${naviDistance}米")
        }.trimEnd().takeIf { it.isNotEmpty() } ?: "无下一路口信息"
    }

    /**
     * 🎯 解析新版限速信息内容
     */
    private fun parseSpeedLimitNewContent(intent: Intent): String {
        val currentSpeedLimit = intent.getIntExtra("CURRENT_SPEED_LIMIT", -1)
        val currentRoadSegmentSpeedLimit = intent.getIntExtra("CURRENT_ROAD_SEGMENT_SPEED_LIMIT", -1)
        return buildString {
            if (currentSpeedLimit > 0) appendLine("当前限速: ${currentSpeedLimit}km/h")
            if (currentRoadSegmentSpeedLimit > 0) appendLine("当前路段限速: ${currentRoadSegmentSpeedLimit}km/h")
        }.trimEnd().takeIf { it.isNotEmpty() } ?: "无限速信息"
    }

    /**
     * 🎯 处理服务区信息
     */
    private fun handleSapaInfo(intent: Intent) {
        val sapaName = intent.getStringExtra("SAPA_NAME") ?: ""
        val sapaDist = intent.getIntExtra("SAPA_DIST", -1)
        val sapaType = intent.getIntExtra("SAPA_TYPE", -1)
        
        if (sapaDist > 0) {
            Log.i(TAG, "⛽ 服务区信息:")
            Log.d(TAG, "   名称: $sapaName")
            Log.d(TAG, "   距离: ${sapaDist}米")
            Log.d(TAG, "   类型: ${mapSapaType(sapaType)}")
            
            carrotManFields.value = carrotManFields.value.copy(
                szNearDirName = if (sapaName.isNotEmpty()) "服务区: $sapaName" else carrotManFields.value.szNearDirName,
                nTBTDist = sapaDist,
                nTBTTurnType = when (sapaType) {
                    0 -> 22  // 服务区
                    1 -> 21  // 收费站
                    2 -> 23  // 加油站
                    3 -> 24  // 停车场
                    else -> carrotManFields.value.nTBTTurnType
                },
                lastUpdateTime = System.currentTimeMillis()
            )
        }
    }

    /**
     * 🎯 映射服务区类型
     */
    private fun mapSapaType(type: Int): String {
        return when (type) {
            0 -> "服务区"
            1 -> "收费站"
            2 -> "加油站"
            3 -> "停车场"
            4 -> "其他"
            else -> "未知类型($type)"
        }
    }

    /**
     * 🎯 解析服务区信息内容
     */
    private fun parseSapaInfoContent(intent: Intent): String {
        val sapaName = intent.getStringExtra("SAPA_NAME") ?: ""
        val sapaDist = intent.getIntExtra("SAPA_DIST", -1)
        val sapaType = intent.getIntExtra("SAPA_TYPE", -1)
        return buildString {
            if (sapaName.isNotEmpty()) appendLine("名称: $sapaName")
            if (sapaDist > 0) appendLine("距离: ${sapaDist}米")
            if (sapaType >= 0) appendLine("类型: ${mapSapaType(sapaType)}")
        }.trimEnd().takeIf { it.isNotEmpty() } ?: "无服务区信息"
    }

    /**
     * 🎯 解析红绿灯信息内容
     */
    private fun parseTrafficLightContent(intent: Intent): String {
        val status = when {
            intent.hasExtra("trafficLightStatus") -> intent.getIntExtra("trafficLightStatus", 0)
            intent.hasExtra("LIGHT_STATUS") -> intent.getIntExtra("LIGHT_STATUS", 0)
            else -> 0
        }

        val direction = when {
            intent.hasExtra("dir") -> intent.getIntExtra("dir", 0)
            intent.hasExtra("LIGHT_DIRECTION") -> intent.getIntExtra("LIGHT_DIRECTION", 0)
            intent.hasExtra("direction") -> intent.getIntExtra("direction", 0)
            else -> 0
        }

        val countdown = when {
            intent.hasExtra("redLightCountDownSeconds") -> intent.getIntExtra("redLightCountDownSeconds", 0)
            intent.hasExtra("COUNTDOWN") -> intent.getIntExtra("COUNTDOWN", 0)
            else -> 0
        }

        val distance = when {
            intent.hasExtra("TRAFFIC_LIGHT_DIST") -> intent.getIntExtra("TRAFFIC_LIGHT_DIST", 0)
            intent.hasExtra("DISTANCE") -> intent.getIntExtra("DISTANCE", 0)
            else -> 0
        }
        
        // 用于最终展示的变量，可被 JSON 数据补全
        var jsonStatus = status
        var jsonDirection = direction
        var jsonCountdown = countdown
        var jsonDistance = distance
        
        // 如果 JSON 数据存在, 尝试补全字段
        intent.getStringExtra("TRAFFIC_LIGHT_INFO")?.let { jsonStr ->
            try {
                val json = JSONObject(jsonStr)
                if (status == 0) jsonStatus = json.optInt("status", status)
                if (direction == 0) jsonDirection = json.optInt("direction", direction)
                if (countdown == 0) jsonCountdown = json.optInt("countdown", countdown)
                if (distance == 0) jsonDistance = json.optInt("distance", distance)
            } catch (_: Exception) { /* ignore */ }
        }

        // 构建并返回描述文本
        return buildString {
            appendLine("状态: ${getTrafficLightStatusDescription(jsonStatus)}")
            appendLine("方向: ${getTrafficLightDirectionDescription(jsonDirection)}")
            if (jsonCountdown > 0) appendLine("倒计时: ${jsonCountdown}秒")
            if (jsonDistance > 0) appendLine("距离: ${jsonDistance}米")
        }.trimEnd()
    }

    /**
     * 🎯 更新UI显示
     */
    private fun updateUI(messageProvider: () -> String) {
        runOnUiThread {
            val message = messageProvider()
            // 更新UI组件
            // TODO: 根据实际UI组件进行更新
            Log.d(TAG, "UI更新: $message")
        }
    }

    /**
     * 🎯 获取广播类型描述
     */
    private fun getBroadcastTypeDescription(type: Int): String {
        return when (type) {
            KEY_TYPE_ROUTE_INFO -> "路线信息"
                            KEY_TYPE_NAVI_SITUATION -> "导航信息"
            KEY_TYPE_TRAFFIC_LIGHT -> "红绿灯信息"
            KEY_TYPE_CAMERA_INFO -> "摄像头信息"
            KEY_TYPE_SAPA_INFO -> "服务区信息"
            KEY_TYPE_SDI_PLUS_INFO -> "SDI Plus 信息"
            else -> "类型$type"
        }
    }

    /**
     * 🎯 解析广播内容
     */
    private fun parseBroadcastContent(intent: Intent): String {
        return buildString {
            val type = intent.getIntExtra("TYPE", -1)
            appendLine("类型: ${getBroadcastTypeDescription(type)}")
            
            when (type) {
                KEY_TYPE_LOCATION_INFO -> {
                    val locationInfoJson = intent.getStringExtra("EXTRA_LOCATION_INFO")
                    if (locationInfoJson != null) {
                        try {
                            val json = JSONObject(locationInfoJson)
                            val bearing = json.optDouble("bearing", 0.0)
                            val speed = json.optDouble("speed", 0.0)
                            val accuracy = json.optDouble("accuracy", 0.0)
                            
                            appendLine("方向: ${bearing.toInt()}°")
                            if (speed > 0) {
                                appendLine("速度: ${(speed * 3.6).toInt()}km/h")
                            }
                            if (accuracy > 0) {
                                appendLine("精度: ${accuracy.toInt()}m")
                            }
                            appendLine("来源: ${json.optString("provider", "")}")
                        } catch (e: Exception) {
                            appendLine("解析失败: ${e.message}")
                        }
                    }
                }
                KEY_TYPE_TRAFFIC_LIGHT -> {
                    appendLine("状态: ${getTrafficLightStatusDescription(intent.getIntExtra("trafficLightStatus", 0))}")
                    appendLine("方向: ${getTrafficLightDirectionDescription(intent.getIntExtra("dir", 0))}")
                    val countdown = intent.getIntExtra("redLightCountDownSeconds", 0)
                    if (countdown > 0) appendLine("倒计时: ${countdown}秒")
                }
                KEY_TYPE_CAMERA_INFO_V2 -> {
                    appendLine(parseCameraInfoV2Content(intent))
                }
                // ... 其他类型的处理 ...
            }
        }.trimEnd()
    }

    /**
     * 🎯 处理 SDI Plus 信息 (KEY_TYPE=10007)
     */
    private fun handleSdiPlusInfo(intent: Intent) {
        // 支持直接字段或 JSON
        var sdiType = intent.getIntExtra("SDI_TYPE", -1)
        var speedLimit = intent.getIntExtra("SPEED_LIMIT", 0)
        var distance = intent.getIntExtra("SDI_DIST", 0)
        val jsonStr = intent.getStringExtra("SDI_PLUS_INFO") ?: intent.getStringExtra("SDI_INFO")
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val json = JSONObject(jsonStr)
                if (sdiType == -1) sdiType = json.optInt("type", -1)
                if (speedLimit == 0) speedLimit = json.optInt("speed_limit", 0)
                if (distance == 0) distance = json.optInt("distance", 0)
            } catch (_: Exception) { }
        }

        val sdiDesc = getSdiTypeDescription(sdiType)
        val infoText = buildString {
            append(sdiDesc)
            if (distance > 0) append(" ${distance}米")
            if (speedLimit > 0) append(" 限速${speedLimit}km/h")
        }
        Log.i(TAG, "📸 SDI Plus: $infoText")
        updateUI { infoText }

        carrotManFields.value = carrotManFields.value.copy(
            nSdiPlusType = sdiType,
            nSdiPlusSpeedLimit = speedLimit,
            nSdiPlusDist = distance,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /** 解析 SDI Plus 广播内容 */
    private fun parseSdiPlusInfoContent(intent: Intent): String {
        val sdiType = intent.getIntExtra("SDI_TYPE", -1)
        val speedLimit = intent.getIntExtra("SPEED_LIMIT", 0)
        val distance = intent.getIntExtra("SDI_DIST", 0)
        return buildString {
            appendLine("类型: ${getSdiTypeDescription(sdiType)}")
            if (speedLimit > 0) appendLine("限速: ${speedLimit}km/h")
            if (distance > 0) appendLine("距离: ${distance}米")
        }.trimEnd()
    }

    /** 解析路线信息查询结果内容 (KEY_TYPE: 10056) */
    private fun parseRouteInfoQueryContent(intent: Intent): String {
        val routeInfoJson = intent.getStringExtra("EXTRA_ROAD_INFO")

        if (routeInfoJson != null) {
            // 🎯 在界面显示中也包含原始数据的前部分
            val jsonPreview = if (routeInfoJson.length > 200) {
                routeInfoJson.take(200) + "..."
            } else {
                routeInfoJson
            }

            try {
                val json = JSONObject(routeInfoJson)

                val fromPoiName = json.optString("FromPoiName", "")
                val toPoiName = json.optString("ToPoiName", "")
                val pathNum = json.optInt("pathNum", 0)
                val midPoisNum = json.optInt("midPoisNum", 0)

                // 解析途经点
                val midPoiArray = json.optJSONArray("midPoiArray")
                val midPoiList = mutableListOf<String>()
                if (midPoiArray != null) {
                    for (i in 0 until midPoiArray.length()) {
                        val midPoi = midPoiArray.getJSONObject(i)
                        val name = midPoi.optString("name", "")
                        if (name.isNotEmpty()) {
                            midPoiList.add(name)
                        }
                    }
                }

                // 解析第一个路线方案
                var totalDistance = 0
                var totalTime = 0
                var routeMethod = ""
                var streetNames = listOf<String>()

                val pathInfoArray = json.optJSONArray("path_info")
                if (pathInfoArray != null && pathInfoArray.length() > 0) {
                    val firstPath = pathInfoArray.getJSONObject(0)
                    totalDistance = firstPath.optInt("distance", 0)
                    totalTime = firstPath.optInt("time", 0)
                    routeMethod = firstPath.optString("method", "")

                    val streetNamesArray = firstPath.optJSONArray("streetNames")
                    if (streetNamesArray != null) {
                        val streetList = mutableListOf<String>()
                        for (i in 0 until streetNamesArray.length()) {
                            streetList.add(streetNamesArray.getString(i))
                        }
                        streetNames = streetList
                    }
                }

                return buildString {
                    appendLine("📋 原始JSON数据预览:")
                    appendLine(jsonPreview)
                    appendLine("")
                    appendLine("📊 解析结果:")
                    if (fromPoiName.isNotEmpty()) appendLine("起点: $fromPoiName")
                    if (toPoiName.isNotEmpty()) appendLine("终点: $toPoiName")
                    if (pathNum > 0) appendLine("路线方案: $pathNum 个")
                    if (routeMethod.isNotEmpty()) appendLine("推荐方案: $routeMethod")
                    if (totalDistance > 0) appendLine("总距离: ${totalDistance}米")
                    if (totalTime > 0) appendLine("总时间: ${totalTime}秒")
                    if (midPoisNum > 0) appendLine("中途点数: $midPoisNum")
                    if (midPoiList.isNotEmpty()) {
                        appendLine("途经点: ${midPoiList.joinToString(", ")}")
                    }
                    if (streetNames.isNotEmpty()) {
                        appendLine("主要道路: ${streetNames.take(3).joinToString(" → ")}")
                    }
                }.trimEnd()

            } catch (e: Exception) {
                return "路线信息解析失败: ${e.message}"
            }
        }

        return "路线信息查询结果"
    }

    /** 新版电子眼信息处理 (KEY_TYPE=100001) */
    private fun handleCameraInfoV2(intent: Intent) {
        val distance = intent.getIntExtra("CAMERA_DIST", -1)
        val type = intent.getIntExtra("CAMERA_TYPE", -1)
        val speedLimit = intent.getIntExtra("CAMERA_SPEED", 0)
        val camIndex = intent.getIntExtra("CAMERA_INDEX", -1)

        val desc = buildString {
            append(getSdiTypeDescription(type))
            if (distance >= 0) append(" ${distance}米")
            if (speedLimit > 0) append(" 限速${speedLimit}km/h")
            if (camIndex >= 0) append(" #$camIndex")
        }
        Log.i(TAG, "📸 电子眼V2: $desc")
        updateUI { desc }

        carrotManFields.value = carrotManFields.value.copy(
            nSdiType = type,
            nSdiSpeedLimit = speedLimit,
            nSdiDist = distance,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    private fun parseCameraInfoV2Content(intent: Intent): String {
        val distance = intent.getIntExtra("CAMERA_DIST", -1)
        val type = intent.getIntExtra("CAMERA_TYPE", -1)
        val speedLimit = intent.getIntExtra("CAMERA_SPEED", 0)
        val camIndex = intent.getIntExtra("CAMERA_INDEX", -1)
        return buildString {
            appendLine("类型: ${getSdiTypeDescription(type)}")
            if (distance >= 0) appendLine("距离: ${distance}米")
            appendLine("编号: $camIndex")
            if (speedLimit > 0) appendLine("限速: ${speedLimit}km/h")
        }.trimEnd()
    }

    /**
     * 底部导航按钮组件
     */
    @Composable
    private fun NavigationButtons(
        onNavigateToHome: () -> Unit,
        onNavigateToCompany: () -> Unit,
        onToggleOpenpilotCard: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onNavigateToHome,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "一键回家",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("一键回家")
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Button(
                onClick = onToggleOpenpilotCard,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "切换卡片",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("切换卡片")
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Button(
                onClick = onNavigateToCompany,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "导航搬砖",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导航搬砖")
                }
            }
        }
    }
    

    
    /**
     * 启动高德地图车机版
     */
    private fun launchAmapAuto() {
        try {
            // 高德地图车机版包名
            val pkgName = "com.autonavi.amapauto"
            
            // 尝试启动高德地图主界面
            val launchIntent = Intent().apply {
                setComponent(
                    ComponentName(
                        pkgName,
                        "com.autonavi.auto.MainMapActivity" // 主地图Activity
                    )
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            startActivity(launchIntent)
            Log.i(TAG, "已启动高德地图车机版")
            
            // 更新UI状态
            receiverStatus.value = "已启动高德地图车机版"
            
        } catch (e: Exception) {
            Log.e(TAG, "启动高德地图失败: ${e.message}", e)
            receiverStatus.value = "启动高德地图失败: ${e.message}"
            
            // 尝试使用隐式Intent启动
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.autonavi.amapauto")
                if (intent != null) {
                    startActivity(intent)
                    Log.i(TAG, "已通过隐式Intent启动高德地图车机版")
                    receiverStatus.value = "已启动高德地图车机版"
                } else {
                    receiverStatus.value = "未找到高德地图车机版应用"
                }
            } catch (e2: Exception) {
                Log.e(TAG, "隐式启动高德地图失败: ${e2.message}", e2)
                receiverStatus.value = "启动高德地图失败: ${e2.message}"
            }
        }
    }
    
    // 唤醒高德地图车机版应用 - 解决应用未启动时无法接收广播的问题
    private fun wakeUpAmapAuto() {
        try {
            val pkgName = "com.autonavi.amapauto"
            val launchIntent = Intent().apply {
                setComponent(
                    ComponentName(
                        pkgName,
                        "com.autonavi.auto.remote.fill.UsbFillActivity"
                    )
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(launchIntent)
            Log.i(TAG, "已尝试唤醒高德地图")
        } catch (e: Exception) {
            Log.e(TAG, "唤醒高德地图失败: ${e.message}", e)
        }
    }
    
    // 导航回家
    private fun navigateToHome() {
        try {
            wakeUpAmapAuto()
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent().apply {
                    action = ACTION_AMAP_RECV
                    putExtra("KEY_TYPE", KEY_TYPE_HOME_COMPANY_NAVIGATION)
                    putExtra("DEST", 0) // 0=回家
                    putExtra("IS_START_NAVI", 0) // 0=直接开始导航
                    putExtra("SOURCE_APP", "CarrotAmap")
                    flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                }
                sendBroadcast(intent)
                Log.i(TAG, "已发送一键回家的请求")
                receiverStatus.value = "已发送一键回家的请求"
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "一键回家失败: ${e.message}", e)
            receiverStatus.value = "一键回家失败: ${e.message}"
        }
    }
    
    // 导航去公司
    private fun navigateToCompany() {
        try {
            wakeUpAmapAuto()
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent().apply {
                    action = ACTION_AMAP_RECV
                    putExtra("KEY_TYPE", KEY_TYPE_HOME_COMPANY_NAVIGATION)
                    putExtra("DEST", 1) // 1=去公司
                    putExtra("IS_START_NAVI", 0) // 0=直接开始导航
                    putExtra("SOURCE_APP", "CarrotAmap")
                    flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                }
                sendBroadcast(intent)
                Log.i(TAG, "已发送导航搬砖的请求")
                receiverStatus.value = "已发送导航搬砖的请求"
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "导航搬砖失败: ${e.message}", e)
            receiverStatus.value = "导航搬砖失败: ${e.message}"
        }
    }

    // 将高德地图的 ROAD_TYPE 映射到 CarrotMan 的 roadcate
    private fun mapRoadTypeToRoadcate(roadType: Int): Int {
        return when (roadType) {
            0 -> 0  // 高速公路
            1 -> 2  // 国道
            2 -> 3  // 省道
            3 -> 4  // 县道
            4 -> 5  // 乡公路 -> 乡道
            5 -> 7  // 县乡村内部道路 -> 其他
            6 -> 1  // 主要大街、城市快速道 -> 城市快速路
            7, 8, 9 -> 6  // 主要道路/次要道路/普通道路 -> 街道
            10 -> 7  // 非导航道路 -> 其他
            else -> 8  // 未知
        }
    }
    
    // 获取道路类型描述
    private fun getRoadTypeDescription(roadType: Int): String {
        return when (roadType) {
            0 -> "高速公路"
            1 -> "国道"
            2 -> "省道"
            3 -> "县道"
            4 -> "乡公路"
            5 -> "县乡村内部道路"
            6 -> "主要大街、城市快速道"
            7 -> "主要道路"
            8 -> "次要道路"
            9 -> "普通道路"
            10 -> "非导航道路"
            else -> "未知道路类型"
        }
    }

    // 映射转弯图标到动作描述
    private fun mapTurnIconToAction(icon: Int): String {
        return when (icon) {
            0 -> "无转弯/通知"
            1 -> "直行"
            2 -> "左转"
            3 -> "右转"
            4 -> "左前方转弯"
            5 -> "右前方转弯"
            6 -> "左后方转弯"
            7 -> "右后方转弯"
            8 -> "左转掉头"
            9 -> "右转掉头"
            10 -> "靠左行驶"
            11 -> "靠右行驶"
            12 -> "进入环岛"
            13 -> "离开环岛"
            14 -> "通过人行横道"
            15 -> "通过过街天桥"
            16 -> "通过隧道"
            17 -> "通过广场"
            18 -> "通过其他"
            19 -> "通过隧道"
            20 -> "通过桥梁"
            21 -> "通过收费站"
            22 -> "通过服务区"
            23 -> "通过加油站"
            24 -> "通过停车场"
            65 -> "向左进入辅道"
            101 -> "向右进入辅道"
            1006 -> "靠左行驶"  // CarrotMan转弯类型1006: off ramp left
            1007 -> "靠右行驶"  // CarrotMan转弯类型1007: off ramp right
            else -> "未知图标($icon)"
        }
    }

    // 将高德地图的 ICON 映射到 CarrotMan 使用的 nTBTTurnType 代码
    private fun mapAmapIconToCarrotTurn(amapIcon: Int): Int {
        return when (amapIcon) {
            0 -> 51               // 无转弯/通知指令
            1 -> 51               // 直行
            2 -> 12               // 左转（修正）
            3 -> 13               // 右转（修正）
            4 -> 102              // 左前方 -> off ramp slight left
            5 -> 101              // 右前方 -> off ramp slight right

            // 🎯 修复靠左/靠右行驶映射错误
            10 -> 1006            // 靠左行驶 -> off ramp left (xTurnInfo=3)
            11 -> 1007            // 靠右行驶 -> off ramp right (xTurnInfo=4)

            65 -> 1006            // 左辅道
            8, 9 -> 14            // 掉头
            6 -> 17               // 左后方 (近似)
            7 -> 19               // 右后方 (近似)
            101 -> 1007           // 向右进入辅道

            // 🎯 修复隧道相关图标 - 确保隧道进入不触发转弯动作
            16, 19 -> 53          // 通过隧道 -> notification straight (不触发转弯控制)
            20 -> 54              // 通过桥梁 -> notification straight
            21 -> 55              // 通过收费站 -> notification straight
            22 -> 55              // 通过服务区 -> notification straight
            23 -> 55              // 通过加油站 -> notification straight
            24 -> 55              // 通过停车场 -> notification straight

            else -> amapIcon      // 其余保持原值，用于调试
        }
    }

    /**
     * 🎯 根据 CarrotMan 转弯类型获取导航类型和修饰符
     * 基于用户提供的正确 nav_type_mapping 映射关系
     */
    private fun getTurnTypeAndModifier(carrotTurnType: Int): Pair<String, String> {
        return when (carrotTurnType) {
            // 基本转弯
            12 -> Pair("turn", "left")              // 左转
            13 -> Pair("turn", "right")             // 右转
            16 -> Pair("turn", "sharp left")        // 急左转
            19 -> Pair("turn", "sharp right")       // 急右转
            14 -> Pair("turn", "uturn")             // 掉头
            1000 -> Pair("turn", "slight left")     // 轻微左转
            1001 -> Pair("turn", "slight right")    // 轻微右转

            // 分岔路口 (Fork)
            7 -> Pair("fork", "left")               // 左侧分岔
            6 -> Pair("fork", "right")              // 右侧分岔
            17 -> Pair("fork", "left")              // 左侧分岔
            44 -> Pair("fork", "left")              // 左侧分岔
            43 -> Pair("fork", "right")             // 右侧分岔
            75 -> Pair("fork", "left")              // 左侧分岔
            76 -> Pair("fork", "left")              // 左侧分岔
            73 -> Pair("fork", "right")             // 右侧分岔
            74 -> Pair("fork", "right")             // 右侧分岔
            117 -> Pair("fork", "right")            // 右侧分岔
            118 -> Pair("fork", "left")             // 左侧分岔
            123 -> Pair("fork", "right")            // 右侧分岔
            124 -> Pair("fork", "right")            // 右侧分岔
            1002 -> Pair("fork", "slight left")     // 轻微左侧分岔
            1003 -> Pair("fork", "slight right")    // 轻微右侧分岔

            // 出入口匝道 (Off Ramp)
            101 -> Pair("off ramp", "slight right") // 轻微右侧出口
            102 -> Pair("off ramp", "slight left")  // 轻微左侧出口
            104 -> Pair("off ramp", "slight right") // 轻微右侧出口
            105 -> Pair("off ramp", "slight left")  // 轻微左侧出口
            111 -> Pair("off ramp", "slight right") // 轻微右侧出口
            112 -> Pair("off ramp", "slight left")  // 轻微左侧出口
            114 -> Pair("off ramp", "slight right") // 轻微右侧出口
            115 -> Pair("off ramp", "slight left")  // 轻微左侧出口
            1006 -> Pair("off ramp", "left")        // 左侧出口
            1007 -> Pair("off ramp", "right")       // 右侧出口

            // 环岛 (Rotary/Roundabout)
            131 -> Pair("rotary", "slight right")   // 环岛轻微右转
            132 -> Pair("rotary", "slight right")   // 环岛轻微右转
            133 -> Pair("rotary", "right")          // 环岛右转
            134 -> Pair("rotary", "sharp right")    // 环岛急右转
            135 -> Pair("rotary", "sharp right")    // 环岛急右转
            136 -> Pair("rotary", "sharp left")     // 环岛急左转
            137 -> Pair("rotary", "sharp left")     // 环岛急左转
            138 -> Pair("rotary", "sharp left")     // 环岛急左转
            139 -> Pair("rotary", "left")           // 环岛左转
            140 -> Pair("rotary", "slight left")    // 环岛轻微左转
            141 -> Pair("rotary", "slight left")    // 环岛轻微左转
            142 -> Pair("rotary", "straight")       // 环岛直行

            // 特殊指令
            201 -> Pair("arrive", "straight")       // 到达目的地
            51 -> Pair("notification", "straight")  // 通知
            52 -> Pair("notification", "straight")  // 通知
            53 -> Pair("notification", "straight")  // 通知
            54 -> Pair("notification", "straight")  // 通知
            55 -> Pair("notification", "straight")  // 通知

            // TG (Traffic Gate) - 收费站
            153 -> Pair("", "")                     // TG
            154 -> Pair("", "")                     // TG
            249 -> Pair("", "")                     // TG

            else -> Pair("invalid", "")             // 未知类型
        }
    }

    /**
     * 🎯 根据 CarrotMan 转弯类型计算 xTurnInfo 代码
     * 基于用户提供的正确 xTurnInfo_mapping 映射关系
     *
     * xTurnInfo 含义：
     * 1: 左转           (left turn)
     * 2: 右转           (right turn)
     * 3: 左侧车道变更    (left lane change/fork)
     * 4: 右侧车道变更    (right lane change/fork)
     * 5: 环岛           (rotary)
     * 6: 收费站(TG)     (traffic gate)
     * 7: 掉头           (uturn)
     * 8: 到达目的地      (arrive)
     * 0: 通知           (notification)
     */
    private fun getXTurnInfo(carrotTurnType: Int): Int {
        return when (carrotTurnType) {
            // 左转 (xTurnInfo: 1)
            12 -> 1               // 左转
            13 -> 2               // 右转
            16 -> 1               // 急左转
            19 -> 2               // 急右转
            14 -> 7               // 掉头
            1000 -> 1             // 轻微左转
            1001 -> 2             // 轻微右转

            // 分岔路口 (Fork) - 左侧车道变更 (xTurnInfo: 3)
            7 -> 3                // 左侧分岔
            6 -> 4                // 右侧分岔
            17 -> 3               // 左侧分岔
            44 -> 3               // 左侧分岔
            43 -> 4               // 右侧分岔
            75 -> 3               // 左侧分岔
            76 -> 3               // 左侧分岔
            73 -> 4               // 右侧分岔
            74 -> 4               // 右侧分岔
            117 -> 4              // 右侧分岔
            118 -> 3              // 左侧分岔
            123 -> 4              // 右侧分岔
            124 -> 4              // 右侧分岔
            1002 -> 3             // 轻微左侧分岔
            1003 -> 4             // 轻微右侧分岔

            // 出入口匝道 (Off Ramp)
            101 -> 4              // 轻微右侧出口
            102 -> 3              // 轻微左侧出口
            104 -> 4              // 轻微右侧出口
            105 -> 3              // 轻微左侧出口
            111 -> 4              // 轻微右侧出口
            112 -> 3              // 轻微左侧出口
            114 -> 4              // 轻微右侧出口
            115 -> 3              // 轻微左侧出口
            1006 -> 3             // 左侧出口
            1007 -> 4             // 右侧出口

            // 环岛 (xTurnInfo: 5)
            131 -> 5              // 环岛轻微右转
            132 -> 5              // 环岛轻微右转
            133 -> 5              // 环岛右转
            134 -> 5              // 环岛急右转
            135 -> 5              // 环岛急右转
            136 -> 5              // 环岛急左转
            137 -> 5              // 环岛急左转
            138 -> 5              // 环岛急左转
            139 -> 5              // 环岛左转
            140 -> 5              // 环岛轻微左转
            141 -> 5              // 环岛轻微左转
            142 -> 5              // 环岛直行

            // 特殊指令
            201 -> 8              // 到达目的地
            51 -> null            // 通知 (返回 null，在调用处转换为 0 或其他默认值)
            52 -> null            // 通知
            53 -> null            // 通知
            54 -> null            // 通知
            55 -> null            // 通知

            // TG (Traffic Gate) - 收费站 (xTurnInfo: 6)
            153 -> 6              // TG
            154 -> 6              // TG
            249 -> 6              // TG
            21 -> 6               // 收费站 (从SAPA_INFO映射)

            // 服务区/加油站/停车场 (xTurnInfo: 0 - 通知)
            22 -> 0               // 服务区
            23 -> 0               // 加油站
            24 -> 0               // 停车场

            else -> -1            // 未知类型
        }?.let { it } ?: 0       // 将 null 转换为 0 (通知)
    }

    /**
     * 🎯 处理和验证目的地信息
     * 从高德地图获取目的地信息并自动发送给comma3设备
     */
    private fun handleDestinationInfo(intent: Intent) {
        // 从高德地图获取目的地信息
        val endPOIName = intent.getStringExtra("endPOIName") ?: ""
        val endPOIAddr = intent.getStringExtra("endPOIAddr") ?: ""
        val endPOILatitude = intent.getDoubleExtra("endPOILatitude", 0.0)
        val endPOILongitude = intent.getDoubleExtra("endPOILongitude", 0.0)
        
        // 获取导航路线信息
        val destinationName = intent.getStringExtra("DESTINATION_NAME") ?: endPOIName
        val routeRemainDis = intent.getIntExtra("ROUTE_REMAIN_DIS", 0)
        val routeRemainTime = intent.getIntExtra("ROUTE_REMAIN_TIME", 0)
        
        // 验证目的地信息有效性
        if (validateDestination(endPOILongitude, endPOILatitude, endPOIName)) {
            val currentDestination = carrotManFields.value
            
            // 检查目的地是否发生变化
            if (shouldUpdateDestination(
                    currentDestination.goalPosX, currentDestination.goalPosY, currentDestination.szGoalName,
                    endPOILongitude, endPOILatitude, endPOIName
                )) {
                
                Log.i(TAG, "🎯 目的地信息更新:")
                Log.d(TAG, "   名称: $endPOIName")
                Log.d(TAG, "   地址: $endPOIAddr") 
                Log.d(TAG, "   坐标: ($endPOILatitude, $endPOILongitude)")
                Log.d(TAG, "   剩余距离: ${routeRemainDis}米")
                Log.d(TAG, "   预计时间: ${routeRemainTime}秒")
                
                // 更新CarrotMan字段
                carrotManFields.value = carrotManFields.value.copy(
                    goalPosX = endPOILongitude,
                    goalPosY = endPOILatitude,
                    szGoalName = endPOIName.takeIf { it.isNotEmpty() } ?: destinationName,
                    nGoPosDist = routeRemainDis.takeIf { it > 0 } ?: carrotManFields.value.nGoPosDist,
                    nGoPosTime = routeRemainTime.takeIf { it > 0 } ?: carrotManFields.value.nGoPosTime,
                    lastUpdateTime = System.currentTimeMillis(),
                    dataQuality = "good"
                )
                
                // 🎯 自动发送目的地信息给comma3（修复坐标顺序：经度，纬度）
                sendDestinationToComma3(endPOILongitude, endPOILatitude, endPOIName, endPOIAddr)
                
                // 缓存目的地信息
                cacheDestination("current_destination", endPOILongitude, endPOILatitude, endPOIName)
                
                // 更新UI显示
                updateUI { "目的地已更新: $endPOIName" }
            }
        } else {
            Log.w(TAG, "⚠️ 目的地信息无效: 坐标($endPOILatitude, $endPOILongitude), 名称: $endPOIName")
        }
    }
    
    // 验证目的地坐标和信息的有效性
    private fun validateDestination(longitude: Double, latitude: Double, name: String): Boolean =
        com.example.carrotamap.validateDestination(longitude, latitude, name)
    
    // 检查是否需要更新目的地信息 - 避免频繁更新
    private fun shouldUpdateDestination(
        currentLon: Double, currentLat: Double, currentName: String,
        newLon: Double, newLat: Double, newName: String
    ): Boolean = com.example.carrotamap.shouldUpdateDestination(
        currentLon, currentLat, currentName, newLon, newLat, newName
    )
    
    // 计算两点间的距离（哈弗辛公式）
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        com.example.carrotamap.haversineDistance(lat1, lon1, lat2, lon2)
    
    // 自动发送目的地信息给comma3设备
    private fun sendDestinationToComma3(longitude: Double, latitude: Double, name: String, address: String = "") {
        if (::carrotNetworkClient.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    carrotNetworkClient.sendDestinationUpdate(
                        goalPosX = longitude,   // 经度
                        goalPosY = latitude,    // 纬度
                        szGoalName = name,
                        goalAddress = address,
                        priority = "high"
                    )
                    carrotNetworkClient.sendCarrotManData(carrotManFields.value)
                    Log.i(TAG, "🎯 目的地信息已发送到comma3: $name ($latitude, $longitude)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 发送目的地信息到comma3失败: ${e.message}", e)
                }
            }
        } else {
            Log.w(TAG, "⚠️ 网络客户端未初始化，无法发送目的地信息")
        }
    }
    
    // 缓存目的地信息
    private val destinationCache = mutableMapOf<String, Triple<Double, Double, String>>()
    
    private fun cacheDestination(key: String, longitude: Double, latitude: Double, name: String) {
        destinationCache[key] = Triple(longitude, latitude, name)
        Log.d(TAG, "📝 目的地已缓存: $key -> $name")
    }
    
    // 处理收藏点数据
    private fun handleFavoriteData(favoriteData: String) {
        try {
            val json = JSONObject(favoriteData)
            val latitude = json.optDouble("latitude", 0.0)
            val longitude = json.optDouble("longitude", 0.0)
            val name = json.optString("name", "")
            val type = json.optString("type", "favorite")
            
            if (validateDestination(longitude, latitude, name)) {
                Log.i(TAG, "🌟 收藏点数据: $name ($latitude, $longitude)")
                
                carrotManFields.value = carrotManFields.value.copy(
                    goalPosX = longitude,
                    goalPosY = latitude,
                    szGoalName = name,
                    lastUpdateTime = System.currentTimeMillis()
                )
                
                sendDestinationToComma3(longitude, latitude, name, "收藏点: $type")
                cacheDestination("favorite_$type", longitude, latitude, name)
                updateUI { "收藏点已设置: $name" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析收藏点数据失败: ${e.message}", e)
        }
    }
    
    // 处理家庭/公司地址数据
    private fun handleHomeCompanyAddress(type: String, intent: Intent) {
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val address = intent.getStringExtra("address") ?: ""
        val name = if (type == "home") "家" else "公司"
        
        if (validateDestination(longitude, latitude, name)) {
            Log.i(TAG, "🏠 ${name}地址: $address ($latitude, $longitude)")
            
            carrotManFields.value = carrotManFields.value.copy(
                goalPosX = longitude,
                goalPosY = latitude,
                szGoalName = name,
                lastUpdateTime = System.currentTimeMillis()
            )
            
            sendDestinationToComma3(longitude, latitude, name, address)
            cacheDestination(type + "_address", longitude, latitude, name)
            
            updateUI { "${name}地址已设置: $address" }
        }
    }

    // 处理家庭/公司导航请求
    private fun handleHomeCompanyNavigation(intent: Intent) {
        val navigationType = intent.getStringExtra("navigation_type") ?: ""
        when (navigationType.lowercase()) {
            "home" -> {
                Log.i(TAG, "🏠 处理回家导航请求")
                handleHomeCompanyAddress("home", intent)
            }
            "company" -> {
                Log.i(TAG, "🏢 处理到公司导航请求")
                handleHomeCompanyAddress("company", intent)
            }
            else -> {
                Log.w(TAG, "⚠️ 未知的家庭/公司导航类型: $navigationType")
            }
        }
    }
    
    // 处理收藏点结果
    private fun handleFavoriteResult(intent: Intent) {
        val favoriteData = intent.getStringExtra("FAVORITE_DATA")
        if (!favoriteData.isNullOrEmpty()) {
            Log.i(TAG, "🌟 处理收藏点结果")
            handleFavoriteData(favoriteData)
        } else {
            val name = intent.getStringExtra("favorite_name") ?: ""
            val latitude = intent.getDoubleExtra("favorite_latitude", 0.0)
            val longitude = intent.getDoubleExtra("favorite_longitude", 0.0)
            
            if (name.isNotEmpty() && latitude != 0.0 && longitude != 0.0) {
                Log.i(TAG, "🌟 从分散字段获取收藏点信息: $name")
                val syntheticJson = JSONObject().apply {
                    put("name", name)
                    put("latitude", latitude)
                    put("longitude", longitude)
                    put("type", "favorite")
                }
                handleFavoriteData(syntheticJson.toString())
            }
        }
    }
    
    /**
     * 🎯 处理路线规划
     */
    private fun handleRoutePlanning(intent: Intent) {
        Log.i(TAG, "🗺️ 处理路线规划")
        
        val startLat = intent.getDoubleExtra("start_latitude", 0.0)
        val startLon = intent.getDoubleExtra("start_longitude", 0.0)
        val endLat = intent.getDoubleExtra("end_latitude", 0.0)
        val endLon = intent.getDoubleExtra("end_longitude", 0.0)
        val endName = intent.getStringExtra("end_name") ?: ""
        
        if (endLat != 0.0 && endLon != 0.0) {
            Log.d(TAG, "   起点: ($startLat, $startLon)")
            Log.d(TAG, "   终点: $endName ($endLat, $endLon)")
            
            // 创建合成的目的地Intent并处理
            val syntheticIntent = Intent().apply {
                putExtra("endPOIName", endName)
                putExtra("endPOILatitude", endLat)
                putExtra("endPOILongitude", endLon)
                putExtra("ROUTE_REMAIN_DIS", 0)  // 规划阶段暂无距离信息
                putExtra("ROUTE_REMAIN_TIME", 0)
            }
            
            handleDestinationInfo(syntheticIntent)
        }
    }
    
    /**
     * 🎯 处理开始导航
     */
    private fun handleStartNavigation(intent: Intent) {
        Log.i(TAG, "🚀 开始导航")
        
        carrotManFields.value = carrotManFields.value.copy(
            isNavigating = true,
            active_carrot = 1,
            lastUpdateTime = System.currentTimeMillis()
        )
        
        // 如果有目的地信息，重新发送到comma3
        val currentFields = carrotManFields.value
        if (currentFields.goalPosX != 0.0 && currentFields.goalPosY != 0.0 && currentFields.szGoalName.isNotEmpty()) {
            sendDestinationToComma3(
                currentFields.goalPosX, 
                currentFields.goalPosY, 
                currentFields.szGoalName,
                "导航开始"
            )
        }
        
        updateUI { "导航已开始" }
    }
    
    /**
     * 🎯 处理停止导航
     */
    private fun handleStopNavigation(intent: Intent) {
        Log.i(TAG, "🛑 停止导航")
        
        carrotManFields.value = carrotManFields.value.copy(
            isNavigating = false,
            active_carrot = 0,
            nGoPosDist = 0,
            nGoPosTime = 0,
            nTBTDist = 0,
            szTBTMainText = "",
            lastUpdateTime = System.currentTimeMillis()
        )
        
        updateUI { "导航已停止" }
    }

    /**
     * 🎯 更新交通倒计时相关字段
     * 基于导航数据计算各种倒计时：转弯倒计时、速度倒计时、综合倒计时
     */
    private fun updateTrafficCountdowns(segRemainDis: Int, segRemainTime: Int, totalRemainDis: Int, totalRemainTime: Int, currentSpeed: Double) {
        val f = carrotManFields.value
        
        // 1. 计算转弯倒计时 (left_tbt_sec)
        val leftTbtSec = when {
            segRemainTime > 0 -> segRemainTime
            segRemainDis > 0 && currentSpeed > 0 -> (segRemainDis / (currentSpeed / 3.6)).toInt() // 距离/速度转换为秒
            else -> 0
        }
        
        // 2. 计算速度控制倒计时 (left_spd_sec)
        val leftSpdSec = when {
            f.nSdiDist > 0 && currentSpeed > 0 -> (f.nSdiDist / (currentSpeed / 3.6)).toInt() // 摄像头距离倒计时
            f.xDistToTurn > 0 && currentSpeed > 0 -> (f.xDistToTurn / (currentSpeed / 3.6)).toInt() // 转弯距离倒计时
            else -> 0
        }
        
        // 3. 计算综合倒计时 (left_sec)
        val leftSec = when {
            leftTbtSec > 0 && leftSpdSec > 0 -> minOf(leftTbtSec, leftSpdSec) // 取最小值
            leftTbtSec > 0 -> leftTbtSec
            leftSpdSec > 0 -> leftSpdSec
            totalRemainTime > 0 -> totalRemainTime
            else -> 0
        }
        
        // 4. 计算最大倒计时 (max_left_sec)
        val maxLeftSec = maxOf(leftTbtSec, leftSpdSec, leftSec)
        
        // 5. 计算CarrotMan倒计时 (carrot_left_sec)
        val carrotLeftSec = when {
            leftSec > 0 -> leftSec // 使用综合倒计时
            else -> f.carrot_left_sec // 保持现有值
        }
        
        // 6. 智能判断交通状态
        val trafficState = when {
            f.traffic_light_count > 0 && leftSec < 30 -> 1 // 接近红绿灯，可能是红灯
            f.traffic_light_count > 0 && leftSec >= 30 -> 2 // 距离红绿灯较远，绿灯
            currentSpeed < 10 -> 1 // 速度很慢，可能红灯
            currentSpeed > 30 -> 2 // 速度较快，绿灯
            else -> f.traffic_state // 保持现有状态
        }
        
        // 更新字段
        carrotManFields.value = f.copy(
            left_tbt_sec = leftTbtSec,
            left_spd_sec = leftSpdSec,
            left_sec = leftSec,
            max_left_sec = maxLeftSec,
            carrot_left_sec = carrotLeftSec,
            traffic_state = trafficState
        )
        
        // 日志输出
        if (leftTbtSec > 0 || leftSpdSec > 0) {
            Log.d(TAG, "⏱️ 倒计时更新: TBT=${leftTbtSec}s, SPD=${leftSpdSec}s, 综合=${leftSec}s, CarrotMan=${carrotLeftSec}s, 交通状态=${trafficState}")
        }
    }

    /**
     * 🎯 重新计算智能限速字段 (xSpdLimit / xSpdDist / xSpdType)
     * 根据摄像头、区间测速、减速带、自动转弯和道路限速等信息综合得出最严格的速度限制
     */
    private fun updateSpeedControl() {
        val f = carrotManFields.value

        // Sentinel 超大值，用于表示未命中限速
        val HIGH = 999

        // 1. 各类候选限速值
        val camSpeed = if (f.nSdiType > 0 && f.nSdiSpeedLimit > 0) f.nSdiSpeedLimit else HIGH
        val sectionSpeed = if (f.nSdiBlockType in 1..3 && f.nSdiBlockSpeed > 0) f.nSdiBlockSpeed else HIGH
        val bumpSpeed = if (f.nSdiType == 22) AUTO_NAVI_SPEED_BUMP_SPEED else HIGH
        val turnSpeed = if (f.xTurnInfo in 1..8) AUTO_TURN_CONTROL_SPEED_TURN else HIGH
        val roadSpeed = if (f.nRoadLimitSpeed > 0) f.nRoadLimitSpeed + AUTO_ROAD_SPEED_LIMIT_OFFSET else HIGH

        val speeds = listOf(camSpeed, sectionSpeed, bumpSpeed, turnSpeed, roadSpeed)
        val minSpeed = speeds.minOrNull() ?: HIGH

        // 2. 若没有激活的限速，则清零
        if (minSpeed == HIGH) {
            carrotManFields.value = f.copy(
                xSpdLimit = 0,
                xSpdDist = 0,
                xSpdType = -1
            )
            return
        }

        // 3. 根据选中的来源确定剩余距离与类型编码
        val idx = speeds.indexOf(minSpeed)
        val dist = when (idx) {
            0 -> f.nSdiDist                // 摄像头剩余距离
            1 -> f.nSdiBlockDist           // 区间测速剩余距离
            2 -> f.nSdiDist                // 减速带沿用摄像头距离字段
            3 -> f.xDistToTurn             // 自动转弯使用转向剩余距离
            else -> 0
        }
        val type = when (idx) {
            0, 1 -> f.nSdiType             // 摄像头或区间测速
            2 -> 22                        // 减速带固定编码 22
            3 -> 1000 + f.xTurnInfo        // 自动转弯自定义编码
            else -> -1
        }

        // 4. 写回最小限速
        carrotManFields.value = f.copy(
            xSpdLimit = minSpeed,
            xSpdDist = dist.coerceAtLeast(0),
            xSpdType = type
        )
    }

    /**
     * 🎯 更新道路限速并维护 nRoadLimitSpeed_counter 计数器
     * 统一入口，避免在各个处理函数中重复逻辑
     * @param newLimit 最新道路限速 (km/h)
     */
    private fun updateRoadSpeedLimit(newLimit: Int) {
        if (newLimit <= 0) return  // 无效限速直接忽略
        val current = carrotManFields.value.nRoadLimitSpeed
        if (newLimit == current) return  // 限速未变化，无需处理

        // 计数器 +1，并记录上一次限速
        val newCounter = carrotManFields.value.nRoadLimitSpeed_counter + 1
        carrotManFields.value = carrotManFields.value.copy(
            nRoadLimitSpeed_last = current,
            nRoadLimitSpeed = newLimit,
            nRoadLimitSpeed_counter = newCounter,
            lastUpdateTime = System.currentTimeMillis()
        )
        Log.d(TAG, "🚸 限速更新: $current -> $newLimit, 计数器 = $newCounter")
    }

}



/**
 * Comma3数据映射界面 - 紧凑表格布局
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Comma3DataMappingScreen(
    broadcastDataList: List<BroadcastData>,
    carrotManFields: CarrotManFields,
    receiverStatus: String,
    totalBroadcastCount: Int,
    onClearDataClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // 状态信息卡片 - 更紧凑版
        CompactStatusCard(
            receiverStatus = receiverStatus,
            totalBroadcastCount = totalBroadcastCount,
            carrotManFields = carrotManFields,
            networkStatus = "未连接",
            networkStats = mapOf(),
            onClearDataClick = onClearDataClick
        )
            
        Spacer(modifier = Modifier.height(8.dp))
            
        // Comma3字段映射表 - 主要内容，铺满剩余空间
    Card(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // 表头
                TableHeader()
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 字段数据 - 分组显示，支持滚动
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // 🎯 基础状态和激活信息
                    item { TableSectionHeader("基础状态") }
                    items(getBasicStatusFields(carrotManFields)) { fieldData ->
                        TableRow(fieldData.first, fieldData.second, fieldData.third)
                    }
                    
                    // 🎯 道路和限速信息
                    item { TableSectionHeader("道路限速") }
                    items(getRoadSpeedFields(carrotManFields)) { fieldData ->
                        TableRow(fieldData.first, fieldData.second, fieldData.third)
                    }
                    
                    // 🎯 GPS和位置信息
                    item { TableSectionHeader("GPS位置") }
                    items(getGpsLocationFields(carrotManFields)) { fieldData ->
                        TableRow(fieldData.first, fieldData.second, fieldData.third)
                    }
                    
                    // 🎯 转弯引导信息
                    item { TableSectionHeader("转弯引导") }
                    items(getTurnGuidanceFields(carrotManFields)) { fieldData ->
                        TableRow(fieldData.first, fieldData.second, fieldData.third)
                    }
                    
                    // 🎯 目标和路线信息
                    item { TableSectionHeader("目标路线") }
                    items(getRouteTargetFields(carrotManFields)) { fieldData ->
                        TableRow(fieldData.first, fieldData.second, fieldData.third)
                    }
                    
                    // 🎯 SDI摄像头信息
                    item { TableSectionHeader("摄像头信息") }
                    items(getSdiCameraFields(carrotManFields)) { fieldData ->
                        TableRow(fieldData.first, fieldData.second, fieldData.third)
                    }
                    
                    // 🎯 交通和时间信息
                    item { TableSectionHeader("交通时间") }
                    items(getTrafficTimeFields(carrotManFields)) { fieldData ->
                        TableRow(fieldData.first, fieldData.second, fieldData.third)
                    }
                    
                    // 🎯 CarrotMan命令信息
                    item { TableSectionHeader("CarrotMan命令") }
                    items(getCarrotManCommandFields(carrotManFields)) { fieldData ->
                        TableRow(fieldData.first, fieldData.second, fieldData.third)
                    }
                }
            }
        }
    }
}

/**
 * 紧凑状态卡片 - 优化版，包含网络状态
 */
@Composable
fun CompactStatusCard(
    receiverStatus: String,
    totalBroadcastCount: Int,
    carrotManFields: CarrotManFields,
    networkStatus: String,
    networkStats: Map<String, Any>,
    onClearDataClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 第一行：基础状态
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp), // 减少高度使布局更紧凑
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // 添加阴影保持一致
            colors = CardDefaults.cardColors(
                containerColor = when (carrotManFields.dataQuality) {
                    "good" -> MaterialTheme.colorScheme.primaryContainer
                    "warning" -> MaterialTheme.colorScheme.tertiaryContainer
                    "error" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface // 使用surface保持一致
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                        text = if (carrotManFields.isNavigating) "导航中" else "待机",
                            style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
        Text(
                        text = "广播:$totalBroadcastCount",
                            style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                        )
                    Spacer(modifier = Modifier.width(8.dp))
                        Text(
                        text = "CM:${carrotManFields.active_carrot}",
                            style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(carrotManFields.lastUpdateTime),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClearDataClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "清空数据",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(3.dp)) // 减少间距
        
        // 第二行：网络状态
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp), // 减少高度
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // 添加阴影保持一致
            colors = CardDefaults.cardColors(
                containerColor = if (networkStatus.startsWith("✅"))
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌐 网络状态",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = networkStatus.take(15) + if(networkStatus.length > 15) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val packetsSent = networkStats["totalPacketsSent"] as? Int ?: 0
                    val deviceCount = networkStats["discoveredDevices"] as? Int ?: 0
                    
                    Text(
                        text = "设备:$deviceCount 发送:$packetsSent",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * OpenpPilot状态卡片组件
 * 显示从7705端口接收到的OpenpPilot状态信息，使用紧凑表格样式
 */
@Composable
fun OpenpilotStatusCard(
    statusData: MainActivity.OpenpilotStatusData
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp) // 减少内边距使布局更紧凑
        ) {
            // 紧凑标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚗 OpenpPilot状态 (14字段)",
                    style = MaterialTheme.typography.titleSmall, // 使用更小的标题
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                // 连接状态指示器
                val statusColor = if (statusData.isOnroad) Color.Green else Color.Gray
                val statusText = if (statusData.isOnroad) "在线" else "离线"

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp) // 减小指示器大小
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp)) // 减少间距

            // 紧凑表格头部
            OpenpilotTableHeader()

            // 表格内容 - 完整显示所有JSON数据参数（按逻辑分组）
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp) // 增加高度以容纳所有14个字段
            ) {
                // === 基础系统信息 ===
                item { OpenpilotTableRow("Carrot2", "版本信息", statusData.carrot2.ifEmpty { "未知" }) }
                item { OpenpilotTableRow("ip", "设备IP", statusData.ip.ifEmpty { "未连接" }) }
                item { OpenpilotTableRow("port", "通信端口", statusData.port.toString()) }
                item { OpenpilotTableRow("log_carrot", "系统日志", statusData.logCarrot.ifEmpty { "无日志" }) }

                // === 运行状态 ===
                item { OpenpilotTableRow("IsOnroad", "道路状态", if (statusData.isOnroad) "在路上" else "未上路") }
                item { OpenpilotTableRow("active", "自动驾驶", if (statusData.active) "激活" else "未激活") }
                item { OpenpilotTableRow("CarrotRouteActive", "导航状态", if (statusData.carrotRouteActive) "导航中" else "未导航") }

                // === 速度信息 ===
                item { OpenpilotTableRow("v_ego_kph", "当前车速", "${statusData.vEgoKph} km/h") }
                item { OpenpilotTableRow("v_cruise_kph", "巡航速度", "${statusData.vCruiseKph} km/h") }

                // === 导航距离信息 ===
                item { OpenpilotTableRow("tbt_dist", "转弯距离", "${statusData.tbtDist} m") }
                item { OpenpilotTableRow("sdi_dist", "限速距离", "${statusData.sdiDist} m") }

                // === 控制状态 ===
                item {
                    val xStateDesc = when (statusData.xState) {
                        0 -> "跟车模式"      // lead
                        1 -> "巡航模式"      // cruise
                        2 -> "端到端巡航"    // e2eCruise
                        3 -> "端到端停车"    // e2eStop
                        4 -> "端到端准备"    // e2ePrepare
                        5 -> "端到端已停"    // e2eStopped
                        else -> "未知状态(${statusData.xState})"
                    }
                    OpenpilotTableRow("xState", "纵向状态", xStateDesc)
                }

                item {
                    val trafficDesc = when (statusData.trafficState) {
                        0 -> "无信号"
                        1 -> "红灯"
                        2 -> "绿灯"
                        3 -> "左转"
                        else -> "未知(${statusData.trafficState})"
                    }
                    OpenpilotTableRow("trafficState", "交通状态", trafficDesc)
                }

                // === 时间信息 ===
                item {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val timeStr = sdf.format(Date(statusData.lastUpdateTime))
                    OpenpilotTableRow("lastUpdateTime", "更新时间", timeStr)
                }
            }
        }
    }
}

/**
 * OpenpPilot表格头部 - 紧凑样式
 */
@Composable
fun OpenpilotTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 4.dp, horizontal = 3.dp), // 减少内边距
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "字段名称",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp // 减小字体
        )
        Text(
            text = "中文名称",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "数据值",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            textAlign = TextAlign.End
        )
    }
}

/**
 * OpenpPilot表格行 - 紧凑样式
 */
@Composable
fun OpenpilotTableRow(
    fieldName: String,
    chineseName: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 3.dp), // 减少内边距
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = fieldName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 8.sp, // 减小字体
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = chineseName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 8.sp,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium
        )
    }
}





// 表格头部
@Composable
fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
        ) {
            Text(
            text = "字段名",
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        Text(
            text = "中文名称",
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
            Text(
            text = "数据值",
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
    }
}

// 表格分组头部
@Composable
fun TableSectionHeader(title: String) {
    Row(
            modifier = Modifier
                .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                RoundedCornerShape(4.dp)
            )
            .padding(6.dp)
        ) {
            Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
            )
    }
}

// 表格行
@Composable
fun TableRow(fieldName: String, chineseName: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
            text = fieldName,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 10.sp
                )
                Text(
            text = chineseName,
            modifier = Modifier.weight(2f),
                    style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp
                )
                Text(
            text = value,
            modifier = Modifier.weight(1.5f),
                    style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (value == "null" || value == "-1" || value == "0" || value == "false") 
                MaterialTheme.colorScheme.outline 
            else 
                MaterialTheme.colorScheme.onSurface
        )
    }
}

// 字段分组函数
fun getBasicStatusFields(fields: CarrotManFields): List<Triple<String, String, String>> {
    return listOf(
        Triple("active_carrot", "CarrotMan激活状态", fields.active_carrot.let {
            when (it) {
                1 -> "CarrotMan激活"
                2 -> "SDI激活"
                3 -> "减速激活"
                4 -> "区间激活"
                5 -> "减速带激活"
                6 -> "限速激活"
                else -> "未激活"
            }
        }),
        Triple("active_count", "激活计数", "${fields.active_count}"),
        Triple("active_sdi_count", "SDI激活计数", "${fields.active_sdi_count}/${fields.active_sdi_count_max}"),
        Triple("active_kisa_count", "KISA激活计数", "${fields.active_kisa_count}"),
        Triple("carrotIndex", "CarrotMan索引", "${fields.carrotIndex}"),
        Triple("isNavigating", "导航状态", if(fields.isNavigating) "导航中" else "未导航"),
        Triple("gps_valid", "GPS有效", if(fields.gps_valid) "有效" else "无效"),
        Triple("dataQuality", "数据质量", when(fields.dataQuality) {
            "good" -> "良好"
            "warning" -> "警告"
            "error" -> "错误"
            else -> fields.dataQuality
        })
    )
}

fun getRoadSpeedFields(fields: CarrotManFields): List<Triple<String, String, String>> {
    return listOf(
        Triple("nRoadLimitSpeed", "道路限速", "${fields.nRoadLimitSpeed} km/h"),
        Triple("nRoadLimitSpeed_last", "上次限速", "${fields.nRoadLimitSpeed_last} km/h"),
        Triple("nRoadLimitSpeed_counter", "限速计数器", "${fields.nRoadLimitSpeed_counter}"),
        Triple("szPosRoadName", "当前道路名", fields.szPosRoadName.ifEmpty { "未知道路" }),
        Triple("roadcate", "道路类别", fields.roadcate.let {
            when (it) {
                0 -> "高速公路"
                1 -> "城市快速路"
                2 -> "国道"
                3 -> "省道"
                4 -> "县道"
                5 -> "乡道"
                6 -> "街道"
                7 -> "其他"
                8 -> "未知"
                else -> "类型$it"
            }
        }),
        Triple("xSpdLimit", "X限速", "${fields.xSpdLimit} km/h"),
        Triple("xSpdDist", "X限速距离", "${fields.xSpdDist} m"),
        Triple("xSpdType", "X限速类型", fields.xSpdType.let {
            when (it) {
                1 -> "固定限速"
                2 -> "临时限速"
                3 -> "变速限速"
                -1 -> "无限速"
                else -> "类型$it"
            }
        })
    )
}

fun getGpsLocationFields(fields: CarrotManFields): List<Triple<String, String, String>> {
    return listOf(
        Triple("vpPosPointLat", "手机GPS纬度", "%.6f".format(fields.vpPosPointLat)),
        Triple("vpPosPointLon", "手机GPS经度", "%.6f".format(fields.vpPosPointLon)),
        Triple("vpPosPointLatNavi", "导航纬度(Navi)", "%.6f".format(fields.vpPosPointLatNavi)),
        Triple("vpPosPointLonNavi", "导航经度(Navi)", "%.6f".format(fields.vpPosPointLonNavi)),
        Triple("nPosSpeed", "当前速度", "%.1f km/h".format(fields.nPosSpeed)),
        Triple("nPosAngle", "位置角度", "%.1f°".format(fields.nPosAngle)),
        Triple("nPosAnglePhone", "手机角度", "%.1f°".format(fields.nPosAnglePhone)),
        Triple("bearing", "方位角", "%.1f°".format(fields.bearing)),
        Triple("bearing_offset", "方位偏移", "%.1f°".format(fields.bearing_offset)),
        Triple("bearing_measured", "测量方位", "%.1f°".format(fields.bearing_measured)),
        Triple("gps_accuracy_phone", "手机GPS精度", "%.1f m".format(fields.gps_accuracy_phone)),
        Triple("gps_accuracy_device", "设备GPS精度", "%.1f m".format(fields.gps_accuracy_device)),
        Triple("diff_angle_count", "角度差计数", "${fields.diff_angle_count}"),
        Triple("last_calculate_gps_time", "最后GPS计算", formatTimestamp(fields.last_calculate_gps_time)),
        Triple("last_update_gps_time", "最后GPS更新", formatTimestamp(fields.last_update_gps_time))
    )
}

fun getTurnGuidanceFields(fields: CarrotManFields): List<Triple<String, String, String>> {
    return listOf(
        Triple("nTBTDist", "转弯距离", "${fields.nTBTDist} m"),
        Triple("nTBTTurnType", "转弯类型", fields.nTBTTurnType.let {
            when (it) {
                // 基本转弯
                12 -> "左转"
                13 -> "右转"
                16 -> "急左转"
                19 -> "急右转"
                14 -> "掉头"
                1000 -> "轻微左转"
                1001 -> "轻微右转"

                // 分岔路口
                7 -> "左侧分岔"
                6 -> "右侧分岔"
                17 -> "左侧分岔"
                44 -> "左侧分岔"
                43 -> "右侧分岔"
                75 -> "左侧分岔"
                76 -> "左侧分岔"
                73 -> "右侧分岔"
                74 -> "右侧分岔"
                117 -> "右侧分岔"
                118 -> "左侧分岔"
                123 -> "右侧分岔"
                124 -> "右侧分岔"
                1002 -> "轻微左侧分岔"
                1003 -> "轻微右侧分岔"

                // 出入口匝道
                101 -> "轻微右侧出口"
                102 -> "轻微左侧出口"
                104 -> "轻微右侧出口"
                105 -> "轻微左侧出口"
                111 -> "轻微右侧出口"
                112 -> "轻微左侧出口"
                114 -> "轻微右侧出口"
                115 -> "轻微左侧出口"
                1006 -> "左侧出口"
                1007 -> "右侧出口"

                // 环岛
                131 -> "环岛轻微右转"
                132 -> "环岛轻微右转"
                133 -> "环岛右转"
                134 -> "环岛急右转"
                135 -> "环岛急右转"
                136 -> "环岛急左转"
                137 -> "环岛急左转"
                138 -> "环岛急左转"
                139 -> "环岛左转"
                140 -> "环岛轻微左转"
                141 -> "环岛轻微左转"
                142 -> "环岛直行"

                // 特殊指令
                201 -> "到达目的地"
                51 -> "通知"
                52 -> "通知"
                53 -> "通知"
                54 -> "通知"
                55 -> "通知"

                // TG (Traffic Gate) - 收费站
                153 -> "收费站(TG)"
                154 -> "收费站(TG)"
                249 -> "收费站(TG)"

                // 其他
                0 -> "通知指令"
                20 -> "直行"
                -1 -> "无转弯"
                else -> "类型$it"
            }
        }),
        Triple("szTBTMainText", "转弯主文本", fields.szTBTMainText.ifEmpty { "无指令" }),
        Triple("szNearDirName", "近方向名", fields.szNearDirName.ifEmpty { "无" }),
        Triple("szFarDirName", "远方向名", fields.szFarDirName.ifEmpty { "无" }),
        Triple("nTBTNextRoadWidth", "下条路宽度", "${fields.nTBTNextRoadWidth} m"),
        Triple("nTBTDistNext", "下一转弯距离", "${fields.nTBTDistNext} m"),
        Triple("nTBTTurnTypeNext", "下一转弯类型", fields.nTBTTurnTypeNext.let {
            when (it) {
                // 基本转弯
                12 -> "左转"
                13 -> "右转"
                16 -> "急左转"
                19 -> "急右转"
                14 -> "掉头"
                1000 -> "轻微左转"
                1001 -> "轻微右转"

                // 分岔路口
                7 -> "左侧分岔"
                6 -> "右侧分岔"
                17 -> "左侧分岔"
                44 -> "左侧分岔"
                43 -> "右侧分岔"
                75 -> "左侧分岔"
                76 -> "左侧分岔"
                73 -> "右侧分岔"
                74 -> "右侧分岔"
                117 -> "右侧分岔"
                118 -> "左侧分岔"
                123 -> "右侧分岔"
                124 -> "右侧分岔"
                1002 -> "轻微左侧分岔"
                1003 -> "轻微右侧分岔"

                // 出入口匝道
                101 -> "轻微右侧出口"
                102 -> "轻微左侧出口"
                104 -> "轻微右侧出口"
                105 -> "轻微左侧出口"
                111 -> "轻微右侧出口"
                112 -> "轻微左侧出口"
                114 -> "轻微右侧出口"
                115 -> "轻微左侧出口"
                1006 -> "左侧出口"
                1007 -> "右侧出口"

                // 环岛
                131 -> "环岛轻微右转"
                132 -> "环岛轻微右转"
                133 -> "环岛右转"
                134 -> "环岛急右转"
                135 -> "环岛急右转"
                136 -> "环岛急左转"
                137 -> "环岛急左转"
                138 -> "环岛急左转"
                139 -> "环岛左转"
                140 -> "环岛轻微左转"
                141 -> "环岛轻微左转"
                142 -> "环岛直行"

                // 特殊指令
                201 -> "到达目的地"
                51 -> "通知"
                52 -> "通知"
                53 -> "通知"
                54 -> "通知"
                55 -> "通知"

                // TG (Traffic Gate) - 收费站
                153 -> "收费站(TG)"
                154 -> "收费站(TG)"
                249 -> "收费站(TG)"

                // 其他
                0 -> "通知指令"
                20 -> "直行"
                -1 -> "无转弯"
                else -> "类型$it"
            }
        }),
        Triple("szTBTMainTextNext", "下一转弯文本", fields.szTBTMainTextNext.ifEmpty { "无指令" }),
        Triple("xTurnInfo", "X转弯信息", "${fields.xTurnInfo} (${
            when (fields.xTurnInfo) {
                1 -> "左转"
                2 -> "右转"
                3 -> "左侧车道变更"
                4 -> "右侧车道变更"
                5 -> "环岛"
                6 -> "收费站(TG)"
                7 -> "掉头"
                8 -> "到达目的地"
                0 -> "通知"
                else -> "未知"
            }
        })"),
        Triple("xDistToTurn", "X转弯距离", "${fields.xDistToTurn} m"),
        Triple("navType", "导航类型", fields.navType),
        Triple("navModifier", "导航修饰符", fields.navModifier.ifEmpty { "无" }),
        Triple("navTypeNext", "下一导航类型", fields.navTypeNext),
        Triple("navModifierNext", "下一导航修饰符", fields.navModifierNext.ifEmpty { "无" })
    )
}

fun getRouteTargetFields(fields: CarrotManFields): List<Triple<String, String, String>> {
    return listOf(
        Triple("nGoPosDist", "目标距离", "${fields.nGoPosDist} m"),
        Triple("nGoPosTime", "目标时间", formatSeconds(fields.nGoPosTime)),
        Triple("goalPosX", "目标X坐标", "%.6f".format(fields.goalPosX)),
        Triple("goalPosY", "目标Y坐标", "%.6f".format(fields.goalPosY)),
        Triple("szGoalName", "目标名称", fields.szGoalName.ifEmpty { "未设置" }),
        Triple("totalDistance", "总距离", "${fields.totalDistance} m")
    )
}

fun getSdiCameraFields(fields: CarrotManFields): List<Triple<String, String, String>> {
    return listOf(
        Triple("nSdiType", "主摄像头类型", fields.nSdiType.let {
            when (it) {
                0 -> "测速摄像头(限速拍照)"
                1 -> "监控摄像头(治安监控)"
                2 -> "闯红灯拍照(红绿灯路口)"
                3 -> "违章拍照(压线/禁停等)"
                4 -> "公交专用道摄像头(公交车道监控)"
                -1 -> "无摄像头"
                else -> "类型$it"
            }
        }),
        Triple("nSdiSpeedLimit", "主摄像头限速", "${fields.nSdiSpeedLimit} km/h"),
        Triple("nSdiDist", "主摄像头距离", "${fields.nSdiDist} m"),
        Triple("nSdiSection", "主摄像头区间", "${fields.nSdiSection}"),
        Triple("nSdiBlockType", "区间测速类型", fields.nSdiBlockType.let {
            when (it) {
                1 -> "固定区间"
                2 -> "临时区间"
                3 -> "变速区间"
                -1 -> "无区间"
                else -> "类型$it"
            }
        }),
        Triple("nSdiBlockSpeed", "区间测速限速", "${fields.nSdiBlockSpeed} km/h"),
        Triple("nSdiBlockDist", "区间测速距离", "${fields.nSdiBlockDist} m"),
        Triple("nSdiPlusType", "次摄像头类型", fields.nSdiPlusType.let {
            when (it) {
                0 -> "测速摄像头(限速拍照)"
                1 -> "监控摄像头(治安监控)"
                2 -> "闯红灯拍照(红绿灯路口)"
                3 -> "违章拍照(压线/禁停等)"
                4 -> "公交专用道摄像头(公交车道监控)"
                -1 -> "无摄像头"
                else -> "类型$it"
            }
        }),
        Triple("nSdiPlusSpeedLimit", "次摄像头限速", "${fields.nSdiPlusSpeedLimit} km/h"),
        Triple("nSdiPlusDist", "次摄像头距离", "${fields.nSdiPlusDist} m"),
        Triple("sdi_inform", "SDI通知", if(fields.sdi_inform) "已通知" else "未通知")
    )
}

fun getTrafficTimeFields(fields: CarrotManFields): List<Triple<String, String, String>> {
    return listOf(
        Triple("traffic_light_count", "红绿灯数量", "${fields.traffic_light_count}"),
        Triple("traffic_state", "交通灯状态", fields.traffic_state.let {
            when (it) {
                0 -> "未知/无信号"
                1 -> "红灯/黄灯"  
                2 -> "绿灯"
                3 -> "左转绿灯"
                else -> "状态$it"
            }
        }),
        Triple("left_spd_sec", "剩余速度秒", formatSeconds(fields.left_spd_sec)),
        Triple("left_tbt_sec", "剩余TBT秒", formatSeconds(fields.left_tbt_sec)),
        Triple("left_sec", "剩余秒数", formatSeconds(fields.left_sec)),
        Triple("max_left_sec", "最大剩余秒", formatSeconds(fields.max_left_sec)),
        Triple("carrot_left_sec", "CarrotMan剩余秒", formatSeconds(fields.carrot_left_sec))
    )
}

fun getCarrotManCommandFields(fields: CarrotManFields): List<Triple<String, String, String>> {
    return listOf(
        Triple("carrotCmdIndex", "命令索引", "${fields.carrotCmdIndex}"),
        Triple("carrotCmd", "命令内容", fields.carrotCmd.ifEmpty { "无命令" }),
        Triple("carrotArg", "命令参数", fields.carrotArg.ifEmpty { "无参数" }),
        Triple("carrotCmdIndex_last", "上次命令索引", "${fields.carrotCmdIndex_last}"),
        Triple("atc_paused", "ATC暂停", if(fields.atc_paused) "已暂停" else "运行中"),
        Triple("atc_activate_count", "ATC激活计数", "${fields.atc_activate_count}"),
        Triple("gas_override_speed", "油门覆盖速度", "${fields.gas_override_speed} km/h"),
        Triple("gas_pressed_state", "油门按下状态", if(fields.gas_pressed_state) "已按下" else "未按下"),
        Triple("source_last", "上次源", fields.source_last.ifEmpty { "无" }),
        Triple("debugText", "调试文本", fields.debugText.take(20).ifEmpty { "无" })
    )
}

// 格式化秒数为可读时间
private fun formatSeconds(seconds: Int): String {
    if (seconds <= 0) return "0秒"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}时${minutes}分${secs}秒"
        minutes > 0 -> "${minutes}分${secs}秒"
        else -> "${secs}秒"
    }
}

// 简化的广播数据卡片
@Composable
fun SimpleBroadcastDataCard(broadcastDataList: List<BroadcastData>) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
            modifier = Modifier.padding(8.dp)
                        ) {
                                Text(
                text = "最新广播 (${broadcastDataList.size}条)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            broadcastDataList.forEach { data ->
                Row(
                        modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = data.dataType,
                                    style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        fontSize = 10.sp
                    )
                                Text(
                        text = formatTimestamp(data.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp
                                )
                            }
                if (broadcastDataList.indexOf(data) < broadcastDataList.size - 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

// 格式化时间戳
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
