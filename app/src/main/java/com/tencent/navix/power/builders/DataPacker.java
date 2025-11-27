package com.tencent.navix.power.builders;

import android.util.Log;

import com.tencent.navix.power.models.TrafficEvent;
import com.tencent.navix.power.models.TrafficLight;
import com.tencent.navix.power.models.RouteOverview;
import com.tencent.navix.power.models.Weather;
import com.tencent.navix.power.models.Weather;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据打包器
 * 负责将所有导航数据结构打包成符合CAN协议要求的二进制格式
 */
public class DataPacker {
    private static final String TAG = "DataPacker";

    // 数据包配置
    private static final int PACKET_SIZE = 33; // 总数据包大小33字节
    private static final int COUNTER_MAX = 255; // 计数器最大值

    // 消息计数器（线程安全）
    private final AtomicInteger messageCounter = new AtomicInteger(0);

    // 单例模式
    private static volatile DataPacker instance;

    public static DataPacker getInstance() {
        if (instance == null) {
            synchronized (DataPacker.class) {
                if (instance == null) {
                    instance = new DataPacker();
                }
            }
        }
        return instance;
    }

    private DataPacker() {
        Log.d(TAG, "数据打包器初始化完成");
    }

    /**
     * 打包所有导航数据
     */
    public byte[] packAll(TrafficEvent trafficEvent, TrafficLight trafficLight,
                          RouteOverview routeOverview, Weather canWeather) {
        ByteBuffer buffer = ByteBuffer.allocate(PACKET_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        try {
            // 1. 消息计数器（1字节）
            int counter = messageCounter.incrementAndGet() & COUNTER_MAX;
            buffer.put((byte) counter);

            // 2. TrafficEvent → 8字节
            packTrafficEvent(buffer, trafficEvent);

            // 3. TrafficLight → 8字节
            packTrafficLight(buffer, trafficLight);

            // 4. RouteOverview → 8字节
            packRouteOverview(buffer, routeOverview);

            // 5. CANWeather → 8字节
            packCANWeather(buffer, canWeather);

            // 验证数据包大小
            if (buffer.position() != PACKET_SIZE) {
                Log.w(TAG, String.format("数据包大小异常: 实际=%d, 预期=%d",
                        buffer.position(), PACKET_SIZE));
            }

            Log.d(TAG, String.format("数据打包完成: 计数器=%d, 大小=%d字节", counter, buffer.position()));

        } catch (Exception e) {
            Log.e(TAG, "数据打包失败", e);
            return createEmptyPacket();
        }

        return buffer.array();
    }

    /**
     * 打包交通事件数据（8字节）
     */
    private void packTrafficEvent(ByteBuffer buffer, TrafficEvent event) {
        if (event == null) {
            buffer.put(new byte[8]); // 填充8字节空数据
            return;
        }

        try {
            // 字节0: eventCount
            buffer.put(event.getEventCount());

            // 字节1: eventSummary
            buffer.put(event.getEventSummary());

            // 字节2-3: nearestDistance (小端序)
            buffer.putShort(event.getNearestDistance());

            // 字节4: nearestType
            buffer.put(event.getNearestType());

            // 字节5: nearestDelay
            buffer.put(event.getNearestDelay());

            // 字节6: severeCount
            buffer.put(event.getSevereCount());

            // 字节7: accidentCount
            buffer.put(event.getAccidentCount());

        } catch (Exception e) {
            Log.e(TAG, "打包交通事件数据失败", e);
            buffer.put(new byte[8]); // 填充空数据
        }
    }

    /**
     * 打包交通灯数据（8字节）
     */
    private void packTrafficLight(ByteBuffer buffer, TrafficLight light) {
        if (light == null) {
            buffer.put(new byte[8]); // 填充8字节空数据
            return;
        }

        try {
            // 字节0: nextLightId
            buffer.put(light.getNextLightId());

            // 字节1: stateFlags
            buffer.put(light.getStateFlags());

            // 字节2-3: positionIndex (小端序)
//            buffer.putShort(light.getPositionIndex());
            // 字节2-3: positionIndex (小端序)
            buffer.putShort(light.getDistanceToNextLight());

            // 字节4: remainingTime
            buffer.put(light.getRemainingTime());

            // 字节5: distanceToLight
            buffer.put(light.getDistanceToLight());

            // 字节6: speed
            buffer.put(light.getSpeed());

            // 字节7: lightCount
            buffer.put(light.getLightCount());

        } catch (Exception e) {
            Log.e(TAG, "打包交通灯数据失败", e);
            buffer.put(new byte[8]); // 填充空数据
        }
    }

    /**
     * 打包路线概览数据（8字节）
     */
    private void packRouteOverview(ByteBuffer buffer, RouteOverview overview) {
        if (overview == null) {
            buffer.put(new byte[8]); // 填充8字节空数据
            return;
        }

        try {
            // 字节0-1: totalDistance (小端序)
            buffer.putShort(overview.getTotalDistance());

            // 字节2: tollDistancePct
            buffer.put(overview.getTollDistancePct());

            // 字节3: congestionFlags
            buffer.put(overview.getCongestionFlags());

            // 字节4-5: estimatedTime (小端序)
            buffer.putShort(overview.getEstimatedTime());

            // 字节6-7: totalFee (小端序)
            buffer.putShort(overview.getTotalFee());

        } catch (Exception e) {
            Log.e(TAG, "打包路线概览数据失败", e);
            buffer.put(new byte[8]); // 填充空数据
        }
    }

    /**
     * 打包CAN天气数据（8字节）
     */
    private void packCANWeather(ByteBuffer buffer, Weather weather) {
        if (weather == null) {
            buffer.put(new byte[8]); // 填充8字节空数据
            return;
        }

        try {
            // 字节0: routeHash (7bit) + dataValid (1bit)
            byte b0 = (byte) ((weather.getRouteHash() & 0x7F) |
                    ((weather.getDataValid() & 0x01) << 7));
            buffer.put(b0);

            // 字节1: weatherCode (4bit) + tempConfidence (4bit)
            byte b1 = (byte) ((weather.getWeatherCode() & 0x0F) |
                    ((weather.getTempConfidence() & 0x0F) << 4));
            buffer.put(b1);

            // 字节2: precipLevel (3bit) + warnType (3bit) + warnLevel (2bit)
            byte b2 = (byte) ((weather.getPrecipLevel() & 0x07) |
                    ((weather.getWarnType() & 0x07) << 3) |
                    ((weather.getWarnLevel() & 0x03) << 6));
            buffer.put(b2);

            // 字节3: realTemperature
            buffer.put(weather.getRealTemperature());

            // 字节4-5: totalDistance (小端序)
            buffer.putShort(weather.getTotalDistance());

            // 字节6-7: keyPoints (小端序)
            buffer.putShort(weather.getKeyPoints());

        } catch (Exception e) {
            Log.e(TAG, "打包CAN天气数据失败", e);
            buffer.put(new byte[8]); // 填充空数据
        }
    }

    /**
     * 创建空数据包
     */
    private byte[] createEmptyPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(PACKET_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // 填充默认数据
        int counter = messageCounter.incrementAndGet() & COUNTER_MAX;
        buffer.put((byte) counter);

        // 填充剩余32字节为0
        byte[] emptyData = new byte[PACKET_SIZE - 1];
        buffer.put(emptyData);

        Log.w(TAG, "创建空数据包，计数器=" + counter);
        return buffer.array();
    }

    /**
     * 验证数据包格式
     */
    public boolean validatePacket(byte[] packet) {
        if (packet == null || packet.length != PACKET_SIZE) {
            Log.e(TAG, String.format("数据包大小无效: %d",
                    packet != null ? packet.length : 0));
            return false;
        }

        try {
            // 检查计数器范围
            int counter = packet[0] & 0xFF;
            if (counter > COUNTER_MAX) {
                Log.w(TAG, "数据包计数器超出范围: " + counter);
                return false;
            }

            // 可以添加更多的验证逻辑
            // 比如检查特定字段的范围等

            return true;

        } catch (Exception e) {
            Log.e(TAG, "数据包验证失败", e);
            return false;
        }
    }

    /**
     * 解析数据包用于调试
     */
    public String parsePacketForDebug(byte[] packet) {
        if (!validatePacket(packet)) {
            return "无效数据包";
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(packet);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            StringBuilder debugInfo = new StringBuilder();
            debugInfo.append("数据包解析:\n");

            // 1. 计数器
            int counter = buffer.get() & 0xFF;
            debugInfo.append(String.format("计数器: %d\n", counter));

            // 2. TrafficEvent (8字节)
            byte eventCount = buffer.get();
            byte eventSummary = buffer.get();
            short nearestDistance = buffer.getShort();
            byte nearestType = buffer.get();
            byte nearestDelay = buffer.get();
            byte severeCount = buffer.get();
            byte accidentCount = buffer.get();

            debugInfo.append(String.format(
                    "交通事件: 总数=%d, 最近距离=%dm, 类型=%d, 延迟=%ds, 严重=%d, 事故=%d\n",
                    eventCount & 0xFF, nearestDistance & 0xFFFF, nearestType & 0xFF,
                    nearestDelay & 0xFF, severeCount & 0xFF, accidentCount & 0xFF
            ));

            // 3. TrafficLight (8字节)
            byte nextLightId = buffer.get();
            byte stateFlags = buffer.get();
//            short positionIndex = buffer.getShort();
            short distanceToNextLight = buffer.getShort();
            byte remainingTime = buffer.get();
            byte distanceToLight = buffer.get();
            byte speed = buffer.get();
            byte lightCount = buffer.get();

            int lightState = (stateFlags >> 2) & 0x03;
//            debugInfo.append(String.format(
//                    "交通灯: ID=%d, 状态=%d, 剩余时间=%ds, 距离=%dm, 速度=%dkm/h, 总数=%d\n",
//                    nextLightId & 0xFF, lightState, remainingTime & 0xFF,
//                    distanceToLight & 0xFF, speed & 0xFF, lightCount & 0xFF
//            ));
            debugInfo.append(String.format(
                    "交通灯: ID=%d, 状态=%d, 剩余时间=%ds, 距离=%dm, 速度=%dkm/h, 总数=%d\n",
                    nextLightId & 0xFF, lightState, remainingTime & 0xFF,
                    distanceToNextLight & 0xFFFF, speed & 0xFF, lightCount & 0xFF
            ));

            // 4. RouteOverview (8字节)
            short totalDistance = buffer.getShort();
            byte tollDistancePct = buffer.get();
            byte congestionFlags = buffer.get();
            short estimatedTime = buffer.getShort();
            short totalFee = buffer.getShort();

            int smooth = (congestionFlags >> 6) & 0x03;
            int slow = (congestionFlags >> 4) & 0x03;
            int jam = congestionFlags & 0x0F;
            debugInfo.append(String.format(
                    "路线概览: 距离=%.1fkm, 时间=%dmin, 费用=%.1f元, 拥堵=%d/%d/%d\n",
                    totalDistance / 1000.0, estimatedTime & 0xFFFF, totalFee / 10.0,
                    smooth * 10, slow * 10, jam * 10
            ));

            // 5. CANWeather (8字节)
            byte b0 = buffer.get();
            byte b1 = buffer.get();
            byte b2 = buffer.get();
            byte realTemperature = buffer.get();
            short weatherTotalDistance = buffer.getShort();
            short keyPoints = buffer.getShort();

            int routeHash = b0 & 0x7F;
            int dataValid = (b0 >> 7) & 0x01;
            int weatherCode = b1 & 0x0F;
            int tempConfidence = (b1 >> 4) & 0x0F;
            int precipLevel = b2 & 0x07;
            int warnType = (b2 >> 3) & 0x07;
            int warnLevel = (b2 >> 6) & 0x03;
            int temperatureCelsius = (realTemperature & 0xFF) - 40;

            debugInfo.append(String.format(
                    "天气预报: 路线哈希=%d, 有效=%d, 天气码=%d, 温度=%d°C, 置信度=%d, 覆盖距离=%dm\n",
                    routeHash, dataValid, weatherCode, temperatureCelsius,
                    tempConfidence, weatherTotalDistance & 0xFFFF
            ));

            return debugInfo.toString();

        } catch (Exception e) {
            Log.e(TAG, "数据包解析失败", e);
            return "数据包解析失败: " + e.getMessage();
        }
    }

    /**
     * 获取十六进制格式的数据包
     */
    public String getPacketHexString(byte[] packet) {
        if (packet == null) {
            return "空数据包";
        }

        StringBuilder hexBuilder = new StringBuilder();
        for (int i = 0; i < packet.length; i++) {
            hexBuilder.append(String.format("%02X", packet[i] & 0xFF));
            if ((i + 1) % 8 == 0) {
                hexBuilder.append(" "); // 每8字节加一个空格
            }
        }
        return hexBuilder.toString().trim();
    }

    /**
     * 获取当前消息计数器值
     */
    public int getCurrentCounter() {
        return messageCounter.get() & COUNTER_MAX;
    }

    /**
     * 重置消息计数器
     */
    public void resetCounter() {
        messageCounter.set(0);
        Log.d(TAG, "消息计数器已重置");
    }

    /**
     * 获取数据包大小
     */
    public int getPacketSize() {
        return PACKET_SIZE;
    }
}

//2025-11-26 09:41:24.156 18288-18288/com.tencent.navix D/DataPacker: 数据打包完成: 计数器=2, 大小=33字节