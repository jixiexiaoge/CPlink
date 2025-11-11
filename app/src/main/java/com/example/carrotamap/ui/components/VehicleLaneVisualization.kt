package com.example.carrotamap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.carrotamap.XiaogeVehicleData
import kotlin.math.abs
import kotlin.math.ln
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/**
 * 车辆和车道可视化弹窗组件 - 优化版
 * 绘制4条车道线（3个车道），当前车辆，前车，曲率弯曲，盲区高亮
 * 并显示核心数据信息
 * 只有用户类型3（赞助者）或4（铁粉）才自动显示
 */
@Composable
fun VehicleLaneVisualization(
    data: XiaogeVehicleData?,
    userType: Int,
    showDialog: Boolean, // 改为必需参数，由外部控制
    onDismiss: () -> Unit // 改为必需参数，添加关闭回调
) {
    // 只有用户类型3或4才允许显示弹窗
    if (userType != 3 && userType != 4) {
        return
    }
    
    if (showDialog) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            val context = LocalContext.current
            val density = LocalDensity.current
            val screenWidth = context.resources.displayMetrics.widthPixels
            val dialogWidth = with(density) { (screenWidth * 0.9f).toDp() }  // 宽度为屏幕的90%
            
            // 🆕 数据更新频率控制：限制为10Hz（每100ms更新一次）
            var displayData by remember { mutableStateOf(data) }
            LaunchedEffect(data) {
                delay(100) // 限制为10Hz
                displayData = data
            }
            
            // 🆕 数据一致性检查：计算数据年龄和延迟
            // 注意：由于网络延迟和数据处理时间，正常延迟可能在1000-2000ms范围内
            // 只有当延迟超过2000ms时才认为数据异常
            val currentTime = System.currentTimeMillis()
            val dataTimestamp = (displayData?.timestamp ?: 0.0) * 1000.0 // 转换为毫秒
            val dataAge = currentTime - dataTimestamp.toLong()
            val isDataStale = dataAge > 2000 // 超过2000ms认为数据延迟（提高阈值）
            
            Card(
                modifier = Modifier
                    .width(dialogWidth)
                    .wrapContentHeight()
                    .padding(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F172A), // 深蓝黑色
                                    Color(0xFF1E293B), // 中蓝黑色
                                    Color(0xFF0F172A)  // 深蓝黑色
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 顶部标题栏
                        TopBar(
                            data = displayData,
                            dataAge = dataAge,
                            isDataStale = isDataStale,
                            onClose = onDismiss
                        )
                        
                        // 车道可视化画布（占据较小区域）
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp), // 🆕 调整为1.5倍高度（200dp * 1.5 = 300dp）
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            // 🆕 优化车辆图片资源处理：支持多种格式和分辨率
                            val carBitmap: ImageBitmap? = remember(context) {
                                runCatching {
                                    // 优先尝试加载 drawable 资源
                                    var resId = context.resources.getIdentifier("car", "drawable", context.packageName)
                                    if (resId == 0) {
                                        // 如果 drawable 不存在，尝试 mipmap
                                        resId = context.resources.getIdentifier("car", "mipmap", context.packageName)
                                    }
                                    if (resId != 0) {
                                        ImageBitmap.imageResource(context.resources, resId)
                                    } else {
                                        null
                                    }
                                }.getOrNull()
                            }
                            
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                drawLaneVisualization(displayData, carBitmap)
                            }
                        }
                        
                        // 数据信息面板（底部显示）
                        DataInfoPanel(
                            data = displayData,
                            dataAge = dataAge,
                            isDataStale = isDataStale,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶部标题栏
 * 🆕 优化：添加超车状态指示和数据延迟显示
 */
@Composable
private fun TopBar(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：超车状态信息和决策原因
        val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
        val overtakeStatus = data?.overtakeStatus
        val statusText = when {
            laneChangeState != 0 -> {
                val direction = when (data?.modelV2?.meta?.laneChangeDirection) {
                    -1 -> "左"
                    1 -> "右"
                    else -> ""
                }
                "变道中($direction)"
            }
            overtakeStatus != null -> overtakeStatus.statusText
            else -> "监控中"
        }
        val statusColor = when {
            laneChangeState != 0 -> Color(0xFF3B82F6)  // 变道中：蓝色
            overtakeStatus?.canOvertake == true -> Color(0xFF10B981)  // 可超车：绿色
            overtakeStatus?.cooldownRemaining != null && overtakeStatus.cooldownRemaining > 0 -> Color(0xFFF59E0B)  // 冷却中：橙色
            else -> Color(0xFF94A3B8)  // 监控中：灰色
        }
        
        // 🆕 显示超车决策原因（如果有）
        val blockingReason = overtakeStatus?.blockingReason
        
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = statusColor.copy(alpha = 0.2f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = statusColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                // 显示冷却时间（如果有）
                overtakeStatus?.cooldownRemaining?.let { cooldown ->
                    if (cooldown > 0) {
                        Text(
                            text = "冷却: ${(cooldown / 1000.0).toInt()}s",
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Light
                        )
                    }
                }
                // 🆕 显示阻止原因（如果有）
                blockingReason?.let { reason ->
                    Text(
                        text = reason,
                        fontSize = 9.sp,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        // 右侧：网络状态和关闭按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🆕 网络连接状态
            // 如果数据延迟很大（超过5000ms），说明未接收到数据，显示"断开"
            val isDisconnected = dataAge > 5000
            val networkColor = when {
                isDisconnected -> Color(0xFFEF4444)  // 断开：红色
                isDataStale -> Color(0xFFF59E0B)     // 延迟：橙色
                else -> Color(0xFF10B981)            // 正常：绿色
            }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = networkColor.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = networkColor,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = when {
                            isDisconnected -> "断开"
                            isDataStale -> "${dataAge}ms"
                            else -> "正常"
                        },
                        fontSize = 10.sp,
                        color = networkColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // 关闭按钮
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color(0xFF334155)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 绘制车道可视化（优化版）
 */
private fun DrawScope.drawLaneVisualization(
    data: XiaogeVehicleData?, 
    carBitmap: ImageBitmap?
) {
    val width = size.width
    val height = size.height
    
    // 绘制道路背景渐变
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF475569).copy(alpha = 0.3f),
                Color(0xFF334155).copy(alpha = 0.5f),
                Color(0xFF1E293B).copy(alpha = 0.7f)
            )
        )
    )
    
    // 计算车道参数
    val laneWidth = width / 3.5f
    val centerX = width / 2f
    
    // 车道线底部和顶部的X位置（加入透视收敛效果）
    val perspectiveScaleTop = 0.6f
    val laneWidthTop = laneWidth * perspectiveScaleTop
    // 底部（靠近用户）更宽，顶部更窄，营造后俯视透视
    val lane1BottomX = centerX - laneWidth * 1.5f
    val lane2BottomX = centerX - laneWidth * 0.5f
    val lane3BottomX = centerX + laneWidth * 0.5f
    val lane4BottomX = centerX + laneWidth * 1.5f
    val lane1TopX = centerX - laneWidthTop * 1.5f
    val lane2TopX = centerX - laneWidthTop * 0.5f
    val lane3TopX = centerX + laneWidthTop * 0.5f
    val lane4TopX = centerX + laneWidthTop * 1.5f
    
    // 获取数据
    val curvature = data?.modelV2?.curvature
    val curvatureRate = curvature?.maxOrientationRate ?: 0f
    val curvatureDirection = curvature?.direction ?: 0
    
    // 绘制盲区高亮（需要随曲率弯曲）
    drawLaneBackgrounds(
        leftBlindspot = data?.carState?.leftBlindspot == true,
        rightBlindspot = data?.carState?.rightBlindspot == true,
        laneWidth = laneWidth,
        centerX = centerX,
        width = width,
        height = height,
        curvatureRate = curvatureRate,
        curvatureDirection = curvatureDirection
    )
    
    // 绘制距离标记
    drawDistanceMarkers(centerX, laneWidth * 1.5f)
    
    // 🆕 绘制弯曲车道线（根据曲率逐点弯曲）
    val leftLaneProb = data?.modelV2?.laneLineProbs?.getOrNull(0) ?: 0f
    val rightLaneProb = data?.modelV2?.laneLineProbs?.getOrNull(1) ?: 0f
    
    drawPerspectiveCurvedLaneLine(lane1BottomX, lane1TopX, curvatureRate, curvatureDirection, Color(0xFF64748B).copy(alpha = 0.5f))
    drawPerspectiveCurvedLaneLine(lane2BottomX, lane2TopX, curvatureRate, curvatureDirection, Color(0xFFFBBF24).copy(alpha = leftLaneProb.coerceIn(0.5f, 1f)))
    drawPerspectiveCurvedLaneLine(lane3BottomX, lane3TopX, curvatureRate, curvatureDirection, Color(0xFFFBBF24).copy(alpha = rightLaneProb.coerceIn(0.5f, 1f)))
    drawPerspectiveCurvedLaneLine(lane4BottomX, lane4TopX, curvatureRate, curvatureDirection, Color(0xFF64748B).copy(alpha = 0.5f))
    
    // 绘制前车（使用车辆图片）
    val lead0 = data?.modelV2?.lead0
    val leadOne = data?.radarState?.leadOne
    if (lead0 != null && lead0.prob > 0.5f && lead0.x > 0f) {
        val leadSpeedKmh = (leadOne?.vLead ?: 0f) * 3.6f
        val leadDistance = lead0.x
        // 根据曲率计算前车的旋转角度（曲率偏移在 drawLeadVehicle 内部计算）
        val leadRotationAngle = calculateVehicleRotationAngle(
            curvatureRate,
            curvatureDirection
        )
        drawLeadVehicle(
            leadDistance = leadDistance,
            leadSpeedKmh = leadSpeedKmh,
            centerX = centerX,
            laneWidth = laneWidth,
            curvatureRate = curvatureRate,
            curvatureDirection = curvatureDirection,
            width = width,
            vRel = leadOne?.vRel ?: 0f,
            carBitmap = carBitmap,
            rotationAngle = leadRotationAngle
        )
    }
    
    // 绘制当前车辆
    val vEgoKmh = (data?.carState?.vEgo ?: 0f) * 3.6f
    drawCurrentVehicle(centerX, laneWidth, carBitmap, vEgoKmh, curvatureRate, curvatureDirection)
}

/**
 * 绘制距离标记
 */
private fun DrawScope.drawDistanceMarkers(centerX: Float, laneAreaWidth: Float) {
    val height = size.height
    val distances = listOf(20f, 40f, 60f, 80f)
    val maxDistance = 80f
    
    distances.forEach { distance ->
        val normalizedDistance = distance / maxDistance
        val y = height * (1f - normalizedDistance) * 0.7f
        
        // 绘制标记线
        drawLine(
            color = Color(0xFF64748B).copy(alpha = 0.3f),
            start = Offset(centerX - laneAreaWidth - 20f, y),
            end = Offset(centerX - laneAreaWidth - 5f, y),
            strokeWidth = 1.dp.toPx()
        )
        
        drawLine(
            color = Color(0xFF64748B).copy(alpha = 0.3f),
            start = Offset(centerX + laneAreaWidth + 5f, y),
            end = Offset(centerX + laneAreaWidth + 20f, y),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * 🆕 绘制弯曲车道线（根据曲率逐点弯曲，参考 openpilot 实现）
 * 每个点的偏移量随距离变化，形成真实的曲线效果
 */
private fun DrawScope.drawPerspectiveCurvedLaneLine(
    laneBottomX: Float,
    laneTopX: Float,
    curvatureRate: Float,
    curvatureDirection: Int,
    color: Color
) {
    val height = size.height
    val steps = 80
    val path = Path()
    val maxDistance = 80f  // 最大距离80米
    
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val y = height * (1f - t)
        val xBase = lerp(laneBottomX, laneTopX, t)
        
        // 🆕 根据距离计算曲率偏移（参考 openpilot 的实现）
        val distance = t * maxDistance
        val curvatureAtDistance = calculateCurvatureAtDistance(
            curvatureRate,
            curvatureDirection,
            distance,
            size.width
        )
        val x = xBase + curvatureAtDistance
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    )
}

/**
 * 🆕 计算特定距离处的曲率偏移（参考 openpilot 的曲率计算）
 * 使用二次函数模拟曲线，让车道线根据距离逐渐弯曲
 */
private fun calculateCurvatureAtDistance(
    curvatureRate: Float,
    direction: Int,
    distance: Float,
    width: Float
): Float {
    if (abs(curvatureRate) < 0.01f || distance < 0.1f) return 0f
    
    // 使用二次函数模拟曲线（参考 openpilot 的曲率计算）
    // 曲率随距离的平方增长，模拟真实的道路弯曲
    // 🆕 修复：减小曲率系数，避免曲率过大
    val curvature = curvatureRate * 0.3f  // 从0.5f减小到0.3f
    val normalizedCurvature = (curvature / 0.02f).coerceIn(-1f, 1f)
    val maxOffset = width * 0.12f  // 从0.15f减小到0.12f
    val offset = normalizedCurvature * distance * distance * 0.005f * maxOffset  // 从0.01f减小到0.005f
    
    return if (direction > 0) offset else -offset
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}

/**
 * 绘制车道背景（盲区高亮）
 * 🆕 修复：盲区高亮随曲率弯曲，而不是简单的矩形
 */
private fun DrawScope.drawLaneBackgrounds(
    leftBlindspot: Boolean,
    rightBlindspot: Boolean,
    laneWidth: Float,
    centerX: Float,
    width: Float,
    height: Float,
    curvatureRate: Float,
    curvatureDirection: Int
) {
    val maxDistance = 80f
    val steps = 40  // 使用较少的步数以优化性能
    
    if (leftBlindspot) {
        val leftLaneLeftBottom = centerX - laneWidth * 1.5f
        val leftLaneRightBottom = centerX - laneWidth * 0.5f
        val perspectiveScaleTop = 0.6f
        val leftLaneLeftTop = centerX - laneWidth * perspectiveScaleTop * 1.5f
        val leftLaneRightTop = centerX - laneWidth * perspectiveScaleTop * 0.5f
        
        // 绘制左侧盲区（随曲率弯曲，使用渐变填充）
        for (i in 0 until steps) {
            val t1 = i / steps.toFloat()
            val t2 = (i + 1) / steps.toFloat()
            val y1 = height * (1f - t1)
            val y2 = height * (1f - t2)
            val distance1 = t1 * maxDistance
            val distance2 = t2 * maxDistance
            
            val leftX1Base = lerp(leftLaneLeftBottom, leftLaneLeftTop, t1)
            val leftX2Base = lerp(leftLaneLeftBottom, leftLaneLeftTop, t2)
            val leftX1 = leftX1Base + calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance1, width)
            val leftX2 = leftX2Base + calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance2, width)
            
            val rightX1Base = lerp(leftLaneRightBottom, leftLaneRightTop, t1)
            val rightX2Base = lerp(leftLaneRightBottom, leftLaneRightTop, t2)
            val rightX1 = rightX1Base + calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance1, width)
            val rightX2 = rightX2Base + calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance2, width)
            
            val alpha = (0.1f + (1f - t1) * 0.2f).coerceIn(0.1f, 0.3f)
            val path = Path().apply {
                moveTo(leftX1, y1)
                lineTo(leftX2, y2)
                lineTo(rightX2, y2)
                lineTo(rightX1, y1)
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFFEF4444).copy(alpha = alpha),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }
    }
    
    if (rightBlindspot) {
        val rightLaneLeftBottom = centerX + laneWidth * 0.5f
        val rightLaneRightBottom = centerX + laneWidth * 1.5f
        val perspectiveScaleTop = 0.6f
        val rightLaneLeftTop = centerX + laneWidth * perspectiveScaleTop * 0.5f
        val rightLaneRightTop = centerX + laneWidth * perspectiveScaleTop * 1.5f
        
        // 绘制右侧盲区（随曲率弯曲）
        for (i in 0 until steps) {
            val t1 = i / steps.toFloat()
            val t2 = (i + 1) / steps.toFloat()
            val y1 = height * (1f - t1)
            val y2 = height * (1f - t2)
            val distance1 = t1 * maxDistance
            val distance2 = t2 * maxDistance
            
            val leftX1Base = lerp(rightLaneLeftBottom, rightLaneLeftTop, t1)
            val leftX2Base = lerp(rightLaneLeftBottom, rightLaneLeftTop, t2)
            val leftX1 = leftX1Base + calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance1, width)
            val leftX2 = leftX2Base + calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance2, width)
            
            val rightX1Base = lerp(rightLaneRightBottom, rightLaneRightTop, t1)
            val rightX2Base = lerp(rightLaneRightBottom, rightLaneRightTop, t2)
            val rightX1 = rightX1Base + calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance1, width)
            val rightX2 = rightX2Base + calculateCurvatureAtDistance(curvatureRate, curvatureDirection, distance2, width)
            
            val alpha = (0.1f + (1f - t1) * 0.2f).coerceIn(0.1f, 0.3f)
            val path = Path().apply {
                moveTo(leftX1, y1)
                lineTo(leftX2, y2)
                lineTo(rightX2, y2)
                lineTo(rightX1, y1)
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFFEF4444).copy(alpha = alpha),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }
    }
}

/**
 * 计算车辆旋转角度（根据曲率）
 * 曲率越大，旋转角度越大，但限制在合理范围内（±15度）
 */
private fun calculateVehicleRotationAngle(
    curvatureRate: Float,
    curvatureDirection: Int
): Float {
    if (abs(curvatureRate) < 0.01f) return 0f
    
    // 根据曲率计算旋转角度（弧度转角度）
    // 曲率越大，角度越大，但限制在±15度以内
    val curvature = curvatureRate * 0.3f  // 与车道线使用相同的系数
    val normalizedCurvature = (curvature / 0.02f).coerceIn(-1f, 1f)
    
    // 最大旋转角度为15度（约0.26弧度）
    val maxRotationDegrees = 15f
    val rotationDegrees = normalizedCurvature * maxRotationDegrees
    
    // 根据方向确定旋转方向
    // curvatureDirection > 0 表示右弯，车辆应该向右旋转（正角度）
    // curvatureDirection < 0 表示左弯，车辆应该向左旋转（负角度）
    return if (curvatureDirection > 0) rotationDegrees else -rotationDegrees
}

/**
 * 绘制前车（优化版，使用车辆图片）
 * 🆕 修复：前车使用车辆图片，并随曲率弯曲
 * 🆕 优化：在前车图片下方显示"车速/距离"文本
 * 🆕 优化：车辆图片随道路曲率微调方向
 */
private fun DrawScope.drawLeadVehicle(
    leadDistance: Float,
    leadSpeedKmh: Float,
    centerX: Float,
    laneWidth: Float,
    curvatureRate: Float,
    curvatureDirection: Int,
    width: Float,
    vRel: Float,
    carBitmap: ImageBitmap?,
    rotationAngle: Float
) {
    val height = size.height
    
    val maxDistance = 80f
    val normalizedDistance = (leadDistance / maxDistance).coerceIn(0f, 1f)
    val logMappedDistance = if (normalizedDistance > 0f) {
        ln(1f + normalizedDistance * 2.718f) / ln(3.718f)
    } else {
        0f
    }
    val leadY = height * (1f - logMappedDistance) * 0.7f
    // 🆕 使用弯曲车道线的曲率计算方式，让前车位置也随距离弯曲
    val curvatureAtDistance = calculateCurvatureAtDistance(
        curvatureRate,
        curvatureDirection,
        leadDistance,
        size.width
    )
    val leadX = centerX + curvatureAtDistance
    
    val vehicleWidth = (laneWidth * 0.6f) * (1f - normalizedDistance * 0.4f)
    val aspectRatio = if (carBitmap != null) carBitmap.height.toFloat() / carBitmap.width.toFloat() else 1.6f
    val vehicleHeight = vehicleWidth * aspectRatio
    
    // 绘制车辆阴影
    drawOval(
        color = Color.Black.copy(alpha = 0.2f * (1f - normalizedDistance * 0.5f)),
        topLeft = Offset(leadX - vehicleWidth / 2f - 2f, leadY + vehicleHeight / 2f + 2f),
        size = Size(vehicleWidth + 4f, 12f * (1f - normalizedDistance * 0.3f))
    )
    
    // 🆕 使用车辆图片绘制前车，并随曲率旋转
    // 使用 drawIntoCanvas 和 nativeCanvas 来应用旋转变换
    if (carBitmap != null) {
        drawIntoCanvas { canvas ->
            canvas.save()
            // 移动到旋转中心
            canvas.translate(leadX, leadY)
            // 旋转
            canvas.rotate(rotationAngle)
            // 移回原点
            canvas.translate(-leadX, -leadY)
            
            // 绘制车辆图片（图片本身已包含所有视觉效果，无需额外绘制边框或车窗）
            drawImage(
                image = carBitmap,
                dstSize = androidx.compose.ui.unit.IntSize(
                    vehicleWidth.toInt(),
                    vehicleHeight.toInt()
                ),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    (leadX - vehicleWidth / 2f).toInt(),
                    (leadY - vehicleHeight / 2f).toInt()
                ),
                alpha = 1.0f,
                blendMode = BlendMode.SrcOver,
                filterQuality = FilterQuality.High
            )
            
            canvas.restore()
        }
    } else {
        // 回退方案：如果没有车辆图片，使用简化的颜色矩形（仅用于开发/调试）
        val vehicleColor = when {
            vRel < -5f -> Color(0xFFEF4444) // 接近过快，红色
            vRel < -2f -> Color(0xFFF59E0B) // 接近中等，橙色
            else -> Color(0xFF10B981) // 安全，绿色
        }
        
        // 绘制车辆主体（渐变）
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    vehicleColor.copy(alpha = 0.9f),
                    vehicleColor,
                    vehicleColor.copy(alpha = 0.8f)
                )
            ),
            topLeft = Offset(leadX - vehicleWidth / 2f, leadY - vehicleHeight / 2f),
            size = Size(vehicleWidth, vehicleHeight)
        )
        
        // 绘制车辆轮廓
        drawRect(
            color = vehicleColor.copy(alpha = 0.5f),
            topLeft = Offset(leadX - vehicleWidth / 2f, leadY - vehicleHeight / 2f),
            size = Size(vehicleWidth, vehicleHeight),
            style = Stroke(width = 2.dp.toPx())
        )
    }
    
    // 🆕 在前车图片下方绘制"车速/距离"文本
    val textY = leadY + vehicleHeight / 2f + 8f
    val fontSize = 10.dp.toPx() * (1f - normalizedDistance * 0.2f).coerceIn(0.7f, 1f)  // 根据距离调整字体大小
    val text = if (leadSpeedKmh > 0.1f) {
        "${leadSpeedKmh.toInt()}km/h / ${leadDistance.toInt()}m"
    } else {
        "${leadDistance.toInt()}m"
    }
    
    // 绘制文本背景（半透明圆角矩形）
    val textPadding = 4.dp.toPx()
    val textWidth = text.length * fontSize * 0.6f  // 估算文本宽度
    drawRoundRect(
        color = Color(0xFF1E293B).copy(alpha = 0.85f),
        topLeft = Offset(leadX - textWidth / 2f - textPadding, textY - fontSize / 2f - textPadding),
        size = Size(textWidth + textPadding * 2f, fontSize + textPadding * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )
    
    // 绘制文本
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawText(text, leadX, textY + fontSize / 3f, paint)
    }
}

/**
 * 绘制当前车辆（优化版，3D效果）
 * 🆕 优化：在车辆图片下方显示车速文本
 * 🆕 优化：车辆图片随道路曲率微调方向
 */
private fun DrawScope.drawCurrentVehicle(
    centerX: Float,
    laneWidth: Float,
    carBitmap: ImageBitmap?,
    vEgoKmh: Float,
    curvatureRate: Float,
    curvatureDirection: Int
) {
    val height = size.height
    
    val vehicleWidth = laneWidth * 0.9f
    val aspectRatio = if (carBitmap != null) carBitmap.height.toFloat() / carBitmap.width.toFloat() else 1.8f
    val vehicleHeight = vehicleWidth * aspectRatio
    val vehicleY = height - vehicleHeight / 2f - 24f
    
    // 地面阴影（更轻、更小，避免显得一块黑色区域）
    if (carBitmap == null) {
        // 仅在无图片回退时绘制明显阴影
        drawOval(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(centerX - vehicleWidth / 2f - 6f, vehicleY + vehicleHeight / 2f + 6f),
            size = Size(vehicleWidth + 12f, 20f)
        )
    } else {
        // 使用更轻的阴影以配合位图自带阴影/高光
        drawOval(
            color = Color.Black.copy(alpha = 0.12f),
            topLeft = Offset(centerX - vehicleWidth / 2f - 4f, vehicleY + vehicleHeight / 2f + 4f),
            size = Size(vehicleWidth + 8f, 16f)
        )
    }
    
    // 🆕 计算本车的旋转角度（根据曲率）
    val rotationAngle = calculateVehicleRotationAngle(curvatureRate, curvatureDirection)
    
    if (carBitmap != null) {
        // 🆕 绘制车辆图片（从后俯视），并随曲率旋转
        // 使用 drawIntoCanvas 和 nativeCanvas 来应用旋转变换
        drawIntoCanvas { canvas ->
            canvas.save()
            // 移动到旋转中心
            canvas.translate(centerX, vehicleY)
            // 旋转
            canvas.rotate(rotationAngle)
            // 移回原点
            canvas.translate(-centerX, -vehicleY)
            
            // 绘制车辆图片
            drawImage(
                image = carBitmap,
                dstSize = androidx.compose.ui.unit.IntSize(
                    vehicleWidth.toInt(),
                    vehicleHeight.toInt()
                ),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    (centerX - vehicleWidth / 2f).toInt(),
                    (vehicleY - vehicleHeight / 2f).toInt()
                ),
                alpha = 1.0f,
                blendMode = BlendMode.SrcOver,
                filterQuality = FilterQuality.High
            )
            
            canvas.restore()
        }
    } else {
        // 资源缺失时的回退：绘制简化的蓝色渐变车身
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF60A5FA),
                    Color(0xFF3B82F6),
                    Color(0xFF2563EB)
                )
            ),
            topLeft = Offset(centerX - vehicleWidth / 2f, vehicleY - vehicleHeight / 2f),
            size = Size(vehicleWidth, vehicleHeight)
        )
        drawRect(
            color = Color(0xFF1E40AF),
            topLeft = Offset(centerX - vehicleWidth / 2f, vehicleY - vehicleHeight / 2f),
            size = Size(vehicleWidth, vehicleHeight),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
    
    // 🆕 在当前车辆图片下方绘制车速文本
    val textY = vehicleY + vehicleHeight / 2f + 10f
    val fontSize = 12.dp.toPx()
    val text = "${vEgoKmh.toInt()}km/h"
    
    // 绘制文本背景（半透明圆角矩形）
    val textPadding = 5.dp.toPx()
    val textWidth = text.length * fontSize * 0.6f  // 估算文本宽度
    drawRoundRect(
        color = Color(0xFF1E293B).copy(alpha = 0.9f),
        topLeft = Offset(centerX - textWidth / 2f - textPadding, textY - fontSize / 2f - textPadding),
        size = Size(textWidth + textPadding * 2f, fontSize + textPadding * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
    )
    
    // 绘制文本
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawText(text, centerX, textY + fontSize / 3f, paint)
    }
}

/**
 * 数据信息面板（优化版）
 * 🆕 优化：添加数据延迟显示
 */
@Composable
private fun DataInfoPanel(
    data: XiaogeVehicleData?,
    dataAge: Long,
    isDataStale: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 🆕 简化数据信息显示：只保留核心决策数据
        // 注意：车速、前车距离、前车速度已显示在车辆图片下方，这里不再重复显示
        
        // 🆕 车道变更进度条（当变道中时显示）
        val laneChangeState = data?.modelV2?.meta?.laneChangeState ?: 0
        if (laneChangeState == 1) {
            // 变道中，显示进度条（模拟进度，实际需要从 openpilot 获取）
            // 注意：openpilot 可能不提供精确的进度值，这里使用时间估算
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "变道中...",
                        fontSize = 12.sp,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 进度条（使用不确定进度，因为 openpilot 可能不提供精确进度）
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF3B82F6),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }
        
        // 第一行：前车相对速度、前车状态、系统状态
        // 🆕 优化：显示前车相对速度，更实用
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 🆕 前车相对速度（替换本车加速度）
            val lead0 = data?.modelV2?.lead0
            val leadOne = data?.radarState?.leadOne
            // 判断是否有前车：lead0存在且置信度>0.5且距离>0，或者leadOne状态为true
            val hasLead = (lead0 != null && lead0.prob > 0.5f && lead0.x > 0f) || (leadOne?.status == true)
            
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "相对速度",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    if (hasLead) {
                        // 有前车时显示相对速度
                        val vRel = leadOne?.vRel ?: 0f  // 相对速度 (m/s)，正值表示前车更快，负值表示本车更快
                        val vRelKmh = vRel * 3.6f  // 转换为 km/h
                        val vRelText = when {
                            vRel > 2f -> "远离"
                            vRel < -2f -> "接近"
                            else -> "保持"
                        }
                        val vRelValue = String.format("%.1f", abs(vRelKmh))
                        val vRelColor = when {
                            vRel < -5f -> Color(0xFFEF4444)  // 接近过快：红色
                            vRel < -2f -> Color(0xFFF59E0B)  // 接近：橙色
                            vRel > 5f -> Color(0xFF3B82F6)   // 远离过快：蓝色
                            else -> Color(0xFF10B981)        // 保持：绿色
                        }
                        Text(
                            text = vRelText,
                            fontSize = 12.sp,
                            color = vRelColor,
                            fontWeight = FontWeight.Bold
                        )
                        if (abs(vRelKmh) > 0.5f) {
                            Text(
                                text = "${if (vRel > 0) "+" else "-"}${vRelValue}km/h",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "0km/h",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        // 无前车时显示"无车"
                        Text(
                            text = "无车",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // 前车状态（保留在中间）
            // 🆕 修复：只有在有前车时才显示前车状态，否则显示"无车"
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "前车状态",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    if (hasLead) {
                        // 有前车时显示前车加速度状态
                        val lead0Accel = data?.modelV2?.lead0?.a ?: 0f
                        val leadAccelText = when {
                            lead0Accel > 0.5f -> "加速"
                            lead0Accel < -0.5f -> "减速"
                            else -> "匀速"
                        }
                        val leadAccelColor = when {
                            lead0Accel > 0.5f -> Color(0xFF10B981)
                            lead0Accel < -0.5f -> Color(0xFFEF4444)
                            else -> Color(0xFF94A3B8)
                        }
                        Text(
                            text = leadAccelText,
                            fontSize = 12.sp,
                            color = leadAccelColor,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        // 无前车时显示"无车"
                        Text(
                            text = "无车",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // 🆕 系统状态（从顶部移动到底部）
            val enabled = data?.systemState?.enabled == true
            val active = data?.systemState?.active == true
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "系统状态",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (enabled && active) Color(0xFF10B981) else Color(0xFF64748B),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            text = if (enabled && active) "激活" else "待机",
                            fontSize = 12.sp,
                            color = if (enabled && active) Color(0xFF10B981) else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // 第二行：曲率信息、超车设置、道路类型
        // 🆕 优化：移除重复的"变道状态"（顶部已显示），改为显示曲率信息
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 🆕 曲率信息（替换变道状态）
            val curvature = data?.modelV2?.curvature
            val curvatureRate = curvature?.maxOrientationRate ?: 0f
            val curvatureDirection = curvature?.direction ?: 0
            val curvatureText = when {
                abs(curvatureRate) < 0.01f -> "直道"
                curvatureDirection > 0 -> "左弯"  // direction: 1=左转
                curvatureDirection < 0 -> "右弯"  // direction: -1=右转
                else -> "直道"
            }
            val curvatureValue = if (abs(curvatureRate) > 0.01f) {
                String.format("%.3f", abs(curvatureRate))
            } else {
                "0.000"
            }
            val curvatureColor = when {
                abs(curvatureRate) < 0.01f -> Color(0xFF94A3B8)
                abs(curvatureRate) < 0.02f -> Color(0xFF3B82F6)
                else -> Color(0xFFF59E0B)
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "道路曲率",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = curvatureText,
                        fontSize = 12.sp,
                        color = curvatureColor,
                        fontWeight = FontWeight.Bold
                    )
                    if (abs(curvatureRate) > 0.01f) {
                        Text(
                            text = curvatureValue,
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 🆕 超车设置（重命名，使其更清晰这是设置值）
            val prefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
            val overtakeMode = prefs.getInt("overtake_mode", 0)
            val overtakeModeNames = arrayOf("禁止超车", "拨杆超车", "自动超车")
            val overtakeModeColors = arrayOf(
                Color(0xFF94A3B8),
                Color(0xFF3B82F6),
                Color(0xFF22C55E)
            )
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "超车设置",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = overtakeModeNames[overtakeMode],
                        fontSize = 12.sp,
                        color = overtakeModeColors[overtakeMode],
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 道路类型指示
            val roadcate = data?.carrotMan?.roadcate ?: 0
            val roadTypeText = when (roadcate) {
                1 -> "高速"
                6 -> "快速"
                else -> "普通"
            }
            val roadTypeColor = when (roadcate) {
                1, 6 -> Color(0xFF10B981)
                else -> Color(0xFF94A3B8)
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "道路类型",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = roadTypeText,
                        fontSize = 12.sp,
                        color = roadTypeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // 🆕 超车决策原因显示（仅在不能超车且有原因时显示）
        // 注意：overtakeStatus 已在 TopBar 中获取，但这里需要独立获取以显示详细信息
        val overtakeStatusForReason = data?.overtakeStatus
        if (overtakeStatusForReason != null && !overtakeStatusForReason.canOvertake && overtakeStatusForReason.blockingReason != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF59E0B).copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ℹ️",
                        fontSize = 16.sp
                    )
                    Column {
                        Text(
                            text = "超车条件不满足",
                            fontSize = 12.sp,
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = overtakeStatusForReason.blockingReason,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // 🆕 可选第三行：数据延迟警告（仅在异常时显示）
        if (isDataStale) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 16.sp
                    )
                    Column {
                        Text(
                            text = "数据延迟警告",
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "数据延迟: ${dataAge}ms (超过2000ms)",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

