// 示例1：基础定位与时间信息
{
  "accuracy": 12.567,                // 定位精度（米）
  "carrotIndex": 65,                 // 数据包序号
  "epochTime": 1755075532,           // Unix时间戳
  "gps_speed": 0.10663239,           // GPS速度（米/秒）
  "heading": 0.0,                    // 方向角（度）
  "latitude": 37.0837762,            // 纬度
  "longitude": 126.8984131,          // 经度
  "timezone": "Asia/Seoul"           // 时区
}

// 示例2：导航与道路详细信息
{
  "carrotIndex": 66,                 // 数据包序号
  "epochTime": 1755075533,           // Unix时间戳
  "ngoPosDist": 12455,               // 目标点距离（米）
  "ngoPosTime": 1752,                // 目标点预计到达时间（秒）
  "nlaneCount": 2,                   // 车道数量
  "nposAngle": 357,                  // 当前角度
  "nposSpeed": 0,                    // 当前速度
  "nroadLimitSpeed": 60,             // 道路限速
  "nsdiDist": 0,                     // 距离下一个限速点距离
  "nsdiPlusBlockDist": 0,            // SDI Plus区间距离
  "nsdiPlusBlockSpeed": 0,           // SDI Plus区间限速
  "nsdiPlusBlockType": 0,            // SDI Plus区间类型
  "nsdiPlusDist": 0,                 // SDI Plus距离
  "nsdiPlusSpeedLimit": 0,           // SDI Plus限速
  "nsdiPlusType": 0,                 // SDI Plus类型
  "nsdiSpeedLimit": 0,               // 限速点限速
  "nsdiType": 0,                     // 限速点类型
  "ntbtdist": 267,                   // 到下个转弯距离
  "ntbtdistNext": 1310,              // 到下下个转弯距离
  "ntbtnextRoadWidth": 10,           // 下条道路宽度
  "ntbtturnType": 13,                // 下个转弯类型
  "ntbtturnTypeNext": 12,            // 下下个转弯类型
  "nGoPosDist": 12455,               // 目标点距离（冗余字段）
  "nGoPosTime": 1752,                // 目标点预计到达时间（冗余字段）
  "nLaneCount": 2,                   // 车道数量（冗余字段）
  "nPosAngle": 357,                  // 当前角度（冗余字段）
  "nPosSpeed": 0,                    // 当前速度（冗余字段）
  "nRoadLimitSpeed": 60,             // 道路限速（冗余字段）
  "nSdiDist": 0,                     // 距离下一个限速点距离（冗余字段）
  "nSdiPlusBlockDist": 0,            // SDI Plus区间距离（冗余字段）
  "nSdiPlusBlockSpeed": 0,           // SDI Plus区间限速（冗余字段）
  "nSdiPlusBlockType": 0,            // SDI Plus区间类型（冗余字段）
  "nSdiPlusDist": 0,                 // SDI Plus距离（冗余字段）
  "nSdiPlusSpeedLimit": 0,           // SDI Plus限速（冗余字段）
  "nSdiPlusType": 0,                 // SDI Plus类型（冗余字段）
  "nSdiSpeedLimit": 0,               // 限速点限速（冗余字段）
  "nSdiType": 0,                     // 限速点类型（冗余字段）
  "nTBTDist": 267,                   // 到下个转弯距离（冗余字段）
  "nTBTDistNext": 1310,              // 到下下个转弯距离（冗余字段）
  "nTBTNextRoadWidth": 10,           // 下条道路宽度（冗余字段）
  "nTBTTurnType": 13,                // 下个转弯类型（冗余字段）
  "nTBTTurnTypeNext": 12,            // 下下个转弯类型（冗余字段）
  "roadcate": 6,                     // 道路类型
  "szFarDirName": "안",              // 远端方向名
  "szNearDirName": "향남(발안)",      // 近端方向名
  "szPosRoadName": "발안공단로4길",   // 当前道路名
  "szTBTMainText": "향남(발안)",      // 主要转弯提示文本
  "timezone": "Asia/Seoul",          // 时区
  "vpPosPointLat": "37.0838459943642", // 虚拟点纬度
  "vpPosPointLon": "126.89957371027718" // 虚拟点经度
}

As you will understand when you see the code, anything that starts with Sdi is a speed bump or speed camera.  Starting with Tbt means TBT information...
And roadcate is the width of the road, 10,11 is the highway.
GoPos is the destination, and 10,11 is the highway.


10019 没有意义 运行的状态 用不上
12110 区间测速的详细数据，非常重要


KEY_TYPE=12205 GPS 定位成功 失败，没意义
13011 TMC json数据，用不上
13005 没用的！要验证
13022 暂时不知
10041 版本号
10049 是否继续上一次导航

10007（SDI Plus）
动作：解析 JSON 扩展，填充 nSdiPlusType/nSdiPlusSpeedLimit/nSdiPlusDist，可选 nSdiPlusBlockType/Speed/Dist
100001（电子眼 V2）
动作：新版字段同样通过 CAMERA_TYPE 统一映射到 nSdiType，并带速度/距离/索引等


60073 红绿灯数据
📦 广播详情: action=AUTONAVI_STANDARD_BROADCAST_SEND, KEY_TYPE=60073, EXTRA_STATE=-1
🔍 开始处理高德地图广播数据 (KEY_TYPE: 60073):
📋 Intent包含的所有数据:
   📌 waitRound = 0
   📌 trafficLightStatus = 1
   📌 greenLightLastSecond = 0
   📌 dir = 4
   📌 redLightCountDownSeconds = 10
   📌 KEY_TYPE = 60073


📦 广播详情: action=AUTONAVI_STANDARD_BROADCAST_SEND, KEY_TYPE=13022, EXTRA_STATE=-1
🔍 开始处理高德地图广播数据 (KEY_TYPE: 13022):
📋 Intent包含的所有数据:
   📌 KEY_TYPE = 13022