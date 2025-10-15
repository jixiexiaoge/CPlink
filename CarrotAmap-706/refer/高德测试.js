"ui";
importClass(android.content.Intent);
importClass(android.content.ContextWrapper);
importClass(android.content.IntentFilter);

// 高德标准广播ACTION
const AMAP_SEND_ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND";
const AMAP_RECV_ACTION = "AUTONAVI_STANDARD_BROADCAST_RECV";

// 创建UI界面
ui.layout(
    <vertical>
        <button id="btnStart" text="开始监听原始广播" w="*" h="50"/>
        <button id="btnStop" text="停止监听" w="*" h="50"/>
        <scroll>
            <text id="rawLog" textSize="12sp" w="*"/>
        </scroll>
    </vertical>
);

let receiver = null;
let rawLogContent = "";

// 更新原始数据日志
function updateRawLog(text) {
    ui.run(() => {
        rawLogContent = text + "\n\n" + rawLogContent;
        ui.rawLog.setText(rawLogContent);
    });
}

// 格式化高德导航数据
function formatAmapData(rawData) {
    let result = "📡 收到高德导航数据:\n";
    result += "🔹 广播类型: " + rawData.action + "\n";
    result += "🔹 关键类型: " + (rawData.extras.KEY_TYPE || "未知") + "\n\n";
    
    // 导航基本信息
    if (rawData.extras.ROUTE_REMAIN_DIS !== undefined) {
        result += "🗺️ 导航概览:\n";
        result += "├─ 剩余距离: " + (rawData.extras.ROUTE_REMAIN_DIS_AUTO || rawData.extras.ROUTE_REMAIN_DIS + "米") + "\n";
        result += "├─ 剩余时间: " + (rawData.extras.ROUTE_REMAIN_TIME_AUTO || rawData.extras.ROUTE_REMAIN_TIME + "秒") + "\n";
        result += "├─ 预计到达: " + (rawData.extras.ETA_TEXT || "未知") + "\n";
        result += "└─ 总距离: " + (rawData.extras.ROUTE_ALL_DIS || 0) + "米\n\n";
    }
    
    // 当前道路信息
    if (rawData.extras.CUR_ROAD_NAME) {
        result += "🛣️ 当前道路:\n";
        result += "├─ 名称: " + rawData.extras.CUR_ROAD_NAME + "\n";
        result += "├─ 当前速度: " + (rawData.extras.CUR_SPEED || 0) + " km/h\n";
        result += "└─ 方向: " + (rawData.extras.CAR_DIRECTION || 0) + "°\n\n";
    }
    
    // 下一个转弯信息
    if (rawData.extras.NEXT_ROAD_NAME) {
        result += "↪️ 下一个转弯:\n";
        result += "├─ 道路名称: " + rawData.extras.NEXT_ROAD_NAME + "\n";
        result += "├─ 图标类型: " + getTurnIconDesc(rawData.extras.ICON) + "\n";
        result += "├─ 剩余距离: " + (rawData.extras.SEG_REMAIN_DIS_AUTO || rawData.extras.SEG_REMAIN_DIS + "米") + "\n";
        result += "└─ 剩余时间: " + (rawData.extras.SEG_REMAIN_TIME || 0) + "秒\n\n";
    }
    
    // 下一个服务区信息
    if (rawData.extras.SAPA_DIST !== -1) {
        result += "⛽ 服务区信息:\n";
        result += "├─ 名称: " + (rawData.extras.SAPA_NAME || "未知") + "\n";
        result += "├─ 距离: " + (rawData.extras.SAPA_DIST_AUTO || rawData.extras.SAPA_DIST + "米") + "\n";
        result += "└─ 类型: " + getSapaTypeDesc(rawData.extras.SAPA_TYPE) + "\n\n";
    }
    
    // 摄像头信息
    if (rawData.extras.CAMERA_DIST !== -1) {
        result += "📸 摄像头信息:\n";
        result += "├─ 距离: " + rawData.extras.CAMERA_DIST + "米\n";
        result += "├─ 类型: " + getCameraTypeDesc(rawData.extras.CAMERA_TYPE) + "\n";
        result += "└─ 限速: " + (rawData.extras.LIMITED_SPEED > 0 ? rawData.extras.LIMITED_SPEED + " km/h" : "未知") + "\n\n";
    }
    
    // 红绿灯信息
    if (rawData.extras.trafficLightStatus !== undefined) {
        result += "🚦 红绿灯信息:\n";
        result += "├─ 状态: " + getTrafficLightStatusDesc(rawData.extras.trafficLightStatus) + "\n";
        result += "├─ 方向: " + getDirectionDesc(rawData.extras.dir) + "\n";
        result += "└─ 倒计时: " + (rawData.extras.redLightCountDownSeconds || 0) + "秒\n\n";
    }
    
    // 目的地信息
    if (rawData.extras.endPOIName) {
        result += "🏁 目的地:\n";
        result += "├─ 名称: " + rawData.extras.endPOIName + "\n";
        result += "├─ 地址: " + rawData.extras.endPOIAddr + "\n";
        result += "└─ 坐标: " + rawData.extras.endPOILatitude + ", " + rawData.extras.endPOILongitude + "\n\n";
    }
    
    // 显示原始JSON数据
    result += "📋 原始数据:\n" + JSON.stringify(rawData, null, 2);
    
    return result;
}

// 获取转弯图标描述
function getTurnIconDesc(icon) {
    const icons = {
        1: "直行",
        2: "右转",
        3: "左转",
        4: "左前方转弯",
        5: "右前方转弯",
        6: "左后方转弯",
        7: "右后方转弯",
        8: "左转掉头",
        9: "右转掉头",
        10: "靠左行驶",
        11: "靠右行驶",
        12: "进入环岛",
        13: "离开环岛",
        14: "通过人行横道",
        15: "通过过街天桥",
        16: "通过地下通道",
        17: "通过广场",
        18: "通过其他",
        19: "通过隧道",
        20: "通过桥梁",
        21: "通过收费站",
        22: "通过服务区",
        23: "通过加油站",
        24: "通过停车场",
        25: "通过飞机场",
        26: "通过火车站",
        27: "通过汽车站",
        28: "通过港口",
        29: "通过医院",
        30: "通过学校",
        31: "通过商场",
        32: "通过酒店",
        33: "通过政府",
        34: "通过银行",
        35: "通过景点",
        36: "通过公园",
        37: "通过厕所",
        38: "通过餐厅",
        39: "通过其他POI"
    };
    return icons[icon] || "未知(" + icon + ")";
}

// 获取服务区类型描述
function getSapaTypeDesc(type) {
    const types = {
        0: "服务区",
        1: "收费站",
        2: "加油站",
        3: "停车场",
        4: "其他"
    };
    return types[type] || "未知(" + type + ")";
}

// 获取摄像头类型描述
function getCameraTypeDesc(type) {
    const types = {
        0: "测速摄像头",
        1: "监控摄像头",
        2: "闯红灯摄像头",
        3: "违章拍照",
        4: "流动测速",
        5: "区间测速起点",
        6: "区间测速终点",
        7: "其他"
    };
    return types[type] || "未知(" + type + ")";
}

// 获取红绿灯状态描述
function getTrafficLightStatusDesc(status) {
    const statuses = {
        0: "未知",
        1: "红灯",
        2: "绿灯",
        3: "黄灯"
    };
    return statuses[status] || "未知(" + status + ")";
}

// 获取方向描述
function getDirectionDesc(dir) {
    const dirs = {
        0: "未知",
        1: "直行",
        2: "右转",
        3: "左转",
        4: "左转掉头",
        5: "右转掉头"
    };
    return dirs[dir] || "未知(" + dir + ")";
}

// 开始监听原始广播
ui.btnStart.on("click", () => {
    if (receiver) {
        updateRawLog("⚠️ 已经在监听状态");
        return;
    }

    let filter = new IntentFilter();
    filter.addAction(AMAP_SEND_ACTION);
    filter.addAction(AMAP_RECV_ACTION);

    receiver = new android.content.BroadcastReceiver({
        onReceive: function(context, intent) {
            // 获取原始广播数据
            let rawData = {
                action: intent.getAction(),
                extras: {}
            };

            // 解析所有附加数据
            let bundle = intent.getExtras();
            if (bundle) {
                let keys = bundle.keySet().toArray();
                for (let i = 0; i < keys.length; i++) {
                    let key = keys[i];
                    rawData.extras[key] = bundle.get(key);
                }
            }

            // 格式化并显示数据
            updateRawLog(formatAmapData(rawData));
        }
    });

    try {
        new ContextWrapper(context).registerReceiver(receiver, filter);
        updateRawLog("✅ 开始监听高德导航广播\n等待数据...");
    } catch (e) {
        updateRawLog("❌ 注册失败: " + e.toString());
    }
});

// 停止监听
ui.btnStop.on("click", () => {
    if (receiver) {
        try {
            new ContextWrapper(context).unregisterReceiver(receiver);
            receiver = null;
            updateRawLog("🛑 已停止监听");
        } catch (e) {
            updateRawLog("❌ 注销失败: " + e.toString());
        }
    }
});
