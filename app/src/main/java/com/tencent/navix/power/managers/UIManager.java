package com.tencent.navix.power.managers;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.navix.R;
import com.tencent.navix.power.builders.DataPacker;
import com.tencent.navix.power.models.Weather;
import com.tencent.navix.power.models.TrafficEvent;
import com.tencent.navix.power.models.RouteOverview;
import com.tencent.navix.power.models.TrafficLight;

import java.util.Locale;

/**
 * UI管理模块
 * 负责所有UI相关的操作，包括视图初始化、事件监听、数据展示、状态更新等
 */
public class UIManager {
    private static final String TAG = "UIManager";

    private final Context context;

    // UI组件
    private EditText etDestination;
    private EditText etIpPort;
    private Button btnConfirmDestination;
    private Button btnConnect;
    private Button btnOptions;
    private TextView tvLocationInfo;
    private TextView tvSpeedInfo;
    private TextView tvWeatherTemp;
    private TextView tvWeatherDesc;
    private ImageView ivWeatherIcon;

    // 控制面板组件 - 新增
    private LinearLayout controlPanel;
    private TextView controlPanelTitle;
    private Button btnShowControlPanel;

    // 调试面板组件
    private LinearLayout debugPanel;
    private TextView debugTitle;
    private TextView debugHex;
    private TextView debugFields;
    private Button btnShowDebugPanel; // 新增
    private boolean debugCollapsed = false;

    // 事件监听器
    private UIEventListener eventListener;

    public UIManager(Context context) {
        this.context = context;
        Log.d(TAG, "UI管理器初始化完成");
    }

    /**
     * 初始化所有视图
     */
    public void initializeViews() {
        Log.d(TAG, "初始化UI视图");

        try {
            // 基础控制组件
            etDestination = ((android.app.Activity) context).findViewById(R.id.et_destination);
            etIpPort = ((android.app.Activity) context).findViewById(R.id.et_ip_port);
            btnConfirmDestination = ((android.app.Activity) context).findViewById(R.id.btn_confirm_destination);
            btnConnect = ((android.app.Activity) context).findViewById(R.id.btn_connect);
            btnOptions = ((android.app.Activity) context).findViewById(R.id.btn_options);
            tvLocationInfo = ((android.app.Activity) context).findViewById(R.id.tv_location_info);
            tvSpeedInfo = ((android.app.Activity) context).findViewById(R.id.tv_speed_info);

            // 天气组件
            tvWeatherTemp = ((android.app.Activity) context).findViewById(R.id.tv_weather_temp);
            tvWeatherDesc = ((android.app.Activity) context).findViewById(R.id.tv_weather_desc);
            ivWeatherIcon = ((android.app.Activity) context).findViewById(R.id.iv_weather_icon);

            // 控制面板组件 - 新增
            controlPanel = ((android.app.Activity) context).findViewById(R.id.control_panel);
            controlPanelTitle = ((android.app.Activity) context).findViewById(R.id.control_panel_title);
            btnShowControlPanel = ((android.app.Activity) context).findViewById(R.id.btn_show_control_panel);

            // 调试面板组件
            debugPanel = ((android.app.Activity) context).findViewById(R.id.debug_panel);
            debugTitle = ((android.app.Activity) context).findViewById(R.id.debug_title);
            debugHex = ((android.app.Activity) context).findViewById(R.id.debug_hex);
            debugFields = ((android.app.Activity) context).findViewById(R.id.debug_fields);
            btnShowDebugPanel = ((android.app.Activity) context).findViewById(R.id.btn_show_debug_panel); // 新增

            // 设置默认值
            setDefaultValues();

            Log.d(TAG, "UI视图初始化成功");

        } catch (Exception e) {
            Log.e(TAG, "UI视图初始化失败", e);
        }
    }

    /**
     * 设置默认值
     */
    private void setDefaultValues() {
        // 设置默认IP端口
        if (etIpPort != null && etIpPort.getText().toString().trim().isEmpty()) {
            etIpPort.setText("192.168.1.30:54330");
        }
        // 设置目的地默认值
        if (etDestination != null && etDestination.getText().toString().trim().isEmpty()) {
            etDestination.setText("沈阳南站");
            etDestination.setTextColor(Color.BLACK);
        }

        // 设置默认位置信息
        if (tvLocationInfo != null) {
            tvLocationInfo.setText("正在获取位置...");
        }

        // 设置默认速度信息
        if (tvSpeedInfo != null) {
            tvSpeedInfo.setText("速度: 0 km/h");
        }

        // 设置默认天气信息
        if (tvWeatherTemp != null) {
            tvWeatherTemp.setText("25°C");
        }
        if (tvWeatherDesc != null) {
            tvWeatherDesc.setText("晴");
        }

        // 初始化面板状态
        if (controlPanel != null) {
            controlPanel.setVisibility(View.GONE); // 默认隐藏控制面板
        }
        if (debugPanel != null) {
            debugPanel.setVisibility(View.GONE); // 默认隐藏调试面板
        }
    }

    /**
     * 设置事件监听器
     */
    public void setupEventListeners(DestinationConfirmListener destinationListener,
                                    ConnectClickListener connectListener) {
        this.eventListener = new UIEventListener(destinationListener, connectListener);
        setupClickListeners();
    }

    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        // 目的地确认按钮
        if (btnConfirmDestination != null) {
            btnConfirmDestination.setOnClickListener(v -> {
                String destination = etDestination != null ? etDestination.getText().toString().trim() : "";
                if (eventListener != null && eventListener.destinationListener != null) {
                    eventListener.destinationListener.onDestinationConfirmed(destination);
                }
            });
        }

        // 连接按钮
        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> {
                String ipPort = etIpPort != null ? etIpPort.getText().toString().trim() : "";
                if (eventListener != null && eventListener.connectListener != null) {
                    eventListener.connectListener.onConnectClicked(ipPort);
                }
            });
        }

        // 选项按钮
        if (btnOptions != null) {
            btnOptions.setOnClickListener(v -> {
                // 这里可以打开设置页面或其他选项
                showToast("选项功能开发中...");
            });
        }

        // 控制面板显示按钮 - 新增
        if (btnShowControlPanel != null) {
            btnShowControlPanel.setOnClickListener(v -> toggleControlPanel());
        }

        // 控制面板标题点击事件（隐藏面板）- 新增
        if (controlPanelTitle != null) {
            controlPanelTitle.setOnClickListener(v -> hideControlPanel());
        }

        // 调试面板显示按钮 - 新增
        if (btnShowDebugPanel != null) {
            btnShowDebugPanel.setOnClickListener(v -> showDebugPanel());
        }

        // 调试面板标题点击事件（展开/折叠）
        if (debugTitle != null) {
            debugTitle.setOnClickListener(v -> toggleDebugPanel());
        }
    }

    /**
     * 切换控制面板的显示/隐藏 - 新增
     */
    public void toggleControlPanel() {
        if (controlPanel.getVisibility() == View.VISIBLE) {
            hideControlPanel();
        } else {
            showControlPanel();
        }
    }

    /**
     * 显示控制面板（从上方滑入）- 新增
     */
    public void showControlPanel() {
        if (controlPanel == null) return;

        runOnUiThread(() -> {
            controlPanel.setVisibility(View.VISIBLE);
            // 从上方滑入动画
            TranslateAnimation animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, -1f,
                    Animation.RELATIVE_TO_SELF, 0f
            );
            animation.setDuration(300);
            controlPanel.startAnimation(animation);
        });
    }

    /**
     * 隐藏控制面板（向上滑出）- 新增
     */
    public void hideControlPanel() {
        if (controlPanel == null) return;

        runOnUiThread(() -> {
            // 向上滑出动画
            TranslateAnimation animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, -1f
            );
            animation.setDuration(300);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    controlPanel.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            controlPanel.startAnimation(animation);
        });
    }

    /**
     * 显示调试面板（从下方滑入）- 新增
     */
    public void showDebugPanel() {
        if (debugPanel == null) return;

        runOnUiThread(() -> {
            debugPanel.setVisibility(View.VISIBLE);
            // 从下方滑入动画
            TranslateAnimation animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 1f,
                    Animation.RELATIVE_TO_SELF, 0f
            );
            animation.setDuration(300);
            debugPanel.startAnimation(animation);
        });
    }

    /**
     * 隐藏调试面板（向下滑出）- 新增
     */
    public void hideDebugPanel() {
        if (debugPanel == null) return;

        runOnUiThread(() -> {
            // 向下滑出动画
            TranslateAnimation animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 1f
            );
            animation.setDuration(300);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    debugPanel.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            debugPanel.startAnimation(animation);
        });
    }

    /**
     * 切换调试面板的展开/折叠状态
     */
    private void toggleDebugPanel() {
        debugCollapsed = !debugCollapsed;

        if (debugHex != null) {
            debugHex.setVisibility(debugCollapsed ? View.GONE : View.VISIBLE);
        }
        if (debugFields != null) {
            debugFields.setVisibility(debugCollapsed ? View.GONE : View.VISIBLE);
        }
        if (debugTitle != null) {
            debugTitle.setText(debugCollapsed ? "调试信息（点我展开）" : "调试信息（点我折叠）");
        }

        Log.d(TAG, "调试面板状态: " + (debugCollapsed ? "折叠" : "展开"));
    }

    /**
     * 更新位置信息显示
     */
    public void updateLocationInfo(TencentLocation location) {
        if (tvLocationInfo != null && location != null) {
            String locationInfo = String.format(Locale.CHINA,
                    "纬度: %.6f\n经度: %.6f\n精度: %.1f米",
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getAccuracy());

            runOnUiThread(() -> tvLocationInfo.setText(locationInfo));
        }
    }

    /**
     * 更新速度信息显示
     */
    public void updateSpeedInfo(double speedMps) {
        if (tvSpeedInfo != null) {
            // 转换为 km/h
            double speedKph = speedMps * 3.6;
            String speedInfo = String.format(Locale.CHINA, "速度: %.1f km/h", speedKph);

            runOnUiThread(() -> tvSpeedInfo.setText(speedInfo));
        }
    }

    /**
     * 更新天气信息显示
     */
    public void updateWeatherInfo(String temperature, String description, String iconCode) {
        runOnUiThread(() -> {
            if (tvWeatherTemp != null) {
                tvWeatherTemp.setText(temperature + "°C");
            }
            if (tvWeatherDesc != null) {
                tvWeatherDesc.setText(description);
            }
            if (ivWeatherIcon != null) {
                // 根据天气代码设置图标
                setWeatherIcon(iconCode);
            }
        });
    }

    /**
     * 设置天气图标
     */
    private void setWeatherIcon(String iconCode) {
        // 这里可以根据天气代码设置对应的图标
        // 实际项目中应该有对应的图标资源
        int iconResId = R.drawable.ic_weather_sunny; // 默认晴天图标

        if (iconCode != null) {
            switch (iconCode) {
                case "01": // 晴
                    iconResId = R.drawable.ic_weather_sunny;
                    break;
                case "02": // 多云
                case "03": // 阴
                    iconResId = R.drawable.ic_weather_cloudy;
                    break;
                case "04": // 小雨
                case "05": // 中雨
                case "06": // 大雨
                    iconResId = R.drawable.ic_weather_rainy;
                    break;
                case "07": // 雪
                    iconResId = R.drawable.ic_weather_snowy;
                    break;
                case "08": // 雾
                case "09": // 霾
                    iconResId = R.drawable.ic_weather_foggy;
                    break;
                default:
                    iconResId = R.drawable.ic_weather_sunny;
                    break;
            }
        }

        ivWeatherIcon.setImageResource(iconResId);
    }

    /**
     * 更新连接状态显示
     */
    public void updateConnectionStatus(boolean connected) {
        if (btnConnect != null) {
            runOnUiThread(() -> {
                btnConnect.setText(connected ? "已连接" : "连接");
                btnConnect.setBackgroundColor(connected ?
                        context.getResources().getColor(android.R.color.holo_green_light) :
                        context.getResources().getColor(android.R.color.holo_blue_light));
            });
        }
    }

    /**
     * 更新导航状态显示
     */
    public void updateNavigationStatus(boolean navigating, String destination) {
        if (btnConfirmDestination != null) {
            runOnUiThread(() -> {
                btnConfirmDestination.setText(navigating ? "停止导航" : "开始导航");
                if (etDestination != null && destination != null) {
                    etDestination.setText(destination);
                }
            });
        }
    }

    /**
     * 更新调试面板
     */
    public void updateDebugPanel(byte[] payload, TrafficEvent event,
                                 TrafficLight light, RouteOverview overview,
                                 Weather weather) {
        if (debugPanel == null || debugHex == null || debugFields == null) {
            return;
        }



        runOnUiThread(() -> {
            // 确保调试面板可见
            if (debugPanel.getVisibility() != View.VISIBLE) {
                debugPanel.setVisibility(View.VISIBLE);
            }

            // 格式化十六进制字符串，每个字节之间添加空格
            StringBuilder hexStringBuilder = new StringBuilder();
            for (byte b : payload) {
                hexStringBuilder.append(String.format("%02X ", b));
            }
            String hexString = hexStringBuilder.toString().trim(); // 移除最后的空格
            debugHex.setText(hexString);

//            // 使用DataPacker获取格式化的十六进制显示
            DataPacker dataPacker = DataPacker.getInstance();
//            String hexString = dataPacker.getPacketHexString(payload);
//            debugHex.setText(hexString);

            // 使用DataPacker解析数据包用于调试显示
            String debugInfo = dataPacker.parsePacketForDebug(payload);
            debugFields.setText(debugInfo);
        });
    }

    /**
     * 格式化调试字段信息
     */
    private String formatDebugFields(Object[] debugData) {
        // 这里需要根据实际的数据结构来格式化显示
        // 由于我们还没有实现具体的数据结构，这里先返回一个简单的占位符
        return String.format(Locale.CHINA,
                "数据包长度: %d字节\n" +
                        "时间: %s\n" +
                        "导航状态: 运行中",
                debugData.length,
                java.text.SimpleDateFormat.getDateTimeInstance().format(new java.util.Date()));
    }

    /**
     * 显示Toast消息
     */
    public void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * 显示长时间Toast消息
     */
    public void showLongToast(String message) {
        runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

    /**
     * 在UI线程执行任务
     */
    private void runOnUiThread(Runnable action) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(action);
        }
    }

    /**
     * 显示加载对话框
     */
    public void showLoading(String message) {
        runOnUiThread(() -> {
            // 实际项目中可以实现一个加载对话框
            // 这里简单使用Toast代替
            if (message != null) {
                showToast(message);
            }
        });
    }

    /**
     * 隐藏加载对话框
     */
    public void hideLoading() {
        runOnUiThread(() -> {
            // 隐藏加载对话框的实现
        });
    }

    /**
     * 清空目的地输入框
     */
    public void clearDestinationInput() {
        if (etDestination != null) {
            runOnUiThread(() -> etDestination.setText(""));
        }
    }

    /**
     * 设置目的地输入框提示
     */
    public void setDestinationHint(String hint) {
        if (etDestination != null) {
            runOnUiThread(() -> etDestination.setHint(hint));
        }
    }

    /**
     * 获取当前输入的目的地
     */
    public String getCurrentDestination() {
        return etDestination != null ? etDestination.getText().toString().trim() : "";
    }

    /**
     * 获取当前输入的IP端口
     */
    public String getCurrentIpPort() {
        return etIpPort != null ? etIpPort.getText().toString().trim() : "";
    }

    /**
     * 释放资源
     */
    public void release() {
        // 清理资源
        etDestination = null;
        etIpPort = null;
        btnConfirmDestination = null;
        btnConnect = null;
        btnOptions = null;
        tvLocationInfo = null;
        tvSpeedInfo = null;
        tvWeatherTemp = null;
        tvWeatherDesc = null;
        ivWeatherIcon = null;

        // 新增组件的清理
        controlPanel = null;
        controlPanelTitle = null;
        btnShowControlPanel = null;
        btnShowDebugPanel = null;

        debugPanel = null;
        debugTitle = null;
        debugHex = null;
        debugFields = null;
        eventListener = null;

        Log.d(TAG, "UI管理器资源已释放");
    }

    /**
     * UI事件监听器包装类
     */
    private static class UIEventListener {
        final DestinationConfirmListener destinationListener;
        final ConnectClickListener connectListener;

        UIEventListener(DestinationConfirmListener destinationListener,
                        ConnectClickListener connectListener) {
            this.destinationListener = destinationListener;
            this.connectListener = connectListener;
        }
    }

    /**
     * 目的地确认监听接口
     */
    public interface DestinationConfirmListener {
        void onDestinationConfirmed(String destination);
    }

    /**
     * 连接点击监听接口
     */
    public interface ConnectClickListener {
        void onConnectClicked(String ipPort);
    }
}
