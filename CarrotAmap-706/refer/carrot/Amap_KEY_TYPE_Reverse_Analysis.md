# 高德广播 KEY_TYPE 数据分析（基于 refer/carrot/amaplog.txt）

> 说明：本文完全根据 `refer/carrot/amaplog.txt` 中出现的实时日志进行整理与推断。写法沿用既有文档风格：先给出总体日志概览，再按 KEY_TYPE 分节说明字段、含义与初步结论；当日志信息不足时，明确标注“数据不足/待补充”。后续可用更完整的日志逐步完善。

---

## 日志概览
- 日志时间（节选）：2025-09-24 13:47:03.150 ~ 13:47:05.331
- 出现的 KEY_TYPE：`12205`、`10001`、`12110`
- 统一 Action：`AUTONAVI_STANDARD_BROADCAST_SEND`
- 重要现象：
  - `12110` 出现两次，字段完整且包含测速摄像头相关信息（限速、距离、起止坐标等）。
  - `12205` 仅见 `EXTRA_STATE=-1` 与少量键，疑似状态类广播。
  - `10001` 仅出现“处理引导信息广播”的提示，未见字段展开。

---

## KEY_TYPE: 12205（疑似状态/控制类广播）

- 相关日志：
  - 13:47:03.150  📦 广播详情: action=AUTONAVI_STANDARD_BROADCAST_SEND, KEY_TYPE=12205, EXTRA_STATE=-1
  - 13:47:03.150  🔍 开始处理高德地图广播数据 (KEY_TYPE: 12205)
  - 13:47:03.150  📋 Intent包含的所有数据:
    - `EXTRA_GEOLOCATION = 0`
    - `KEY_TYPE = 12205`

- 字段与可能含义（基于本次样本）
  - `EXTRA_GEOLOCATION`：疑似地理相关标志/开关，本次为 `0`（关闭/无效）。
  - `EXTRA_STATE`：出现于“广播详情”行，值为 `-1`，但未在“Intent包含的所有数据”清单中展开。

- 初步推断
  - 此 KEY_TYPE 在本次截取里未携带业务负载字段，更像“状态/控制类”信号。
  - 需要更多样本观察 `EXTRA_STATE` 的取值语义与状态机关系。

- 数据不足/待补充
  - 缺少结构化业务字段（如距离、类型、速度）。
  - 无法与 CarrotMan 现有数据模型建立稳定映射。

---

## KEY_TYPE: 10001（引导信息广播，占位，字段未展开）
- 相关日志：
  - 13:47:03.291  📦 广播详情: action=AUTONAVI_STANDARD_BROADCAST_SEND, KEY_TYPE=10001, EXTRA_STATE=-1
  - 13:47:03.291  🧭 处理引导信息广播 (KEY_TYPE: 10001)

- 字段与可能含义（基于本次样本）
  - 本次样本未打印出 `Intent` 的展开键值，无法确认结构。
  - 结合“引导信息”字样，可能涉及路口引导、下一步动作、转向图标、道路名等信息。

- 初步推断
  - 需要更长窗口的日志或调试输出，确认是否有诸如 `ICON`, `NEXT_ROAD`, `CURRENT_ROAD`, `REMAIN_DISTANCE/TIME` 等字段。

- 数据不足/待补充
  - 暂无字段清单。
  - 无法进行稳定映射与取值范围约束。

---

## KEY_TYPE: 12110（测速/电子眼相关，字段较完整）

- 相关日志（两次出现）：
  1) 13:47:03.315  EXTRA_STATE=2，字段均为 0 或空串
     - `START_DISTANCE = 0.0`
     - `END_DISTANCE = 0.0`
     - `INTERVAL_DISTANCE = 0.0`
     - `END_DISTANCE_TEXT = ""`
     - `LIMITED_SPEED = 0`
     - `START_DISTANCE_TEXT = ""`
     - `AVERAGE_SPEED = 0`
     - `CAMERA_TYPE = 0`
     - `INTERVAL_DISTANCE_TEXT = ""`
     - `EXTRA_STATE = 2`
     - `EXTRA_DLAT = 0.0`
     - `EXTRA_DLON = 0.0`
     - `EXTRA_SLAT = 0.0`
     - `EXTRA_SLON = 0.0`
     - `KEY_TYPE = 12110`

  2) 13:47:05.331  EXTRA_STATE=0，出现有效测速信息
     - `START_DISTANCE = 1630.0`（起始距离，米）
     - `END_DISTANCE = 0.0`
     - `INTERVAL_DISTANCE = 0.0`
     - `END_DISTANCE_TEXT = ""`
     - `LIMITED_SPEED = 120`（限速，km/h）
     - `START_DISTANCE_TEXT = "1.6km"`（带单位的人类可读文本）
     - `AVERAGE_SPEED = 0`（区间测速平均速度，当前为 0，可能表示未进入区间或尚未计算）
     - `CAMERA_TYPE = 8`（摄像头类型，8 需结合映射表确认语义）
     - `INTERVAL_DISTANCE_TEXT = ""`
     - `EXTRA_STATE = 0`（状态位）
     - `EXTRA_DLAT = 0.0`, `EXTRA_DLON = 0.0`（终点坐标？）
     - `EXTRA_SLAT = 31.704974444444446`, `EXTRA_SLON = 119.02960583333333`（起点坐标？）
     - `KEY_TYPE = 12110`

- 字段与可能含义
  - `START_DISTANCE`/`START_DISTANCE_TEXT`：距离测速点/区域起点的距离；`*_TEXT` 为带单位字符串。
  - `END_DISTANCE`/`END_DISTANCE_TEXT`：距离终点的距离及其文本形式（本次为空）。
  - `INTERVAL_DISTANCE`/`INTERVAL_DISTANCE_TEXT`：区间距离及文本（本次为 0）。
  - `LIMITED_SPEED`：当前路段限速（单位 km/h）。
  - `AVERAGE_SPEED`：区间测速平均速度（单位 km/h，需确认）。
  - `CAMERA_TYPE`：摄像头类型编号，需结合既有映射文档（如 `TurnType_Mapping_Analysis.md` 或外部表）判读。
  - `EXTRA_STATE`：状态位；样本显示 2（空载/初始化？）与 0（有效提示）。
  - `EXTRA_SLAT/EXTRA_SLON`：疑似起点坐标（纬度/经度，十进制度）。
  - `EXTRA_DLAT/EXTRA_DLON`：疑似终点坐标（本次为 0）。

- 初步结论
  - `12110` 极可能对应“测速/电子眼/区间测速提示”类广播。
  - 当 `EXTRA_STATE=0` 且 `LIMITED_SPEED>0` 时，包含有效的限速与距离提示，可用于 UI 或语音提醒。
  - `START_DISTANCE` 为米单位更利于精确控制提醒阈值；`*_TEXT` 适合直接显示。

- 数据不足/待补充
  - `CAMERA_TYPE` 枚举需补充对照表。
  - `AVERAGE_SPEED` 的计算触发条件与单位需更多样本验证。
  - `EXTRA_*` 坐标含义（起/终点）有较高可信度，但仍需二次验证。

---

## 与 CarrotMan 数据模型的潜在映射建议（初稿）

> 注：以下为“方向性建议”，仅在样本有限前提下提供，正式接入前需以更丰富日志交叉验证。

- `12110` → `SpeedCameraEvent`（建议新增/复用模型）
  - `limitedSpeedKmh`  ← `LIMITED_SPEED`
  - `distanceToStartMeters` ← `START_DISTANCE`
  - `distanceToStartText`   ← `START_DISTANCE_TEXT`
  - `cameraType`            ← `CAMERA_TYPE`
  - `averageSpeedKmh`       ← `AVERAGE_SPEED`
  - `startLatitude`/`startLongitude` ← `EXTRA_SLAT`/`EXTRA_SLON`
  - `endLatitude`/`endLongitude`     ← `EXTRA_DLAT`/`EXTRA_DLON`
  - `state`                 ← `EXTRA_STATE`

- `12205` → `AmapStateSignal`（占位，待定义）
  - `state`                 ← `EXTRA_STATE`
  - `geoLocationFlag`       ← `EXTRA_GEOLOCATION`

- `10001` → `GuidanceInfo`（占位，待定义）
  - 后续根据更完整日志补齐字段（道路名、动作、图标、剩余距离/时间等）。

---

## 后续工作与采集建议
- 增加 10001、12205 的 Intent 键值完整打印，便于字段反推。
- 在 `12110` 处长期采集更多样本，关注 `CAMERA_TYPE` 与 `AVERAGE_SPEED` 的取值变化。
- 若允许，可与高德文档或社区资料对照，完善枚举与单位定义。
- 将上述映射尝试接入到 `AmapBroadcastHandlers.kt` 的对应处理分支，分阶段验证。

### 10001 追加样本（07:28:06.873 时段）

- 相关日志要点（已展开的 Intent 键值）：
  - `NEW_ICON = 65`
  - `EXIT_NAME_INFO = null`
  - `arrivePOILatitude = 0.0`
  - `NEXT_ROAD_PROGRESS_PERCENT = -1`
  - `ROUND_ALL_NUM = 0`
  - `cameraPenalty = false`
  - `CAMERA_INDEX = -1`
  - `CAMERA_SPEED = -1`
  - `NEXT_NEXT_TURN_ICON = 0`
  - `SAPA_NUM = 2`
  - `ROUTE_REMAIN_TIME = 18740`
  - `NEXT_SAPA_DIST = 47961`
  - `NEXT_SAPA_NAME = 青草服务区`
  - `NEXT_SAPA_TYPE = 0`
  - `arrivePOIType = 000002`
  - `NEXT_ROAD_NAME = 桐城南枢纽`
  - `addIcon = ""`
  - `endPOIAddr = null`
  - `endPOIName = 太湖朗峯`
  - `endPOIType = 000002`
  - `CUR_SEG_NUM = 10`
  - `newCamera = false`
  - `CUR_ROAD_NAME = G4221沪武高速`
  - `LIMITED_SPEED = 120`
  - `ETA_TEXT = 预计13:40到达`
  - `ROUTE_ALL_TIME = 21359`
  - `ROUTE_ALL_DIS = 566781`
  - `viaPOItime = 0`
  - `CUR_SPEED = 110`
  - `NEXT_SEG_REMAIN_TIME = 0`
  - `CAR_LATITUDE = 0.0`
  - `ROUTE_REMAIN_DIS = 504514`
  - `cameraID = 33500416`
  - `CAMERA_DIST = -1`
  - `CAMERA_TYPE = -1`
  - `ICON = 65`
  - `TYPE = 0`
  - `NEXT_SEG_REMAIN_DIS = 0`
  - `EXIT_DIRECTION_INFO = null`
  - `routeRemainTrafficLightNum = 6`
  - `viaPOIdistance = 0`
  - `NEXT_SAPA_DIST_AUTO = 47公里`
  - `ROUTE_REMAIN_TIME_AUTO = 5时12分`
  - `TRAFFIC_LIGHT_NUM = 0`
  - `CAR_DIRECTION = 62`
  - `SEG_REMAIN_TIME = 2327`
  - `endPOILongitude = 120.6313768029213`
  - `nextRoadNOAOrNot = false`
  - `SEG_REMAIN_DIS_AUTO = 62公里`
  - `ROUTE_REMAIN_DIS_AUTO = 504公里`
  - `SEG_REMAIN_DIS = 62132`
  - `KEY_TYPE = 10001`
  - `ROUNG_ABOUT_NUM = 0`（疑为 `ROUND_ABOUT_NUM` 拼写变体）
  - `CUR_POINT_NUM = 6`
  - `SEG_ASSISTANT_ACTION = 25`
  - `NEXT_SEG_REMAIN_DIS_AUTO = ""`
  - `SAPA_DIST_AUTO = 1.2公里`
  - `CAR_LONGITUDE = 0.0`
  - `arrivePOILongitude = 0.0`
  - `SAPA_DIST = 1277`
  - `SAPA_NAME = 温泉服务区`
  - `SAPA_TYPE = 0`
  - `nextNextAddIcon = ""`
  - `NEXT_NEXT_ROAD_NAME = null`
  - `ROAD_TYPE = 0`
  - `endPOILatitude = 31.201044206995313`

- 字段与可能含义（初步归类）
  - 【路线整体信息】
    - `ROUTE_ALL_DIS`（整条路线距离，米）；`ROUTE_ALL_TIME`（整条路线预计用时，秒）。
    - `ROUTE_REMAIN_DIS`（剩余距离，米）；`ROUTE_REMAIN_TIME`（剩余时间，秒）。
    - `ROUTE_REMAIN_DIS_AUTO`、`ROUTE_REMAIN_TIME_AUTO`（带单位/本地化的可读文本）。
    - `ETA_TEXT`（预计到达时间的文本，如“预计13:40到达”）。
  - 【当前路段与分段】
    - `CUR_ROAD_NAME`（当前道路名），`CUR_SEG_NUM`、`CUR_POINT_NUM`（当前段/点索引）。
    - `SEG_REMAIN_DIS`（本段剩余距离，米），`SEG_REMAIN_TIME`（本段剩余时间，秒）。
    - `SEG_REMAIN_DIS_AUTO`、`NEXT_SEG_REMAIN_DIS_AUTO`（本段/下一段剩余距离文本）。
    - `SEG_ASSISTANT_ACTION`（辅助动作/转向编码，需映射表）。
  - 【下一道路与多步引导】
    - `NEXT_ROAD_NAME`（下一道路/枢纽名），`NEXT_ROAD_PROGRESS_PERCENT`（进度百分比）。
    - `NEXT_NEXT_TURN_ICON`、`nextNextAddIcon`（下下步图标/附加图标）。
    - `ICON`、`NEW_ICON`（当前引导图标，整数编码；需对照表）。
  - 【摄像头与限速】
    - `LIMITED_SPEED`（限速 km/h），`CUR_SPEED`（当前车速 km/h）。
    - `CAMERA_TYPE`、`CAMERA_DIST`、`CAMERA_INDEX`、`cameraID`、`CAMERA_SPEED`、`cameraPenalty`（摄像头相关字段；-1 常表示无效/未知）。
  - 【服务区/设施（SAPA）】
    - `SAPA_NUM`（沿途服务区个数），`SAPA_NAME`、`SAPA_TYPE`、`SAPA_DIST`（米）。
    - `NEXT_SAPA_NAME`、`NEXT_SAPA_TYPE`、`NEXT_SAPA_DIST`（米），以及 `*_AUTO` 文本字段（如“47公里”、“1.2公里”）。
  - 【POI 与目的地】
    - `endPOIName`、`endPOIAddr`、`endPOIType`、`endPOILatitude`、`endPOILongitude`（目的地信息）。
    - `arrivePOIType`、`arrivePOILatitude`、`arrivePOILongitude`（到达信息/类型）。
  - 【交通信号与道路类型】
    - `routeRemainTrafficLightNum`、`TRAFFIC_LIGHT_NUM`（沿途或当前段红绿灯数量）。
    - `ROAD_TYPE`、`TYPE`（道路/引导类型编码，需映射）。
    - `ROUNG_ABOUT_NUM`/`ROUND_ALL_NUM`（环岛相关计数，存在拼写差异需统一）。
  - 【车辆姿态/坐标】
    - `CAR_SPEED`（本样本用 `CUR_SPEED` 表示），`CAR_DIRECTION`（方向编码）。
    - `CAR_LATITUDE`、`CAR_LONGITUDE`（车辆坐标，本样本为 0.0，可能未启用）。

- 初步结论
  - `10001` 很可能是“综合引导信息”广播，携带路线全局、分段、图标、服务区、限速与交通设施等多维度信息，适合用于车机 UI 的综合引导面板更新。
  - 存在一组“数值字段（米/秒）+ 文本字段（本地化字符串）”的双轨设计，便于既做逻辑判断又直接展示。
  - 摄像头相关字段在 `10001` 中也可能出现，但当前样本以无效占位为主；真正的摄像头细节更多出现在 `12110`。

- 与 CarrotMan 模型的映射建议（增补）
  - `GuidanceInfo`（建议字段）：
    - `routeAllDistanceMeters` ← `ROUTE_ALL_DIS`
    - `routeAllTimeSeconds` ← `ROUTE_ALL_TIME`
    - `routeRemainDistanceMeters` ← `ROUTE_REMAIN_DIS`
    - `routeRemainTimeSeconds` ← `ROUTE_REMAIN_TIME`
    - `routeRemainDistanceText` ← `ROUTE_REMAIN_DIS_AUTO`
    - `routeRemainTimeText` ← `ROUTE_REMAIN_TIME_AUTO`
    - `etaText` ← `ETA_TEXT`
    - `currentRoadName` ← `CUR_ROAD_NAME`
    - `currentSegmentIndex` ← `CUR_SEG_NUM`
    - `currentPointIndex` ← `CUR_POINT_NUM`
    - `segmentRemainDistanceMeters` ← `SEG_REMAIN_DIS`
    - `segmentRemainTimeSeconds` ← `SEG_REMAIN_TIME`
    - `segmentRemainDistanceText` ← `SEG_REMAIN_DIS_AUTO`
    - `nextSegmentRemainDistanceText` ← `NEXT_SEG_REMAIN_DIS_AUTO`
    - `assistantActionCode` ← `SEG_ASSISTANT_ACTION`
    - `nextRoadName` ← `NEXT_ROAD_NAME`
    - `nextRoadProgressPercent` ← `NEXT_ROAD_PROGRESS_PERCENT`
    - `iconCode` ← `ICON`（或 `NEW_ICON`，需统一优先级）
    - `nextNextTurnIconCode` ← `NEXT_NEXT_TURN_ICON`
    - `limitedSpeedKmh` ← `LIMITED_SPEED`
    - `currentSpeedKmh` ← `CUR_SPEED`
    - `trafficLightsOnRoute` ← `routeRemainTrafficLightNum`
    - `trafficLightsOnSegment` ← `TRAFFIC_LIGHT_NUM`
    - `roadTypeCode` ← `ROAD_TYPE`
    - `guidanceTypeCode` ← `TYPE`
    - `roundaboutCount` ← `ROUND_ALL_NUM`（或 `ROUNG_ABOUT_NUM`，需字段统一）
    - `sapaNextName` ← `NEXT_SAPA_NAME`
    - `sapaNextDistanceMeters` ← `NEXT_SAPA_DIST`
    - `sapaNextDistanceText` ← `NEXT_SAPA_DIST_AUTO`
    - `sapaName` ← `SAPA_NAME`
    - `sapaDistanceMeters` ← `SAPA_DIST`
    - `sapaDistanceText` ← `SAPA_DIST_AUTO`
    - `endPoiName` ← `endPOIName`
    - `endPoiType` ← `endPOIType`
    - `endPoiLatitude`/`endPoiLongitude` ← `endPOILatitude`/`endPOILongitude`

- 数据一致性与工程建议
  - 统一 `ROUND_ALL_NUM` 与 `ROUNG_ABOUT_NUM` 字段名（建议在接收层做别名映射）。
  - `ICON` 与 `NEW_ICON` 的优先级：建议优先使用 `NEW_ICON`，若缺失则回退 `ICON`，并建立图标枚举表。
  - 对 `*_AUTO` 文本字段与数值字段的绑定建立校验（例如数值→文本的换算是否一致）。
  - 将 `GuidanceInfo` 的解析与渲染分层：解析层负责单位与取值校验，UI 层直接消费文本或格式化数值。
