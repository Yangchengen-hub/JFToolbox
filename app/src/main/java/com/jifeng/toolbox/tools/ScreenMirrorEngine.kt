package com.jifeng.toolbox.tools

import com.jifeng.toolbox.adb.AdbManager
import com.jifeng.toolbox.core.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 屏幕镜像引擎 —— 从被控端拉取屏幕帧并暴露为 Flow。
 *
 * 模式:
 * - SCREENCAP_POLL: 反复执行 `adb exec-out screencap -p` 拉取 PNG。免依赖, 默认 5 FPS。
 * - SCREENRECORD_H264: 占位, 暂未实现 (避免引入 MediaCodec + native 解码依赖)。
 *
 * 帧通过 frames (SharedFlow) 推送, 引擎状态通过 state (StateFlow) 暴露。
 * UI 端可调 setDeviceSize() 写入由 `wm size` 解析得到的真实设备屏幕尺寸,
 * 用于把 UI 上的点击坐标缩放到设备屏幕真实坐标。
 */
object ScreenMirrorEngine {

    private const val TAG = "ScreenMirrorEngine"

    sealed class Frame {
        data class Jpeg(val data: ByteArray, val w: Int, val h: Int) : Frame()
        object End : Frame()
    }

    sealed class State {
        object Idle : State()
        data class Streaming(val w: Int, val h: Int) : State()
        data class Failed(val msg: String) : State()
    }

    enum class Mode { SCREENCAP_POLL, SCREENRECORD_H264 }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: MutableStateFlow<State> = _state

    private val _frames = MutableSharedFlow<Frame>(replay = 0, extraBufferCapacity = 4)
    val frames: SharedFlow<Frame> = _frames.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    // 设备真实屏幕尺寸 (像素), 由 UI 通过 wm size 解析后 setDeviceSize 写入。
    @Volatile private var cachedDeviceW: Int = 0
    @Volatile private var cachedDeviceH: Int = 0

    fun setDeviceSize(w: Int, h: Int) {
        if (w > 0 && h > 0) { cachedDeviceW = w; cachedDeviceH = h }
    }

    fun deviceSize(): Pair<Int, Int> = Pair(cachedDeviceW, cachedDeviceH)

    fun start(serial: String, mode: Mode = Mode.SCREENCAP_POLL, targetFps: Int = 5): Boolean {
        if (job?.isActive == true) {
            Logger.w(TAG, "已有镜像任务在运行")
            return false
        }
        if (!AdbManager.isConnected) {
            _state.value = State.Failed("ADB 未连接")
            return false
        }
        return when (mode) {
            Mode.SCREENRECORD_H264 -> {
                _state.value = State.Failed("实验性, 暂未实现")
                false
            }
            Mode.SCREENCAP_POLL -> {
                _state.value = State.Idle
                job = scope.launch { runScreencapPoll(serial, targetFps) }
                true
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = State.Idle
        scope.launch { _frames.emit(Frame.End) }
    }

    private suspend fun runScreencapPoll(serial: String, targetFps: Int) {
        val intervalMs = (1000 / targetFps.coerceAtLeast(1)).toLong()
        try {
            while (true) {
                val t0 = System.currentTimeMillis()
                val png: ByteArray? = withContext(Dispatchers.IO) {
                    AdbManager.shellBytes(serial, "screencap -p")
                }
                if (png == null || png.size < 24) {
                    _state.value = State.Failed("截屏失败: 无数据")
                    break
                }
                if (!isPng(png)) {
                    _state.value = State.Failed("截屏数据非 PNG 格式")
                    break
                }
                val (w, h) = parsePngSize(png)
                // 兜底: UI 未通过 wm size 写入时, 用帧尺寸做坐标缩放
                if (cachedDeviceW == 0) setDeviceSize(w, h)
                _state.value = State.Streaming(w, h)
                _frames.emit(Frame.Jpeg(data = png, w = w, h = h))
                val cost = System.currentTimeMillis() - t0
                val sleep = intervalMs - cost
                if (sleep > 0) delay(sleep)
            }
        } catch (e: CancellationException) {
            // 正常取消, 不上报失败
        } catch (e: Exception) {
            Logger.e(TAG, "screencap 轮询异常: ${e.message}")
            _state.value = State.Failed("轮询异常: ${e.message}")
        } finally {
            _state.value = State.Idle
        }
    }

    // PNG 文件签名
    private val PNG_SIG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    private fun isPng(data: ByteArray): Boolean {
        if (data.size < PNG_SIG.size) return false
        for (i in PNG_SIG.indices) if (data[i] != PNG_SIG[i]) return false
        return true
    }

    /** 从 PNG IHDR chunk 解析宽高 (offset 16/20, 大端 4 字节)。 */
    private fun parsePngSize(data: ByteArray): Pair<Int, Int> {
        val w = ByteBuffer.wrap(data, 16, 4).order(ByteOrder.BIG_ENDIAN).int
        val h = ByteBuffer.wrap(data, 20, 4).order(ByteOrder.BIG_ENDIAN).int
        return Pair(w, h)
    }
}
