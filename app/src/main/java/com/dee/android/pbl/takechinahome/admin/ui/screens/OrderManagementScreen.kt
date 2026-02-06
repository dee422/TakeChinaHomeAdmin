package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
    orders: List<Order>,
    managerId: Int,
    onRefresh: (Int) -> Unit,
    // ✨ 关键修改：参数由 Int 改为 Order，变量名保持不变
    onConfirmIntent: (Order) -> Unit,
    onCompleteOrder: (Int) -> Unit
) {
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
                            // ✨ 关键修改：直接传递整个 order 对象
                            onConfirm = { onConfirmIntent(order) },
                            onComplete = { onCompleteOrder(order.id) },
                            onChatClick = { selectedOrder ->
                                activeChatOrder = selectedOrder
                                showChatSheet = true
                            }
                        )
                    }
                }
            }
        }

        if (showChatSheet && activeChatOrder != null) {
            ModalBottomSheet(
                onDismissRequest = { showChatSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                ChatBottomSheetContent(
                    order = activeChatOrder!!,
                    onDismiss = { showChatSheet = false },
                    onDataChanged = { onRefresh(managerId) }
                )
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

            Text("拟选清单:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
            order.details.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("• ${item.name}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("x${item.qty}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

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
                            Text("意向核对助手", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            order.aiSuggestion ?: "点击完善意向信息...",
                            fontSize = 12.sp, maxLines = 2, color = Color.DarkGray
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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

// ... 剩余代码（ChatBottomSheetContent 等）保持不变 ...
@Composable
fun ChatBottomSheetContent(
    order: Order,
    onDismiss: () -> Unit,
    onDataChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val refItem = order.details.firstOrNull()
    val refName = refItem?.name ?: ""
    val refQty = refItem?.qty ?: 0

    var giftName by remember {
        mutableStateOf(if (order.targetGiftName == "待定" || order.targetGiftName.isNullOrEmpty()) refName else order.targetGiftName)
    }
    var qty by remember {
        mutableStateOf(if (order.targetQty == 0) refQty.toString() else order.targetQty.toString())
    }
    var date by remember { mutableStateOf(order.deliveryDate ?: "待定") }
    var contact by remember { mutableStateOf(order.contactMethod ?: "待定") }

    var aiReminder by remember { mutableStateOf(order.aiSuggestion ?: "正在分析采集进度...") }
    var isSaving by remember { mutableStateOf(false) }
    var isAiLoading by remember { mutableStateOf(false) }

    val isLocked = order.intentConfirmStatus == 1

    LaunchedEffect(order.id) {
        if (order.aiSuggestion == null || order.aiSuggestion == "待定") {
            isAiLoading = true
            try {
                val response = RetrofitClient.adminService.getAiSuggestion(order.id)
                if (response.success) aiReminder = response.data ?: ""
            } finally {
                isAiLoading = false
            }
        }
    }

    Column(modifier = Modifier
        .padding(16.dp)
        .padding(bottom = 32.dp)
        .verticalScroll(rememberScrollState())
    ) {
        Text("意向卷宗采集", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isAiLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(text = aiReminder, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))

        IntentField("意向礼品名称", giftName, isLocked) { giftName = it }
        IntentField("意向数量", qty, isLocked) { qty = it }
        IntentField("期望交货时间", date, isLocked) { date = it }
        IntentField("联系方式及时间", contact, isLocked) { contact = it }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isSaving = true
                    try {
                        // ✨ 核心修正：将原始数据包装为 RequestBody
                        val textType = "text/plain".toMediaTypeOrNull()

                        val orderIdPart = order.id.toString().toRequestBody(textType)
                        val giftNamePart = giftName.toRequestBody(textType)
                        val qtyPart = qty.toRequestBody(textType)
                        val datePart = date.toRequestBody(textType)
                        val contactPart = contact.toRequestBody(textType)
                        val statusPart = "1".toRequestBody(textType)

                        // 调用接口，注意：最后一个参数 formalImage 传 null
                        // 因为在这个弹窗里我们只是锁定文字信息，不触发截图上传
                        val res = RetrofitClient.adminService.updateOrderIntent(
                            orderId = orderIdPart,
                            giftName = giftNamePart,
                            qty = qtyPart,
                            date = datePart,
                            contact = contactPart,
                            status = statusPart,
                            formalImage = null
                        )

                        if (res.success) {
                            Toast.makeText(context, "意向单已生成并锁定", Toast.LENGTH_SHORT).show()
                            onDataChanged()
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuditFlow", "Save Error: ${e.message}")
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isSaving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLocked && !isSaving,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Text(if (isLocked) "意向单已锁定（经理跟进中）" else "确认信息并生成意向单")
            }
        }

        if (!isLocked) {
            Text(
                "注：锁定后信息将同步给经理，且不可在客户端修改。",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun IntentField(label: String, value: String, isLocked: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        enabled = !isLocked,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
            disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
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