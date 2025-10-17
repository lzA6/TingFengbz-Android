package com.example.tfgy999

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log

/**
 * 设备工具类，提供设备相关信息的获取方法，例如温度。
 * 所有方法都是静态的，通过单例对象访问。
 */
object DeviceUtils {
    private const val TAG = "DeviceUtils"

    /**
     * 获取设备温度，目前使用电池温度作为近似值。
     *
     * @param context 应用程序上下文
     * @return 温度值（摄氏度），如果无法获取则返回 0.0f
     */
    fun getTemperature(context: Context): Float {
        return getBatteryTemperature(context)
    }

    /**
     * 获取 GPU 温度。由于 Android API 不直接提供 GPU 温度访问接口，
     * 暂时使用电池温度作为近似值。
     * TODO: 如果需要更精确的 GPU 温度，可以考虑通过硬件特定的 API 或 NDK 获取。
     *
     * @param context 应用程序上下文
     * @return 温度值（摄氏度），如果无法获取则返回 0.0f
     */
    fun getGpuTemperature(context: Context): Float {
        Log.w(TAG, "GPU 温度暂不支持直接获取，返回电池温度作为近似值")
        return getBatteryTemperature(context)
    }

    /**
     * 获取电池温度，通过 BatteryManager 获取原始数据并转换为摄氏度。
     *
     * @param context 应用程序上下文
     * @return 电池温度（摄氏度），如果获取失败则返回 0.0f
     */
    private fun getBatteryTemperature(context: Context): Float {
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent == null) {
                Log.e(TAG, "无法获取电池信息，registerReceiver 返回 null")
                return 0.0f
            }
            val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            return temperature / 10f // BatteryManager 返回值是以 1/10°C 为单位的整数
        } catch (e: Exception) {
            Log.e(TAG, "获取电池温度失败: ${e.message}", e)
            return 0.0f
        }
    }
}
