package com.tencent.navix.power.models;

/**
 * CAN协议天气数据结构
 * 对应8字节的CAN天气数据包
 */
public class Weather {
    private byte routeHash;        // 7bit - 路线哈希
    private byte dataValid;        // 1bit - 数据有效性标志
    private byte weatherCode;      // 4bit - 天气代码
    private byte tempConfidence;   // 4bit - 温度置信度
    private byte precipLevel;      // 3bit - 降水等级
    private byte warnType;         // 3bit - 预警类型
    private byte warnLevel;        // 2bit - 预警等级
    private byte realTemperature;  // 8bit - 实际温度（-40~50°C，存储时+40）
    private short totalDistance;   // 16bit - 总距离（米）
    private short keyPoints;       // 16bit - 关键点数量

    public Weather() {
        // 默认构造函数
    }

    // Getter 和 Setter 方法
    public byte getRouteHash() {
        return routeHash;
    }

    public void setRouteHash(byte routeHash) {
        this.routeHash = routeHash;
    }

    public byte getDataValid() {
        return dataValid;
    }

    public void setDataValid(byte dataValid) {
        this.dataValid = dataValid;
    }

    public byte getWeatherCode() {
        return weatherCode;
    }

    public void setWeatherCode(byte weatherCode) {
        this.weatherCode = weatherCode;
    }

    public byte getTempConfidence() {
        return tempConfidence;
    }

    public void setTempConfidence(byte tempConfidence) {
        this.tempConfidence = tempConfidence;
    }

    public byte getPrecipLevel() {
        return precipLevel;
    }

    public void setPrecipLevel(byte precipLevel) {
        this.precipLevel = precipLevel;
    }

    public byte getWarnType() {
        return warnType;
    }

    public void setWarnType(byte warnType) {
        this.warnType = warnType;
    }

    public byte getWarnLevel() {
        return warnLevel;
    }

    public void setWarnLevel(byte warnLevel) {
        this.warnLevel = warnLevel;
    }

    public byte getRealTemperature() {
        return realTemperature;
    }

    public void setRealTemperature(byte realTemperature) {
        this.realTemperature = realTemperature;
    }

    public short getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(short totalDistance) {
        this.totalDistance = totalDistance;
    }

    public short getKeyPoints() {
        return keyPoints;
    }

    public void setKeyPoints(short keyPoints) {
        this.keyPoints = keyPoints;
    }

    /**
     * 获取实际温度值（转换为摄氏度）
     */
    public int getTemperatureCelsius() {
        return (realTemperature & 0xFF) - 40;
    }

    @Override
    public String toString() {
        return String.format(
                "CANWeather{routeHash=%d, dataValid=%d, weatherCode=%d, temp=%d°C, distance=%dm}",
                routeHash & 0x7F, dataValid & 0x01, weatherCode & 0x0F,
                getTemperatureCelsius(), totalDistance & 0xFFFF
        );
    }
}
