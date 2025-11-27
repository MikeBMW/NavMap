package com.tencent.navix.power.models;

/**
 * 路线概览数据结构
 */
public class RouteOverview {
    public short totalDistance;     // 总距离 (米)
    public byte tollDistancePct;    // 收费距离百分比 (%)
    public byte congestionFlags;    // 拥堵标志 (3段占比)
    public short estimatedTime;     // 预计时间 (分钟)
    public short totalFee;          // 总费用 (0.1元)

    public RouteOverview() {
        // 默认构造函数
    }

    // Getter 和 Setter 方法
    public short getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(short totalDistance) {
        this.totalDistance = totalDistance;
    }

    public byte getTollDistancePct() {
        return tollDistancePct;
    }

    public void setTollDistancePct(byte tollDistancePct) {
        this.tollDistancePct = tollDistancePct;
    }

    public byte getCongestionFlags() {
        return congestionFlags;
    }

    public void setCongestionFlags(byte congestionFlags) {
        this.congestionFlags = congestionFlags;
    }

    public short getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(short estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public short getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(short totalFee) {
        this.totalFee = totalFee;
    }

    /**
     * 获取实际费用（元）
     */
    public double getFeeInYuan() {
        return totalFee / 10.0;
    }

    /**
     * 获取拥堵等级分布
     */
    public int[] getCongestionLevels() {
        int smooth = (congestionFlags >> 6) & 0x03; // bit7-6: 畅通
        int slow = (congestionFlags >> 4) & 0x03;   // bit5-4: 缓行
        int jam = congestionFlags & 0x0F;           // bit3-0: 拥堵

        return new int[]{smooth * 10, slow * 10, jam * 10};
    }

    @Override
    public String toString() {
        int[] levels = getCongestionLevels();
        return String.format(
                "RouteOverview{距离=%.1fkm, 时间=%dmin, 费用=%.1f元, 拥堵=%d/%d/%d}",
                totalDistance / 1000.0, estimatedTime & 0xFFFF, getFeeInYuan(),
                levels[0], levels[1], levels[2]
        );
    }
}