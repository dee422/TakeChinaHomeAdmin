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
// 确保导入的是 model 包下的 AdminUser
import com.dee.android.pbl.takechinahome.admin.data.model.AdminUser
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userList by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    // 刷新用户列表
    fun refreshUsers() {
        scope.launch {
            isLoading = true
            try {
                val response = RetrofitClient.adminService.getAdminUsers()
                if (response.success) {
                    // response.data 已经是 List<AdminUser>，不需要 'as' 强制转换
                    userList = response.data ?: emptyList()
                } else {
                    Toast.makeText(context, "获取失败: ${response.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "加载错误: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshUsers() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("后台用户管理") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加用户")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("账号权限列表", style = MaterialTheme.typography.titleMedium, color = Color.Gray)

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(userList) { user ->
                    UserListItem(user)
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
                        // ✨ 修正点：这里的参数必须与 AdminApiService 定义的一致
                        // 如果你的 API 需要 5 个参数，请确保传入 token
                        val res = RetrofitClient.adminService.createAdminUser(
                            email = email,
                            password = pwd,
                            name = name,
                            role = role,
                            adminToken = "your_admin_token" // 替换为实际的 token 或从存储获取
                        )
                        if (res.success) {
                            Toast.makeText(context, "创建成功", Toast.LENGTH_SHORT).show()
                            showAddDialog = false
                            refreshUsers()
                        } else {
                            Toast.makeText(context, "失败: ${res.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
fun UserListItem(user: AdminUser) {
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
                Text(
                    text = if (user.role == "admin") "管理员" else "员工",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (user.role == "admin") MaterialTheme.colorScheme.primary else Color.Gray
                )
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
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("邮箱") })
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("姓名") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it })
                    Text("设为管理员")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(email, password, name, if (isAdmin) "admin" else "user") }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}