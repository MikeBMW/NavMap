package com.tencent.navix.demo.route;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.demo.R;


public class RouteRequestActivity extends BaseNavActivity {

    private RouteView routeView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initRouteView();
        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(new NavSearchPoint(40.007056, 116.389895))
                        .end(new NavSearchPoint(39.894551, 116.321515))
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
