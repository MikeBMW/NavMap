package com.tencent.navix.power.managers;


import android.util.Log;
import android.widget.Toast;
import okhttp3.*;
import okio.ByteString;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";

    private OkHttpClient client;
    private WebSocket webSocket;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private WebSocketListener listener;
    private ConnectionCallback connectionCallback;

    // 回调接口
    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onMessageReceived(String message);
    }

    public WebSocketManager() {
        initClient();
    }

    private void initClient() {
        client = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)   // 心跳
                .build();
    }

    /**
     * 连接 WebSocket
     */
    public void connect(String url, ConnectionCallback callback) {
        this.connectionCallback = callback;

        // 关闭旧连接
        disconnect();

        Log.d(TAG, "连接地址 = " + url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "✅ WebSocket 连接成功");
                isConnected.set(true);
                WebSocketManager.this.webSocket = webSocket;

                if (connectionCallback != null) {
                    connectionCallback.onConnected();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket 连接失败", t);
                isConnected.set(false);
                webSocket = null;

                if (connectionCallback != null) {
                    connectionCallback.onError(t.getMessage());
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "收到文本消息: " + text);
                if (connectionCallback != null) {
                    connectionCallback.onMessageReceived(text);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "收到二进制消息，长度: " + bytes.size());
                // 可以添加二进制消息处理逻辑
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket 连接关闭: " + reason);
                isConnected.set(false);
                if (webSocket == WebSocketManager.this.webSocket) {
                    WebSocketManager.this.webSocket = null;
                }

                if (connectionCallback != null) {
                    connectionCallback.onDisconnected();
                }
            }
        };

        webSocket = client.newWebSocket(request, listener);
    }

    /**
     * 发送二进制数据
     */
    public boolean sendBinaryData(byte[] data) {
        if (webSocket != null && isConnected.get()) {
            return webSocket.send(ByteString.of(data));
        }
        Log.w(TAG, "WebSocket 未连接，无法发送数据");
        return false;
    }

    /**
     * 发送文本数据
     */
    public boolean sendTextData(String text) {
        if (webSocket != null && isConnected.get()) {
            return webSocket.send(text);
        }
        Log.w(TAG, "WebSocket 未连接，无法发送数据");
        return false;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "正常关闭");
            webSocket = null;
        }
        isConnected.set(false);
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return isConnected.get() && webSocket != null;
    }

    /**
     * 释放资源
     */
    public void release() {
        disconnect();
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }
}