package com.tencent.navix.demo.navi;

import static com.tencent.navix.api.config.SimulatorConfig.Type.SIMULATE_LOCATIONS_ALONG_ROUTE;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.tencent.navix.api.NavigatorZygote;
import com.tencent.navix.api.config.SimulatorConfig;
import com.tencent.navix.api.layer.NavigatorLayerRootRide;
import com.tencent.navix.api.layer.NavigatorViewStub;
import com.tencent.navix.api.model.NavError;
import com.tencent.navix.api.model.NavNonMotorRoute;
import com.tencent.navix.api.model.NavRoutePlan;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.navigator.NavigatorRide;
import com.tencent.navix.api.observer.SimpleNavigatorWalkObserver;
import com.tencent.navix.api.plan.RoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.api.tts.NavigatorTTSPlayer;
import com.tencent.navix.demo.R;
import com.tencent.navix.tts.api.TTSPlayer;
import com.tencent.navix.ui.NavigatorLayerViewRide;

import java.util.List;

public class RideGpsNavActivity extends AppCompatActivity {

    private NavigatorRide navigatorRide;
    private NavigatorLayerRootRide layerRootRide;
    private NavigatorLayerViewRide layerViewRide;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_riding);
        navigatorRide = NavigatorZygote.with(getApplicationContext()).navigator(NavigatorRide.class);
        NavigatorViewStub navigatorViewStub = findViewById(R.id.view_stub);
        navigatorViewStub.setTravelMode(NavRouteReqParam.TravelMode.TravelModeRiding);
        navigatorViewStub.inflate();
        layerRootRide = navigatorViewStub.getNavigatorView();
        layerViewRide = new NavigatorLayerViewRide(this);
        layerRootRide.addViewLayer(layerViewRide);
        navigatorRide.bindView(layerRootRide);
        navigatorRide.registerObserver(observer);
        //算路开导航
        navigatorRide.searchRoute(RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeRiding)
                .start(new NavSearchPoint(40.007056, 116.389895))
                .end(new NavSearchPoint(39.513005, 116.416642))
                .build(), new RoutePlanRequestCallback<NavNonMotorRoute>() {
            @Override
            public void onResultCallback(NavRoutePlan<NavNonMotorRoute> navRoutePlan, NavError navError) {
                List<NavNonMotorRoute> routeDatas = navRoutePlan.getRouteDatas();
                String routeId = routeDatas.get(0).getRouteId();
                navigatorRide.simulator().setEnable(false);
                navigatorRide.startNavigation(routeId);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        layerRootRide.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        layerRootRide.onRestart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        layerRootRide.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        layerRootRide.onPause();
        navigatorRide.stopNavigation();
    }

    @Override
    protected void onStop() {
        super.onStop();
        layerRootRide.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除导航监听
        navigatorRide.unregisterObserver(observer);
        // 移除默认面板
        layerRootRide.removeViewLayer(layerViewRide);
        // 解绑导航地图
        navigatorRide.unbindView(layerRootRide);

        // 关闭TTS和导航
        NavigatorTTSPlayer ttsPlayer = navigatorRide.getTTSPlayer();
        if (ttsPlayer instanceof TTSPlayer) {
            ((TTSPlayer) ttsPlayer).stop();
        }
        navigatorRide.stopNavigation();
        layerRootRide.onDestroy();
    }

    private SimpleNavigatorWalkObserver observer = new SimpleNavigatorWalkObserver() {
        //即将到达目的地
        @Override
        public void onWillArriveDestination() {
            super.onWillArriveDestination();
        }
    };
}