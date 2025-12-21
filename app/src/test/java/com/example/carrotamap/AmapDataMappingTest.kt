package com.example.carrotamap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 高德地图数据映射测试
 * 测试 CAMERA_TYPE → nSdiType 映射的正确性
 * 
 * 基于 映射关系.md 文档中的映射规则
 */
class AmapDataMappingTest {
    
    @Test
    fun `测试超速拍照映射 CAMERA_TYPE_0 到 nSdiType_1`() {
        // Given
        val cameraType = 0 // 超速拍照
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
        
        // Then
        assertThat(nSdiType).isEqualTo(1) // 固定测速摄像头
    }
    
    @Test
    fun `测试区间测速启点映射 CAMERA_TYPE_8 到 nSdiType_2`() {
        // Given
        val cameraType = 8 // 区间限速启点
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
        
        // Then
        assertThat(nSdiType).isEqualTo(2) // 区间测速开始
    }
    
    @Test
    fun `测试区间测速终点映射 CAMERA_TYPE_9 到 nSdiType_3`() {
        // Given
        val cameraType = 9 // 区间限速终点
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
        
        // Then
        assertThat(nSdiType).isEqualTo(3) // 区间测速结束
    }
    
    @Test
    fun `测试闯红灯拍照映射 CAMERA_TYPE_2 到 nSdiType_6`() {
        // Given
        val cameraType = 2 // 闯红灯拍照
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
        
        // Then
        assertThat(nSdiType).isEqualTo(6) // 闯红灯拍照
    }
    
    @Test
    fun `测试流动测速电子眼映射 CAMERA_TYPE_10 到 nSdiType_7`() {
        // Given
        val cameraType = 10 // 流动测速电子眼
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
        
        // Then
        assertThat(nSdiType).isEqualTo(7) // 流动测速摄像头
    }
    
    @Test
    fun `测试所有直接引用的映射 CAMERA_TYPE_14到65`() {
        // 7, 14-65 的直接引用（跳过不存在的类型）
        val directMappingTypes = listOf(7) + (14..65).toList()
        
        directMappingTypes.forEach { cameraType ->
            // When
            val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
            
            // Then - 直接引用时，值应该相等
            assertThat(nSdiType).isEqualTo(cameraType)
        }
    }
    
    @Test
    fun `测试公交专用道摄像头映射 CAMERA_TYPE_4 到 nSdiType_9`() {
        // Given
        val cameraType = 4 // 公交专用道摄像头
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
        
        // Then
        assertThat(nSdiType).isEqualTo(9) // 公交专用车道区间
    }
    
    @Test
    fun `测试应急车道拍照映射 CAMERA_TYPE_5 到 nSdiType_11`() {
        // Given
        val cameraType = 5 // 应急车道拍照
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
        
        // Then
        assertThat(nSdiType).isEqualTo(11) // 应急车道拍照
    }
    
    @Test
    fun `测试未知类型映射到 nSdiType_66`() {
        // Given - 未定义的类型
        val unknownType = 999
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(unknownType)
        
        // Then
        assertThat(nSdiType).isEqualTo(66) // 空/忽略
    }
    
    @Test
    fun `测试负数类型映射到 nSdiType_66`() {
        // Given
        val negativeType = -1
        
        // When
        val nSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(negativeType)
        
        // Then
        assertThat(nSdiType).isEqualTo(66) // 空/忽略
    }
    
    @Test
    fun `测试所有需要映射的类型 0到13`() {
        // 验证需要映射的类型（0-13，排除14）
        val mappings = mapOf(
            0 to 1,   // 超速拍照 → 固定测速摄像头
            1 to 14,  // 道路拍照 → 治安监控
            2 to 6,   // 闯红灯拍照 → 闯红灯拍照
            3 to 17,  // 违章拍照 → 违停拍照点
            4 to 9,   // 公交专用道摄像头 → 公交专用车道区间
            5 to 11,  // 应急车道拍照 → 应急车道拍照
            6 to 8,   // 测速拍照 → 测速拍照
            8 to 2,   // 区间限速启点 → 区间测速开始
            9 to 3,   // 区间限速终点 → 区间测速结束
            10 to 7,  // 流动测速电子眼 → 流动测速摄像头
            11 to 26, // ECT计费拍照 → ETC计费拍照
            12 to 41, // 人行道拍照 → 行人乱穿马路多发处
            13 to 41  // 礼让行人拍照 → 行人乱穿马路多发处
        )
        
        mappings.forEach { (cameraType, expectedSdiType) ->
            // When
            val actualSdiType = AmapBroadcastHandlers.mapAmapCameraTypeToSdi(cameraType)
            
            // Then
            assertThat(actualSdiType).isEqualTo(expectedSdiType)
        }
    }
}
