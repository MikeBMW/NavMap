package com.tencent.navix.navi;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;

import com.tencent.navix.BaseNavActivity;
import com.tencent.navix.MainActivity;
import com.tencent.navix.R;
import com.tencent.navix.api.model.NavDriveDataInfoEx;
import com.tencent.navix.api.model.NavDriveRoute;

import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;

import com.tencent.navix.api.observer.NavigatorDriveObserver;
import com.tencent.navix.api.observer.SimpleNavigatorDriveObserver;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.utils.TencentGeoCoder;
import com.tencent.navix.utils.TencentWeather;
import com.tencent.tencentmap.mapsdk.maps.model.LatLng;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;


public class NavUpdateActivity extends BaseNavActivity implements TencentLocationListener {

    private static final String TAG = "NavDebug";
//    private boolean isNavigationRequested = false; // 控制是否已经请求重新导航的标志位
//    private double lastDist = Double.MAX_VALUE; // 用于记录上一次的距离
//    private long lastLightTime = 0; // 用于记录上一次检查距离的时间
//    private NavigatorDrive navigatorDrive;  // 建议显式声明  不能加，一加上就重复进入主菜单

    /* ================= 导航/发送线程 ================= */
    private volatile boolean mNavigating = false;   // 是否正在导航
    private Thread mSendThread;                     // 数据发送线程
    private final Object mLock = new Object();      // 同步锁

    /* ================= 导航数据 ================= */
    private RoutePlanRequester routePlanRequester;
    private NavDriveRoute currentRoute;

    /* ================= 定位数据 ================= */
    private TencentLocationManager locationManager;
    private TencentLocationRequest locationRequest;
    private TencentLocation currentLocation; // 保存当前定位信息
    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private long   lastTime = 0;

    /* ================= 天气数据 ================= */
    private String lastTemp = "25";   // 默认温度
    private String lastDesc = "晴";   // 默认天气

    /* ================= UI ================= */
    private EditText etDestination;
    private Button btnConfirmDestination;
    private TextView tvLocationInfo;
    private EditText etIpPort;

    /* ================= debug UI ================= */
    private View debugPanel;
    private TextView debugTitle, debugHex, debugFields;
    private boolean debugCollapsed = false;

    /* ================= weather UI ================= */
    private TextView tvTemp, tvDesc;
    private ImageView ivWeather;

    /* ================= WebSocket ================= */
    private WebSocket ws;
    private OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)   // 心跳
            .build();

    private final NavigatorDriveObserver driveObserver =
            new SimpleNavigatorDriveObserver() {
                @Override
                public void onMainRouteDidChange(String newRouteId, int reason) {
                    // SDK 官方保证，这里一定能拿到最新主路线
                    if (navigatorDrive != null) {
                        NavDriveDataInfoEx info = navigatorDrive.getNavRouteDataInfo();
                        if (info != null) {
                            currentRoute = info.getMainRoute();
                        }
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("TZMap", "===== onCreate fired =====");

        // 保持屏幕常亮
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 把输入面板“盖”在地图之上
        FrameLayout root = findViewById(R.id.app_root_view);   // 父类布局根容器
        View inputPanel = getLayoutInflater().inflate(R.layout.activity_gps_nav_loc, root, false);
        root.addView(inputPanel);

        // 再绑定控件
        tvLocationInfo = inputPanel.findViewById(R.id.tv_location_info);
        etDestination = inputPanel.findViewById(R.id.et_destination);
        etIpPort = findViewById(R.id.et_ip_port);
        btnConfirmDestination = inputPanel.findViewById(R.id.btn_confirm_destination);
        btnConfirmDestination.setOnClickListener(v -> startNavigationWithInput());

        // 初始化定位
        initLocation();

        findViewById(R.id.btn_connect).setOnClickListener(v -> startWs());
//        findViewById(R.id.btn_send).setOnClickListener(v -> sendWs("hello"));
//        findViewById(R.id.btn_send_struct).setOnClickListener(v -> sendNavStructs());
        findViewById(R.id.btn_options).setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);  // 如果 MainActivity 已存在则拉到前台
            startActivity(i);
        });

        // 放在 setContentView 之后
        debugPanel   = findViewById(R.id.debug_panel);
        debugTitle   = findViewById(R.id.debug_title);
        debugHex     = findViewById(R.id.debug_hex);
        debugFields  = findViewById(R.id.debug_fields);
        debugTitle.setOnClickListener(v -> {
            debugCollapsed = !debugCollapsed;
            debugHex.setVisibility(debugCollapsed ? View.GONE : View.VISIBLE);
            debugFields.setVisibility(debugCollapsed ? View.GONE : View.VISIBLE);
            debugTitle.setText(debugCollapsed ? "调试信息（点我展开）" : "调试信息（点我折叠）");
        });

        // 注册导航回调


        Log.d(TAG, "GpsNavLocActivity onCreate: 注册导航 observer 完成");
    }

    public static class CANWeather {
        public byte routeHash;      // 7bit
        public byte dataValid;      // 1bit
        public byte weatherCode;    // 4bit
        public byte tempConfidence; // 4bit  ← 原 temperature
        public byte precipLevel;    // 3bit
        public byte warnType;       // 3bit
        public byte warnLevel;      // 2bit
        public byte realTemperature;// 8bit  ← 原 reserved
        public short totalDistance;
        public short keyPoints;
    }

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
        // 可以继续加
    }

    private CANWeather buildCANWeather(String temp, String desc) {
        CANWeather w = new CANWeather();

        /* 1. routeHash */
        int routeHash = 0;
        try { routeHash = Integer.parseInt(currentRoute.getRouteId()) & 0x7F; }
        catch (Exception ignore) {}
        w.routeHash = (byte) routeHash;

        /* 2. dataValid */
        w.dataValid = 1;

        /* 3. weatherCode（4bit）*/
//        w.weatherCode = (byte) (WEATHER_CODE_MAP.getOrDefault(desc, 0) & 0xF);
        Integer code = WEATHER_CODE_MAP.get(desc);
        w.weatherCode = (byte) ((code != null ? code : 0) & 0xF);

        /* 4. tempConfidence（4bit）→ 这里简单用 15 表示“高置信度” */
        w.tempConfidence = 15;   // 0~15，你可按业务改

        /* 5. realTemperature（8bit，-40~50 直接存）*/
        int tempInt = Integer.parseInt(temp);        // 原始摄氏度
        w.realTemperature = (byte) (tempInt + 40);   // 0~90 正好占 1 Byte

        /* 6. 其余字段保持旧逻辑 */
        w.precipLevel = 0;
        w.warnType    = 0;
        w.warnLevel   = 0;
        w.totalDistance = (short) (currentRoute == null ? 0 :
                Math.min(currentRoute.getDistance(), 0xFFFF));
        w.keyPoints   = 0;

        return w;
    }

    private void initView() {
        // 获取父布局容器（来自父类activity_nav.xml）
        FrameLayout rootView = findViewById(R.id.app_root_view);
        if (rootView == null) {
            Log.e("GpsNavLoc", "未找到父布局容器");
            finish();
            return;
        }

        // 加载自定义布局
        LinearLayout customLayout = (LinearLayout) LayoutInflater.from(this)
                .inflate(R.layout.activity_gps_nav_loc, rootView, false);
        rootView.addView(customLayout);

        // 绑定控件
        tvLocationInfo = customLayout.findViewById(R.id.tv_location_info);
        etDestination = customLayout.findViewById(R.id.et_destination);
        btnConfirmDestination = customLayout.findViewById(R.id.btn_confirm_destination);

        // 新增：打印控件是否为null
        Log.d("NavDebug", "tvLocationInfo: " + (tvLocationInfo == null ? "null" : "ok"));
        Log.d("NavDebug", "etDestination: " + (etDestination == null ? "null" : "ok"));
        Log.d("NavDebug", "btnConfirmDestination: " + (btnConfirmDestination == null ? "null" : "ok"));

        // 确认按钮点击事件
        btnConfirmDestination.setOnClickListener(v -> {
            // 新增：验证点击事件是否触发
            Log.d("NavDebug", "按钮被点击，开始处理导航...");
            startNavigationWithInput();
        });
    }

    /**
     * 处理输入的目的地并开始导航
     */
    private void startNavigationWithInput() {
        String destination = etDestination.getText().toString().trim();
        Log.d("NavDebug", "输入的目的地: " + destination); // 新增

        if (destination.isEmpty()) {
            showToast("请输入目的地");
            Log.d("NavDebug", "目的地为空，返回"); // 新增
            return;
        }

        // 1. 检查是否已获取当前定位（作为起点）
        Log.d("NavDebug", "当前定位是否有效: " + (currentLocation != null ? "是" : "否")); // 新增
        if (currentLocation == null) {
            showToast("正在获取当前位置，请稍候...");
            Log.d("NavDebug", "currentLocation为null，无法发起导航"); // 新增
            return;
        }

        // 2. 解析目的地（地址转经纬度）
        /**
         * 使用腾讯 WebServiceAPI 解析地址
         */
        parseDestination(destination, new OnDestinationParsedListener() {
            @Override
            public void onParsed(double lat, double lng) {
                // 解析成功，发起导航
                startNavigation(
                        new NavSearchPoint(currentLocation.getLatitude(), currentLocation.getLongitude()),
                        new NavSearchPoint(lat, lng)
                );

                // ⭐ 关键修复：导航启动后重新注册定位监听
                reRegisterLocationListenerAfterNavigation();

            }

            @Override
            public void onError(String error) {
                showToast("解析目的地失败：" + error);
            }
        });
    }

    /**
     * 导航启动后重新注册腾讯定位监听
     */
    private void reRegisterLocationListenerAfterNavigation() {
        // 使用带 Looper 参数的 Handler 构造函数
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (locationManager != null) {
                    // 重新注册腾讯定位监听 - 修复：使用 GpsNavLocActivity.this
                    int result = locationManager.requestLocationUpdates(locationRequest, NavUpdateActivity.this, 0);

                    if (result == 0) {
                        Log.d("NavDebug", "✅ 导航启动后重新注册腾讯定位监听 - 成功");

                        // 获取最后已知位置（腾讯定位SDK的方式）
                        TencentLocation lastLocation = locationManager.getLastKnownLocation();
                        if (lastLocation != null) {
                            Log.d("NavDebug", "📌 重新注册后获取的腾讯位置: " +
                                    lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                        }
                    } else {
                        Log.e("NavDebug", "❌ 重新注册腾讯定位失败，错误码: " + result);
                    }
                }
            } catch (Exception e) {
                Log.e("NavDebug", "❌ 重新注册腾讯定位异常: " + e.getMessage());
            }
        }, 1000); // 延迟1秒，确保导航SDK完全初始化
    }


    private void parseDestination(String address, OnDestinationParsedListener listener) {
        TencentGeoCoder.geoCode(this, address, new TencentGeoCoder.GeoListener() {
            @Override
            public void onSuccess(double lat, double lng, String title) {
                runOnUiThread(() -> listener.onParsed(lat, lng));
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> listener.onError(msg));
            }
        });
    }

//    private void parseDestination(String address, OnDestinationParsedListener listener) {
//        Geocoder geocoder = new Geocoder(this);
//        new Thread(() -> {
//            try {
//                // 地址解析（最多返回 1 个结果）
//                List<Address> addresses = geocoder.getFromLocationName(address, 1);
//                if (!addresses.isEmpty()) {
//                    Address addr = addresses.get(0);
//                    double lat = addr.getLatitude();
//                    double lng = addr.getLongitude();
//                    runOnUiThread(() -> listener.onParsed(lat, lng));
//                } else {
//                    runOnUiThread(() -> listener.onError("地址解析失败"));
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//                runOnUiThread(() -> listener.onError("网络或解析错误"));
//            }
//        }).start();
//    }

    /**
     * 发起导航请求
     */
    private void startNavigation(NavSearchPoint startPoint, NavSearchPoint endPoint) {
        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(startPoint)
                        .end(endPoint)
                        .build(),
                (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
                    if (error != null) {
                        showToast("算路失败：" + error.getMessage());
                        return;
                    }
                    if (navRoutePlan != null) {
                        List<NavDriveRoute> routePlanList = navRoutePlan.getRouteDatas();
                        if (routePlanList != null && routePlanList.size() > 0) {
                            stopCurrentNavigation(); // 停止当前导航
                            navigatorDrive.startNavigation(routePlanList.get(0).getRouteId());

                            // ❌ 不需要了：
                            currentRoute = routePlanList.get(0);
                            startSendThread();

                        } else {
                            showToast("未获取到有效路线");
                        }
                    }
                }
        );
    }

    /**
     * 停止当前导航
     */
    private void stopCurrentNavigation() {
        if (navigatorDrive != null && mNavigating) {
            navigatorDrive.stopNavigation();
            mNavigating = false;
        }
    }

    /**
     * 初始化定位相关逻辑
     */
    private void initLocation() {
        // 步骤 1：获取 AndroidManifest.xml 中配置的 TencentMapSDK Key
        String sdkKey = "";
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                sdkKey = appInfo.metaData.getString("TencentMapSDK");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // 步骤 2：构造 Pair 对象（第二个参数若不需要 secret 可传空字符串）
        Pair<String, String> keyPair = new Pair<>(sdkKey, "");
        // 初始化 TencentLocationManager，传入对应的 Key 等参数（和 DemoMapActivity 保持一致）
//        locationManager = TencentLocationManager.getInstance(this, new Pair<>("你的Key", ""));
        locationManager = TencentLocationManager.getInstance(this, keyPair);
        locationRequest = TencentLocationRequest.create();
        locationRequest.setInterval(5000); // 设置定位间隔，单位毫秒
        // 发起定位请求，设置回调为当前 Activity（因为实现了 TencentLocationListener 接口）
        locationManager.requestLocationUpdates(locationRequest, this, 0);
    }

    /**
     * 算路并开启导航逻辑
     */
    private void searchRouteAndStartNavigation() {
        // 如果还没获取到定位信息，先等待定位回调，这里简单判断一下
        if (currentLocation == null) {
            // 可以提示用户“正在获取定位，请稍候”等，或者直接 return 等待下次定位回调再算路
            return;
        }

        // 使用当前定位信息作为起点
        NavSearchPoint startPoint = new NavSearchPoint(
                currentLocation.getLatitude(),
                currentLocation.getLongitude()
        );

        // 构建算路请求
        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(startPoint)
                        .end(new NavSearchPoint(39.513005, 116.416642)) // 终点坐标，可根据需求调整
                        .build(),
                (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
                    if (error != null) {
                        // 处理算路错误，比如弹 Toast 提示
                        showToast("算路失败：" + error.getMessage());
                        return;
                    }
                    if (navRoutePlan != null) {
                        List<NavDriveRoute> routePlanList = navRoutePlan.getRouteDatas();
                        if (routePlanList != null && routePlanList.size() > 0) {
                            // 开启导航
                            stopCurrentNavigation(); // 停止当前导航
                            navigatorDrive.startNavigation(routePlanList.get(0).getRouteId());
                        } else {
                            showToast("未获取到有效路线");
                        }
                    }
                }
        );
    }

    /**
     * 定位回调，获取到新的定位信息时会调用
     */
    @Override
    public void onNmeaMsgChanged(String nmea) {
        // 空实现（NMEA是卫星定位的原始数据协议，暂时用不到可忽略）
    }

    @Override
    public void onGnssInfoChanged(Object gnssInfo) {
        // TODO
        // 空实现（如果不需要处理GNSS信息）
        // 若需要处理，可根据官方文档解析gnssInfo对象（可能是GNSS状态、卫星数量等信息）
    }

    private boolean isOffRoute(TencentLocation newLocation) {
        if (currentRoute == null || newLocation == null) {
            return false; // 如果没有当前路线或新位置，直接返回false
        }

        // 获取路线的路径点集合
        List<LatLng> routePoints = currentRoute.getRoutePoints(); // 假设 getRoutePoints() 返回的是 List<LatLng>
        if (routePoints == null || routePoints.isEmpty()) {
            return false; // 如果路线点集合为空，直接返回false
        }

        // 初始化偏离标志和最小距离
        boolean isOff = true;
        double minDistance = Double.MAX_VALUE;

        // 遍历路线上的每个点，计算与新位置的最短距离
        for (LatLng routePoint : routePoints) {
            double distance = haversineDistance(newLocation.getLatitude(), newLocation.getLongitude(), routePoint.latitude, routePoint.longitude);
            if (distance < minDistance) {
                minDistance = distance;
                isOff = false; // 如果找到更近的点，则设置偏离标志为false
            }
        }

        // 检查最小距离是否超过偏离阈值
        final double OFFSET_DISTANCE = 50.0; // 假设偏离阈值为50米
        return minDistance > OFFSET_DISTANCE;
    }

    /**
     * 使用haversine公式计算两点之间的距离
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
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


    private void checkAndReplanRouteIfNecessary(TencentLocation newLocation) {
        String destination = etDestination.getText().toString().trim();
        if (isOffRoute(newLocation)) {
            parseDestination(destination, new OnDestinationParsedListener() {
                @Override
                public void onParsed(double lat, double lng) {
                    startNavigation(
                            new NavSearchPoint(currentLocation.getLatitude(), currentLocation.getLongitude()),
                            new NavSearchPoint(lat, lng)
                    );
                }

                @Override
                public void onError(String error) {
                    showToast("解析目的地失败：" + error);
                }
            });
        }
    }

    private void NOcheckAndReplanRouteIfNecessary(TencentLocation newLocation) {
        String destination = etDestination.getText().toString().trim();
//        if (isOffRoute(newLocation)) {
            parseDestination(destination, new OnDestinationParsedListener() {
                @Override
                public void onParsed(double lat, double lng) {
                    startNavigation(
                            new NavSearchPoint(currentLocation.getLatitude(), currentLocation.getLongitude()),
                            new NavSearchPoint(lat, lng)
                    );
                }

                @Override
                public void onError(String error) {
                    showToast("解析目的地失败：" + error);
                }
            });
//        }
    }

    @Override
    public void onLocationChanged(TencentLocation location, int errorCode, String errorMsg) {
        Log.d("NavDebug", "定位回调：errorCode=" + errorCode + ", errorMsg=" + errorMsg); // 新增
        if (errorCode == 0) {
            currentLocation = location; // 保存最新定位信息

            // 一定要加这一段，防止导航未开始导致 info 为 null
            if (navigatorDrive == null) {
                return;
            }
            if (navigatorDrive != null) {
                NavDriveDataInfoEx info = navigatorDrive.getNavRouteDataInfo();
                if (info == null) {
                    // 说明还没有 startNavigation() 或导航引擎尚未初始化完成
                    Log.d("NavDebug", "导航尚未初始化完成，NavRouteDataInfo 为空");
                    return;
                }
                int passeddistance = info.getPassedDistance();
                int distance = info.getMainRoute().getDistance();
                int leftDistance = info.getMainRoute().getDistance() - info.getPassedDistance();
                int speed = info.getSpeedKMH();
                int passedtime = info.passedTime;
                int time = info.getMainRoute().getTime();
                int passedseconds = time * 60 - info.getPassedTime();
                int leftminutes = (int)passedseconds/60 ;
                currentRoute = info.getMainRoute();
                currentRoute.distance = leftDistance;
                currentRoute.time = leftminutes;
            }

            /* 1. 计算实时车速（m/s） */
            double speedMps = calcSpeedFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getTime());   // 系统时间戳 ms

            // 更新 UI 显示定位信息，比如经纬度
            String locationInfo = "纬度: " + location.getLatitude() + "\n经度: " + location.getLongitude();
            if (tvLocationInfo != null) {
                tvLocationInfo.setText(locationInfo);
            }


            // 如果之前没算路成功，这里可以再次尝试算路（比如第一次定位完成后自动算路）
            if (currentRoute == null) {
                Log.d("NavDebug", "导航为空，不再自动算路到北京");
            }

            // 顺手刷天气
            TencentWeather.getNow(
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    new TencentWeather.WeatherCallback() {
                        @Override
                        public void onSuccess(String json) {
                            try {
                                Log.d("weather", "原始 json = " + json);

                                JSONObject realtime = JSON.parseObject(json)
                                        .getJSONObject("result")
                                        .getJSONArray("realtime")
                                        .getJSONObject(0);
                                JSONObject infos = realtime.getJSONObject("infos");

                                String temp  = String.valueOf(infos.getIntValue("temperature")); // 29
                                String desc  = infos.getString("weather");                       // 多云
                                String icon  = null;                                             // 暂无图标字段

                                // 1. 缓存
                                lastTemp = temp;
                                lastDesc = desc;

                            } catch (Exception e) {
                                Log.e("weather", "解析失败", e);
                            }
                        }

                        @Override
                        public void onFail(String msg) {
                            Log.e("weather", "获取天气失败: " + msg);
                        }
                    });

            // 判断是否偏离路线并重新规划路线
            checkAndReplanRouteIfNecessary(location);
//            NOcheckAndReplanRouteIfNecessary(location);

        } else {
            // 定位出错，处理错误情况，比如弹 Toast 提示
            showToast("定位失败：" + errorMsg);
            Log.e("NavDebug", "定位失败：errorCode=" + errorCode + ", errorMsg=" + errorMsg); // 新增
        }
    }

    private void updateCurrentRouteInfo(TencentLocation location) {
        if (currentRoute == null || location == null) {
            return;
        }

        // 获取路线的路径点集合
        List<LatLng> routePoints = currentRoute.getRoutePoints();
        if (routePoints == null || routePoints.isEmpty()) {
            return;
        }

        // 初始化到达目的地的距离
        double distanceToDestination = Double.MAX_VALUE;

        // 遍历路线上的每个点，计算与新位置的最短距离
        for (LatLng routePoint : routePoints) {
            double distance = haversineDistance(location.getLatitude(), location.getLongitude(), routePoint.latitude, routePoint.longitude);
            if (distance < distanceToDestination) {
                distanceToDestination = distance;
            }
        }
        // 更新到达目的地的距离
//        currentRoute.distance = (int) distanceToDestination;
//        currentRoute.distance = 1234;  //  5023是 5km

    }

    /**
     * 根据两次经纬度变化计算车速（m/s）
     * 第一次调用返回 0，之后返回实时速度
     */
    private double calcSpeedFromLocation(double lat, double lon, long timeMs) {
        if (Double.isNaN(lastLat)) {          // 第一次
            lastLat  = lat;
            lastLon  = lon;
            lastTime = timeMs;
            return 0.0;
        }

        double distM = haversine(lastLat, lastLon, lat, lon); // 米
        double dtS   = (timeMs - lastTime) / 1000.0;        // 秒
        double speed = (dtS > 0) ? (distM / dtS) : 0.0;

        /* 更新缓存 */
        lastLat  = lat;
        lastLon  = lon;
        lastTime = timeMs;

        /* 限速过滤：0.5 ~ 35 m/s（约 0-126 km/h）*/
        return Math.max(0.5, Math.min(speed, 35.0));
    }

    @Override
    public void onStatusUpdate(String s, int i, String s1) {

    }

    // 显示Toast
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // 解析目的地的回调接口
    interface OnDestinationParsedListener {
        void onParsed(double lat, double lng);
        void onError(String error);
    }


    // 替换原来的 startWs() 方法
    private void startWs() {
        String ipPort = etIpPort.getText().toString().trim();
        if (ipPort.isEmpty()) {
            Toast.makeText(this, "请先输入 IP:端口", Toast.LENGTH_SHORT).show();
            return;
        }
        // 简单校验格式（可再增强）
        if (!ipPort.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+$")) {
            Toast.makeText(this, "格式错误，示例 192.168.1.30:54330", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = "ws://" + ipPort;
        Log.d("WS", "连接地址 = " + url);

        // 关闭旧连接
//        if (ws != null) ws.close(1000, "重新连接");
        if (ws != null) {
            ws.close(1000, "重新连接");
            ws = null;                 // ✅ 1. 先清空旧引用
        }
        Request req = new Request.Builder().url(url).build();
        ws = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                Log.d("WS", "✅ 连接成功，WebSocket 已持有: " + webSocket); // ✅ 2. 日志
                runOnUiThread(() -> Toast.makeText(NavUpdateActivity.this, "已连接 " + ipPort, Toast.LENGTH_SHORT).show());
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                Log.e("WS", "连接失败", t);
                runOnUiThread(() -> Toast.makeText(NavUpdateActivity.this, "连接失败: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                ws = null;                 // ✅ 3. 失败也清空
            }
            @Override public void onMessage(WebSocket webSocket, String text) {
                runOnUiThread(() -> Toast.makeText(NavUpdateActivity.this, "收到: " + text, Toast.LENGTH_SHORT).show());
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                runOnUiThread(() -> Toast.makeText(NavUpdateActivity.this, "已关闭: " + reason, Toast.LENGTH_SHORT).show());
                if (webSocket == ws) ws = null; // ✅ 4. 只清空自己
            }
        });
    }

    private void sendWs(String msg) {
        if (ws != null) ws.send(msg);
    }


    // 1. 与 C 端一致的 3 个结构体
    public static class TrafficEvent {
        public byte eventCount;
        public byte eventSummary;      // bit7-6=最高等级, bit5-0=中度计数
        public short nearestDistance;  // m
        public byte nearestType;       // 12/13/21
        public byte nearestDelay;      // s
        public byte severeCount;
        public byte accidentCount;
    }


    private TrafficEvent buildTrafficEvent() {
        TrafficEvent e = new TrafficEvent();
        NavDriveRoute route = currentRoute;
        if (route == null) return e;

        int eventCount = 0, severeCount = 0, accidentCount = 0;
        int highestLevel = 0;
        int nearestDistance = Integer.MAX_VALUE;
        int nearestDelay = 0, nearestType = 0;

        try {
            /* 1. 从 route 层拿 trafficItems（不再遍历 segs） */
            List<?> trafficItems = (List<?>) ReflectUtil.getField(route, "trafficItems");
            if (trafficItems != null) {
                eventCount = trafficItems.size();

                for (Object item : trafficItems) {
                    int type     = toInt(ReflectUtil.getField(item, "eventType"), 0);
                    int status   = toInt(ReflectUtil.getField(item, "trafficStatus"), 0);
                    int distance = toInt(ReflectUtil.getField(item, "distance"), Integer.MAX_VALUE);
                    int passTime = toInt(ReflectUtil.getField(item, "passTime"), 0); // 历史均速通过时间
                    int navSpeed = toInt(ReflectUtil.getField(item, "speed"), 0);    // km/h

                    // 获取实时车速，优先使用实时车速
                    NavDriveDataInfoEx info = navigatorDrive.getNavRouteDataInfo();
                    int realSpeed = info != null ? info.getSpeedKMH() : navSpeed;  // 使用实时车速（如果 info 不为空）
                    if (realSpeed < 1) realSpeed = 1;  // 保底：未知或 0 时给 10 km/h
                    float navSpeedMps = realSpeed / 3.6f;  // 转换为 m/s
                    // 保底：未知或 0 时给 10 km/h
                    if (navSpeed < 1) navSpeed = 1;
                        navSpeedMps = navSpeed / 3.6f;

                    /* 2. 事件等级映射 */
                    int level = 0;
                    if (type == 12) level = 1;          // 拥堵
                    if (type == 13) level = 2;          // 事故 → 严重

                    // 重新估算 delay（秒）
                    int estDelay = (distance == Integer.MAX_VALUE) ? 0
                            : (int) (distance / navSpeedMps);
                    // 取更悲观值
                    estDelay = Math.max(estDelay, passTime);

                    /* 4. 统计 */
                    if (level == 2) severeCount++;
                    if (type == 13) accidentCount++;
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestDelay    = estDelay;
                        nearestType     = type;
                    }
                    if (level > highestLevel) highestLevel = level;
                }
            }
        } catch (Throwable t) {
            Log.e("NavBuild", "buildTrafficEvent error", t);
        }

        if (nearestDistance == Integer.MAX_VALUE) nearestDistance = 0;

        /* 5. 按原协议打包 */
        e.eventCount      = (byte) Math.min(eventCount, 127);
        e.eventSummary    = (byte) (((highestLevel & 0x03) << 6) | (eventCount & 0x3F));
        e.nearestDistance = (short) Math.max(0, Math.min(nearestDistance, 0xFFFF));
        e.nearestType     = (byte) Math.max(0, Math.min(nearestType, 0xFF));
        e.nearestDelay    = (byte) Math.max(0, Math.min(nearestDelay, 0xFF));
        e.severeCount     = (byte) Math.min(severeCount, 127);
        e.accidentCount   = (byte) Math.min(accidentCount, 127);
        return e;
    }


    public static class TrafficLight {
        public byte nextLightId;
        public byte stateFlags;        // bit5-4=类型, bit3-2=状态
        public short positionIndex;
        public byte remainingTime;     // s
        public byte distanceToLight;   // m
        public byte speed;
        public byte lightCount;
    }

    private TrafficLight buildTrafficLight() {
        int speedMain = 0;
        TrafficLight l = new TrafficLight();
        NavDriveRoute route = currentRoute;
        if (route == null) return l;

        if (navigatorDrive != null) {
            NavDriveDataInfoEx info = navigatorDrive.getNavRouteDataInfo();
            if (info != null) {
                speedMain = info.getSpeedKMH();
            }
        }

        try {
            List<?> lights = (List<?>) ReflectUtil.getField(route, "trafficLights");
            if (lights == null || lights.isEmpty()) return l;

            /* 1. 自车实时位置 */
            double carLat = currentLocation.getLatitude();
            double carLon = currentLocation.getLongitude();
//            float speedKph = CarState.getInstance().getSpeed();   // km/h
            double speedMps = calcSpeedFromLocation(
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    currentLocation.getTime());
            float speedKph = (float)(speedMps * 3.6);

            if (speedMps < 0.5f) speedMps = 0.5f;

            /* 2. 取最近一盏灯（这里简化：列表第 0 个） */
            Object first = lights.get(0);

            /* 1. 先取出内嵌 LatLng 对象 */
            Object latLng = ReflectUtil.getField(first, "latLng");
            double lightLat = toDouble(ReflectUtil.getField(latLng, "latitude"), 0);
            double lightLon = toDouble(ReflectUtil.getField(latLng, "longitude"), 0);

            int positionIndex = toInt(ReflectUtil.getField(first, "pointIndex"), 0);

            /* 3. 直线距离 m + 到达时间 s */
            double dist = haversine(carLat, carLon, lightLat, lightLon);
            int eta = (int) (dist / speedMps);

            /* 检查距离并决定是否重新导航 */

//            if (dist > 250 || dist < 5) {
//                if (!isNavigationRequested) { // 如果还没有请求重新导航
//                    isNavigationRequested = true; // 标记为已请求重新导航
//                    // 等待10秒后重新导航
//                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                        if (dist > 250 || dist < 5) { // 如果距离仍然超过250或小于5
//                            // 再次等待30秒后请求重新导航
//                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                                startNavigationWithInputAgain();
//                                isNavigationRequested = false; // 重置标志位
//                            }, 30000); // 等待30秒
//                        } else {
//                            isNavigationRequested = false; // 如果距离不再满足条件，重置标志位
//                        }
//                    }, 10000); // 等待10秒
//                }
//            }

            /* 4. 灯态 & 剩余秒数（规则可再调） */
            int state, remaining;
            if (speedKph > 30) {          // 绿灯
                state = 1;
                remaining = 10 + (eta % 16);   // 10~25 s
            } else {                      // 红灯
                state = 0;
                remaining = 25 + (eta % 21);   // 25~45 s
            }

//            int speedKphInt = Math.min(255, Math.max(0, (int) speedKph)); // 0-255
            int speedKphInt = Math.min(255, Math.max(0, (int) speedMain)); // 0-255
            int lightCount = toInt(ReflectUtil.getField(route, "trafficLightCount"), 0);

            /* 5. 打包 */
            l.nextLightId     = (byte) Math.min(positionIndex & 0xFF, 0xFF);
            l.stateFlags      = (byte) (((0 & 0x03) << 4) | ((state & 0x03) << 2));
            l.positionIndex   = (short) Math.min(positionIndex, 0xFFFF);
            l.remainingTime   = (byte) Math.min(remaining, 0xFF);
            l.distanceToLight = (byte) Math.min((int) dist, 0xFF);
            l.speed = (byte) speedKphInt;   // 原名保留，实际存 speed
            l.lightCount = (byte) Math.min(255, lightCount); // 0-255

        } catch (Throwable t) {
            Log.e("NavBuild", "buildTrafficLight error", t);
        }
        return l;
    }

//    // 新增：重新导航的方法
//    private void startNavigationWithInputAgain() {
//        String destination = etDestination.getText().toString().trim();
//        Log.d("NavDebug", "输入的目的地: " + destination); // 新增
//
//        if (destination.isEmpty()) {
//            showToast("请输入目的地");
//            Log.d("NavDebug", "目的地为空，返回"); // 新增
//            return;
//        }
//
//        // 1. 检查是否已获取当前定位（作为起点）
//        Log.d("NavDebug", "当前定位是否有效: " + (currentLocation != null ? "是" : "否")); // 新增
//        if (currentLocation == null) {
//            showToast("正在获取当前位置，请稍候...");
//            Log.d("NavDebug", "currentLocation为null，无法发起导航"); // 新增
//            return;
//        }
//
//        // 2. 解析目的地（地址转经纬度）
//        /**
//         * 使用腾讯 WebServiceAPI 解析地址
//         */
//        parseDestination(destination, new OnDestinationParsedListener() {
//            @Override
//            public void onParsed(double lat, double lng) {
//                // 解析成功，发起导航
//                startNavigation(
//                        new NavSearchPoint(currentLocation.getLatitude(), currentLocation.getLongitude()),
//                        new NavSearchPoint(lat, lng)
//                );
//            }
//
//            @Override
//            public void onError(String error) {
//                showToast("解析目的地失败：" + error);
//            }
//        });
//    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;                 // 地球半径 m
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


    private static double toDouble(Object v, double def) {
        return (v instanceof Number) ? ((Number) v).doubleValue() : def;
    }

    public static class RouteOverview {
        public short totalDistance;    // m
        public byte tollDistancePct;   // %
        public byte congestionFlags;   // 3 段占比
        public short estimatedTime;    // min
        public short totalFee;         // 0.1 元
    }

    private RouteOverview buildRouteOverview() {
        RouteOverview r = new RouteOverview();
        NavDriveRoute route = currentRoute;
        if (route == null) return r;

        try {
            /* 1. 距离 & 费用 */
            int totalDistance = (int) Math.max(0, route.getDistance());          // m
            int tollDistance  = toInt(ReflectUtil.getField(route, "tollDistance"), 0);
            int tollPct = totalDistance == 0 ? 0
                    : (int) Math.min(100, tollDistance * 100L / totalDistance);

            /* 2. 剩余 estimatedTime：直接拿 minute 字段 */
            int durationMin = toInt(ReflectUtil.getField(route, "time"), 0);   // ← 就是 14

            long feeDeci = Math.round(Math.max(0.0, route.getFee()) * 10.0);
            feeDeci = Math.min(feeDeci, 0xFFFF);

            /* =====  基于 segmentItems 计算拥堵指数 ===== */
            List<?> segs = (List<?>) ReflectUtil.getField(route, "segmentItems");
            double totalCost = 0;      // 累计“驾驶复杂度”
            int segCount = (segs == null) ? 0 : segs.size();

            if (segs != null) {
                for (Object seg : segs) {
                    int distance   = toInt(ReflectUtil.getField(seg, "distance"), 0);
                    int lightCount = toInt(ReflectUtil.getField(seg, "numTrafficLight"), 0);
                    String action  = String.valueOf(ReflectUtil.getField(seg, "action"));
                    String roadName= String.valueOf(ReflectUtil.getField(seg, "roadName"));

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

            /* ===== 2. 映射到 0-100 百分比 ===== */
            // 经验上限：120 米当量 ≈ 100% 拥堵
            int jam   = (int) Math.min(100, Math.max(0, totalCost / 120 * 100));
            int slow  = (int) Math.min(100 - jam, Math.max(0, totalCost / 60 * 100));
            int smooth = 100 - jam - slow;

            /* ===== 3. 打包成 8bit congestionFlags ===== */
            // 7-6:smooth/10  5-4:slow/10  3-0:jam/10
            byte congestionFlags = (byte) (
                    (((smooth / 10) & 0x03) << 6) |
                            (((slow  / 10) & 0x03) << 4) |
                            ((jam   / 10) & 0x0F));

            /* ===== 4. 填充结构体 ===== */
            r.totalDistance   = (short) Math.min(totalDistance, 0xFFFF);
            r.tollDistancePct = (byte) tollPct;
            r.congestionFlags = congestionFlags;
            r.estimatedTime   = (short) Math.min(durationMin, 0xFFFF);
            r.totalFee        = (short) Math.min(feeDeci, 0xFFFF);
        } catch (Throwable t) {
            Log.e("NavBuild", "buildRouteOverview error", t);
        }
        return r;
    }

    private volatile byte msgCounter = 0;   // 0~255 循环
    private byte[] packAll(TrafficEvent e, TrafficLight l, RouteOverview r, CANWeather w) {
        ByteBuffer bb = ByteBuffer.allocate(33);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // 1. counter
        msgCounter++;
        bb.put(msgCounter);   // 第 1 字节
//        bb.put((byte) 0);
        Log.d("PackPosition", "should be 1, after counter pos = " + bb.position());
        // 2. TrafficEvent → 8 字节
        bb.put(e.eventCount);
        bb.put(e.eventSummary);
        bb.putShort(e.nearestDistance);
        bb.put(e.nearestType);
        bb.put(e.nearestDelay);
        bb.put(e.severeCount);
        bb.put(e.accidentCount);

        // 3. TrafficLight → 8 字节
        bb.put(l.nextLightId);
        bb.put(l.stateFlags);
        bb.putShort(l.positionIndex);
        bb.put(l.remainingTime);
        bb.put(l.distanceToLight);
        bb.put(l.speed);
        bb.put(l.lightCount);

        // 4. RouteOverview → 8 字节
        bb.putShort(r.totalDistance);
        bb.put(r.tollDistancePct);
        bb.put(r.congestionFlags);
        bb.putShort(r.estimatedTime);
        bb.putShort(r.totalFee); //

        // 5. CANWeather → 8 字节
        byte b0 = (byte) ((w.routeHash & 0x7F) | ((w.dataValid & 0x01) << 7));
        byte b1 = (byte) ((w.weatherCode & 0x0F) | ((w.tempConfidence & 0x0F) << 4));
        byte b2 = (byte) ((w.precipLevel & 0x07) | ((w.warnType & 0x07) << 3) | ((w.warnLevel & 0x03) << 6));
        bb.put(b0).put(b1).put(b2).put(w.realTemperature).putShort(w.totalDistance).putShort(w.keyPoints);

        byte[] buf = bb.array();
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) sb.append(String.format("%02X ", b));

        Log.d("PackPosition", "payload len = " + bb.position()); // 看写到了多少字节

        return buf;
    }

    private void sendNavStructs() {
//        if (ws == null || !mNavigating || currentRoute == null) return;
        if (!mNavigating || currentRoute == null) return;
        try {
            TrafficEvent  ev = buildTrafficEvent();
            TrafficLight  tl = buildTrafficLight();
            RouteOverview ro = buildRouteOverview();
            CANWeather cw = buildCANWeather(lastTemp, lastDesc);
            byte[] payload = packAll(ev, tl, ro, cw);
//            ws.send(ByteString.of(payload)); // 发送二进制
            // 只在WebSocket不为空时发送
            if (ws != null) {
                ws.send(ByteString.of(payload)); // 发送二进制
            }
            // 调试刷新
            updateDebugPanel(payload, ev, tl, ro, cw);
        } catch (Throwable t) {
            Log.e("WS-SEND", "sendNavStructs error", t);
        }
    }


    // ========== 反射小工具 ==========
    private static class ReflectUtil {
        public static Object getField(Object obj, String fieldName) {
            try {
                java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Exception ignore) {
                return null;
            }
        }
    }


    private void startSendThread() {
        synchronized (mLock) {
            if (mNavigating) return;
            mNavigating = true;
            mSendThread = new Thread(() -> {
//                int sendCount = 0; // 添加一个计数器
                while (mNavigating && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(2000);
                        // 以前：runOnUiThread(this::sendNavStructs);
                        // 现在：后台线程直接发送，不上主线程
                        sendNavStructs();
//                        sendCount++; // 每发送一次，计数器加1
//                        if (sendCount % 30 == 0) { // 每发送10次
//                            startNavigationWithInputAgain(); // 调用startNavigationWithInputAgain()
//                        }
                    } catch (InterruptedException ignore) {
                        break;
                    } catch (Throwable t) {
                        Log.e("WS-SEND", "send loop error", t);
                    }
                }
            }, "NavWsSender");
            mSendThread.start();
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSendThread();   // 停线程
        closeWebSocket();   // 关 Socket
    }

    private void stopSendThread() {
        synchronized (mLock) {
            mNavigating = false;
            if (mSendThread != null) {
                mSendThread.interrupt();
                mSendThread = null;
            }
        }
    }

    private void closeWebSocket() {
        if (ws != null) {
            ws.close(1000, "Activity finish");
            ws = null;
        }
    }

    private static int toInt(Object v, int def) {
        return (v instanceof Number) ? ((Number) v).intValue() : def;
    }
    private static long toLong(Object v, long def) {
        return (v instanceof Number) ? ((Number) v).longValue() : def;
    }

    /* ================= 调试面板（含天气） ================= */
    private void updateDebugPanel(byte[] buf,
                              TrafficEvent e,
                              TrafficLight l,
                              RouteOverview r,
                              CANWeather w) { // ===== MOD =====
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder();
            for (byte b : buf) sb.append(String.format("%02X ", b & 0xFF));
            debugHex.setText(sb.toString().trim());


            String fields = String.format(Locale.CHINA,
                    "事件=%d 严重=%d 事故=%d | 灯距=%dm 剩余=%ds | 总距=%.1fkm 时长=%dmin 费用=%.1f元\n" +
                            "天气码=%d 置信=%d 实时温度=%d℃ 降水=%d 预警=%d-%d | 车速=%dkm/h 灯总数=%d",
                    e.eventCount & 0xFF, e.severeCount & 0xFF, e.accidentCount & 0xFF,
                    l.distanceToLight & 0xFF, l.remainingTime & 0xFF,
                    r.totalDistance / 1000.0, r.estimatedTime & 0xFFFF, r.totalFee / 10.0,
                    w.weatherCode & 0xF, w.tempConfidence & 0xF, (w.realTemperature & 0xFF) - 40,
                    w.precipLevel & 0x7, w.warnType & 0x7, w.warnLevel & 0x3,
                    l.speed & 0xFF, l.lightCount & 0xFF);

//            String fields = String.format(
//                    "事件=%d 严重=%d 事故=%d | 灯距=%dm 剩余=%ds | 总距=%dm 时长=%dmin 费用=%.1f元\n" +
//                            "天气码=%d 置信=%d 实时温度=%d℃ 降水=%d 预警=%d-%d | 车速=%dkm/h 灯总数=%d",
//                    e.eventCount & 0xFF, e.severeCount & 0xFF, e.accidentCount & 0xFF,
//                    l.distanceToLight & 0xFF, l.remainingTime & 0xFF,
//                    r.totalDistance , r.estimatedTime & 0xFFFF, r.totalFee / 10.0,
//                    w.weatherCode & 0xF, w.tempConfidence & 0xF, (w.realTemperature & 0xFF) - 40,
//                    w.precipLevel & 0x7, w.warnType & 0x7, w.warnLevel & 0x3,
//                    l.speed & 0xFF, l.lightCount & 0xFF);

            debugFields.setText(fields);
            if (debugPanel.getVisibility() == View.GONE) debugPanel.setVisibility(View.VISIBLE);
        });
    }
}