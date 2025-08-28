package com.tencent.navix.demo.route;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanOptions;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.demo.R;
import com.tencent.tencentmap.mapsdk.maps.model.LatLng;
import com.tencent.tencentmap.mapsdk.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

public class CustomRouteRequestActivity extends BaseNavActivity {

    private RouteView routeView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initRouteView();

        // 添加自定义避让区域
        List<List<LatLng>> avoidAreaList = new ArrayList<>();
        List<LatLng> area = new ArrayList<>();
        area.add(new LatLng(39.983171, 116.295090));
        area.add(new LatLng(39.830686, 116.277580));
        area.add(new LatLng(39.833059, 116.485634));
        area.add(new LatLng(39.984486, 116.485291));
        avoidAreaList.add(area);

        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(new NavSearchPoint(40.007056, 116.389895))
                        .end(new NavSearchPoint(39.513005, 116.416642))
                        .options(DriveRoutePlanOptions.Companion.newBuilder().avoidAreaList(
                            avoidAreaList
                        ).build())
                        .build(),
                (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
                    if (error != null) {
                        // handle error
                        return;
                    }
                    if (navRoutePlan != null) {
                        // handle result
                        routeView.updateRoutePlan(navRoutePlan);
                    }
                }
        );

        // 绘制自定义区域
        layerRootDrive.getMapApi().addPolyline(
                new PolylineOptions().addAll(area).add(area.get(0)).width(2).color(Color.RED)
        );
    }

    private void initRouteView() {
        routeView = new RouteView(this, null);
        routeView.injectMap(layerRootDrive.getMapApi());
        ((FrameLayout)findViewById(R.id.app_root_view)).addView(routeView);
    }
}