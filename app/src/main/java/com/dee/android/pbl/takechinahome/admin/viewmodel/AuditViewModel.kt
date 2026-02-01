package com.dee.android.pbl.takechinahome.admin.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.launch

// 1. 定义筛选模式枚举
enum class FilterMode { ALL, PENDING, APPROVED, REJECTED }

// 2. 完善 UI 状态类
data class AuditUiState(
    val isLoading: Boolean = false,
    val allItems: List<ExchangeGift> = emptyList(),
    val pendingItems: List<ExchangeGift> = emptyList(),
    val filterMode: FilterMode = FilterMode.PENDING,
    val errorMessage: String? = null
)

class AuditViewModel : ViewModel() {

    private val _uiState = mutableStateOf(AuditUiState())
    val uiState: State<AuditUiState> = _uiState

    init {
        fetchPendingItems()
    }

    /**
     * 从后端获取数据
     */
    fun fetchPendingItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // 调用接口获取数据
                val response = RetrofitClient.instance.getPendingItems()

                if (response.success) {
                    // 修正：直接从 response.data 获取，处理 null 情况
                    val fetchedData = response.data ?: emptyList()

                    println("DEBUG: 收到数据总量 = ${fetchedData.size}")
                    fetchedData.forEach { println("DEBUG: 物品=${it.itemName}, 状态=${it.status}") }

                    _uiState.value = _uiState.value.copy(
                        allItems = fetchedData,
                        pendingItems = applyFilter(fetchedData, _uiState.value.filterMode),
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = response.message ?: "获取数据失败",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "网络连接异常: ${e.localizedMessage}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * 切换筛选模式
     */
    fun setFilterMode(mode: FilterMode) {
        _uiState.value = _uiState.value.copy(
            filterMode = mode,
            pendingItems = applyFilter(_uiState.value.allItems, mode)
        )
    }

    /**
     * 核心过滤逻辑
     */
    private fun applyFilter(list: List<ExchangeGift>, mode: FilterMode): List<ExchangeGift> {
        return when (mode) {
            FilterMode.ALL -> list
            FilterMode.PENDING -> list.filter { it.status == 1 }
            FilterMode.APPROVED -> list.filter { it.status == 2 }
            FilterMode.REJECTED -> list.filter { it.status == 3 }
        }
    }

    /**
     * 执行审核动作
     */
    fun performAction(id: Int, approve: Boolean) {
        viewModelScope.launch {
            val newStatus = if (approve) 2 else 3
            try {
                // 确保引用的是正确的 adminService 或 instance
                val response = RetrofitClient.instance.auditItem(id, newStatus)
                if (response.success) {
                    fetchPendingItems()
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "操作失败: ${response.message}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "提交审核出错: ${e.localizedMessage}")
            }
        }
    }
}