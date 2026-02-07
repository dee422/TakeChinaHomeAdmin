package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
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
    onCompleteOrder: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showChatSheet by remember { mutableStateOf(false) }
    var activeChatOrder by remember { mutableStateOf<Order?>(null) }
    var orderToDelete by remember { mutableStateOf<Order?>(null) }
    var orderToConfirm by remember { mutableStateOf<Order?>(null) }
    var isSynchronizing by remember { mutableStateOf(false) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("待处理意向", "正式订单库")

    val performDelete = { order: Order ->
        scope.launch {
            try {
                val res = RetrofitClient.adminService.deleteOrderManager(order.id, managerId)
                if (res.success) {
                    Toast.makeText(context, "卷宗已销毁", Toast.LENGTH_SHORT).show()
                    onRefreshIntent(managerId)
                } else {
                    Toast.makeText(context, "错误: ${res.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络异常", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val performConfirm = { order: Order ->
        Log.d("AuditFlow", "点击了确认转正按钮，OrderID: ${order.id}") // 添加这行
        scope.launch {
            try {
                isSynchronizing = true
                onConfirmIntent(order)
                kotlinx.coroutines.delay(1500)
                onRefreshIntent(managerId)
                onRefreshFormal()
                isSynchronizing = false
                Toast.makeText(context, "转正成功，请查看正式库", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("AuditFlow", "转正过程崩溃", e) // 添加这行
                isSynchronizing = false
                Toast.makeText(context, "同步异常", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 临时调试：看看列表里到底有没有东西
            val currentDisplayList = if (selectedTabIndex == 0) intentOrders else formalOrders
            Text("调试：当前列表长度 = ${currentDisplayList.size}", color = Color.Red)
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
                            isFormalTab = true,
                            onConfirm = {},
                            onComplete = { onCompleteOrder(order.id) },
                            onChatClick = {
                                activeChatOrder = order
                                showChatSheet = true
                            }
                        )
                    }
                }
            }
        }

        // 弹窗逻辑
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

        if (orderToDelete != null) {
            AlertDialog(
                onDismissRequest = { orderToDelete = null },
                title = { Text("确认终止") },
                text = { Text("此操作将永久销毁该卷宗，是否继续？") },
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

        if (showChatSheet && activeChatOrder != null) {
            ModalBottomSheet(onDismissRequest = { showChatSheet = false }) {
                ChatBottomSheetContent(
                    order = activeChatOrder!!,
                    managerId = managerId,
                    onDismiss = { showChatSheet = false },
                    onDataChanged = { onRefreshIntent(managerId) }
                )
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    isFormalTab: Boolean,
    onConfirm: () -> Unit,
    onComplete: () -> Unit,
    onChatClick: (Order) -> Unit
) {
    val isCompleted = order.status.equals("Completed", ignoreCase = true) ||
            order.status.equals("Delivered", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (isCompleted) 0.8f else 1.0f),
        colors = CardDefaults.cardColors(containerColor = if (isFormalTab) Color(0xFFF0F7F0) else Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "卷宗 #${order.id}", fontWeight = FontWeight.Bold)
                Badge(containerColor = if (isCompleted) Color.Gray else Color(0xFF1976D2)) {
                    Text(text = if (isCompleted) "已交付" else "研制中", color = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("客户: ${order.contactName}")
            Text("明细: ${order.targetGiftName} x${order.targetQty}")

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { onChatClick(order) }) { Text("沟通") }
                if (isFormalTab) {
                    Button(
                        onClick = onComplete,
                        enabled = !isCompleted,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text(if (isCompleted) "已归档" else "交付") }
                }
            }
        }
    }
}

@Composable
fun IntentOrderCard(order: Order, onComplete: (Order) -> Unit, onDelete: (Order) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("意向卷宗 #${order.id}", color = Color.Gray)
            Text(order.contactName, fontWeight = FontWeight.Bold)
            Text(order.targetGiftName ?: "未指定礼品", fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.End) {
                TextButton(onClick = { onDelete(order) }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("终止") }
                Button(onClick = { onComplete(order) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("转为正式") }
            }
        }
    }
}

@Composable
fun ChatBottomSheetContent(order: Order, managerId: Int, onDismiss: () -> Unit, onDataChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var giftName by remember { mutableStateOf(order.targetGiftName ?: "") }
    var qty by remember { mutableStateOf(order.targetQty.toString()) }
    var date by remember { mutableStateOf(order.deliveryDate ?: "") }
    var contact by remember { mutableStateOf(order.contactMethod ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("更新意向信息", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        IntentField("礼品名称", giftName, false) { giftName = it }
        IntentField("数量", qty, false) { qty = it }
        IntentField("日期", date, false) { date = it }
        IntentField("联系方式", contact, false) { contact = it }

        Button(
            onClick = {
                scope.launch {
                    isSaving = true
                    try {
                        val textType = "text/plain".toMediaTypeOrNull()
                        val res = RetrofitClient.adminService.updateOrderIntent(
                            orderId = order.id.toString().toRequestBody(textType),
                            managerId = managerId.toString().toRequestBody(textType),
                            managerName = "斯嘉丽".toRequestBody(textType),
                            giftName = giftName.toRequestBody(textType),
                            qty = qty.toRequestBody(textType),
                            date = date.toRequestBody(textType),
                            contact = contact.toRequestBody(textType),
                            status = "1".toRequestBody(textType),
                            formalImage = null
                        )
                        if (res.success) {
                            Toast.makeText(context, "更新成功", Toast.LENGTH_SHORT).show()
                            onDataChanged()
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "错误: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally { isSaving = false }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text("确认并同步")
        }
    }
}

@Composable
fun IntentField(label: String, value: String, isLocked: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        enabled = !isLocked,
        singleLine = true
    )
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