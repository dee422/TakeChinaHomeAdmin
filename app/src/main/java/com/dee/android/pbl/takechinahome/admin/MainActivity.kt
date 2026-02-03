package com.dee.android.pbl.takechinahome.admin

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dee.android.pbl.takechinahome.admin.data.model.AdminRole
import com.dee.android.pbl.takechinahome.admin.ui.screens.*
import com.dee.android.pbl.takechinahome.admin.ui.theme.TakeChinaHomeAdminTheme
import com.dee.android.pbl.takechinahome.admin.viewmodel.AuditViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TakeChinaHomeAdminTheme {
                AdminMainContainer()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMainContainer() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE) }

    // 1. 登录状态持久化逻辑
    var isLoggedIn by remember { mutableStateOf(prefs.getBoolean("is_logged_in", false)) }
    var userRole by remember {
        val savedRole = prefs.getString("user_role", AdminRole.USER.name) ?: AdminRole.USER.name
        mutableStateOf(AdminRole.valueOf(savedRole))
    }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "员工") ?: "员工") }

    // 2. 界面控制状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentScreen by remember { mutableStateOf("置换审核") }
    var refreshSignal by remember { mutableLongStateOf(0L) }

    // 获取 ViewModel
    val auditViewModel: AuditViewModel = viewModel()
    val pendingCount = auditViewModel.uiState.value.allItems.count { it.status == 1 }

    // --- 逻辑判断：未登录则显示登录页 ---
    if (!isLoggedIn) {
        AdminLoginScreen(onLoginSuccess = { user ->
            isLoggedIn = true
            userRole = user.role  // 确保 AdminUserInfo 里的 role 也是 AdminRole 类型
            userName = user.name
            currentScreen = "置换审核"
        })
        return
    }

    // --- 以下为已登录后的主界面逻辑 ---

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
                    Text(text = "雅鉴管理后台", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "欢迎您，$userName (${if (userRole == AdminRole.ADMIN) "管理员" else "经理"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                HorizontalDivider()

                // ✨ 权限过滤逻辑：定义所有菜单，然后根据 role 过滤
                val allMenuItems = listOf(
                    Triple("产品上架", Icons.Default.AddBox, "all"),
                    Triple("置换审核", Icons.Default.CheckCircle, "all"),
                    Triple("产品管理", Icons.Default.List, "all"),
                    Triple("礼品发布", Icons.Default.CardGiftcard, "all"),
                    Triple("订单管理", Icons.Default.Chat, "all"),
                    Triple("用户管理", Icons.Default.People, "admin"), // 仅 admin 可见
                    Triple("数据看板", Icons.Default.Assessment, "admin") // 仅 admin 可见
                )

                allMenuItems.forEach { (title, icon, requiredRole) ->
                    // 将 userRole.name (即 "ADMIN" 或 "USER") 与字符串比较
                    // 注意：AdminRole.ADMIN.name 通常是大写的 "ADMIN"，请确保逻辑一致
                    val hasPermission = requiredRole == "all" || userRole == AdminRole.ADMIN

                    if (hasPermission) {
                        NavigationDrawerItem(
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(title) },
                            selected = currentScreen == title,
                            onClick = {
                                currentScreen = title
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 退出登录按钮
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    label = { Text("退出登录") },
                    selected = false,
                    onClick = {
                        prefs.edit().clear().apply()
                        isLoggedIn = false
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(currentScreen, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            when (currentScreen) {
                                "置换审核" -> auditViewModel.fetchPendingItems()
                                "产品管理" -> refreshSignal = System.currentTimeMillis()
                                "用户管理" -> refreshSignal = System.currentTimeMillis() // 假设用户管理也支持刷新
                                else -> Toast.makeText(context, "$currentScreen 暂不支持刷新", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }

                        Box(modifier = Modifier.padding(end = 12.dp)) {
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) {
                                        Badge(containerColor = Color.Red, contentColor = Color.White) {
                                            Text(pendingCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = if (userRole == AdminRole.ADMIN) MaterialTheme.colorScheme.primaryContainer else Color.LightGray
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = "用户中心")
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                when (currentScreen) {
                    "置换审核" -> AuditDashboardScreen(viewModel = auditViewModel)
                    "产品上架" -> ProductUploadScreen()
                    "产品管理" -> ProductListScreen(refreshSignal = refreshSignal)
                    "礼品发布" -> GiftDevScreen(auditViewModel = auditViewModel)
                    "用户管理" -> if (userRole == AdminRole.ADMIN) UserManagerScreen() else PlaceholderScreen("权限不足")
                    "订单管理" -> PlaceholderScreen("订单管理 (开发中...)")
                    else -> PlaceholderScreen(currentScreen)
                }
            }
        }
    }
}

@Composable
fun ProductUploadPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
            Text("产品上架功能模块", color = Color.Gray)
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text = "$name 模块待开发", color = Color.Gray)
    }
}