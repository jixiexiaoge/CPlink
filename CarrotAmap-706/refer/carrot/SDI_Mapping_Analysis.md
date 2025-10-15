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

| 字段名称 | Android字段 | Python字段 | 数据类型 | 描述 | 高德广播字段 | KEY_TYPE |
|---------|-------------|------------|----------|------|-------------|----------|
| **SDI基础信息** |
| SDI类型 | `nSdiType` | `nSdiType` | Int | 电子眼/摄像头类型 | `CAMERA_TYPE` | 13005, 100001 |
| SDI限速 | `nSdiSpeedLimit` | `nSdiSpeedLimit` | Int | 测速限速值(km/h) | `SPEED_LIMIT`, `CAMERA_SPEED` | 13005, 100001 |
| SDI距离 | `nSdiDist` | `nSdiDist` | Int | 到测速点距离(m) | `DISTANCE`, `CAMERA_DIST` | 13005, 100001 |
| SDI区间ID | `nSdiSection` | `nSdiSection` | Int | 区间测速标识 | 高德内部字段 | 13005 |
| **SDI区间测速** |
| 区间类型 | `nSdiBlockType` | `nSdiBlockType` | Int | 区间状态(1=开始,2=中,3=结束) | 高德内部字段 | 13005 |
| 区间限速 | `nSdiBlockSpeed` | `nSdiBlockSpeed` | Int | 区间测速限速(km/h) | 高德内部字段 | 13005 |
| 区间距离 | `nSdiBlockDist` | `nSdiBlockDist` | Int | 区间测速距离(m) | 高德内部字段 | 13005 |
| **SDI Plus扩展** |
| Plus类型 | `nSdiPlusType` | `nSdiPlusType` | Int | Plus类型(22=减速带) | `SDI_TYPE` | 10007 |
| Plus限速 | `nSdiPlusSpeedLimit` | `nSdiPlusSpeedLimit` | Int | Plus限速值(km/h) | `SPEED_LIMIT` | 10007 |
| Plus距离 | `nSdiPlusDist` | `nSdiPlusDist` | Int | Plus距离(m) | `SDI_DIST` | 10007 |
| **SDI Plus区间** |
| Plus区间类型 | `nSdiPlusBlockType` | `nSdiPlusBlockType` | Int | Plus区间状态 | JSON字段 | 10007 |
| Plus区间限速 | `nSdiPlusBlockSpeed` | `nSdiPlusBlockSpeed` | Int | Plus区间限速(km/h) | JSON字段 | 10007 |
| Plus区间距离 | `nSdiPlusBlockDist` | `nSdiPlusBlockDist` | Int | Plus区间距离(m) | JSON字段 | 10007 |

## SDI类型映射表

| SDI类型 | 高德描述 | Python描述 | 中文描述 | 处理逻辑 | 启用条件 |
|---------|----------|------------|----------|----------|----------|
| **基础摄像头类型（处理）** |
| 0 | 测速摄像头 | 신호과속 | 信号超速 | ✅ 基础测速 | `autoNaviSpeedCtrlMode > 0` |
| 1 | 监控摄像头 | 과속 (고정식) | 超速（固定式） | ✅ 基础测速 | `autoNaviSpeedCtrlMode > 0` |
| 2 | 闯红灯拍照 | 구간단속 시작 | 区间测速开始 | ✅ 基础测速 | `autoNaviSpeedCtrlMode > 0` |
| 3 | 违章拍照 | 구간단속 끝 | 区间测速结束 | ✅ 基础测速 | `autoNaviSpeedCtrlMode > 0` |
| 4 | 公交专用道摄像头 | 구간단속중 | 区间测速中 | ✅ 基础测速 | `autoNaviSpeedCtrlMode > 0` |
| 7 | 移动式超速 | 과속 (이동식) | 超速（移动式） | ✅ 条件测速 | `autoNaviSpeedCtrlMode >= 3` |
| 8 | 固定式超速危险区 | 고정식 과속위험 구간(박스형) | 固定式超速危险区（箱式） | ✅ 基础测速 | `autoNaviSpeedCtrlMode > 0` |
| **特殊类型（处理）** |
| 22 | 减速带 | 과속방지턱 | 减速带 | ✅ 特殊处理 | `roadcate > 1` 且 `autoNaviSpeedCtrlMode >= 2` |
| 75 | 隧道内变道抓拍 | 터널내 차로변경단속 | 隧道内变道抓拍 | ✅ 基础测速 | `autoNaviSpeedCtrlMode > 0` |
| 76 | 隧道内变道抓拍 | 터널내 차로변경단속 | 隧道内变道抓拍 | ✅ 基础测速 | `autoNaviSpeedCtrlMode > 0` |
| **其他类型（不处理）** |
| 5 | 跟车抓拍摄像头 | 꼬리물기단속카메라 | 跟车抓拍摄像头 | ❌ 不处理 | - |
| 6 | 信号灯抓拍 | 신호 단속 | 信号灯抓拍 | ❌ 不处理 | - |
| 9 | 公交专用道区间 | 버스전용차로구간 | 公交专用道区间 | ❌ 不处理 | - |
| 10 | 可变车道抓拍 | 가변 차로 단속 | 可变车道抓拍 | ❌ 不处理 | - |
| 11 | 路肩监控点 | 갓길 감시 지점 | 路肩监控点 | ❌ 不处理 | - |
| 12 | 禁止加塞 | 끼어들기 금지 | 禁止加塞 | ❌ 不处理 | - |
| 13 | 交通信息采集点 | 교통정보 수집지점 | 交通信息采集点 | ❌ 不处理 | - |
| 14 | 防盗CCTV | 방범용cctv | 防盗CCTV | ❌ 不处理 | - |
| 15 | 超载车辆危险区 | 과적차량 위험구간 | 超载车辆危险区 | ❌ 不处理 | - |
| 16 | 货物装载不良抓拍 | 적재 불량 단속 | 货物装载不良抓拍 | ❌ 不处理 | - |
| 17 | 违停抓拍点 | 주차단속 지점 | 违停抓拍点 | ❌ 不处理 | - |
| 18 | 单行道 | 일방통행도로 | 单行道 | ❌ 不处理 | - |
| 19 | 铁路道口 | 철길 건널목 | 铁路道口 | ❌ 不处理 | - |
| 20 | 儿童保护区开始 | 어린이 보호구역(스쿨존 시작 구간) | 儿童保护区（学校区域开始） | ❌ 不处理 | - |
| 21 | 儿童保护区结束 | 어린이 보호구역(스쿨존 끝 구간) | 儿童保护区（学校区域结束） | ❌ 不处理 | - |
| 23-66 | 其他类型 | 其他类型 | 其他类型 | ❌ 不处理 | - |

## 基于 carrot_serv.py 的 SDI 字段逆向分析（逐字段）

> 依据 `refer/carrot/carrot_serv.py` 的成员变量、更新流程（`update`）、核心处理（`_update_sdi`、`update_navi`）与下游输出，逐项解释字段的来源、类型、单位、意义与使用位置。若字段含义有推测成分，会明确标注“需验证”。

### 一、SDI 基础字段（电子眼/测速相关）
- `nSdiType`（Int）
  - 含义：SDI类型编号（摄像头/执法设备类型）。
  - 取值：常用 0,1,2,3,4,7,8,75,76,22 等，详见类型表；-1 表示无效。
  - 来源：`update(json)` 中直接从 JSON 赋值。
  - 用途：`_update_sdi` 中决定是否生效与如何处理；影响 `xSpdType` 与速度控制分支。

- `nSdiSpeedLimit`（Int，km/h）
  - 含义：与该 SDI 相关的限速值。
  - 来源：`update(json)` 中直接从 JSON 赋值。
  - 用途：`_update_sdi` 将其乘以 `autoNaviSpeedSafetyFactor` 作为 `xSpdLimit` 的基础。

- `nSdiDist`（Int，m）
  - 含义：到该 SDI 点（或区间关键点）的剩余距离（米）。
  - 来源：`update(json)` 中直接从 JSON 赋值。
  - 用途：`_update_sdi` 中赋给 `xSpdDist`，参与减速速度计算。

- `nSdiSection`（Int）
  - 含义：区间测速标识（需验证）。
  - 来源：`update(json)` 赋值；当前核心逻辑中未直接使用。
  - 用途：暂无直接使用，可能为上游保留字段。

### 二、区间测速字段（Block）
- `nSdiBlockType`（Int）
  - 含义：区间测速状态；约定：1=开始、2=中、3=结束。
  - 来源：`update(json)` 赋值。
  - 用途：在 `_update_sdi` 中，若取值为 2 或 3，则：
    - `xSpdDist = nSdiBlockDist`
    - `xSpdType = 4`（统一视为“区间中/结束”类型，便于速度控制）

- `nSdiBlockSpeed`（Int，km/h）
  - 含义：区间测速的限速（需验证）。
  - 来源：`update(json)` 赋值。
  - 用途：当前核心逻辑未直接引用，可能用于 UI 或后续扩展。

- `nSdiBlockDist`（Int，m）
  - 含义：区间测速剩余距离或区间长度（需验证）。
  - 用途：当 `nSdiBlockType in [2,3]` 时，用于覆盖 `xSpdDist`。

### 三、SDI Plus 字段（扩展，如减速带）
- `nSdiPlusType`（Int）
  - 含义：Plus 类型编号，`22` 表示减速带（已在逻辑中明确）。
  - 用途：当（`nSdiPlusType == 22` 或 `nSdiType == 22`）且 `roadcate > 1` 且 `autoNaviSpeedCtrlMode >= 2` 时：
    - `xSpdLimit = autoNaviSpeedBumpSpeed`
    - `xSpdDist = nSdiPlusDist`（若Plus）或 `nSdiDist`（若Type是22）
    - `xSpdType = 22`

- `nSdiPlusSpeedLimit`（Int，km/h）
  - 含义：Plus 的限速值（若存在，需验证数据源是否稳定）。
  - 用途：核心逻辑未直接引用，减速带使用的是 `autoNaviSpeedBumpSpeed`。

- `nSdiPlusDist`（Int，m）
  - 含义：到 Plus 事件（如减速带）的距离（米）。
  - 用途：与 `nSdiPlusType` 配合用于设置 `xSpdDist`。

- `nSdiPlusBlockType / nSdiPlusBlockSpeed / nSdiPlusBlockDist`（Int）
  - 含义：Plus 的区间扩展（需验证是否有上游提供）。
  - 用途：当前核心逻辑未引用，暂视为占位扩展。

### 四、运行参数与阈值（影响 SDI 生效方式）
- `autoNaviSpeedSafetyFactor`（Float，百分比×0.01）
  - 用途：`xSpdLimit = nSdiSpeedLimit * autoNaviSpeedSafetyFactor`。
- `autoNaviSpeedCtrlMode`（Int）
  - 用途：>0 才启用基础测速控制；移动式 `nSdiType==7` 需 >=3 才启用。
- `autoNaviSpeedCtrlEnd`（Float，s）
  - 用途：`calculate_current_speed` 的 `safe_time`（非减速带）。
- `autoNaviSpeedBumpTime`（Float，s）
  - 用途：减速带的 `safe_time`。
- `autoNaviSpeedBumpSpeed`（Float，km/h）
  - 用途：减速带的目标安全速度。
- `autoNaviSpeedDecelRate`（Float，m/s^2）
  - 用途：`calculate_current_speed` 的减速度。
- `roadcate`（Int）
  - 用途：>1 才允许触发减速带逻辑（视为非高速，需验证定义）。

### 五、输出到速度控制的核心中间量
- `xSpdType`（Int）
  - 含义：当前生效的 SDI 类型，用于速度控制分支。
  - 取值：
    - 22：减速带
    - 4：区间测速生效态（由 `nSdiBlockType in [2,3]` 归一）
    - 0/1/2/3/7/8/75/76：对应常见摄像头类型
    - 100/101：来自外部（Waze/police）提示
    - -1：无生效 SDI

- `xSpdLimit`（Int，km/h）
  - 含义：当前生效的目标控制限速。
  - 来源：由 `_update_sdi` 结合 `nSdiSpeedLimit` 或减速带参数计算。

- `xSpdDist`（Int，m）
  - 含义：到控制点/事件的剩余距离，用于减速曲线计算。
  - 来源：通常来自 `nSdiDist`，区间或减速带条件下会被覆盖。

### 六、速度计算与控制触发
- `calculate_current_speed(left_dist, safe_speed_kph, safe_time, safe_decel_rate)`
  - 逻辑：
    - 将目标速度换算为 m/s；预留 `safe_time` 作为通过事件前的安全距离；
    - 使用减速度与剩余距离计算当前可行速度；
    - 取 `max(safe_speed_kph, ...)` 并限制上限 250。
  - 使用：
    - 在 `update_navi` 中，若 SDI 或 HDA 生效，计算 `sdi_speed` 作为候选速度。

- 触发与激活
  - `self.active_carrot`：
    - 3：速度减速生效（普通摄像头/区间）
    - 4：区间生效态（或警示源到达）
    - 5：减速带生效
  - 速度源融合：
    - `desired_speed` = min(atc, sdi_speed, road_limit, route/vturn …)

### 七、与上游字段的典型映射（结合 KEY_TYPE 经验）
- 基础摄像头（如 13005 / 100001）：
  - `CAMERA_TYPE` → `nSdiType`
  - `SPEED_LIMIT`/`CAMERA_SPEED` → `nSdiSpeedLimit`
  - `DISTANCE`/`CAMERA_DIST` → `nSdiDist`
- 区间测速（内部/扩展字段）：
  - `BLOCK_TYPE` → `nSdiBlockType`
  - `BLOCK_SPEED` → `nSdiBlockSpeed`
  - `BLOCK_DIST` → `nSdiBlockDist`
- SDI Plus（如 10007 减速带）：
  - `SDI_TYPE(=22)` → `nSdiPlusType`
  - `SPEED_LIMIT` → `nSdiPlusSpeedLimit`
  - `SDI_DIST` → `nSdiPlusDist`

### 八、逐字段速查表（汇总）
| 字段 | 类型/单位 | 角色 | 来源 | 主要用途 |
|------|-----------|------|------|----------|
| `nSdiType` | Int | 基础 | JSON | 决定类型与分支处理 |
| `nSdiSpeedLimit` | Int, km/h | 基础 | JSON | 计算 `xSpdLimit` |
| `nSdiDist` | Int, m | 基础 | JSON | 计算/更新 `xSpdDist` |
| `nSdiSection` | Int | 附属 | JSON | 暂未用，待验证 |
| `nSdiBlockType` | Int | 区间 | JSON | 归一为类型4，改写距离 |
| `nSdiBlockSpeed` | Int, km/h | 区间 | JSON | 暂未用，待验证 |
| `nSdiBlockDist` | Int, m | 区间 | JSON | 覆盖 `xSpdDist` |
| `nSdiPlusType` | Int | Plus | JSON | 触发减速带逻辑 |
| `nSdiPlusSpeedLimit` | Int, km/h | Plus | JSON | 暂未用，减速带速度用参数 |
| `nSdiPlusDist` | Int, m | Plus | JSON | 作为 `xSpdDist` 值 |
| `nSdiPlusBlockType` | Int | Plus区间 | JSON | 暂未用 |
| `nSdiPlusBlockSpeed` | Int, km/h | Plus区间 | JSON | 暂未用 |
| `nSdiPlusBlockDist` | Int, m | Plus区间 | JSON | 暂未用 |
| `xSpdType` | Int | 输出 | 计算 | 速度控制分支与状态 |
| `xSpdLimit` | Int, km/h | 输出 | 计算 | 期望速度上限 |
| `xSpdDist` | Int, m | 输出 | 计算 | 减速距离基准 |

### 九、尚需验证的点
- `nSdiSection`、`nSdiBlockSpeed`、Plus 区间三字段的上游来源与实际用途。
- `roadcate` 精确定义及其与不同道路类型（高速/城市）的对应关系。
- `nSdiType=75/76` 的真实触发条件与与隧道变道取缔场景的联动。
