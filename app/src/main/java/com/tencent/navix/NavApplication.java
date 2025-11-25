package com.tencent.navix;

import android.app.Application;

import com.tencent.navix.api.NavigatorConfig;
import com.tencent.navix.api.NavigatorZygote;
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer;

public class NavApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 设置同意地图SDK隐私协议
        TencentMapInitializer.setAgreePrivacy(getApplicationContext(), true);
        TencentMapInitializer.start(getApplicationContext());

        // 初始化导航SDK
        NavigatorZygote.with(getApplicationContext()).init(
                NavigatorConfig.builder()
                        // 设置同意导航SDK隐私协议
                        .setUserAgreedPrivacy(true)
                        // 设置自定义的可区分设备的ID
                        .setDeviceId("develop_custom_id_123456")
//                        .experiment().setDebug(true)
                        .build()
        );
        NavigatorZygote.with(getApplicationContext()).start();
    }
}
