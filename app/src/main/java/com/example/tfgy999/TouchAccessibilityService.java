package com.example.tfgy999;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class TouchAccessibilityService extends AccessibilityService {
    private static final String TAG = "TouchAccessibilityService";
    public static TouchAccessibilityService instance;

    // 主线程 Handler，用于处理延迟或异步操作
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "无障碍服务已创建");
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        Log.i(TAG, "无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 当前实现中无需监听事件，仅用于触控注入，因此留空
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.i(TAG, "无障碍服务已销毁");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    /**
     * 模拟单指触控移动到指定位置
     * @param x X坐标
     * @param y Y坐标
     * @param duration 手势持续时间（毫秒）
     */
    public void simulateTouch(float x, float y, long duration) {
        Path path = new Path();
        path.moveTo(x, y); // 移动到目标位置

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
                path, 0, duration
        );
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.i(TAG, "单指触控事件注入成功: X=" + x + ", Y=" + y);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.e(TAG, "单指触控事件注入取消");
            }
        }, mainHandler);
    }

    /**
     * 模拟多指触控，支持指定多个触点
     * @param points 触点数组，每个触点包含 x, y 坐标
     * @param duration 手势持续时间（毫秒）
     */
    public void simulateMultiTouch(PointF[] points, long duration) {
        if (points == null || points.length == 0) {
            Log.e(TAG, "多指触控失败：触点数组为空");
            return;
        }

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        for (int i = 0; i < points.length; i++) {
            Path path = new Path();
            path.moveTo(points[i].x, points[i].y);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(
                    path, 0, duration
            );
            gestureBuilder.addStroke(stroke);
        }

        GestureDescription gesture = gestureBuilder.build();
        boolean result = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.i(TAG, "多指触控事件注入成功: 触点数=" + points.length);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.e(TAG, "多指触控事件注入取消");
            }
        }, mainHandler);

        if (!result) {
            Log.e(TAG, "多指触控事件注入失败：服务未正确启用或权限不足");
        }
    }

    // 定义一个简单的 PointF 类，用于表示触点坐标
    public static class PointF {
        public float x;
        public float y;

        public PointF(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "PointF(x=" + x + ", y=" + y + ")";
        }
    }

    // 提供静态方法以便外部调用单指触控
    public static void performTouch(float x, float y, long duration) {
        if (instance != null) {
            instance.simulateTouch(x, y, duration);
        } else {
            Log.e(TAG, "无障碍服务实例未初始化，无法执行单指触控操作");
        }
    }

    // 提供静态方法以便外部调用多指触控
    public static void performMultiTouch(PointF[] points, long duration) {
        if (instance != null) {
            instance.simulateMultiTouch(points, duration);
        } else {
            Log.e(TAG, "无障碍服务实例未初始化，无法执行多指触控操作");
        }
    }
}