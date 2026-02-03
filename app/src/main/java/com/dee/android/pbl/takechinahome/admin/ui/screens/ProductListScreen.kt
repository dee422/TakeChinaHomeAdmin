package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.launch

// --- 数据模型 ---
data class AdminGift(
    val id: Int,
    val name: String,
    val spec: String,
    val desc: String,
    val deadline: String,
    val images: List<String>
)

data class AiEngine(val id: String, val name: String, val isOverseas: Boolean)

val aiEngines = listOf(
    AiEngine("zhipu", "智谱 GLM-4", false),
    AiEngine("deepseek", "DeepSeek-V3", false),
    AiEngine("gemini", "Gemini 1.5", true),
    AiEngine("qwen", "通义千问", false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(refreshSignal: Long = 0L) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var productList by remember { mutableStateOf<List<AdminGift>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val loadData = {
        scope.launch {
            isLoading = true
            try {
                // 1. 获取包装后的响应
                val response = RetrofitClient.adminService.getGifts()
                // 2. 只有成功且 data 不为空时才赋值
                if (response.success) {
                    productList = response.data ?: emptyList()
                } else {
                    Toast.makeText(context, response.message ?: "加载失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(refreshSignal) { loadData() }
    LaunchedEffect(Unit) { loadData() }

    var selectedProduct by remember { mutableStateOf<AdminGift?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<AdminGift?>(null) }

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

        if (productToDelete != null) {
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
                title = { Text("确认移除？") },
                text = { Text("将删除 [${productToDelete?.name}]。") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val response = RetrofitClient.adminService.deleteGift(productToDelete!!.id)
                                    if (response.success) {
                                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                        loadData()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                                }
                                productToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("确定", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { productToDelete = null }) { Text("取消") } }
            )
        }
    }

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
                            loadData()
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(product.name) }
    var deadline by remember { mutableStateOf(product.deadline) }
    var spec by remember { mutableStateOf(product.spec) }
    var desc by remember { mutableStateOf(product.desc) }

    var currentEngine by remember { mutableStateOf(aiEngines[0]) }
    var showEngineSettings by remember { mutableStateOf(false) }
    var apiKeyMap by remember {
        mutableStateOf(aiEngines.associate { it.id to (prefs.getString("key_${it.id}", "") ?: "") })
    }

    var isAiRefining by remember { mutableStateOf(false) }
    var zoomImageUrl by remember { mutableStateOf<String?>(null) }

    // --- 样本管理逻辑 ---
    val defaultSamples = listOf(
        "通用" to "釉色温润，包浆自然，器型规整，尽显古朴大方之气。",
        "青瓷" to "釉面滋润如玉，青中泛翠，色泽清冽。其开片纹理自然，犹如冰裂错落有致。",
        "紫砂" to "泥质纯正，砂粒隐现。壶身线条饱满，口盖严丝合缝，出水顺畅如柱。",
        "禅茶" to "意境深远，器物与空间相映成趣。无冗余修饰，唯余质朴本真，屏息凝神。"
    )

    var sampleList by remember { mutableStateOf(defaultSamples) }
    var selectedSampleIndex by remember { mutableIntStateOf(0) }
    var showSampleManager by remember { mutableStateOf(false) }
    var sampleText by remember { mutableStateOf(sampleList[0].second) }

    // 用于新增样本的临时状态
    var showAddSampleDialog by remember { mutableStateOf(false) }
    var newSampleTitle by remember { mutableStateOf("") }
    var newSampleContent by remember { mutableStateOf("") }

    // --- 1. 样本管理列表弹窗 ---
    if (showSampleManager) {
        AlertDialog(
            onDismissRequest = { showSampleManager = false },
            title = { Text("选择学习样本库") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    sampleList.forEachIndexed { index, pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedSampleIndex = index }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (selectedSampleIndex == index), onClick = { selectedSampleIndex = index })
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(pair.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(pair.second, maxLines = 1, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    TextButton(onClick = { showAddSampleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("添加自定义类别样本")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    sampleText = sampleList[selectedSampleIndex].second
                    showSampleManager = false
                    Toast.makeText(context, "已切换至：${sampleList[selectedSampleIndex].first}", Toast.LENGTH_SHORT).show()
                }) { Text("使用此风格") }
            },
            dismissButton = { TextButton(onClick = { showSampleManager = false }) { Text("取消") } }
        )
    }

    // --- 2. 添加新样本的输入弹窗 ---
    if (showAddSampleDialog) {
        AlertDialog(
            onDismissRequest = { showAddSampleDialog = false },
            title = { Text("新增风格样本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newSampleTitle, onValueChange = { newSampleTitle = it }, label = { Text("风格名称 (如: 木作)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newSampleContent, onValueChange = { newSampleContent = it }, label = { Text("文案范例") }, modifier = Modifier.fillMaxWidth().height(120.dp))
                }
            },
            confirmButton = {
                Button(enabled = newSampleTitle.isNotBlank() && newSampleContent.isNotBlank(), onClick = {
                    sampleList = sampleList + (newSampleTitle to newSampleContent)
                    newSampleTitle = ""
                    newSampleContent = ""
                    showAddSampleDialog = false
                }) { Text("确认添加") }
            },
            dismissButton = { TextButton(onClick = { showAddSampleDialog = false }) { Text("取消") } }
        )
    }

    // --- 主编辑对话框 ---
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("编辑卷宗", fontSize = 18.sp)
                IconButton(onClick = { showEngineSettings = true }) {
                    Icon(Icons.Default.SettingsSuggest, contentDescription = "设置", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("原件影像 (点击放大)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(product.images) { url ->
                        AsyncImage(
                            model = url, contentDescription = null,
                            modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray).clickable { zoomImageUrl = url },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("日期") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("规格") }, modifier = Modifier.fillMaxWidth())

                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("描述 ", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("风格: ${sampleList[selectedSampleIndex].first}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        OutlinedTextField(
                            value = desc, onValueChange = { desc = it },
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                        // 操作按钮组
                        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // ✨ 点击这里打开样本管理器
                            SmallFloatingActionButton(
                                onClick = { showSampleManager = true },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.LibraryBooks, "选择样本", modifier = Modifier.size(18.dp))
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    val userKey = apiKeyMap[currentEngine.id]
                                    if (userKey.isNullOrBlank()) {
                                        Toast.makeText(context, "请先设置 Key", Toast.LENGTH_SHORT).show()
                                    } else {
                                        scope.launch {
                                            isAiRefining = true
                                            try {
                                                val response = RetrofitClient.adminService.refineText(currentEngine.id, userKey, desc, sampleText)
                                                desc = response.refinedText ?: desc
                                                Toast.makeText(context, "润色成功", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                                            } finally { isAiRefining = false }
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary, shape = CircleShape
                            ) {
                                if (isAiRefining) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Default.AutoAwesome, "生成", modifier = Modifier.size(18.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(product.copy(name = name, deadline = deadline, spec = spec, desc = desc)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    // --- API 设置弹窗 ---
    if (showEngineSettings) {
        AlertDialog(
            onDismissRequest = { showEngineSettings = false },
            title = { Text("API 密钥配置") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    aiEngines.forEach { engine ->
                        val isSelected = currentEngine == engine
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { currentEngine = engine },
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = isSelected, onClick = { currentEngine = engine })
                                    Text(engine.name, fontWeight = FontWeight.Bold)
                                }
                                OutlinedTextField(
                                    value = apiKeyMap[engine.id] ?: "",
                                    onValueChange = { newKey ->
                                        apiKeyMap = apiKeyMap.toMutableMap().apply { put(engine.id, newKey) }
                                        prefs.edit().putString("key_${engine.id}", newKey).apply()
                                    },
                                    label = { Text("API Key") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showEngineSettings = false }) { Text("完成") } }
        )
    }

    // --- 缩放层 ---
    if (zoomImageUrl != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { zoomImageUrl = null }) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black)
                    .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset += pan } },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = zoomImageUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp).graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun ProductItemRow(product: AdminGift, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "规格: ${product.spec}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray) }
    }
}