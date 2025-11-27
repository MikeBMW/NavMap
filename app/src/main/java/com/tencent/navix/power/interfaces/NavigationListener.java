package com.tencent.navix.power.interfaces;

import com.tencent.navix.api.model.NavDriveRoute;

public interface NavigationListener {
    void onRoutePlanSuccess(NavDriveRoute route);
    void onRoutePlanFailed(String error);
    void onNavigationStarted();
    void onNavigationStopped();
    void onRouteDeviationDetected();
}
