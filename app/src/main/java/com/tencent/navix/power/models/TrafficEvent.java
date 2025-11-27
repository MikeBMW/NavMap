package com.tencent.navix.power.models;

/**
 * 交通事件数据结构
 */
public class TrafficEvent {
    public byte eventCount;        // 事件总数
    public byte eventSummary;      // 事件摘要 (bit7-6=最高等级, bit5-0=中度计数)
    public short nearestDistance;  // 最近事件距离 (米)
    public byte nearestType;       // 最近事件类型 (12/13/21)
    public byte nearestDelay;      // 最近事件延迟 (秒)
    public byte severeCount;       // 严重事件数量
    public byte accidentCount;     // 事故数量

    public TrafficEvent() {
        // 默认构造函数
    }

    // Getter 和 Setter 方法
    public byte getEventCount() {
        return eventCount;
    }

    public void setEventCount(byte eventCount) {
        this.eventCount = eventCount;
    }

    public byte getEventSummary() {
        return eventSummary;
    }

    public void setEventSummary(byte eventSummary) {
        this.eventSummary = eventSummary;
    }

    public short getNearestDistance() {
        return nearestDistance;
    }

    public void setNearestDistance(short nearestDistance) {
        this.nearestDistance = nearestDistance;
    }

    public byte getNearestType() {
        return nearestType;
    }

    public void setNearestType(byte nearestType) {
        this.nearestType = nearestType;
    }

    public byte getNearestDelay() {
        return nearestDelay;
    }

    public void setNearestDelay(byte nearestDelay) {
        this.nearestDelay = nearestDelay;
    }

    public byte getSevereCount() {
        return severeCount;
    }

    public void setSevereCount(byte severeCount) {
        this.severeCount = severeCount;
    }

    public byte getAccidentCount() {
        return accidentCount;
    }

    public void setAccidentCount(byte accidentCount) {
        this.accidentCount = accidentCount;
    }

    @Override
    public String toString() {
        return String.format(
                "TrafficEvent{事件数=%d, 最近距离=%dm, 最近类型=%d, 严重事件=%d, 事故=%d}",
                eventCount & 0xFF, nearestDistance & 0xFFFF, nearestType & 0xFF,
                severeCount & 0xFF, accidentCount & 0xFF
        );
    }
}