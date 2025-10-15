# CarrotMan 数据字段映射分析

## 概述
本文档分析了 CarrotMan 系统中手机与 comma3 设备之间的数据通信字段映射关系。

### 数据流向
- **7705端口 (UDP广播)**: comma3 → 手机 (状态信息)
- **7706端口 (UDP)**: 手机 → comma3 (导航数据)

---

## 7705端口 - comma3发送给手机的数据字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 |
|--------|------|----------|------|----------|------|
| Carrot2 | String | ✅ | OpenpPilot版本信息 | "0.9.4" | 从params获取Version |
| IsOnroad | Boolean | ✅ | 是否在道路上行驶 | true | 从params获取IsOnroad |
| CarrotRouteActive | Boolean | ✅ | 导航路线是否激活 | true | navi_points_active状态 |
| ip | String | ✅ | comma3设备IP地址 | "192.168.1.100" | 动态获取的IP |
| port | Integer | ✅ | 数据接收端口 | 7706 | carrot_man_port |
| log_carrot | String | ✅ | CarrotMan状态日志 | "active" | 从carState.logCarrot获取 |
| v_cruise_kph | Float | ✅ | 巡航设定速度(km/h) | 60.0 | 从carState.vCruise获取 |
| v_ego_kph | Integer | ✅ | 当前实际车速(km/h) | 45 | 从carState.vEgoCluster计算 |
| tbt_dist | Integer | ✅ | 到下个转弯距离(米) | 150 | carrot_serv.xDistToTurn |
| sdi_dist | Integer | ✅ | 到速度限制点距离(米) | 200 | carrot_serv.xSpdDist |
| active | Boolean | ✅ | 自动驾驶控制激活状态 | true | 从selfdriveState.active获取 |
| xState | Integer | ✅ | 纵向控制状态码 | 1 | 从longitudinalPlan.xState获取 |
| trafficState | Integer | ✅ | 交通灯状态 | 0 | 从longitudinalPlan.trafficState获取 |

---

## 7706端口 - 手机发送给comma3的数据字段

### 基础通信字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| carrotIndex | Long | ✅ | 数据包序号 | 12345 | 必需字段，用于数据同步 | 计算/本地生成 | - |
| epochTime | Long | ✅ | Unix时间戳 | 1703123456 | 每60个包发送一次 | 计算/本地生成 | - |
| timezone | String | ✅ | 时区 | "Asia/Shanghai" | 默认Asia/Seoul | 计算/本地生成 | - |

### GPS定位字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| latitude | Double | ✅ | GPS纬度(WGS84) | 39.9042 | 手机GPS或导航GPS | Amap导航或手机GPS | 10065: LATITUDE 或 手机传感器 |
| longitude | Double | ✅ | GPS经度(WGS84) | 116.4074 | 手机GPS或导航GPS | Amap导航或手机GPS | 10065: LONGITUDE 或 手机传感器 |
| heading | Double | ✅ | 方向角(0-360度) | 45.5 | 手机GPS方向角 | Amap导航或手机GPS | 10065: BEARING 或 手机传感器 |
| accuracy | Double | ✅ | GPS精度(米) | 3.2 | 手机GPS精度 | 手机GPS | 手机传感器 |
| gps_speed | Double | ✅ | GPS速度(m/s) | 12.5 | 手机GPS速度 | 手机GPS | 手机传感器 |

### 目的地信息字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| goalPosX | Double | ✅ | 目标经度 | 116.4074 | 导航目的地 | Amap | 10001: endPOILongitude |
| goalPosY | Double | ✅ | 目标纬度 | 39.9042 | 导航目的地 | Amap | 10001: endPOILatitude |
| szGoalName | String | ✅ | 目标名称 | "天安门广场" | 导航目的地名称 | Amap | 10001: endPOIName |

### 道路信息字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| nRoadLimitSpeed | Integer | ✅ | 道路限速(km/h) | 60 | 当前道路限速 | Amap | 10001: LIMITED_SPEED 或 12110: SPEED_LIMIT |
| roadcate | Integer | ✅ | 道路类别 | 8 | 10/11=高速，其它非高速 | 计算/映射 | 10001: ROAD_TYPE → 映射函数 |
| szPosRoadName | String | ✅ | 当前道路名称 | "长安街" | 当前所在道路 | Amap | 10001: CUR_ROAD_NAME 或 10003: ROUTE_NAME |

### SDI速度检测字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| nSdiType | Integer | ✅ | SDI类型 | 1 | 测速类型编码 | Amap | 13005: CAMERA_TYPE 或 100001: CAMERA_TYPE |
| nSdiSpeedLimit | Integer | ✅ | 测速限速(km/h) | 50 | 测速点限速 | Amap | 13005: SPEED_LIMIT 或 100001: CAMERA_SPEED |
| nSdiDist | Integer | ✅ | 到测速点距离(m) | 300 | 距离测速点距离 | Amap | 13005: DISTANCE 或 100001: CAMERA_DIST |
| nSdiSection | Integer | ✅ | 区间测速ID | 12345 | 区间测速标识 | Amap/内部 | 13005 内部字段/未公开 |
| nSdiBlockType | Integer | ✅ | 区间状态 | 1 | 1=开始,2=中,3=结束 | Amap/内部 | 13005 内部字段/未公开 |
| nSdiBlockSpeed | Integer | ✅ | 区间限速 | 60 | 区间测速限速 | Amap/内部 | 13005 内部字段/未公开 |
| nSdiBlockDist | Integer | ✅ | 区间距离 | 2000 | 区间测速总距离 | Amap/内部 | 13005 内部字段/未公开 |

### SDI Plus扩展字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| nSdiPlusType | Integer | ✅ | Plus类型 | 22 | 22=减速带 | Amap | 10007: SDI_TYPE 或 SDI_PLUS_INFO.type |
| nSdiPlusSpeedLimit | Integer | ✅ | Plus限速 | 30 | 减速带限速 | Amap | 10007: SPEED_LIMIT 或 SDI_PLUS_INFO.speed_limit |
| nSdiPlusDist | Integer | ✅ | Plus距离 | 100 | 到减速带距离 | Amap | 10007: SDI_DIST 或 SDI_PLUS_INFO.distance |
| nSdiPlusBlockType | Integer | ✅ | Plus区间类型 | -1 | 区间状态 | Amap(JSON) | 10007: SDI_PLUS_INFO.block_type |
| nSdiPlusBlockSpeed | Integer | ✅ | Plus区间限速 | 0 | 区间限速 | Amap(JSON) | 10007: SDI_PLUS_INFO.block_speed |
| nSdiPlusBlockDist | Integer | ✅ | Plus区间距离 | 0 | 区间距离 | Amap(JSON) | 10007: SDI_PLUS_INFO.block_dist |

### TBT转弯导航字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| nTBTDist | Integer | ✅ | 转弯距离(m) | 200 | 到转弯点距离 | Amap | 10001: SEG_REMAIN_DIS 或 10006: TURN_DISTANCE |
| nTBTTurnType | Integer | ✅ | 转弯类型 | 12 | 转弯类型编码 | Amap+映射 | 10001: ICON/NEW_ICON → 映射, 或 10006: TURN_TYPE |
| szTBTMainText | String | ✅ | 主要指令文本 | "前方200米左转" | 导航指令文本 | 计算/拼装 | 10001/10006 + 文本生成 |
| szNearDirName | String | ✅ | 近处方向名 | "建国门" | 近处地标 | Amap | 10001: NEXT_ROAD_NAME |
| szFarDirName | String | ✅ | 远处方向名 | "东二环" | 远处地标 | Amap | 10001: NEXT_NEXT_ROAD_NAME |
| nTBTNextRoadWidth | Integer | ✅ | 下一道路宽度 | 6 | 车道数 | Amap | 10001: N/A, 13012: drive_way_size(车道线信息) |
| nTBTDistNext | Integer | ✅ | 下一转弯距离 | 500 | 下下个转弯距离 | Amap | 10001: NEXT_SEG_REMAIN_DIS 或 10006: NEXT_TURN_DISTANCE |
| nTBTTurnTypeNext | Integer | ✅ | 下一转弯类型 | 13 | 下下个转弯类型 | Amap+映射 | 10001: NEXT_NEXT_TURN_ICON → 映射, 或 10006: NEXT_TURN_TYPE |
| szTBTMainTextNext | String | ✅ | 下一转弯指令 | "前方500米右转" | 下下个指令 | 计算/拼装 | 10001/10006 + 文本生成 |

### 目的地剩余字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| nGoPosDist | Integer | ✅ | 剩余距离(m) | 5000 | 到目的地距离 | Amap | 10001: ROUTE_REMAIN_DIS |
| nGoPosTime | Integer | ✅ | 剩余时间(s) | 600 | 预计到达时间 | Amap | 10001: ROUTE_REMAIN_TIME 或 10003: ROUTE_TIME |

### 导航位置字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| vpPosPointLat | Double | ✅ | 导航纬度 | 39.9042 | 导航系统纬度 | Amap | 10001: CAR_LATITUDE 或 10065: LATITUDE |
| vpPosPointLon | Double | ✅ | 导航经度 | 116.4074 | 导航系统经度 | Amap | 10001: CAR_LONGITUDE 或 10065: LONGITUDE |
| nPosAngle | Double | ✅ | 导航方向角 | 45.5 | 导航系统方向角 | Amap/手机 | 10001: CAR_DIRECTION 或 10065: BEARING/手机 |
| nPosSpeed | Double | ✅ | 导航速度 | 12.5 | 导航系统速度 | Amap/手机 | 10001: CUR_SPEED 或 10065: SPEED/手机 |

### 命令控制字段

| 字段名 | 类型 | 是否发送 | 描述 | 实例数据 | 备注 | 来源(Amap/计算) | KEY_TYPE / Amap字段 |
|--------|------|----------|------|----------|------|------------------|---------------------|
| carrotCmd | String | ✅ | 命令类型 | "DETECT" | 控制命令 | 计算/本地生成 | - |
| carrotArg | String | ✅ | 命令参数 | "red,100,200,0.8" | 命令参数 | 计算/本地生成 | - |

### 7706端口 字段来源映射总览（基于 Amap 广播逆向）

为保持原有表格结构，下面新增“来源映射”总览表，逐项标注字段由哪个高德广播 KEY_TYPE 与 Extra 字段映射而来。KEY_TYPE 与字段名来源于对 `AmapBroadcastHandlers.kt` 的逐行逆向分析，未出现于代码的字段标记为“无/本地派生”。

| 字段名 | 来源KEY_TYPE | Amap Extra字段名 | 说明 |
|--------|--------------|------------------|------|
| szPosRoadName | 10001 | CUR_ROAD_NAME | 当前道路名（亦可能被 10003 ROUTE_NAME 或 12110 ROAD_NAME 覆盖） |
| szNearDirName | 10001 | NEXT_ROAD_NAME | 近处方向名 |
| szFarDirName | 10001 | NEXT_NEXT_ROAD_NAME | 远处方向名 |
| nRoadLimitSpeed | 10001/12110 | LIMITED_SPEED / SPEED_LIMIT | 限速；12110 同时提供 xSpdLimit/xSpdDist 更新 |
| nGoPosDist | 10001 | ROUTE_REMAIN_DIS | 剩余距离 |
| nGoPosTime | 10001/10003 | ROUTE_REMAIN_TIME / ROUTE_TIME | 剩余时间；10003 路线信息也会写入 |
| totalDistance | 10001/10003 | ROUTE_ALL_DIS / ROUTE_DISTANCE | 全程距离 |
| nPosSpeed | 10001/10065 | CUR_SPEED / SPEED | 车辆速度；10065 中单位按实现直接写入 |
| nPosAngle | 10001/10065 | CAR_DIRECTION / BEARING | 航向角 |
| xPosSpeed | 10065 | SPEED | 协议标准位置速度字段同步 |
| xPosAngle | 10065 | BEARING | 协议标准位置航向字段同步 |
| xPosLat | 10065 | LATITUDE | 协议标准位置纬度字段同步 |
| xPosLon | 10065 | LONGITUDE | 协议标准位置经度字段同步 |
| vpPosPointLatNavi | 10001/10065 | CAR_LATITUDE / LATITUDE | 导航/定位纬度（优先用导航字段，缺省用定位） |
| vpPosPointLonNavi | 10001/10065 | CAR_LONGITUDE / LONGITUDE | 导航/定位经度（同上） |
| goalPosX | 10001 | endPOILongitude | 目的地经度 |
| goalPosY | 10001 | endPOILatitude | 目的地纬度 |
| szGoalName | 10001 | endPOIName | 目的地名称 |
| nTBTDist | 10001/10006 | SEG_REMAIN_DIS / TURN_DISTANCE | 本段转弯剩余距离 |
| nTBTTurnType | 10001/10006 | ICON/NEW_ICON(映射) / TURN_TYPE | 10001 需经 mapAmapIconToCarrotTurn 转换；10006 直接赋值 |
| amapIcon | 10001 | ICON 或 NEW_ICON | 保存高德原始转向图标（便于调试） |
| nTBTDistNext | 10001/10006 | NEXT_SEG_REMAIN_DIS / NEXT_TURN_DISTANCE | 下下个转弯距离 |
| nTBTTurnTypeNext | 10001/10006 | NEXT_NEXT_TURN_ICON(映射) / NEXT_TURN_TYPE | 同上，10001 需映射 |
| amapIconNext | 10001 | NEXT_NEXT_TURN_ICON | 下下个高德原始图标 |
| traffic_light_count | 10001/60073 | TRAFFIC_LIGHT_NUM / TRAFFIC_LIGHT_COUNT | 红绿灯个数（两处可能更新） |
| traffic_state | 60073 | trafficLightStatus (+ dir) | 经 mapTrafficLightStatus 映射到协议态 0/1/2/3/-1 |
| left_sec | 60073 | redLightCountDownSeconds | 实际为“当前态倒计时”，绿灯时亦落在该字段；若缺失沿用旧值 |
| roadType | 10001 | ROAD_TYPE | 高德道路类型原值保留 |
| roadcate | 10001(派生) | ROAD_TYPE → mapRoadTypeToRoadcate | 由 ROAD_TYPE 映射：0,1,2,6,7 → 10；其他 → 6 |
| nLaneCount | 13012 | EXTRA_DRIVE_WAY.drive_way_size | 车道路数（需解析 JSON） |
| nSdiType | 13005/100001 | CAMERA_TYPE | 电子眼/新版电子眼；直接映射到 Python SDI 类型 0-4 等 |
| nSdiSpeedLimit | 13005/100001 | SPEED_LIMIT / CAMERA_SPEED | 测速限速 |
| nSdiDist | 13005/100001 | DISTANCE / CAMERA_DIST | 到测速点距离 |
| szSdiDescr | 13005/100001 | CAMERA_TYPE(派生) | 通过 getSdiTypeDescription 文本化描述 |
| nSdiPlusType | 10007 | SDI_TYPE 或 SDI_PLUS_INFO.type | SDI Plus 扩展类型（如 22 减速带） |
| nSdiPlusSpeedLimit | 10007 | SPEED_LIMIT 或 SDI_PLUS_INFO.speed_limit | 扩展限速 |
| nSdiPlusDist | 10007 | SDI_DIST 或 SDI_PLUS_INFO.distance | 扩展距离 |
| isNavigating | 10042/10019 | NAVI_STATUS / EXTRA_STATE | 导航状态；到达目的地（10019）会置 false |
| active_carrot | 10042/10019/10001 | NAVI_STATUS / EXTRA_STATE / 由剩余距离等推断 | 导航活跃标记 |
| debugText | 本地派生 | - | 由 generateDebugText 生成，便于调试 |
| desiredSpeed | 10001/12110 | LIMITED_SPEED 或 SPEED_LIMIT | 期望速度（多源择优） |
| desiredSource | 10001/12110 | 同上 | 期望速度来源标记 |
| xSpdLimit | 12110 | SPEED_LIMIT | 协议限速字段同步（含 xSpdType=1） |
| xSpdDist | 12110 | DISTANCE | 限速点距离 |

注：
- “映射”表示需经 `mapAmapIconToCarrotTurn()` 或 `mapRoadTypeToRoadcate()` 等函数转换后写入。
- 60073 红绿灯广播的方向 `dir` 会影响红/左转的细化状态映射。
- 10019 地图状态在到达目的地时会统一设置 `nTBTTurnType=201`，并清零相关距离/时间。

#### 关键 KEY_TYPE 与字段对照（补充说明）

- 10001 引导信息：CUR_ROAD_NAME、NEXT_ROAD_NAME、NEXT_NEXT_ROAD_NAME、LIMITED_SPEED、CUR_SPEED、CAR_DIRECTION、ROUTE_REMAIN_DIS、ROUTE_REMAIN_TIME、ROUTE_ALL_DIS、ICON、NEW_ICON、NEXT_NEXT_TURN_ICON、ROUND_ABOUT_NUM、CAR_LATITUDE、CAR_LONGITUDE、TRAFFIC_LIGHT_NUM、ROAD_TYPE、endPOIName、endPOILatitude、endPOILongitude、SEG_REMAIN_DIS、NEXT_SEG_REMAIN_DIS 等。
- 10065 定位信息：LATITUDE、LONGITUDE、SPEED、BEARING。
- 10006 转向信息：TURN_DISTANCE、TURN_TYPE、TURN_INSTRUCTION、NEXT_TURN_DISTANCE、NEXT_TURN_TYPE。
- 12110 限速信息：SPEED_LIMIT、ROAD_NAME、DISTANCE。
- 13005 电子眼：CAMERA_TYPE、SPEED_LIMIT、DISTANCE（直接映射到 SDI）。
- 100001 新版电子眼：CAMERA_TYPE、CAMERA_SPEED、CAMERA_DIST、CAMERA_INDEX。
- 10007 SDI Plus：SDI_TYPE、SPEED_LIMIT、SDI_DIST，或 SDI_PLUS_INFO(JSON: type/speed_limit/distance)。
- 13012 车道线信息：EXTRA_DRIVE_WAY(JSON: drive_way_size, drive_way_info[])。
- 60073 红绿灯：trafficLightStatus、dir、redLightCountDownSeconds、greenLightLastSecond、TRAFFIC_LIGHT_COUNT、TRAFFIC_LIGHT_DISTANCE、waitRound 等。
- 10042 导航状态：NAVI_STATUS。
- 10019 地图状态：EXTRA_STATE（到达目的地场景）。

---

## 字段需求分析

### 7705端口 - 实际不需要的字段
- ✅ **导航路径数据**: 不需要发送，因为路径数据通过内部消息系统(`navRoute`)处理
- ✅ **详细状态信息**: 当前字段已足够，包含核心状态信息
- ✅ **错误信息**: 通过`log_carrot`字段传递状态信息

### 7706端口 - 实际不需要的字段
- ✅ **车道信息**: 不需要发送，`nTBTNextRoadWidth`字段已包含道路宽度信息
- ✅ **交通灯详细状态**: 已有完整的交通灯处理系统，通过`traffic_light()`函数处理
- ✅ **天气信息**: 系统设计不包含天气数据，专注于导航和速度控制
- ✅ **路况信息**: 通过`DrivingModeDetector`类自动检测交通拥堵状态

---

## 数据同步机制

### 时间同步
- `carrotIndex`: 每包递增，用于数据包顺序确认
- `epochTime`: 每60个包发送一次，用于时间同步

### GPS数据优先级
1. **导航GPS优先**: 如果导航GPS可用，优先使用导航GPS数据
2. **手机GPS备用**: 导航GPS不可用时，使用手机GPS数据
3. **超时机制**: 导航GPS超过3秒无更新时，切换到手机GPS

### 状态管理
- `active_count`: 数据包计数器，用于维持连接状态
- `active_sdi_count`: SDI数据计数器，用于速度检测状态管理

## 系统架构分析

### 导航路径处理
- **内部处理**: 导航路径通过`carrot_navi_route()`函数处理，生成相对坐标和曲率数据
- **路径发送**: 通过`naviPaths`字段发送格式化的路径字符串
- **路径计算**: 使用`haversine`公式计算距离，`calculate_curvature`计算曲率

### 交通灯处理系统
- **检测机制**: 通过`traffic_light()`函数处理交通灯状态
- **状态管理**: 支持红、绿、黄灯状态，包含置信度(`cnf`)参数
- **队列管理**: 使用`traffic_light_q`队列管理历史交通灯数据
- **状态映射**: `traffic_state` (0=无, 1=红灯, 2=绿灯, 3=左转灯)

### 车道信息处理
- **道路宽度**: 通过`nTBTNextRoadWidth`字段传递下一道路的车道数
- **车道检测**: 系统设计不依赖外部车道信息，通过内部算法处理

### 路况检测系统
- **自动检测**: `DrivingModeDetector`类自动检测交通拥堵状态
- **检测条件**: 基于车速、加速度、跟车距离等参数
- **模式切换**: 拥堵时自动切换到安全模式

---

## 注意事项

1. **数据格式**: 所有数据通过JSON格式传输
2. **编码**: 字符串使用UTF-8编码
3. **精度**: GPS坐标使用双精度浮点数
4. **单位**: 距离使用米，速度使用km/h或m/s
5. **默认值**: 大部分字段都有合理的默认值
6. **错误处理**: 包含基本的错误处理和数据验证机制

---

---

## roadcate 道路类别字段详细分析

### roadcate 字段定义与关系

| roadcate值 | 道路类型 | 中文描述 | 道路宽度 | 减速带处理 | 代码逻辑 | 备注 |
|------------|----------|----------|----------|------------|----------|------|
| 0-9 | Local Roads | 地方道路/普通道路 | 窄道路 | ✅ 处理减速带 | `roadcate > 1` 为true | 普通道路 |
| 10 | Highway | 高速公路 | 宽道路 | ❌ 不处理减速带 | `roadcate > 1` 为true | **高速公路** |
| 11 | Highway | 高速公路 | 宽道路 | ❌ 不处理减速带 | `roadcate > 1` 为true | **高速公路** |
| 8 | Default | 默认道路类型 | 中等宽度 | ✅ 处理减速带 | `roadcate > 1` 为true | **默认值** |

**重要修正**: `roadcate`字段表示**道路宽度**，而不是道路等级。10和11表示高速公路（宽道路），其他值表示普通道路。

### roadcate 在代码中的关键逻辑

#### 1. 初始化默认值
```python
# carrot_serv.py:132
self.roadcate = 8  # 默认值为8，表示地方道路
```

#### 2. 减速带处理逻辑
```python
# carrot_serv.py:466
elif (self.nSdiPlusType == 22 or self.nSdiType == 22) and self.roadcate > 1 and self.autoNaviSpeedCtrlMode >= 2:
    # speed bump, roadcate:10,11: highway (宽道路不处理减速带)
    self.xSpdLimit = self.autoNaviSpeedBumpSpeed
    self.xSpdDist = self.nSdiPlusDist if self.nSdiPlusType == 22 else self.nSdiDist
    self.xSpdType = 22
```

**注意**: 代码注释中的"roadcate:0,1: highway"是过时的，实际应该是"roadcate:10,11: highway"

#### 3. 数据接收处理
```python
# carrot_serv.py:1073
self.roadcate = int(json.get("roadcate", 0))  # 从手机接收，默认0
```

### roadcate 与其他字段的关系

| 相关字段 | 关系说明 | 影响逻辑 |
|----------|----------|----------|
| `nSdiType` | 当`nSdiType=22`(减速带)时，`roadcate`决定是否处理 | `roadcate > 1`时才处理减速带 |
| `autoNaviSpeedCtrlMode` | 配合`roadcate`控制减速带处理 | 需要`>= 2`才启用减速带控制 |
| `nRoadLimitSpeed` | `roadcate`影响默认限速设置 | 高速路限速更高，地方道路限速较低 |
| `autoNaviSpeedBumpSpeed` | 减速带限速，仅在`roadcate > 1`时生效 | 城市道路和地方道路适用 |

### 实际应用场景

#### 高速公路场景 (roadcate = 10, 11)
- **特点**: 宽道路，高速行驶，无减速带
- **处理**: 不处理减速带检测，专注高速行驶
- **道路宽度**: 宽道路，多车道
- **限速**: 通常120 km/h

#### 普通道路场景 (roadcate = 0-9)
- **特点**: 窄到中等宽度道路，有减速带
- **处理**: 检测并处理减速带，自动降速
- **道路宽度**: 窄到中等宽度，单车道或双车道
- **限速**: 通常30-80 km/h

#### 默认道路场景 (roadcate = 8)
- **特点**: 中等宽度道路，减速带较多
- **处理**: 重点处理减速带，安全驾驶
- **道路宽度**: 中等宽度，双车道
- **限速**: 通常30-60 km/h

### 数据流向总结

1. **手机发送**: 高德地图根据当前道路宽度设置`roadcate`值
   - `roadcate = 10, 11`: 高速公路（宽道路）
   - `roadcate = 0-9`: 普通道路（窄到中等宽度）
2. **comma3接收**: 通过7706端口接收`roadcate`字段
3. **逻辑判断**: 根据`roadcate`值决定是否处理减速带
   - 高速公路(10,11): 不处理减速带
   - 普通道路(0-9): 处理减速带
4. **速度控制**: 结合道路宽度和其他参数进行智能速度控制

### 开发者说明总结

根据开发者解释：
- **`roadcate`**: 表示道路宽度，10和11是高速公路（宽道路）
- **`GoPos`**: 表示目的地，10和11也是高速公路
- **关键理解**: 这个字段主要用于区分宽道路（高速公路）和窄道路（普通道路），从而决定是否处理减速带等安全措施

---

## 详细字段参数分析

### nSdiType - SDI速度检测类型字段

| nSdiType值 | 中文描述 | 英文描述 | 处理逻辑 | 实例数据 | 备注 |
|------------|----------|----------|----------|----------|------|
| 0 | 信号超速 | Signal Speed | ✅ 处理 | 0 | 信号灯处超速检测 |
| 1 | 超速(固定式) | Speed Camera (Fixed) | ✅ 处理 | 1 | 固定测速摄像头 |
| 2 | 区间测速开始 | Section Speed Start | ✅ 处理 | 2 | 区间测速起点 |
| 3 | 区间测速结束 | Section Speed End | ✅ 处理 | 3 | 区间测速终点 |
| 4 | 区间测速中 | Section Speed In Progress | ✅ 处理 | 4 | 区间测速进行中 |
| 5 | 跟车抓拍摄像头 | Following Camera | ❌ 不处理 | 5 | 跟车检测摄像头 |
| 6 | 信号灯抓拍 | Signal Camera | ❌ 不处理 | 6 | 信号灯抓拍 |
| 7 | 超速(移动式) | Speed Camera (Mobile) | ✅ 处理* | 7 | 移动测速设备 |
| 8 | 固定式超速危险区 | Fixed Speed Danger Zone | ✅ 处理 | 8 | 固定危险区域 |
| 22 | 减速带 | Speed Bump | ✅ 处理 | 22 | 减速带检测 |
| 75,76 | 特殊测速类型 | Special Speed Types | ✅ 处理 | 75,76 | 特殊测速设备 |
| 其他 | 其他类型 | Other Types | ❌ 不处理 | -1 | 默认值，不处理 |

**处理逻辑说明**:
- 处理条件: `nSdiType in [0,1,2,3,4,7,8,75,76]` 且 `nSdiSpeedLimit > 0` 且 `autoNaviSpeedCtrlMode > 0`
- 移动式测速(7): 需要 `autoNaviSpeedCtrlMode >= 3` 才处理
- 减速带(22): 需要 `roadcate > 1` 且 `autoNaviSpeedCtrlMode >= 2`

### nSdiSection - SDI区间测速ID字段

| 字段用途 | 数据类型 | 描述 | 实例数据 | 备注 |
|----------|----------|------|----------|------|
| 区间标识 | Integer | 区间测速的唯一标识符 | 12345 | 用于关联区间测速的起点和终点 |
| 默认值 | Integer | 无区间测速时的默认值 | 0 | 表示不在区间测速中 |

### nSdiBlockType - SDI区间状态字段

| nSdiBlockType值 | 中文描述 | 英文描述 | 处理逻辑 | 实例数据 | 备注 |
|-----------------|----------|----------|----------|----------|------|
| -1 | 无区间状态 | No Block State | 默认值 | -1 | 不在区间测速中 |
| 0 | 不减速 | No Deceleration | 不处理 | 0 | 区间内不减速 |
| 1 | 区间测速开始 | Section Speed Start | 处理 | 1 | 区间测速起点 |
| 2 | 区间测速中 | Section Speed In Progress | 处理 | 2 | 区间测速进行中 |
| 3 | 区间测速结束 | Section Speed End | 处理 | 3 | 区间测速终点 |

**处理逻辑**:
```python
if self.nSdiBlockType in [2,3]:
    self.xSpdDist = self.nSdiBlockDist  # 使用区间距离
    self.xSpdType = 4  # 设置为区间测速类型
```

### nSdiPlusType - SDI Plus扩展类型字段

| nSdiPlusType值 | 中文描述 | 英文描述 | 处理逻辑 | 实例数据 | 备注 |
|----------------|----------|----------|----------|----------|------|
| -1 | 无Plus类型 | No Plus Type | 默认值 | -1 | 无扩展类型 |
| 22 | 减速带 | Speed Bump | ✅ 处理 | 22 | 减速带检测 |
| 其他 | 其他扩展类型 | Other Plus Types | ❌ 不处理 | 0 | 暂不支持 |

**处理逻辑**:
```python
elif (self.nSdiPlusType == 22 or self.nSdiType == 22) and self.roadcate > 1 and self.autoNaviSpeedCtrlMode >= 2:
    self.xSpdLimit = self.autoNaviSpeedBumpSpeed
    self.xSpdDist = self.nSdiPlusDist if self.nSdiPlusType == 22 else self.nSdiDist
    self.xSpdType = 22
```

### nSdiPlusBlockType - SDI Plus区间类型字段

| nSdiPlusBlockType值 | 中文描述 | 英文描述 | 处理逻辑 | 实例数据 | 备注 |
|---------------------|----------|----------|----------|----------|------|
| -1 | 无Plus区间状态 | No Plus Block State | 默认值 | -1 | 无扩展区间状态 |
| 0 | 无区间 | No Section | 不处理 | 0 | 无区间测速 |
| 其他 | 扩展区间状态 | Extended Block States | 待扩展 | 1+ | 未来可能支持 |

### nTBTTurnType - TBT转弯类型字段

| nTBTTurnType值 | 转弯类型 | 修饰符 | xTurnInfo | 中文描述 | 实例数据 |
|----------------|----------|--------|-----------|----------|----------|
| 12 | turn | left | 1 | 左转 | 12 |
| 16 | turn | sharp left | 1 | 急左转 | 16 |
| 13 | turn | right | 2 | 右转 | 13 |
| 19 | turn | sharp right | 2 | 急右转 | 19 |
| 102,105,112,115 | off ramp | slight left | 3 | 出口匝道(左) | 102 |
| 101,104,111,114 | off ramp | slight right | 4 | 出口匝道(右) | 101 |
| 7,44,17,75,76,118 | fork | left | 3 | 分叉(左) | 7 |
| 6,43,73,74,123,124,117 | fork | right | 4 | 分叉(右) | 6 |
| 131,132 | rotary | slight right | 5 | 环岛(右) | 131 |
| 140,141 | rotary | slight left | 5 | 环岛(左) | 140 |
| 133,134,135 | rotary | right/sharp right | 5 | 环岛(右) | 133 |
| 136,137,138 | rotary | sharp left | 5 | 环岛(左) | 136 |
| 139 | rotary | left | 5 | 环岛(左) | 139 |
| 142 | rotary | straight | 5 | 环岛(直行) | 142 |
| 14 | turn | uturn | 7 | 掉头 | 14 |
| 201 | arrive | straight | 8 | 到达 | 201 |
| 51,52,53,54,55 | notification | straight | 0 | 通知 | 51 |
| 153,154,249 | "" | "" | 6 | 交通灯 | 153 |

### nTBTNextRoadWidth - 下一道路宽度字段

| nTBTNextRoadWidth值 | 道路宽度描述 | 车道数 | 实例数据 | 备注 |
|---------------------|--------------|--------|----------|------|
| 0 | 未知宽度 | 未知 | 0 | 默认值 |
| 1-3 | 窄道路 | 1车道 | 2 | 单车道道路 |
| 4-6 | 中等宽度 | 2车道 | 6 | 双车道道路 |
| 7-9 | 宽道路 | 3车道 | 8 | 三车道道路 |
| 10+ | 很宽道路 | 4+车道 | 10 | 多车道道路 |

**用途**: 用于计算转弯控制距离，影响ATC(自动转弯控制)的启动距离。

### nTBTTurnTypeNext - 下一转弯类型字段

| 字段说明 | 数据类型 | 描述 | 实例数据 | 备注 |
|----------|----------|------|----------|------|
| 下一转弯类型 | Integer | 下下个转弯的类型编码 | 12 | 与nTBTTurnType使用相同的编码表 |
| 默认值 | Integer | 无下一转弯时的默认值 | -1 | 表示没有下下个转弯 |
| 用途 | - | 用于提前规划转弯策略 | - | 帮助系统提前准备转弯控制 |

### 字段关系总结

| 字段组合 | 关系说明 | 处理逻辑 |
|----------|----------|----------|
| `nSdiType` + `nSdiBlockType` | 区间测速状态管理 | `nSdiBlockType in [2,3]`时使用区间距离 |
| `nSdiType` + `nSdiPlusType` | 减速带检测 | 任一为22时触发减速带处理 |
| `nTBTTurnType` + `nTBTNextRoadWidth` | 转弯控制 | 道路宽度影响转弯启动距离 |
| `nTBTTurnType` + `nTBTTurnTypeNext` | 连续转弯 | 支持连续转弯的提前规划 |

---

## 更新日志

- **2024-01-01**: 初始版本，基于carrot_man.py和carrot_serv.py分析
- **2024-01-01**: 添加CarrotManDataModels.kt字段映射
- **2024-01-01**: 完善实例数据和备注信息
- **2024-01-01**: 新增roadcate字段详细分析表格
- **2024-01-01**: 根据开发者说明修正roadcate字段含义（道路宽度，10/11=高速公路）
- **2024-01-01**: 新增详细字段参数分析，包含nSdiType、nSdiSection、nSdiBlockType、nSdiPlusType、nSdiPlusBlockType、nTBTTurnType、nTBTNextRoadWidth、nTBTTurnTypeNext等字段的完整映射表

---

## 来自 OpenPilot 系统的字段（来源标注）

下表列出在 `carrot_man.py`、`carrot_serv.py`、`carrot_functions.py` 中直接从 OpenPilot 的 SubMaster/Params/消息体读取的字段。

| 名称 | 说明 | 类型 | 解释 | 实例 | 备注 | 来源 |
|------|------|------|------|------|------|------|
| IsOnroad | 是否在道路上 | Boolean | 来自 Params("IsOnroad") 或设备状态 | true | 由 comma3 系统状态决定 | carrot_man.Params / deviceState |
| Version | OpenPilot 版本 | String | Params("Version") | "0.9.4" | 用于广播版本 | carrot_man.Params |
| v_ego | 车辆实际速度 | Float | carState.vEgo (m/s) | 16.7 | 乘以3.6→km/h | carState |
| v_ego_kph | 实际速度(km/h) | Float | vEgo*3.6 | 60.1 | 显示与控制用 | carState |
| vEgoCluster | 仪表速度 | Float | carState.vEgoCluster | 15.0 | 有时用于巡航控制 | carState |
| vCruise | 巡航设定速度 | Float | carState.vCruise | 80.0 | 可能被融合控制修改 | carState |
| speedLimit | 地图限速 | Float | carState.speedLimit (m/s) | 27.8 | HDA来源限速 | carState |
| speedLimitDistance | 限速距离 | Float | carState.speedLimitDistance | 300 | HDA剩余距离 | carState |
| steeringPressed | 方向盘按压 | Boolean | 人为干预检测 | false | ATC暂停逻辑参考 | carState |
| steeringTorque | 转向力矩 | Float | 手动转向力度 | 0.2 | ATC暂停判定 | carState |
| gasPressed | 油门按下 | Boolean | 人为加速 | false | 影响ATC与起步 | carState |
| brakePressed | 刹车按下 | Boolean | 人为制动 | false | 安全逻辑 | carState |
| softHoldActive | 软保持 | Boolean | 是否软保持停车 | false | 影响起步状态 | carState |
| vCluRatio | 仪表比例 | Float | vCruise修正因子 | 1.0 | 巡航速度换算 | carState |
| bearing_calculated | 航向角 | Float | liveLocationKalman.calibratedOrientationNED | 123.4 | 用于位置推算 | liveLocationKalman |
| positionGeodetic | 位置有效 | Bool/Struct | 经纬度与有效性 | valid | 更新内部GPS | liveLocationKalman |
| distanceTraveled | 行驶距离 | Float | selfdriveState.distanceTraveled | 12345.6 | 用于距离差计算 | selfdriveState |
| active | 控制激活 | Boolean | selfdriveState.active | true | 车辆是否在控 | selfdriveState |
| model.orientationRate.z | 横摆速率 | Array | 模型预测横摆速率 | [...] | 曲率/弯速估计 | modelV2 |
| model.velocity.x | 纵向速度序列 | Array | 模型速度轨迹 | [...] | 曲率/弯速估计 | modelV2 |
| navInstruction | 导航提示 | Msg | OP 内部导航提示 | type/modifier | 作为回退/融合 | navInstruction |
| networkType | 网络类型 | Enum | 设备网络状态 | wifi | 用于日志/上传 | deviceState |

注：以上均通过 SubMaster 订阅或 Params 获取，详见 `CarrotMan.__init__` 与 `CarrotServ.update_navi()`、`CarrotPlanner` 中的使用。

---

## 由其他字段计算/派生的字段（来源标注）

下表列出通过内部计算、插值或映射得到的派生字段。

| 名称 | 说明 | 类型 | 解释 | 实例 | 备注 | 来源 |
|------|------|------|------|------|------|------|
| route_speed | 路径推荐速度 | Float | 基于路径曲率与参数计算 | 85.0 | 曲率插值与加速度限幅 | carrot_man.carrot_navi_route / carrot_serv.update_navi |
| out_speed | 弯道输出速度 | Float | 根据最大横向加速度和模型曲率估计 | 72.5 | 上限250，下限5 | carrot_man.carrot_navi_route |
| coords_str(naviPaths) | 路径点串 | String | 相对坐标重采样与拼接 | "x,y,d;..." | 5m采样 | carrot_serv.update_navi |
| bearing_offset | 航向修正量 | Float | 由模型/导航/手机航向融合估计 | 2.1 | 平滑修正 | carrot_serv._update_gps |
| vpPosPointLat/Lon | 预测位置 | Double | 基于速度与航向估计位置 | (lat,lon) | 位置外推 | carrot_serv.estimate_position |
| desiredSpeed | 期望速度 | Float | 多源竞赛取最小值 | 48.0 | atc/hda/cam/route/road/gas | carrot_serv.update_navi |
| desiredSource | 速度来源 | String | 最小速度来源标记 | "atc" | 诊断用 | 同上 |
| atcType | 自动转弯状态 | String | 基于 xTurnInfo 与距离计算 | "turn left prepare" | 带有prepare/canceled后缀 | carrot_serv.update_auto_turn |
| atcSpeed/atcDist | ATC参数 | Int | 基于参数与限速计算 | 35/60 | 不同 xTurnInfo 有不同规则 | 同上 |
| xDistToTurn/Next | 转弯距离 | Int | 由 nTBTDist 等更新与累加 | 120/350 | Next包含累加 | carrot_serv._update_tbt |
| xTurnInfo/xTurnInfoNext | 控制类别 | Int | 由 nTBTTurnType 映射 | 1/4 | 见 nav_type_mapping | carrot_serv._update_tbt |
| szSdiDescr | SDI文本 | String | 由 nSdiType 通过表映射 | "신호과속" | 0-66映射 | carrot_serv._get_sdi_descr |
| xSpdLimit/xSpdDist | SDI控制量 | Int | 由 SDI/区间/Plus规则计算 | 50/300 | 安全系数与区间替换 | carrot_serv._update_sdi |
| xSpdType | SDI控制类型 | Int | 由 SDI类型派生(含100,101) | 4 | Waze/警察特殊规则 | 同上/更新KISA |
| left_spd_sec/left_tbt_sec | 倒计时 | Int | 基于距离与速度估算 | 7/9 | autoNaviCountDownMode | carrot_serv.update_navi |
| left_sec/carrot_left_sec | 综合倒计时 | Int | 两者取小并限幅 | 6/11 | 触发显示/提示 | 同上 |
| active_carrot | Carrot状态 | Int | 基于活跃计数与SDI等 | 0/1/2/3/4/5/6 | 影响UI与控制 | carrot_serv.update_navi |
| roadcate | 道路类别 | Int | 由手机 ROAD_TYPE 映射 | 6/10 | 宽度语义，影响减速带 | Amap→Android→carrot_serv |
| nRoadLimitSpeed | 路口限速 | Int | 多源融合/计数稳定 | 60 | 防抖:超过5次才更新 | carrot_serv.update |
| traffic_state | 红绿灯状态 | Int | 由 traffic_light 队列与触发计算 | 0/1/2/3 | 队列投票式 | carrot_serv.traffic_light |
| jamDrivingMode | 拥堵驾驶模式 | Enum | 基于雷达/速度/加速度判断 | Safe/Normal | 自动切换 | carrot_functions.DrivingModeDetector |
| stop_dist | 预计停车距离 | Float | 基于舒适制动与模型距离 | 12.3 | e2eStop策略 | carrot_functions.CarrotPlanner |
| vturn_speed | 弯道控制速度 | Float | 模型横摆速率与参数推导 | 65.0 | TARGET_LAT_A约束 | carrot_man.vturn_speed |

来源说明：
- 多源融合项（desiredSpeed/desiredSource）取 ATC、下一ATC、SDI/HDA、道路限速、路线速度、驾驶员油门等一组候选中的最小值，并在特定条件下切换来源标签。
- xTurnInfo/xTurnInfoNext 与 nTBTTurnType 的映射以 `nav_type_mapping` 为准；环岛/到达/掉头统一归入 xTurnInfo=5 控制族。
- nRoadLimitSpeed 存在防抖逻辑（计数>5才更新），避免瞬时异常导致油门/降速抖动。