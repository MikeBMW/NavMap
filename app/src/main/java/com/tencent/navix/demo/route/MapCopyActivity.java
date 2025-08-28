package com.tencent.navix.demo.route;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.navix.demo.R;
import com.tencent.tencentmap.mapsdk.maps.TextureMapView;
import com.tencent.tencentmap.mapsdk.maps.UiSettings;
import com.tencent.tencentmap.mapsdk.maps.model.Marker;

import java.util.ArrayList;
import java.util.List;

public class MapCopyActivity extends AppCompatActivity implements  TencentLocationListener {

    private TextureMapView mapView;
    private TencentLocationManager locationManager;
    private TencentLocationManager locationRequest;
    private boolean isFirstLoc = true;

    // UI 组件
    private Button locationButton;
    private Switch followSwitch;
    private Marker locationMarker;
    private UiSettings uiSettings;

    // 权限相关
    private static final int PERMISSION_REQUEST_CODE = 1;
    private List<String> missingPermissions = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        mapView = findViewById(R.id.map_view);
//        mapView.onCreate(savedInstanceState);
//        mapView.getMapAsync(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLocationChanged(TencentLocation tencentLocation, int i, String s) {

    }

    @Override
    public void onStatusUpdate(String s, int i, String s1) {

    }

    @Override
    public void onGnssInfoChanged(Object o) {

    }

    @Override
    public void onNmeaMsgChanged(String s) {

    }

}
