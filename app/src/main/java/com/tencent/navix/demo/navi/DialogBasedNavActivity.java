package com.tencent.navix.demo.navi;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.tencent.navix.api.NavigatorZygote;
import com.tencent.navix.api.config.SimulatorConfig;
import com.tencent.navix.api.layer.NavigatorLayerRootDrive;
import com.tencent.navix.api.layer.NavigatorViewStub;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavMode;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.navigator.NavigatorDrive;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.demo.R;
import com.tencent.navix.tts.DefaultTTSPlayer;
import com.tencent.navix.ui.NavigatorLayerViewDrive;
import com.tencent.navix.ui.api.config.UIComponentConfig;

import java.util.List;

public class DialogBasedNavActivity extends AppCompatActivity {

    private NavigatorDrive navigatorDrive;
    private NavigatorLayerRootDrive layerRootDrive;
    private NavigatorLayerViewDrive layerViewDrive;
    private View dialogView;
    private boolean resumed = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog_nav);

        findViewById(R.id.start_nav).setOnClickListener(v -> {
            showNavDialog();
        });

        // 创建驾车 NavigatorDrive
        navigatorDrive = NavigatorZygote.with(getApplicationContext()).navigator(NavigatorDrive.class);
        navigatorDrive.setTTSPlayer(new DefaultTTSPlayer());

        searchRouteAndSimulateNav();
    }

    private void searchRouteAndSimulateNav() {
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
    }

    private void showNavDialog() {

        LayoutInflater inflater = getLayoutInflater();
        dialogView = inflater.inflate(R.layout.dialog_nav, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(DialogBasedNavActivity.this);
        builder.setView(dialogView);
        builder.setOnDismissListener(dialog -> {
            // 移除默认面板
            layerRootDrive.removeViewLayer(layerViewDrive);
        });
        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        window.setGravity(Gravity.BOTTOM);
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);


        // 创建导航地图层 NavigatorLayerRootDrive
        NavigatorViewStub navigatorViewStub = dialogView.findViewById(R.id.navigator_view_stub);
        navigatorViewStub.setTravelMode(NavRouteReqParam.TravelMode.TravelModeDriving);
        navigatorViewStub.inflate();
        layerRootDrive = navigatorViewStub.getNavigatorView();

        // 创建默认面板 NavigatorLayerViewDrive，并添加到导航地图层
        layerViewDrive = new NavigatorLayerViewDrive(this);
        layerViewDrive.setUIComponentConfig(UIComponentConfig.builder(layerViewDrive.getUIComponentConfig())
                .setComponentVisibility(UIComponentConfig.UIComponent.BOTTOM_PANEL_VIEW, false)
                .build());
        layerRootDrive.addViewLayer(layerViewDrive);

        // 将导航地图层绑定到Navigator
        navigatorDrive.bindView(layerRootDrive);

        navigatorDrive.setTTSPlayer(new DefaultTTSPlayer());

        if (resumed) {
            layerRootDrive.onResume();
        }

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (layerRootDrive != null) {
            layerRootDrive.onResume();
        }
        resumed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (layerRootDrive != null) {
            layerRootDrive.onPause();
        }
        resumed = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (layerRootDrive != null) {
            navigatorDrive.unbindView(layerRootDrive);
            layerRootDrive.onDestroy();
        }
    }
}
