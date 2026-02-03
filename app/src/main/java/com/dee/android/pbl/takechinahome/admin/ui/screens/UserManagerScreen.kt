package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// ✨ 修正导入路径
import com.dee.android.pbl.takechinahome.admin.data.model.AdminUser
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ✨ 这里的类型直接用 AdminUser
    var userList by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refreshUsers() {
        scope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.adminService.getAdminUsers()
                if (response.success) {
                    userList = response.data as List<com.dee.android.pbl.takechinahome.admin.data.model.AdminUser>
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "错误: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshUsers() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加用户")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("后台用户管理", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("仅管理员可见，用于分配员工账号", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(userList) { user ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(user.name) },
                            supportingContent = { Text(user.email) },
                            leadingContent = {
                                Icon(
                                    imageVector = if (user.role == "admin") Icons.Default.Shield else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (user.role == "admin") MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            },
                            trailingContent = {
                                Badge(containerColor = if (user.role == "admin") Color(0xFFE3F2FD) else Color(0xFFF5F5F5)) {
                                    Text(if (user.role == "admin") "管理员" else "普通用户", color = Color.DarkGray)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUserDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { email, pwd, name, role ->
                scope.launch {
                    try {
                        val res = RetrofitClient.adminService.createAdminUser(email, pwd, name, role)
                        if (res.success) {
                            Toast.makeText(context, "创建成功", Toast.LENGTH_SHORT).show()
                            showAddDialog = false
                            refreshUsers()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
fun AddUserDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增后台用户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("登录邮箱") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("初始密码") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("姓名") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it })
                    Text("设为管理员权限")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(email, password, name, if (isAdmin) "admin" else "user") }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}