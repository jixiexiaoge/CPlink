package com.example.carrotamap.examples

import com.example.carrotamap.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result 框架使用示例
 * 
 * 这个文件展示了如何在现有代码中渐进式地使用 Result 框架
 * 不需要一次性重写所有代码，可以逐步迁移
 */

// ============================================
// 示例 1: 简单的同步函数封装
// ============================================

/**
 * 旧代码风格 - 使用 try-catch
 */
fun parseSpeedOldWay(speedStr: String): Int {
    return try {
        speedStr.toInt()
    } catch (e: Exception) {
        android.util.Log.e("ParseSpeed", "解析失败", e)
        0 // 返回默认值
    }
}

/**
 * 新代码风格 - 使用 Result
 */
fun parseSpeedNewWay(speedStr: String): Result<Int> {
    return runSafely(ErrorCode.DATA_PARSE_ERROR) {
        speedStr.toInt()
    }
}

// 使用示例：
// when (val result = parseSpeedNewWay("120")) {
//     is Result.Success -> println("速度: ${result.data}")
//     is Result.Error -> println("错误: ${result.message}")
//     is Result.Loading -> println("加载中")
// }


// ============================================
// 示例 2: 网络请求封装
// ============================================

/**
 * 模拟网络请求 - 旧风格
 */
fun sendDataToServerOldWay(data: String): Boolean {
    return try {
        // 模拟网络请求
        if (data.isNotEmpty()) {
            true
        } else {
            throw IllegalArgumentException("数据为空")
        }
    } catch (e: Exception) {
        android.util.Log.e("Network", "发送失败", e)
        false
    }
}

/**
 * 模拟网络请求 - 新风格（使用 Result）
 */
suspend fun sendDataToServerNewWay(data: String): Result<Unit> = withContext(Dispatchers.IO) {
    runSafely(ErrorCode.NETWORK_CONNECTION_FAILED) {
        if (data.isEmpty()) {
            throw IllegalArgumentException("数据为空")
        }
        // 模拟网络请求
        // TODO: 实际的网络请求代码
    }
}

// 使用示例：
// viewModelScope.launch {
//     when (val result = sendDataToServerNewWay("test")) {
//         is Result.Success -> {
//             Log.d(TAG, "发送成功")
//             ErrorReporterInstance.reporter.log("数据发送成功")
//         }
//         is Result.Error -> {
//             Log.e(TAG, "发送失败: ${result.message}")
//             ErrorReporterInstance.reporter.reportError(result)
//         }
//         is Result.Loading -> {
//             // 显示加载状态
//         }
//     }
// }


// ============================================
// 示例 3: 链式操作（map 函数使用）
// ============================================

/**
 * 获取速度并转换单位
 */
fun getSpeedInMph(speedKmh: String): Result<Double> {
    return parseSpeedNewWay(speedKmh)
        .map { km -> km * 0.621371 }  // 转换为英里
}

// 使用示例：
// val speedResult = getSpeedInMph("120")
// val speed = speedResult.getOrDefault(0.0)
// println("Speed in MPH: $speed")


// ============================================
// 示例 4: 向后兼容的过渡方案
// ============================================

/**
 * 兼容层：将 Result 转换为旧代码期望的值
 * 这样可以让新代码与旧代码共存
 */
fun parseSpeedCompat(speedStr: String): Int {
    return parseSpeedNewWay(speedStr).getOrDefault(0)
}

/**
 * 兼容层：将 Result 转换为 Boolean
 */
suspend fun sendDataCompat(data: String): Boolean {
    return when (sendDataToServerNewWay(data)) {
        is Result.Success -> true
        is Result.Error -> false
        is Result.Loading -> false
    }
}


// ============================================
// 示例 5: 错误上报集成
// ============================================

/**
 * 带错误上报的网络请求
 */
suspend fun sendDataWithReporting(data: String, userId: String): Result<Unit> {
    // 设置用户上下文
    ErrorReporterInstance.reporter.setUserId(userId)
    ErrorReporterInstance.reporter.setCustomKey("data_size", data.length)
    
    val result = sendDataToServerNewWay(data)
    
    // 自动上报错误
    result.onError { error ->
        ErrorReporterInstance.reporter.reportError(error, "NetworkRequest")
    }
    
    return result
}


// ============================================
// 示例 6: 实际应用场景 - NetworkManager 改造示例
// ============================================

/**
 * 这是一个真实的改造示例，展示如何改造 NetworkManager 的发送方法
 * 
 * 改造策略：
 * 1. 创建新的 suspend 函数返回 Result
 * 2. 保留旧函数（调用新函数并转换结果）
 * 3. 逐步迁移调用方到新函数
 */
class NetworkManagerExample {
    
    // 旧方法：保持不变，确保向后兼容
    fun sendCarrotManDataOld() {
        try {
            // 原有代码...
            android.util.Log.d("Network", "数据发送成功")
        } catch (e: Exception) {
            android.util.Log.e("Network", "发送失败", e)
        }
    }
    
    // 新方法：返回 Result
    suspend fun sendCarrotManDataNew(): Result<Unit> = withContext(Dispatchers.IO) {
        runSafely(ErrorCode.NETWORK_CONNECTION_FAILED) {
            // 原有的发送逻辑...
            // 如果成功，直接返回 Unit
            // 如果失败，抛出异常会被 runSafely 捕获
        }
    }
    
    // 兼容方法：调用新方法，但提供旧接口
    suspend fun sendCarrotManDataCompat() {
        when (val result = sendCarrotManDataNew()) {
            is Result.Success -> {
                android.util.Log.d("Network", "数据发送成功")
            }
            is Result.Error -> {
                android.util.Log.e("Network", "发送失败: ${result.message}", result.exception)
                ErrorReporterInstance.reporter.reportError(result, "NetworkManager")
            }
            is Result.Loading -> {
                // 处理加载状态
            }
        }
    }
}


// ============================================
// 使用建议
// ============================================

/*
渐进式迁移步骤：

1. 第一阶段（不破坏现有代码）：
   - ✅ 引入 Result.kt 和 ErrorReporter.kt
   - ✅ 为新功能使用 Result 框架
   - ✅ 旧代码保持不变

2. 第二阶段（创建兼容层）：
   - 为关键方法创建返回 Result 的新版本
   - 保留旧方法，内部调用新方法并转换结果
   - 示例：sendCarrotManDataOld() 调用 sendCarrotManDataNew().getOrDefault()

3. 第三阶段（逐步迁移）：
   - 新功能优先使用 Result 版本
   - 逐个文件迁移现有代码
   - 使用 @Deprecated 标记旧方法

4. 第四阶段（清理）：
   - 当所有调用方都迁移后，删除旧方法
   - 完全使用 Result 框架

关键原则：
✅ 向后兼容 - 不破坏现有功能
✅ 渐进式 - 不需要一次性重写
✅ 类型安全 - 编译时发现错误
✅ 可测试 - 容易编写单元测试
*/
