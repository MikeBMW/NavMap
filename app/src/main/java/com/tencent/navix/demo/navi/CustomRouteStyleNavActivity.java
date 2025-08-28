package com.tencent.navix.demo.navi;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.tencent.navix.api.config.RouteStyleConfig;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.BaseNavActivity;

import java.util.List;

public class CustomRouteStyleNavActivity extends BaseNavActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        layerRootDrive.setRouteStyleConfig(RouteStyleConfig.builder()
                .setDayMainRouteStyle(RouteStyleConfig.RouteStyle.ofMainDay()
                        .setErase(Color.parseColor("#9E9E9E"))
                        .setEraseStroke(Color.parseColor("#757575"))
                        .setUnknown(Color.parseColor("#03A9F4"))
                        .setUnknownStroke(Color.parseColor("#0288D1"))
                        .setSmooth(Color.parseColor("#8BC34A"))
                        .setSmoothStroke(Color.parseColor("#689F38"))
                        .setSlow(Color.parseColor("#FFEB3B"))
                        .setSlowStroke(Color.parseColor("#FBC02D"))
                        .setVerySlow(Color.parseColor("#FF9800"))
                        .setVerySlowStroke(Color.parseColor("#F57C00"))
                        .setJam(Color.parseColor("#F44336"))
                        .setJamStroke(Color.parseColor("#D32F2F"))
                        .build())
                .setDayBackupRouteStyle(RouteStyleConfig.RouteStyle.ofBackupDay()
                        .setErase(Color.parseColor("#9E9E9E"))
                        .setEraseStroke(Color.parseColor("#757575"))
                        .setUnknown(Color.parseColor("#03A9F4"))
                        .setUnknownStroke(Color.parseColor("#B3E5FC"))
                        .setSmooth(Color.parseColor("#8BC34A"))
                        .setSmoothStroke(Color.parseColor("#DCEDC8"))
                        .setSlow(Color.parseColor("#FFEB3B"))
                        .setSlowStroke(Color.parseColor("#FFF9C4"))
                        .setVerySlow(Color.parseColor("#FF9800"))
                        .setVerySlowStroke(Color.parseColor("#FFE0B2"))
                        .setJam(Color.parseColor("#F44336"))
                        .setJamStroke(Color.parseColor("#FFCDD2"))
                        .build())
                .build());
        // 算路并开启模拟导航
        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(new NavSearchPoint(40.007056, 116.389895))
                        .end(new NavSearchPoint(39.513005, 116.416642))
                        .build(),
                (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
                    if (error != null) {
                        // handle error
                        return;
                    }
                    if (navRoutePlan != null) {
                        // handle result
                        List<NavDriveRoute> routePlanList = navRoutePlan.getRouteDatas();
                        if (routePlanList != null && routePlanList.size() > 0) {
                            navigatorDrive.simulator().setEnable(true);
                            navigatorDrive.startNavigation(navRoutePlan.getRouteDatas().get(0).getRouteId());
                        }
                    }
                }
        );
    }
}
