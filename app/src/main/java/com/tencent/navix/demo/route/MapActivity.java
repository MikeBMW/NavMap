package com.tencent.navix.demo.route;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tencent.navix.demo.R;
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory;
import com.tencent.tencentmap.mapsdk.maps.TencentMap;
import com.tencent.tencentmap.mapsdk.maps.TextureMapView;
import com.tencent.tencentmap.mapsdk.maps.UiSettings;
import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptorFactory;
import com.tencent.tencentmap.mapsdk.maps.model.LatLng;
import com.tencent.tencentmap.mapsdk.maps.model.MyLocationStyle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {

    private static final String TAG = "MapActivity";
    private TextureMapView mapView;
    private TencentMap tencentMap;
    private boolean isFirstLoc = true;
    private boolean isFollowing = true; // 是否开启位置跟随
    private TencentMap.OnMyLocationChangeListener locationListener;

    // UI 组件
    private Button locationButton;
    private Switch followSwitch;
    private UiSettings uiSettings;

    // 权限相关
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int LOCATION_SETTINGS_REQUEST = 100;
    private List<String> missingPermissions = new ArrayList<>();
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // 初始化地图
        mapView = findViewById(R.id.map_view);
        tencentMap = mapView.getMap();
        uiSettings = tencentMap.getUiSettings();
        uiSettings.setZoomGesturesEnabled(true); // 启用缩放手势

        // 初始化UI组件
        locationButton = findViewById(R.id.btn_location);
        followSwitch = findViewById(R.id.switch_follow);

        // 设置定位按钮点击事件
        locationButton.setOnClickListener(v -> {
            Log.d(TAG, "定位按钮被点击");
            if (checkPermissions()) {
                startLocationUpdates();
            } else {
                Log.d(TAG, "请求位置权限");
                requestLocationPermissions();
            }
        });

        // 设置跟随开关
        followSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isFollowing = isChecked;
            Log.d(TAG, "跟随模式: " + isFollowing);
            if (isChecked) {
                // 确保定位服务已启动
                if (checkPermissions() && !isLocationServiceActive()) {
                    startLocationUpdates();
                }

                // 尝试获取当前位置
                attemptToGetLocation();
            }
        });

        // 配置定位样式
        setupLocationStyle();
        // 设置定位监听器
        setupLocationListener();

        // 自动启动定位（如果权限已授予）
        if (checkPermissions()) {
            startLocationUpdates();
        }
    }

    // 尝试获取当前位置
    private void attemptToGetLocation() {
        Location location = tencentMap.getMyLocation();
        if (location != null) {
            moveToCurrentLocation(location);
        } else {
            Toast.makeText(this, "正在获取位置...", Toast.LENGTH_SHORT).show();

            // 延迟后再次尝试
            new Handler().postDelayed(() -> {
                Location loc = tencentMap.getMyLocation();
                if (loc != null) {
                    moveToCurrentLocation(loc);
                } else {
                    Log.w(TAG, "获取位置失败，请检查定位设置");
                    Toast.makeText(MapActivity.this,
                            "获取位置失败，请检查定位设置",
                            Toast.LENGTH_LONG).show();

                    // 提示用户开启定位服务
                    new Handler().postDelayed(() -> {
                        showLocationSettings();
                    }, 1000);
                }
            }, 2000);
        }
    }

    // 显示定位设置
    private void showLocationSettings() {
        Toast.makeText(this, "正在打开定位设置...", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        try {
            startActivityForResult(intent, LOCATION_SETTINGS_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "无法打开定位设置", e);
            Toast.makeText(this, "无法打开定位设置，请手动开启", Toast.LENGTH_LONG).show();
        }
    }

    // 检查定位服务是否激活
    private boolean isLocationServiceActive() {
        return tencentMap != null && tencentMap.isMyLocationEnabled();
    }

    // 实现监听器方法
    private void setupLocationListener() {
        locationListener = location -> {
            if (location != null) {
                Log.d(TAG, "收到位置更新: " + location.getLatitude() + ", " + location.getLongitude());

                if (isFollowing) {
                    moveToCurrentLocation(location);
                    isFirstLoc = false;
                }
            } else {
                Log.w(TAG, "位置更新为空");
            }
        };
        tencentMap.setOnMyLocationChangeListener(locationListener);
    }

    // 配置定位样式
    private void setupLocationStyle() {
        MyLocationStyle locationStyle = new MyLocationStyle();
        locationStyle.strokeColor(Color.TRANSPARENT);

        try {
            // 设置定位图标
            Method iconMethod = locationStyle.getClass().getMethod("icon", com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptor.class);
            iconMethod.invoke(locationStyle, BitmapDescriptorFactory.defaultMarker());
            Log.d(TAG, "自定义定位图标设置成功");
        } catch (Exception ex) {
            Log.w(TAG, "无法设置自定义定位图标，使用默认样式");
        }

        try {
            // 设置填充颜色
            Method fillColorMethod = locationStyle.getClass().getMethod("fillColor", int.class);
            fillColorMethod.invoke(locationStyle, Color.TRANSPARENT);
        } catch (Exception ex) {
            // 忽略
        }

        // 设置定位样式
        tencentMap.setMyLocationStyle(locationStyle);
    }

    // 检查并请求权限
    private boolean checkPermissions() {
        missingPermissions.clear();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        return missingPermissions.isEmpty();
    }

    // 请求位置权限
    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                PERMISSION_REQUEST_CODE
        );
    }

    // 开始定位更新 - 增强版
    private void startLocationUpdates() {
        Log.d(TAG, "启动定位服务");

        // 确保定位服务已启用
        if (!tencentMap.isMyLocationEnabled()) {
            tencentMap.setMyLocationEnabled(true);
            Log.d(TAG, "定位图层已启用");
        }

        uiSettings.setMyLocationButtonEnabled(false); // 禁用SDK自带的定位按钮

        // 设置定位模式
        try {
            Method method = tencentMap.getClass().getMethod("setLocationSource", int.class);
            method.invoke(tencentMap, isFollowing ? 1 : 0); // 根据跟随状态设置模式
            Log.d(TAG, "定位模式设置为: " + (isFollowing ? "跟随" : "普通"));
        } catch (Exception e) {
            Log.e(TAG, "设置定位模式失败", e);
            // 尝试兼容方案
            try {
                tencentMap.getClass().getMethod("setMyLocationTrackingMode", int.class)
                        .invoke(tencentMap, isFollowing ? 1 : 0);
            } catch (Exception ex) {
                Log.e(TAG, "备选定位模式设置也失败", ex);
            }
        }

        // 确保监听器已设置
        if (locationListener == null) {
            Log.d(TAG, "设置位置监听器");
            setupLocationListener();
        }

        // 尝试获取当前位置
        attemptToGetLocation();
    }

    // 停止定位更新
    private void stopLocationUpdates() {
        Log.d(TAG, "停止定位服务");
        if (tencentMap != null) {
            tencentMap.setMyLocationEnabled(false);
        }
    }

    // 移动到当前位置
    private void moveToCurrentLocation(Location location) {
        if (location == null) {
            Log.w(TAG, "移动位置失败：位置为空");
            return;
        }

        Log.d(TAG, "移动到位置: " + location.getLatitude() + ", " + location.getLongitude());

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        tencentMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));

        // 显示位置信息
        String locationInfo = String.format("位置: %.6f, %.6f\n精度: %.1f米",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());
        Toast.makeText(this, locationInfo, Toast.LENGTH_SHORT).show();
        isFirstLoc = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "位置权限已授予");
                // 延迟确保定位服务就绪
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startLocationUpdates();
                }, 300);
            } else {
                Log.w(TAG, "位置权限被拒绝");
                Toast.makeText(this, "需要位置权限才能使用定位功能", Toast.LENGTH_SHORT).show();
                // 再次请求权限
                new Handler().postDelayed(() -> {
                    requestLocationPermissions();
                }, 2000);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOCATION_SETTINGS_REQUEST) {
            Log.d(TAG, "从定位设置返回");
            // 用户可能已经启用了定位服务
            if (checkPermissions()) {
                startLocationUpdates();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        Log.d(TAG, "onResume");

        // 自动恢复定位服务
        if (checkPermissions() && (followSwitch.isChecked() || isFollowing)) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        Log.d(TAG, "onPause");
        stopLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        Log.d(TAG, "onDestroy");
        stopLocationUpdates();
    }
}