package com.tencent.navix.power.models;

/**
 * 交通灯数据结构
 */
public class TrafficLight {
    public byte nextLightId;       // 下一个红绿灯ID
    public byte stateFlags;        // 状态标志 (bit5-4=类型, bit3-2=状态)  bit1和bit0为是否通过的标记，bit 0 通过则置1，未通过置0； bit1 预留置0
//    public short positionIndex;    // 位置索引
    public short distanceToNextLight;    // 位置索引 改成  distanceToNextLight  到下一个红绿灯的距离 (米)
    public byte remainingTime;     // 剩余时间 (秒)
    public byte distanceToLight;   // 【预留】到红绿灯距离 (米)
    public byte speed;             // 速度 (km/h)
    public byte lightCount;        // 红绿灯总数

    public TrafficLight() {
        // 默认构造函数
    }

    // Getter 和 Setter 方法
    public byte getNextLightId() {
        return nextLightId;
    }

    public void setNextLightId(byte nextLightId) {
        this.nextLightId = nextLightId;
    }

    public byte getStateFlags() {
        return stateFlags;
    }

    public void setStateFlags(byte stateFlags) {
        this.stateFlags = stateFlags;
    }

//    public short getPositionIndex() {
//        return positionIndex;
//    }
    public short getDistanceToNextLight() {
        return distanceToNextLight;
    }

//    public void setPositionIndex(short positionIndex) {
//        this.positionIndex = positionIndex;
//    }
    public void setDistanceToNextLight(short distanceToNextLight) {
        this.distanceToNextLight = distanceToNextLight;
    }

    public byte getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(byte remainingTime) {
        this.remainingTime = remainingTime;
    }

    public byte getDistanceToLight() {
        return distanceToLight;
    }

    public void setDistanceToLight(byte distanceToLight) {
        this.distanceToLight = distanceToLight;
    }

    public byte getSpeed() {
        return speed;
    }

    public void setSpeed(byte speed) {
        this.speed = speed;
    }

    public byte getLightCount() {
        return lightCount;
    }

    public void setLightCount(byte lightCount) {
        this.lightCount = lightCount;
    }

    /**
     * 获取交通灯状态
     */
    public int getLightState() {
        return (stateFlags >> 2) & 0x03; // 提取bit3-2
    }

    /**
     * 获取交通灯类型
     */
    public int getLightType() {
        return (stateFlags >> 4) & 0x03; // 提取bit5-4
    }

    @Override
    public String toString() {
        return String.format(
                "TrafficLight{距离=%dm, 剩余时间=%ds, 状态=%d, 速度=%dkm/h, 总数=%d}",
                distanceToLight & 0xFF, remainingTime & 0xFF, getLightState(),
                speed & 0xFF, lightCount & 0xFF
        );
    }
}