package com.jifeng.toolbox.ui.disclaimer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.permission.PermissionComposeActivity
import com.jifeng.toolbox.ui.main.MainComposeActivity
import com.jifeng.toolbox.ui.theme.JFTheme

class DisclaimerComposeActivity : ComponentActivity() {

    companion object {
        const val PREFS = "jf_disclaimer"
        const val KEY_ACCEPTED = "accepted"
        const val KEY_PERMISSION_ASKED = "permission_asked"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ACCEPTED, false)) {
            // 已同意: 权限询问过则进主页, 否则先进权限页
            val target = if (prefs.getBoolean(KEY_PERMISSION_ASKED, false)) {
                MainComposeActivity::class.java
            } else {
                PermissionComposeActivity::class.java
            }
            startActivity(Intent(this, target))
            finish(); return
        }
        setContent { JFTheme { DisclaimerScreen(onAccept = { accept() }, onExit = { finishAffinity() }) } }
    }

    /** 同意免责声明: 写入标记后跳转权限申请页 (而非直接主页)。 */
    private fun accept() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putBoolean(KEY_ACCEPTED, true).apply()
        startActivity(Intent(this, PermissionComposeActivity::class.java))
        finish()
    }
}

/** 免责声明条款: 标题 / 正文 / 图标 */
private data class Clause(val title: String, val body: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val CLAUSES = listOf(
    Clause("一、风险告知",
        "刷机、解锁 Bootloader、写入底层分区(含 9008 EDL 模式)属于高危操作, 可能导致设备永久变砖、数据全部丢失、失去官方保修、触发 KNOX/安全熔丝等不可逆后果。",
        Icons.Default.Security),
    Clause("二、责任归属",
        "本软件(极风工具箱/JFToolbox)仅供技术研究、学习交流与个人设备维护使用。使用者必须为设备合法所有者或已获得明确授权。严禁用于: ① 销售已刷机设备牟利 ② 修改他人设备 ③ 绕过数字版权保护(DRM) ④ 任何违反《中华人民共和国网络安全法》《计算机信息系统安全保护条例》及使用者所在地法律法规的行为。",
        Icons.Default.Gavel),
    Clause("三、数据安全",
        "使用本软件前, 使用者有义务对设备内全部数据进行完整备份。因刷机导致的数据丢失、损坏, 作者与极风工作室不承担任何责任。",
        Icons.Default.Security),
    Clause("四、知识产权",
        "本软件涉及的所有商标(含 Android、HyperOS、MIUI、Fastboot、高通 9008 等)归各自所有者所有。本软件不内置任何受版权保护的固件、ROM、镜像文件, 用户需自行获取合法授权的刷机包。",
        Icons.Default.Gavel),
    Clause("五、免责声明",
        "本软件按'现状'提供(AS IS), 不提供任何明示或暗示的担保。在适用法律允许的最大范围内, 作者及极风工作室不对因使用本软件而产生的任何直接、间接、附带、特殊、衍生或惩罚性损害承担责任, 包括但不限于: 设备损坏、数据丢失、业务中断、利润损失。",
        Icons.Default.Security),
    Clause("六、开源协议",
        "本软件遵循 GPL-3.0 开源协议, 任何人可自由使用、修改、分发, 但衍生作品必须同样开源。",
        Icons.Default.Gavel),
    Clause("七、第三方组件",
        "本软件使用了 AOSP、OkHttp、JSch、BouncyCastle、Apache Commons Compress、Jetpack Compose 等开源组件, 各遵循其原始许可证。",
        Icons.Default.Security),
    Clause("八、接受条款",
        "点击'我已阅读并同意'即表示您已完整阅读、理解并自愿接受上述全部条款, 自愿承担使用本软件的一切风险与后果。",
        Icons.Default.Gavel)
)

@Composable
private fun DisclaimerScreen(onAccept: () -> Unit, onExit: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Spacer(Modifier.height(20.dp))
            Icon(Icons.Default.Security, contentDescription = null,
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(56.dp))
            Text("免责声明", style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text("使用极风工具箱前, 请仔细阅读以下全部条款:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 每条条款单独一块 LiquidGlassCard
            CLAUSES.forEach { clause ->
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(clause.icon, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(clause.title, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                        Text(clause.body, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("我已阅读并同意", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("不同意, 退出", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
