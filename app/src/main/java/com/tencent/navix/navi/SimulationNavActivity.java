package com.tencent.navix.navi;

import android.os.Bundle;

//import androidx.annotation.Nullable;

import com.tencent.navix.api.config.SimulatorConfig;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.BaseNavActivity;
import com.tencent.navix.tts.DefaultTTSPlayer;

//import org.jetbrains.annotations.Nullable;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SimulationNavActivity extends BaseNavActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                            navigatorDrive.simulator()
                                    .setConfig(SimulatorConfig
                                            .builder(SimulatorConfig.Type.SIMULATE_LOCATIONS_ALONG_ROUTE)
                                            .setSimulateSpeed(55)  // 设置模拟导航速度
                                            .build()
                                    )
                                    .setEnable(true);
                            navigatorDrive.startNavigation(navRoutePlan.getRouteDatas().get(0).getRouteId());
                        }
                    }
                }
        );

        navigatorDrive.setTTSPlayer(new DefaultTTSPlayer());
    }
}
