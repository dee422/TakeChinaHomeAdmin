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
    val allItems: List<ExchangeGift> = emptyList(),     // 存储从后端获取的原始全量数据
    val pendingItems: List<ExchangeGift> = emptyList(), // 存储当前 UI 实际展示的（过滤后）数据
    val filterMode: FilterMode = FilterMode.PENDING,    // 当前选中的筛选模式
    val errorMessage: String? = null
)

class AuditViewModel : ViewModel() {

    // 使用 Compose 的 mutableStateOf 实现响应式状态
    private val _uiState = mutableStateOf(AuditUiState())
    val uiState: State<AuditUiState> = _uiState

    init {
        // 首次进入页面自动加载
        fetchPendingItems()
    }

    /**
     * 从后端获取数据
     */
    fun fetchPendingItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // 注意：这里调用的是获取所有待审核（或全量）数据的接口
                val response = RetrofitClient.instance.getPendingItems()
                val data = response.data ?: emptyList()

                if (response.success) {
                    val data = response.data ?: emptyList()
                    // 打印出来看看：后端到底传了几个物品？每个物品的 status 到底是多少？
                    println("DEBUG: 收到数据总量 = ${data.size}")
                    data.forEach { println("DEBUG: 物品=${it.itemName}, 状态=${it.status}") }

                    _uiState.value = _uiState.value.copy(
                        allItems = data,
                        pendingItems = applyFilter(data, _uiState.value.filterMode),
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
     * 核心过滤逻辑：将后端 status 映射到 UI 模式
     */
    private fun applyFilter(list: List<ExchangeGift>, mode: FilterMode): List<ExchangeGift> {
        return when (mode) {
            FilterMode.ALL -> list
            FilterMode.PENDING -> list.filter { it.status == 1 }   // 假设 1 为待审核
            FilterMode.APPROVED -> list.filter { it.status == 2 }  // 假设 2 为已通过
            FilterMode.REJECTED -> list.filter { it.status == 3 }  // 假设 3 为已拒绝
        }
    }

    /**
     * 执行审核动作 (Approve/Reject)
     */
    fun performAction(id: Int, approve: Boolean) {
        viewModelScope.launch {
            val newStatus = if (approve) 2 else 3
            try {
                val response = RetrofitClient.instance.auditItem(id, newStatus)
                if (response.success) {
                    // 操作成功后，重新抓取数据以确保状态同步
                    fetchPendingItems()
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "操作失败: ${response.message}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "提交审核出错")
            }
        }
    }
}