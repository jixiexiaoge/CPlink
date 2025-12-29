package com.example.carrotamap.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.example.carrotamap.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.example.carrotamap.XiaogeVehicleData
import com.example.carrotamap.CarrotManFields
import com.example.carrotamap.LeadData
import com.example.carrotamap.SystemStateData
import com.example.carrotamap.logic.OvertakeConditionChecker
import com.example.carrotamap.ui.utils.rememberPreference
import com.example.carrotamap.ui.utils.rememberFloatPreference
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val DATA_STALE_THRESHOLD_MS = 2000L
private const val DATA_DISCONNECTED_THRESHOLD_MS = 4000L

/**
 * 检查条件数据类
 */
data class CheckCondition(
    val name: String,
    val threshold: String,
    val actual: String,
    val isMet: Boolean
)

/**
 * 车道位置推断结果 (仅用于视觉模拟)
 */
data class VisualLanePositionResult(
    val currentLaneIndex: Int, // 当前车道索引 (1-based, 从左往右)
    val totalLanes: Int,       // 推断出的总车道数
    val laneDescription: String // 车道描述（如 "左起第 2 车道 / 共 3 车道"）
)

/**
 * 🆕 根据路缘和车道宽度推断当前车道位置
 */
private fun inferLanePosition(
    laneWidthLeft: Float,
    laneWidthRight: Float,
    roadEdgeLeft: Float,
    roadEdgeRight: Float
): VisualLanePositionResult {
    val defaultLaneWidth = 3.6f
    
    // 1. 推断左侧还有几条车道
    // 如果 laneWidthLeft > 0，说明左侧至少有一条完整车道
    // 如果 roadEdgeLeft 很大，可能包含多条车道
    val leftLanes = when {
        roadEdgeLeft > 0.5f -> {
            // 路缘距离包含左侧车道宽度
            val lanes = Math.round((roadEdgeLeft + (if (laneWidthLeft > 0.5f) defaultLaneWidth else 0f)) / defaultLaneWidth).toInt()
            Math.max(if (laneWidthLeft > 0.5f) 1 else 0, lanes)
        }
        laneWidthLeft > 0.5f -> 1
        else -> 0
    }
    
    // 2. 推断右侧还有几条车道
    val rightLanes = when {
        roadEdgeRight > 0.5f -> {
            val lanes = Math.round((roadEdgeRight + (if (laneWidthRight > 0.5f) defaultLaneWidth else 0f)) / defaultLaneWidth).toInt()
            Math.max(if (laneWidthRight > 0.5f) 1 else 0, lanes)
        }
        laneWidthRight > 0.5f -> 1
        else -> 0
    }
    
    val totalLanes = leftLanes + 1 + rightLanes
    val currentLaneIndex = leftLanes + 1
    
    val description = when {
        totalLanes == 1 -> "单车道"
        totalLanes > 1 -> "第 $currentLaneIndex / $totalLanes 车道"
        else -> "车道识别中"
    }
    
    return VisualLanePositionResult(currentLaneIndex, totalLanes, description)
}

/**
 * UI 常量配置
 */
private object UIConstants {
    val COLOR_SUCCESS = Color(0xFF10B981)
    val COLOR_WARNING = Color(0xFFF59E0B)
    val COLOR_DANGER = Color(0xFFEF4444)
    val COLOR_INFO = Color(0xFF3B82F6)
    val COLOR_NEUTRAL = Color(0xFF94A3B8)
    val CARD_BACKGROUND = Color(0xFF1E293B).copy(alpha = 0.8f)
    val CARD_SHAPE = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
}

@Composable
fun VehicleLaneVisualization(
    dataState: State<XiaogeVehicleData?>,
    userType: Int,
    showDialog: Boolean,
    onDismiss: () -> Unit,
    carrotManFields: CarrotManFields? = null,
    deviceIP: String? = null,
    isTcpConnected: Boolean = false
) {
    if ((userType != 3 && userType != 4) || !showDialog) return

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
        val dialogWidth = with(density) { (screenWidth * 0.9f).toDp() }
        
        val data by dataState
        var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
        
        LaunchedEffect(Unit) {
            while (true) {
                delay(100)
                currentTime = System.currentTimeMillis()
            }
        }
        
        val currentData = data
        val dataAge = when {
            currentData == null || currentData.receiveTime <= 0 -> DATA_DISCONNECTED_THRESHOLD_MS + 1000L
            else -> (currentTime - currentData.receiveTime).coerceAtLeast(0L)
        }
        val isDataStale = dataAge > DATA_STALE_THRESHOLD_MS
        
        val overtakeMode by rememberPreference("overtake_mode", 0)

        Card(
            modifier = Modifier.width(dialogWidth).wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A))
                        )
                    )
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TopBar(
                        dataAge = dataAge,
                        isDataStale = isDataStale,
                        overtakeMode = overtakeMode,
                        systemState = currentData?.systemState,
                        currentData = currentData,
                        deviceIP = deviceIP,
                        isTcpConnected = isTcpConnected,
                        onClose = onDismiss
                    )
                    
                    DataInfoPanel(
                        data = currentData,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    dataAge: Long,
    isDataStale: Boolean,
    overtakeMode: Int,
    systemState: SystemStateData?,
    currentData: XiaogeVehicleData?,
    deviceIP: String?,
    isTcpConnected: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(
                text = listOf("禁止", "拨杆", "自动")[overtakeMode.coerceIn(0, 2)],
                color = listOf(UIConstants.COLOR_NEUTRAL, UIConstants.COLOR_INFO, UIConstants.COLOR_SUCCESS)[overtakeMode.coerceIn(0, 2)]
            )
            
            val active = systemState?.active == true && systemState.enabled
            StatusBadge(
                text = if (active) "激活" else "待机",
                color = if (active) UIConstants.COLOR_SUCCESS else UIConstants.COLOR_NEUTRAL
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!deviceIP.isNullOrEmpty()) {
                StatusBadge(text = deviceIP, color = UIConstants.COLOR_INFO)
            }
            
            val (statusText, statusColor, statusIcon) = when {
                !isTcpConnected -> Triple("断开", Color(0xFFEF4444), "●")
                isDataStale -> Triple("延迟 ${String.format("%.1f", dataAge/1000f)}s", Color(0xFFF59E0B), "◐")
                systemState?.active == true -> Triple("正常", Color(0xFF10B981), "●")
                currentData?.carState != null -> Triple("准备", Color(0xFF3B82F6), "◔")
                else -> Triple("待机", Color(0xFF64748B), "○")
            }
            
            StatusBadge(text = statusText, color = statusColor, icon = statusIcon)
            
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color, icon: String? = null) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Text(text = icon, fontSize = 8.sp, color = color, fontWeight = FontWeight.Bold)
            } else {
                Box(modifier = Modifier.size(5.dp).background(color = color, shape = androidx.compose.foundation.shape.CircleShape))
            }
            Text(text = text, fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DataInfoPanel(data: XiaogeVehicleData?, modifier: Modifier = Modifier) {
    val minSpeed by rememberFloatPreference("overtake_param_min_speed_kph", 60f)
    val speedDiff by rememberFloatPreference("overtake_param_speed_diff_kph", 10f)
    
    val checker = remember { OvertakeConditionChecker() }
    val conditions = remember(data, minSpeed, speedDiff) {
        checker.getCheckConditions(data, minSpeed, speedDiff)
    }
    
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (data?.modelV2?.meta?.laneChangeState == 1) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp), color = Color(0xFF3B82F6))
        }
        
        // 上帝视角模拟图
        TopDownVisualization(data = data, modifier = Modifier.fillMaxWidth())
        
        CheckConditionTable(conditions)
    }
}

/**
 * 🆕 上帝视角模拟图组件
 */
@Composable
fun TopDownVisualization(data: XiaogeVehicleData?, modifier: Modifier = Modifier) {
    val meta = data?.modelV2?.meta
    val drivingIntent = data?.modelV2?.drivingIntent
    val carState = data?.carState
    val curvature = data?.modelV2?.curvature
    
    val laneWidthLeft = meta?.laneWidthLeft ?: 0f
    val laneWidthRight = meta?.laneWidthRight ?: 0f
    val roadEdgeLeft = meta?.distanceToRoadEdgeLeft ?: 0f
    val roadEdgeRight = meta?.distanceToRoadEdgeRight ?: 0f
    
    val desireText = drivingIntent?.getDesireText() ?: "无"
    val lcProb = drivingIntent?.laneChangeProb ?: 0f
    
    // 🆕 推断车道位置
    val lanePos = inferLanePosition(laneWidthLeft, laneWidthRight, roadEdgeLeft, roadEdgeRight)

    val brakeProb = drivingIntent?.disengagePredictions?.brakeDisengageProbs?.maxOrNull() ?: 0f
    val gasProb = drivingIntent?.disengagePredictions?.gasDisengageProbs?.maxOrNull() ?: 0f
    val curRate = curvature?.maxOrientationRate ?: 0f
    val leftBlind = carState?.leftBlindspot ?: false
    val rightBlind = carState?.rightBlindspot ?: false

    val currentLaneWidth = 3.6f
    val carPainter = painterResource(id = R.drawable.car)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(containerColor = UIConstants.CARD_BACKGROUND),
        shape = UIConstants.CARD_SHAPE
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 左侧数据面板
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DataLabel("意图", desireText, color = if (desireText != "无") Color(0xFF3B82F6) else Color.Gray)
                DataLabel("变道率", String.format("%.1f%%", lcProb * 100))
                DataLabel("当前车道", lanePos.laneDescription, color = UIConstants.COLOR_SUCCESS)
                DataLabel("曲率", String.format("%.3f", curRate))
            }
            
            // 右侧数据面板
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.End
            ) {
                DataLabel("刹车脱管", String.format("%.1f%%", brakeProb * 100), color = if (brakeProb > 0.5f) Color.Red else Color.Gray)
                DataLabel("油门脱管", String.format("%.1f%%", gasProb * 100), color = if (gasProb > 0.5f) Color.Red else Color.Gray)
                if (leftBlind) Text("左盲区!", fontSize = 9.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                if (rightBlind) Text("右盲区!", fontSize = 9.sp, color = Color.Red, fontWeight = FontWeight.Bold)
            }
            
            Canvas(modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, start = 60.dp, end = 60.dp)) {
                val width = size.width
                val height = size.height
                val centerX = width / 2
                
                val defaultLaneWidth = 3.6f
                
                // 1. 获取汽车原始比例并计算视觉缩放
                val intrinsicSize = carPainter.intrinsicSize
                val carRatio = if (intrinsicSize.height > 0) intrinsicSize.width / intrinsicSize.height else 0.5f
                
                // 设定汽车在画布中的高度比例 (40% 画布高度，放大2倍)
                 val carHeightPx = height * 0.4f 
                 val carWidthPx = carHeightPx * carRatio
                 
                 // 设定车道宽度为汽车宽度的 1.2 倍
                 val visualLaneWidthPx = carWidthPx * 1.2f
                
                // 计算物理米到像素的转换比例 (以标准车道宽 3.6m 为基准)
                val meterToPx = visualLaneWidthPx / defaultLaneWidth
                
                // 2. 计算当前车道边界 (基于视觉车道宽度)
                // 如果实时数据有效，则按比例缩放，否则使用视觉基准宽度
                val realLaneWidthLeft = if (laneWidthLeft > 0.5f) laneWidthLeft else (defaultLaneWidth / 2)
                val realLaneWidthRight = if (laneWidthRight > 0.5f) laneWidthRight else (defaultLaneWidth / 2)
                
                val curLaneLeftX = centerX - realLaneWidthLeft * meterToPx
                val curLaneRightX = centerX + realLaneWidthRight * meterToPx
                
                // 3. 绘制左侧其他车道
                val leftLanesCount = lanePos.currentLaneIndex - 1
                for (i in 1..leftLanesCount) {
                    val laneLeftX = curLaneLeftX - i * visualLaneWidthPx
                    val laneRightX = curLaneLeftX - (i - 1) * visualLaneWidthPx
                    
                    // 填充背景
                    drawRect(
                        color = Color.White.copy(alpha = 0.03f),
                        topLeft = Offset(laneLeftX, 0f),
                        size = Size(laneRightX - laneLeftX, height)
                    )
                    
                    // 绘制左侧车道线（虚线）
                    drawPath(
                        path = Path().apply {
                            moveTo(laneLeftX, 0f)
                            lineTo(laneLeftX, height)
                        },
                        color = Color.White.copy(alpha = 0.3f),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                }
                
                // 4. 绘制右侧其他车道
                val rightLanesCount = lanePos.totalLanes - lanePos.currentLaneIndex
                for (i in 1..rightLanesCount) {
                    val laneLeftX = curLaneRightX + (i - 1) * visualLaneWidthPx
                    val laneRightX = curLaneRightX + i * visualLaneWidthPx
                    
                    // 填充背景
                    drawRect(
                        color = Color.White.copy(alpha = 0.03f),
                        topLeft = Offset(laneLeftX, 0f),
                        size = Size(laneRightX - laneLeftX, height)
                    )
                    
                    // 绘制右侧车道线（虚线）
                    drawPath(
                        path = Path().apply {
                            moveTo(laneRightX, 0f)
                            lineTo(laneRightX, height)
                        },
                        color = Color.White.copy(alpha = 0.3f),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                }
                
                // 5. 绘制左路缘 (相对于汽车中心偏移)
                if (roadEdgeLeft > 0.1f) {
                    val edgeX = centerX - roadEdgeLeft * meterToPx
                    val edgeWidth = 2.0f * meterToPx // 显示一段路缘宽度
                    
                    drawRect(
                        color = Color(0xFF475569).copy(alpha = 0.2f),
                        topLeft = Offset(edgeX - edgeWidth, 0f),
                        size = Size(edgeWidth, height)
                    )
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(edgeX, 0f),
                        end = Offset(edgeX, height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                
                // 6. 绘制右路缘 (相对于汽车中心偏移)
                if (roadEdgeRight > 0.1f) {
                    val edgeX = centerX + roadEdgeRight * meterToPx
                    val edgeWidth = 2.0f * meterToPx
                    
                    drawRect(
                        color = Color(0xFF475569).copy(alpha = 0.2f),
                        topLeft = Offset(edgeX, 0f),
                        size = Size(edgeWidth, height)
                    )
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(edgeX, 0f),
                        end = Offset(edgeX, height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                
                // 7. 绘制当前车道边界（加粗虚线）
                drawPath(
                    path = Path().apply {
                        moveTo(curLaneLeftX, 0f)
                        lineTo(curLaneLeftX, height)
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                    )
                )
                drawPath(
                    path = Path().apply {
                        moveTo(curLaneRightX, 0f)
                        lineTo(curLaneRightX, height)
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                    )
                )
                
                // 8. 绘制本车 (位置进一步下移)
                 val carX = centerX - carWidthPx / 2
                 // 将汽车位置设定在画布高度的 85% 处（继续向下移动）
                 val carY = height * 0.85f - carHeightPx / 2
                
                withTransform({
                    translate(carX, carY)
                }) {
                    with(carPainter) {
                        draw(size = Size(carWidthPx, carHeightPx))
                    }
                }
            }
            
            // 底部距离信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (roadEdgeLeft > 0) Text("L路缘: ${String.format("%.1f", roadEdgeLeft)}m", fontSize = 9.sp, color = UIConstants.COLOR_NEUTRAL)
                if (laneWidthLeft > 0) Text("L宽: ${String.format("%.1f", laneWidthLeft)}m", fontSize = 9.sp, color = UIConstants.COLOR_NEUTRAL)
                Spacer(modifier = Modifier.weight(1f))
                if (laneWidthRight > 0) Text("R宽: ${String.format("%.1f", laneWidthRight)}m", fontSize = 9.sp, color = UIConstants.COLOR_NEUTRAL)
                if (roadEdgeRight > 0) Text("R路缘: ${String.format("%.1f", roadEdgeRight)}m", fontSize = 9.sp, color = UIConstants.COLOR_NEUTRAL)
            }
        }
    }
}

@Composable
private fun DataLabel(label: String, value: String, color: Color = Color.Gray) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 9.sp, color = Color.Gray.copy(alpha = 0.6f))
        Text(text = value, fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CheckConditionTable(conditions: List<CheckCondition>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = UIConstants.CARD_BACKGROUND),
        shape = UIConstants.CARD_SHAPE
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            VisualizationTableHeader()
            
            conditions.forEachIndexed { index, cond ->
                if (index in listOf(3, 6, 7, 10)) HorizontalDivider(color = Color.White.copy(0.1f), thickness = 0.5.dp)
                ConditionRow(cond, index % 2 != 0)
            }
        }
    }
}

@Composable
private fun VisualizationTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF334155).copy(alpha = 0.3f)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("条件", fontSize = 10.sp, color = Color.White, modifier = Modifier.weight(1.8f))
        Text("阈值", fontSize = 10.sp, color = Color.White, modifier = Modifier.weight(1.6f))
        Text("实际", fontSize = 10.sp, color = Color.White, modifier = Modifier.weight(1.6f))
        Text("✓", fontSize = 10.sp, color = Color.White, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun ConditionRow(cond: CheckCondition, isStripe: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isStripe) Color.White.copy(0.05f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(cond.name, fontSize = 9.sp, color = Color.LightGray, modifier = Modifier.weight(1.8f))
        Text(cond.threshold, fontSize = 8.5.sp, color = Color.Gray, modifier = Modifier.weight(1.6f))
        Text(cond.actual, fontSize = 8.5.sp, color = if (cond.isMet) Color.Green else Color.Red, modifier = Modifier.weight(1.6f))
        Text(if (cond.isMet) "✓" else "✗", fontSize = 10.sp, color = if (cond.isMet) Color.Green else Color.Red, modifier = Modifier.weight(0.6f))
    }
}
