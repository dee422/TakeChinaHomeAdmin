package com.dee.android.pbl.takechinahome.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift

@Composable
fun AuditItemCard(
    item: ExchangeGift,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    var showFullImage by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // 列表缩略图
            AsyncImage(
                model = item.imageUrl,
                contentDescription = "物什图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { showFullImage = true },
                contentScale = ContentScale.Fit
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = item.itemName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "申请人: ${item.ownerEmail ?: "未知用户"}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = item.description ?: "该物什暂无描述信息。", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮区
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (item.status == 1) {
                        OutlinedButton(onClick = onReject, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("驳回") }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("准入画卷", color = Color.White) }
                    } else {
                        val statusText = if (item.status == 2) "已准入" else "已驳回"
                        val statusColor = if (item.status == 2) Color(0xFF4CAF50) else Color.Red
                        Text(text = statusText, color = statusColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                        Text("(已锁定)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }

    // ✨ 增强版大图预览弹窗（带手势监听）
    if (showFullImage) {
        Dialog(
            onDismissRequest = { showFullImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // 定义缩放和位移状态
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        // 核心手势监听
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale *= zoom
                            // 限制缩放范围在 1倍 到 5倍 之间
                            scale = scale.coerceIn(1f, 5f)

                            // 只有在放大状态下才允许平移
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero // 缩小回原样时重置位移
                            }
                        }
                    }
                    .clickable { showFullImage = false }, // 单击返回
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = "完整图片预览",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )

                if (scale == 1f) {
                    Text(
                        text = "双指缩放查看细节 · 点击返回",
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}