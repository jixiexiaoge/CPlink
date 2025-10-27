package com.example.carrotamap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carrotamap.ui.theme.CPlinkTheme

// UI组件导入
import com.example.carrotamap.ui.components.*
import com.example.carrotamap.ui.components.CompactStatusCard
import com.example.carrotamap.ui.components.TableHeader
import com.example.carrotamap.ui.components.DataTable

/**
 * MainActivity UI组件管理类
 * 负责所有UI组件的定义和界面逻辑
 */
class MainActivityUI(
    private val core: MainActivityCore
) {

    /**
     * 设置用户界面
     */
    @Composable
    fun SetupUserInterface() {
        CPlinkTheme {
            Scaffold(
                bottomBar = {
                    BottomNavigationBar(
                        currentPage = core.currentPage,
                        onPageChange = { page -> core.currentPage = page },
                        userType = core.userType.value
                    )
                }
            ) { paddingValues ->
                // 使用可滚动布局支持横屏和不同屏幕高度
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 主内容区域 - 可滚动
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // 根据当前页面显示不同内容
                        when (core.currentPage) {
                            0 -> HomePage(
                                deviceId = core.deviceId.value,
                                remainingSeconds = core.remainingSeconds.value,
                                selfCheckStatus = core.selfCheckStatus.value,
                                userType = core.userType.value,
                                carrotManFields = core.carrotManFields.value,
                                dataFieldManager = core.dataFieldManager,
                                onSendCommand = { command, arg -> core.sendCarrotCommand(command, arg) },
                                onSendRoadLimitSpeed = { core.sendCurrentRoadLimitSpeed() }
                            )
                            1 -> HelpPage()
                            2 -> QAPage()
                            3 -> ProfilePage(
                                usageStats = core.usageStats.value,
                                deviceId = core.deviceId.value
                            )
                            4 -> {
                                // 数据页面：只有铁粉（用户类型4）才能访问
                                if (core.userType.value == 4) {
                                    DataPage(
                                        carrotManFields = core.carrotManFields.value,
                                        dataFieldManager = core.dataFieldManager,
                                        networkManager = core.networkManager,
                                        amapBroadcastManager = core.amapBroadcastManager
                                    )
                                } else {
                                    DataPageAccessDenied(core.userType.value)
                                }
                            }
                        }
                        
                        // 下载弹窗
                        if (core.showDownloadDialog.value) {
                            CarrotAmapDownloadDialog(
                                onDismiss = { core.showDownloadDialog.value = false },
                                onDownload = { 
                                    core.showDownloadDialog.value = false
                                    core.openGitHubWebsite()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 实时数据页面组件
     */
    @Composable
    private fun DataPage(
        carrotManFields: CarrotManFields,
        dataFieldManager: DataFieldManager,
        networkManager: NetworkManager,
        amapBroadcastManager: AmapBroadcastManager
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFE2E8F0)
                        )
                    )
                )
        ) {
            // 使用LazyColumn替代Column + verticalScroll
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态卡片
                item {
                    CompactStatusCard(
                        receiverStatus = amapBroadcastManager.receiverStatus.value,
                        totalBroadcastCount = amapBroadcastManager.totalBroadcastCount.intValue,
                        carrotManFields = carrotManFields,
                        networkStatus = networkManager.getNetworkConnectionStatus(),
                        networkStats = networkManager.getNetworkStatistics(),
                        onClearDataClick = {
                            amapBroadcastManager.clearBroadcastData()
                        }
                    )
                }
                
                // 数据表格
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "实时数据信息",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            // 表格头部
                            TableHeader()
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 数据表格
                            DataTable(
                                carrotManFields = carrotManFields,
                                dataFieldManager = dataFieldManager,
                                networkManager = networkManager
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 主页组件
     */
    @Composable
    private fun HomePage(
        deviceId: String,
        remainingSeconds: Int,
        selfCheckStatus: SelfCheckStatus,
        userType: Int,
        carrotManFields: CarrotManFields,
        dataFieldManager: DataFieldManager,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit
    ) {
        val scrollState = rememberScrollState()
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFE2E8F0)
                        )
                    )
                )
        ) {
            // 主内容区域
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部控制按钮区域
                VehicleControlButtons(
                    onPageChange = { page -> 
                        // 这里需要访问MainActivity的currentPage状态
                        // 暂时用Log记录，后续可以通过其他方式实现
                        android.util.Log.i("MainActivity", "页面切换请求: $page")
                    },
                    onSendCommand = onSendCommand,
                    onSendRoadLimitSpeed = onSendRoadLimitSpeed
                )
                
                // 可滚动的内容区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 当前检查项卡片（只在未完成时显示）
                    if (selfCheckStatus.currentComponent.isNotEmpty() && !selfCheckStatus.isCompleted) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp,
                                        color = Color(0xFF3B82F6)
                                    )
                                    
                                    Text(
                                        text = selfCheckStatus.currentComponent,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = selfCheckStatus.currentMessage,
                                    fontSize = 14.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 已完成项目列表
                    if (selfCheckStatus.completedComponents.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(
                                    text = "已完成项目",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                selfCheckStatus.completedComponents.forEachIndexed { index, component ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(
                                                    Color(0xFF22C55E),
                                                    androidx.compose.foundation.shape.CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✓",
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Text(
                                            text = if (index == 3) {
                                                // 第4行（索引3）整合系统信息
                                                val systemInfo = buildString {
                                                    append(component)
                                                    if (deviceId.isNotEmpty()) {
                                                        append(" (ID: $deviceId)")
                                                    }
                                                    val userTypeText = when (userType) {
                                                        0 -> "未知用户"
                                                        1 -> "新用户"
                                                        2 -> "支持者"
                                                        3 -> "赞助者"
                                                        4 -> "铁粉"
                                                        else -> "未知类型($userType)"
                                                    }
                                                    append(" - $userTypeText")
                                                    append(" - 智能驾驶助手")
                                                }
                                                systemInfo
                                            } else {
                                                // 显示组件名称和消息内容
                                                val message = selfCheckStatus.completedMessages[component] ?: ""
                                                if (message.isNotEmpty()) {
                                                    "$component: $message"
                                                } else {
                                                    component
                                                }
                                            },
                                            fontSize = 14.sp,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Comma3数据表格（可折叠）
                Spacer(modifier = Modifier.height(16.dp))
                Comma3DataTable(
                    carrotManFields = carrotManFields,
                    dataFieldManager = dataFieldManager
                )
                
                // 添加底部安全间距
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    /**
     * 底部导航栏组件
     */
    @Composable
    private fun BottomNavigationBar(
        currentPage: Int,
        onPageChange: (Int) -> Unit,
        userType: Int = 0
    ) {
        // 根据用户类型决定是否显示数据页面
        val basePages = listOf(
            BottomNavItem("主页", Icons.Default.Home, 0),
            BottomNavItem("帮助", Icons.Default.Info, 1),
            BottomNavItem("问答", Icons.Default.Info, 2),
            BottomNavItem("我的", Icons.Default.Person, 3)
        )
        
        val pages = if (userType == 4) {
            // 铁粉用户可以看到数据页面
            basePages + BottomNavItem("数据", Icons.Default.Settings, 4)
        } else {
            // 其他用户类型不显示数据页面
            basePages
        }
        
        NavigationBar(
            containerColor = Color.White,
            contentColor = Color(0xFF2196F3)
        ) {
            pages.forEach { page ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = page.title
                        )
                    },
                    label = {
                        Text(
                            text = page.title,
                            fontSize = 12.sp
                        )
                    },
                    selected = currentPage == page.index,
                    onClick = { onPageChange(page.index) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2196F3),
                        selectedTextColor = Color(0xFF2196F3),
                        unselectedIconColor = Color(0xFF999999),
                        unselectedTextColor = Color(0xFF999999)
                    )
                )
            }
        }
    }

    /**
     * CarrotAmap下载弹窗组件
     */
    @Composable
    private fun CarrotAmapDownloadDialog(
        onDismiss: () -> Unit,
        onDownload: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "🚗 请使用 CarrotAmap",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "感谢您的支持！作为支持者，您需要使用 CarrotAmap 应用来获得完整的导航功能。",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 20.sp
                    )
                    
                    Text(
                        text = "CarrotAmap 是基于高德地图的增强导航应用，提供：",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "🚗 自动按导航变道和转弯",
                            "🗺️ 自动沿导航路线行驶", 
                            "📊 根据限速自动调整车速",
                            "🚦 红灯自动减速停车",
                            "🛣️ 弯道自动减速"
                        ).forEach { feature ->
                            Text(
                                text = feature,
                                fontSize = 13.sp,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Text(
                        text = "立即下载",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = "稍后再说",
                        color = Color(0xFF64748B)
                    )
                }
            },
            containerColor = Color.White,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }

    /**
     * 车辆控制按钮组件 - 恢复SPEED和LANECHANGE按钮，参考AdvancedOperationDialog.kt
     */
    @Composable
    private fun VehicleControlButtons(
        onPageChange: (Int) -> Unit,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ) {
            // 控制按钮行 - 恢复SPEED和LANECHANGE按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 加速按钮
                ControlButton(
                    icon = "",
                    label = "加速",
                    color = Color(0xFF22C55E),
                    onClick = {
                        android.util.Log.i("MainActivity", "🎮 主页：用户点击加速按钮")
                        onSendCommand("SPEED", "UP")
                    }
                )
                
                // 减速按钮
                ControlButton(
                    icon = "",
                    label = "减速",
                    color = Color(0xFFEF4444),
                    onClick = {
                        android.util.Log.i("MainActivity", "🎮 主页：用户点击减速按钮")
                        onSendCommand("SPEED", "DOWN")
                    }
                )
                
                // 左变道按钮
                ControlButton(
                    icon = "",
                    label = "左变道",
                    color = Color(0xFF3B82F6),
                    onClick = {
                        android.util.Log.i("MainActivity", "🎮 主页：用户点击左变道按钮")
                        onSendCommand("LANECHANGE", "LEFT")
                    }
                )
                
                // 右变道按钮
                ControlButton(
                    icon = "",
                    label = "右变道",
                    color = Color(0xFF3B82F6),
                    onClick = {
                        android.util.Log.i("MainActivity", "🎮 主页：用户点击右变道按钮")
                        onSendCommand("LANECHANGE", "RIGHT")
                    }
                )
                
                // 设置按钮（发送配置到comma3设备）
                ControlButton(
                    icon = "",
                    label = "设置",
                    color = Color(0xFF8B5CF6),
                    onClick = {
                        android.util.Log.i("MainActivity", "🎯 主页：用户点击设置按钮，发送当前道路限速")
                        onSendRoadLimitSpeed()
                    }
                )
            }
        }
    }

    /**
     * 控制按钮组件
     */
    @Composable
    private fun ControlButton(
        icon: String,
        label: String,
        color: Color,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.White
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(56.dp)
                .height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (icon.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 16.sp
                    )
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    /**
     * 数据页面访问拒绝组件
     * 只有铁粉（用户类型4）才能访问数据页面
     */
    @Composable
    private fun DataPageAccessDenied(userType: Int) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFE2E8F0)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 锁图标
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "访问受限",
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF64748B)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 标题
                Text(
                    text = "🔒 数据页面访问受限",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 用户类型信息
                val userTypeText = when (userType) {
                    0 -> "未知用户"
                    1 -> "新用户"
                    2 -> "支持者"
                    3 -> "赞助者"
                    4 -> "铁粉"
                    else -> "未知类型($userType)"
                }
                
                Text(
                    text = "当前用户类型：$userTypeText",
                    fontSize = 16.sp,
                    color = Color(0xFF64748B),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 说明卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🚀 数据页面功能说明",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "数据页面提供实时导航数据监控功能，包括：",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "📊 实时CarrotMan字段数据",
                                "🌐 网络连接状态监控",
                                "📡 高德地图广播数据统计",
                                "🔧 系统状态和性能指标",
                                "📈 数据质量分析报告"
                            ).forEach { feature ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "✓",
                                        fontSize = 16.sp,
                                        color = Color(0xFF22C55E),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = feature,
                                        fontSize = 14.sp,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 升级提示
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "💎 升级到铁粉用户",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "只有铁粉用户才能访问数据页面功能。\n请联系管理员升级您的账户类型。",
                                    fontSize = 13.sp,
                                    color = Color(0xFF92400E),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Comma3数据表格组件（可折叠）
     */
    @Composable
    private fun Comma3DataTable(
        carrotManFields: CarrotManFields,
        dataFieldManager: DataFieldManager
    ) {
        var isExpanded by remember { mutableStateOf(false) }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题行（可点击）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📥",
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Comma3实时数据",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                    
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        tint = Color(0xFF64748B)
                    )
                }
                
                // 数据表格（可折叠）
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 表格头部
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "字段",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "描述",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "值",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // 数据行
                    dataFieldManager.getOpenpilotReceiveFields(carrotManFields).forEach { fieldData ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = fieldData.first,
                                fontSize = 11.sp,
                                color = Color(0xFF374151),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = fieldData.second,
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = fieldData.third,
                                fontSize = 11.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底部导航项数据类
 */
private data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val index: Int
)
