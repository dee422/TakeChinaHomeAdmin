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
import com.dee.android.pbl.takechinahome.admin.viewmodel.OrderViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

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

    // --- 状态变量 (由 remember 驱动，确保实时刷新) ---
    var isLoggedIn by remember { mutableStateOf(prefs.getBoolean("is_logged_in", false)) }
    var currentManagerId by remember { mutableIntStateOf(prefs.getInt("user_id", 0)) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "员工") ?: "员工") }
    var userRole by remember {
        val savedRole = prefs.getString("user_role", AdminRole.USER.name) ?: AdminRole.USER.name
        mutableStateOf(try { AdminRole.valueOf(savedRole) } catch(e: Exception) { AdminRole.USER })
    }

    // 界面控制
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentScreen by remember { mutableStateOf("置换审核") }
    var refreshSignal by remember { mutableLongStateOf(0L) }

    // ViewModels
    val auditViewModel: AuditViewModel = viewModel()
    val orderViewModel: OrderViewModel = viewModel()
    val orders by orderViewModel.orders.collectAsState()
    val pendingCount = auditViewModel.uiState.value.allItems.count { it.status == 1 }

    // 1. 登录拦截逻辑
    if (!isLoggedIn) {
        AdminLoginScreen(onLoginSuccess = { user ->
            // ✨ 核心修正：立即同步内存状态
            currentManagerId = user.id
            userName = user.name
            userRole = user.role
            isLoggedIn = true

            // ✨ 同步持久化存储
            prefs.edit().apply {
                putBoolean("is_logged_in", true)
                putInt("user_id", user.id)
                putString("user_name", user.name)
                putString("user_role", user.role.name)
                apply()
            }
            android.util.Log.d("ID_CHECK", "登录成功！当前内存 ID: $currentManagerId")
        })
        return
    }

    // 2. 主界面逻辑 (已登录)
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

                val allMenuItems = listOf(
                    Triple("产品上架", Icons.Default.AddBox, "all"),
                    Triple("置换审核", Icons.Default.CheckCircle, "all"),
                    Triple("产品管理", Icons.Default.List, "all"),
                    Triple("礼品发布", Icons.Default.CardGiftcard, "all"),
                    Triple("订单管理", Icons.Default.Chat, "all"),
                    Triple("用户管理", Icons.Default.People, "admin"),
                    Triple("数据看板", Icons.Default.Assessment, "admin")
                )

                allMenuItems.forEach { (title, icon, requiredRole) ->
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

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    label = { Text("退出登录") },
                    selected = false,
                    onClick = {
                        prefs.edit().clear().apply()
                        isLoggedIn = false
                        currentManagerId = 0
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
                                "产品管理", "用户管理" -> refreshSignal = System.currentTimeMillis()
                                "订单管理" -> orderViewModel.fetchOrders(currentManagerId)
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
                    "订单管理" -> {
                        // 自动刷新逻辑
                        LaunchedEffect(currentManagerId) {
                            if (currentManagerId != 0) {
                                orderViewModel.fetchOrders(currentManagerId)
                            }
                        }

                        OrderManagementScreen(
                            // 1. 数据源拆分：从 auditViewModel 获取两个独立的列表
                            intentOrders = auditViewModel.uiState.value.intentOrders,
                            formalOrders = auditViewModel.uiState.value.formalOrders,

                            managerId = currentManagerId,

                            // 2. 刷新逻辑拆分：
                            // 意向单刷新调用原有的 fetchIntentOrders
                            onRefreshIntent = { id -> auditViewModel.fetchIntentOrders() },
                            // 正式单刷新调用新写的 fetchFormalOrders
                            onRefreshFormal = { auditViewModel.fetchFormalOrders() },

                            // 3. 核心业务逻辑：
                            onConfirmIntent = { orderObject ->
                                // 触发转正引擎：生成卷宗 -> 上传 -> 迁移数据库
                                auditViewModel.approveAndConvertOrder(orderObject)
                            },

                            // 4. 完成逻辑：
                            onCompleteOrder = { id ->
                                // 保持原有的 orderViewModel 处理逻辑
                                orderViewModel.completeOrder(id, currentManagerId)
                            }
                        )
                    }
                    else -> PlaceholderScreen(currentScreen)
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text = "$name 模块待开发", color = Color.Gray)
    }
}