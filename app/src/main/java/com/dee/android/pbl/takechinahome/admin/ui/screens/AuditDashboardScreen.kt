package com.dee.android.pbl.takechinahome.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dee.android.pbl.takechinahome.admin.ui.components.AuditItemCard
import com.dee.android.pbl.takechinahome.admin.viewmodel.AuditViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditDashboardScreen(viewModel: AuditViewModel = viewModel()) {
    // 观察 ViewModel 中的状态
    val uiState by viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("管理后台 - 待审核物什") })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // 1. 加载中状态
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // 2. 错误状态
            uiState.errorMessage?.let { error ->
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.fetchPendingItems() }) {
                        Text("重试")
                    }
                }
            }

            // 3. 数据列表
            if (uiState.pendingItems.isEmpty() && !uiState.isLoading) {
                Text(text = "暂无待审核项", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.pendingItems) { item ->
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