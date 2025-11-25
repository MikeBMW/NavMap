package com.tencent.navix.navi;

import android.os.Bundle;

//import androidx.annotation.Nullable;

import com.tencent.navix.api.config.SimulatorConfig;
import com.tencent.navix.api.model.NavDriveRoute;
import com.tencent.navix.api.model.NavRouteReqParam;
import com.tencent.navix.api.model.NavSearchPoint;
import com.tencent.navix.api.plan.DriveRoutePlanRequestCallback;
import com.tencent.navix.api.plan.RoutePlanRequester;
import com.tencent.navix.BaseNavActivity;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

//import kotlin.io.ByteStreamsKt;
//import kotlin.io.FilesKt;

public class PlaybackNavActivity extends BaseNavActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File playback = playbackFilePrepare();

        // 算路并开启轨迹回放
        navigatorDrive.searchRoute(
                RoutePlanRequester.Companion.newBuilder(NavRouteReqParam.TravelMode.TravelModeDriving)
                        .start(new NavSearchPoint(39.983707, 116.30821))
                        .end(new NavSearchPoint(39.896835, 116.319423))
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
                                    .setEnable(true)
                                    .setConfig(SimulatorConfig
                                            .builder(SimulatorConfig.Type.REPLAY_LOCATIONS_FROM_FILE)
                                            .setReplayFile(playback)
                                            .build()
                                    );
                            navigatorDrive.startNavigation(navRoutePlan.getRouteDatas().get(0).getRouteId());
                        }
                    }
                }
        );
    }

//    private File playbackFilePrepare() {
//        File playback = new File(getFilesDir(), "playback.gps");
//        try {
//            FileUtil.write(playback, ByteStreamsKt.readBytes(getResources().getAssets().open("case001.gps")));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return playback;
//    }
    private File playbackFilePrepare() {
        File playback = new File(getFilesDir(), "playback.gps");
        try (InputStream inputStream = getResources().getAssets().open("case001.gps");
             FileOutputStream fileOutputStream = new FileOutputStream(playback)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return playback;
    }
}
