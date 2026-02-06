package com.dee.android.pbl.takechinahome.admin.ui.screens

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
import androidx.compose.ui.text.style.TextOverflow
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
    // 获取 Context 用于 Toast
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var showChatSheet by remember { mutableStateOf(false) }
    var activeChatOrder by remember { mutableStateOf<Order?>(null) }
    var orderToDelete by remember { mutableStateOf<Int?>(null) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    val tabs = listOf("待处理意向", "正式订单库")

    // 1. 删除逻辑封装 (修正变量名与上下文)
    val performDelete = { id: Int ->
        scope.launch {
            try {
                // 使用正确的参数名 managerId
                val res = RetrofitClient.adminService.deleteOrderManager(id, managerId)
                if (res.success) {
                    Toast.makeText(context, "卷宗已销毁", Toast.LENGTH_SHORT).show()
                    // 使用正确的刷新回调
                    onRefreshIntent(managerId)
                } else {
                    Toast.makeText(context, "错误: ${res.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Tab 切换触发刷新
    LaunchedEffect(selectedTabIndex, managerId) {
        if (selectedTabIndex == 0) {
            onRefreshIntent(managerId)
        } else {
            onRefreshFormal()
        }
    }

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

            val currentDisplayList = if (selectedTabIndex == 0) intentOrders else formalOrders

            if (currentDisplayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.TaskAlt, null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
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
                        // 根据 Tab 决定渲染哪种卡片
                        if (selectedTabIndex == 0) {
                            // 意向单 Tab 使用带删除功能的 IntentOrderCard
                            IntentOrderCard(
                                order = order,
                                onComplete = { onConfirmIntent(it) }, // 跳转生成正式单
                                onDelete = { orderToDelete = it }
                            )
                        } else {
                            // 正式单 Tab 使用普通 OrderCard
                            OrderCard(
                                order = order,
                                isFormalTab = true,
                                onConfirm = { },
                                onComplete = { onCompleteOrder(order.id) },
                                onChatClick = { /* 正式单通常不进入采集模式 */ }
                            )
                        }
                    }
                }
            }
        }

        // --- 对话框组件 ---

        // 1. 确认删除对话框
        if (orderToDelete != null) {
            AlertDialog(
                onDismissRequest = { orderToDelete = null },
                title = { Text("确认终止") },
                text = { Text("此操作将永久销毁该意向卷宗，是否继续？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            performDelete(orderToDelete!!)
                            orderToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) { Text("确认销毁") }
                },
                dismissButton = {
                    TextButton(onClick = { orderToDelete = null }) { Text("取消") }
                }
            )
        }

        // 2. 意向核对详情 底部弹窗
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
    // 统一处理状态判断（忽略大小写）
    val isCompleted = order.status.equals("COMPLETED", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .alpha(if (isCompleted && !isFormalTab) 0.6f else 1.0f),
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
                        text = if (isFormalTab) "正式卷宗: #${order.id}" else "采集意向: #${order.id}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = if (isFormalTab) Color(0xFF2E7D32) else Color.Unspecified
                    )
                    // 优化：显示真实姓名“斯嘉丽”，增加经办图标
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "负责人: ${order.managerName ?: "系统分配"}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                Badge(
                    containerColor = when {
                        isFormalTab -> Color(0xFF2E7D32)
                        else -> getStatusColor(order.status)
                    }
                ) {
                    Text(
                        text = if (isFormalTab) "已归档" else order.status,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp
                    )
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
                Text("客户: ${order.contactName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("联系方式: ${order.contactMethod ?: order.userEmail}", fontSize = 12.sp, color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 清单展示
            Text("清单明细:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
            if (order.details.orEmpty().isEmpty()) {
                // 正式库展示逻辑
                Column {
                    Text("• ${order.targetGiftName ?: "未指定礼品"} x${order.targetQty}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (isFormalTab && !order.deliveryDate.isNullOrEmpty()) {
                        Text(
                            text = "📅 预定交付: ${order.deliveryDate}",
                            fontSize = 12.sp,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                // 意向单展示逻辑
                order.details.orEmpty().forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text("• ${item.name}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("x${item.qty}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 意向核对助手 (仅在意向阶段显示)
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
                                text = order.aiSuggestion ?: "点击完善采集信息...",
                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.DarkGray
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                    }
                }
            }

            // 操作按钮
            if (!isCompleted || isFormalTab) {
                // 注意：正式库即使状态是 Completed 也可以显示“完成交付”来做最终结单，或者不显示
                val showButton = if (isFormalTab) order.status != "Delivered" else !isCompleted

                if (showButton) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (!isFormalTab) {
                            Button(
                                onClick = onConfirm,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.TaskAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("生成正式卷宗")
                            }
                        } else if (order.status != "Delivered") {
                            OutlinedButton(
                                onClick = onComplete,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF2E7D32))
                            ) {
                                Text("完成最终交付", color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntentOrderCard(
    order: Order,
    onComplete: (Order) -> Unit,
    onDelete: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            // --- 头部：订单 ID 与 客户名 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "意向卷宗 #${order.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "客户: ${order.contactName}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- 核心内容：礼品详情 ---
            Text(
                text = order.targetGiftName ?: "未知礼品",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 展示具体的商品规格/数量（解析自 details JSON）
            order.details.forEach { item ->
                Text(
                    text = "• ${item.name} x ${item.qty} ${item.spec ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // --- AI 客服功能块 (重新找回) ---
            if (!order.aiSuggestion.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFE3F2FD), // 淡蓝色背景
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "🤖 AI客服: ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2)
                        )
                        Text(
                            text = order.aiSuggestion!!,
                            fontSize = 12.sp,
                            color = Color(0xFF0D47A1),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // --- 客户留言/联系方式 ---
            if (!order.contactMethod.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "📞 联系方式: ${order.contactMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
            Spacer(Modifier.height(12.dp))

            // --- 操作按键区 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 终止按键
                TextButton(
                    onClick = { onDelete(order.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("终止意向")
                }

                Spacer(Modifier.width(12.dp))

                // 2. 转正按键
                Button(
                    onClick = { onComplete(order) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("转为正式卷宗")
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