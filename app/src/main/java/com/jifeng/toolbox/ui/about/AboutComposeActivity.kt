package com.jifeng.toolbox.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.BuildConfig
import com.jifeng.toolbox.R
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFTheme

class AboutComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JFTheme { AboutScreen() } }
    }
}

/** 鸣谢条目: 项目名 / 作者 / 平台 / 跳转链接 */
private data class CreditItem(
    val name: String,
    val author: String,
    val platform: String,
    val url: String
)

private val CREDITS = listOf(
    CreditItem("ADB 协议实现参考", "Android Open Source Project", "GitHub",
        "https://github.com/aosp-mirror/platform_system_core"),
    CreditItem("Fastboot 协议参考", "Android Open Source Project", "GitHub",
        "https://github.com/aosp-mirror/platform_system_core/tree/main/fastboot"),
    CreditItem("JSch SSH 库", "JCraft (mwiede fork)", "GitHub",
        "https://github.com/mwiede/jsch"),
    CreditItem("BouncyCastle 加密库", "The Legion of the Bouncy Castle", "GitHub",
        "https://github.com/bcgit/bc-java"),
    CreditItem("OkHttp 网络库", "Square", "GitHub",
        "https://github.com/square/okhttp"),
    CreditItem("Apache Commons Compress", "Apache Software Foundation", "GitHub",
        "https://github.com/apache/commons-compress"),
    CreditItem("Compose Material3", "Google", "GitHub",
        "https://github.com/androidx/androidx"),
    CreditItem("Liquid Glass UI 灵感", "Apple (iOS 26)", "官网",
        "https://developer.apple.com/design/human-interface-guidelines/material"),
    CreditItem("HyperOS 动画规范参考", "小米", "官网",
        "https://hyper.mi.com"),
    CreditItem("卡刷包解析思路", "酷安@某只寄托", "酷安",
        "https://www.coolapk.com/feed/45000000"),
    CreditItem("9008 EDL 救砖原理", "酷安@高通刷机研究组", "酷安",
        "https://www.coolapk.com/feed/45000001"),
    CreditItem("分区表工具灵感", "酷安@PartitionTool", "酷安",
        "https://www.coolapk.com/apk/com.partition.tool")
)

/** 联系方式: 标签 / 详情 / 主链接 / 备用链接 */
private data class ContactItem(
    val label: String,
    val detail: String,
    val icon: ImageVector,
    val primaryUrl: String,
    val fallbackUrl: String? = null
)

private val CONTACTS = listOf(
    ContactItem("酷安", "极风工作室", Icons.Default.Forum,
        "https://www.coolapk.com/dynasty/22800000"),
    ContactItem("QQ群", "1083612300", Icons.Default.Forum,
        "mqqopensdkapi://card/show_pslcard?src_type=internal&version=1&uin=1083612300&card_type=group&source=qrcode",
        fallbackUrl = "https://qm.qq.com/q/1083612300"),
    ContactItem("Telegram", "纸飞机账号", Icons.Default.Send,
        "https://t.me/jftoolbox")
)

@Composable
private fun AboutScreen() {
    val ctx = LocalContext.current
    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Spacer(Modifier.height(24.dp))

            // 作者头像 (卡通) + 网名 + 版本
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.ic_avatar_cartoon),
                    contentDescription = "作者头像",
                    modifier = Modifier.size(88.dp)
                )
                Column {
                    Text("诺言", style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text("极风工作室", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 鸣谢名单
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("鸣谢名单", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("感谢以下作者与项目提供的技术参考与开源组件, 点击每行可跳转:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    CREDITS.forEach { item -> CreditRow(item) { openUrl(ctx, item.url) } }
                }
            }

            // 联系方式
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("联系方式", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    CONTACTS.forEach { item ->
                        ContactRow(item) {
                            if (item.fallbackUrl != null) {
                                openUrlWithFallback(ctx, item.primaryUrl, item.fallbackUrl)
                            } else {
                                openUrl(ctx, item.primaryUrl)
                            }
                        }
                    }
                }
            }

            // 底部开源协议
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .clickable { openUrl(ctx, "https://github.com/Yangchengen-hub/JFToolbox") }
                .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("本软件开源, 遵循 GPL-3.0 协议",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }

            Text("极风工作室 · 作者: 诺言", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("本软件仅供技术研究, 刷机有风险, 操作需谨慎。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CreditRow(item: CreditItem, onClick: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text("${item.author} · ${item.platform}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.Web, contentDescription = "打开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ContactRow(item: ContactItem, onClick: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(item.icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(item.detail, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.Web, contentDescription = "打开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}

private fun openUrlWithFallback(ctx: Context, primary: String, fallback: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(primary)))
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)))
        } catch (_: Exception) {}
    }
}
