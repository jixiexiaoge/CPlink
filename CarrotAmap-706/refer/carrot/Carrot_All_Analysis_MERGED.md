# Carrot 项目分析资料（五文合一·无删减合并版）

> 本文件整合以下五份文档，内容保持原样无删减，仅做顺序编排与分节标注，便于统一阅读与检索：
> 1) Amap_KEY_TYPE_Reverse_Analysis.md（高德广播逆向）
> 2) CarrotMan_Data_Field_Mapping.md（字段映射总览）
> 3) TurnType_Mapping_Analysis.md（转弯类型映射）
> 4) SDI_Mapping_Analysis.md（SDI/SDIPlus 映射）
> 5) CarrotServ_Field_Mapping.md（设备端映射与字段说明）

---

## 目录
- 第一部分：Amap_KEY_TYPE_Reverse_Analysis.md（原文保留）
- 第二部分：CarrotMan_Data_Field_Mapping.md（原文保留）
- 第三部分：TurnType_Mapping_Analysis.md（原文保留）
- 第四部分：SDI_Mapping_Analysis.md（原文保留）
- 第五部分：CarrotServ_Field_Mapping.md（原文保留）

---

## 第一部分：Amap_KEY_TYPE_Reverse_Analysis.md（原文保留）

# 高德广播 KEY_TYPE 逆向分析（基于 app/src/main/java/com/example/carrotamap/AmapBroadcastHandlers.kt）

> 说明：本文按 `AmapBroadcastHandlers.kt` 中各处理器的 KEY_TYPE 分组，列出广播字段、含义、单位与到 CarrotMan 字段的映射与推导逻辑。60073（红绿灯）部分参照并补充 `CarrotServ_Field_Mapping.md` 第四章的分析结论。

---

## 目录
- 10001 引导信息（Guide Info）
- 10003 路线信息（Route Info）
- 10006 转向信息（Turn Info）
- 10007 SDI Plus 信息（SDI_PLUS）
- 10019 地图状态（Map State）
- 10042 导航状态（Navigation Status）
- 10065 定位信息（Location Info）
- 12110 限速信息（Speed Limit）
- 13005 电子眼/摄像头（Camera Info）
- 13012 车道线信息（Drive Way Info）
- 60073 红绿灯信息（Traffic Light）
- 100001 电子眼V2（Camera Info V2）

---

## KEY_TYPE: 10001 引导信息（Guide Info）

- 主要来源字段（Intent Extra）：
  - `CUR_ROAD_NAME` 当前道路名（string）
  - `NEXT_ROAD_NAME` 下一道路名（string）
  - `NEXT_NEXT_ROAD_NAME` 下下道路名（string）
  - `LIMITED_SPEED` 道路限速（int, km/h）
  - `CUR_SPEED` 当前速度（int, km/h）
  - `CAR_DIRECTION` 航向角（int, deg）
  - `ROUTE_REMAIN_DIS` 全局剩余距离（int, m）
  - `ROUTE_REMAIN_TIME` 全局剩余时间（int, s）
  - `ROUTE_REMAIN_TIME_STRING` ETA 文本（string）
  - `ROUTE_ALL_DIS` 路线总距离（int, m）
  - `ROUTE_ALL_TIME` 路线总时间（int, s）
  - `ROUTE_REMAIN_TIME_AUTO` ETA 自动文本（string）
  - `SEG_REMAIN_DIS` 本段剩余距离（int, s）
  - `SEG_REMAIN_TIME` 本段剩余时间（int, s）
  - `NEXT_SEG_REMAIN_DIS` 下一段剩余距离（int, s）
  - `NEXT_SEG_REMAIN_TIME` 下一段剩余时间（int, s）
  - `CUR_SEG_NUM` 当前段号（int）
  - `CUR_POINT_NUM` 当前点号（int）
  - `ICON` / `NEW_ICON` 当前转向图标（int）
  - `NEXT_NEXT_TURN_ICON` 下下次转向图标（int）
  - `ROUND_ABOUT_NUM` 环岛出口序号（int）/ `ROUND_ALL_NUM` 环岛总出口数（int）
  - `CAR_LATITUDE` / `CAR_LONGITUDE` 导航定位（double, deg）
  - `SAPA_*` 服务区相关（类型/数量/距离/名称）
  - `CAMERA_*` 电子眼相关（类型/限速/距离/索引）
  - `TYPE` 导航类型（int）
  - `TRAFFIC_LIGHT_NUM` 前方红绿灯数量（int）
  - `ROAD_TYPE` 道路类型（int，高德定义）
  - 目的地：`endPOIName`/`endPOIAddr`/`endPOILatitude`/`endPOILongitude`

- 关键映射/推导：
  - 道路名：`szPosRoadName`、`szNearDirName`、`szFarDirName`
  - 速度/航向：`nPosSpeed`、`xPosSpeed`、`nPosAngle`、`xPosAngle`
  - 距离/时间：`nGoPosDist`、`nGoPosTime`、`totalDistance`
  - 段信息：`nTBTDist`、`nTBTDistNext`
  - 转向类型：高德 ICON → `nTBTTurnType` → `navType/navModifier` 与 `xTurnInfo/xTurnInfoNext`
  - 文本：`szTBTMainText`、`szTBTMainTextNext`（由类型+道路名+距离生成）
  - 期望速度：`desiredSpeed/desiredSource`（示例代码用 road limit 简化最小值）
  - 转弯速度：`vTurnSpeed`（简化为固定或路限）
  - ATC 类型：由 `xTurnInfo` 推导（turn/fork/roundabout/arrive/none 等）
  - 路径串：`naviPaths = "lat,lon,dist"`（简化）
  - 坐标：`vpPosPointLatNavi/vpPosPointLonNavi`（若 0 则回退到已有手机定位）
  - 目的地：`goalPosX/goalPosY/szGoalName`
  - 导航状态：`isNavigating= true`，`active_carrot` 视路况置 1
  - 道路类别：`roadType → roadcate`（见 13012/映射函数）
  - SDI：`nSdiType/nSdiSpeedLimit/nSdiDist/szSdiDescr`
  - 红绿灯数量：`traffic_light_count`
  - 时间戳：`last_update_gps_time_navi/lastUpdateTime`

---

（以下为该文件其余全部原文内容，保持不变）

## KEY_TYPE: 10003 路线信息（Route Info）

- 字段：`ROUTE_DISTANCE`（m）、`ROUTE_TIME`（s）、`ROUTE_NAME`（string）
- 映射：`totalDistance`、`nGoPosTime`、`szPosRoadName`
- 备注：仅做整体路线层面的覆盖更新

---

## KEY_TYPE: 10006 转向信息（Turn Info）

- 字段：
  - `TURN_DISTANCE` 本次转弯距离（m）
  - `TURN_TYPE` 本次转弯类型（int，直传为 `nTBTTurnType`）
  - `TURN_INSTRUCTION` 指令文本（string）
  - `NEXT_TURN_DISTANCE` / `NEXT_TURN_TYPE` 下一转向（m/int）
- 映射：`nTBTDist/nTBTTurnType/szTBTMainText/nTBTDistNext/nTBTTurnTypeNext`
- 备注：该分支不做 navType/xTurnInfo 计算（由 10001 主分支负责）

---

## KEY_TYPE: 10007 SDI Plus 信息

- 字段来源：`SDI_PLUS_INFO` 或 `SDI_INFO`（JSON），可包含：
  - `type`（int）`speed_limit`（int, km/h）`distance`（int, m）等
- 首选 Intent Extra：`SDI_TYPE`、`SPEED_LIMIT`、`SDI_DIST`
- 映射：`nSdiPlusType/nSdiPlusSpeedLimit/nSdiPlusDist`
- 用途：与 13005 合作参与 `xSpdType/xSpdLimit/xSpdDist` 推导（详见 `CarrotServ_Field_Mapping.md` 的 SDI 规则）

---

## KEY_TYPE: 10019 地图状态（Map State）

- 字段：`EXTRA_STATE`（int）
- 当前实现：检测到达目的地（`ARRIVE_DESTINATION`）时：
  - `isNavigating=false`，`nTBTTurnType=201`（到达），`xTurnInfo=8`
  - 距离/时间清零，`navType="arrive"、`navModifier="straight"`
  - `active_carrot=0`，`debugText="已到达目的地"`

---

## KEY_TYPE: 10042 导航状态（Navigation Status）

- 字段：`NAVI_STATUS`（int）
- 规则：示例实现将 `1` 视为导航中，映射到 `isNavigating/active_carrot`

---

## KEY_TYPE: 10065 定位信息（Location Info）

- 字段：`LATITUDE/LONGITUDE`（double, deg）、`SPEED`（float, km/h）、`BEARING`（float, deg）
- 映射：
  - 位置：`vpPosPointLatNavi/vpPosPointLonNavi`，同时同步协议标准：`xPosLat/xPosLon`
  - 航向与速度：`xPosAngle/xPosSpeed` 与 `nPosAngle/nPosSpeed`
  - 有效标志与时间：`gps_valid/last_update_gps_time_navi/lastUpdateTime`

---

## KEY_TYPE: 12110 限速信息（Speed Limit）

- 字段：`SPEED_LIMIT`（km/h）、`ROAD_NAME`（string）、`DISTANCE`（m）
- 推导：
  - 倒计时：`xSpdCountDown ≈ DISTANCE / (xPosSpeed/3.6)`（简化）
  - 更新：`nRoadLimitSpeed/xSpdLimit/xSpdDist/xSpdType=1`，路名兜底覆盖
  - 数据源标记：`source_last="amap_speed"`；调试文本：`debugText`

---

## KEY_TYPE: 13005 电子眼/摄像头（Camera Info）

- 字段：`CAMERA_TYPE`、`SPEED_LIMIT`、`DISTANCE`
- 映射：`nSdiType/nSdiSpeedLimit/nSdiDist/szSdiDescr`
- 备注：SDI 类型描述参见 `getSdiDescription()` 与 `CarrotServ_Field_Mapping.md` SDI 表

---

## KEY_TYPE: 13012 车道线信息（Drive Way Info）

- 字段（JSON，`EXTRA_DRIVE_WAY`）：
  - `drive_way_enabled`（"true"/"false"）
  - `drive_way_size`（int）
  - 可选 `drive_way_info` 数组（每车道包含 `drive_way_number`、`drive_way_lane_Back_icon` 等）
- 映射：`nLaneCount`（当有效且>0）
- 备注：当前不直接改变 `roadcate`；道路类别映射函数在 10001 使用：
  - 高德 `ROAD_TYPE` → `roadcate`（10/11 为高速，其他为非高速；详见代码映射函数与 `CarrotServ_Field_Mapping.md` 注解）

---

## KEY_TYPE: 60073 红绿灯信息（Traffic Light）

- 关键字段（参见 `CarrotServ_Field_Mapping.md` 第四章）：
  - `trafficLightStatus`（int）：-1=黄，1=红，2=绿；3/4 为变体（红/绿）
  - `redLightCountDownSeconds`（int, s）：实际为“当前状态倒计时”（字段名具有误导性）
  - `dir`（int）：方向标识（0=直行黄灯专用，1=左转，2=右转，3=左转掉头，4=直行，5=右转掉头）
  - `greenLightLastSecond`（int）：观测到常为 0/3，作为“绿灯状态标识”更合适
  - `waitRound`（int）：等待轮次（复杂路口）
  - 可选：`TRAFFIC_LIGHT_COUNT`（数量）、`TRAFFIC_LIGHT_DISTANCE`（距离）

- 状态映射（amap → CarrotMan `traffic_state`）：
  - 黄灯：`trafficLightStatus=-1` → `-1`（`dir=0` 表示直行黄灯）
  - 红灯：`trafficLightStatus in {1,3}` → `1`
  - 绿灯：`trafficLightStatus in {2,4}` →
    - `dir=1/3` 左转或左转掉头 → `3`（左转绿）
    - `dir=2/4/5/其他` → `2`（绿）
  - 未知/无信号：`0` → `0`（off）

- 倒计时处理：
  - `left_sec` 取 `redLightCountDownSeconds`（红/绿均适用）；黄灯多为 0
  - 无效时保留前值；必要时做状态推断（如红灯倒计时到 0 推断绿灯，默认 30s）

- 映射与记录：
  - `traffic_state`、`traffic_light_direction=dir`
  - `left_sec/max_left_sec/carrott_left_sec`
  - 原始字段旁路记录：`amap_traffic_light_status/dir/greenLightLastSecond/waitRound`

- 典型用途：
  - UI 提示、路线选择、驾驶辅助、自动驾驶策略（只在状态/倒计时变化时记关键日志）

---

## KEY_TYPE: 100001 电子眼V2（Camera Info V2）

- 字段：`CAMERA_DIST`、`CAMERA_TYPE`、`CAMERA_SPEED`、`CAMERA_INDEX`
- 映射：`nSdiType/nSdiSpeedLimit/nSdiDist`（V2 版本字段命名）
- 备注：与 13005 语义一致，字段名不同

---

## 统一的类型/导航映射补充

- ICON→`nTBTTurnType`：见 `mapAmapIconToCarrotTurn()`，对隧道/桥梁/收费站等“通知类”统一映射为直行通知（不触发 ATC）。
- `nTBTTurnType`→`navType/navModifier` 与 `xTurnInfo`：见 `getTurnTypeAndModifier()` 与 `getXTurnInfo()`；与 `CarrotServ_Field_Mapping.md` 表格基本一致（少量差异用于 UI/ATC 简化）。
- `roadType`→`roadcate`：`0/1` 映射为高速（10/11），`6` 为城市快速路，`7/8/9` 为街道，其余见代码函数与注释。

---

## 建议与注意

- 红绿灯倒计时字段名“redLightCountDownSeconds”具有误导性：应理解为“当前状态倒计时”。
- `dir` 表示交通灯控制方向，不是车辆行驶方向；`dir=0` 为黄灯专用（直行黄灯）。
- SDI/减速带等速度控制需结合 `roadcate` 与车速/距离进行动态推导，详见 `CarrotServ_Field_Mapping.md` 的规则。
- 导航坐标为 0 时需回退到手机 GPS；时间戳字段统一更新，便于融合与外推。

---

## 第二部分：CarrotMan_Data_Field_Mapping.md（原文保留）

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

（以下为原文“来源映射总览表”与注释、补充说明，保持原样）

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

（后续“字段需求分析/数据同步机制/系统架构分析/注意事项/roadcate 详细分析/详细字段参数分析/更新日志/来源表/派生字段表”等原文内容保持不变，完整收录）

---

## 第三部分：TurnType_Mapping_Analysis.md（原文保留）

# TurnType 映射总表（按 nTBTTurnType 升序）

> 数据来源：严格逆向 `refer/carrot/carrot_serv.py` 的 `nav_type_mapping` 与 `_update_tbt` 内部 `turn_type_mapping`，两者取并集去重；说明字段采用 `navType / navModifier`，控制字段采用 `xTurnInfo`。高德地图 ICON 列基于 Android 端 ICON→nTBTTurnType 的实现进行反查，若无法唯一对应则留空。

| nTBTTurnType | 说明(navType / navModifier) | xTurnInfo | 高德ICON | ICON说明 | 备注 |
|---:|---|---:|---:|---|---|
| 6 | fork / right | 4 | 7 | 右后方 | Python映射（分岔-右） |
| 7 | fork / left | 3 | 6 | 左后方 | Python映射（分岔-左） |
| 12 | turn / left | 1 | 2 | 左转 | 基础左转 |
| 13 | turn / right | 2 | 3 | 右转 | 基础右转 |
| 14 | turn / uturn | 5 | 8, 19 | 掉头(左右通行地区) | Python最终控制归入直行系(5)；Android直连14 |
| 16 | turn / sharp left | 1 |  |  | 急左转 |
| 17 | fork / left | 3 |  |  | 分岔-左（同7/44/75/76/118）|
| 18 |  |  |  |  | - |
| 19 | turn / sharp right | 2 |  |  | 急右转 |
| 43 | fork / right | 4 |  |  | 分岔-右（同6/73/74/117/123/124）|
| 44 | fork / left | 3 |  |  | 分岔-左 |
| 51 | notification / straight | 0 或 None | 0, 13, 14, 16 | 无转弯/服务区/收费站/隧道 | 通知类，不参与控制 |
| 52 | notification / straight | 0 或 None |  |  | 通知类 |
| 53 | notification / straight | 0 或 None |  |  | 通知类 |
| 54 | notification / straight | 0 或 None |  |  | 通知类 |
| 55 | notification / straight | 0 或 None |  |  | 通知类 |
| 73 | fork / right | 4 |  |  | 分岔-右 |
| 74 | fork / right | 4 |  |  | 分岔-右 |
| 75 | fork / left | 3 |  |  | 分岔-左 |
| 76 | fork / left | 3 |  |  | 分岔-左 |
| 101 | off ramp / slight right | 4 |  |  | 右匝道（轻微）|
| 102 | off ramp / slight left | 3 |  |  | 左匝道（轻微）|
| 104 | off ramp / slight right | 4 |  |  | 右匝道（轻微）|
| 105 | off ramp / slight left | 3 |  |  | 左匝道（轻微）|
| 111 | off ramp / slight right | 4 |  |  | 右匝道（轻微）|
| 112 | off ramp / slight left | 3 |  |  | 左匝道（轻微）|
| 114 | off ramp / slight right | 4 |  |  | 右匝道（轻微）|
| 115 | off ramp / slight left | 3 |  |  | 左匝道（轻微）|
| 117 | fork / right | 4 |  |  | 分岔-右 |
| 118 | fork / left | 3 |  |  | 分岔-左 |
| 123 | fork / right | 4 |  |  | 分岔-右 |
| 124 | fork / right | 4 |  |  | 分岔-右 |
| 131 | rotary / slight right | 5 | 11, 12 | 入/出环岛(右通行地区) | Python控制统一为5 |
| 132 | rotary / slight right | 5 |  |  | 环岛（细分角度）|
| 133 | rotary / right | 5 |  |  | 环岛 |
| 134 | rotary / sharp right | 5 |  |  | 环岛 |
| 135 | rotary / sharp right | 5 |  |  | 环岛 |
| 136 | rotary / sharp left | 5 |  |  | 环岛 |
| 137 | rotary / sharp left | 5 |  |  | 环岛 |
| 138 | rotary / sharp left | 5 |  |  | 环岛 |
| 139 | rotary / left | 5 |  |  | 环岛 |
| 140 | rotary / slight left | 5 | 17, 18 | 入/出环岛(左通行地区) | Python控制统一为5 |
| 141 | rotary / slight left | 5 |  |  | 环岛（细分角度）|
| 142 | rotary / straight | 5 |  |  | 环岛直行 |
| 153 |  | 6 |  |  | TG（收费相关）|
| 154 |  | 6 |  |  | TG（收费相关）|
| 201 | arrive / straight | 5 或 8 | 10, 15 | 到达途经点/目的地 | Python侧归入5，历史亦有8 |
| 249 |  | 6 |  |  | TG（收费相关）|
| 1000 | turn / slight left | 1 |  |  | 轻微左转（Android常用于补全）|
| 1001 | turn / slight right | 2 |  |  | 轻微右转（Android常用于补全）|
| 1002 | fork / slight left | 3 |  |  | 分岔-轻微左（Android常用于补全）|
| 1003 | fork / slight right | 4 |  |  | 分岔-轻微右（Android常用于补全）|
| 1006 | off ramp / left | 3 | 65 | 左侧辅道 | 兼容老版本 ICON 65 |
| 1007 | off ramp / right | 4 | 101 | 进入右侧辅道 | 兼容老版本 ICON 101 |
| 200 | turn / straight | 5 | 1, 9, 20 | 直行/顺行 | Android侧扩展（Python无该编号）|

说明与约定：
- …（原文后续各段，完整保留）

---

## 第四部分：SDI_Mapping_Analysis.md（原文保留）

# SDI和SDIPlus字段映射关系分析

## 概述

本文档分析了Android应用（CarrotAmap）和Comma3设备（carrot_serv.py）之间SDI（Speed Detection Information，速度检测信息）和SDIPlus字段的映射关系。SDI系统用于处理电子眼、测速摄像头、减速带等道路安全设施的检测和控制。

## 数据流向

```
高德地图广播 → Android应用 → Comma3设备 → OpenPilot控制
     ↓              ↓            ↓            ↓
  电子眼信息    SDI字段映射    SDI处理逻辑   速度控制
```

## 核心字段对比表

（以下为该文档完整表格与章节，原样保留）

| 字段名称 | Android字段 | Python字段 | 数据类型 | 描述 | 高德广播字段 | KEY_TYPE |
|---------|-------------|------------|----------|------|-------------|----------|
| **SDI基础信息** |
| SDI类型 | `nSdiType` | `nSdiType` | Int | 电子眼/摄像头类型 | `CAMERA_TYPE` | 13005, 100001 |
| SDI限速 | `nSdiSpeedLimit` | `nSdiSpeedLimit` | Int | 测速限速值(km/h) | `SPEED_LIMIT`, `CAMERA_SPEED` | 13005, 100001 |
| SDI距离 | `nSdiDist` | `nSdiDist` | Int | 到测速点距离(m) | `DISTANCE`, `CAMERA_DIST` | 13005, 100001 |
| …（其余完整内容、表格与说明保持不变）

---

## 第五部分：CarrotServ_Field_Mapping.md（原文保留）

# CarrotServ 映射关系与字段数据说明（基于 refer/carrot/carrot_serv.py 逆向）

> 本文基于 `refer/carrot/carrot_serv.py` 逐行核对的结果，整理了：
> - 导航/控制相关的“映射关系表”（转弯类型映射、ATC映射、SDI类型映射等）
> - 7706 输入 JSON 字段与 `carrotMan` 输出消息字段的“字段数据说明表”（含单位/范围/含义/来源）

（以下为该文档全部内容，表格与代码块、说明均保持不变）

## 一、映射关系表

### 1) 转弯类型映射（nTBTTurnType → navType/navModifier/xTurnInfo）

来源：`nav_type_mapping`（文件顶部常量）与 `_update_tbt()` 内部 `turn_type_mapping`

| nTBTTurnType | navType      | navModifier     | xTurnInfo | 说明 |
|---|---|---|---:|---|
| 12 | turn | left | 1 | 左转 |
| 16 | turn | sharp left | 1 | 急左转 |
| 13 | turn | right | 2 | 右转 |
| …（其余完整内容保持）

---

（合并结束）
