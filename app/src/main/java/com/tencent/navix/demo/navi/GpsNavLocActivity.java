package com.tencent.navix.demo.navi;

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
import com.tencent.navix.demo.R;

import java.io.IOException;
import java.util.List;

import android.location.Geocoder;
import android.location.Address;
//import com.tencent.map.search.SearchManager;
//import com.tencent.map.search.param.SearchParam;
//import com.tencent.map.search.result.PoiResult;


public class GpsNavLocActivity extends BaseNavActivity implements TencentLocationListener {

    // 导航相关变量
    private RoutePlanRequester routePlanRequester;
    private NavDriveRoute currentRoute;

    // 定位相关变量
    private TencentLocationManager locationManager;
    private TencentLocationRequest locationRequest;
    private TencentLocation currentLocation; // 保存当前定位信息

    // UI控件
//    private TextView tvLocationInfo;
    private EditText etDestination;
    private Button btnConfirmDestination;
    // 用于显示定位信息的 TextView（可根据实际布局调整）
    private TextView tvLocationInfo;


//    @Override
//    protected void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        // 设置布局，假设你有对应的布局文件 activity_gps_nav_loc.xml
////        setContentView(R.layout.activity_gps_nav_loc);
////
////        // 初始化用于显示定位信息的控件
////        tvLocationInfo = findViewById(R.id.tv_location_info);
//        // 先加载父类布局（父类内部会 setContentView 加载 activity_nav.xml）
//
//        // 动态加载自定义布局 activity_gps_nav_loc.xml
//        LinearLayout customLayout = (LinearLayout) LayoutInflater.from(this)
//                .inflate(R.layout.activity_gps_nav_loc, null);
//        // 获取父布局容器（假设父类布局里有可添加子 View 的容器，如 FrameLayout 等，这里用 app_root_view 举例）
//        FrameLayout rootView = findViewById(R.id.app_root_view);
//        // 将自定义布局添加到父布局里
//        rootView.addView(customLayout);
//
//        // 获取定位信息显示的 TextView
//        tvLocationInfo = customLayout.findViewById(R.id.tv_location_info);
//
//
//        // 1. 初始化定位
//        initLocation();
//        // 2. 执行算路并开启导航逻辑
//        searchRouteAndStartNavigation();
//    }


//    @Override
//    protected void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        // 1. 动态加载自定义布局 activity_gps_nav_loc.xml
//        // 获取父布局的根容器（假设父类布局里有 FrameLayout，id 为 app_root_view ）
//        FrameLayout rootView = findViewById(R.id.app_root_view);
//        if (rootView == null) {
//            Log.e("GpsNavLocActivity", "父布局根容器 app_root_view 未找到，初始化失败");
//            finish();
//            return;
//        }
//
//        // 加载自定义布局，注意第三个参数传 false，后续手动添加
//        LinearLayout customLayout = (LinearLayout) LayoutInflater.from(this)
//                .inflate(R.layout.activity_gps_nav_loc, rootView, false);
//
//        // 将自定义布局添加到父布局的根容器里
//        rootView.addView(customLayout);
//
//        // 2. 获取定位信息显示的 TextView
//        tvLocationInfo = customLayout.findViewById(R.id.tv_location_info);
//        if (tvLocationInfo == null) {
//            Log.w("GpsNavLocActivity", "自定义布局中未找到 tv_location_info TextView");
//        }
//
//        // 3. 初始化定位
//        initLocation();
//        // 4. 执行算路并开启导航逻辑
//        searchRouteAndStartNavigation();
//    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 把我们的输入面板“盖”在地图之上
        FrameLayout root = findViewById(R.id.app_root_view);   // 父类布局根容器
        View inputPanel = getLayoutInflater().inflate(R.layout.activity_gps_nav_loc, root, false);
        root.addView(inputPanel);

        // 再绑定控件
        tvLocationInfo = inputPanel.findViewById(R.id.tv_location_info);
        etDestination = inputPanel.findViewById(R.id.et_destination);
        btnConfirmDestination = inputPanel.findViewById(R.id.btn_confirm_destination);

        btnConfirmDestination.setOnClickListener(v -> startNavigationWithInput());

//        btnConfirmDestination.setOnClickListener(v -> {
//            // 1. 隐藏底部栏
//            findViewById(R.id.bottom_bar).setVisibility(View.GONE);
//
//            // 2. 你的导航逻辑
//            startNavigationWithInput();
//        });
        // 初始化定位
        initLocation();
        btnConfirmDestination.setOnClickListener(v -> startNavigationWithInput());
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
//        btnConfirmDestination.setOnClickListener(v -> startNavigationWithInput());
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

    /**
     * 解析目的地（地址文本转经纬度）
     * 这里使用腾讯定位SDK的逆地理编码功能，也可替换为其他地图SDK的地址解析
     */
//    private void parseDestination(String address, OnDestinationParsedListener listener) {
//        // 步骤 1：获取 AndroidManifest.xml 中配置的 TencentMapSDK Key
//        String sdkKey = "";
//        try {
//            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
//                    getPackageName(), PackageManager.GET_META_DATA);
//            if (appInfo.metaData != null) {
//                sdkKey = appInfo.metaData.getString("TencentMapSDK");
//            }
//        } catch (PackageManager.NameNotFoundException e) {
//            e.printStackTrace();
//        }
//
//        // 步骤 2：构造 Pair 对象（第二个参数若不需要 secret 可传空字符串）
//        Pair<String, String> keyPair = new Pair<>(sdkKey, "");
//        // 初始化 TencentLocationManager，传入对应的 Key 等参数（和 DemoMapActivity 保持一致）
////        locationManager = TencentLocationManager.getInstance(this, new Pair<>("你的Key", ""));
////        locationManager = TencentLocationManager.getInstance(this, keyPair);
//
//
//        // 使用TencentLocationManager的地址解析功能
//        TencentLocationManager locationManager = TencentLocationManager.getInstance(this,keyPair);
//        // 发起地址解析（同步方法，建议在子线程执行）
//        new Thread(() -> {
//            // 解析地址（返回的location中包含经纬度）
//            TencentLocation location = locationManager.getLocationFromAddress(address);
//            runOnUiThread(() -> {
//                if (location != null && location.getErrorCode() == 0) {
//                    // 解析成功
//                    listener.onParsed(location.getLatitude(), location.getLongitude());
//                } else {
//                    // 解析失败
//                    String error = location != null ? location.getErrorMsg() : "未知错误";
//                    listener.onError(error);
//                }
//            });
//        }).start();
//    }


    private void parseDestination(String address, OnDestinationParsedListener listener) {
        Geocoder geocoder = new Geocoder(this);
        new Thread(() -> {
            try {
                // 地址解析（最多返回 1 个结果）
                List<Address> addresses = geocoder.getFromLocationName(address, 1);
                if (!addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    double lat = addr.getLatitude();
                    double lng = addr.getLongitude();
                    runOnUiThread(() -> listener.onParsed(lat, lng));
                } else {
                    runOnUiThread(() -> listener.onError("地址解析失败"));
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> listener.onError("网络或解析错误"));
            }
        }).start();
    }

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

    @Override
    public void onStatusUpdate(String s, int i, String s1) {

    }

    /**
     * 简单封装的显示 Toast 方法
     */
//    private void showToast(String msg) {
//        runOnUiThread(() -> {
//            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
//        });
//    }
    // 显示Toast
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // 解析目的地的回调接口
    interface OnDestinationParsedListener {
        void onParsed(double lat, double lng);
        void onError(String error);
    }
}