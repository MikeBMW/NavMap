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


public class TruckRouteRequestActivity extends BaseNavActivity {

    private RouteView routeView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initRouteView();

        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(new NavSearchPoint(40.191873, 116.251917))
                        .end(new NavSearchPoint(39.715935, 116.462760))
                        .options(DriveRoutePlanOptions.Companion.newBuilder()
                                .licenseNumber("京A12345") // 车牌号
                                .truckOptions(DriveRoutePlanOptions.TruckOptions.newBuilder()
                                        .setHeight(4) // 设置货车高度。单位：m
                                        .setLength(10) // 设置货车长度。单位：m
                                        .setWidth(4) // 设置货车宽度。单位：m
                                        .setWeight(20) // 设置货车重量。单位：t
                                        .setAxisCount(2) // 设置货车轴数
                                        .setAxisLoad(4) // 设置货车轴重。单位：t
                                        .setPlateColor(DriveRoutePlanOptions.TruckOptions.PlateColor.Blue)  // 设置车牌颜色。
                                        .setTrailerType(DriveRoutePlanOptions.TruckOptions.TrailerType.Container) // 设置是否是拖挂车。
                                        .setTruckType(DriveRoutePlanOptions.TruckOptions.TruckType.Medium) // 设置货车类型。
                                        .setEmissionStandard(DriveRoutePlanOptions.TruckOptions.EmissionStandard.V)  // 设置排放标准
                                        .setPassType(DriveRoutePlanOptions.TruckOptions.PassType.NoNeed) // 设置通行证。
                                        .setEnergyType(DriveRoutePlanOptions.TruckOptions.EnergyType.Diesel) // 设置能源类型。
                                        .setFunctionType(DriveRoutePlanOptions.TruckOptions.FunctionType.Normal) // 设置
                                        .build())
                                .build()
                        )
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

    }

    private void initRouteView() {
        routeView = new RouteView(this, null);
        routeView.injectMap(layerRootDrive.getMapApi());
        ((FrameLayout)findViewById(R.id.app_root_view)).addView(routeView);
    }
}