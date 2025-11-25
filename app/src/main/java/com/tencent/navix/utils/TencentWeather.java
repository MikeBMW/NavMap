package com.tencent.navix.utils;

//import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TencentWeather {

    private static final String KEY = "CJZBZ-5EXW4-2UQUY-FSM3S-TUXS3-PWFDF"; // 换成你申请的
    private static final OkHttpClient CLIENT = new OkHttpClient();

    public static void getNow(double lat, double lng, final WeatherCallback callback) {
        String geoUrl = "https://apis.map.qq.com/ws/geocoder/v1/"
                + "?location=" + lat + "," + lng + "&key=" + KEY;

        Request geoReq = new Request.Builder().url(geoUrl).build();
        CLIENT.newCall(geoReq).enqueue(new Callback() {
            @Override
            public void onFailure( Call call, IOException e) {
                callback.onFail("地理编码失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call,  Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFail("地理编码响应异常");
                    return;
                }
                JSONObject geoJson = JSON.parseObject(response.body().string());
                JSONObject adInfo = geoJson.getJSONObject("result")
                        .getJSONObject("ad_info");
                int adcode = adInfo.getIntValue("adcode");

                String weatherUrl = "https://apis.map.qq.com/ws/weather/v1/"
                        + "?adcode=" + adcode + "&type=now&key=" + KEY;
                Request weatherReq = new Request.Builder().url(weatherUrl).build();
                CLIENT.newCall(weatherReq).enqueue(new Callback() {
                    @Override
                    public void onResponse(Call call,  Response wResponse) throws IOException {
                        callback.onSuccess(wResponse.body().string());
                    }

                    @Override
                    public void onFailure( Call call,  IOException e) {
                        callback.onFail("天气查询失败: " + e.getMessage());
                    }
                });
            }
        });
    }

    public interface WeatherCallback {
        void onSuccess(String json);
        void onFail(String error);
    }
}