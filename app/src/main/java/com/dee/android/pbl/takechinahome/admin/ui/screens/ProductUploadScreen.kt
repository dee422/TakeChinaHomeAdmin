package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

// 缩放函数：防止图片太大导致上传失败或闪退
fun uriToBase64(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val bytes = inputStream.readBytes()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        // 将 1600 降为 1024，大幅减少数据量
        options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
        options.inJustDecodeBounds = false

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val outputStream = ByteArrayOutputStream()
        // 质量从 70 降到 50
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductUploadScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("2026-05-05") }
    var spec by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val capturedImageUris = remember { mutableStateListOf<Uri>() }
    var isUploading by remember { mutableStateOf(false) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) currentPhotoUri?.let { capturedImageUris.add(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val file = File(context.externalCacheDir, "gift_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            currentPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "需要权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    // 相册选择启动器：允许选择所有 image/* 类型的文件
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        // 将选中的多张图片添加到列表
        capturedImageUris.addAll(uris)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("录入新产品", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                // 定义一个显示弹窗的状态
                var showImageSourceDialog by remember { mutableStateOf(false) }

                // 在原有的“+”号 Surface 点击处修改逻辑
                Surface(
                    modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { showImageSourceDialog = true } // 点击弹出选择框
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddAPhoto, "添加图片")
                    }
                }

                // 简单的选择弹窗
                if (showImageSourceDialog) {
                    AlertDialog(
                        onDismissRequest = { showImageSourceDialog = false },
                        title = { Text("选择图片来源") },
                        confirmButton = {
                            TextButton(onClick = {
                                showImageSourceDialog = false
                                // 检查拍照权限逻辑（沿用你昨天的逻辑）
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED) {
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
                                galleryLauncher.launch("image/*") // 打开相册
                            }) { Text("从相册选择") }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("产品名称") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("截止日期") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("规格") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth().height(120.dp))
        Spacer(modifier = Modifier.height(32.dp))

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
                            capturedImageUris.clear(); name = ""; spec = ""; desc = ""
                        } else {
                            Toast.makeText(context, "上传失败: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    } finally { isUploading = false }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isUploading && name.isNotEmpty() && capturedImageUris.isNotEmpty()
        ) {
            if (isUploading) CircularProgressIndicator(Modifier.size(24.dp), Color.White)
            else Row { Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("确认发布") }
        }
    }
}