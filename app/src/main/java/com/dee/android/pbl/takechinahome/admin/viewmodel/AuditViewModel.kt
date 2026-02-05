package com.dee.android.pbl.takechinahome.admin.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import com.dee.android.pbl.takechinahome.admin.data.model.Order // 确保导入了 Order 模型
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import com.dee.android.pbl.takechinahome.admin.ui.util.ScrollGenerator // 导入刚刚创建的生成器
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 1. 定义筛选模式
enum class FilterMode { ALL, PENDING, APPROVED, REJECTED }

// 2. 完善 UI 状态类
data class AuditUiState(
    val isLoading: Boolean = false,
    val allItems: List<ExchangeGift> = emptyList(),
    val pendingItems: List<ExchangeGift> = emptyList(),
    // 新增：意向订单列表状态
    val intentOrders: List<Order> = emptyList(),
    val filterMode: FilterMode = FilterMode.PENDING,
    val errorMessage: String? = null,
    val syncMessage: String? = null // 用于提示离线同步状态
)

// 3. 修改为 AndroidViewModel 以获取 Context
class AuditViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = mutableStateOf(AuditUiState())
    val uiState: State<AuditUiState> = _uiState

    // 初始化卷宗生成器
    private val scrollGenerator = ScrollGenerator(application)

    init {
        refreshAll()
    }

    /**
     * 统一刷新：同时获取审核项和意向订单
     */
    fun refreshAll() {
        fetchPendingItems()
        fetchIntentOrders()
    }

    /**
     * 原有逻辑：获取置换审核项
     */
    fun fetchPendingItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = RetrofitClient.instance.getPendingItems()
                if (response.success) {
                    val fetchedData = response.data ?: emptyList()
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
     * 新增逻辑：获取待处理的客户意向订单
     */
    fun fetchIntentOrders() {
        viewModelScope.launch {
            try {
                // 假设你已经在某处存储了登录经理的 ID（比如从 SharedPreferences 或 LoginRepository 获取）
                // 这里暂时传 0 或者实际的 ID。如果传的是 Admin 的 ID，PHP 会返回全部。
                val currentManagerId = 1 // TODO: 替换为实际登录的经理 ID

                // 修改为调用 getIntentOrders 而不是不存在的 getAdminIntentOrders
                val response = RetrofitClient.instance.getIntentOrders(currentManagerId)

                if (response.success) {
                    _uiState.value = _uiState.value.copy(intentOrders = response.data ?: emptyList())
                }
            } catch (e: Exception) {
                android.util.Log.e("Audit", "获取意向订单失败", e)
            }
        }
    }

    /**
     * ✨ 核心逻辑：审核通过并将意向单“转正”
     * 包含：生成图片 -> 保存本地 -> (待后续接入) 离线队列
     */
    fun approveAndConvertOrder(order: Order) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        // 在主线程启动生成器（WebView 必须在主线程）
        viewModelScope.launch(Dispatchers.Main) {
            try {
                scrollGenerator.generateFormalScroll(order) { imageFile ->
                    // 图片生成成功后的回调
                    handleGeneratedScroll(order.id, imageFile)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "生成卷宗失败: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun handleGeneratedScroll(orderId: Int, file: File) {
        // 1. 更新 UI 提示
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            syncMessage = "卷宗已生成：${file.name}，正在准备同步..."
        )

        // 2. 这里后续接入 Room 数据库，存入 PendingUpload 表
        // TODO: repository.insertPendingTask(PendingTask(orderId, file.path))

        android.util.Log.d("Audit", "本地卷宗存根成功: ${file.absolutePath}")
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

    private fun applyFilter(list: List<ExchangeGift>, mode: FilterMode): List<ExchangeGift> {
        return when (mode) {
            FilterMode.ALL -> list
            FilterMode.PENDING -> list.filter { it.status == 1 }
            FilterMode.APPROVED -> list.filter { it.status == 2 }
            FilterMode.REJECTED -> list.filter { it.status == 3 }
        }
    }

    /**
     * 执行审核动作（针对置换物品）
     */
    fun performAction(id: Int, approve: Boolean) {
        viewModelScope.launch {
            val newStatus = if (approve) 2 else 3
            try {
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