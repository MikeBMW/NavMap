package com.tencent.navix.demo.navi;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.tencent.navix.api.config.SimulatorConfig;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavDriveRoutePlan;
import com.tencent.navix.api.model.NavNavigationStartFail;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.observer.SimpleNavigatorDriveObserver;
import com.tencent.navix.api.plan.DriveRoutePlanOptions;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.tts.DefaultTTSPlayer;

import java.util.List;

public class SimulationSliceNavActivity extends BaseNavActivity {

    private NavDriveRoutePlan driveRoutePlan;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 注册监听者
        navigatorDrive.registerObserver(simpleNavigatorDriveObserver);

        // 算路并开启模拟导航
        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(new NavSearchPoint(40.056921, 116.615109))
                        .end(new NavSearchPoint(31.194228, 121.326356))
                        .options(DriveRoutePlanOptions.Companion.newBuilder()
                                /**
                                 * 0: 不分片;
                                 * 1: 两段分片;
                                 * 2: 三段分片;
                                 *
                                 * 当设置为三段分片时，需要监听onNavigationDataReady回调。
                                 * 并当需要监听onNavigationDataReady回调回调为成功后，才能开启导航，否则可能开启导航失败
                                 *
                                 */
//                                .setRequestSlice(2)
                                .build())
                        .build(),
                (DriveRoutePlanRequestCallback) (navRoutePlan, error) -> {
                    if (error != null) {
                        // handle error
                        return;
                    }
                    if (navRoutePlan instanceof NavDriveRoutePlan) {
                        // handle result
                        driveRoutePlan = (NavDriveRoutePlan) navRoutePlan;
                    }
                }
        );

        navigatorDrive.setTTSPlayer(new DefaultTTSPlayer());

    }

    SimpleNavigatorDriveObserver simpleNavigatorDriveObserver = new SimpleNavigatorDriveObserver() {
        @Override
        public void onDidStartNavigationFail(String s, NavNavigationStartFail navNavigationStartFail) {
            super.onDidStartNavigationFail(s, navNavigationStartFail);
            Log.d("SliceNav", "onDidStartNavigationFail " + navNavigationStartFail.getCode());
        }

//        @Override
//        public void onNavigationDataReady(String sessionID, boolean success) {
//            super.onNavigationDataReady(sessionID, success);
//
//            // 对应sessionID的算路结果导航数据就绪
//            if (driveRoutePlan != null
//                    && success
//                    && sessionID.equals(driveRoutePlan.getNavSessionId())) {
//
//                List<NavDriveRoute> routePlanList = driveRoutePlan.getRouteDatas();
//                if (routePlanList != null && routePlanList.size() > 0) {
//                    navigatorDrive.simulator()
//                            .setConfig(SimulatorConfig
//                                    .builder(SimulatorConfig.Type.SIMULATE_LOCATIONS_ALONG_ROUTE)
//                                    .setSimulateSpeed(55)  // 设置模拟导航速度
//                                    .build()
//                            )
//                            .setEnable(true);
//                    navigatorDrive.startNavigation(routePlanList.get(0).routeId);
//                }
//            }
//            Log.d("SliceNav", "onNavigationDataReady sessionID=" + sessionID + ", success=" + success);
//
//        }
    };
}
