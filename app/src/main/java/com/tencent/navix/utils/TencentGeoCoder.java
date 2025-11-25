package com.tencent.navix.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

//import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.net.URLEncoder;

/**
 * 腾讯 WebServiceAPI 地址解析（地址→经纬度）
 * 线程安全：内部已切换主线程回调
 */
public class TencentGeoCoder {
    private static final String TAG = "TencentGeoCoder";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface GeoListener {
        void onSuccess(double lat, double lng, String title);
        void onError(String msg);
    }

    /**
     * 根据地址异步解析
     */
    public static void geoCode(Context ctx, String address, GeoListener listener) {
        String key = getWebServiceKey(ctx);
        if (key == null) {
            MAIN.post(() -> listener.onError("AndroidManifest 未配置 TencentMapWebServiceKey"));
            return;
        }
        try {
            // 用 JDK 自带 URLEncoder 替代 okhttp3.HttpUrl.encode
            String encoded = URLEncoder.encode(address, "UTF-8");
            String url = "https://apis.map.qq.com/ws/geocoder/v1/?address=" + encoded + "&key=" + key;

            Request req = new Request.Builder().url(url).build();
            CLIENT.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (call == null || e == null) return; // 保底判断
                    MAIN.post(() -> listener.onError("网络错误：" + e.getMessage()));
                }



                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (call == null || response == null) return; // 保底判断
                    if (!response.isSuccessful()) {
                        MAIN.post(() -> listener.onError("http code=" + response.code()));
                        return;
                    }
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        int status = json.optInt("status", -1);
                        if (status != 0) {
                            String msg = json.optString("message", "未知错误");
                            MAIN.post(() -> listener.onError("解析失败：" + msg));
                            return;
                        }
                        JSONObject loc = json.getJSONObject("result").getJSONObject("location");
                        double lat = loc.getDouble("lat");
                        double lng = loc.getDouble("lng");
                        String title = json.getJSONObject("result").optString("title", address);
                        MAIN.post(() -> listener.onSuccess(lat, lng, title));
                    } catch (Exception e) {
                        Log.e(TAG, "parse json error", e);
                        MAIN.post(() -> listener.onError("数据解析异常"));
                    }
                }
            });
        } catch (Exception e) {
            MAIN.post(() -> listener.onError("地址编码失败"));
        }
    }



    private static String getWebServiceKey(Context ctx) {
        try {
            ApplicationInfo ai = ctx.getPackageManager()
                    .getApplicationInfo(ctx.getPackageName(),
                            PackageManager.GET_META_DATA);
            return ai.metaData.getString("TencentMapWebServiceKey");
//            return ai.metaData.getString("TencentMapSDK");
        } catch (Exception ignore) {}
        return null;
    }
}