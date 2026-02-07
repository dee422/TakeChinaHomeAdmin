package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dee.android.pbl.takechinahome.admin.data.model.Order
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderManagementScreen(
    intentOrders: List<Order>,
    formalOrders: List<Order>,
    managerId: Int,
    onRefreshIntent: (Int) -> Unit,
    onRefreshFormal: () -> Unit,
    onConfirmIntent: (Order) -> Unit,
    onCompleteOrder: (Int) -> Unit,
    onTerminateOrder: (Int) -> Unit // 确认这里有这个参数
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var orderToDelete by remember { mutableStateOf<Order?>(null) }
    var orderToConfirm by remember { mutableStateOf<Order?>(null) }
    var formalOrderToComplete by remember { mutableStateOf<Order?>(null) } // ✨ 新增：交付确认
    var formalOrderToTerminate by remember { mutableStateOf<Order?>(null) } // ✨ 新增：终止确认

    var isSynchronizing by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("待处理意向", "正式订单库")

    // 意向单终止（物理删除）
    val performDelete = { order: Order ->
        scope.launch {
            try {
                val res = RetrofitClient.adminService.deleteOrderManager(order.id, managerId)
                if (res.success) {
                    Toast.makeText(context, "意向已销毁", Toast.LENGTH_SHORT).show()
                    onRefreshIntent(managerId)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络异常", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 意向单转正逻辑
    val performConfirm = { order: Order ->
        scope.launch {
            try {
                isSynchronizing = true
                onConfirmIntent(order)
                kotlinx.coroutines.delay(1500)
                onRefreshIntent(managerId)
                onRefreshFormal()
                isSynchronizing = false
            } catch (e: Exception) {
                isSynchronizing = false
                Toast.makeText(context, "同步异常", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSynchronizing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFFE65100))
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            val currentDisplayList = if (selectedTabIndex == 0) intentOrders else formalOrders

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentDisplayList, key = { it.id }) { order ->
                    if (selectedTabIndex == 0) {
                        IntentOrderCard(
                            order = order,
                            onComplete = { orderToConfirm = it },
                            onDelete = { orderToDelete = order }
                        )
                    } else {
                        OrderCard(
                            order = order,
                            onTerminate = { formalOrderToTerminate = order }, // ✨ 连向终止弹窗
                            onComplete = { formalOrderToComplete = order }    // ✨ 连向交付弹窗
                        )
                    }
                }
            }
        }

        // --- 弹窗逻辑组 ---

        // 1. 意向转正确认
        if (orderToConfirm != null) {
            AlertDialog(
                onDismissRequest = { orderToConfirm = null },
                title = { Text("确认转正") },
                text = { Text("确定要将客户【${orderToConfirm!!.contactName}】的意向转为正式卷宗吗？") },
                confirmButton = {
                    Button(onClick = {
                        val target = orderToConfirm!!
                        orderToConfirm = null
                        performConfirm(target)
                    }) { Text("确认") }
                },
                dismissButton = { TextButton(onClick = { orderToConfirm = null }) { Text("取消") } }
            )
        }

        // 2. 正式订单交付确认
        if (formalOrderToComplete != null) {
            AlertDialog(
                onDismissRequest = { formalOrderToComplete = null },
                title = { Text("确认交付") },
                text = { Text("确认该卷宗已完成交付并归档？") },
                confirmButton = {
                    Button(onClick = {
                        onCompleteOrder(formalOrderToComplete!!.id)
                        formalOrderToComplete = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("确认交付") }
                },
                dismissButton = { TextButton(onClick = { formalOrderToComplete = null }) { Text("取消") } }
            )
        }

        // 3. 正式订单终止确认 (状态改为 Terminated)
        if (formalOrderToTerminate != null) {
            AlertDialog(
                onDismissRequest = { formalOrderToTerminate = null },
                title = { Text("终止订单", color = Color.Red) },
                text = { Text("确定要终止该正式卷宗吗？此操作不可撤销。") },
                confirmButton = {
                    Button(onClick = {
                        onTerminateOrder(formalOrderToTerminate!!.id)
                        formalOrderToTerminate = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("确认终止") }
                },
                dismissButton = { TextButton(onClick = { formalOrderToTerminate = null }) { Text("取消") } }
            )
        }

        // 4. 意向单销毁确认 (物理删除)
        if (orderToDelete != null) {
            AlertDialog(
                onDismissRequest = { orderToDelete = null },
                title = { Text("确认终止意向") },
                text = { Text("此操作将永久销毁该意向卷宗，是否继续？") },
                confirmButton = {
                    TextButton(onClick = {
                        val target = orderToDelete!!
                        orderToDelete = null
                        performDelete(target)
                    }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("确认销毁") }
                },
                dismissButton = { TextButton(onClick = { orderToDelete = null }) { Text("取消") } }
            )
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onTerminate: () -> Unit,
    onComplete: () -> Unit
) {
    // 终态判断逻辑：只有 Completed 和 Terminated 会隐藏按钮
    val isFinished = order.status.equals("Completed", ignoreCase = true) ||
            order.status.equals("Terminated", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isFinished) 0.8f else 1.0f),
        colors = CardDefaults.cardColors(
            containerColor = when {
                order.status.equals("Terminated", true) -> Color(0xFFFFF1F0) // 终止用浅红背景
                isFinished -> Color(0xFFF8F8F8) // 已交付用浅灰背景
                else -> Color(0xFFF0F7F0) // 研制中用浅绿背景
            }
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "正式卷宗 #${order.id}", fontWeight = FontWeight.Bold)

                // 使用 getStatusColor 渲染徽章
                Badge(containerColor = getStatusColor(order.status)) {
                    Text(
                        text = when {
                            order.status.equals("Completed", true) -> "已交付"
                            order.status.equals("Terminated", true) -> "已终止"
                            else -> "研制中"
                        },
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("客户: ${order.contactName}")
            Text("明细: ${order.targetGiftName} x${order.targetQty}")

            // 只有 CONFIRMED 状态（非终态）才显示操作按钮
            if (!isFinished) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTerminate,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.Red),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("终止")
                    }

                    Button(
                        onClick = onComplete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("完成交付")
                    }
                }
            }
        }
    }
}

@Composable
fun IntentOrderCard(order: Order, onComplete: (Order) -> Unit, onDelete: (Order) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "意向卷宗 #${order.id}",
                    color = getStatusColor("INTENT"), // 使用紫色标识意向
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(order.contactName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(order.targetGiftName ?: "未指定礼品", fontSize = 14.sp, color = Color.DarkGray)

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.End) {
                TextButton(
                    onClick = { onDelete(order) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("终止意向")
                }

                Button(
                    onClick = { onComplete(order) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("转为正式")
                }
            }
        }
    }
}

fun getStatusColor(status: String): Color {
    // 使用 uppercase() 确保无论后端传 CONFIRMED 还是 Confirmed 都能匹配
    return when (status.uppercase()) {
        "INTENT" -> Color(0xFF9C27B0)    // 紫色
        "PENDING" -> Color(0xFFE91E63)   // 粉色
        "CONFIRMED" -> Color(0xFF1976D2) // 蓝色 (研制中)
        "COMPLETED" -> Color(0xFF2E7D32) // 绿色 (已交付)
        "TERMINATED" -> Color(0xFFD32F2F)// 红色 (已终止)
        else -> Color.Gray
    }
}