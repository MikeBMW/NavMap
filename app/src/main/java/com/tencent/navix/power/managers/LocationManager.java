package com.tencent.navix.power.managers;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.util.Pair;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
import com.tencent.navix.power.interfaces.LocationListener;
import com.tencent.navix.power.models.LatLng;

import java.util.List;

/**
 * 定位管理模块
 * 负责腾讯定位SDK的初始化、位置监听、速度计算等功能
 */
public class LocationManager implements TencentLocationListener {
    private static final String TAG = "LocationManager";

    private final Context context;
    private final LocationListener locationListener;

    // 腾讯定位相关
    private TencentLocationManager locationManager;
    private TencentLocationRequest locationRequest;
    private TencentLocation currentLocation;

    // 速度计算相关
    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private long lastTime = 0;

    // 状态标志
    private boolean isLocationStarted = false;

    public LocationManager(Context context, LocationListener listener) {
        this.context = context.getApplicationContext();
        this.locationListener = listener;
        initializeLocation();
    }

    /**
     * 初始化定位服务
     */
    private void initializeLocation() {
        try {
            // 步骤 1：获取 AndroidManifest.xml 中配置的 TencentMapSDK Key
            String sdkKey = "";
            try {
                ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                        context.getPackageName(), PackageManager.GET_META_DATA);
                if (appInfo.metaData != null) {
                    sdkKey = appInfo.metaData.getString("TencentMapSDK");
                    Log.d(TAG, "获取到腾讯地图SDK Key: " + (sdkKey != null ? "成功" : "为空"));
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "获取应用信息失败", e);
            }

            // 步骤 2：构造 Pair 对象
            Pair<String, String> keyPair = new Pair<>(sdkKey, "");

            // 步骤 3：初始化 TencentLocationManager
            locationManager = TencentLocationManager.getInstance(context, keyPair);

            // 步骤 4：创建定位请求
            locationRequest = TencentLocationRequest.create();
            locationRequest.setInterval(5000); // 设置定位间隔，单位毫秒
            locationRequest.setRequestLevel(TencentLocationRequest.REQUEST_LEVEL_ADMIN_AREA);

            Log.d(TAG, "定位管理器初始化完成");

        } catch (Exception e) {
            Log.e(TAG, "定位管理器初始化失败", e);
            if (locationListener != null) {
                locationListener.onLocationError(-1, "定位初始化失败: " + e.getMessage());
            }
        }
    }

    /**
     * 开始定位更新
     */
    public void startLocationUpdates() {
        if (locationManager == null) {
            Log.e(TAG, "定位管理器未初始化");
            return;
        }

        if (isLocationStarted) {
            Log.w(TAG, "定位更新已启动，无需重复启动");
            return;
        }

        try {
            // 发起定位请求
            int result = locationManager.requestLocationUpdates(locationRequest, this, 0);

            if (result == 0) {
                isLocationStarted = true;
                Log.d(TAG, "✅ 定位更新启动成功");

                // 获取最后已知位置
                TencentLocation lastKnownLocation = locationManager.getLastKnownLocation();
                if (lastKnownLocation != null) {
                    Log.d(TAG, "获取到最后已知位置: " +
                            lastKnownLocation.getLatitude() + ", " + lastKnownLocation.getLongitude());
                    currentLocation = lastKnownLocation;
                }
            } else {
                Log.e(TAG, "❌ 定位更新启动失败，错误码: " + result);
                if (locationListener != null) {
                    locationListener.onLocationError(result, "定位启动失败，错误码: " + result);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "启动定位更新异常", e);
            if (locationListener != null) {
                locationListener.onLocationError(-1, "定位启动异常: " + e.getMessage());
            }
        }
    }

    /**
     * 停止定位更新
     */
    public void stopLocationUpdates() {
        if (locationManager != null && isLocationStarted) {
            locationManager.removeUpdates(this);
            isLocationStarted = false;
            Log.d(TAG, "定位更新已停止");
        }

        // 重置速度计算缓存
        resetSpeedCalculation();
    }

    /**
     * 重新启动定位更新（用于导航开始后重新注册）
     */
    public void restartLocationUpdates() {
        Log.d(TAG, "重新启动定位更新");
        stopLocationUpdates();

        // 延迟重新启动，确保导航SDK完全初始化
        new android.os.Handler().postDelayed(this::startLocationUpdates, 1000);
    }

    /**
     * 导航模式下的位置监听重新注册
     * 在导航启动后调用，确保位置更新不被导航SDK覆盖
     */
    public void reRegisterForNavigationMode() {
        Log.d(TAG, "导航模式下重新注册位置监听");

        try {
            // 先完全停止位置更新
            if (isLocationStarted) {
                locationManager.removeUpdates(this);
                isLocationStarted = false;
                Log.d(TAG, "已停止原有位置监听");
            }

            // 等待导航SDK完全初始化
            new android.os.Handler().postDelayed(() -> {
                try {
                    // 重新创建定位请求，使用更适合导航的参数
                    TencentLocationRequest navLocationRequest = TencentLocationRequest.create();
                    navLocationRequest.setInterval(1000); // 导航模式下更频繁的更新
                    navLocationRequest.setRequestLevel(TencentLocationRequest.REQUEST_LEVEL_POI);
                    navLocationRequest.setAllowDirection(true); // 允许获取方向

                    // 重新注册位置监听
                    int result = locationManager.requestLocationUpdates(navLocationRequest, this, 0);

                    if (result == 0) {
                        isLocationStarted = true;
                        Log.d(TAG, "✅ 导航模式下位置监听重新注册成功");

                        // 立即获取当前位置
                        TencentLocation currentLoc = locationManager.getLastKnownLocation();
                        if (currentLoc != null) {
                            Log.d(TAG, "📌 重新注册后当前位置: " +
                                    currentLoc.getLatitude() + ", " + currentLoc.getLongitude());
                            onLocationChanged(currentLoc, 0, "success");
                        }
                    } else {
                        Log.e(TAG, "❌ 导航模式下位置监听注册失败，错误码: " + result);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "❌ 导航模式下位置监听注册异常", e);
                }
            }, 2000); // 延迟2秒，确保导航SDK完全初始化

        } catch (Exception e) {
            Log.e(TAG, "❌ 导航模式重新注册异常", e);
        }
    }

    /**
     * 检查位置监听器是否活跃
     */
    public boolean isLocationUpdatesActive() {
        return isLocationStarted && locationManager != null;
    }

    /**
     * 获取位置监听状态信息
     */
    public String getLocationStatus() {
        if (!isLocationStarted) {
            return "位置监听未启动";
        }

        if (currentLocation != null) {
            return String.format("监听活跃 (%.6f, %.6f)",
                    currentLocation.getLatitude(), currentLocation.getLongitude());
        } else {
            return "监听活跃，等待位置数据";
        }
    }

    /**
     * 检查是否有有效的当前位置
     */
    public boolean hasValidLocation() {
        return currentLocation != null;
    }

    /**
     * 获取当前位置
     */
    public TencentLocation getCurrentLocation() {
        return currentLocation;
    }

    /**
     * 获取当前位置的经纬度
     */
    public double[] getCurrentCoordinates() {
        if (currentLocation != null) {
            return new double[] {
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude()
            };
        }
        return null;
    }

    // ========== TencentLocationListener 接口实现 ==========

    @Override
    public void onLocationChanged(TencentLocation location, int errorCode, String errorMsg) {
        Log.d(TAG, "定位回调：errorCode=" + errorCode + ", errorMsg=" + errorMsg);

        if (errorCode == 0) {
            // 定位成功
            currentLocation = location;

            // 计算实时车速
            double speedMps = calculateSpeedFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getTime());

            // 回调给监听器
            if (locationListener != null) {
                locationListener.onLocationUpdate(location);
                locationListener.onSpeedCalculate(speedMps);
            }

            Log.d(TAG, String.format("位置更新: %.6f, %.6f, 速度: %.2f m/s",
                    location.getLatitude(), location.getLongitude(), speedMps));

        } else {
            // 定位失败
            Log.e(TAG, "定位失败：errorCode=" + errorCode + ", errorMsg=" + errorMsg);
            if (locationListener != null) {
                locationListener.onLocationError(errorCode, errorMsg);
            }
        }
    }

    @Override
    public void onStatusUpdate(String name, int status, String desc) {
        Log.d(TAG, "定位状态更新: " + name + ", status=" + status + ", desc=" + desc);
        // 可以处理定位状态变化，比如GPS开关状态等
    }

    @Override
    public void onNmeaMsgChanged(String nmea) {
        // NMEA是卫星定位的原始数据协议，暂时用不到可忽略
    }

    @Override
    public void onGnssInfoChanged(Object gnssInfo) {
        // GNSS信息变化，可以处理卫星状态等信息
        // 如果需要处理，可根据官方文档解析gnssInfo对象
    }

    // ========== 速度计算相关方法 ==========

    /**
     * 根据两次经纬度变化计算车速（m/s）
     * 第一次调用返回 0，之后返回实时速度
     */
    private double calculateSpeedFromLocation(double lat, double lon, long timeMs) {
        if (Double.isNaN(lastLat)) {
            // 第一次调用，初始化缓存
            lastLat = lat;
            lastLon = lon;
            lastTime = timeMs;
            return 0.0;
        }

        // 计算距离（米）和时间（秒）
        double distanceM = calculateHaversineDistance(lastLat, lastLon, lat, lon);
        double timeS = (timeMs - lastTime) / 1000.0;
        double speed = (timeS > 0) ? (distanceM / timeS) : 0.0;

        // 更新缓存
        lastLat = lat;
        lastLon = lon;
        lastTime = timeMs;

        // 限速过滤：0.5 ~ 35 m/s（约 0-126 km/h）
        return Math.max(0.5, Math.min(speed, 35.0));
    }

    /**
     * 重置速度计算缓存
     */
    private void resetSpeedCalculation() {
        lastLat = Double.NaN;
        lastLon = Double.NaN;
        lastTime = 0;
        Log.d(TAG, "速度计算缓存已重置");
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
     * 计算两点之间的直线距离（米）
     * 公开方法，供外部调用
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        return calculateHaversineDistance(lat1, lon1, lat2, lon2);
    }

    /**
     * 检查是否偏离路线
     */
    public boolean isOffRoute(double currentLat, double currentLon,
                              List<LatLng> routePoints, double thresholdMeters) {
        if (routePoints == null || routePoints.isEmpty()) {
            return false;
        }

        double minDistance = Double.MAX_VALUE;

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
        return minDistance > thresholdMeters;
    }

    /**
     * 释放资源
     */
    public void release() {
        stopLocationUpdates();
        locationManager = null;
        locationRequest = null;
        currentLocation = null;
        Log.d(TAG, "定位管理器资源已释放");
    }
}