package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import com.dee.android.pbl.takechinahome.admin.viewmodel.AuditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

// --- 引擎模型定义 ---
data class ImageAiEngine(val id: String, val name: String, val provider: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftDevScreen(auditViewModel: AuditViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE) }

    // --- 1. 状态变量 ---
    var posterTheme by remember { mutableStateOf("") }
    var imageSceneReq by remember { mutableStateOf("高级茶室，柔和自然光，留白空间") }
    var copyStyleReq by remember { mutableStateOf("温润、高级、富有禅意") }
    var noChinese by remember { mutableStateOf(true) }

    var generatedImageUrl by remember { mutableStateOf<String?>(null) }
    var generatedCopywriting by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // 官方与置换产品数据
    val officialGifts = remember { mutableStateListOf<AdminGift>() }
    var isLoadingOfficial by remember { mutableStateOf(false) }
    val approvedExchangeItems by remember {
        derivedStateOf { auditViewModel.uiState.value.allItems.filter { it.status == 2 } }
    }
    val selectedProductNames = remember { mutableStateListOf<String>() }
    var showProductPicker by remember { mutableStateOf(false) }

    // AI 设置
    val imageAiEngines = listOf(
        ImageAiEngine("zhipu", "智谱 CogView-4", "zhipu"),
        ImageAiEngine("openai", "DALL-E 3", "openai")
    )
    var currentImgEngine by remember {
        val savedId = prefs.getString("last_img_engine", "zhipu")
        mutableStateOf(imageAiEngines.find { it.id == savedId } ?: imageAiEngines[0])
    }
    var showImgSettings by remember { mutableStateOf(false) }
    var imgApiKeyMap by remember {
        mutableStateOf(imageAiEngines.associate { it.id to (prefs.getString("img_key_${it.id}", "") ?: "") })
    }

    // 初始化加载官方礼品
    LaunchedEffect(Unit) {
        isLoadingOfficial = true
        try {
            val data = RetrofitClient.adminService.getGifts()
            officialGifts.addAll(data)
        } catch (e: Exception) { e.printStackTrace() } finally { isLoadingOfficial = false }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AI 礼品助手 (场景化方案)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // --- 1. 关联产品区 ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("第一步：选择关联产品", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedProductNames.isEmpty()) Text("暂未选择产品", fontSize = 12.sp, color = Color.Gray)
                    selectedProductNames.forEach { name ->
                        InputChip(selected = true, onClick = { selectedProductNames.remove(name) }, label = { Text(name) })
                    }
                    AssistChip(onClick = { showProductPicker = true }, label = { Text("选择产品") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) })
                }
            }
        }

        // --- 2. 需求配置区 ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("第二步：场景与文案风格", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = posterTheme, onValueChange = { posterTheme = it }, label = { Text("海报主题 (如：春意伴手礼)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = imageSceneReq, onValueChange = { imageSceneReq = it }, label = { Text("期望背景描述 (不含产品)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = copyStyleReq, onValueChange = { copyStyleReq = it }, label = { Text("文案要求") }, modifier = Modifier.fillMaxWidth())

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = noChinese, onCheckedChange = { noChinese = it })
                    Text("禁止图片出现中文", fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showImgSettings = true }) { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        // --- 3. 生成操作 ---
        Button(
            onClick = {
                val key = imgApiKeyMap[currentImgEngine.id]
                if (key.isNullOrBlank()) { showImgSettings = true; return@Button }
                scope.launch {
                    isGenerating = true
                    try {
                        val productStr = selectedProductNames.joinToString(", ")
                        val imgPrompt = "A background for a gift poster. Theme: $posterTheme. Scene: $imageSceneReq. Cinematic lighting. DO NOT draw specific products."
                        val txtPrompt = "请为礼品主题【$posterTheme】创作营销文案。关联产品：$productStr。要求风格：$copyStyleReq。30字以内。"

                        // 并行异步请求
                        val imgTask = async { RetrofitClient.adminService.generateImage(currentImgEngine.provider, key, imgPrompt, noChinese) }
                        val txtTask = async { RetrofitClient.adminService.generateMarketingCopy(currentImgEngine.provider, key, txtPrompt) }

                        val imgRes = imgTask.await()
                        val txtRes = txtTask.await()

                        if (imgRes.success) generatedImageUrl = imgRes.image_url

                        // 文案生成策略：API失败则回退至本地模板
                        generatedCopywriting = if (txtRes.success && !txtRes.refined_text.isNullOrBlank()) {
                            txtRes.refined_text
                        } else {
                            "【$posterTheme】\n甄选 $productStr \n$copyStyleReq，传递东方温情。"
                        }

                    } catch (e: Exception) {
                        Toast.makeText(context, "生成失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    } finally { isGenerating = false }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !isGenerating && posterTheme.isNotBlank()
        ) {
            if (isGenerating) CircularProgressIndicator(Modifier.size(24.dp), Color.White) else Text("生成场景背景与营销文案")
        }

        // --- 4. 结果展示与下载 ---
        if (generatedImageUrl != null || generatedCopywriting.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = generatedCopywriting,
                    onValueChange = { generatedCopywriting = it },
                    label = { Text("AI 营销文案 (可手动编辑)") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(generatedCopywriting))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, null) }
                    }
                )

                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                    AsyncImage(model = generatedImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

                    if (generatedImageUrl != null) {
                        FilledIconButton(
                            onClick = {
                                scope.launch {
                                    isDownloading = true
                                    val success = saveImageToGallery(context, generatedImageUrl!!)
                                    if (success) Toast.makeText(context, "已保存至相册", Toast.LENGTH_SHORT).show()
                                    else Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                    isDownloading = false
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            enabled = !isDownloading
                        ) {
                            if (isDownloading) CircularProgressIndicator(Modifier.size(18.dp), Color.White)
                            else Icon(Icons.Default.Download, null)
                        }
                    }
                }
            }
        }
    }

    // 产品选择弹窗
    if (showProductPicker) {
        AlertDialog(
            onDismissRequest = { showProductPicker = false },
            title = { Text("选择上架产品") },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn {
                        item { Text("🎁 官方库", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp)) }
                        items(officialGifts) { gift ->
                            ListItem(headlineContent = { Text(gift.name) }, modifier = Modifier.clickable {
                                if (!selectedProductNames.contains(gift.name)) selectedProductNames.add(gift.name)
                                showProductPicker = false
                            })
                        }
                        item { Text("🔄 置换库", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp)) }
                        items(approvedExchangeItems) { item ->
                            ListItem(headlineContent = { Text(item.itemName) }, modifier = Modifier.clickable {
                                if (!selectedProductNames.contains(item.itemName)) selectedProductNames.add(item.itemName)
                                showProductPicker = false
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProductPicker = false }) { Text("取消") } }
        )
    }

    // 引擎设置弹窗
    if (showImgSettings) {
        AlertDialog(
            onDismissRequest = { showImgSettings = false },
            title = { Text("AI 密钥配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    imageAiEngines.forEach { engine ->
                        val isSelected = currentImgEngine.id == engine.id
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray, RoundedCornerShape(8.dp))
                                .clickable { currentImgEngine = engine }
                                .padding(8.dp)
                        ) {
                            Text(engine.name, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = imgApiKeyMap[engine.id] ?: "",
                                onValueChange = { newKey ->
                                    imgApiKeyMap = imgApiKeyMap.toMutableMap().apply { put(engine.id, newKey) }
                                    prefs.edit().putString("img_key_${engine.id}", newKey).apply()
                                },
                                label = { Text("API Key") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showImgSettings = false }) { Text("完成") } }
        )
    }
}

/**
 * 图像保存逻辑
 */
suspend fun saveImageToGallery(context: Context, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val inputStream = URL(imageUrl).openStream()
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val filename = "GiftPoster_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/TakeChinaHome")
            }
        }

        val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            context.contentResolver.openOutputStream(it).use { output ->
                if (output != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
                    return@withContext true
                }
            }
        }
        false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}