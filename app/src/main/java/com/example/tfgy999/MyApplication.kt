// MyApplication.kt
package com.example.tfgy999

import android.app.Application
import leakcanary.LeakCanary

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化LeakCanary → Initialize LeakCanary
        LeakCanary.config = LeakCanary.config.copy(dumpHeap = true)
    }
}
