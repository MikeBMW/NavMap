package com.tencent.navix.demo.navi;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.tencent.navix.api.config.BitmapCreator;
import com.tencent.navix.api.config.LocatorStyleConfig;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.demo.R;

import java.util.List;

public class CustomLocatorNavActivity extends BaseNavActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 自定义自车标样式
        layerRootDrive.setLocatorStyleConfig(LocatorStyleConfig.builder()
                .setCompassEnable(true)
                .setDayLocatorStyle(LocatorStyleConfig.LocatorStyle.builder()
                        .setLocator(new BitmapCreator.ResourceBitmapCreator(R.drawable.app_icon_locator_car))
                        .setLocatorForWeakGps(new BitmapCreator.ResourceBitmapCreator(R.drawable.app_icon_locator_car))
                        .build()
                )
                .setNightLocatorStyle(LocatorStyleConfig.LocatorStyle.builder()
                        .setLocator(new BitmapCreator.ResourceBitmapCreator(R.drawable.app_icon_locator_car_night))
                        .setLocatorForWeakGps(new BitmapCreator.ResourceBitmapCreator(R.drawable.app_icon_locator_car_night))
                        .build()
                )
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
