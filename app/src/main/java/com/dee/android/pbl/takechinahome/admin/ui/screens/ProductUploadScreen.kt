package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

// --- 图片处理工具函数 ---

fun uriToBase64(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val bytes = inputStream.readBytes()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
        options.inJustDecodeBounds = false

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    } ?: ""
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

// --- 主屏幕界面 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductUploadScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状态定义
    var name by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("2026-05-05") }
    var spec by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val capturedImageUris = remember { mutableStateListOf<Uri>() }
    var isUploading by remember { mutableStateOf(false) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    // 拍照 Launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) currentPhotoUri?.let { capturedImageUris.add(it) }
    }

    // 权限 Launcher
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val file = File(context.externalCacheDir, "gift_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            currentPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    // 相册 Launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        capturedImageUris.addAll(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("录入新产品", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        // 图片预览与添加区域
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 渲染已选图片
            items(capturedImageUris.size) { index ->
                Box {
                    Surface(
                        modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        AsyncImage(
                            model = capturedImageUris[index],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // 添加按钮
            item {
                Surface(
                    modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { showImageSourceDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, "添加图片")
                            Text("添加照片", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (showImageSourceDialog) {
            AlertDialog(
                onDismissRequest = { showImageSourceDialog = false },
                title = { Text("选择图片来源") },
                text = { Text("您可以直接拍摄实物或从相册选择已有的照片。") },
                confirmButton = {
                    TextButton(onClick = {
                        showImageSourceDialog = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            val file = File(context.externalCacheDir, "gift_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            currentPhotoUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text("现在拍照") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImageSourceDialog = false
                        galleryLauncher.launch("image/*")
                    }) { Text("相册选择") }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 输入表单
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("产品名称 (必填)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("截止日期") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("规格 (可稍后补全)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("描述 (可稍后补全)") }, modifier = Modifier.fillMaxWidth().height(120.dp))

        Spacer(modifier = Modifier.height(32.dp))

        // 发布按钮
        Button(
            onClick = {
                isUploading = true
                scope.launch {
                    try {
                        val base64List = capturedImageUris.map { uriToBase64(context, it) }
                        val imagesJson = Gson().toJson(base64List)
                        val response = RetrofitClient.adminService.uploadGift(name, deadline, spec, desc, imagesJson)

                        if (response.success) {
                            Toast.makeText(context, "发布成功！", Toast.LENGTH_SHORT).show()
                            // 清空状态以便下次录入
                            capturedImageUris.clear(); name = ""; spec = ""; desc = ""
                        } else {
                            Toast.makeText(context, "上传失败: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络错误，请检查服务器", Toast.LENGTH_SHORT).show()
                    } finally { isUploading = false }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            // 只要有名称和图片即可上架
            enabled = !isUploading && name.isNotEmpty() && capturedImageUris.isNotEmpty()
        ) {
            if (isUploading) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("确认发布")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 重置/取消按钮
        TextButton(
            onClick = {
                capturedImageUris.clear()
                name = ""
                spec = ""
                desc = ""
                Toast.makeText(context, "已清空当前内容", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isUploading
        ) {
            Icon(Icons.Default.DeleteSweep, null)
            Spacer(Modifier.width(8.dp))
            Text("放弃当前录入", color = MaterialTheme.colorScheme.error)
        }
    }
}