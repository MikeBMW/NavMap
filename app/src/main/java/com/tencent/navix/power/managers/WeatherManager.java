package com.tencent.navix.power.managers;

import android.content.Context;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tencent.navix.power.models.Weather;
import com.tencent.navix.utils.TencentWeather;
import com.tencent.navix.api.model.NavDriveRoute;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 天气管理模块
 * 负责天气数据的获取、解析、缓存和更新
 */
public class WeatherManager {
    private static final String TAG = "WeatherManager";

    private final Context context;
    private WeatherUpdateListener weatherListener;

    // 天气数据缓存
    private String currentTemperature = "25";   // 默认温度
    private String currentDescription = "晴";   // 默认天气
    private String currentIconCode = "00";      // 默认图标代码

    // 天气代码映射表
    private static final Map<String, Integer> WEATHER_CODE_MAP = new HashMap<>();
    static {
        WEATHER_CODE_MAP.put("晴", 0);
        WEATHER_CODE_MAP.put("多云", 1);
        WEATHER_CODE_MAP.put("阴", 2);
        WEATHER_CODE_MAP.put("小雨", 3);
        WEATHER_CODE_MAP.put("中雨", 4);
        WEATHER_CODE_MAP.put("大雨", 5);
        WEATHER_CODE_MAP.put("暴雨", 6);
        WEATHER_CODE_MAP.put("雪", 7);
        WEATHER_CODE_MAP.put("雾", 8);
        WEATHER_CODE_MAP.put("霾", 9);
        WEATHER_CODE_MAP.put("雷阵雨", 10);
        WEATHER_CODE_MAP.put("雨夹雪", 11);
        // 可以继续添加更多天气类型
    }

    // 定时更新
    private ScheduledExecutorService weatherUpdateScheduler;
    private static final long WEATHER_UPDATE_INTERVAL = 10 * 60 * 1000; // 10分钟更新一次

    // 当前坐标（用于天气查询）
    private double currentLatitude = Double.NaN;
    private double currentLongitude = Double.NaN;

    public WeatherManager(Context context) {
        this.context = context.getApplicationContext();
        initializeWeatherScheduler();
        Log.d(TAG, "天气管理器初始化完成");
    }

    /**
     * 初始化天气更新调度器
     */
    private void initializeWeatherScheduler() {
        weatherUpdateScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "WeatherUpdateThread");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 设置天气更新监听器
     */
    public void setWeatherListener(WeatherUpdateListener listener) {
        this.weatherListener = listener;
    }

    /**
     * 开始天气更新服务
     */
    public void startWeatherUpdates() {
        Log.d(TAG, "开始天气更新服务");

        // 如果已经有有效坐标，立即获取一次天气
        if (!Double.isNaN(currentLatitude) && !Double.isNaN(currentLongitude)) {
            fetchWeatherData(currentLatitude, currentLongitude);
        }

        // 启动定时更新（10分钟一次）
        weatherUpdateScheduler.scheduleAtFixedRate(() -> {
            if (!Double.isNaN(currentLatitude) && !Double.isNaN(currentLongitude)) {
                fetchWeatherData(currentLatitude, currentLongitude);
            }
        }, WEATHER_UPDATE_INTERVAL, WEATHER_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * 更新位置并获取天气
     */
    public void updateLocation(double latitude, double longitude) {
        // 检查位置是否发生显著变化（超过1公里）
        if (isLocationSignificantlyChanged(latitude, longitude)) {
            this.currentLatitude = latitude;
            this.currentLongitude = longitude;

            Log.d(TAG, String.format("位置更新，获取新位置天气: %.6f, %.6f", latitude, longitude));
            fetchWeatherData(latitude, longitude);
        }
    }

    /**
     * 检查位置是否发生显著变化
     */
    private boolean isLocationSignificantlyChanged(double newLat, double newLon) {
        if (Double.isNaN(currentLatitude) || Double.isNaN(currentLongitude)) {
            return true; // 第一次获取位置
        }

        double distance = calculateHaversineDistance(currentLatitude, currentLongitude, newLat, newLon);
        return distance > 1000; // 超过1公里认为位置显著变化
    }

    /**
     * 主动获取天气数据
     */
    public void fetchWeatherData(double latitude, double longitude) {
        Log.d(TAG, "获取天气数据: " + latitude + ", " + longitude);

        TencentWeather.getNow(latitude, longitude, new TencentWeather.WeatherCallback() {
            @Override
            public void onSuccess(String json) {
                Log.d(TAG, "天气数据获取成功");
                parseAndUpdateWeatherData(json);
            }

            @Override
            public void onFail(String msg) {
                Log.e(TAG, "天气数据获取失败: " + msg);
                handleWeatherError("获取失败: " + msg);
            }
        });
    }

    /**
     * 解析并更新天气数据
     */
    private void parseAndUpdateWeatherData(String json) {
        try {
            Log.d(TAG, "原始天气JSON: " + json);

            JSONObject root = JSON.parseObject(json);
            if (root == null) {
                throw new Exception("JSON解析失败: 根对象为空");
            }

            JSONObject result = root.getJSONObject("result");
            if (result == null) {
                throw new Exception("JSON解析失败: result对象为空");
            }

            JSONObject realtime = result.getJSONArray("realtime").getJSONObject(0);
            if (realtime == null) {
                throw new Exception("JSON解析失败: realtime对象为空");
            }

            JSONObject infos = realtime.getJSONObject("infos");
            if (infos == null) {
                throw new Exception("JSON解析失败: infos对象为空");
            }

            // 提取天气信息
            String temp = String.valueOf(infos.getIntValue("temperature")); // 温度
            String desc = infos.getString("weather");                       // 天气描述
            String icon = infos.getString("weatherCode");                   // 天气图标代码（如果有）

            // 验证数据有效性
            if (temp == null || desc == null) {
                throw new Exception("天气数据不完整");
            }

            // 更新缓存
            currentTemperature = temp;
            currentDescription = desc;
            currentIconCode = icon != null ? icon : "00";

            Log.d(TAG, String.format("天气数据更新: 温度=%s°C, 描述=%s, 图标=%s",
                    temp, desc, currentIconCode));

            // 通知监听器
            if (weatherListener != null) {
                weatherListener.onWeatherUpdated(currentTemperature, currentDescription, currentIconCode);
            }

        } catch (Exception e) {
            Log.e(TAG, "解析天气数据失败", e);
            handleWeatherError("解析失败: " + e.getMessage());
        }
    }

    /**
     * 处理天气获取错误
     */
    private void handleWeatherError(String error) {
        Log.w(TAG, "天气获取失败，使用默认数据: " + error);
        // 保持当前缓存数据不变，或者使用默认值
        // 这里我们选择不更新缓存，保持上次成功获取的数据

        if (weatherListener != null) {
            weatherListener.onWeatherError(error);
        }
    }

    /**
     * 构建Weather数据结构
     */
    public Weather buildWeather(NavDriveRoute currentRoute) {
        Weather weather = new Weather();

        try {
            /* 1. routeHash - 从路线ID计算哈希 */
            int routeHash = 0;
            if (currentRoute != null && currentRoute.getRouteId() != null) {
                try {
                    routeHash = Integer.parseInt(currentRoute.getRouteId()) & 0x7F;
                } catch (NumberFormatException e) {
                    // 如果RouteId不是数字，使用字符串哈希
                    routeHash = Math.abs(currentRoute.getRouteId().hashCode()) & 0x7F;
                }
            }
            weather.setRouteHash((byte) routeHash);

            /* 2. dataValid - 数据有效性标志 */
            weather.setDataValid((byte) 1);

            /* 3. weatherCode - 天气代码（4bit）*/
            Integer weatherCode = WEATHER_CODE_MAP.get(currentDescription);
            if (weatherCode == null) {
                // 如果没有精确匹配，尝试模糊匹配
                weatherCode = findBestMatchingWeatherCode(currentDescription);
            }
            weather.setWeatherCode((byte) (weatherCode & 0xF));

            /* 4. tempConfidence - 温度置信度（4bit）*/
            weather.setTempConfidence((byte) 15); // 0~15，15表示高置信度

            /* 5. realTemperature - 实际温度（8bit，-40~50 直接存）*/
            int tempInt;
            try {
                tempInt = Integer.parseInt(currentTemperature);
            } catch (NumberFormatException e) {
                tempInt = 25; // 默认温度
            }
            // 限制温度范围并转换为0~90（对应-40~50°C）
            tempInt = Math.max(-40, Math.min(50, tempInt));
            weather.setRealTemperature((byte) (tempInt + 40));

            /* 6. 降水等级、预警类型、预警等级 */
            weather.setPrecipLevel((byte) calculatePrecipitationLevel(currentDescription));
            weather.setWarnType((byte) 0);    // 默认无预警
            weather.setWarnLevel((byte) 0);   // 默认无预警

            /* 7. 路线距离和关键点 */
            int totalDistance = 0;
            if (currentRoute != null) {
                totalDistance = Math.min(currentRoute.getDistance(), 0xFFFF);
            }
            weather.setTotalDistance((short) totalDistance);
            weather.setKeyPoints((short) 0); // 默认无关键点

            Log.d(TAG, String.format("构建Weather: 温度=%d°C, 天气码=%d, 距离=%dm",
                    tempInt, weatherCode, totalDistance));

        } catch (Exception e) {
            Log.e(TAG, "构建Weather失败", e);
            // 返回默认的Weather对象
            return createDefaultWeather();
        }

        return weather;
    }

    /**
     * 查找最佳匹配的天气代码
     */
    private int findBestMatchingWeatherCode(String description) {
        if (description == null) return 0;

        String descLower = description.toLowerCase();

        if (descLower.contains("晴")) return 0;
        if (descLower.contains("多云")) return 1;
        if (descLower.contains("阴")) return 2;
        if (descLower.contains("小雨")) return 3;
        if (descLower.contains("中雨")) return 4;
        if (descLower.contains("大雨") || descLower.contains("暴雨")) return 5;
        if (descLower.contains("雪")) return 7;
        if (descLower.contains("雾")) return 8;
        if (descLower.contains("霾")) return 9;
        if (descLower.contains("雷")) return 10;

        return 0; // 默认晴天
    }

    /**
     * 计算降水等级
     */
    private int calculatePrecipitationLevel(String description) {
        if (description == null) return 0;

        String descLower = description.toLowerCase();

        if (descLower.contains("暴雨")) return 3;
        if (descLower.contains("大雨")) return 2;
        if (descLower.contains("中雨")) return 1;
        if (descLower.contains("小雨")) return 1;
        if (descLower.contains("雪")) return 1;

        return 0; // 无降水
    }

    /**
     * 创建默认的Weather对象
     */
    private Weather createDefaultWeather() {
        Weather weather = new Weather();
        weather.setRouteHash((byte) 0);
        weather.setDataValid((byte) 1);
        weather.setWeatherCode((byte) 0);
        weather.setTempConfidence((byte) 15);
        weather.setRealTemperature((byte) (25 + 40)); // 25°C
        weather.setPrecipLevel((byte) 0);
        weather.setWarnType((byte) 0);
        weather.setWarnLevel((byte) 0);
        weather.setTotalDistance((short) 0);
        weather.setKeyPoints((short) 0);
        return weather;
    }

    /**
     * 获取当前温度
     */
    public String getCurrentTemperature() {
        return currentTemperature;
    }

    /**
     * 获取当前天气描述
     */
    public String getCurrentDescription() {
        return currentDescription;
    }

    /**
     * 获取当前天气图标代码
     */
    public String getCurrentIconCode() {
        return currentIconCode;
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
        if (weatherUpdateScheduler != null) {
            weatherUpdateScheduler.shutdown();
            try {
                if (!weatherUpdateScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    weatherUpdateScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                weatherUpdateScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        Log.d(TAG, "天气管理器资源已释放");
    }

    /**
     * 天气更新监听接口
     */
    public interface WeatherUpdateListener {
        void onWeatherUpdated(String temperature, String description, String iconCode);
        void onWeatherError(String error);
    }
}