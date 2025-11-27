package com.tencent.navix.power.managers;

import android.content.Context;
import android.util.Log;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.navix.api.model.NavDriveDataInfoEx;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.api.navigator.NavigatorDrive;
import com.tencent.navix.power.interfaces.NavigationListener;
import com.tencent.navix.utils.TencentGeoCoder;
import com.tencent.tencentmap.mapsdk.maps.model.LatLng;

import java.util.List;

/**
 * 导航管理模块
 * 负责路线规划、导航控制、路线数据管理、偏离检测和重新规划
 */
public class NavigationManager {
    private static final String TAG = "NavigationManager";

    private final Context context;
    private final NavigationListener navigationListener;
    private final NavigatorDrive navigatorDrive;

    // 导航状态
    private boolean isNavigating = false;
    private NavDriveRoute currentRoute;
    private String currentDestination;

    // 路线偏离检测
    private static final double ROUTE_DEVIATION_THRESHOLD = 50.0; // 偏离阈值50米
    private boolean isReplanning = false; // 防止重复重新规划

    // 当前位置信息 - 通过外部设置
    private double currentLatitude = Double.NaN;
    private double currentLongitude = Double.NaN;

    public NavigationManager(Context context, NavigationListener listener, NavigatorDrive navigator) {
        this.context = context.getApplicationContext();
        this.navigationListener = listener;
        this.navigatorDrive = navigator;
        Log.d(TAG, "导航管理器初始化完成");
    }

    /**
     * 根据地址开始导航
     */
    public void startNavigationToAddress(String destinationAddress) {
        if (destinationAddress == null || destinationAddress.trim().isEmpty()) {
            Log.e(TAG, "目的地地址为空");
            if (navigationListener != null) {
                navigationListener.onRoutePlanFailed("目的地地址为空");
            }
            return;
        }

        currentDestination = destinationAddress.trim();
        Log.d(TAG, "开始导航到: " + currentDestination);

        // 先解析目的地地址
        parseDestinationAddress(currentDestination, new GeocodeCallback() {
            @Override
            public void onSuccess(double destLat, double destLng, String title) {
                Log.d(TAG, "目的地解析成功: " + destLat + ", " + destLng);
                // 目的地解析成功后，开始路线规划
                startRoutePlanning(destLat, destLng);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "目的地解析失败: " + error);
                if (navigationListener != null) {
                    navigationListener.onRoutePlanFailed("解析目的地失败: " + error);
                }
            }
        });
    }

    /**
     * 根据经纬度开始导航
     */
    public void startNavigationToCoordinates(double lat, double lng) {
        Log.d(TAG, "开始导航到坐标: " + lat + ", " + lng);
        startRoutePlanning(lat, lng);
    }

    /**
     * 解析目的地地址
     */
    private void parseDestinationAddress(String address, GeocodeCallback callback) {
        TencentGeoCoder.geoCode(context, address, new TencentGeoCoder.GeoListener() {
            @Override
            public void onSuccess(double lat, double lng, String title) {
                Log.d(TAG, "地址解析成功: " + title + " -> " + lat + ", " + lng);
                callback.onSuccess(lat, lng, title);
            }

            @Override
            public void onError(String msg) {
                Log.e(TAG, "地址解析失败: " + msg);
                callback.onError(msg);
            }
        });
    }

    /**
     * 开始路线规划 - 使用外部设置的当前位置
     */
    private void startRoutePlanning(double destLat, double destLng) {
        Log.d(TAG, "开始路线规划到: " + destLat + ", " + destLng);

        // 检查当前位置是否可用
        if (!isCurrentLocationValid()) {
            Log.e(TAG, "当前位置无效，无法进行路线规划");
            if (navigationListener != null) {
                navigationListener.onRoutePlanFailed("当前位置无效，请确保定位已开启");
            }
            return;
        }

        // 停止当前导航（如果正在导航）
        stopCurrentNavigation();

        // 使用真实位置创建起点和终点
        NavSearchPoint startPoint = new NavSearchPoint(currentLatitude, currentLongitude);
        NavSearchPoint endPoint = new NavSearchPoint(destLat, destLng);

        Log.d(TAG, "路线规划起点: " + currentLatitude + ", " + currentLongitude +
                " -> 终点: " + destLat + ", " + destLng);

        // 构建路线规划请求
        RoutePlanRequester request = RoutePlanRequester.Companion.newBuilder(
                NavRouteReqParam.TravelMode.TravelModeDriving)
                .start(startPoint)
                .end(endPoint)
                .build();

        // 发起路线规划请求
        navigatorDrive.searchRoute(request, (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
            if (error != null) {
                Log.e(TAG, "路线规划失败: " + error.getMessage());
                if (navigationListener != null) {
                    navigationListener.onRoutePlanFailed(error.getMessage());
                }
                return;
            }

            if (navRoutePlan != null) {
                List<NavDriveRoute> routePlanList = navRoutePlan.getRouteDatas();
                if (routePlanList != null && routePlanList.size() > 0) {
                    // 选择第一条路线
                    currentRoute = routePlanList.get(0);
                    Log.d(TAG, "路线规划成功，路线ID: " + currentRoute.getRouteId() +
                            ", 距离: " + currentRoute.getDistance() + "米, 时间: " + currentRoute.getTime() + "分钟");

                    if (navigationListener != null) {
                        navigationListener.onRoutePlanSuccess(currentRoute);
                    }

                    // 路线规划成功后自动开始导航
                    startNavigation();

                } else {
                    Log.e(TAG, "未获取到有效路线");
                    if (navigationListener != null) {
                        navigationListener.onRoutePlanFailed("未获取到有效路线");
                    }
                }
            } else {
                Log.e(TAG, "路线规划返回空结果");
                if (navigationListener != null) {
                    navigationListener.onRoutePlanFailed("路线规划返回空结果");
                }
            }
        });
    }

    /**
     * 开始导航（在路线规划成功后调用）
     */
    public void startNavigation() {
        if (currentRoute == null) {
            Log.e(TAG, "没有可用路线，无法开始导航");
            return;
        }

        try {
            String routeId = currentRoute.getRouteId();
            Log.d(TAG, "开始导航，路线ID: " + routeId);

            navigatorDrive.startNavigation(routeId);
            isNavigating = true;

            if (navigationListener != null) {
                navigationListener.onNavigationStarted();
            }

            Log.d(TAG, "✅ 导航启动成功");

        } catch (Exception e) {
            Log.e(TAG, "导航启动失败", e);
            if (navigationListener != null) {
                navigationListener.onRoutePlanFailed("导航启动失败: " + e.getMessage());
            }
        }
    }

    /**
     * 停止当前导航
     */
    public void stopCurrentNavigation() {
        if (navigatorDrive != null && isNavigating) {
            try {
                navigatorDrive.stopNavigation();
                isNavigating = false;
                currentRoute = null;

                Log.d(TAG, "导航已停止");

                if (navigationListener != null) {
                    navigationListener.onNavigationStopped();
                }

            } catch (Exception e) {
                Log.e(TAG, "停止导航失败", e);
            }
        }
    }

    /**
     * 设置当前位置 - 从外部Activity调用
     */
    public void setCurrentLocation(double latitude, double longitude) {
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
        Log.d(TAG, "设置当前位置: " + latitude + ", " + longitude);
    }

    /**
     * 设置当前位置 - 从外部Activity调用（使用TencentLocation）
     */
    public void setCurrentLocation(TencentLocation location) {
        if (location != null) {
            this.currentLatitude = location.getLatitude();
            this.currentLongitude = location.getLongitude();
            Log.d(TAG, "设置当前位置(TencentLocation): " + currentLatitude + ", " + currentLongitude);
        }
    }

    /**
     * 检查当前位置是否有效
     */
    private boolean isCurrentLocationValid() {
        return !Double.isNaN(currentLatitude) && !Double.isNaN(currentLongitude) &&
                currentLatitude != 0.0 && currentLongitude != 0.0;
    }

    /**
     * 处理位置更新 - 用于路线偏离检测
     */
    public void onLocationUpdate(TencentLocation location) {
        if (!isNavigating || currentRoute == null || location == null) {
            return;
        }

        // 更新当前位置
        setCurrentLocation(location);

        // 更新路线实时信息
        updateRouteRealTimeInfo(location);

        // 检查是否偏离路线
        if (isRouteDeviated(location)) {
            Log.w(TAG, "检测到路线偏离，当前位置与路线最短距离超过阈值");

            if (!isReplanning && navigationListener != null) {
                isReplanning = true;
                navigationListener.onRouteDeviationDetected();

                // 触发重新规划
                replanRouteFromCurrentLocation(location);
            }
        }
    }

    /**
     * 更新路线实时信息
     */
    private void updateRouteRealTimeInfo(TencentLocation location) {
        if (navigatorDrive == null || currentRoute == null) {
            return;
        }

        try {
            NavDriveDataInfoEx info = navigatorDrive.getNavRouteDataInfo();
            if (info == null) {
                return;
            }

            // 计算剩余距离和剩余时间
            int passedDistance = info.getPassedDistance();
            int totalDistance = currentRoute.getDistance();
            int leftDistance = Math.max(0, totalDistance - passedDistance);

            int passedTime = info.getPassedTime(); // 已行驶时间（秒）
            int totalTime = currentRoute.getTime(); // 总时间（分钟）
            int leftTime = Math.max(0, totalTime - passedTime / 60); // 剩余时间（分钟）

            Log.d(TAG, String.format("路线进度: 已行驶%d米/总%d米, 剩余%d分钟",
                    passedDistance, totalDistance, leftTime));

        } catch (Exception e) {
            Log.e(TAG, "更新路线实时信息异常", e);
        }
    }

    /**
     * 检查是否偏离路线
     */
    private boolean isRouteDeviated(TencentLocation location) {
        if (currentRoute == null) {
            return false;
        }

        try {
            // 获取路线的路径点集合
            List<LatLng> routePoints = currentRoute.getRoutePoints();
            if (routePoints == null || routePoints.isEmpty()) {
                return false;
            }

            double minDistance = Double.MAX_VALUE;
            double currentLat = location.getLatitude();
            double currentLon = location.getLongitude();

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

    /**
     * 从当前位置重新规划路线
     */
    private void replanRouteFromCurrentLocation(TencentLocation currentLocation) {
        if (currentDestination == null) {
            Log.e(TAG, "没有目的地，无法重新规划");
            isReplanning = false;
            return;
        }

        Log.d(TAG, "开始重新规划路线从当前位置");

        // 重新解析目的地并规划路线
        parseDestinationAddress(currentDestination, new GeocodeCallback() {
            @Override
            public void onSuccess(double destLat, double destLng, String title) {
                // 直接调用路线规划，使用最新的当前位置
                startRoutePlanning(destLat, destLng);
                isReplanning = false;
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "重新规划时目的地解析失败: " + error);
                isReplanning = false;
            }
        });
    }

    /**
     * 重新规划路线（外部调用）
     */
    public void replanRoute() {
        if (isCurrentLocationValid() && currentDestination != null) {
            parseDestinationAddress(currentDestination, new GeocodeCallback() {
                @Override
                public void onSuccess(double destLat, double destLng, String title) {
                    startRoutePlanning(destLat, destLng);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "重新规划失败: " + error);
                }
            });
        }
    }

    /**
     * 获取当前路线
     */
    public NavDriveRoute getCurrentRoute() {
        return currentRoute;
    }

    /**
     * 获取导航状态
     */
    public boolean isNavigating() {
        return isNavigating;
    }

    /**
     * 获取当前目的地
     */
    public String getCurrentDestination() {
        return currentDestination;
    }

    /**
     * 获取当前位置纬度
     */
    public double getCurrentLatitude() {
        return currentLatitude;
    }

    /**
     * 获取当前位置经度
     */
    public double getCurrentLongitude() {
        return currentLongitude;
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
     * 释放资源
     */
    public void release() {
        stopCurrentNavigation();
        currentRoute = null;
        currentDestination = null;
        currentLatitude = Double.NaN;
        currentLongitude = Double.NaN;
        Log.d(TAG, "导航管理器资源已释放");
    }

    /**
     * 地址解析回调接口
     */
    private interface GeocodeCallback {
        void onSuccess(double lat, double lng, String title);
        void onError(String error);
    }
}














//package com.tencent.navix.power.managers;
//
//import android.content.Context;
//import android.util.Log;
//
//import com.tencent.map.geolocation.TencentLocation;
//import com.tencent.navix.api.model.NavDriveDataInfoEx;
//import com.tencent.navix.api.model.NavDriveRoute;
//import com.tencent.navix.api.model.NavRouteReqParam;
//import com.tencent.navix.api.model.NavSearchPoint;
//import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
//import com.tencent.navix.api.plan.RoutePlanRequester;
//import com.tencent.navix.api.navigator.NavigatorDrive;
//import com.tencent.navix.power.interfaces.NavigationListener;
//import com.tencent.navix.utils.TencentGeoCoder;
//import com.tencent.tencentmap.mapsdk.maps.model.LatLng;
//
//import java.util.List;
//
///**
// * 导航管理模块
// * 负责路线规划、导航控制、路线数据管理、偏离检测和重新规划
// */
//public class NavigationManager {
//    private static final String TAG = "NavigationManager";
//
//    private final Context context;
//    private final NavigationListener navigationListener;
//    private final NavigatorDrive navigatorDrive;
//
//    // 导航状态
//    private boolean isNavigating = false;
//    private NavDriveRoute currentRoute;
//    private String currentDestination;
//
//    // 路线偏离检测
//    private static final double ROUTE_DEVIATION_THRESHOLD = 50.0; // 偏离阈值50米
//    private boolean isReplanning = false; // 防止重复重新规划
//
//    public NavigationManager(Context context, NavigationListener listener, NavigatorDrive navigator) {
//        this.context = context.getApplicationContext();
//        this.navigationListener = listener;
//        this.navigatorDrive = navigator;
//        Log.d(TAG, "导航管理器初始化完成");
//    }
//
//    /**
//     * 根据地址开始导航
//     */
//    public void startNavigationToAddress(String destinationAddress) {
//        if (destinationAddress == null || destinationAddress.trim().isEmpty()) {
//            Log.e(TAG, "目的地地址为空");
//            if (navigationListener != null) {
//                navigationListener.onRoutePlanFailed("目的地地址为空");
//            }
//            return;
//        }
//
//        currentDestination = destinationAddress.trim();
//        Log.d(TAG, "开始导航到: " + currentDestination);
//
//        // 先解析目的地地址
//        parseDestinationAddress(currentDestination, new GeocodeCallback() {
//            @Override
//            public void onSuccess(double destLat, double destLng, String title) {
//                Log.d(TAG, "目的地解析成功: " + destLat + ", " + destLng);
//                // 目的地解析成功后，开始路线规划
//                startRoutePlanning(destLat, destLng);
//            }
//
//            @Override
//            public void onError(String error) {
//                Log.e(TAG, "目的地解析失败: " + error);
//                if (navigationListener != null) {
//                    navigationListener.onRoutePlanFailed("解析目的地失败: " + error);
//                }
//            }
//        });
//    }
//
//    /**
//     * 根据经纬度开始导航
//     */
//    public void startNavigationToCoordinates(double lat, double lng) {
//        Log.d(TAG, "开始导航到坐标: " + lat + ", " + lng);
//        startRoutePlanning(lat, lng);
//    }
//
//    /**
//     * 解析目的地地址
//     */
//    private void parseDestinationAddress(String address, GeocodeCallback callback) {
//        TencentGeoCoder.geoCode(context, address, new TencentGeoCoder.GeoListener() {
//            @Override
//            public void onSuccess(double lat, double lng, String title) {
//                Log.d(TAG, "地址解析成功: " + title + " -> " + lat + ", " + lng);
//                callback.onSuccess(lat, lng, title);
//            }
//
//            @Override
//            public void onError(String msg) {
//                Log.e(TAG, "地址解析失败: " + msg);
//                callback.onError(msg);
//            }
//        });
//    }
//
//    /**
//     * 开始路线规划
//     */
//    private void startRoutePlanning(double destLat, double destLng) {
//        Log.d(TAG, "开始路线规划到: " + destLat + ", " + destLng);
//
//        // 停止当前导航（如果正在导航）
//        stopCurrentNavigation();
//
//        // 创建起点和终点
//        NavSearchPoint startPoint = new NavSearchPoint(0, 0); // 起点将在定位回调中更新
//        NavSearchPoint endPoint = new NavSearchPoint(destLat, destLng);
//
//        // 构建路线规划请求
//        RoutePlanRequester request = RoutePlanRequester.Companion.newBuilder(
//                NavRouteReqParam.TravelMode.TravelModeDriving)
//                .start(startPoint)
//                .end(endPoint)
//                .build();
//
//        // 发起路线规划请求
//        navigatorDrive.searchRoute(request, (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
//            if (error != null) {
//                Log.e(TAG, "路线规划失败: " + error.getMessage());
//                if (navigationListener != null) {
//                    navigationListener.onRoutePlanFailed(error.getMessage());
//                }
//                return;
//            }
//
//            if (navRoutePlan != null) {
//                List<NavDriveRoute> routePlanList = navRoutePlan.getRouteDatas();
//                if (routePlanList != null && routePlanList.size() > 0) {
//                    // 选择第一条路线
//                    currentRoute = routePlanList.get(0);
//                    Log.d(TAG, "路线规划成功，路线ID: " + currentRoute.getRouteId() +
//                            ", 距离: " + currentRoute.getDistance() + "米, 时间: " + currentRoute.getTime() + "分钟");
//
//                    if (navigationListener != null) {
//                        navigationListener.onRoutePlanSuccess(currentRoute);
//                    }
//
//                } else {
//                    Log.e(TAG, "未获取到有效路线");
//                    if (navigationListener != null) {
//                        navigationListener.onRoutePlanFailed("未获取到有效路线");
//                    }
//                }
//            } else {
//                Log.e(TAG, "路线规划返回空结果");
//                if (navigationListener != null) {
//                    navigationListener.onRoutePlanFailed("路线规划返回空结果");
//                }
//            }
//        });
//    }
//
//    /**
//     * 开始导航（在路线规划成功后调用）
//     */
//    public void startNavigation() {
//        if (currentRoute == null) {
//            Log.e(TAG, "没有可用路线，无法开始导航");
//            return;
//        }
//
//        try {
//            String routeId = currentRoute.getRouteId();
//            Log.d(TAG, "开始导航，路线ID: " + routeId);
//
//            navigatorDrive.startNavigation(routeId);
//            isNavigating = true;
//
//            if (navigationListener != null) {
//                navigationListener.onNavigationStarted();
//            }
//
//            Log.d(TAG, "✅ 导航启动成功");
//
//        } catch (Exception e) {
//            Log.e(TAG, "导航启动失败", e);
//            if (navigationListener != null) {
//                navigationListener.onRoutePlanFailed("导航启动失败: " + e.getMessage());
//            }
//        }
//    }
//
//    /**
//     * 停止当前导航
//     */
//    public void stopCurrentNavigation() {
//        if (navigatorDrive != null && isNavigating) {
//            try {
//                navigatorDrive.stopNavigation();
//                isNavigating = false;
//                currentRoute = null;
//
//                Log.d(TAG, "导航已停止");
//
//                if (navigationListener != null) {
//                    navigationListener.onNavigationStopped();
//                }
//
//            } catch (Exception e) {
//                Log.e(TAG, "停止导航失败", e);
//            }
//        }
//    }
//
//    /**
//     * 处理位置更新 - 用于路线偏离检测
//     */
//    public void onLocationUpdate(TencentLocation location) {
//        if (!isNavigating || currentRoute == null || location == null) {
//            return;
//        }
//
//        // 检查是否偏离路线
//        if (isRouteDeviated(location)) {
//            Log.w(TAG, "检测到路线偏离，当前位置与路线最短距离超过阈值");
//
//            if (!isReplanning && navigationListener != null) {
//                isReplanning = true;
//                navigationListener.onRouteDeviationDetected();
//
//                // 触发重新规划
//                replanRouteFromCurrentLocation(location);
//            }
//        } else {
//            // 更新路线数据（剩余距离、时间等）
//            updateRouteInfo(location);
//        }
//    }
//
//    /**
//     * 检查是否偏离路线
//     */
//    private boolean isRouteDeviated(TencentLocation location) {
//        if (currentRoute == null) {
//            return false;
//        }
//
//        try {
//            // 获取路线的路径点集合
//            List<LatLng> routePoints = currentRoute.getRoutePoints();
//            if (routePoints == null || routePoints.isEmpty()) {
//                return false;
//            }
//
//            double minDistance = Double.MAX_VALUE;
//            double currentLat = location.getLatitude();
//            double currentLon = location.getLongitude();
//
//            // 遍历路线上的每个点，计算与当前位置的最短距离
//            for (LatLng routePoint : routePoints) {
//                double distance = calculateHaversineDistance(
//                        currentLat, currentLon,
//                        routePoint.latitude, routePoint.longitude);
//                if (distance < minDistance) {
//                    minDistance = distance;
//                }
//            }
//
//            // 检查最小距离是否超过偏离阈值
//            boolean isDeviated = minDistance > ROUTE_DEVIATION_THRESHOLD;
//            if (isDeviated) {
//                Log.w(TAG, String.format("路线偏离检测: 最短距离=%.2f米, 阈值=%.2f米",
//                        minDistance, ROUTE_DEVIATION_THRESHOLD));
//            }
//
//            return isDeviated;
//
//        } catch (Exception e) {
//            Log.e(TAG, "路线偏离检测异常", e);
//            return false;
//        }
//    }
//
//    /**
//     * 从当前位置重新规划路线
//     */
//    private void replanRouteFromCurrentLocation(TencentLocation currentLocation) {
//        if (currentDestination == null) {
//            Log.e(TAG, "没有目的地，无法重新规划");
//            isReplanning = false;
//            return;
//        }
//
//        Log.d(TAG, "开始重新规划路线从当前位置");
//
//        // 重新解析目的地并规划路线
//        parseDestinationAddress(currentDestination, new GeocodeCallback() {
//            @Override
//            public void onSuccess(double destLat, double destLng, String title) {
//                // 使用当前位置作为新起点
//                NavSearchPoint startPoint = new NavSearchPoint(
//                        currentLocation.getLatitude(),
//                        currentLocation.getLongitude());
//                NavSearchPoint endPoint = new NavSearchPoint(destLat, destLng);
//
//                // 重新规划路线
//                replanRoute(startPoint, endPoint);
//            }
//
//            @Override
//            public void onError(String error) {
//                Log.e(TAG, "重新规划时目的地解析失败: " + error);
//                isReplanning = false;
//            }
//        });
//    }
//
//    /**
//     * 重新规划路线
//     */
//    private void replanRoute(NavSearchPoint startPoint, NavSearchPoint endPoint) {
//        RoutePlanRequester request = RoutePlanRequester.Companion.newBuilder(
//                NavRouteReqParam.TravelMode.TravelModeDriving)
//                .start(startPoint)
//                .end(endPoint)
//                .build();
//
//        navigatorDrive.searchRoute(request, (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
//            isReplanning = false; // 重置重新规划标志
//
//            if (error != null) {
//                Log.e(TAG, "重新规划路线失败: " + error.getMessage());
//                return;
//            }
//
//            if (navRoutePlan != null && navRoutePlan.getRouteDatas() != null &&
//                    navRoutePlan.getRouteDatas().size() > 0) {
//
//                // 停止当前导航
//                navigatorDrive.stopNavigation();
//
//                // 使用新路线
//                currentRoute = navRoutePlan.getRouteDatas().get(0);
//
//                // 重新开始导航
//                navigatorDrive.startNavigation(currentRoute.getRouteId());
//
//                Log.d(TAG, "✅ 路线重新规划成功，新路线ID: " + currentRoute.getRouteId());
//
//            } else {
//                Log.e(TAG, "重新规划未获取到有效路线");
//            }
//        });
//    }
//
//    /**
//     * 更新路线信息（剩余距离、时间等）
//     */
//    private void updateRouteInfo(TencentLocation location) {
//        if (navigatorDrive == null || currentRoute == null) {
//            return;
//        }
//
//        try {
//            NavDriveDataInfoEx info = navigatorDrive.getNavRouteDataInfo();
//            if (info == null) {
//                return;
//            }
//
//            // 更新当前路线的剩余信息
//            int passedDistance = info.getPassedDistance();
//            int totalDistance = info.getMainRoute().getDistance();
//            int leftDistance = Math.max(0, totalDistance - passedDistance);
//
//            int passedTime = info.getPassedTime();
//            int totalTime = info.getMainRoute().getTime();
//            int leftTime = Math.max(0, totalTime - passedTime / 60); // 转换为分钟
//
//            // 更新当前路线的剩余距离和时间
//            // 注意：这里我们通过反射或其他方式更新，因为原Route可能没有setter方法
//            // 在实际项目中，可能需要创建Route的副本或使用其他方式管理这些动态数据
//
//            Log.d(TAG, String.format("路线进度: 已行驶%d米/总%d米, 剩余%d分钟",
//                    passedDistance, totalDistance, leftTime));
//
//        } catch (Exception e) {
//            Log.e(TAG, "更新路线信息异常", e);
//        }
//    }
//
//    /**
//     * 获取当前路线
//     */
//    public NavDriveRoute getCurrentRoute() {
//        return currentRoute;
//    }
//
//    /**
//     * 获取导航状态
//     */
//    public boolean isNavigating() {
//        return isNavigating;
//    }
//
//    /**
//     * 获取当前目的地
//     */
//    public String getCurrentDestination() {
//        return currentDestination;
//    }
//
//    /**
//     * 使用haversine公式计算两点之间的距离（米）
//     */
//    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
//        final int R = 6371000; // 地球半径，单位：米
//
//        double radLat1 = Math.toRadians(lat1);
//        double radLat2 = Math.toRadians(lat2);
//        double deltaLat = Math.toRadians(lat2 - lat1);
//        double deltaLon = Math.toRadians(lon2 - lon1);
//
//        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
//                Math.cos(radLat1) * Math.cos(radLat2) *
//                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
//        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//
//        return R * c;
//    }
//
//    /**
//     * 释放资源
//     */
//    public void release() {
//        stopCurrentNavigation();
//        currentRoute = null;
//        currentDestination = null;
//        Log.d(TAG, "导航管理器资源已释放");
//    }
//
//    /**
//     * 地址解析回调接口
//     */
//    private interface GeocodeCallback {
//        void onSuccess(double lat, double lng, String title);
//        void onError(String error);
//    }
//}