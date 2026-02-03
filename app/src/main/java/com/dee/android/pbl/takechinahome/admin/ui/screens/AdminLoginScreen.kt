package com.dee.android.pbl.takechinahome.admin.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dee.android.pbl.takechinahome.admin.data.model.AdminUserInfo
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.launch

@Composable
fun AdminLoginScreen(onLoginSuccess: (AdminUserInfo) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("管理端登录", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("请输入管理员或员工账号", color = MaterialTheme.colorScheme.outline)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("邮箱地址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("登录密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            }
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) return@Button
                scope.launch {
                    isLoggingIn = true
                    try {
                        val response = RetrofitClient.adminService.login(email, password)
                        if (response.success && response.data != null) {
                            val user = response.data
                            // 保存登录状态
                            prefs.edit().apply {
                                putString("user_email", user.email)
                                putString("user_name", user.name)
                                putString("user_role", user.role.name)
                                putBoolean("is_logged_in", true)
                                apply()
                            }
                            onLoginSuccess(user)
                        } else {
                            Toast.makeText(context, response.message ?: "登录失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
                    } finally { isLoggingIn = false }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoggingIn
        ) {
            if (isLoggingIn) CircularProgressIndicator(Modifier.size(24.dp), Color.White) else Text("立即登录")
        }
    }
}