package com.dee.android.pbl.takechinahome.admin.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.launch

// 定义 UI 状态
data class AuditUiState(
    val isLoading: Boolean = false,
    val pendingItems: List<ExchangeGift> = emptyList(),
    val errorMessage: String? = null
)

class AuditViewModel : ViewModel() {

    // 使用 mutableStateOf 让 Compose 能够观察到状态变化
    private val _uiState = mutableStateOf(AuditUiState())
    val uiState: State<AuditUiState> = _uiState

    init {
        // 初始化时自动获取一次数据
        fetchPendingItems()
    }

    fun fetchPendingItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = RetrofitClient.instance.getPendingItems()
                if (response.success) {
                    _uiState.value = _uiState.value.copy(
                        pendingItems = response.data ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = response.message ?: "获取失败",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "网络错误: ${e.localizedMessage}",
                    isLoading = false
                )
            }
        }
    }

    // 审核操作：通过或驳回
    fun performAction(id: Int, approve: Boolean) {
        viewModelScope.launch {
            val newStatus = if (approve) 2 else 3
            try {
                val response = RetrofitClient.instance.auditItem(id, newStatus)
                if (response.success) {
                    // 成功后，重新刷新列表
                    fetchPendingItems()
                }
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }
}