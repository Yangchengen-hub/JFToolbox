package com.jifeng.toolbox.ui.flash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jifeng.toolbox.ui.components.LiquidGlassCard
import com.jifeng.toolbox.ui.theme.JFColors

/**
 * 镜像刷写进度组件。
 *
 * 功能:
 * - 分区选择 (可多选)
 * - 进度条显示
 * - 百分比显示
 * - 当前刷写分区信息
 */
data class FlashPartition(
    val name: String,
    val sizeBytes: Long = 0,
    val status: FlashStatus = FlashStatus.PENDING,
    val progress: Float = 0f
)

enum class FlashStatus {
    PENDING,
    FLASHING,
    SUCCESS,
    FAILED,
    SKIPPED
}

@Composable
fun FlashProgressCard(
    partitions: List<FlashPartition>,
    totalProgress: Float,
    currentPartition: String?,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(modifier = modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("刷写进度",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold)
                Text(
                    "${(totalProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = JFColors.Brand,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { totalProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )

            Spacer(Modifier.height(4.dp))

            currentPartition?.let {
                Text(
                    "正在刷写: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // 分区列表
            partitions.forEach { p ->
                PartitionProgressItem(p)
            }
        }
    }
}

@Composable
private fun PartitionProgressItem(partition: FlashPartition) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (partition.status) {
            FlashStatus.SUCCESS -> Icons.Default.CheckCircle
            FlashStatus.FAILED -> Icons.Default.Error
            else -> Icons.Default.Pending
        }
        val tint = when (partition.status) {
            FlashStatus.SUCCESS -> JFColors.Success
            FlashStatus.FAILED -> JFColors.Danger
            FlashStatus.FLASHING -> JFColors.Brand
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = null, tint = tint,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            partition.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (partition.status == FlashStatus.FLASHING) {
            Text(
                "${(partition.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = JFColors.Brand
            )
        }
    }
}
