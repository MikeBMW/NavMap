package com.tencent.navix.power.models;

/**
 * 经纬度坐标点
 */
public class LatLng {
    public double latitude;
    public double longitude;

    public LatLng() {}

    public LatLng(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getter 和 Setter 方法
    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return "LatLng{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                '}';
    }
}