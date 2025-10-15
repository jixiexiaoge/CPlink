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
- “说明(navType / navModifier)” 与 “xTurnInfo” 直接来源于 `carrot_serv.py` 的映射；当两个表（nav_type_mapping 与 turn_type_mapping）对同一编号给出不同的 xTurnInfo 时，以 nav_type_mapping 为优先；个别历史差异（如 14/201 的 xTurnInfo）已在 Python 中统一为 5。
- 高德 ICON 的对应关系仅对能在 Android 端明确映射到该 nTBTTurnType 的项进行标注：
  - 0/13/14/16 → 51（通知）、11/12 → 131（环岛-右通行）、17/18 → 140（环岛-左通行）、10/15 → 201（到达）、8/19 → 14（掉头）、1/9/20 → 200（直行）。
  - ICON 4/5/6/7 在 Android 端为控制类别直出（3/4/1/2），Python 侧没有对应编号；因此未列入“标准”对应，仅在上表用 6/7 两行体现“后方”分岔行为。
- 若某编号在 `carrot_serv.py` 中仅用于细粒度控制但无明确 ICON 来源，则“高德ICON/ICON说明”留空。

---

### 快速对照：xTurnInfo 含义（来自 `carrot_serv.py`）
- 1: 左转 turn left
- 2: 右转 turn right
- 3: 左侧分岔/变道 fork left / left lane change / off ramp left
- 4: 右侧分岔/变道 fork right / right lane change / off ramp right
- 5: 环岛/直行控制类 rotary/straight family（包括到达、掉头等归入直行控制族）
- 6: TG（收费/特殊）
- 7: 掉头（历史用法，已趋向归入5）
- 8: 到达（历史用法，已趋向归入5）

### 备注
- 为避免歧义，建议 Android 侧尽量发送 `nav_type_mapping` 中存在的 nTBTTurnType；ICON 4/5/6/7 如需在 Python 生效，推荐转换为 1000/1001/1002/1003 或 12/13/7/6 等 Python已知编号。
