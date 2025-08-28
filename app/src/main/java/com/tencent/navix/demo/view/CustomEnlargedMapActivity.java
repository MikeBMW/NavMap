package com.tencent.navix.demo.view;

import static com.tencent.navix.ui.api.config.UIComponentConfig.UIComponent.*;

import android.os.Bundle;
import android.util.TypedValue;

import androidx.annotation.Nullable;

import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.core.NavigatorContext;
import com.tencent.navix.demo.BaseNavActivity;
import com.tencent.navix.ui.api.config.EnlargedMapUIConfig;
import com.tencent.navix.ui.api.config.UIComponentConfig;

import java.util.List;

public class CustomEnlargedMapActivity extends BaseNavActivity {

    private final int DP_40 =
            (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    40f,
                    NavigatorContext.share().getApplicationContext().getResources().getDisplayMetrics()
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        layerViewDrive.setUIComponentConfig(UIComponentConfig.builder()
                // 开启放大图，其他UI组件按需开启或关闭
                .setAllComponentVisibility(false)
                .setComponentVisibility(ENLARGE_INFO_VIEW, true)
                .build());

        // 设置放大图的比例，Margins等参数
        layerViewDrive.setEnlargedMapUIConfig(EnlargedMapUIConfig.builder()
                .setAspectRatio(1.5f)
                .setProgressBarEnable(false)
                .setMargins(0, DP_40, DP_40)
                .build());

        // 算路并开启模拟导航
        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(new NavSearchPoint(39.984071,  116.308096))
                        .end(new NavSearchPoint(39.896938, 116.316483))
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
