package com.jifeng.toolbox.ui.crypto

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.tools.FileCrypto
import com.jifeng.toolbox.ui.components.JFScaffold
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CryptoComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CryptoScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CryptoScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("文件加密", "文本加密")

    // 文件模式状态
    var inputPath by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedAlgo by remember { mutableStateOf(FileCrypto.CryptoAlgorithm.AES_256) }
    var resultMsg by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // 文本模式状态
    var inputText by remember { mutableStateOf("") }
    var textPassword by remember { mutableStateOf("") }
    var textAlgo by remember { mutableStateOf(FileCrypto.CryptoAlgorithm.AES_256) }
    var textResult by remember { mutableStateOf("") }
    var textIsEncrypting by remember { mutableStateOf(true) } // true=加密, false=解密

    var algoExpanded by remember { mutableStateOf(false) }
    var textAlgoExpanded by remember { mutableStateOf(false) }

    JFScaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState())) {
            Text("加密解密工具", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Tab切换
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                        text = { Text(title) })
                }
            }
            Spacer(Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    // 文件加密/解密
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 算法选择
                            ExposedDropdownMenuBox(
                                expanded = algoExpanded,
                                onExpandedChange = { algoExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedAlgo.displayName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("加密算法") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algoExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = algoExpanded,
                                    onDismissRequest = { algoExpanded = false }
                                ) {
                                    FileCrypto.CryptoAlgorithm.values().forEach { algo ->
                                        DropdownMenuItem(
                                            text = { Text(algo.displayName) },
                                            onClick = {
                                                selectedAlgo = algo
                                                algoExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = inputPath,
                                onValueChange = { inputPath = it },
                                label = { Text("文件路径") },
                                placeholder = { Text("/sdcard/Download/file.zip") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("密码") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (inputPath.isBlank() || password.isBlank()) {
                                            resultMsg = "请填写文件路径和密码"
                                            return@Button
                                        }
                                        isProcessing = true
                                        resultMsg = "加密中..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                val input = File(inputPath)
                                                val output = File(inputPath + ".jfc")
                                                FileCrypto.encryptFile(input, output, password, selectedAlgo)
                                            }
                                            isProcessing = false
                                            resultMsg = result.message
                                        }
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (isProcessing) "处理中..." else "加密") }

                                Button(
                                    onClick = {
                                        if (inputPath.isBlank() || password.isBlank()) {
                                            resultMsg = "请填写文件路径和密码"
                                            return@Button
                                        }
                                        isProcessing = true
                                        resultMsg = "解密中..."
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                val input = File(inputPath)
                                                val outputName = inputPath.removeSuffix(".jfc") + ".dec"
                                                val output = File(outputName)
                                                FileCrypto.decryptFile(input, output, password)
                                            }
                                            isProcessing = false
                                            resultMsg = result.message
                                        }
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier.weight(1f)
                                ) { Text("解密") }
                            }

                            if (resultMsg.isNotEmpty()) {
                                Text(resultMsg, style = MaterialTheme.typography.bodyMedium,
                                    color = if (resultMsg.contains("完成")) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                1 -> {
                    // 文本加密/解密
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 算法选择
                            ExposedDropdownMenuBox(
                                expanded = textAlgoExpanded,
                                onExpandedChange = { textAlgoExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = textAlgo.displayName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("加密算法") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = textAlgoExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = textAlgoExpanded,
                                    onDismissRequest = { textAlgoExpanded = false }
                                ) {
                                    FileCrypto.CryptoAlgorithm.values().forEach { algo ->
                                        DropdownMenuItem(
                                            text = { Text(algo.displayName) },
                                            onClick = {
                                                textAlgo = algo
                                                textAlgoExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = { Text(if (textIsEncrypting) "要加密的文本" else "要解密的文本 (Base64)") },
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                            )

                            OutlinedTextField(
                                value = textPassword,
                                onValueChange = { textPassword = it },
                                label = { Text("密码") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        textIsEncrypting = true
                                        val result = FileCrypto.encryptText(inputText, textPassword, textAlgo)
                                        textResult = if (result.ok) result.outputText ?: "" else result.message
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("加密文本") }

                                Button(
                                    onClick = {
                                        textIsEncrypting = false
                                        val result = FileCrypto.decryptText(inputText, textPassword)
                                        textResult = if (result.ok) result.outputText ?: "" else result.message
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("解密文本") }
                            }

                            if (textResult.isNotEmpty()) {
                                Text("结果:", style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold)
                                Text(textResult, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}
