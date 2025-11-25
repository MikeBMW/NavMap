package com.tencent.navix.navi;

import static com.tencent.navix.api.config.SimulatorConfig.Type.SIMULATE_LOCATIONS_ALONG_ROUTE;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.tencent.navix.api.NavigatorZygote;
import com.tencent.navix.api.config.SimulatorConfig;
import com.tencent.navix.api.layer.NavigatorLayerRootWalk;
import com.tencent.navix.api.layer.NavigatorViewStub;
import com.tencent.navix.api.model.NavError;
import com.tencent.navix.api.model.NavNonMotorRoute;
import com.tencent.navix.api.model.NavRoutePlan;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.navigator.NavigatorWalk;
import com.tencent.navix.api.observer.SimpleNavigatorWalkObserver;
import com.tencent.navix.api.plan.RoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.api.tts.NavigatorTTSPlayer;
import com.tencent.navix.R;
import com.tencent.navix.tts.api.TTSPlayer;
import com.tencent.navix.ui.NavigatorLayerViewWalk;

import java.util.List;

public class WalkSimNavActivity extends AppCompatActivity {

    private NavigatorWalk navigatorWalk;
    private NavigatorLayerRootWalk layerRootWalk;
    private NavigatorLayerViewWalk layerViewWalk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walking);
        navigatorWalk = NavigatorZygote.with(getApplicationContext()).navigator(NavigatorWalk.class);
        NavigatorViewStub navigatorViewStub = findViewById(R.id.view_stub);
        navigatorViewStub.setTravelMode(NavRouteReqParam.TravelMode.TravelModeWalking);
        navigatorViewStub.inflate();
        layerRootWalk = navigatorViewStub.getNavigatorView();
        layerViewWalk = new NavigatorLayerViewWalk(this);
        layerRootWalk.addViewLayer(layerViewWalk);
        navigatorWalk.bindView(layerRootWalk);
        navigatorWalk.registerObserver(observer);
        //算路开导航
        navigatorWalk.searchRoute(RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeWalking)
                .start(new NavSearchPoint(40.007056, 116.389895))
                .end(new NavSearchPoint(39.513005, 116.416642))
                .build(), new RoutePlanRequestCallback<NavNonMotorRoute>() {
            @Override
            public void onResultCallback(NavRoutePlan<NavNonMotorRoute> navRoutePlan, NavError navError) {
                List<NavNonMotorRoute> routeDatas = navRoutePlan.getRouteDatas();
                String routeId = routeDatas.get(0).getRouteId();
                navigatorWalk.simulator().setEnable(true).setConfig(SimulatorConfig.builder(SIMULATE_LOCATIONS_ALONG_ROUTE).setSimulateSpeed(6).build());
                navigatorWalk.startNavigation(routeId);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        layerRootWalk.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        layerRootWalk.onRestart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        layerRootWalk.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        layerRootWalk.onPause();
        navigatorWalk.stopNavigation();
    }

    @Override
    protected void onStop() {
        super.onStop();
        layerRootWalk.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除导航监听
        navigatorWalk.unregisterObserver(observer);
        // 移除默认面板
        layerRootWalk.removeViewLayer(layerViewWalk);
        // 解绑导航地图
        navigatorWalk.unbindView(layerRootWalk);

        // 关闭TTS和导航
        NavigatorTTSPlayer ttsPlayer = navigatorWalk.getTTSPlayer();
        if (ttsPlayer instanceof TTSPlayer) {
            ((TTSPlayer) ttsPlayer).stop();
        }
        navigatorWalk.stopNavigation();
        layerRootWalk.onDestroy();
    }

    private SimpleNavigatorWalkObserver observer = new SimpleNavigatorWalkObserver() {
        //即将到达目的地
        @Override
        public void onWillArriveDestination() {
            super.onWillArriveDestination();
        }
    };
}