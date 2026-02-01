package com.dee.android.pbl.takechinahome.admin

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dee.android.pbl.takechinahome.admin.ui.screens.AuditDashboardScreen
import com.dee.android.pbl.takechinahome.admin.ui.screens.ProductUploadScreen
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("置换审核") }

    // 获取 ViewModel
    val auditViewModel: AuditViewModel = viewModel()

    // ✨ 核心修正：通过 uiState 动态计算待审核数量 (status == 1)
    // 这样当后端数据刷新或管理员点击审核后，这个数字会自动变动
    val pendingCount = auditViewModel.uiState.value.allItems.count { it.status == 1 }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "雅鉴管理后台",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()

                val menuItems = listOf(
                    "产品上架" to Icons.Default.AddBox,
                    "置换审核" to Icons.Default.CheckCircle,
                    "产品管理" to Icons.Default.List,
                    "礼品发布" to Icons.Default.CardGiftcard,
                    "订单管理" to Icons.Default.Chat,
                    "用户管理" to Icons.Default.People,
                    "数据看板" to Icons.Default.Assessment
                )

                menuItems.forEach { (title, icon) ->
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
                        // 1. 刷新按钮：联动 ViewModel 的 fetch 函数
                        IconButton(onClick = { auditViewModel.fetchPendingItems() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }

                        // 2. 搜索按钮
                        IconButton(onClick = { /* 搜索逻辑待实现 */ }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }

                        // 3. 用户头像 + 动态 Badge
                        // ✨ 修正：增加 end padding (12.dp) 确保红点显示完整
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
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = "用户中心")
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            // ✨ 修正：直接进入主内容，彻底干掉阴影区提到的冗余 Header
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                when (currentScreen) {
                    "置换审核" -> AuditDashboardScreen(viewModel = auditViewModel)
                    "产品上架" -> ProductUploadScreen()
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