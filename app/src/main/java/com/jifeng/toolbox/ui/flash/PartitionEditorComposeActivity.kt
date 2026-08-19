package com.jifeng.toolbox.ui.flash

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFTheme

/**
 * 分区表编辑器 v2。
 *
 * 支持:
 * 1) 编辑本地救砖包的分区表 (从文件加载 → 编辑 → 保存)
 * 2) 编辑目标设备的分区表 (通过 ADB 读取 → 编辑 → 写回)
 */
class PartitionEditorComposeActivity : ComponentActivity() {

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadPartitionTableFromFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JFTheme { PartitionEditorScreen() } }
    }

    private fun loadPartitionTableFromFile(uri: Uri) {
        // TODO: 实现从文件加载分区表
    }
}

data class PartitionEntry(
    val name: String,
    val startLba: Long,
    val sizeLba: Long,
    val type: String,
    val attributes: Long = 0
)

@Composable
private fun PartitionEditorScreen() {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("本地救砖包", "目标设备")

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("分区表编辑", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("编辑本地救砖包分区表 / 编辑目标设备分区表",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(title) })
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tabIndex) {
                0 -> LocalPartitionEditor()
                1 -> DevicePartitionEditor()
            }
        }
    }
}

@Composable
private fun LocalPartitionEditor() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var partitions by remember { mutableStateOf<List<PartitionEntry>>(emptyList()) }
    var loadedFile by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SdStorage, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    loadedFile ?: "未加载文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    // 触发文件选择
                }) {
                    Text("加载救砖包")
                }
            }
        }

        if (partitions.isNotEmpty()) {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("分区列表 (${partitions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Row {
                            IconButton(onClick = { /* 添加分区 */ }) {
                                Icon(Icons.Default.Add, contentDescription = "添加")
                            }
                            IconButton(onClick = { /* 保存 */ }) {
                                Icon(Icons.Default.Save, contentDescription = "保存")
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(partitions) { p ->
                            PartitionRow(p, onEdit = {})
                        }
                    }
                }
            }
        } else {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 24.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SdStorage, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp))
                    Text("请先加载救砖包文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("支持 rawprogram.xml / partition.img / GPT 头部镜像",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DevicePartitionEditor() {
    var isConnected by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null,
                    tint = if (isConnected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (isConnected) "已连接设备" else "未连接",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    // TODO: ADB 读取分区表
                    isConnected = !isConnected
                }) {
                    Text(if (isConnected) "刷新" else "读取设备分区")
                }
            }
        }

        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 24.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp))
                Text("设备分区表编辑器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("通过 ADB 读取目标设备分区表, 编辑后写回",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PartitionRow(entry: PartitionEntry, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                "LBA: ${entry.startLba} · 大小: ${entry.sizeLba} sectors · ${entry.type}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}
