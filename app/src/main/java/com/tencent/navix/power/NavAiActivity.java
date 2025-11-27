package com.tencent.navix.power;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.navix.BaseNavActivity;
import com.tencent.navix.R;
import com.tencent.navix.api.config.SimulatorConfig;
import com.tencent.navix.api.model.NavDriveDataInfoEx;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.power.builders.DataPacker;
import com.tencent.navix.power.builders.NavDataBuilder;
import com.tencent.navix.power.interfaces.*;
import com.tencent.navix.power.managers.*;
import com.tencent.navix.power.models.RouteOverview;
import com.tencent.navix.power.models.TrafficEvent;
import com.tencent.navix.power.models.TrafficLight;
import com.tencent.navix.power.models.Weather;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class NavAiActivity extends BaseNavActivity
        implements LocationListener, NavigationListener, DataSendListener,
                   WeatherManager.WeatherUpdateListener,
                   UIManager.DestinationConfirmListener,
                   UIManager.ConnectClickListener{

    private static final String TAG = "NavAiActivity";

    private NavDataBuilder navDataBuilder;
    private DataPacker dataPacker;
    private NavDriveRoute currentRoute;

    // 管理器实例
    private LocationManager locationManager;
    private NavigationManager navigationManager;
    private WebSocketManager webSocketManager;
    private WeatherManager weatherManager;
    private UIManager uiManager;

    // 状态标志
    private boolean isNavigating = false;
    private boolean isPlaybackMode = false;    // 添加回放模式标志
    private File playbackFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);     // 先调用父类初始化导航界面
        Log.d(TAG, "NavAiActivity onCreate");

        // 保持屏幕常亮
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 设置布局
//        setContentView(R.layout.activity_nav_ai);
        // 添加自定义UI面板到父类的导航界面上
        setupCustomUI();

        // 检查是否进入回放测试模式
        if (getIntent() != null && getIntent().getBooleanExtra("playback_mode", false)) {
            enablePlaybackMode();
        } else {
            initializeManagers();
            initializeUI();
            startBaseServices();
        }

        // 检查网络状态
        checkNetworkStatus();
    }

    /**
     * 像 GpsNavLocActivity 那样添加自定义UI到导航界面之上
     */
    private void setupCustomUI() {
        Log.d(TAG, "设置自定义UI面板");

        try {
            // 找到父类布局的根容器
            FrameLayout root = findViewById(R.id.app_root_view);
            if (root == null) {
                Log.e(TAG, "找不到app_root_view，检查父类布局");
                return;
            }

            // 将自定义UI布局添加到导航地图之上
            View customPanel = getLayoutInflater().inflate(R.layout.activity_nav_ai, root, false);
            root.addView(customPanel);

            Log.d(TAG, "自定义UI面板添加完成");

        } catch (Exception e) {
            Log.e(TAG, "设置自定义UI失败", e);
        }
    }

    private void enablePlaybackMode() {
        Log.d(TAG, "启用回放测试模式");
        isPlaybackMode = true;

        initializeManagers();
        initializeUI();

        // 准备回放文件
        playbackFile = preparePlaybackFile();
        if (playbackFile == null) {
            Log.e(TAG, "回放文件准备失败");
            uiManager.showToast("回放文件准备失败");
            return;
        }

        startPlaybackNavigation();
    }

    private File preparePlaybackFile() {
        File playback = new File(getFilesDir(), "test_navigation.gps");
        try (InputStream inputStream = getResources().getAssets().open("test_navigation.gps");
             FileOutputStream fileOutputStream = new FileOutputStream(playback)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            Log.d(TAG, "回放文件准备完成: " + playback.getAbsolutePath());

            // 检查文件内容
            checkGpsFileContent(playback);
            return playback;
        } catch (IOException e) {
            Log.e(TAG, "回放文件准备错误", e);
            return null;
        }
    }

    private void checkGpsFileContent(File gpsFile) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(gpsFile));
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (lineCount <= 3) { // 只检查前3行作为示例
                    Log.d(TAG, "GPS文件第" + lineCount + "行: " + line);
                }
            }
            reader.close();
            Log.d(TAG, "GPS文件总行数: " + lineCount);
        } catch (IOException e) {
            Log.e(TAG, "检查GPS文件内容失败", e);
        }
    }

    /**
     * 手动检查位置更新状态
     */
    private void manuallyCheckLocationUpdates() {
        Log.d(TAG, "手动检查位置更新");

        // 延迟检查，给模拟器一些启动时间
        new Handler().postDelayed(() -> {
            if (locationManager != null) {
                TencentLocation currentLocation = locationManager.getCurrentLocation();
                if (currentLocation != null) {
                    Log.d(TAG, "📍 手动检查 - 当前位置: " +
                            currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
                    onLocationUpdate(currentLocation);
                } else {
                    Log.w(TAG, "📍 手动检查 - 无当前位置数据");
                }
            }
        }, 5000); // 5秒后检查
    }

    private void checkNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        Log.d(TAG, "网络状态: " + (isConnected ? "已连接" : "未连接"));
        if (!isConnected) {
            uiManager.showToast("请检查网络连接");
        }
    }

    private void startPlaybackNavigation() {
        Log.d(TAG, "开始回放导航");

        try {
            // 诊断导航驱动状态
            Log.d(TAG, "navigatorDrive状态: " + (navigatorDrive != null ? "正常" : "null"));

            // 诊断路线规划请求器
            RoutePlanRequester requester = RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                    .start(new NavSearchPoint(39.983707, 116.30821))
                    .end(new NavSearchPoint(39.896835, 116.319423))
                    .build();

            Log.d(TAG, "路线规划请求器创建: " + (requester != null ? "成功" : "失败"));
            Log.d(TAG, "起点: 39.983707, 116.30821");
            Log.d(TAG, "终点: 39.896835, 116.319423");

            // 模拟路线规划
            navigatorDrive.searchRoute(
                    requester,
                    (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
                        Log.d(TAG, "🔔 路线规划回调被触发!");

                        if (error != null) {
                            Log.e(TAG, "❌ 路线规划失败: " + error.getMessage());
                            Log.e(TAG, "错误代码: " + error.getErrorCode());
                            Log.e(TAG, "错误详情: " + error.getErrorCode());
                            uiManager.showToast("路线规划失败: " + error.getMessage());
                            return;
                        }

                        if (navRoutePlan != null) {
                            Log.d(TAG, "✅ 路线规划成功，获取路线数据");
                            List<NavDriveRoute> routePlanList = navRoutePlan.getRouteDatas();
                            if (routePlanList != null) {
                                Log.d(TAG, "路线数量: " + routePlanList.size());
                                if (routePlanList.size() > 0) {
                                    NavDriveRoute route = routePlanList.get(0);
                                    Log.d(TAG, "✅ 第一条路线ID: " + route.getRouteId());
                                    Log.d(TAG, "路线距离: " + route.getDistance() + "米");
                                    Log.d(TAG, "预计时间: " + route.getTime() + "分钟");

                                    currentRoute = route; // 保存当前路线

                                    // 启用模拟器
                                    try {
                                        navigatorDrive.simulator()
                                                .setEnable(true)
                                                .setConfig(SimulatorConfig
                                                        .builder(SimulatorConfig.Type.REPLAY_LOCATIONS_FROM_FILE)
                                                        .setReplayFile(playbackFile)
                                                        .build()
                                                );
                                        Log.d(TAG, "✅ 模拟器配置完成");

                                        // 开始导航
                                        navigatorDrive.startNavigation(routePlanList.get(0).getRouteId());
                                        Log.d(TAG, "✅ 导航已启动，路线ID: " + routePlanList.get(0).getRouteId());

                                        // 🔥 关键修复：导航启动后重新注册位置监听  回放模式引起路线偏移
//                                        reRegisterLocationListenerAfterNavigation();

                                        // 更新导航状态
                                        isNavigating = true;
                                        uiManager.updateNavigationStatus(true, "回放测试目的地");

                                        // 检查位置监听器注册
                                        checkLocationListenerRegistration();
                                        // 启动测试监控
                                        setupTestMonitor();

                                        // 在导航启动后添加
//                                        manuallyCheckLocationUpdates();

                                    } catch (Exception e) {
                                        Log.e(TAG, "❌ 模拟器配置或导航启动失败", e);
                                    }

                                } else {
                                    Log.e(TAG, "❌ 路线列表为空");
                                    uiManager.showToast("路线列表为空");
                                }
                            } else {
                                Log.e(TAG, "❌ 路线数据为null");
                                uiManager.showToast("路线数据为null");
                            }
                        } else {
                            Log.e(TAG, "❌ navRoutePlan为null");
                        }
                    }
            );

            Log.d(TAG, "路线规划请求已发送，等待回调...");

        } catch (Exception e) {
            Log.e(TAG, "❌ 路线规划请求异常", e);
        }
    }

    /**
     * 检查位置监听器注册状态
     */
    private void checkLocationListenerRegistration() {
        Log.d(TAG, "检查位置监听器注册状态");

        // 检查LocationManager是否正常注册
        if (locationManager != null) {
            Log.d(TAG, "LocationManager状态: 正常");
            // 可以添加更多位置管理器状态检查
        } else {
            Log.e(TAG, "LocationManager状态: null");
        }

        // 检查导航驱动的观察者注册
        if (navigatorDrive != null) {
            Log.d(TAG, "navigatorDrive状态: 正常");
        } else {
            Log.e(TAG, "navigatorDrive状态: null");
        }
    }

    // 添加测试监控
    private void setupTestMonitor() {
        Log.d(TAG, "启动测试监控");
        Handler testHandler = new Handler();
        Runnable testMonitor = new Runnable() {
            @Override
            public void run() {
                if (isNavigating) {
                    Log.d(TAG, "✅ 测试监控 - 导航状态: 进行中");
                    Log.d(TAG, "✅ 测试监控 - WebSocket: " +
                            (webSocketManager != null && webSocketManager.isConnected() ? "已连接" : "未连接"));

                    if (currentRoute != null) {
                        Log.d(TAG, "✅ 测试监控 - 当前路线ID: " + currentRoute.getRouteId());
                    }

                    // 检查模拟器状态
//                    if (navigatorDrive != null) {
//                        boolean simEnabled = navigatorDrive.simulator().isEnable();
//                        Log.d(TAG, "✅ 测试监控 - 模拟器状态: " + (simEnabled ? "已启用" : "未启用"));
//                    }

                    // 详细的位置监听器状态检查
                    if (locationManager != null) {
                        TencentLocation currentLoc = locationManager.getCurrentLocation();
                        boolean isListening = locationManager.isLocationUpdatesActive();
                        String locationStatus = locationManager.getLocationStatus();

                        Log.d(TAG, "✅ 测试监控 - 位置监听器状态: " + (isListening ? "活跃" : "不活跃"));
                        Log.d(TAG, "✅ 测试监控 - 位置状态详情: " + locationStatus);

                        if (currentLoc != null) {
                            Log.d(TAG, "✅ 测试监控 - 当前位置: " +
                                    currentLoc.getLatitude() + ", " + currentLoc.getLongitude() +
                                    " 速度: " + currentLoc.getSpeed() + "m/s");
                        } else {
                            Log.w(TAG, "✅ 测试监控 - 当前位置: 无位置数据");
                        }
                    }

                    // 测试数据发送
                    if (isNavigating && webSocketManager != null && webSocketManager.isConnected()) {
                        sendNavigationData();
                    }
                }
                testHandler.postDelayed(this, 3000); // 每3秒监控一次
            }
        };
        testHandler.postDelayed(testMonitor, 3000);
    }

    private void initializeManagers() {
        Log.d(TAG, "初始化管理器");

        // 创建管理器实例

        try {
            locationManager = new LocationManager(this, this);
            Log.d(TAG, "LocationManager初始化成功");

            navigationManager = new NavigationManager(this, this, navigatorDrive);
            Log.d(TAG, "NavigationManager初始化成功");

            // ⭐ 关键修复：如果有缓存的定位信息，立即传递给 NavigationManager   不能有，否则总会到  北京 回放地点
//            if (locationManager.hasValidLocation()) {
//                TencentLocation currentLocation = locationManager.getCurrentLocation();
//                if (currentLocation != null) {
//                    navigationManager.setCurrentLocation(currentLocation);
//                    Log.d(TAG, "✅ 初始化时设置当前位置: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
//                }
//            }

            webSocketManager = new WebSocketManager();
            Log.d(TAG, "WebSocketManager初始化成功");

            weatherManager = new WeatherManager(this);
            Log.d(TAG, "WeatherManager初始化成功");

            uiManager = new UIManager(this);
            Log.d(TAG, "UIManager初始化成功");

            navDataBuilder = new NavDataBuilder();
            dataPacker = DataPacker.getInstance();

            weatherManager.setWeatherListener(this);
            Log.d(TAG, "所有管理器初始化完成");

        } catch (Exception e) {
            Log.e(TAG, "管理器初始化失败", e);
        }
    }

    private void initializeUI() {
        Log.d(TAG, "初始化UI");
        uiManager.initializeViews();
        uiManager.setupEventListeners(this::onDestinationConfirmed, this::onConnectClicked);
    }

    private void startBaseServices() {
        Log.d(TAG, "启动基础服务");
        locationManager.startLocationUpdates();
        weatherManager.startWeatherUpdates();   // 启动天气更新服务
    }

    // 用户交互回调
    @Override
    public void onDestinationConfirmed(String destination) {
        Log.d(TAG, "目的地确认: " + destination);
        if (locationManager != null && locationManager.hasValidLocation()) {

            // ⭐ 关键修复：在开始导航前，确保 NavigationManager 有最新位置
            TencentLocation currentLocation = locationManager.getCurrentLocation();
            if (currentLocation != null && navigationManager != null) {
                navigationManager.setCurrentLocation(currentLocation);
                Log.d(TAG, "✅ 导航前设置当前位置: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
            }

            // 使用 NavigationManager 开始导航
            if (navigationManager != null) {
                navigationManager.startNavigationToAddress(destination);
                uiManager.updateNavigationStatus(true, destination);
            }
        } else {
            uiManager.showToast("正在获取当前位置，请稍候...");
        }
    }
    @Override
    public void onConnectClicked(String ipPort) {
        Log.d(TAG, "连接WebSocket: " + ipPort);

        if (ipPort.isEmpty()) {
            uiManager.showToast("请先输入 IP:端口");
            return;
        }

        // 简单校验格式
        if (!ipPort.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+$")) {
            uiManager.showToast("格式错误，示例 192.168.1.30:54330");
            return;
        }

        String url = "ws://" + ipPort;

        webSocketManager.connect("ws://" + ipPort, new WebSocketManager.ConnectionCallback() {
            @Override
            public void onConnected() {
                uiManager.showToast("连接成功");
                uiManager.updateConnectionStatus(true);
            }

            @Override
            public void onDisconnected() {
                uiManager.showToast("连接断开");
                uiManager.updateConnectionStatus(false);
            }

            @Override
            public void onError(String error) {
                uiManager.showToast("连接失败: " + error);
                uiManager.updateConnectionStatus(false);
            }

            @Override
            public void onMessageReceived(String message) {
                uiManager.showToast("收到: " + message);
            }
        });
    }

    // ========== 接口实现 ==========


    /**
     * 导航启动后重新注册腾讯定位监听
     */
    private void reRegisterLocationListenerAfterNavigation() {
        Log.d(TAG, "准备重新注册腾讯定位监听 - 导航模式");

        // 使用带 Looper 参数的 Handler 构造函数
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (locationManager != null) {
                    Log.d(TAG, "调用导航模式重新注册");

                    // 使用专门为导航模式设计的方法
                    locationManager.reRegisterForNavigationMode();

                } else {
                    Log.e(TAG, "❌ LocationManager为null，无法重新注册");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ 重新注册腾讯定位异常", e);
            }
        }, 1500); // 延迟1.5秒
    }


    @Override
    public void onLocationUpdate(TencentLocation location) {

        // ⭐ 关键修复：无论是否在导航状态，都更新 NavigationManager 的位置
        if (navigationManager != null) {
            navigationManager.setCurrentLocation(location);
            Log.d(TAG, "✅ 已更新 NavigationManager 位置: " + location.getLatitude() + ", " + location.getLongitude());
        }

        // 添加时间戳和详细诊断
        long currentTime = System.currentTimeMillis();
        Log.d(TAG, "📍 位置更新 [" + currentTime + "]: " +
                location.getLatitude() + ", " + location.getLongitude() +
                " 精度: " + location.getAccuracy() + "m 速度: " + location.getSpeed() + "m/s");

        // 检查是否在回放模式
        if (isPlaybackMode) {
            Log.d(TAG, "🎯 回放模式位置更新");

            // 检查位置数据是否来自模拟器
            if (location.getProvider() != null) {
                Log.d(TAG, "位置来源: " + location.getProvider());
            }
        }

        // 检查位置数据有效性
        if (location.getLatitude() == 0.0 && location.getLongitude() == 0.0) {
            Log.w(TAG, "⚠️ 位置数据为0，可能无效");
            return;
        }

        // 检查位置时间戳
        long locationTime = location.getTime();
        long timeDiff = System.currentTimeMillis() - locationTime;
        Log.d(TAG, "位置时间戳差异: " + timeDiff + "ms");

        uiManager.updateLocationInfo(location);
        uiManager.updateSpeedInfo(location.getSpeed());

        // 通知天气管理器位置更新
        if (weatherManager != null) {
            weatherManager.updateLocation(location.getLatitude(), location.getLongitude());
        }

        // 通知导航管理器位置更新
        if (navigationManager != null) {
            navigationManager.onLocationUpdate(location);
        }

        // 检查路线偏离
        if (isNavigating && currentRoute != null) {
            boolean isDeviated = navDataBuilder.isRouteDeviated(location, currentRoute);
            if (isDeviated) {
                Log.w(TAG, "⚠️ 检测到路线偏离");
                uiManager.showToast("检测到路线偏离，大于500米");
            }
        }

        // 如果正在导航，发送数据
        if (isNavigating) {
            Log.d(TAG, "🚀 触发数据发送流程");
            sendNavigationData();
        }
    }

    @Override
    public void onLocationError(int errorCode, String errorMsg) {
        Log.e(TAG, "定位错误: " + errorCode + " - " + errorMsg);
        uiManager.showToast("定位失败: " + errorMsg);
    }

    @Override
    public void onSpeedCalculate(double speedMps) {
        uiManager.updateSpeedInfo(speedMps);
    }

    @Override
    public void onRoutePlanSuccess(NavDriveRoute route) {
        Log.d(TAG, "路线规划成功");
        uiManager.showToast("路线规划成功");
        currentRoute = route; // 保存当前路线

        // 路线规划成功后自动开始导航
        if (navigationManager != null) {
            navigationManager.startNavigation();
        }
    }

    @Override
    public void onRoutePlanFailed(String error) {
        Log.e(TAG, "路线规划失败: " + error);
        uiManager.showToast("路线规划失败: " + error);
    }

    @Override
    public void onNavigationStarted() {
        Log.d(TAG, "导航开始");
        isNavigating = true;
        startDataSending();

        uiManager.updateNavigationStatus(true, navigationManager.getCurrentDestination());

        // 重新注册定位监听，确保导航过程中能持续获取位置
        if (locationManager != null) {
            // 🔥 关键修复：使用新的重新注册方法
            reRegisterLocationListenerAfterNavigation();  // ← 替换或添加这行
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "NavAiActivity onResume");

        // 如果正在导航，重新注册位置监听（应用从后台恢复时）
        if (isNavigating) {
            Log.d(TAG, "应用恢复，重新注册位置监听");
            reRegisterLocationListenerAfterNavigation();
        }
    }

    @Override
    public void onNavigationStopped() {
        Log.d(TAG, "导航停止");
        isNavigating = false;
        stopDataSending();
        uiManager.updateNavigationStatus(false, "");
    }

    @Override
    public void onRouteDeviationDetected() {
        Log.w(TAG, "检测到路线偏离");
        uiManager.showToast("检测到路线偏离，检查1");
        // NavigationManager 会自动处理重新规划
    }

    @Override
    public void onDataSent(boolean success) {
        if (!success) {
            Log.w(TAG, "数据发送失败");
        }
    }

    @Override
    public void onConnectionStatusChanged(boolean connected) {
        uiManager.updateConnectionStatus(connected);
    }

    // ========== WeatherUpdateListener 接口实现 ==========

    @Override
    public void onWeatherUpdated(String temperature, String description, String iconCode) {
        Log.d(TAG, "天气数据更新: " + temperature + "°C, " + description);

        // 更新UI显示天气信息
        if (uiManager != null) {
            uiManager.updateWeatherInfo(temperature, description, iconCode);
        }

        // 如果正在导航，立即发送一次数据（因为天气数据更新了）
        if (isNavigating) {
            sendNavigationData();
        }
    }

    @Override
    public void onWeatherError(String error) {
        Log.e(TAG, "天气数据错误: " + error);
        // 可以显示错误提示，但保持使用缓存的天气数据
        if (uiManager != null) {
            uiManager.showToast("天气更新失败，使用缓存数据");
        }
    }


    // ========== 数据发送逻辑 ==========

    private void startDataSending() {
        // 启动数据发送线程或定时器
        // 这里可以复用原来的发送线程逻辑
    }

    private void stopDataSending() {
        // 停止数据发送
    }

    /**
     * 发送导航数据（包含天气数据）
     */
    private void sendNavigationData() {
        Log.d(TAG, "📦 开始发送导航数据");

        if (!isNavigating || currentRoute == null) {
            Log.w(TAG, "发送条件不满足 - 导航状态: " + isNavigating + ", 当前路线: " + (currentRoute != null));
            return;
        }

        try {
            // 获取导航信息
            NavDriveDataInfoEx navInfo = navigatorDrive.getNavRouteDataInfo();
            TencentLocation currentLocation = locationManager.getCurrentLocation();

            Log.d(TAG, "获取导航数据: " + (navInfo != null) + ", 当前位置: " + (currentLocation != null));

            // 构建数据结构
            TrafficEvent trafficEvent = navDataBuilder.buildTrafficEvent(currentRoute, navInfo);
            TrafficLight trafficLight = navDataBuilder.buildTrafficLight(currentRoute, currentLocation, navInfo);
            RouteOverview routeOverview = navDataBuilder.buildRouteOverview(currentRoute, navInfo);
            Weather weather = weatherManager.buildWeather(currentRoute);

            Log.d(TAG, "✅ 数据结构构建完成: " +
                    "交通事件=" + (trafficEvent != null) + ", " +
                    "交通灯=" + (trafficLight != null) + ", " +
                    "路线概览=" + (routeOverview != null) + ", " +
                    "天气=" + (weather != null));

            // 打包数据
            byte[] payload = dataPacker.packAll(trafficEvent, trafficLight, routeOverview, weather);
            Log.d(TAG, "📦 数据包大小: " + payload.length + " 字节");

            // 验证数据包
            if (!dataPacker.validatePacket(payload)) {
                Log.w(TAG, "❌ 数据包验证失败，跳过发送");
                return;
            }
            Log.d(TAG, "✅ 数据包验证通过");

            // 发送数据
            if (webSocketManager != null && webSocketManager.isConnected()) {
                boolean sent = webSocketManager.sendBinaryData(payload);
                Log.d(TAG, "📤 数据发送结果: " + (sent ? "成功" : "失败"));
            } else {
                Log.w(TAG, "🌐 WebSocket未连接，无法发送数据");
            }

            // 更新调试面板
            uiManager.updateDebugPanel(payload, trafficEvent, trafficLight, routeOverview, weather);

        } catch (Throwable t) {
            Log.e(TAG, "❌ 发送导航数据异常", t);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "NavAiActivity onDestroy");

        // 释放所有管理器资源
        if (locationManager != null) {
            locationManager.stopLocationUpdates();
        }
        if (webSocketManager != null) {
            webSocketManager.release();
        }
        if (navigationManager != null) {
            navigationManager.stopCurrentNavigation();
        }
        if (weatherManager != null) {
            weatherManager.release();
        }
        if (uiManager != null) {
            uiManager.release();
        }
        if (dataPacker != null) {
            dataPacker.resetCounter();
        }
        // 其他管理器的资源释放...
    }
}

// 十六进制显示
//"01 02 03 04 05 06 07 08 09 0A ..."

// 结构化解析
//        "数据包解析:
//        计数器: 1
//        交通事件: 总数=0, 最近距离=0m, 类型=0, 延迟=0s, 严重=0, 事故=0
//        交通灯: ID=0, 状态=0, 剩余时间=0s, 距离=0m, 速度=0km/h, 总数=0
//        路线概览: 距离=0.0km, 时间=0min, 费用=0.0元, 拥堵=0/0/0
//        天气: 路线哈希=0, 有效=1, 天气码=0, 温度=25°C, 置信度=15, 距离=0m"

//        ┌─────────────────────────────────────────────────────────────┐
//        │                    NavAiActivity                           │
//        ├─────────────────────────────────────────────────────────────┤
//        │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
//        │  │ Location    │  │ Navigation  │  │ WebSocketManager    │  │
//        │  │ Manager     │  │ Manager     │  │  - 连接管理         │  │
//        │  │ - 定位服务   │  │ - 路线规划  │  │  - 数据发送         │  │
//        │  │ - 速度计算   │  │ - 导航控制  │  │  - 心跳维护         │  │
//        │  └─────────────┘  └─────────────┘  └─────────────────────┘  │
//        │                                                             │
//        │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
//        │  │ Weather     │  │ UIManager   │  │ NavDataBuilder      │  │
//        │  │ Manager     │  │ - UI管理    │  │ - 数据结构构建       │  │
//        │  │ - 天气服务   │  │ - 事件处理  │  │ - 路线偏离检测      │  │
//        │  │ - 数据缓存   │  │ - 状态更新  │  └─────────────────────┘  │
//        │  └─────────────┘  └─────────────┘                          │
//        │                                                             │
//        │  ┌─────────────────────────────────────────────────────┐   │
//        │  │                   DataPacker                        │   │
//        │  │                - 数据打包器                         │   │
//        │  │                - 协议兼容性                         │   │
//        │  │                - 调试支持                           │   │
//        │  └─────────────────────────────────────────────────────┘   │
//        └─────────────────────────────────────────────────────────────┘

//        定位数据 → 路线规划 → 导航开始 → 数据构建 → 数据打包 → WebSocket发送
//        ↓           ↓           ↓           ↓           ↓           ↓
//        Location    Navigation  Navigation  NavData     DataPacker  WebSocket
//        Manager     Manager     Control     Builder                 Manager

//
//com.tencent.navix.power/
//        ├── NavAiActivity.java (主Activity)
//        ├── builders/
//        │   ├── DataPacker.java
//        │   └── NavDataBuilder.java
//        ├── interfaces/
//        │   ├── DataSendListener.java
//        │   ├── LocationListener.java
//        │   └── NavigationListener.java
//        ├── managers/
//        │   ├── LocationManager.java
//        │   ├── NavigationManager.java
//        │   ├── UIManager.java
//        │   ├── WeatherManager.java
//        │   └── WebSocketManager.java
//        ├── models/
//        │   ├── LatLng.java
//        │   ├── RouteOverview.java
//        │   ├── TrafficEvent.java
//        │   ├── TrafficLight.java
//        │   └── Weather.java
//        └── test/ (新建测试包)
//        ├── TestLauncherActivity.java
//        └── NavAiActivityTest.java