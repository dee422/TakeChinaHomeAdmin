package com.dee.android.pbl.takechinahome.admin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift

@Composable
fun AuditItemCard(
    item: ExchangeGift,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // 1. 物物图片展示 (AsyncImage 默认支持 String?)
            AsyncImage(
                model = item.imageUrl,
                contentDescription = "物什图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )

            // 2. 信息详情区
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = item.itemName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ✨ 修复 1：使用 ?: 提供默认邮箱
                Text(
                    text = "申请人: ${item.ownerEmail ?: "未知用户"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ✨ 修复 2：使用 ?: 提供默认描述
                Text(
                    text = item.description ?: "该物什暂无描述信息。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 操作按钮区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status == 1) {
                        // 只有待审核才显示按钮
                        OutlinedButton(
                            onClick = onReject,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Text("驳回")
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("准入画卷", color = Color.White)
                        }
                    } else {
                        // 已经处理过的，显示最终状态
                        val statusText = if (item.status == 2) "已准入" else "已驳回"
                        val statusColor = if (item.status == 2) Color(0xFF4CAF50) else Color.Red

                        Text(
                            text = statusText,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        // 可选：加一个“重新审核”的文字链接，或者提示“等待用户重提”
                        Text("(已锁定)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}