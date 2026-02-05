package com.dee.android.pbl.takechinahome.admin.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dee.android.pbl.takechinahome.admin.data.db.AppDatabase // 确保导入你的数据库类
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import com.dee.android.pbl.takechinahome.admin.data.model.Order
import com.dee.android.pbl.takechinahome.admin.data.model.PendingTask // 确保导入实体类
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import com.dee.android.pbl.takechinahome.admin.ui.util.ScrollGenerator
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
    val intentOrders: List<Order> = emptyList(),
    val filterMode: FilterMode = FilterMode.PENDING,
    val errorMessage: String? = null,
    val syncMessage: String? = null
)

class AuditViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = mutableStateOf(AuditUiState())
    val uiState: State<AuditUiState> = _uiState

    // ✨ 只有这里增加了数据库相关的初始化，其他逻辑不动
    private val db = AppDatabase.getInstance(application)
    private val taskDao = db.pendingTaskDao()
    private val scrollGenerator = ScrollGenerator(application)

    init {
        refreshAll()
    }

    fun refreshAll() {
        fetchPendingItems()
        fetchIntentOrders()
    }

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

    fun fetchIntentOrders() {
        viewModelScope.launch {
            try {
                val currentManagerId = 0 // 改为 0 配合你的 PHP 逻辑获取全量
                val response = RetrofitClient.instance.getIntentOrders(currentManagerId)
                if (response.success) {
                    _uiState.value = _uiState.value.copy(intentOrders = response.data ?: emptyList())
                }
            } catch (e: Exception) {
                android.util.Log.e("Audit", "获取意向订单失败", e)
            }
        }
    }

    fun approveAndConvertOrder(order: Order) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.Main) {
            try {
                scrollGenerator.generateFormalScroll(order) { imageFile ->
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
        // ✨ 将原本的 TODO 替换为真正的 IO 写入逻辑
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 存入 Room 数据库
                val task = PendingTask(
                    orderId = orderId,
                    localImagePath = file.absolutePath
                )
                taskDao.insertTask(task)

                // 2. 切回主线程更新 UI
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        syncMessage = "卷宗已生成并存入离线队列"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "存入数据库失败: ${e.localizedMessage}"
                    )
                }
            }
        }

        // 保留你的测试日志
        if (file.exists() && file.length() > 0) {
            android.util.Log.d("SUCCESS", "卷宗生成成功！路径: ${file.absolutePath} 大小: ${file.length()} bytes")
        } else {
            android.util.Log.e("ERROR", "图片生成失败或为空文件")
        }
    }

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