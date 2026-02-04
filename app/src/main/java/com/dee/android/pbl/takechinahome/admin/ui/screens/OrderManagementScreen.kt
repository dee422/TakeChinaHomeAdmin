package com.dee.android.pbl.takechinahome.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    managerId: Int,
    onRefresh: (Int) -> Unit,
    onConfirmIntent: (Int) -> Unit,
    onCompleteOrder: (Int) -> Unit
) {
    // 1. 弹窗控制状态
    var showChatSheet by remember { mutableStateOf(false) }
    var activeChatOrder by remember { mutableStateOf<Order?>(null) }
    val sheetState = rememberModalBottomSheetState()

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
                            onComplete = { onCompleteOrder(order.id) },
                            onChatClick = { clickedOrder ->
                                // ✨ 点击时：保存当前选中的订单并弹出底栏
                                activeChatOrder = clickedOrder
                                showChatSheet = true
                            }
                        )
                    }
                }
            }
        }

        // 2. ✨ 底部对话弹窗 (ModalBottomSheet)
        if (showChatSheet && activeChatOrder != null) {
            ModalBottomSheet(
                onDismissRequest = { showChatSheet = false },
                sheetState = sheetState
            ) {
                ChatBottomSheetContent(activeChatOrder!!)
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onConfirm: () -> Unit,
    onComplete: () -> Unit,
    onChatClick: (Order) -> Unit
) {
    val isCompleted = order.status == "COMPLETED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .alpha(if (isCompleted) 0.6f else 1.0f),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) Color(0xFFF5F5F5) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (isCompleted) 1.dp else 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部：单号与状态
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("单号: #${order.id}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("跟进经理: ${order.managerName ?: "未分配"}", fontSize = 11.sp, color = Color.Gray)
                }
                Badge(containerColor = getStatusColor(order.status)) {
                    Text(order.status, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 客户信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text("客户: ${order.contactName}", fontWeight = FontWeight.Bold)
                Text(order.userEmail, fontSize = 12.sp, color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 礼品详情
            Text("拟选清单:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
            order.details.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("• ${item.name}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("x${item.qty}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

            // ✨ AI 建议区（点击进入对话）
            Surface(
                onClick = { onChatClick(order) },
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("AI 话术建议", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            order.aiSuggestion ?: "点击分析该客户的潜在置换价值...",
                            fontSize = 12.sp, maxLines = 2, color = Color.DarkGray
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 按钮操作区
            if (!isCompleted) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (order.isIntent == 1) {
                        Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                            Text("转为正式订单")
                        }
                    } else {
                        OutlinedButton(onClick = onComplete) {
                            Text("标记已交付")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBottomSheetContent(order: Order) {
    Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
        Text("AI 沟通助手", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        Text("针对订单 #${order.id} 的建议：", color = Color.Gray, fontSize = 12.sp)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Text(
                text = order.aiSuggestion ?: "正在深度分析客户偏好...",
                modifier = Modifier.padding(16.dp),
                lineHeight = 22.sp
            )
        }

        Button(onClick = { /* TODO: 复制话术 */ }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("复制 AI 建议话术")
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