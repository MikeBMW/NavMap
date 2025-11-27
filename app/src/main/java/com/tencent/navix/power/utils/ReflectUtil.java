package com.tencent.navix.power.utils;

import android.util.Log;
import java.lang.reflect.Field;

/**
 * 反射工具类
 * 用于访问私有字段和方法
 */
public class ReflectUtil {
    private static final String TAG = "ReflectUtil";

    /**
     * 获取对象的字段值
     */
    public static Object getField(Object obj, String fieldName) {
        if (obj == null || fieldName == null) {
            return null;
        }

        try {
            Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception e) {
            Log.w(TAG, "获取字段失败: " + fieldName + " - " + e.getMessage());
        }

        return null;
    }

    /**
     * 设置对象的字段值
     */
    public static boolean setField(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) {
            return false;
        }

        try {
            Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "设置字段失败: " + fieldName + " - " + e.getMessage());
        }

        return false;
    }

    /**
     * 递归查找字段（包括父类）
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 转换为整型
     */
    public static int toInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 转换为长整型
     */
    public static long toLong(Object value, long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 转换为双精度浮点型
     */
    public static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 转换为字符串
     */
    public static String toString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
}