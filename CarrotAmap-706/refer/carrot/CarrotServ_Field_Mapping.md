# CarrotServ 映射关系与字段数据说明（基于 refer/carrot/carrot_serv.py 逆向）

> 本文基于 `refer/carrot/carrot_serv.py` 逐行核对的结果，整理了：
> - 导航/控制相关的“映射关系表”（转弯类型映射、ATC映射、SDI类型映射等）
> - 7706 输入 JSON 字段与 `carrotMan` 输出消息字段的“字段数据说明表”（含单位/范围/含义/来源）

---

## 一、映射关系表

### 1) 转弯类型映射（nTBTTurnType → navType/navModifier/xTurnInfo）

来源：`nav_type_mapping`（文件顶部常量）与 `_update_tbt()` 内部 `turn_type_mapping`

| nTBTTurnType | navType      | navModifier     | xTurnInfo | 说明 |
|---|---|---|---:|---|
| 12 | turn | left | 1 | 左转 |
| 16 | turn | sharp left | 1 | 急左转 |
| 13 | turn | right | 2 | 右转 |
| 19 | turn | sharp right | 2 | 急右转 |
| 1000 | turn | slight left | 1 | 轻微左转 |
| 1001 | turn | slight right | 2 | 轻微右转 |
| 1002 | fork | slight left | 3 | 左分叉 |
| 1003 | fork | slight right | 4 | 右分叉 |
| 1006 | off ramp | left | 3 | 左匝道 |
| 1007 | off ramp | right | 4 | 右匝道 |
| 102/105/112/115 | off ramp | slight left | 3 | 左匝道（变体） |
| 101/104/111/114 | off ramp | slight right | 4 | 右匝道（变体） |
| 7/44/17/75/76/118 | fork | left | 3 | 左分叉（变体） |
| 6/43/73/74/123/124/117 | fork | right | 4 | 右分叉（变体） |
| 131/132 | rotary | slight right | 5 | 环岛轻右 |
| 140/141 | rotary | slight left | 5 | 环岛轻左 |
| 133 | rotary | right | 5 | 环岛右转 |
| 134/135 | rotary | sharp right | 5 | 环岛急右 |
| 136/137/138 | rotary | sharp left | 5 | 环岛急左 |
| 139 | rotary | left | 5 | 环岛左转 |
| 142 | rotary | straight | 5 | 环岛直行 |
| 14 | turn | uturn | 7 | 掉头 |
| 201 | arrive | straight | 8 | 到达 |
| 51/52/53/54/55 | notification | straight | 0 | 通知类，不参与 ATC |
| 153/154/249 | "" | "" | 6 | TG（特殊直行） |

> 注：`_update_tbt()` 中将 `nTBTDist/nTBTDistNext` 映射到 `xDistToTurn/xDistToTurnNext`，并以“累计距离”描述下一转弯（Next=Next+当前）。

### 2) ATC（自动转弯控制）映射（xTurnInfo → atcType/速度/距离/触发）

来源：`update_auto_turn()`

- 关键参数：
  - `autoTurnControlSpeedTurn`（基础转弯速度，km/h）
  - `autoTurnControlTurnEnd`（控制结束时间，s）
  - `nRoadLimitSpeed`（道路限速，km/h）
  - `nTBTNextRoadWidth`（下一道路宽度，m）

- 规则表：

| xTurnInfo | atcType 初值 | 目标速度 | decel 距离 | prepare 起始距离 |
|---:|---|---:|---:|---:|
| 1（左转） | turn left | `autoTurnControlSpeedTurn` | `autoTurnControlTurnEnd * autoTurnControlSpeedTurn / 3.6` | `np.interp(nRoadLimitSpeed,[30,50,100],[160,200,350])` |
| 2（右转） | turn right | `autoTurnControlSpeedTurn` | `autoTurnControlTurnEnd * autoTurnControlSpeedTurn / 3.6` | `np.interp(nRoadLimitSpeed,[30,50,100],[160,200,350])` |
| 3（左分叉） | fork left | `nRoadLimitSpeed` | `autoTurnControlTurnEnd * nRoadLimitSpeed / 3.6` | `np.interp(nRoadLimitSpeed,[30,50,100],[160,200,350])` |
| 4（右分叉） | fork right | `nRoadLimitSpeed` | `autoTurnControlTurnEnd * nRoadLimitSpeed / 3.6` | `np.interp(nRoadLimitSpeed,[30,50,100],[160,200,350])` |
| 5（环岛） | straight | `autoTurnControlSpeedTurn` | `autoTurnControlTurnEnd * autoTurnControlSpeedTurn / 3.6` | `np.interp(nTBTNextRoadWidth,[5,10],[43,60])` |
| 6（TG） | straight | `nRoadLimitSpeed` | `autoTurnControlTurnEnd * nRoadLimitSpeed / 3.6` | `np.interp(nRoadLimitSpeed,[30,50,100],[160,200,350])` |
| 7（掉头） | straight | 1 | 5 | 1000 |
| 8（到达） | straight | 1 | 5 | 1000 |

- 触发与修饰：
  - 若 `x_dist_to_turn > atc_start_dist` → `atcType += " prepare"`
  - 若 `atcType` 为 turn 且距离仍较远 → 置为 `"atc left"/"atc right"`
  - 方向盘干预可置 `atcType += " canceled"` 并暂停
  - `AutoTurnControl` 不同取值可屏蔽“速度控制”或“转向提示”

### 3) SDI（测速/区间/减速带）类型说明（部分）

来源：`_get_sdi_descr()` 与 `_update_sdi()`

- 常见类型（编号→含义）：0 信号超速、1 固定测速、2 区间开始、3 区间结束、4 区间中、7 移动测速、22 减速带、…（其余见代码表）
- 参与规则（简化要点）：
  - 若 `nSdiType∈[0,1,2,3,4,7,8,75,76]` 且有 `nSdiSpeedLimit` 且 `autoNaviSpeedCtrlMode>0`：
    - `xSpdLimit = nSdiSpeedLimit * autoNaviSpeedSafetyFactor`
    - `xSpdDist = nSdiDist`（若 `nSdiBlockType ∈ [2,3]` 则改用 `nSdiBlockDist` 且 `xSpdType=4`）
    - 移动测速（7）在 `autoNaviSpeedCtrlMode < 3` 时会被置零忽略
  - 若为减速带（22），非高速（`roadcate > 1`）且 `autoNaviSpeedCtrlMode >= 2`：
    - `xSpdLimit = autoNaviSpeedBumpSpeed`，`xSpdDist` 取 `nSdiPlusDist`（若 plus==22）否则取 `nSdiDist`

---

## 二、字段数据说明表

### A. 7706 UDP JSON 输入（App → comma3）

| 字段 | 类型 | 单位 | 取值/范围 | 含义 | 被谁消费 |
|---|---|---|---|---|---|
| carrotIndex | int | - | ≥0 | 数据包序号 | `CarrotServ.update`（节流/命令对齐） |
| epochTime | int | s | Unix | 时间戳（可用于校时） | `set_time`（仅非PC） |
| timezone | string | - | 例如 `Asia/Seoul` | 时区 | `set_time` |
| carrotCmd | string | - | `DETECT`/… | 命令类型 | `_update_cmd`/`_handle_detect_command` |
| carrotArg | string | - | `Red Light,x,y,conf` | 命令参数 | `_update_cmd`/`_handle_detect_command`（触发窗口≈0.5s） |
| goalPosX | float | deg | 经度 | 目标经度 | `CarrotServ.update` 设置 `goalPos*`；`CarrotMan.send_routes` 写 `Params("NavDestination")` |
| goalPosY | float | deg | 纬度 | 目标纬度 | `CarrotServ.update` 设置 `goalPos*`；`CarrotMan.send_routes` 写 `Params("NavDestination")` |
| szGoalName | string | - | 任意 | 目标名称 | `CarrotServ.update` 维护；用于 `NavDestination.place_name` |
| nRoadLimitSpeed | int | km/h | 1~200+ | 道路限速候选 | `CarrotServ.update`（含计数防抖/特殊映射） |
| nSdiType | int | - | 见表 | SDI 类型 | `_update_sdi` |
| nSdiSpeedLimit | int | km/h | ≥0 | SDI 限速 | `_update_sdi` |
| nSdiSection | int | - | - | 区间标识 | `_update_sdi` |
| nSdiDist | int | m | ≥0 | 到 SDI 距离 | `_update_sdi` |
| nSdiBlockType | int | - | 0/1/2/3 | SDI 阻断类型 | `_update_sdi` |
| nSdiBlockSpeed | int | km/h | ≥0 | 阻断速度 | `_update_sdi` |
| nSdiBlockDist | int | m | ≥0 | 阻断距离 | `_update_sdi` |
| nSdiPlusType | int | - | - | PLUS 类型（如 22） | `_update_sdi` |
| nSdiPlusSpeedLimit | int | km/h | ≥0 | PLUS 限速 | `_update_sdi` |
| nSdiPlusDist | int | m | ≥0 | PLUS 距离 | `_update_sdi` |
| nSdiPlusBlockType | int | - | - | PLUS 阻断类型 | `_update_sdi` |
| nSdiPlusBlockSpeed | int | km/h | ≥0 | PLUS 阻断速度 | `_update_sdi` |
| nSdiPlusBlockDist | int | m | ≥0 | PLUS 阻断距离 | `_update_sdi` |
| roadcate | int | - | 0..8 | 道路类别（0/1 高速） | `_update_sdi` 的减速带启用条件之一 |
| nTBTDist | int | m | ≥0 | 到本次转弯距离 | `_update_tbt`（映射到 `xDistToTurn`） |
| nTBTTurnType | int | - | 见映射表 | 转弯类型 | `_update_tbt`（映射到 `xTurnInfo`） |
| szTBTMainText | string | - | - | 导航主文本 | `navInstructionCarrot` 复制输出 |
| szNearDirName | string | - | - | 近处名 | `navInstructionCarrot` 复制输出 |
| szFarDirName | string | - | - | 远处名 | `navInstructionCarrot` 复制输出 |
| nTBTNextRoadWidth | int | m | ≥0 | 下一道路宽度 | `update_auto_turn` 计算起始触发距离 |
| nTBTDistNext | int | m | ≥0 | 下次转弯距离 | `_update_tbt`（累计到 `xDistToTurnNext`） |
| nTBTTurnTypeNext | int | - | 见映射表 | 下次转弯类型 | `_update_tbt`（映射到 `xTurnInfoNext`） |
| nGoPosDist | int | m | ≥0 | 到目的地距离 | `navInstructionCarrot` 填充 |
| nGoPosTime | int | s | ≥0 | 到目的地时间 | `navInstructionCarrot` 填充 |
| szPosRoadName | string | - | - | 道路名 | 广播文本/`debugText` 组合显示 |
| vpPosPointLat | float | deg | - | 导航纬度 | `_update_gps` 外推输入 |
| vpPosPointLon | float | deg | - | 导航经度 | `_update_gps` 外推输入 |
| nPosAngle | float | deg | 0..360 | 航向角 | `_update_gps`（含 `bearing_offset` 修正） |
| nPosSpeed | float | m/s | ≥0 | 速度 | 显示/调试用途 |
| latitude | float | deg | - | 手机纬度 | 外部 GPS 兜底（>3s 未收导航） |
| longitude | float | deg | - | 手机经度 | 外部 GPS 兜底（>3s 未收导航） |
| heading | float | deg | 0..360 | 手机航向 | 外部 GPS 兜底（>3s 未收导航） |
| accuracy | float | m | ≥0 | 手机精度 | 外部 GPS 兜底（>3s 未收导航） |
| gps_speed | float | m/s | ≥0 | 手机速度 | 外部 GPS 兜底（>3s 未收导航） |

### B. `carrotMan` 输出（Pub：carrotMan/navInstructionCarrot）

- `carrotMan` 消息核心字段（来源：`update_navi()` 打包段）：

| 字段 | 类型 | 单位 | 含义 | 来源/计算 |
|---|---|---|---|---|
| activeCarrot | int | - | Carrot 激活级别（0..6） | 由 `active_count/active_sdi_count/active_kisa_count` 决定（>0 时分别置 1/2） |
| nRoadLimitSpeed | int | km/h | 当前路限 | `nRoadLimitSpeed`（必要时加 `AutoRoadSpeedLimitOffset`） |
| remote | string | - | 最近远端 IP | 7706 线程更新 `remote_ip` |
| xSpdType | int | - | SDI 类型 | `_update_sdi`，或 `update_kisa`/`update_nav_instruction` 影响 |
| xSpdLimit | int | km/h | SDI 目标限速 | `_update_sdi` 或 `update_kisa` 推导 |
| xSpdDist | int | m | 到 SDI 距离 | 在 `update_navi` 中按 `delta_dist` 逐帧递减 |
| xSpdCountDown | int | s | SDI 倒计时 | 由 `xSpdDist` 与 `v_ego` 粗估 |
| xTurnInfo | int | - | 转弯信息编码 | `_update_tbt` |
| xDistToTurn | int | m | 到转弯距离 | 在 `update_navi` 中按 `delta_dist` 逐帧递减 |
| xTurnCountDown | int | s | 转弯倒计时 | 由 `xDistToTurn` 与 `v_ego` 粗估 |
| atcType | string | - | ATC 类型 | `update_auto_turn`（含 `prepare/canceled/atc left/right`） |
| vTurnSpeed | int | km/h | vturn 速度 | 来自 `carrot_man.vturn_speed` |
| szPosRoadName | string | - | 道路名+调试文本 | 拼接 `szPosRoadName + debugText`（debugText 含 `route=`） |
| szTBTMainText | string | - | 导航主文本 | 从外部导航/`navInstruction` 同步 |
| desiredSpeed | int | km/h | 合成期望速度 | `min(atc, atc2, sdi/hda, road, vturn?, route?)` + gas 覆盖 |
| desiredSource | string | - | 速度来源 | `atc/atc2/cam/section/bump/hda/road/route/vturn/gas` |
| carrotCmdIndex/carrotCmd/carrotArg | - | - | 命令追踪 | `_update_cmd` |
| trafficState | int | - | 交通灯状态 | 0/1/2/3 |
| xPosSpeed | float | km/h | 车速 | `v_ego * 3.6` |
| xPosAngle | float | deg | 航向角 | `_update_gps` 计算结果 |
| xPosLat/xPosLon | float | deg | 位置 | `_update_gps` 外推结果 |
| nGoPosDist/nGoPosTime | int | m/s | 目的地剩余 | 从 `navInstruction` 同步 |
| szSdiDescr | string | - | SDI 文本 | `_get_sdi_descr` 返回（韩文） |
| naviPaths | string | - | 路径串 | `"x,y,dist;..."` 串行化（与 `coords/distances` 对齐） |
| leftSec | int | s | UI 倒计时 | `carrot_left_sec`（基于 `left_spd_sec/left_tbt_sec` 规则） |

- `navInstructionCarrot`：当 `active_carrot>1` 且 `active_kisa_count<=0` 时，用外部导航填充，否则复用原 `navInstruction`。

---

## 三、逆向分析要点补充（carrot_serv.py）

- GPS 融合与航向：
  - 3 秒窗口内若有外部导航：以外部航向参与 `bearing_offset` 的缓慢修正（`diff_angle_count>5`），位置用“最近外部点+速度/航向外推”。
  - 外部超时而 LLK 有效：切回 LLK；若手机数据最近更新且 `active_carrot>0`，航向可用 `nPosAnglePhone`。
  - 计算航向为 `(bearing + bearing_offset) % 360`，并按延迟加权 `dt + gpsDelayTimeAdjust` 外推经纬度。

- 速度源合成与 gas 覆盖：
  - 候选源最小值：`atc/atc2/sdi或hda/road/(vturn?)/(route?)`，由 `TurnSpeedControlMode` 控制后两者是否参与。
  - 当来源变化、刹车、速度>150、车速过低或路限变化等情况，gas 覆盖被清零；否则按松开→按压沿用最大值。

- 倒计时与 UI 展示：
  - `left_spd_sec/left_tbt_sec` 由距离与 `v_ego` 粗略换算；`leftSec` 通过节拍门控（最大 11 秒）映射到 UI。
  - `debugText` 用于在道路名后拼接 `route=...` 等调试信息。

- 激活状态（activeCarrot）：
  - `active_kisa_count>0` → 2；否则 `active_count>0` → 1（若同时 `active_sdi_count>0` 则仍记为 2）；均为 0 → 0。

- SDI/HDA：
  - HDA（内建限速）在 `CS.speedLimit>0 && CS.speedLimitDistance>0` 时参与计算，源标记为 `hda`；
  - SDI 22（减速带）仅在 `roadcate>1` 且模式≥2 时启用，速度与时间分别由 `autoNaviSpeedBumpSpeed/autoNaviSpeedBumpTime` 决定。

- TBT/ATC：
  - `xTurnInfo` 通过 `nTBTTurnType` 映射；`xDistToTurnNext = nTBTDistNext + nTBTDist`；
  - ATC 可进入 `prepare/atc left|right/canceled` 状态；当 `AutoTurnControl` 未启用相应功能时屏蔽输出。

---

## 四、高德地图 60073 红绿灯广播字段分析

> 基于实际日志数据分析，补充高德地图红绿灯信息广播的完整字段说明

### 4.1 60073 广播字段详细分析表

| 字段名 | 数据类型 | 单位 | 取值/范围 | 含义说明 | 实际用途 | 映射到CarrotMan |
|---|---|---|---|---|---|---|
| `KEY_TYPE` | Integer | - | 60073 | 广播类型标识符 | 区分广播类型 | - |
| `trafficLightStatus` | Integer | - | -1,0,1,2,3,4 | 交通灯当前状态 | 核心状态信息 | `traffic_state` |
| `redLightCountDownSeconds` | Integer | s | ≥0 | 当前状态倒计时秒数 | 显示剩余时间 | `left_sec` |
| `dir` | Integer | - | 1,2,3,4,5 | 交通灯方向信息 | 标识控制方向 | `traffic_light_direction` |
| `greenLightLastSecond` | Integer | - | 0,3 | 绿灯状态标识符 | 0=非绿灯状态, 3=绿灯状态 | - |
| `waitRound` | Integer | - | ≥0 | 等待轮次信息 | 复杂路口等待周期 | - |

### 4.2 交通灯状态值映射表

| trafficLightStatus | 高德状态 | CarrotMan状态 | 说明 | 倒计时处理 |
|---|---|---|---|---|
| -1 | 黄灯 | -1 (黄灯) | 黄灯状态，通常很短 | 通常为0 |
| 0 | 未知 | 0 (关闭) | 状态未知 | 使用前值或0 |
| 1 | 红灯 | 1 (红灯) | 红灯状态，所有方向都是红灯 | `redLightCountDownSeconds` |
| 2 | 绿灯 | 2/3 (绿灯/左转) | 绿灯状态，根据方向区分 | `redLightCountDownSeconds` |
| 3 | 红灯 | 1 (红灯) | 红灯状态（变体） | `redLightCountDownSeconds` |
| 4 | 绿灯 | 2 (绿灯) | 绿灯状态（变体） | `redLightCountDownSeconds` |

**方向与状态的组合映射：**
- `trafficLightStatus = -1, dir = 0` → 直行黄灯 → CarrotMan状态-1
- `trafficLightStatus = 1, dir = 1` → 左转红灯 → CarrotMan状态1
- `trafficLightStatus = 2, dir = 1` → 左转绿灯 → CarrotMan状态3
- `trafficLightStatus = 1, dir = 3` → 左转掉头红灯 → CarrotMan状态1
- `trafficLightStatus = 2, dir = 3` → 左转掉头绿灯 → CarrotMan状态3
- `trafficLightStatus = 1, dir = 4` → 直行红灯 → CarrotMan状态1
- `trafficLightStatus = 2, dir = 4` → 直行绿灯 → CarrotMan状态2

### 4.3 交通灯方向值映射表

| dir | 方向描述 | 说明 | 应用场景 |
|---|---|---|---|
| 0 | 直行黄灯 | 黄灯状态时表示直行黄灯 | 黄灯状态专用 |
| 1 | 左转 | 左转方向交通灯 | 左转方向 |
| 2 | 右转 | 右转方向交通灯 | 右转方向 |
| 3 | 左转掉头 | 左转掉头方向交通灯 | 掉头方向 |
| 4 | 直行 | 直行方向交通灯 | 直行方向 |
| 5 | 右转掉头 | 右转掉头方向交通灯 | 掉头方向 |

**重要说明**：
1. `dir` 字段表示的是**交通灯控制的方向**，而不是车辆需要行驶的方向
2. `dir = 0` 是特殊情况，仅在黄灯状态（`trafficLightStatus = -1`）时使用，表示直行黄灯
3. 这与之前的理解不同，需要特别注意方向字段的正确含义

### 4.4 关键字段处理逻辑

#### 4.4.1 倒计时字段处理
```kotlin
// 关键理解：redLightCountDownSeconds 字段名有误导性
// 实际存储的是当前状态的倒计时，而不是专门的红灯倒计时
val trafficLightCountDownSeconds = when (trafficLightStatus) {
    -1 -> 0                      // 黄灯状态：通常很短，没有倒计时
    1 -> redLightCountDown       // 红灯状态：redLightCountDownSeconds 是红灯倒计时
    2 -> redLightCountDown       // 绿灯状态：redLightCountDownSeconds 实际是绿灯倒计时！
    3 -> redLightCountDown       // 红灯变体：红灯倒计时
    4 -> redLightCountDown       // 绿灯变体：绿灯倒计时
    else -> redLightCountDown    // 其他状态：使用该字段
}
```

#### 4.4.2 状态映射逻辑（重要修正）
```kotlin
// 基于实际UI观察数据的正确映射逻辑
private fun mapTrafficLightStatus(amapStatus: Int, direction: Int = 0): Int {
    // trafficLightStatus: 1=红灯, 2=绿灯, -1=黄灯
    // dir: 表示交通灯控制的方向（0=直行黄灯, 1=左转, 2=右转, 3=左转掉头, 4=直行, 5=右转掉头）
    return when (amapStatus) {
        -1 -> when (direction) {
            0 -> -1     // 直行黄灯（dir=0表示直行黄灯）
            else -> -1  // 其他方向黄灯
        }
        0 -> 0          // 未知/无信号 -> off
        1 -> when (direction) {
            1 -> 1      // 左转红灯 -> red
            2 -> 1      // 右转红灯 -> red
            3 -> 1      // 左转掉头红灯 -> red
            4 -> 1      // 直行红灯 -> red
            5 -> 1      // 右转掉头红灯 -> red
            else -> 1   // 其他方向红灯 -> red
        }
        2 -> when (direction) {
            1 -> 3      // 左转绿灯 -> left
            2 -> 2      // 右转绿灯 -> green
            3 -> 3      // 左转掉头绿灯 -> left
            4 -> 2      // 直行绿灯 -> green
            5 -> 2      // 右转掉头绿灯 -> green
            else -> 2   // 其他方向绿灯 -> green
        }
        3 -> 1          // 红灯变体 -> red
        4 -> 2          // 绿灯变体 -> green
        else -> 0
    }
}
```

#### 4.4.3 状态推断逻辑
```kotlin
// 特殊处理：当接收到状态0且倒计时0时，检查是否应该推断为绿灯状态
if (carrotTrafficState == 0 && leftSec <= 0) {
    // 如果之前是红灯状态且倒计时接近结束，可能应该转换为绿灯
    if (previousTrafficState == 1 && previousLeftSec <= 3) {
        carrotTrafficState = 2  // 设置为绿灯
        leftSec = 30           // 设置默认绿灯倒计时
    }
}
```

### 4.5 实际日志数据分析示例

基于实际捕获的日志数据：

```
时间点1: redLightCountDownSeconds = 13, trafficLightStatus = 1, dir = 4
时间点2: redLightCountDownSeconds = 12, trafficLightStatus = 1, dir = 4  
时间点3: redLightCountDownSeconds = 11, trafficLightStatus = 1, dir = 4
```

**分析结果：**
- 倒计时每秒递减1秒，符合预期
- 状态稳定为红灯（trafficLightStatus = 1）
- 方向固定为左转掉头（dir = 4），表示车辆需要左转掉头
- 其他字段保持稳定（waitRound = 0, greenLightLastSecond = 0）

**重要发现（基于实际UI观察）：**
- `trafficLightStatus = -1, dir = 0` → 直行黄灯（黄灯状态）
- `trafficLightStatus = 1, dir = 1` → 左转红灯（左转方向红灯）
- `trafficLightStatus = 2, dir = 1` → 左转绿灯（左转方向绿灯）
- `trafficLightStatus = 1, dir = 3` → 左转掉头红灯（左转掉头方向红灯）
- `trafficLightStatus = 2, dir = 3` → 左转掉头绿灯（左转掉头方向绿灯）
- `trafficLightStatus = 1, dir = 4` → 直行红灯（直行方向红灯）
- `trafficLightStatus = 2, dir = 4` → 直行绿灯（直行方向绿灯）

这证明了：
1. `trafficLightStatus` 表示交通灯的颜色状态（-1=黄灯，1=红灯，2=绿灯）
2. `dir` 字段表示的是**交通灯控制的方向**，而不是车辆需要行驶的方向
3. `dir = 0` 是特殊情况，仅在黄灯状态时使用，表示直行黄灯
4. 只有左转（dir=1）和左转掉头（dir=3）的绿灯状态才会映射为CarrotMan的左转绿灯状态（3）

### 4.6 greenLightLastSecond 字段特殊分析

**重要发现**：`greenLightLastSecond` 字段不是倒计时，而是绿灯状态标识符！

**观察到的行为模式**：
- 直行绿灯时：`greenLightLastSecond = 3`
- 直行红灯时：`greenLightLastSecond = 0`
- 无论倒计时多少秒，只有0和3两种值

**可能含义**：
1. **绿灯状态标识符**：
   - `0` = 当前不是绿灯状态（红灯或黄灯）
   - `3` = 当前是绿灯状态
2. **绿灯优先级标识**：
   - `0` = 低优先级绿灯（或非绿灯）
   - `3` = 高优先级绿灯
3. **绿灯剩余周期数**：
   - `0` = 绿灯周期结束
   - `3` = 绿灯周期剩余3个时间单位

**实际应用价值**：
- 可以用于快速判断当前是否为绿灯状态
- 比 `trafficLightStatus` 更直接地表示绿灯状态
- 可能用于绿灯优先级的判断

### 4.6 与CarrotMan字段的完整映射关系

| 高德60073字段 | CarrotMan字段 | 映射逻辑 | 备注 |
|---|---|---|---|
| `trafficLightStatus` | `traffic_state` | 状态值转换映射 | 核心状态信息 |
| `redLightCountDownSeconds` | `left_sec` | 直接映射 | 倒计时秒数 |
| `dir` | `traffic_light_direction` | 直接映射 | 方向信息 |
| `KEY_TYPE` | - | 不映射 | 广播标识 |
| `greenLightLastSecond` | - | 备用字段 | 暂未使用 |
| `waitRound` | - | 备用字段 | 暂未使用 |

### 4.7 广播频率与数据更新

- **广播频率**：约每秒1次
- **数据更新**：倒计时字段实时递减
- **状态变化**：状态字段在红绿灯切换时更新
- **方向稳定**：方向字段在单次等待期间保持稳定

### 4.8 应用场景与价值

1. **导航提示**：显示"前方红灯，还需等待X秒"
2. **路线规划**：根据等待时间选择最优路线
3. **驾驶辅助**：提醒驾驶员准备起步或继续等待
4. **交通分析**：统计不同方向的等待时间
5. **自动驾驶**：为车辆控制提供交通灯状态信息

---

以上内容如与源代码更新有差异，以最新实现为准。