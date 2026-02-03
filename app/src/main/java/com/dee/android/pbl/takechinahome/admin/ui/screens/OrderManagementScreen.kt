package com.dee.android.pbl.takechinahome.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dee.android.pbl.takechinahome.admin.data.model.Order

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderManagementScreen(
    orders: List<Order>,
    managerId: Int, // ✨ 新增参数：需要传入 managerId
    onRefresh: (Int) -> Unit, // ✨ 新增参数：传入刷新回调
    onConfirmIntent: (Int) -> Unit,
    onCompleteOrder: (Int) -> Unit
) {
    // 🔥 这就是“保险丝”：每当进入此页面，或 managerId 发生变化，立即触发刷新
    LaunchedEffect(managerId) {
        if (managerId != 0) {
            onRefresh(managerId)
        }
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("待处理意向", "正式订单库")

    val filteredOrders = if (selectedTabIndex == 0) {
        orders.filter { it.isIntent == 1 }
    } else {
        orders.filter { it.isIntent == 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("卷宗管理 (订单)") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            if (filteredOrders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无相关卷宗", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(filteredOrders) { order ->
                        OrderCard(
                            order = order,
                            onConfirm = { onConfirmIntent(order.id) },
                            onComplete = { onCompleteOrder(order.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onConfirm: () -> Unit,
    onComplete: () -> Unit
) {
    val isCompleted = order.status == "COMPLETED"
    val cardAlpha = if (isCompleted) 0.5f else 1.0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .alpha(cardAlpha),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 顶部：单号与状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("单号: #${order.id}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Badge(containerColor = getStatusColor(order.status)) {
                    Text(order.status, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 用户信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                Text(order.userEmail, fontSize = 13.sp, color = Color.DarkGray)
            }
            Text("联系人: ${order.contactName}", fontSize = 14.sp, fontWeight = FontWeight.Medium)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

            // ✨ 重点：展示选中的礼品列表 (details 现在是 List)
            Text("拟选礼品:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))

            order.details.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(text = "x${item.qty}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI 建议部分
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("AI 评估建议:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        order.aiSuggestion ?: "AI 正在分析该用户的置换价值...",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮区
            if (!isCompleted) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (order.isIntent == 1) {
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("确认为正式订单", fontSize = 13.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onComplete,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("标记已完成", fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Text(
                    "—— 该卷宗已归档，不可编辑 ——",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

fun getStatusColor(status: String): Color {
    return when (status) {
        "INTENT" -> Color(0xFF9C27B0)
        "PENDING" -> Color(0xFFE91E63)
        "CONFIRMED" -> Color(0xFF2196F3)
        "COMPLETED" -> Color(0xFF757575)
        else -> Color.Black
    }
}