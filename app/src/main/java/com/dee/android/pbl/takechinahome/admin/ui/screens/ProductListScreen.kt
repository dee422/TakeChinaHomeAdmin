package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer

// 数据模型
data class AdminGift(
    val id: Int,
    val name: String,
    val spec: String,
    val desc: String,
    val deadline: String,
    val images: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(refreshSignal: Long = 0L) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var productList by remember { mutableStateOf<List<AdminGift>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 封装刷新函数
    val loadData = {
        scope.launch {
            isLoading = true
            try {
                productList = RetrofitClient.adminService.getGifts()
            } catch (e: Exception) {
                Toast.makeText(context, "同步失败", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // ✨ 核心修正：监听 refreshSignal 的变化
    // 每次点击 MainActivity 的刷新按钮，refreshSignal 改变，这里就会执行
    LaunchedEffect(refreshSignal) {
        loadData()
    }

    var selectedProduct by remember { mutableStateOf<AdminGift?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<AdminGift?>(null) }

    // 刷新函数
    val refreshData = {
        scope.launch {
            isLoading = true
            try {
                productList = RetrofitClient.adminService.getGifts()
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.width(48.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(productList) { product ->
                    ProductItemRow(
                        product = product,
                        onEdit = {
                            selectedProduct = product
                            showEditDialog = true
                        },
                        onDelete = { productToDelete = product }
                    )
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }

        // --- 1. 删除确认弹窗 ---
        if (productToDelete != null) {
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                icon = { // 确保 imageVector 是第一个参数，或者使用命名参数
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "待精修",
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp)
                    ) },
                title = { Text("确认下架该卷宗？") },
                text = { Text("产品 [${productToDelete?.name}] 将被永久从库中抹除，不可撤回。") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val response = RetrofitClient.adminService.deleteGift(productToDelete!!.id)
                                    if (response.success) {
                                        Toast.makeText(context, "已成功移除", Toast.LENGTH_SHORT).show()
                                        refreshData()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
                                }
                                productToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("确定移除", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { productToDelete = null }) { Text("留存") } }
            )
        }
    }

    // --- 2. 编辑弹窗 (集成图片展示 & AI 预留) ---
    if (showEditDialog && selectedProduct != null) {
        EditProductDialog(
            product = selectedProduct!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedProduct ->
                scope.launch {
                    try {
                        val response = RetrofitClient.adminService.updateGift(
                            updatedProduct.id, updatedProduct.name,
                            updatedProduct.deadline, updatedProduct.spec, updatedProduct.desc
                        )
                        if (response.success) {
                            Toast.makeText(context, "更新成功", Toast.LENGTH_SHORT).show()
                            showEditDialog = false
                            refreshData()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductDialog(
    product: AdminGift,
    onDismiss: () -> Unit,
    onSave: (AdminGift) -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var deadline by remember { mutableStateOf(product.deadline) }
    var spec by remember { mutableStateOf(product.spec) }
    var desc by remember { mutableStateOf(product.desc) }
    var isAiRefining by remember { mutableStateOf(false) }
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("精修卷宗", fontSize = 18.sp)
                // ✨ 晚上我们要在这里接入 API 设置按键
                IconButton(onClick = { /* TODO: 打开 API 设置弹窗 */ }) {
                    Icon(Icons.Default.SettingsSuggest, contentDescription = "API设置", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("原件影像 (点击放大)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(product.images) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray)
                                .clickable { zoomImageUrl = url },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") })
                OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("截止日期") })
                OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("规格") })

                Box {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("描述") },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                    FilledIconButton(
                        onClick = { /* 晚上接入 AI 逻辑 */ },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp),
                        enabled = !isAiRefining && desc.isNotEmpty()
                    ) {
                        if (isAiRefining) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(product.copy(name = name, deadline = deadline, spec = spec, desc = desc)) }) {
                Text("存入库房")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    // ✨ 大图预览层（含手势缩放）
    if (zoomImageUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { zoomImageUrl = null }
        ) {
            // 用于存储缩放和平移状态
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        // 监听手势
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f) // 限制缩放倍数 1x-5x
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = zoomImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        // 应用缩放和平移变换
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )

                // 顶部操作提示
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("双指缩放", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    IconButton(onClick = { zoomImageUrl = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ProductItemRow(product: AdminGift, onEdit: () -> Unit, onDelete: () -> Unit) {
    val isIncomplete = product.spec.isEmpty() || product.desc.isEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (isIncomplete) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
                    Text(" 待精修", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(text = "规格: ${product.spec}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        // 操作区
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray)
        }
    }
}