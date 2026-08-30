package com.jifeng.toolbox

import android.app.Application
import android.content.Context
import android.util.Log
import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.CrashHandler
import com.jifeng.toolbox.core.ThemeManager
import com.jifeng.toolbox.notify.FlashNotificationManager
import com.jifeng.toolbox.terminal.TerminalEngine
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

        // 稳定性保障：全局崩溃捕获（最先安装，接管后续所有线程异常）
        CrashHandler.install(this)

        // Init subsystems
        ThemeManager.init(this)
        AdbManager.init(this)
        FlashNotificationManager.init(this)
        TerminalEngine.init(this)

        Log.i("JFToolbox", "极风工具箱 v${BuildConfig.VERSION_NAME} 已启动")
    }

    fun appContext(): Context = applicationContext
}
