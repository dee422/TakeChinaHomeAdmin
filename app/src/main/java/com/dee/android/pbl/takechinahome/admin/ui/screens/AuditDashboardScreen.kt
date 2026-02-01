package com.dee.android.pbl.takechinahome.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dee.android.pbl.takechinahome.admin.ui.components.AuditItemCard
import com.dee.android.pbl.takechinahome.admin.viewmodel.AuditViewModel
import com.dee.android.pbl.takechinahome.admin.viewmodel.FilterMode

@Composable
fun AuditDashboardScreen(viewModel: AuditViewModel = viewModel()) {
    val uiState by viewModel.uiState
    var searchQuery by remember { mutableStateOf("") }

    val announcements = listOf(
        "端午文化专题审核准则更新" to "14:20",
        "系统服务器定于今晚进行例行维护" to "昨天"
    )

    // ✨ 核心修正：移除 Scaffold 和 TopAppBar，直接从内容开始
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // ✨ 修正：移除了 AdminTopHeader (那块带欢迎语和重复头像的区域)

            // 1. 系统公告区
            item {
                AnnouncementSection(announcements)
            }

            // 2. 功能导航区 (准入审核/全部清单/已驳回)
            item {
                FunctionNavigation(
                    currentMode = uiState.filterMode,
                    onFilterChange = { mode -> viewModel.setFilterMode(mode) }
                )
            }

            // 3. 数据看板 (数据概览卡片)
            item { DashboardSection() }

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

            // 5. 动态清单标题
            item {
                val title = when (uiState.filterMode) {
                    FilterMode.ALL -> "全部物什清单"
                    FilterMode.PENDING -> "待审核物什清单"
                    FilterMode.APPROVED -> "已发布物什清单"
                    FilterMode.REJECTED -> "已驳回物什清单"
                }
                Text(
                    text = title,
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
                val filteredList = uiState.pendingItems.filter {
                    it.itemName.contains(searchQuery, ignoreCase = true)
                }

                if (filteredList.isEmpty()) {
                    item {
                        Text(
                            "暂无相关记录",
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                } else {
                    items(filteredList) { item ->
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
}

// ✨ 这里的 AdminTopHeader 已弃用，MainActivity 接管了头像显示

@Composable
fun FunctionNavigation(currentMode: FilterMode, onFilterChange: (FilterMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        NavigationItem(
            icon = Icons.Default.CheckCircle,
            label = "准入审核",
            color = Color(0xFF4CAF50),
            isSelected = currentMode == FilterMode.PENDING,
            onClick = { onFilterChange(FilterMode.PENDING) }
        )
        NavigationItem(
            icon = Icons.AutoMirrored.Filled.List,
            label = "全部清单",
            color = Color(0xFF2196F3),
            isSelected = currentMode == FilterMode.ALL,
            onClick = { onFilterChange(FilterMode.ALL) }
        )
        NavigationItem(
            icon = Icons.Default.Cancel,
            label = "已驳回",
            color = Color(0xFFF44336),
            isSelected = currentMode == FilterMode.REJECTED,
            onClick = { onFilterChange(FilterMode.REJECTED) }
        )
    }
}

@Composable
fun NavigationItem(
    icon: ImageVector,
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = if (isSelected) color else color.copy(alpha = 0.1f),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else color,
                modifier = Modifier.padding(16.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
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
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color(0xFFFBC02D))
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
fun DashboardSection() {
    val stats = listOf(
        DashboardStat("总置换", "1,284", Icons.Default.SwapHoriz, Color(0xFF673AB7)),
        DashboardStat("活跃用户", "456", Icons.Default.People, Color(0xFF009688)),
        DashboardStat("本周新增", "+12", Icons.Default.TrendingUp, Color(0xFFFF5722))
    )

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "数据概览",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stats.forEach { stat ->
                StatCard(stat, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatCard(stat: DashboardStat, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(stat.icon, tint = stat.color, modifier = Modifier.size(20.dp), contentDescription = null)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stat.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(text = stat.label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

data class DashboardStat(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)