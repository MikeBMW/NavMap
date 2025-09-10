package com.tencent.navix.demo.navi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
//import androidx.camera.core.CameraSelector;
//import androidx.camera.core.Preview;
//import androidx.camera.lifecycle.ProcessCameraProvider;
//import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.firebase.database.tubesock.WebSocket;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.demo.MainActivity;
import com.tencent.navix.demo.R;
import com.tencent.navix.demo.utils.TencentGeoCoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import android.location.Geocoder;
import android.location.Address;
//import com.tencent.map.search.SearchManager;
//import com.tencent.map.search.param.SearchParam;
//import com.tencent.map.search.result.PoiResult;


import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.util.concurrent.TimeUnit;
import okhttp3.RequestBody;
import okio.ByteString;


public class GpsNavLocActivity extends BaseNavActivity implements TencentLocationListener {

    // ⚠️修改 1：导航状态标志 + 发送线程 + 导航监听
    private volatile boolean mNavigating = false;   // 是否正在导航
    private Thread mSendThread;                     // 数据发送线程
    private final Object mLock = new Object();      // 同步锁

    // 导航相关变量
    private RoutePlanRequester routePlanRequester;
    private NavDriveRoute currentRoute;

    // 定位相关变量
    private TencentLocationManager locationManager;
    private TencentLocationRequest locationRequest;
    private TencentLocation currentLocation; // 保存当前定位信息

    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private long   lastTime = 0;

    // UI控件
    private EditText etDestination;
    private Button btnConfirmDestination;
    // 用于显示定位信息的 TextView（可根据实际布局调整）
    private TextView tvLocationInfo;
    private EditText etIpPort;

    // debug
    private View debugPanel;
    private TextView debugTitle, debugHex, debugFields;
    private boolean debugCollapsed = false;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1️⃣ 动态申请相机权限
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, 100);
        // 2. 启动相机
        // 把我们的输入面板“盖”在地图之上
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
        // onCreate 里追加
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
            }

            @Override
            public void onError(String error) {
                showToast("解析目的地失败：" + error);
            }
        });
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
                            navigatorDrive.startNavigation(routePlanList.get(0).getRouteId());

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
        // 空实现（如果不需要处理GNSS信息）
        // 若需要处理，可根据官方文档解析gnssInfo对象（可能是GNSS状态、卫星数量等信息）
    }
    @Override
    public void onLocationChanged(TencentLocation location, int errorCode, String errorMsg) {
        Log.d("NavDebug", "定位回调：errorCode=" + errorCode + ", errorMsg=" + errorMsg); // 新增
        if (errorCode == 0) {
            currentLocation = location; // 保存最新定位信息

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
//                searchRouteAndStartNavigation();
                Log.d("NavDebug", "导航为空，不再自动算路到北京");
            }
        } else {
            // 定位出错，处理错误情况，比如弹 Toast 提示
            showToast("定位失败：" + errorMsg);
            Log.e("NavDebug", "定位失败：errorCode=" + errorCode + ", errorMsg=" + errorMsg); // 新增
        }
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

    // 放在 GpsNavLocActivity 类内部即可
    private WebSocket ws;
    private OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)   // 心跳
            .build();

//    private void startWs() {
////        String url = "ws://192.168.1.6:8765";   // 换成你的主机 IP
////        String url = "ws://192.168.223.71:8765";   // 换成你的主机 IP
////        String url = "ws://192.168.92.71:8765";   // 换成你的主机 IP
//        String url = "ws://192.168.0.71:8765";   // 换成你的主机 IP
//
//        Request req = new Request.Builder().url(url).build();
//        ws = client.newWebSocket(req, new WebSocketListener() {
//            @Override public void onOpen(WebSocket webSocket, okhttp3.Response response) {
//                runOnUiThread(() ->
//                        Toast.makeText(GpsNavLocActivity.this, "已连接", Toast.LENGTH_SHORT).show());
//            }
//
//            @Override public void onMessage(WebSocket webSocket, String text) {
//                runOnUiThread(() ->
//                        Toast.makeText(GpsNavLocActivity.this, "收到: " + text, Toast.LENGTH_SHORT).show());
//            }
//
//            @Override public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
//                Log.e("WS", "连接失败", t);           // 关键：把异常打印到 Logcat
//                runOnUiThread(() ->
//                        Toast.makeText(GpsNavLocActivity.this, "连接失败: " + t.getMessage(), Toast.LENGTH_SHORT).show());
//            }
//
//            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
//                runOnUiThread(() ->
//                        Toast.makeText(GpsNavLocActivity.this, "已关闭: " + reason, Toast.LENGTH_SHORT).show());
//            }
//        });
//    }

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
        if (ws != null) ws.close(1000, "重新连接");
        Request req = new Request.Builder().url(url).build();
        ws = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                runOnUiThread(() -> Toast.makeText(GpsNavLocActivity.this, "已连接 " + ipPort, Toast.LENGTH_SHORT).show());
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                Log.e("WS", "连接失败", t);
                runOnUiThread(() -> Toast.makeText(GpsNavLocActivity.this, "连接失败: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
            @Override public void onMessage(WebSocket webSocket, String text) {
                runOnUiThread(() -> Toast.makeText(GpsNavLocActivity.this, "收到: " + text, Toast.LENGTH_SHORT).show());
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                runOnUiThread(() -> Toast.makeText(GpsNavLocActivity.this, "已关闭: " + reason, Toast.LENGTH_SHORT).show());
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

                    // 保底：未知或 0 时给 10 km/h
                    if (navSpeed < 1) navSpeed = 1;
                    float navSpeedMps = navSpeed / 3.6f;

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
        public byte reserved1;
        public byte reserved2;
    }

    private TrafficLight buildTrafficLight() {
        TrafficLight l = new TrafficLight();
        NavDriveRoute route = currentRoute;
        if (route == null) return l;

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

            /* 4. 灯态 & 剩余秒数（规则可再调） */
            int state, remaining;
            if (speedKph > 30) {          // 绿灯
                state = 1;
                remaining = 10 + (eta % 16);   // 10~25 s
            } else {                      // 红灯
                state = 0;
                remaining = 25 + (eta % 21);   // 25~45 s
            }

            /* 5. 打包 */
            l.nextLightId     = (byte) Math.min(positionIndex & 0xFF, 0xFF);
            l.stateFlags      = (byte) (((0 & 0x03) << 4) | ((state & 0x03) << 2));
            l.positionIndex   = (short) Math.min(positionIndex, 0xFFFF);
            l.remainingTime   = (byte) Math.min(remaining, 0xFF);
            l.distanceToLight = (byte) Math.min((int) dist, 0xFF);
            l.reserved1 = 0;
            l.reserved2 = 0;

        } catch (Throwable t) {
            Log.e("NavBuild", "buildTrafficLight error", t);
        }
        return l;
    }

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


    private byte[] packAll(TrafficEvent e, TrafficLight l, RouteOverview r) {
        ByteBuffer bb = ByteBuffer.allocate(24);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        /* 1. TrafficEvent  7 字节  (BBHBBB) */
        bb.put(e.eventCount)               // 0
                .put(e.eventSummary)             // 1
                .putShort(e.nearestDistance)     // 2-3
                .put(e.nearestType)              // 4
                .put(e.nearestDelay)             // 5
                .put(e.severeCount)              // 6
                .put(e.accidentCount);           // 7 ← 共 7 字节

        /* 2. TrafficLight  7 字节  (BBHBBB) */
        bb.put(l.nextLightId)              // 8
                .put(l.stateFlags)               // 9
                .putShort(l.positionIndex)       // 10-11
                .put(l.remainingTime)            // 12
                .put(l.distanceToLight)          // 13
                .put(l.reserved1)                // 14
                .put(l.reserved2);               // 15 ← 共 7 字节

        /* 3. RouteOverview 10 字节 (HHBBHH) */
        bb.putShort(r.totalDistance)       // 16-17
                .put(r.tollDistancePct)          // 18
                .put(r.congestionFlags)          // 19
                .putShort(r.estimatedTime)       // 20-21  ← 15 的小端 short
                .putShort(r.totalFee);           // 22-23 ← 共 10 字节

        /* 4. 打印 hex & 校验 */
        bb.rewind();
        short check = bb.getShort(20);     // 读 20-21 字节
        Log.d("Pack", "estimatedTime @20-21 = " + check);

        byte[] buf = bb.array();
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) sb.append(String.format("%02X ", b));
        Log.d("Pack", "24 B hex = " + sb.toString().trim());

        return buf;
    }

    private void sendNavStructs() {
        if (ws == null || !mNavigating || currentRoute == null) return;
        try {
            TrafficEvent  ev = buildTrafficEvent();
            TrafficLight  tl = buildTrafficLight();
            RouteOverview ro = buildRouteOverview();
            byte[] payload = packAll(ev, tl, ro);
            ws.send(ByteString.of(payload)); // 发送二进制
            // 调试刷新
            updateDebugPanel(payload, ev, tl, ro);
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
                while (mNavigating && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(2000);
                        // 以前：runOnUiThread(this::sendNavStructs);
                        // 现在：后台线程直接发送，不上主线程
                        sendNavStructs();
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


    // ⚠️修改 3：页面销毁时统一清理
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

    private void updateDebugPanel(byte[] buf,
                                  TrafficEvent e,
                                  TrafficLight l,
                                  RouteOverview r) {
        runOnUiThread(() -> {
            // 16 进制
            StringBuilder sb = new StringBuilder();
            for (byte b : buf) sb.append(String.format("%02X ", b & 0xFF));
            debugHex.setText(sb.toString().trim());

            // 关键字段
            String fields = String.format(
                    "事件=%d 严重=%d 事故=%d | 灯距=%dm 剩余=%ds | 总距=%.1fkm 时长=%dmin 费用=%.1f元",
                    e.eventCount & 0xFF, e.severeCount & 0xFF, e.accidentCount & 0xFF,
                    l.distanceToLight & 0xFF, l.remainingTime & 0xFF,
                    r.totalDistance / 1000.0, r.estimatedTime & 0xFFFF,
                    r.totalFee / 10.0);
            debugFields.setText(fields);

            // 第一次收到数据时自动展开
            if (debugPanel.getVisibility() == View.GONE) {
                debugPanel.setVisibility(View.VISIBLE);
            }
        });
    }
}