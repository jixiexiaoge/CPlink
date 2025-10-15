# Carrot 导航系统文档（合并版）

> 本文件由以下两份文档无删减合并而成：
> 1) Carrot_Reverse_Engineering_Documentation.md
> 2) app分析.md

---

## 第一部分：Carrot_Reverse_Engineering_Documentation.md（原文保留）

```markdown
# Carrot导航系统逆向工程文档（校正版）

## 概述

Carrot 是集成在 OpenPilot 平台上的导航/速度控制扩展，主要运行于 comma3 设备上，通过本地网络与手机 App 通讯。本文档基于对以下核心代码的逐行核对与逆向分析后进行校正：
- `refer/carrot/carrot_man.py`
- `refer/carrot/carrot_serv.py`
- `refer/carrot/carrot_functions.py`
- 相关工具与模拟程序：`vehicle_data_broadcaster.py`、`vehicle_monitor_web.py`、`comma3_simulator.py`

文档中的端口、协议、数据结构、状态机与速度控制逻辑均以源码实现为准，并对原版文档中数处偏差进行了纠正与说明。

## 系统架构（校对）

### 组件与职责

- CarrotMan（`carrot_man.py`）
  - 网络通讯与路线/广播调度：
    - UDP 广播/单播状态（端口 7705，约 10Hz）
    - UDP 接收 App 主数据（端口 7706）
    - TCP 接收二进制路线（端口 7709）
    - ZMQ 命令（端口 7710，REQ/REP）
    - KISA/Waze 风格数据（端口 12345）
  - 路径曲率分析 + 基于模型的弯道推荐速度（vturn）
  - 与 `CarrotServ` 协同，封装并发布 `carrotMan`、`navRoute`、`navInstructionCarrot` 消息

- CarrotServ（`carrot_serv.py`）
  - 主数据融合与速度源合成：
    - 外部导航/手机与内部 LLK 的 GPS 融合与航向估计
    - SDI（测速/区间/减速带）与 OpenPilot speedLimit 的统一处理
    - TBT（逐向导航）解析与 ATC（自动转弯控制）
    - 路网曲率（route）与模型曲率（vturn）速度约束的条件性参与
    - 倒计时（leftSec）与调试文本输出

- CarrotPlanner（`carrot_functions.py`）
  - 端到端停车/起步信号判定（基于模型的 stop/go）
  - 动态跟驰时间间隔（T_FOLLOW）与驾驶模式对 jerk 因子的影响
  - 与 `carrotMan` 广播的 `trafficState`/`atcType` 协作

### 线程（与实现一致）
- 广播线程：`broadcast_version_info()` → UDP 7705
- 数据线程：`carrot_man_thread()` → UDP 7706（App→comma3）
- 路线线程：`carrot_route()` → TCP 7709（App→comma3，二进制）
- 命令线程：`carrot_cmd_zmq()` → ZMQ 7710
- KISA 线程：`kisa_app_thread()` → UDP 12345
- 调试线程：`carrot_panda_debug()` → 按需

## 通讯协议（校正）

### 7705 UDP 广播/单播（设备状态）
- 发送方：comma3（CarrotMan）
- 频率：约 10Hz（`Ratekeeper(10)`）
- 实际行为：既可能向广播地址发送，也可能向最近的远端客户端 IP 单播。`ip` 字段为设备局域网 IP（非广播地址）。
- 字段包含（与实现匹配）：
  - `Carrot2`（版本，来自 `Params("Version")`）
  - `IsOnroad`（`Params("IsOnroad")`）
  - `CarrotRouteActive`（是否已接收/激活路径）
  - `ip`（设备本机 IP）
  - `port`（固定 7706）
  - `log_carrot`（`carState.logCarrot`）
  - `v_cruise_kph`（`carState.vCruise`）
  - `v_ego_kph`（`carState.vEgoCluster*3.6`，取整）
  - `tbt_dist`（由 `carrot_serv.xDistToTurn`）
  - `sdi_dist`（由 `carrot_serv.xSpdDist`）
  - `active`（`selfdriveState.active`）
  - `xState`（`longitudinalPlan.xState`）
  - `trafficState`（`longitudinalPlan.trafficState`）

提示：`trafficState` 可为 0/1/2/3（off/red/green/left），虽然 `carrot_functions.py` 的内部枚举只包含 0/1/2，但广播取自 `longitudinalPlan`，实际含 3。

### 7706 UDP 主数据（App→comma3）
- 内容：JSON。`CarrotServ.update(json)` 按键更新内部状态（GPS/SDI/TBT/命令等）。
- 最小定位数据：`carrotIndex`、`latitude`、`longitude`、`heading`（与原文一致）。
- 完整导航数据：与原文所列键基本一致，用于限速/转弯/目的地等。
- 命令数据：`{"carrotCmd":"DETECT","carrotArg":"Red Light,x,y,conf"}` 触发交通灯状态机。

### 7709 TCP 路线数据（App→comma3）
- 载荷格式（与实现一致）：
  - 首部：4 字节无符号整型，总负载大小（网络字节序，大端）
  - 之后：按 8 字节为一组的坐标点序列，每组为 `!ff`（4 字节 float 经度 + 4 字节 float 纬度，均大端）
- CarrotMan 将点集合写入 `self.navi_points`，并通过 `navRoute` 发布，同时可派生 `navInstructionCarrot`。

### 7710 ZMQ 命令（双向）
- 模式：REP（服务端），请求包含 JSON。
- 支持命令：
  - `echo_cmd`: 执行 shell 并回传 `exitStatus/stdout/stderr`
  - `tmux_send`: 触发抓取并通过 FTP 发送调试文件
- 额外：在 onroad 一段时间后会自动尝试发送 tmux（非命令调用路径）。

### 12345 UDP KISA/Waze 风格数据（App→comma3）
- 文本格式：`key:value/key:value/...`
- 关键键：
  - `kisawazeroadspdlimit` → 更新 `nRoadLimitSpeed`（自动单位换算）
  - `kisawazereportid` + `kisawazealertdist` → 生成 xSpd 提示（police=100，camera=101）
  - `kisawazeroadname` → 道路名

## 关键算法与实现细节（校正）

### GPS 融合与航向（修正原文优先级表述）
实际逻辑要点：
- 外部源包括“手机/导航”两类，CarrotServ 通过时间戳判断最近 3 秒是否更新。
- 如果最近 3 秒内收到导航（App）更新：
  - 使用外部提供的航向参与“航向偏移”的慢速修正（防抖 `diff_angle_count` > 5 才更新偏移）。
  - 位置采用“最近外部位置 + 速度-航向的运动学外推”（dead-reckoning），并考虑外部延迟补偿。
- 如果外部信号超时而内部 LLK 有效：
  - 使用 LLK（`liveLocationKalman`）的地理位置作为基准。
- 如果手机源最近更新：将偏移清零（认为手机航向作为直接参考）。

与原文不同之处：
- 不是“手机>导航>内部”的绝对优先级，而是“有外部→外部修正与外推；外部超时→内部兜底”。
- 航向采用“外部测量 + 平滑偏移”的组合；外部未更新时使用 LLK 航向。

### 弯道速度（vturn，修正“符号与阈值”）
- CarrotMan 计算 vturn：基于模型 `orientationRate.z` 与 `velocity.x`，结合参数：
  - `AutoCurveSpeedFactor`（对 orientationRate 的放大因子）
  - `AutoCurveSpeedAggressiveness`（对目标横向加速度 1.9 m/s² 的缩放）
- 速度结果：单位 km/h，区间 [5, 250]，并且“带方向符号”（左/右弯以符号区分）。在融合阶段 `carrot_serv` 会取 `max(abs(vturn), LowerLimit)`。
- 原文未说明“方向符号”与“下限限幅”，已在此补充。

示例实现引用：
```372:412:refer/carrot/carrot_man.py
  def vturn_speed(self, CS, sm):
    TARGET_LAT_A = 1.9  # m/s^2
    ...
    turnSpeed = max(abs(adjusted_target_lat_a / max_curve)**0.5  * 3.6, 5)
    turnSpeed = min(turnSpeed, 250)
    return turnSpeed * curv_direction
```

### 路网曲率速度（route）与加速度约束（新增说明）
- CarrotMan 将地图路线重采样为车辆坐标系的等距点，计算局部曲率映射到目标速度序列，再按“加速度极限（`AutoNaviSpeedDecelRate`）”进行反向时域约束，得到平滑的 `route_speed`。
- CarrotServ 根据 `TurnSpeedControlMode` 决定 route/vturn 是否参与最小值合成：
  - 1/2：加入 vturn
  - 2：在接近转弯（±500m）时加入 route
  - 3：全程加入 route

引用：
```834:906:refer/carrot/carrot_man.py
  # 反向加速度限幅，计算 out_speeds 并输出 out_speed
```

### 速度源合成（与实现一致，补充条件）
- 候选来源（取最小）：
  - `atc` 当前转弯目标速度
  - `atc2` 下一个转弯速度
  - `sdi_speed`（测速/区间/减速带）或 HDA（来自 OP 内置 speedLimit）
  - `road`（道路限速，含偏置）
  - 可选 `vturn`（模式 1/2）
  - 可选 `route`（模式 2/3；模式 2 仅在接近转弯时）
- 油门覆盖：在一定条件下将最小值抬升为“油门覆盖速度”。刹车、来源变化、极值等会清零覆盖。

引用：
```974:1070:refer/carrot/carrot_serv.py
    speed_n_sources = [ ... ]
    desired_speed, source = min(speed_n_sources, key=lambda x: x[0])
    ... # gas override 逻辑
```

### TBT 解析与 ATC（与实现一致，补充映射）
- `nTBTTurnType` 经映射得到 `xTurnInfo`（1=左转，2=右转，3/4=分叉，5=环岛，6=收费/特殊，7=掉头，8=到达）。
- `update_auto_turn` 根据 `xTurnInfo/距离/下一道路宽度/当前限速` 计算 ATC 类型与速度、提前触发与取消（含方向盘干预暂停）。

引用：
```468:531:refer/carrot/carrot_serv.py
  def _update_tbt(self):
    # turn_type_mapping -> xTurnInfo 映射
```

### SDI（测速/区间/减速带）（与实现一致，补充 HDA）
- 来自 App 的 SDI：`nSdiType/nSdiSpeedLimit/nSdiDist/...` → 生成 `xSpdLimit/xSpdDist/xSpdType`。
- 减速带（22）在非高速道路且模式≥2时启用固定减速带速度与距离。
- OP 内建 HDA：当 `carState.speedLimit/speedLimitDistance` 有效时，以内建限速同样进入合成（标记为 `hda`）。

引用：
```651:713:refer/carrot/carrot_serv.py
  def _update_sdi(self):
    ... # xSpdLimit/xSpdDist/xSpdType
```

## 状态与模式（校正）

- XState（与实现一致）：`lead(0), cruise(1), e2eCruise(2), e2eStop(3), e2ePrepare(4), e2eStopped(5)`
- TrafficState（广播可为 0/1/2/3）：off/red/green/left
- 驾驶模式（Planner 内部）：`Eco(1), Safe(2), Normal(3), High(4)`；但“自动判定”仅在 `MyDrivingModeAuto>0` 时由 `DrivingModeDetector` 在 Safe 与 Normal 之间切换（不会自动切 High/Eco）。原文关于“自动检测四种模式”的描述不准确，已更正。

引用：
```1:65:refer/carrot/carrot_functions.py
class XState(Enum): ...
class DrivingMode(Enum): ...
```

## 数据结构与字段（核对要点）

- UDP 广播字段：与“7705”小节一致。
- 7706 主数据：原文列出的 GPS/SDI/TBT/目的地/命令键名与代码一致；注意命令 `DETECT` 会写入半秒有效的交通灯触发窗口。
- 7709 路线：二进制 `!I + N*(!ff)`，经纬度均为 float（大端）。
- `carrotMan` 发布的内部消息包含：路限/来源/倒计时/调试文本/位置/路径序列化 `naviPaths` 等（原文已列，保持一致）。
- 额外通道：`navInstructionCarrot` 会在外部导航活跃时由 CarrotServ 填充，否则复用 OP 的 `navInstruction`。

## 与原文差异汇总（改动清单）

- 修正“GPS 融合优先级”为“外部（手机/导航）存在则外推 + 偏移修正；外部超时则用 LLK 兜底”。
- 补充 vturn“带方向符号”和“下限 5 km/h，上限 250 km/h”的实现细节。
- 明确 7705 发送的 `ip` 为设备本机 IP，且存在针对远端客户端的单播行为（不总是广播地址）。
- 补充 route 速度的“加速度限幅反向传播”细节与 `TurnSpeedControlMode` 对 vturn/route 参与的限制条件。
- 修正“驾驶模式自动判定”仅在 Safe/Normal 间切换的事实，非四模式全自动。
- SDI/HDA 合成与减速带条件的实现补充。
- `trafficState` 在广播中可为 3（left），与 Planner 内部枚举（0/1/2）不同，已标注。

## 开发建议（保持/补充）

- App 侧：
  - 建议基于“7705”监听，获取 `ip/port` 后采用“7706/7709/12345/7710”与设备通讯。
  - “7706”保持 1~5Hz，GPS 与 TBT/SDI 改变时尽快推送；命令 `DETECT` 控制触发窗口较短（约 0.5s）。
  - 发送路线时严格遵守大端 `float` 编码的二进制打包。
- 设备侧：
  - `TurnSpeedControlMode/AutoNavi...` 参数强相关，注意与 UI/Params 的一致性。
  - 关注 `leftSec/carrot_left_sec` 提示节奏与 `sdi_inform` 标记，避免过度打扰。

---

以上校正基于源码当前实现逐行核对。若后续代码更新，请以最新实现为准同步修订本说明。
```

---

## 第二部分：app分析.md（原文保留）

```markdown
## NetworkManager.kt 架构与代码导读（面向初学者）

> 目标：理解网络门面如何封装 `CarrotManNetworkClient` 给上层（Activity/Compose）调用，提供易用的发送接口与状态查询。

- **文件定位**：`app/src/main/java/com/example/carrotamap/NetworkManager.kt`
- **职责**：
  - 生命周期封装：初始化网络客户端、启动/停止、清理；
  - 提供易用 API：
    - 发现/连接：获取当前设备 IP、设备列表、连接状态/统计；
    - 发送：`sendCarrotManData(...)`、`sendDestinationToComma3(...)`、`sendDestinationUpdate(...)`、`sendTrafficLightUpdate(...)`、`sendDetectCommand(...)`；
    - 设置/模式：`sendSettingsToComma3()`、`sendModeChangeToComma3(mode)`；
  - 与 UI 交互：暴露 `getNetworkConnectionStatus()` 与 `getNetworkStatistics()` 用于状态卡片展示；
  - 回调桥接：把 `CarrotManNetworkClient` 的回调结果更新到本地状态或日志。

- **在 UI 中的使用**：
  - 速度表页右上限速圆环点击 → `sendSettingsToComma3()`；
  - 模式切换按钮 → `sendModeChangeToComma3(nextMode)`；
  - 导航确认 → `sendNavigationConfirmationToComma3(name, lat, lon)`（若提供）；
  - 顶部视频渲染 → 通过 `getCurrentDeviceIP()` 获取 IP；

- **设计建议**：
  - 限流与失败重试交给底层 NetworkClient；
  - 统一异常处理/日志风格，避免 UI 侧分散 try/catch；
  - 对外 API 命名保持语义一致（动宾短语），参数顺序固定（经度在前）。

---
## PermissionManager.kt 架构与代码导读（面向初学者）

> 目标：理解权限集中管理，确保定位/网络等关键能力在使用前被正确授权，并提供清理逻辑。

- **文件定位**：`app/src/main/java/com/example/carrotamap/PermissionManager.kt`
- **职责**：
  - 初始化权限请求流程：`initialize()`；
  - 统一申请与检查：`setupPermissionsAndLocation()`；
  - 清理：`cleanup()` 释放监听或回调；
  - 与 `LocationSensorManager` 协作，按需触发定位开关。

- **设计建议**：
  - 针对 Android 12+ 蓝牙权限/前台服务权限进行版本分支；
  - 非阻塞提示用户，并在 UI 提供“重试”入口；
  - 将权限结果同步记录到日志，便于问题定位。

---
## VehicleInfo.kt 与 VehicleInfoManager.kt 架构与代码导读（面向初学者）

> 目标：理解车辆信息的数据结构与持久化流程，为 UI 展示与网络上报提供车型上下文。

- **文件定位**：
  - `app/src/main/java/com/example/carrotamap/VehicleInfo.kt`（数据类）
  - `app/src/main/java/com/example/carrotamap/VehicleInfoManager.kt`（管理器）

- **VehicleInfo（数据类）**：
  - 字段：`manufacturer/model/fingerprint/...`（按工程实际定义）；
  - 用途：封装 UI 选择结果与持久化载体。

- **VehicleInfoManager（管理器）**：
  - 读取：`getVehicleInfo()`；保存：`saveVehicleInfo(info)`；清除：`clearVehicleInfo()`；
  - 存储介质：SharedPreferences/本地文件（按实现）；
  - 与 UI：对话框确认后保存；`DeviceInfoDisplay` 读取并显示车型；
  - 与网络：`LocationReportManager.performLocationReport(...)` 会读取厂商/车型/指纹随位置上报。

- **设计建议**：
  - 指纹匹配可考虑增加校验（与设备/车型白名单对照）；
  - 存储时加上版本号与校验，便于将来迁移；
  - 提供导入/导出，方便用户迁移设置。

---
## 软件整体架构与功能说明（总览）

### 架构概览

- **分层思想**：
  - 表层 UI（Compose）：`MainActivity` 与一组 Composable（速度表页/数据卡片页/对话框）。
  - 广播与数据映射层：`AmapBroadcastManager`（注册+分发） + `AmapBroadcastHandlers`（KEY_TYPE 解析与字段映射） + `AmapDataProcessor`（轻计算）。
  - 目的地与导航协调：`AmapDestinationManager` + `AmapNavigationManager`。
  - 网络层：`NetworkManager`（门面） + `CarrotManNetworkClient`（UDP 底座）。
  - 设备/权限/位置等支撑：`DeviceManager`、`PermissionManager`、`LocationSensorManager`、`VehicleInfo*`。
  - 常量/数据模型：`AppConstants`、`CarrotManDataModels`、表格分组与解析工具：`DataFieldManager`、`DataParsingManager`。

- **数据主线（单一事实来源）**：
  - `CarrotManFields` 为唯一共享状态容器，广播→映射→UI 展示→网络发送均围绕此结构。

- **关键流转**：
  1) 高德发广播 → `AmapBroadcastManager` 收到并分发；
  2) `AmapBroadcastHandlers` 将原始字段映射到 `CarrotManFields`；
  3) `AmapDataProcessor` 做倒计时与限速轻计算；
  4) UI 订阅 `CarrotManFields` 自动刷新；
  5) 用户操作（设置/模式/导航确认）通过 `NetworkManager` 调用网络层；
  6) 设备 7705 广播反哺状态，由 NetworkClient 回调到 UI。

### 功能说明

- **核心功能**：
  - 解读高德导航/限速/红绿灯/车道等数据，统一映射为 `CarrotManFields`；
  - 将目的地/限速/红绿灯状态等关键信息通过 UDP 下发到设备，实现辅助驾驶策略的数据支撑；
  - 提供“回家/公司、一键设置（傻瓜配置）、模式切换、导航确认”等交互；
  - 速度表页实时视频（WebRTC）与数据卡片页全量字段可视化；
  - 启动期位置上报与倒计时安全退出机制。

- **设计取舍**：
  - App 侧尽量“轻逻辑”，复杂融合策略下沉到设备端，降低前后端不一致风险；
  - 广播与协议兼容性通过多字段名兼容、原始字段保留、日志辅助实现；
  - 统一常量与数据模型，减少魔法数和字段拼写不一致问题。

- **可扩展点**：
  - 新 KEY_TYPE：Manager 的 `when(keyType)` + Handlers 的解析映射 + Processor 的可选轻计算；
  - 新 UI 展示：在 `DataFieldManager` 新增分组 + 在数据卡片页添加分节；
  - 新设备能力：在 `NetworkManager`/`CarrotManNetworkClient` 增加发送 API 与回调处理。

- **运行与维护建议**：
  - 打开必要的日志级别，排障先看 Manager 的全量 extras 与 Network 的连接统计；
  - 若高德字段变化，优先在 Handlers 做兼容映射，再更新解析文本与显示；
  - 定期回顾 `CarrotManFields` 的冗余字段，逐步收敛。

---
```
