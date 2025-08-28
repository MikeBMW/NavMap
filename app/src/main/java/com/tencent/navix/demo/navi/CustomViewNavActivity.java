package com.tencent.navix.demo.navi;

import android.os.Bundle;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import com.tencent.navix.api.model.NavDriveDataInfo;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavDriveRouteData;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.observer.SimpleNavigatorDriveObserver;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.demo.view.CustomNavView;
import com.tencent.navix.ui.api.config.UIComponentConfig;
import com.tencent.navix.ui.component.NavTrafficBar;

import java.util.List;

public class CustomViewNavActivity extends BaseNavActivity {

    private CustomNavView customLayer;
    private NavTrafficBar navTrafficBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 关闭默认面板
        layerViewDrive.setUIComponentConfig(UIComponentConfig.builder()
                .setAllComponentVisibility(false)
                .setComponentVisibility(UIComponentConfig.UIComponent.ENLARGE_INFO_VIEW, false)
                .build()
        );

        // 创建自定义，并添加到导航地图层
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
                            navigatorDrive.simulator().setEnable(true);
                            navigatorDrive.startNavigation(navRoutePlan.getRouteDatas().get(0).getRouteId());
                        }
                    }
                }
        );

        customLayer.bindLayerRoot(layerRootDrive);

        // 自定义TrafficBar
        navTrafficBar = new NavTrafficBar(this);
        navTrafficBar.setVisibility(View.GONE); // 需要TrafficBar有数据后才能展示
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        customLayer.addView(navTrafficBar, layoutParams);

        navigatorDrive.registerObserver(driveObserver);
    }

    @Override
    protected void onDestroy() {
        navigatorDrive.unregisterObserver(driveObserver);
        customLayer.unbindLayerRoot();
        // 移除默认面板
        layerRootDrive.removeViewLayer(customLayer);
        super.onDestroy();
    }


    private NavDriveDataInfo latestDriveDataInfo;
    private final SimpleNavigatorDriveObserver driveObserver = new SimpleNavigatorDriveObserver() {
        @Override
        public void onNavDataInfoUpdate(NavDriveDataInfo navDriveDataInfo) {
            super.onNavDataInfoUpdate(navDriveDataInfo);
            if (navTrafficBar != null) {
                // 更新数据给TrafficBar
                navTrafficBar.onNavDataInfoUpdate(navDriveDataInfo);

                if (latestDriveDataInfo == null) {
                    navTrafficBar.setVisibility(View.VISIBLE);
                }
            }
            latestDriveDataInfo = navDriveDataInfo;
        }

        @Override
        public void onDidStartNavigation() {
            super.onDidStartNavigation();
        }

        @Override
        public void onDidStopNavigation() {
            super.onDidStopNavigation();
        }

        @Override
        public void onUpdateRouteTraffic(List<NavDriveRouteData> list) {
            super.onUpdateRouteTraffic(list);
            if (navTrafficBar != null) {
                // 更新路况给TrafficBar
                navTrafficBar.updateRouteTraffic(list);
            }
        }
    };
}
