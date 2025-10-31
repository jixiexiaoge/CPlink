package com.example.carrotamap

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState

/**
 * 高德地图数据处理器 (简化版)
 * 只负责基础的数据解析和映射，不做复杂计算
 * 所有计算逻辑由Python端(carrot_serv.py)处理
 */
class AmapDataProcessor(
    private val context: Context,
    private val carrotManFields: MutableState<CarrotManFields>
) {
    companion object {
        private const val TAG = "AmapDataProcessor"
    }

    /**
     * 简化的倒计时更新 - 只做基础的数据映射
     * 所有复杂计算由Python端(carrot_serv.py)处理
     */
    fun updateTrafficCountdowns(segRemainDis: Int, segRemainTime: Int, totalRemainDis: Int, totalRemainTime: Int, currentSpeed: Double) {
        // 移除Android端的倒计时计算逻辑
        // Python端会处理所有倒计时计算和速度控制
        Log.d(TAG, "⏱️ 倒计时计算由Python端处理，Android只负责数据映射")
    }

    /**
     * 简化的速度控制更新 - 只做基础的数据映射
     * 所有复杂逻辑由Python端(carrot_serv.py)处理
     */
    fun updateSpeedControl() {
        // 移除Android端的速度选择逻辑
        // Python端(carrot_serv.py)的_update_sdi()方法会处理所有SDI逻辑
        Log.d(TAG, "🎯 速度控制由Python端处理，Android只负责数据映射")
    }

    /**
     * 道路限速更新 - 直接映射到CarrotMan字段
     * 所有复杂逻辑由Python端(carrot_serv.py)处理
     * 
     * 🆕 不再保存到SharedPreferences（悬浮窗已移除）
     * 所有数据都从carrotManFields实时获取
     */
    fun updateRoadSpeedLimit(newLimit: Int) {
        if (newLimit <= 0) return

        // 直接更新到carrotManFields，不进行变化检测
        carrotManFields.value = carrotManFields.value.copy(
            nRoadLimitSpeed = newLimit,
            lastUpdateTime = System.currentTimeMillis()
        )
        
        Log.d(TAG, "🚦 限速已更新: ${newLimit}km/h (实时更新到carrotManFields)")
    }
}
