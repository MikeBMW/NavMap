package com.tencent.navix.power.interfaces;

public interface DataSendListener {
    void onDataSent(boolean success);
    void onConnectionStatusChanged(boolean connected);
}
