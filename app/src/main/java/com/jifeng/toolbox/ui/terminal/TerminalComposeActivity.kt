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
import com.jifeng.toolbox.ui.theme.JFTheme
import kotlinx.coroutines.launch

class TerminalComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoCommand = intent?.getStringExtra("auto_command")
        val autoLang = intent?.getStringExtra("auto_lang") ?: "shell"
        setContent { JFTheme { TerminalScreen(autoCommand, autoLang) } }
    }
}

private val TERM_BG = Color(0xFF000000)
private val TERM_FG = Color(0xFFE6E6E6)
private val TERM_PROMPT = Color(0xFF4ADE80)
private val TERM_ACCENT = Color(0xFF7DD3FC)
private val TERM_DIM = Color(0xFF888888)
private val TERM_ERR = Color(0xFFF87171)

@Composable
private fun TerminalScreen(autoCommand: String? = null, autoLang: String = "shell") {
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(autoLang) }
    var isRunning by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<TermLine>() }
    var input by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) { cwd = TerminalEngine.currentDir() }

    fun append(line: TermLine) {
        logs.add(line)
        scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
    }
    fun runCommand(cmd: String, language: String) {
        if (cmd.isBlank() || isRunning) return
        append(TermLine.Prompt(cwd, language, cmd))
        isRunning = true
        scope.launch {
            val r = TerminalEngine.execute(language, cmd)
            val isErr = r.output.contains("[exit=") && !r.output.contains("[exit=0]")
            append(TermLine.Output(r.output, isErr))
            cwd = TerminalEngine.currentDir()
            isRunning = false
        }
    }
    LaunchedEffect(autoCommand) {
        if (!autoCommand.isNullOrBlank()) runCommand(autoCommand, autoLang)
        else append(TermLine.Output(buildString {
            appendLine("JF Toolbox Terminal v6 (Termux-style)")
            appendLine("持久 Shell - 多语言支持 - 工作目录保持")
            appendLine()
            appendLine("预装/可调用: sh, toybox, app_process")
            appendLine("安装更多工具: pkg install python nodejs clang git")
            appendLine("语言前缀: :py :js :lua 临时切换")
        }, false))
    }

    Box(modifier = Modifier.fillMaxSize().background(TERM_BG)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF111111))
                .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("●", color = if (isRunning) TERM_ERR else TERM_PROMPT, fontSize = 10.sp)
                Text("JF-Terminal", color = TERM_FG, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text("env: $lang", color = TERM_DIM, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text(cwd, color = TERM_DIM, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                listOf("sh" to "shell", "py" to "python", "js" to "javascript", "lua" to "lua").forEach { (tag, mapped) ->
                    val bg = if (lang == mapped) Color(0xFF1A3A1A) else Color.Transparent
                    Text(tag, color = if (lang == mapped) TERM_PROMPT else TERM_DIM,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.background(bg).padding(horizontal = 4.dp))
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(scrollState).padding(horizontal = 10.dp, vertical = 8.dp)) {
                logs.forEach { line ->
                    when (line) {
                        is TermLine.Prompt -> Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("jif:", color = TERM_PROMPT, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(line.cwd, color = TERM_ACCENT, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(" ${line.lang} $ ", color = TERM_DIM, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(line.cmd, color = TERM_FG, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        is TermLine.Output -> Text(line.text,
                            color = if (line.isError) TERM_ERR else TERM_FG,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A))
                .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("jif:$cwd>", color = TERM_PROMPT, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1)
                BasicTextField(value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f).background(Color.Transparent),
                    textStyle = TextStyle(color = TERM_FG, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    maxLines = 4, keyboardOptions = KeyboardOptions.Default,
                    cursorBrush = SolidColor(TERM_PROMPT),
                    decorationBox = { inner ->
                        if (input.isEmpty()) Text("输入命令...", color = TERM_DIM,
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        inner()
                    })
                IconButton(onClick = {
                    if (input.isNotBlank() && !isRunning) {
                        var cmd = input; var tl = lang
                        when {
                            cmd.startsWith(":py ") -> { tl = "python"; cmd = cmd.removePrefix(":py ") }
                            cmd.startsWith(":js ") -> { tl = "javascript"; cmd = cmd.removePrefix(":js ") }
                            cmd.startsWith(":lua ") -> { tl = "lua"; cmd = cmd.removePrefix(":lua ") }
                        }
                        input = ""
                        if (cmd.isNotBlank()) runCommand(cmd, tl)
                    }
                }, enabled = input.isNotBlank() && !isRunning) {
                    Icon(Icons.Filled.Send, contentDescription = "执行", tint = TERM_PROMPT)
                }
            }
        }
    }
}

private sealed class TermLine {
    data class Prompt(val cwd: String, val lang: String, val cmd: String) : TermLine()
    data class Output(val text: String, val isError: Boolean) : TermLine()
}
