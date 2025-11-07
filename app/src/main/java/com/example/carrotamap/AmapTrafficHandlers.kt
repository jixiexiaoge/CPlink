package com.example.carrotamap

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.MutableState

/**
 * 高德地图广播处理器（交通/限速/电子眼/红绿灯 等拆分）
 */
class AmapTrafficHandlers(
    private val carrotManFields: MutableState<CarrotManFields>,
    private val networkManager: NetworkManager? = null,
    private val context: android.content.Context? = null
) {
    companion object {
        private const val TAG = "AmapTrafficHandlers"

        /**
         * 统一映射：高德 CAMERA_TYPE → Python nSdiType
         * 与 AmapBroadcastHandlers 中的映射保持一致
         */
        fun mapAmapCameraTypeToSdi(cameraType: Int): Int {
            return when (cameraType) {
                0 -> 1
                1 -> 14
                2 -> 6
                3 -> 17
                4 -> 9
                5 -> 11
                6 -> 8
                7 -> 7
                8 -> 2
                9 -> 3
                11 -> 26
                12 -> 41
                13 -> 41
                14 -> 14
                15 -> 15
                16 -> 16
                17 -> 17
                18 -> 18
                19 -> 19
                20 -> 20
                21 -> 21
                22 -> 22
                23 -> 23
                24 -> 24
                25 -> 25
                26 -> 26
                27 -> 27
                28 -> 28
                29 -> 29
                30 -> 30
                31 -> 31
                32 -> 32
                33 -> 33
                34 -> 34
                35 -> 35
                36 -> 36
                37 -> 37
                38 -> 38
                39 -> 39
                40 -> 40
                41 -> 41
                42 -> 42
                43 -> 43
                44 -> 44
                45 -> 45
                46 -> 46
                47 -> 47
                48 -> 48
                49 -> 49
                50 -> 50
                51 -> 51
                52 -> 52
                53 -> 53
                54 -> 54
                55 -> 55
                56 -> 56
                57 -> 57
                58 -> 58
                59 -> 59
                60 -> 60
                61 -> 61
                62 -> 62
                63 -> 63
                64 -> 64
                65 -> 65
                else -> 66
            }
        }

        private fun mapTrafficLightStatus(amapStatus: Int, direction: Int = 0): Int {
            return when (amapStatus) {
                -1 -> -1
                0 -> 0
                1 -> 1
                2 -> if (direction == 1 || direction == 3) 3 else 2
                3 -> 1
                4 -> 2
                else -> 0
            }
        }

        private fun getTrafficLightDirectionDesc(direction: Int): String {
            return when (direction) {
                0 -> "直行黄灯"
                1 -> "左转"
                2 -> "右转"
                3 -> "左转掉头"
                4 -> "直行"
                5 -> "右转掉头"
                else -> "方向$direction"
            }
        }
    }

    /**
     * 处理限速信息广播 (KEY_TYPE: 12110)
     */
    fun handleSpeedLimit(intent: Intent) {
        Log.i(TAG, "🚦 开始处理限速信息广播 (KEY_TYPE: 12110)")

        try {
            val speedLimit = intent.getIntExtra("LIMITED_SPEED", 0)
            val roadName = intent.getStringExtra("ROAD_NAME") ?: ""
            val speedLimitType = intent.getIntExtra("SPEED_LIMIT_TYPE", -1)

            @Suppress("DEPRECATION")
            fun readNumberAsInt(key: String): Int {
                val extras = intent.extras
                if (extras == null || !extras.containsKey(key)) return 0
                val raw = extras.get(key)
                return when (raw) {
                    is Int -> raw
                    is Long -> raw.toInt()
                    is Float -> raw.toInt()
                    is Double -> raw.toInt()
                    is String -> raw.toDoubleOrNull()?.toInt() ?: 0
                    else -> 0
                }
            }

            @Suppress("DEPRECATION")
            fun readNumberAsDouble(key: String): Double {
                val extras = intent.extras
                if (extras == null || !extras.containsKey(key)) return 0.0
                val raw = extras.get(key)
                return when (raw) {
                    is Int -> raw.toDouble()
                    is Long -> raw.toDouble()
                    is Float -> raw.toDouble()
                    is Double -> raw
                    is String -> raw.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            }

            val hasStartDistance = intent.hasExtra("START_DISTANCE")
            val hasEndDistance = intent.hasExtra("END_DISTANCE")
            val hasIntervalDistance = intent.hasExtra("INTERVAL_DISTANCE")
            val hasAverageSpeed = intent.hasExtra("AVERAGE_SPEED")

            val startDistanceInt = if (hasStartDistance) readNumberAsInt("START_DISTANCE") else 0
            val endDistanceInt = if (hasEndDistance) readNumberAsInt("END_DISTANCE") else 0
            val intervalDistanceInt = if (hasIntervalDistance) readNumberAsInt("INTERVAL_DISTANCE") else 0
            val isInSectionSpeedControl = hasStartDistance || hasEndDistance || hasIntervalDistance

            val nSdiType = if (isInSectionSpeedControl) 4 else carrotManFields.value.nSdiType
            val nSdiDist = if (isInSectionSpeedControl && hasEndDistance && endDistanceInt > 0) endDistanceInt else carrotManFields.value.nSdiDist
            val nSdiBlockType = if (isInSectionSpeedControl) 2 else carrotManFields.value.nSdiBlockType
            val nSdiSection = if (isInSectionSpeedControl && hasStartDistance && startDistanceInt >= 0) startDistanceInt else carrotManFields.value.nSdiSection
            val nSdiBlockDist = if (isInSectionSpeedControl && hasIntervalDistance && intervalDistanceInt > 0) intervalDistanceInt else carrotManFields.value.nSdiBlockDist
            val nSdiBlockSpeed = if (isInSectionSpeedControl && speedLimit > 0) speedLimit else carrotManFields.value.nSdiBlockSpeed.takeIf { it > 0 } ?: 0
            val nSdiSpeedLimit = if (isInSectionSpeedControl && speedLimit > 0) speedLimit else carrotManFields.value.nSdiSpeedLimit.takeIf { it > 0 } ?: 0

            carrotManFields.value = carrotManFields.value.copy(
                nRoadLimitSpeed = speedLimit.takeIf { it > 0 } ?: carrotManFields.value.nRoadLimitSpeed,
                szPosRoadName = roadName.takeIf { it.isNotEmpty() } ?: carrotManFields.value.szPosRoadName,
                speedLimitType = speedLimitType.takeIf { it >= 0 } ?: carrotManFields.value.speedLimitType,
                nSdiType = if (isInSectionSpeedControl) nSdiType else carrotManFields.value.nSdiType,
                nSdiSpeedLimit = if (isInSectionSpeedControl) nSdiSpeedLimit else carrotManFields.value.nSdiSpeedLimit,
                nSdiDist = if (isInSectionSpeedControl) nSdiDist else carrotManFields.value.nSdiDist,
                nSdiSection = if (isInSectionSpeedControl) nSdiSection else carrotManFields.value.nSdiSection,
                nSdiBlockType = if (isInSectionSpeedControl) nSdiBlockType else carrotManFields.value.nSdiBlockType,
                nSdiBlockSpeed = if (isInSectionSpeedControl) nSdiBlockSpeed else carrotManFields.value.nSdiBlockSpeed,
                nSdiBlockDist = if (isInSectionSpeedControl) nSdiBlockDist else carrotManFields.value.nSdiBlockDist,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理限速信息失败: ${e.message}", e)
        }
    }

    /**
     * 处理电子眼信息广播 (KEY_TYPE: 13005)
     */
    fun handleCameraInfo(intent: Intent) {
        try {
            val cameraType = intent.getIntExtra("CAMERA_TYPE", -1)
            val cameraDistance = intent.getIntExtra("CAMERA_DISTANCE", 0)
            val cameraSpeedLimit = intent.getIntExtra("CAMERA_SPEED_LIMIT", 0)

            val mappedSdiType = if (cameraType >= 0) mapAmapCameraTypeToSdi(cameraType) else carrotManFields.value.nSdiType
            val shouldClearSdi = cameraDistance <= 20

            carrotManFields.value = carrotManFields.value.copy(
                nAmapCameraType = if (cameraType >= 0) cameraType else carrotManFields.value.nAmapCameraType,
                nSdiType = if (shouldClearSdi) -1 else mappedSdiType,
                nSdiDist = if (shouldClearSdi) 0 else cameraDistance,
                nSdiSpeedLimit = if (shouldClearSdi) 0 else cameraSpeedLimit,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理电子眼信息失败: ${e.message}", e)
        }
    }

    /**
     * 处理SDI Plus信息广播 (KEY_TYPE: 10007)
     */
    fun handleSdiPlusInfo(intent: Intent) {
        try {
            val sdiPlusType = intent.getIntExtra("SDI_PLUS_TYPE", -1)
            val sdiPlusDistance = intent.getIntExtra("SDI_PLUS_DISTANCE", 0)
            val sdiPlusSpeedLimit = intent.getIntExtra("SDI_PLUS_SPEED_LIMIT", 0)

            carrotManFields.value = carrotManFields.value.copy(
                nSdiPlusType = sdiPlusType,
                nSdiPlusDist = sdiPlusDistance,
                nSdiPlusSpeedLimit = sdiPlusSpeedLimit,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理SDI Plus信息失败: ${e.message}", e)
        }
    }

    /**
     * 处理路况信息广播 (KEY_TYPE: 10070)
     */
    fun handleTrafficInfo(intent: Intent) {
        try {
            val trafficLevel = intent.getIntExtra("TRAFFIC_LEVEL", -1)
            val trafficDescription = intent.getStringExtra("TRAFFIC_DESCRIPTION") ?: ""

            carrotManFields.value = carrotManFields.value.copy(
                trafficLevel = trafficLevel,
                trafficDescription = trafficDescription,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理路况信息失败: ${e.message}", e)
        }
    }

    /**
     * 处理导航态势广播 (KEY_TYPE: 13003)
     */
    fun handleNaviSituation(intent: Intent) {
        try {
            val situationType = intent.getIntExtra("SITUATION_TYPE", -1)
            val situationDistance = intent.getIntExtra("SITUATION_DISTANCE", 0)
            val situationDescription = intent.getStringExtra("SITUATION_DESCRIPTION") ?: ""

            carrotManFields.value = carrotManFields.value.copy(
                situationType = situationType,
                situationDistance = situationDistance,
                situationDescription = situationDescription,
                lastUpdateTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理导航态势失败: ${e.message}", e)
        }
    }

    /**
     * 处理红绿灯信息广播 - KEY_TYPE: 60073
     */
    fun handleTrafficLightInfo(intent: Intent) {
        try {
            val trafficLightStatus = when {
                intent.hasExtra("trafficLightStatus") -> intent.getIntExtra("trafficLightStatus", 0)
                intent.hasExtra("TRAFFIC_LIGHT_STATUS") -> intent.getIntExtra("TRAFFIC_LIGHT_STATUS", 0)
                intent.hasExtra("LIGHT_STATUS") -> intent.getIntExtra("LIGHT_STATUS", 0)
                else -> 0
            }

            val redLightCountDown = intent.getIntExtra("redLightCountDownSeconds", 0)
            val greenLightCountDown = intent.getIntExtra("greenLightLastSecond", 0)
            val direction = when {
                intent.hasExtra("dir") -> intent.getIntExtra("dir", 0)
                intent.hasExtra("TRAFFIC_LIGHT_DIRECTION") -> intent.getIntExtra("TRAFFIC_LIGHT_DIRECTION", 0)
                intent.hasExtra("LIGHT_DIRECTION") -> intent.getIntExtra("LIGHT_DIRECTION", 0)
                else -> 0
            }
            val waitRound = intent.getIntExtra("waitRound", 0)

            var carrotTrafficState = mapTrafficLightStatus(trafficLightStatus, direction)
            var leftSec = if (trafficLightStatus == 1 || trafficLightStatus == 3 || trafficLightStatus == 2 || trafficLightStatus == 4) redLightCountDown else redLightCountDown

            val previousTrafficState = carrotManFields.value.traffic_state
            val previousLeftSec = carrotManFields.value.left_sec

            if (carrotTrafficState == 0 && leftSec <= 0) {
                if (previousTrafficState == 1 && previousLeftSec <= 3) {
                    carrotTrafficState = 2
                    leftSec = 30
                }
            }

            val stateChanged = (carrotTrafficState != previousTrafficState) || (leftSec != previousLeftSec)

            carrotManFields.value = carrotManFields.value.copy(
                traffic_light_count = intent.getIntExtra("TRAFFIC_LIGHT_COUNT", -1).takeIf { it >= 0 }
                    ?: carrotManFields.value.traffic_light_count,
                traffic_state = carrotTrafficState,
                traffic_light_direction = direction,
                left_sec = leftSec,
                max_left_sec = maxOf(leftSec, carrotManFields.value.max_left_sec),
                carrot_left_sec = leftSec,
                amap_traffic_light_status = trafficLightStatus,
                amap_traffic_light_dir = direction,
                amap_green_light_last_second = greenLightCountDown,
                amap_wait_round = waitRound,
                lastUpdateTime = System.currentTimeMillis()
            )

            if (stateChanged) {
                val directionDesc = getTrafficLightDirectionDesc(direction)
                Log.v(TAG, "🚦 交通灯状态变化: state=$carrotTrafficState, left=$leftSec, dir=$directionDesc")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理红绿灯信息失败: ${e.message}", e)
        }
    }
}


