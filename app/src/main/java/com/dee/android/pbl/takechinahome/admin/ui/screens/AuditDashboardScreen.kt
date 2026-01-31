package com.dee.android.pbl.takechinahome.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dee.android.pbl.takechinahome.admin.ui.components.AuditItemCard
import com.dee.android.pbl.takechinahome.admin.viewmodel.AuditViewModel

// 必须手动导入 getValue 才能使用 'by' 代理
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditDashboardScreen(viewModel: AuditViewModel = viewModel()) {
    // 因为 ViewModel 中使用 mutableStateOf，这里直接使用 by 读取即可
    val uiState by viewModel.uiState
    var searchQuery by remember { mutableStateOf("") }

    val announcements = listOf(
        "端午文化专题审核准则更新" to "14:20",
        "系统服务器定于今晚进行例行维护" to "昨天"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理后台", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.fetchPendingItems() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            // 1. 顶部个人信息区
            item {
                AdminTopHeader(pendingCount = uiState.pendingItems.size)
            }

            // 2. 系统公告区
            item {
                AnnouncementSection(announcements)
            }

            // 3. 功能导航区
            item {
                FunctionNavigation()
            }

            // 4. 搜索框
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("搜索物什名称或申请人...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
            }

            // 5. 待审核清单标题
            item {
                Text(
                    text = "待审核物什清单",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 6. 审核列表项
            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                // 修正变量名为 pendingItems，并使用 Boolean 调用 performAction
                items(uiState.pendingItems.filter { it.itemName.contains(searchQuery, ignoreCase = true) }) { item ->
                    AuditItemCard(
                        item = item,
                        onApprove = { viewModel.performAction(item.id, true) },
                        onReject = { viewModel.performAction(item.id, false) }
                    )
                }
            }
        }
    }
}

@Composable
fun AdminTopHeader(pendingCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("午安，管理员", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("目前有 $pendingCount 项待处理申请", color = Color.Gray)
        }
        Box {
            Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(8.dp))
            }
            if (pendingCount > 0) {
                Badge(Modifier.align(Alignment.TopEnd)) { Text(pendingCount.toString()) }
            }
        }
    }
}

@Composable
fun AnnouncementSection(notices: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFBC02D))
                Spacer(Modifier.width(8.dp))
                Text("系统公告", fontWeight = FontWeight.Bold)
            }
            notices.forEach { (msg, time) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween) {
                    Text("• $msg", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(time, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun FunctionNavigation() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        NavigationItem(Icons.Default.CheckCircle, "准入审核", Color(0xFF4CAF50))
        NavigationItem(Icons.AutoMirrored.Filled.List, "全部清单", Color(0xFF2196F3))
        NavigationItem(Icons.Default.Settings, "后台设置", Color(0xFF9E9E9E))
    }
}

@Composable
fun NavigationItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.1f), modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(16.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}