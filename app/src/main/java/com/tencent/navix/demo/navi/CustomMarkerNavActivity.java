package com.tencent.navix.demo.navi;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.tencent.navix.api.config.BitmapCreator;
import com.tencent.navix.api.config.RouteMarkerStyleConfig;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavMode;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.demo.R;

import java.util.ArrayList;
import java.util.List;

public class CustomMarkerNavActivity extends BaseNavActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NavSearchPoint start = new NavSearchPoint(40.007056, 116.389895);
        NavSearchPoint end = new NavSearchPoint(39.511822, 116.416755);

        List<NavSearchPoint> waypoints = new ArrayList<>();
        waypoints.add(new NavSearchPoint(39.984050, 116.307664).setSearchId("user_set_waypoint_id_1"));
        waypoints.add(new NavSearchPoint(39.896559, 116.321161).setSearchId("user_set_waypoint_id_2"));

        // 自定义Marker样式
        layerRootDrive.setRouteMarkerStyleConfig(RouteMarkerStyleConfig.builder()
                // 设置起点Marker样式
                .setDefaultMakerOfStart(new BitmapCreator.ResourceBitmapCreator(R.drawable.app_icon_start), new BitmapCreator.NullBitmapCreator())
                // 设置终点Marker样式
                .setDefaultMarkerOfDest(new BitmapCreator.ResourceBitmapCreator(R.drawable.app_icon_finish), new BitmapCreator.NullBitmapCreator())
                // 设置途径点ID对应的Marker样式
                .addSpecificMarkerStyle("user_set_waypoint_id_2", new BitmapCreator.ResourceBitmapCreator(R.drawable.app_icon_start))
                .addSpecificMarkerStyle("user_set_waypoint_id_1", new BitmapCreator.ResourceBitmapCreator(R.drawable.app_icon_finish))
                .build()
        );

        // 算路并开启导航
        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(start)
                        .wayPoints(waypoints)
                        .end(end)
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
                            layerRootDrive.setNavMode(NavMode.MODE_OVERVIEW);
                        }
                    }
                }
        );
    }
}
