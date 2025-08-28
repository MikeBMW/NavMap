package com.tencent.navix.demo.navi;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tencent.navix.api.config.SimulatorConfig;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavEnlargedMapInfo;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.observer.SimpleNavigatorDriveObserver;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.core.NavigatorContext;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.demo.view.CustomNavView;
import com.tencent.navix.tts.DefaultTTSPlayer;
import com.tencent.navix.ui.api.config.EnlargedMapUIConfig;
import com.tencent.navix.ui.api.config.NavMapVisionConfig;
import com.tencent.navix.ui.api.config.UIComponentConfig;

import java.util.List;

public class LandscapeSimulationNavActivity extends BaseNavActivity {

    private CustomNavView customLayer;

    private int screenWidth = 0;

    private final int DP_10 =
            (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    10f,
                    NavigatorContext.share().getApplicationContext().getResources().getDisplayMetrics()
            );


    private final int DP_30 =
            (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    30f,
                    NavigatorContext.share().getApplicationContext().getResources().getDisplayMetrics()
            );


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        layerViewDrive.setUIComponentConfig(UIComponentConfig.builder()
                .setAllComponentVisibility(false)
                .setComponentVisibility(UIComponentConfig.UIComponent.ENLARGE_INFO_VIEW, true)
                .build()
        );

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        layerViewDrive.setEnlargedMapUIConfig(EnlargedMapUIConfig.builder()
                .setProgressBarEnable(true)
                .setMargins(DP_10, screenWidth / 4 * 3, DP_10)
                .build());

        customLayer = new CustomNavView(this);
        layerRootDrive.addViewLayer(customLayer);

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

        navigatorDrive.registerObserver(landscapeNavigatorObserver);

        // 设置左面板避让区域
        layerViewDrive.setNavMapVisionConfig(NavMapVisionConfig.builder()
                .setPadding(screenWidth / 4, DP_30, DP_30, DP_30)  // 导航中额外地图边距
                .build());

        customLayer.bindLayerRoot(layerRootDrive);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            screenWidth = getResources().getDisplayMetrics().widthPixels;
            layerViewDrive.setEnlargedMapUIConfig(EnlargedMapUIConfig.builder(layerViewDrive.getEnlargedMapUIConfig())
                    .setAspectRatio(1)
                    .setMargins(DP_10, screenWidth / 2, DP_10)
                    .build());
        } else {
            screenWidth = getResources().getDisplayMetrics().widthPixels;
            layerViewDrive.setEnlargedMapUIConfig(EnlargedMapUIConfig.builder(layerViewDrive.getEnlargedMapUIConfig())
                    .setAspectRatio(2)
                    .setMargins(DP_10, screenWidth / 3 * 2, DP_10)
                    .build());
        }
    }

    @Override
    protected void onDestroy() {
        customLayer.unbindLayerRoot();
        navigatorDrive.unregisterObserver(landscapeNavigatorObserver);
        layerRootDrive.removeViewLayer(customLayer);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            screenWidth = getResources().getDisplayMetrics().widthPixels;
            layerViewDrive.setEnlargedMapUIConfig(EnlargedMapUIConfig.builder(layerViewDrive.getEnlargedMapUIConfig())
                    .setAspectRatio(1)
                    .setRoundCorners(30, 30, 30, 30)
                    .setMargins(DP_10, screenWidth / 2, DP_10)
                    .build());
        } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            screenWidth = getResources().getDisplayMetrics().widthPixels;
            layerViewDrive.setEnlargedMapUIConfig(EnlargedMapUIConfig.builder(layerViewDrive.getEnlargedMapUIConfig())
                    .setAspectRatio(2)
                    .setRoundCorners(30, 30, 30, 30)
                    .setMargins(DP_10, screenWidth / 3 * 2, DP_10)
                    .build());
        }
    }

    SimpleNavigatorDriveObserver landscapeNavigatorObserver = new SimpleNavigatorDriveObserver() {
        @Override
        public void onHideEnlargedMap() {
            super.onHideEnlargedMap();
            layerViewDrive.setNavMapVisionConfig(NavMapVisionConfig.builder()
                    .setPadding(screenWidth / 4, DP_30, DP_30, DP_30)  // 导航中额外地图边距
                    .build());
        }

        @Override
        public void onShowEnlargedMap(NavEnlargedMapInfo navEnlargedMapInfo) {
            super.onShowEnlargedMap(navEnlargedMapInfo);
            layerViewDrive.setNavMapVisionConfig(NavMapVisionConfig.builder()
                    .setPadding(screenWidth / 4, DP_30, screenWidth / 4, DP_30)  // 导航中额外地图边距
                    .build());
        }
    };
}
