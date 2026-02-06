package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.widget.Toast
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
    // ✨ 修改：传入两个列表，分别对应两张表
    intentOrders: List<Order>,
    formalOrders: List<Order>,
    managerId: Int,
    onRefreshIntent: (Int) -> Unit,    // 刷新意向单 (orders表)
    onRefreshFormal: () -> Unit,      // 刷新正式单 (formal_orders表)
    onConfirmIntent: (Order) -> Unit,
    onCompleteOrder: (Int) -> Unit
) {
    var showChatSheet by remember { mutableStateOf(false) }
    var activeChatOrder by remember { mutableStateOf<Order?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("待处理意向", "正式订单库")

    // ✨ 核心逻辑：根据 Tab 自动触发对应的数据抓取
    LaunchedEffect(selectedTabIndex, managerId) {
        if (selectedTabIndex == 0) {
            onRefreshIntent(managerId)
        } else {
            onRefreshFormal()
        }
    }

    // ✨ 根据当前 Tab 选择显示的列表
    val currentDisplayList = if (selectedTabIndex == 0) intentOrders else formalOrders

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("卷宗管理 (订单)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tab 切换头
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }
            }

            if (currentDisplayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt, // 👈 显式指定参数名
                            contentDescription = null,           // 👈 显式指定第二个参数名
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("暂无相关卷宗", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentDisplayList, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            isFormalTab = selectedTabIndex == 1, // 告知卡片当前是否在正式库
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

        // 详情/核对 底部弹窗
        if (showChatSheet && activeChatOrder != null) {
            ModalBottomSheet(
                onDismissRequest = { showChatSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                ChatBottomSheetContent(
                    order = activeChatOrder!!,
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
    val isCompleted = order.status == "COMPLETED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isCompleted) 0.6f else 1.0f),
        colors = CardDefaults.cardColors(
            containerColor = if (isFormalTab) Color(0xFFF0F7F0) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (isFormalTab) 2.dp else 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部信息
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = if (isFormalTab) "正式单: #${order.id}" else "意向单: #${order.id}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = if (isFormalTab) Color(0xFF2E7D32) else Color.Unspecified
                    )
                    Text("经理: ${order.managerName ?: "System"}", fontSize = 11.sp, color = Color.Gray)
                }
                Badge(containerColor = getStatusColor(order.status)) {
                    Text(order.status, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 客户信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text("联系人: ${order.contactName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("邮箱: ${order.userEmail}", fontSize = 12.sp, color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 清单展示
            Text("清单明细:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
            // 如果 formal_orders 结构不同，这里可以做适配
            if (order.details.orEmpty().isEmpty()) {
                Text("• ${order.targetGiftName ?: "未指定"} x${order.targetQty}", fontSize = 13.sp)
            } else {
                order.details.orEmpty().forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text("• ${item.name}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("x${item.qty}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 意向核对助手 (仅在意向阶段或正式库查看详情时显示)
            if (!isFormalTab) {
                Spacer(modifier = Modifier.height(12.dp))
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
                                Text("意向核对详情", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                order.aiSuggestion ?: "点击完善采集信息...",
                                fontSize = 12.sp, maxLines = 1, color = Color.DarkGray
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                    }
                }
            }

            // 操作按钮
            if (!isCompleted) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!isFormalTab) {
                        // 在意向 Tab 显示转正按钮
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.TaskAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("生成正式卷宗")
                        }
                    } else {
                        // 在正式库显示交付按钮
                        OutlinedButton(
                            onClick = onComplete,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("完成交付")
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