package com.jifeng.toolbox.ui.flash

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.edl.ProgramEntry
import com.jifeng.toolbox.edl.RawprogramParser
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.components.LogTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

class PartitionEditorComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PartitionEditorScreen() }
    }
}

@Composable
private fun PartitionEditorScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val parser = remember { RawprogramParser() }

    var packPath by remember { mutableStateOf<String?>(null) }
    var rawprogramFile by remember { mutableStateOf<File?>(null) }
    val entries = remember { mutableStateListOf<ProgramEntry>() }
    val selectedLabels = remember { mutableStateListOf<String>() }
    val logs = remember { mutableStateListOf<String>() }

    // 对话框状态
    var editingEntry by remember { mutableStateOf<ProgramEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val (path, rpFile) = withContext(Dispatchers.IO) {
                    // 复制到缓存
                    val tmp = File(ctx.cacheDir, "jf_part_${System.currentTimeMillis()}.zip")
                    ctx.contentResolver.openInputStream(it)?.use { i ->
                        tmp.outputStream().use { i.copyTo(it) }
                    }
                    // 解压找 rawprogram*.xml
                    val extractDir = File(tmp.parentFile, tmp.nameWithoutExtension + "_part").apply { mkdirs() }
                    try {
                        ZipFile(tmp).use { zf ->
                            zf.entries.toList().forEach { e ->
                                if (!e.isDirectory) {
                                    val out = File(extractDir, e.name)
                                    out.parentFile?.mkdirs()
                                    zf.getInputStream(e).use { it.copyTo(out.outputStream()) }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logs.add("❌ 解压失败: ${e.message}")
                    }
                    val rp = extractDir.listFiles()?.firstOrNull {
                        it.name.matches(Regex("rawprogram\\d+\\.xml", RegexOption.IGNORE_CASE))
                    }
                    Pair(tmp.absolutePath, rp)
                }
                packPath = path
                rawprogramFile = rpFile
                if (rpFile != null) {
                    entries.clear()
                    entries.addAll(parser.parse(rpFile))
                    selectedLabels.clear()
                    logs.add("✅ 已加载 ${rpFile.name}: ${entries.size} 条")
                } else {
                    logs.add("❌ 包内未找到 rawprogram*.xml")
                }
            }
        }
    }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("分区表编辑器", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // 救砖包路径选择
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("救砖包路径", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = packPath ?: "",
                            onValueChange = {},
                            modifier = Modifier.weight(1f),
                            readOnly = true,
                            placeholder = { Text("未选择") },
                            singleLine = true
                        )
                        OutlinedButton(onClick = {
                            launcher.launch(arrayOf("application/zip", "application/octet-stream"))
                        }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null,
                                modifier = Modifier.height(18.dp))
                            Text(" 选择")
                        }
                    }
                    rawprogramFile?.let {
                        Text("rawprogram: ${it.name}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 分区列表
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
                if (entries.isEmpty()) {
                    Text("请先选择救砖包", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("共 ${entries.size} 个分区 (选中 ${selectedLabels.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(entries) { entry ->
                            PartitionRow(
                                entry = entry,
                                isSelected = entry.label in selectedLabels,
                                onClick = {
                                    if (entry.label in selectedLabels) selectedLabels.remove(entry.label)
                                    else selectedLabels.add(entry.label)
                                },
                                onEdit = { editingEntry = entry }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 底部按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val f = rawprogramFile
                    if (f == null) { logs.add("❌ 未加载文件"); return@Button }
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { parser.writeXml(f, entries) }
                        logs.add(if (ok) "✅ 已保存 ${f.name}" else "❌ 保存失败")
                    }
                }, modifier = Modifier.weight(1f)) { Text("保存") }
                OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("添加")
                }
                OutlinedButton(onClick = {
                    if (selectedLabels.isEmpty()) {
                        logs.add("❌ 未选中条目")
                    } else {
                        val cnt = selectedLabels.size
                        selectedLabels.toList().forEach { parser.removeEntry(entries, it) }
                        selectedLabels.clear()
                        logs.add("已删除 $cnt 条")
                    }
                }, modifier = Modifier.weight(1f)) { Text("删除选中") }
                OutlinedButton(onClick = {
                    val warnings = parser.validateLayout(entries)
                    if (warnings.isEmpty()) logs.add("✅ 布局校验通过, 无重叠/空洞/越界")
                    else warnings.forEach { logs.add("⚠ $it") }
                }, modifier = Modifier.weight(1f)) { Text("校验") }
            }

            Spacer(Modifier.height(12.dp))

            // 校验结果 / 日志
            LogTerminal(logs, modifier = Modifier.fillMaxWidth())
        }
    }

    // 编辑对话框
    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onConfirm = { mods ->
                val newEntry = parser.editEntrySmart(entry, mods, entries)
                if (newEntry == null) {
                    logs.add("❌ 编辑 ${entry.label} 失败: 校验未通过 (重叠或非法值)")
                } else {
                    val idx = entries.indexOfFirst { it.label == entry.label }
                    if (idx >= 0) entries[idx] = newEntry
                    logs.add("✅ 已修改 ${entry.label}")
                }
                editingEntry = null
            }
        )
    }

    // 添加对话框
    if (showAddDialog) {
        AddEntryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newEntry ->
                val ok = parser.addEntry(entries, newEntry)
                logs.add(if (ok) "✅ 已添加 ${newEntry.label}" else "❌ 添加失败: label 重复")
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun PartitionRow(
    entry: ProgramEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyMedium,
                color = if (entry.isProtected) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (entry.isProtected) FontWeight.Bold else FontWeight.Normal)
            Text("${entry.filename} | @${entry.startSector} +${entry.numSectors}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${entry.sizeBytes / 1024} KB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = isSelected, onCheckedChange = { onClick() })
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "编辑")
        }
    }
}

@Composable
private fun EditEntryDialog(
    entry: ProgramEntry,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    var startSector by remember { mutableStateOf(entry.startSector.toString()) }
    var numSectors by remember { mutableStateOf(entry.numSectors.toString()) }
    var filename by remember { mutableStateOf(entry.filename) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 ${entry.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = startSector, onValueChange = { startSector = it },
                    label = { Text("start_sector") }, singleLine = true)
                OutlinedTextField(value = numSectors, onValueChange = { numSectors = it },
                    label = { Text("num_partition_sectors") }, singleLine = true)
                OutlinedTextField(value = filename, onValueChange = { filename = it },
                    label = { Text("filename") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(mapOf(
                    "start_sector" to startSector,
                    "num_partition_sectors" to numSectors,
                    "filename" to filename
                ))
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (ProgramEntry) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var filename by remember { mutableStateOf("") }
    var startSector by remember { mutableStateOf("0") }
    var numSectors by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加条目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text("label") }, singleLine = true)
                OutlinedTextField(value = filename, onValueChange = { filename = it },
                    label = { Text("filename") }, singleLine = true)
                OutlinedTextField(value = startSector, onValueChange = { startSector = it },
                    label = { Text("start_sector") }, singleLine = true)
                OutlinedTextField(value = numSectors, onValueChange = { numSectors = it },
                    label = { Text("num_partition_sectors") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ProgramEntry(
                    partitionNumber = 0,
                    label = label,
                    filename = filename,
                    startSector = startSector.toLongOrNull() ?: 0L,
                    numSectors = numSectors.toLongOrNull() ?: 0L,
                    physicalPartition = 0,
                    sectorSize = 4096,
                    isProtected = false
                ))
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
