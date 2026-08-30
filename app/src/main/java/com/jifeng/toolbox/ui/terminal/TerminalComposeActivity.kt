package com.jifeng.toolbox.ui.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jifeng.toolbox.terminal.TerminalEngine
import com.jifeng.toolbox.ui.theme.JFTheme
import kotlinx.coroutines.launch

class TerminalComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoCommand = intent?.getStringExtra("auto_command")
        setContent { JFTheme { TerminalScreen(autoCommand) } }
    }
}

private val TERM_BG = Color(0xFF000000)
private val TERM_FG = Color(0xFFE8E8E8)
private val TERM_PROMPT = Color(0xFF4ADE80)
private val TERM_ACCENT = Color(0xFF7DD3FC)
private val TERM_DIM = Color(0xFF6B7280)
private val TERM_ERR = Color(0xFFF87171)
private val TERM_BAR = Color(0xFF0A0A0A)
private val TERM_BAR_DIVIDER = Color(0xFF1A1A1A)

@Composable
private fun TerminalScreen(autoCommand: String? = null) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<TermLine>() }
    var input by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf(TerminalEngine.currentDir()) }
    val history = remember { mutableStateListOf<String>() }
    var historyIdx by remember { mutableStateOf(-1) }
    val scrollState = rememberScrollState()

    fun append(line: TermLine) {
        logs.add(line)
        scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
    }

    fun clearScreen() {
        logs.clear()
    }

    fun runCommand(raw: String) {
        val cmd = raw.trim()
        if (cmd.isBlank() || isRunning) return
        append(TermLine.Prompt(TerminalEngine.currentDir(), cmd))
        if (history.isEmpty() || history.last() != cmd) history.add(cmd)
        historyIdx = history.size
        isRunning = true
        scope.launch {
            val r = TerminalEngine.run(cmd)
            if (r.clearScreen) {
                clearScreen()
            } else if (r.output.isNotBlank()) {
                append(TermLine.Output(r.output, r.isError))
            }
            cwd = TerminalEngine.currentDir()
            isRunning = false
        }
    }

    // 打开即显示极简 banner (不执行任何阻塞命令)
    LaunchedEffect(Unit) {
        cwd = TerminalEngine.currentDir()
        append(TermLine.Output(TerminalEngine.banner(), false))
    }

    LaunchedEffect(autoCommand) {
        if (!autoCommand.isNullOrBlank()) runCommand(autoCommand)
    }

    Box(modifier = Modifier.fillMaxSize().background(TERM_BG)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 极简状态栏
            Row(modifier = Modifier.fillMaxWidth().background(TERM_BAR_DIVIDER)
                .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = if (isRunning) TERM_ERR else TERM_PROMPT, fontSize = 9.sp)
                Spacer(Modifier.width(6.dp))
                Text("jf-term", color = TERM_FG, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text(cwd, color = TERM_DIM, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1)
            }
            // 输出区
            Column(modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(scrollState).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)) {
                logs.forEach { line ->
                    when (line) {
                        is TermLine.Prompt -> Row(modifier = Modifier.fillMaxWidth()) {
                            Text("jif:", color = TERM_PROMPT, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            Text(shortPath(line.cwd), color = TERM_ACCENT, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            Text("> ", color = TERM_DIM, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            Text(line.cmd, color = TERM_FG, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                        is TermLine.Output -> Text(
                            text = line.text,
                            color = if (line.isError) TERM_ERR else TERM_FG,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            // 输入栏
            Row(modifier = Modifier.fillMaxWidth().background(TERM_BAR)
                .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("jif:${shortPath(cwd)}>", color = TERM_PROMPT, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).background(Color.Transparent),
                    textStyle = TextStyle(color = TERM_FG, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions.Default,
                    cursorBrush = SolidColor(TERM_PROMPT),
                    decorationBox = { inner ->
                        if (input.isEmpty()) Text("输入命令...  help 查看用法",
                            color = TERM_DIM, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        inner()
                    }
                )
                IconButton(onClick = {
                    if (input.isNotBlank() && !isRunning) {
                        val c = input; input = ""; runCommand(c)
                    }
                }, enabled = input.isNotBlank() && !isRunning) {
                    Icon(Icons.Filled.Send, contentDescription = "执行", tint = TERM_PROMPT)
                }
            }
        }
    }
}

/** 路径过长时折叠显示: /sdcard/Download/a/b → /sdcard/.../a/b */
private fun shortPath(p: String): String {
    if (p.length <= 24) return p
    val head = p.substringBefore("/", "")
    val segments = p.trim('/').split("/")
    if (segments.size <= 3) return p
    val first = segments.first()
    val tail = segments.takeLast(2).joinToString("/")
    return "/$first/.../$tail"
}

private sealed class TermLine {
    data class Prompt(val cwd: String, val cmd: String) : TermLine()
    data class Output(val text: String, val isError: Boolean) : TermLine()
}
