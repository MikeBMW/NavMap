package com.tencent.navix.power.builders;

import android.util.Log;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.navix.api.model.NavDriveDataInfoEx;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.power.managers.UIManager;
import com.tencent.navix.power.models.TrafficEvent;
import com.tencent.navix.power.models.TrafficLight;
import com.tencent.navix.power.models.RouteOverview;
import com.tencent.navix.power.utils.ReflectUtil;
import com.tencent.tencentmap.mapsdk.maps.model.LatLng;

import java.util.List;

/**
 * 导航数据结构构建器
 * 负责构建 TrafficEvent、TrafficLight、RouteOverview 等数据结构
 */
public class NavDataBuilder {
    private static final String TAG = "NavDataBuilder";

    // 配置参数 -> 导航路径
    private static final double ROUTE_DEVIATION_THRESHOLD = 1000.0; // 偏离阈值100米
    private static final int MAX_TRAFFIC_DISTANCE = 0xFFFF; // 最大交通事件距离
    //配置参数 -> 红绿灯
    private static final byte MAX_REMAINING_TIME = 20; // 最大剩余时间
    private static final double PASS_THRESHOLD = 50.0; // 通过红绿灯的阈值距离（米）  20米向右转太近了。

    // 交通灯状态管理
    private byte currentLightId = 0; // 当前下一个红绿灯ID
    private byte lastRemainingTime = MAX_REMAINING_TIME;
    private long lastUpdateTime = 0;
    private boolean isInitialized = false;
    private NavDriveRoute lastRoute = null;
    private int totalLights = 0; // 总红绿灯数量

    /**
     * 构建交通事件数据
     */
    public TrafficEvent buildTrafficEvent(NavDriveRoute currentRoute, NavDriveDataInfoEx navInfo) {
        TrafficEvent event = new TrafficEvent();

        if (currentRoute == null) {
            Log.w(TAG, "当前路线为空，返回默认交通事件");
            return createDefaultTrafficEvent();
        }

        try {
            int eventCount = 0, severeCount = 0, accidentCount = 0;
            int highestLevel = 0;
            int nearestDistance = Integer.MAX_VALUE;
            int nearestDelay = 0, nearestType = 0;

            /* 1. 从 route 层获取 trafficItems */
            List<?> trafficItems = (List<?>) ReflectUtil.getField(currentRoute, "trafficItems");
            if (trafficItems != null) {
                eventCount = trafficItems.size();

                for (Object item : trafficItems) {
                    int type = ReflectUtil.toInt(ReflectUtil.getField(item, "eventType"), 0);
                    int status = ReflectUtil.toInt(ReflectUtil.getField(item, "trafficStatus"), 0);
                    int distance = ReflectUtil.toInt(ReflectUtil.getField(item, "distance"), Integer.MAX_VALUE);
                    int passTime = ReflectUtil.toInt(ReflectUtil.getField(item, "passTime"), 0);
                    int navSpeed = ReflectUtil.toInt(ReflectUtil.getField(item, "speed"), 0);

                    // 获取实时车速，优先使用实时车速
                    int realSpeed = navInfo != null ? navInfo.getSpeedKMH() : navSpeed;
                    if (realSpeed < 1) realSpeed = 10;  // 保底：10 km/h
                    float navSpeedMps = realSpeed / 3.6f;

                    /* 2. 事件等级映射 */
                    int level = 0;
                    if (type == 12) level = 1;          // 拥堵
                    if (type == 13) level = 2;          // 事故 → 严重

                    // 重新估算 delay（秒）
                    int estDelay = (distance == Integer.MAX_VALUE) ? 0
                            : (int) (distance / navSpeedMps);
                    // 取更悲观值
                    estDelay = Math.max(estDelay, passTime);

                    /* 3. 统计 */
                    if (level == 2) severeCount++;
                    if (type == 13) accidentCount++;
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestDelay = estDelay;
                        nearestType = type;
                    }
                    if (level > highestLevel) highestLevel = level;
                }
            }

            if (nearestDistance == Integer.MAX_VALUE) {
                nearestDistance = 0;
            }

            /* 4. 按协议打包 */
            event.setEventCount((byte) Math.min(eventCount, 127));
            event.setEventSummary((byte) (((highestLevel & 0x03) << 6) | (eventCount & 0x3F)));
            event.setNearestDistance((short) Math.max(0, Math.min(nearestDistance, MAX_TRAFFIC_DISTANCE)));
            event.setNearestType((byte) Math.max(0, Math.min(nearestType, 0xFF)));
            event.setNearestDelay((byte) Math.max(0, Math.min(nearestDelay, 0xFF)));
            event.setSevereCount((byte) Math.min(severeCount, 127));
            event.setAccidentCount((byte) Math.min(accidentCount, 127));

            Log.d(TAG, String.format("构建交通事件: 总数=%d, 最近距离=%dm, 严重=%d",
                    eventCount, nearestDistance, severeCount));

        } catch (Exception e) {
            Log.e(TAG, "构建交通事件失败", e);
            return createDefaultTrafficEvent();
        }

        return event;
    }

    /**
     * 构建交通灯数据
     */
    public TrafficLight buildTrafficLight(NavDriveRoute currentRoute,
                                          TencentLocation currentLocation,
                                          NavDriveDataInfoEx navInfo) {
        TrafficLight light = new TrafficLight();

        if (currentRoute == null || currentLocation == null) {
            Log.w(TAG, "路线或位置为空，返回默认交通灯数据");
            return createDefaultTrafficLight();
        }

        try {
            // 检查是否是新路线，需要重置状态
            if (lastRoute != currentRoute) {
                resetTrafficLightState(currentRoute);
                lastRoute = currentRoute;
            }
            int speedMain = 0;
            if (navInfo != null) {
                speedMain = navInfo.getSpeedKMH();
            }

//            List<?> lights = (List<?>) ReflectUtil.getField(currentRoute, "trafficLights");
//            if (lights == null || lights.isEmpty()) {
//                Log.d(TAG, "没有交通灯数据");
//                return light;
//            }
            List<?> lights = (List<?>) ReflectUtil.getField(currentRoute, "trafficLights");
            if (lights == null || lights.isEmpty()) {
                Log.d(TAG, "没有交通灯数据");
                return createDefaultTrafficLight();
            }

            // 确保当前灯ID在有效范围内
            if (currentLightId >= totalLights) {
                Log.d(TAG, "已通过所有红绿灯");
                return createDefaultTrafficLight();
            }

            /* 1. 自车实时位置 */
            double carLat = currentLocation.getLatitude();
            double carLon = currentLocation.getLongitude();
            double speedMps = calculateSpeedFromLocation(currentLocation);

            /* 2. 获取当前下一个红绿灯的位置和距离 */
            TrafficLightInfo lightInfo = getCurrentTrafficLightInfo(lights, carLat, carLon);
            if (lightInfo == null) {
                Log.d(TAG, "无法获取红绿灯信息");
                return createDefaultTrafficLight();
            }

            double distanceToNextLight = lightInfo.distance;
            Object currentLight = lightInfo.lightObject;

            /* 3. 初始化交通灯状态管理 */
            if (!isInitialized) {
                initializeTrafficLightState();
            }

            /* 4. 更新交通灯状态 */
            updateTrafficLightState((byte) speedMain, (short) distanceToNextLight);

            /* 5. 检查是否通过当前红绿灯 */
            if (distanceToNextLight <= PASS_THRESHOLD) {
                passCurrentLight();
                // 重新获取下一个红绿灯信息
                if (currentLightId < totalLights) {
                    lightInfo = getCurrentTrafficLightInfo(lights, carLat, carLon);
                    if (lightInfo != null) {
                        distanceToNextLight = lightInfo.distance;
                    }
                }
            }

            /* 6. 灯态（基于实时车速） */
            int state = (speedMain > 30) ? 1 : 0; // 1=绿灯, 0=红灯
            int speedKphInt = Math.min(255, Math.max(0, speedMain)); // 0-255

            /* 7. 打包 */
            light.setNextLightId(currentLightId);
            light.setStateFlags((byte) (((0 & 0x03) << 4) | ((state & 0x03) << 2)));
            light.setDistanceToNextLight((short) Math.min(distanceToNextLight, 0xFFFF));
            light.setRemainingTime(lastRemainingTime);
            light.setDistanceToLight((byte) Math.min((int) distanceToNextLight, 0xFF));
            light.setSpeed((byte) speedKphInt);
            light.setLightCount((byte) Math.min(255, totalLights));

            Log.d(TAG, String.format("构建交通灯: 下一个灯ID=%d, 距离=%.1fm, 剩余时间=%ds, 状态=%d, 车速=%dkm/h, 总灯数=%d",
                    currentLightId, distanceToNextLight, lastRemainingTime, state, speedKphInt, totalLights));




//            float speedKph = (float)(speedMps * 3.6);

//            if (speedMps < 0.5f) speedMps = 0.5f;

            /* 2. 取最近一盏灯（这里简化：列表第 0 个） */
//            Object firstLight = lights.get(0);


            /* 3. 获取交通灯位置 */
//            Object latLng = ReflectUtil.getField(firstLight, "latLng");
//            double lightLat = ReflectUtil.toDouble(ReflectUtil.getField(latLng, "latitude"), 0);
//            double lightLon = ReflectUtil.toDouble(ReflectUtil.getField(latLng, "longitude"), 0);

//            int positionIndex = ReflectUtil.toInt(ReflectUtil.getField(firstLight, "pointIndex"), 0);
//            short distanceToNextLight = 1000;
            /* 4. 直线距离 m + 到达时间 s */
//            double dist = calculateHaversineDistance(carLat, carLon, lightLat, lightLon);
//            int eta = (int) (dist / speedMps);

            /* 5. 灯态 & 剩余秒数（基于实时车速） */
//            int state, remaining;
//            if (speedKph > 30) {          // 车速较快，假设绿灯
//                state = 1;
//                remaining = 10 + (eta % 16);   // 10~25 s
//            } else {                      // 车速较慢，假设红灯
//                state = 0;
//                remaining = 25 + (eta % 21);   // 25~45 s
//            }


//            int lightCount = ReflectUtil.toInt(ReflectUtil.getField(currentRoute, "trafficLightCount"), 0);

//            /* 6. 打包 */
//            light.setNextLightId((byte) Math.min(positionIndex & 0xFF, 0xFF));
//            light.setStateFlags((byte) (((0 & 0x03) << 4) | ((state & 0x03) << 2)));
////            light.setPositionIndex((short) Math.min(positionIndex, 0xFFFF));
//            light.setDistanceToNextLight((short) Math.min(distanceToNextLight, 0xFFFF));
//            light.setRemainingTime((byte) Math.min(remaining, 0xFF));
//            light.setDistanceToLight((byte) Math.min((int) dist, 0xFF));
//            light.setSpeed((byte) speedKphInt);
//            light.setLightCount((byte) Math.min(255, lightCount));

//            Log.d(TAG, String.format("构建交通灯: 距离=%dm, 剩余时间=%ds, 状态=%d",
//                    (int) dist, remaining, state));

        } catch (Exception e) {
            Log.e(TAG, "构建交通灯数据失败", e);
            return createDefaultTrafficLight();
        }

        return light;
    }

//    /**
//     * 初始化交通灯状态
//     */
//    private void initializeTrafficLightState() {
//        currentLightId = 0;
//        lastRemainingTime = MAX_REMAINING_TIME;
//        lastUpdateTime = System.currentTimeMillis();
//        isInitialized = true;
//        Log.d(TAG, "交通灯状态管理初始化完成");
//    }
    /**
     * 初始化交通灯状态
     */
    private void initializeTrafficLightState() {
        currentLightId = 0;
        lastRemainingTime = MAX_REMAINING_TIME;
        lastUpdateTime = System.currentTimeMillis();
        isInitialized = true;
        Log.d(TAG, "交通灯状态管理初始化完成，总灯数: " + totalLights);
    }

    /**
     * 获取当前下一个红绿灯的信息
     */
    private TrafficLightInfo getCurrentTrafficLightInfo(List<?> lights, double carLat, double carLon) {
        if (lights == null || lights.isEmpty() || currentLightId >= lights.size()) {
            return null;
        }

        try {
            Object light = lights.get(currentLightId);
            Object latLng = ReflectUtil.getField(light, "latLng");
            double lightLat = ReflectUtil.toDouble(ReflectUtil.getField(latLng, "latitude"), 0);
            double lightLon = ReflectUtil.toDouble(ReflectUtil.getField(latLng, "longitude"), 0);

            double distance = calculateHaversineDistance(carLat, carLon, lightLat, lightLon);

            return new TrafficLightInfo(currentLightId, distance, light);
        } catch (Exception e) {
            Log.e(TAG, "获取红绿灯信息失败", e);
            return null;
        }
    }

    /**
     * 更新交通灯状态
     */
    private void updateTrafficLightState(byte currentSpeed, short distanceToNextLight) {
        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - lastUpdateTime) / 1000;

        if (elapsedSeconds > 0) {
            updateRemainingTime((int) elapsedSeconds, currentSpeed);
            lastUpdateTime = currentTime;
        }
    }

    /**
     * 更新剩余时间算法
     */
    private void updateRemainingTime(int elapsedSeconds, byte currentSpeed) {
        for (int i = 0; i < elapsedSeconds; i++) {
            if (currentSpeed > 5) {
                // 车速 > 5km/h，时间递减
                if (lastRemainingTime > 0) {
                    lastRemainingTime--;
                }
            } else {
                // 车速 <= 5km/h，时间递增
                if (lastRemainingTime < MAX_REMAINING_TIME) {
                    lastRemainingTime++;
                }
            }
        }
    }

    /**
     * 通过当前红绿灯
     */
    private void passCurrentLight() {
        if (currentLightId < totalLights - 1) {
            currentLightId++;
            lastRemainingTime = MAX_REMAINING_TIME;
            Log.d(TAG, "通过红绿灯，切换到下一个灯ID: " + currentLightId);
        } else {
            currentLightId = (byte) totalLights; // 设置为超出范围，表示已通过所有灯
            Log.d(TAG, "已通过所有红绿灯");
        }
    }

    /**
     * 重置交通灯状态（当开始新的导航时调用）
     */
    public void resetTrafficLightState(NavDriveRoute currentRoute) {
        if (currentRoute != null) {
            try {
                List<?> lights = (List<?>) ReflectUtil.getField(currentRoute, "trafficLights");
                totalLights = (lights != null) ? lights.size() : 0;
            } catch (Exception e) {
                Log.e(TAG, "获取红绿灯数量失败", e);
                totalLights = 0;
            }
        }

        currentLightId = 0;
        lastRemainingTime = MAX_REMAINING_TIME;
        lastUpdateTime = System.currentTimeMillis();
        isInitialized = false;
        lastRoute = null;

        Log.d(TAG, "交通灯状态已重置，总灯数: " + totalLights);
    }

    /**
     * 红绿灯信息类
     */
    private static class TrafficLightInfo {
        public byte lightId;
        public double distance;
        public Object lightObject;

        public TrafficLightInfo(byte lightId, double distance, Object lightObject) {
            this.lightId = lightId;
            this.distance = distance;
            this.lightObject = lightObject;
        }
    }

    /**
     * 构建路线概览数据
     */
    public RouteOverview buildRouteOverview(NavDriveRoute currentRoute, NavDriveDataInfoEx navInfo) {
        RouteOverview overview = new RouteOverview();

        if (currentRoute == null) {
            Log.w(TAG, "当前路线为空，返回默认路线概览");
            return createDefaultRouteOverview();
        }

        try {
            /* 1. 距离 & 费用 */
            int totalDistance = (int) Math.max(0, currentRoute.getDistance());
            int passedDistance = 0;
            int leftDistance = totalDistance;
            // 如果有实时导航信息，使用实时计算的剩余距离
            if (navInfo != null) {
                passedDistance = navInfo.getPassedDistance();
                leftDistance = totalDistance - passedDistance;

                // 确保剩余距离不会为负数
                if (leftDistance < 0) {
                    leftDistance = 0;
                }
            }
            int tollDistance = ReflectUtil.toInt(ReflectUtil.getField(currentRoute, "tollDistance"), 0);
            int tollPct = totalDistance == 0 ? 0
                    : (int) Math.min(100, tollDistance * 100L / totalDistance);

            /* 2. 剩余 estimatedTime */
//            int durationMin = ReflectUtil.toInt(ReflectUtil.getField(currentRoute, "time"), 0);
            /* 2. 剩余 estimatedTime - 使用实时计算的剩余时间 */
            int totalTimeMinutes = ReflectUtil.toInt(ReflectUtil.getField(currentRoute, "time"), 0);
            int leftTimeMinutes = totalTimeMinutes;

            // 如果有实时导航信息，使用实时计算的剩余时间
            if (navInfo != null) {
                int totalTimeSeconds = totalTimeMinutes * 60;
                int passedTimeSeconds = navInfo.getPassedTime();
                int leftTimeSeconds = totalTimeSeconds - passedTimeSeconds;

                // 确保剩余时间不会为负数
                if (leftTimeSeconds < 0) {
                    leftTimeSeconds = 0;
                }

                leftTimeMinutes = (int) Math.ceil(leftTimeSeconds / 60.0);
            }


            long feeDeci = Math.round(Math.max(0.0, currentRoute.getFee()) * 10.0);
            feeDeci = Math.min(feeDeci, 0xFFFF);

            /* 3. 基于 segmentItems 计算拥堵指数 */
            List<?> segs = (List<?>) ReflectUtil.getField(currentRoute, "segmentItems");
            double totalCost = 0;      // 累计"驾驶复杂度"
            int segCount = (segs == null) ? 0 : segs.size();

            if (segs != null) {
                for (Object seg : segs) {
                    int distance = ReflectUtil.toInt(ReflectUtil.getField(seg, "distance"), 0);
                    int lightCount = ReflectUtil.toInt(ReflectUtil.getField(seg, "numTrafficLight"), 0);
                    String action = ReflectUtil.toString(ReflectUtil.getField(seg, "action"), "");
                    String roadName = ReflectUtil.toString(ReflectUtil.getField(seg, "roadName"), "");

                    /* 动作惩罚 */
                    int actionPenalty = 0;
                    if ("左转".equals(action) || "掉头".equals(action)) actionPenalty = 25;
                    else if ("直行".equals(action)) actionPenalty = 10;
                    else if ("右转".equals(action)) actionPenalty = 5;

                    /* 道路类型惩罚 */
                    int roadPenalty = 0;
                    if (roadName.contains("内部道路") || roadName.contains("小区"))
                        roadPenalty = 20;
                    else if (roadName.contains("主干道") || roadName.contains("高速"))
                        roadPenalty = -10;   // 负值=更快

                    /* 单段代价（米当量） */
                    double segCost = distance
                            + 30 * lightCount
                            + actionPenalty
                            + roadPenalty;
                    totalCost += Math.max(0, segCost);
                }
            }

            /* 4. 映射到 0-100 百分比 */
            int jam = (int) Math.min(100, Math.max(0, totalCost / 120 * 100));
            int slow = (int) Math.min(100 - jam, Math.max(0, totalCost / 60 * 100));
            int smooth = 100 - jam - slow;

            /* 5. 打包成 8bit congestionFlags */
            byte congestionFlags = (byte) (
                    (((smooth / 10) & 0x03) << 6) |
                            (((slow  / 10) & 0x03) << 4) |
                            ((jam   / 10) & 0x0F));

            /* 6. 填充结构体 */
//            overview.setTotalDistance((short) Math.min(totalDistance, 0xFFFF));
            overview.setTotalDistance((short) Math.min(leftDistance, 0xFFFF)); // 使用剩余距离
            overview.setTollDistancePct((byte) tollPct);
            overview.setCongestionFlags(congestionFlags);
//            overview.setEstimatedTime((short) Math.min(durationMin, 0xFFFF));
            overview.setEstimatedTime((short) Math.min(leftTimeMinutes, 0xFFFF)); // 使用剩余时间
            overview.setTotalFee((short) Math.min(feeDeci, 0xFFFF));

//            Log.d(TAG, String.format("构建路线概览: 距离=%.1fkm, 时间=%dmin, 费用=%.1f元",
//                    totalDistance / 1000.0, durationMin, feeDeci / 10.0));
            Log.d(TAG, String.format("构建路线概览: 总距离=%.1fkm, 已行驶=%.1fkm, 剩余距离=%.1fkm, 剩余时间=%dmin, 费用=%.1f元",
                    totalDistance / 1000.0, passedDistance / 1000.0, leftDistance / 1000.0,
                    leftTimeMinutes, feeDeci / 10.0));

        } catch (Exception e) {
            Log.e(TAG, "构建路线概览失败", e);
            return createDefaultRouteOverview();
        }

        return overview;
    }

    /**
     * 从位置信息计算速度
     */
    private double calculateSpeedFromLocation(TencentLocation location) {
        // 这里简化实现，实际应该基于连续的位置变化计算速度
        // 可以使用LocationManager中的速度计算逻辑
        if (location != null) {
            float speed = location.getSpeed(); // m/s
            return Math.max(0.5, Math.min(speed, 35.0));
        }
        return 0.5; // 默认最低速度
    }

    /**
     * 使用haversine公式计算两点之间的距离（米）
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // 地球半径，单位：米

        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(radLat1) * Math.cos(radLat2) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * 创建默认的交通事件数据
     */
    private TrafficEvent createDefaultTrafficEvent() {
        TrafficEvent event = new TrafficEvent();
        event.setEventCount((byte) 0);
        event.setEventSummary((byte) 0);
        event.setNearestDistance((short) 0);
        event.setNearestType((byte) 0);
        event.setNearestDelay((byte) 0);
        event.setSevereCount((byte) 0);
        event.setAccidentCount((byte) 0);
        return event;
    }

    /**
     * 创建默认的交通灯数据
     */
    private TrafficLight createDefaultTrafficLight() {
        TrafficLight light = new TrafficLight();
        light.setNextLightId((byte) 0);
        light.setStateFlags((byte) 0);
//        light.setPositionIndex((short) 0);
        light.setDistanceToNextLight((short) 0);
        light.setRemainingTime((byte) 0);
        light.setDistanceToLight((byte) 0);
        light.setSpeed((byte) 0);
        light.setLightCount((byte) 0);
        return light;
    }

    /**
     * 创建默认的路线概览数据
     */
    private RouteOverview createDefaultRouteOverview() {
        RouteOverview overview = new RouteOverview();
        overview.setTotalDistance((short) 0);
        overview.setTollDistancePct((byte) 0);
        overview.setCongestionFlags((byte) 0);
        overview.setEstimatedTime((short) 0);
        overview.setTotalFee((short) 0);
        return overview;
    }

    /**
     * 检查是否偏离路线
     */
    public boolean isRouteDeviated(TencentLocation currentLocation, NavDriveRoute currentRoute) {
        if (currentRoute == null || currentLocation == null) {
            return false;
        }

        try {
            // 获取路线的路径点集合
            List<LatLng> routePoints = currentRoute.getRoutePoints();
            if (routePoints == null || routePoints.isEmpty()) {
                return false;
            }

            double minDistance = Double.MAX_VALUE;
            double currentLat = currentLocation.getLatitude();
            double currentLon = currentLocation.getLongitude();

            // 遍历路线上的每个点，计算与当前位置的最短距离
            for (LatLng routePoint : routePoints) {
                double distance = calculateHaversineDistance(
                        currentLat, currentLon,
                        routePoint.latitude, routePoint.longitude);
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }

            // 检查最小距离是否超过偏离阈值
            boolean isDeviated = minDistance > ROUTE_DEVIATION_THRESHOLD;
            if (isDeviated) {
                Log.w(TAG, String.format("路线偏离检测: 最短距离=%.2f米, 阈值=%.2f米",
                        minDistance, ROUTE_DEVIATION_THRESHOLD));
            }

            return isDeviated;

        } catch (Exception e) {
            Log.e(TAG, "路线偏离检测异常", e);
            return false;
        }
    }
}