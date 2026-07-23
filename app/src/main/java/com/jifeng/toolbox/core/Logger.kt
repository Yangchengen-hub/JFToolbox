package com.jifeng.toolbox.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局日志收集器 —— 供 Flash 页等可视化日志终端使用。
 */
object Logger {

    private val buffer = ArrayDeque<String>(2000)
    private val dateFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val listeners = mutableListOf<(String) -> Unit>()

    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String) = log("E", tag, msg)

    private fun log(level: String, tag: String, msg: String) {
        val ts = dateFmt.format(Date())
        val line = "[$ts] $level/$tag: $msg"
        synchronized(buffer) { buffer.addLast(line) }
        println(line)
        listeners.forEach { it(line) }
    }

    fun history(): List<String> = synchronized(buffer) { buffer.toList() }

    fun clear() = synchronized(buffer) { buffer.clear() }

    fun subscribe(l: (String) -> Unit) { listeners.add(l) }
    fun unsubscribe(l: (String) -> Unit) { listeners.remove(l) }
}
