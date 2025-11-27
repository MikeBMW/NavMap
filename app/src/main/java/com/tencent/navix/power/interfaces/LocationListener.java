package com.tencent.navix.power.interfaces;

import com.tencent.map.geolocation.TencentLocation;

public interface LocationListener {
    void onLocationUpdate(TencentLocation location);
    void onLocationError(int errorCode, String errorMsg);
    void onSpeedCalculate(double speedMps);
}