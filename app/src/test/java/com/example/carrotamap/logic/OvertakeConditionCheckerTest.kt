package com.example.carrotamap.logic

import com.example.carrotamap.XiaogeVehicleData
import com.example.carrotamap.ModelV2Data
import com.example.carrotamap.ModelV2Meta
import com.example.carrotamap.CarStateData
import com.example.carrotamap.OvertakeStatusData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OvertakeConditionChecker 逻辑单元测试
 */
class OvertakeConditionCheckerTest {

    private lateinit var checker: OvertakeConditionChecker

    @Before
    fun setup() {
        checker = OvertakeConditionChecker()
    }

    @Test
    fun `test canOvertake returns true when all conditions are met`() {
        val data = createMockVehicleData(
            speed = 80f,          // > 60
            vLead = 65f,          // 速度差 15 > 10
            laneWidth = 3.2f,     // > 2.8
            laneProb = 0.9f       // > 0.7
        )
        
        val conditions = checker.getCheckConditions(data, 60f, 10f)
        
        // 验证关键指标是否满足
        assertTrue("速度应该满足", conditions.any { it.name == "本车速度" && it.isMet })
        assertTrue("速度差应该满足", conditions.any { it.name == "速度差" && it.isMet })
        assertTrue("车道线置信度应该满足", conditions.any { it.name == "车道线置信度" && it.isMet })
    }

    @Test
    fun `test canOvertake returns false when speed is too low`() {
        val data = createMockVehicleData(
            speed = 50f,          // < 60
            vLead = 40f,
            laneWidth = 3.2f,
            laneProb = 0.9f
        )
        
        val conditions = checker.getCheckConditions(data, 60f, 10f)
        
        val speedCondition = conditions.find { it.name == "本车速度" }
        assertFalse("速度太低时不应满足", speedCondition?.isMet ?: true)
    }

    @Test
    fun `test canOvertake returns false when speed diff is too small`() {
        val data = createMockVehicleData(
            speed = 80f,
            vLead = 75f,          // 速度差 5 < 10
            laneWidth = 3.2f,
            laneProb = 0.9f
        )
        
        val conditions = checker.getCheckConditions(data, 60f, 10f)
        
        val diffCondition = conditions.find { it.name == "速度差" }
        assertFalse("速度差太小时不应满足", diffCondition?.isMet ?: true)
    }

    /**
     * 辅助函数：创建模拟的车辆数据
     */
    private fun createMockVehicleData(
        speed: Float,
        vLead: Float,
        laneWidth: Float,
        laneProb: Float
    ): XiaogeVehicleData {
        // 由于 XiaogeVehicleData 结构比较复杂，这里模拟其关键字段
        val carState = CarStateData().apply {
            vEgo = speed / 3.6f // 转为 m/s
        }
        
        val modelV2 = ModelV2Data().apply {
            // 模拟车道线点
            laneLines = listOf(
                com.example.carrotamap.LaneLineData().apply { y = listOf(-laneWidth/2, -laneWidth/2) },
                com.example.carrotamap.LaneLineData().apply { y = listOf(laneWidth/2, laneWidth/2) }
            )
            // 模拟置信度
            laneLineProbs = listOf(laneProb, laneProb)
            // 模拟元数据
            meta = ModelV2Meta().apply {
                laneChangeState = 0
            }
        }
        
        return XiaogeVehicleData().apply {
            this.carState = carState
            this.modelV2 = modelV2
            // 模拟前车状态
            this.overtakeStatus = OvertakeStatusData().apply {
                this.vLead = vLead / 3.6f
                this.canOvertake = true // 只是 mock
            }
        }
    }
}
