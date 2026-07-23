package com.jifeng.toolbox

import android.app.Application
import android.content.Context
import android.util.Log
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class JFToolboxApp : Application() {

    companion object {
        lateinit var instance: JFToolboxApp
            private set
        val appScope = CoroutineScope(SupervisorJob())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Init subsystems
        ThemeManager.init(this)
        AdbManager.init(this)

        Log.i("JFToolbox", "极风工具箱 v${BuildConfig.VERSION_NAME} 已启动")
    }

    fun appContext(): Context = applicationContext
}
