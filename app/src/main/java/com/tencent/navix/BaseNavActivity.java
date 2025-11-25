package com.tencent.navix;

import android.os.Bundle;
//import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tencent.navix.api.NavigatorZygote;
import com.tencent.navix.api.layer.NavigatorLayerRootDrive;
import com.tencent.navix.api.layer.NavigatorViewStub;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.navigator.NavigatorDrive;
import com.tencent.navix.api.observer.SimpleNavigatorDriveObserver;
import com.tencent.navix.api.tts.NavigatorTTSPlayer;
import com.tencent.navix.R;
import com.tencent.navix.tts.api.TTSPlayer;
import com.tencent.navix.ui.NavigatorLayerViewDrive;

import org.jetbrains.annotations.Nullable;


public abstract class BaseNavActivity extends AppCompatActivity {

    protected NavigatorDrive navigatorDrive;
    protected NavigatorLayerRootDrive layerRootDrive;
    protected NavigatorLayerViewDrive layerViewDrive;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_nav);

        // 创建驾车 NavigatorDrive
        navigatorDrive = NavigatorZygote.with(getApplicationContext()).navigator(NavigatorDrive.class);

        // 创建导航地图层 NavigatorLayerRootDrive
        NavigatorViewStub navigatorViewStub = findViewById(R.id.navigator_view_stub);
        navigatorViewStub.setTravelMode(NavRouteReqParam.TravelMode.TravelModeDriving);
        navigatorViewStub.inflate();
        layerRootDrive = navigatorViewStub.getNavigatorView();

        // 创建默认面板 NavigatorLayerViewDrive，并添加到导航地图层
        layerViewDrive = new NavigatorLayerViewDrive(this);
        layerRootDrive.addViewLayer(layerViewDrive);

        // 将导航地图层绑定到Navigator
        navigatorDrive.bindView(layerRootDrive);

        // 注册导航监听
        navigatorDrive.registerObserver(driveObserver);
    }

    @Override
    protected void onStart() {
        super.onStart();
        layerRootDrive.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        layerRootDrive.onRestart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        layerRootDrive.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        layerRootDrive.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        layerRootDrive.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        layerRootDrive.onDestroy();

        // 移除导航监听
        navigatorDrive.unregisterObserver(driveObserver);

        // 移除默认面板
        layerRootDrive.removeViewLayer(layerViewDrive);
        // 解绑导航地图
        navigatorDrive.unbindView(layerRootDrive);

        // 关闭TTS和导航
        NavigatorTTSPlayer ttsPlayer = navigatorDrive.getTTSPlayer();
        if (ttsPlayer instanceof TTSPlayer) {
            ((TTSPlayer) ttsPlayer).stop();
        }
        navigatorDrive.stopNavigation();
    }

    private final SimpleNavigatorDriveObserver driveObserver = new SimpleNavigatorDriveObserver() {
        @Override
        public void onWillArriveDestination() {
            super.onWillArriveDestination();
            if (navigatorDrive != null) {
                navigatorDrive.stopNavigation();
            }
        }
    };
}
