package com.dee.android.pbl.takechinahome.admin.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dee.android.pbl.takechinahome.admin.data.db.AppDatabase
import com.dee.android.pbl.takechinahome.admin.data.model.ExchangeGift
import com.dee.android.pbl.takechinahome.admin.data.model.Order
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import com.dee.android.pbl.takechinahome.admin.ui.util.ScrollGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Log

enum class FilterMode { ALL, PENDING, APPROVED, REJECTED }

data class AuditUiState(
    val isLoading: Boolean = false,
    val allItems: List<ExchangeGift> = emptyList(),
    val pendingItems: List<ExchangeGift> = emptyList(),
    val intentOrders: List<Order> = emptyList(),
    val formalOrders: List<Order> = emptyList(),
    val filterMode: FilterMode = FilterMode.PENDING,
    val errorMessage: String? = null,
    val syncMessage: String? = null
)

class AuditViewModel(application: Application) : AndroidViewModel(application) {
    // ✨ 保存当前的经理ID，防止刷新时丢失上下文
    private var currentManagerId: Int = 0

    private val _uiState = mutableStateOf(AuditUiState())
    val uiState: State<AuditUiState> = _uiState

    private val db = AppDatabase.getInstance(application)
    private val scrollGenerator = ScrollGenerator(application)

    init {
        // 初始化时暂时使用 ID 1 预加载，后期建议由 UI 层调用 refreshAll(id)
        refreshAll(1)
    }

    // ✨ 核心修正：带参数的刷新，并更新成员变量
    fun refreshAll(managerId: Int) {
        this.currentManagerId = managerId
        fetchPendingItems()
        fetchIntentOrders(managerId)
        fetchFormalOrders()
    }

    fun fetchPendingItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = RetrofitClient.adminService.getPendingItems()
                if (response.success) {
                    val fetchedData = response.data ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        allItems = fetchedData,
                        pendingItems = applyFilter(fetchedData, _uiState.value.filterMode),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "加载失败: ${e.message}", isLoading = false)
            }
        }
    }

    fun fetchIntentOrders(managerId: Int) {
        if (managerId <= 0) return
        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminService.getIntentOrders(managerId)
                if (response.success) {
                    _uiState.value = _uiState.value.copy(
                        intentOrders = response.data ?: emptyList()
                    )
                    Log.d("AuditFlow", "意向订单刷新成功，数量: ${response.data?.size}")
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "失败: ${e.message}")
            }
        }
    }

    fun fetchFormalOrders() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminService.getFormalOrders()
                if (response.success) {
                    _uiState.value = _uiState.value.copy(formalOrders = response.data ?: emptyList())
                    Log.d("AuditFlow", "✅ 正式订单抓取成功: ${response.data?.size}")
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "获取正式订单库失败", e)
            }
        }
    }

    fun approveAndConvertOrder(order: Order, managerEmail: String = "admin@ichessgeek.com") {
        Log.d("AuditFlow", "1. 触发转正流程: OrderID=${order.id}")
        _uiState.value = _uiState.value.copy(isLoading = true, syncMessage = "正在生成正式卷宗...")

        viewModelScope.launch {
            try {
                scrollGenerator.generateFormalScroll(order) { imageFile ->
                    handleGeneratedScroll(order, imageFile, managerEmail)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "截图失败: ${e.message}")
            }
        }
    }

    private fun handleGeneratedScroll(order: Order, file: File, managerEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileRequestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val formalImagePart = MultipartBody.Part.createFormData(
                    "formal_image",
                    file.name,
                    fileRequestBody
                )

                val textType = "text/plain".toMediaTypeOrNull()

                // 🚩 这里的 managerIdBody 使用了保存的 currentManagerId
                val response = RetrofitClient.adminService.updateOrderIntent(
                    orderId = order.id.toString().toRequestBody(textType),
                    managerId = currentManagerId.toString().toRequestBody(textType),
                    managerName = "斯嘉丽".toRequestBody(textType),
                    // 确保不要回传硬编码的 "正式卷宗"，除非真的没名字
                    giftName = (order.targetGiftName ?: "未命名礼品").toRequestBody(textType),
                    qty = order.targetQty.toString().toRequestBody(textType),
                    date = (order.deliveryDate ?: "无日期").toRequestBody(textType),
                    contact = (order.contactMethod ?: "无联系方式").toRequestBody(textType),
                    status = "1".toRequestBody(textType), // 状态 1 代表确认为正式
                    formalImage = formalImagePart
                )

                Log.d("AuditFlow", "上传响应: ${response.success}, 消息: ${response.message}")

                if (response.success) {
                    finalizeTransaction(order, file.absolutePath, managerEmail)
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "同步失败: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "handleGeneratedScroll 崩溃", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "网络异常")
                }
            }
        }
    }

    private fun finalizeTransaction(order: Order, localPath: String, managerEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.adminService.finalizeOrder(
                    orderId = order.id,
                    localPath = localPath,
                    managerEmail = managerEmail
                )

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        _uiState.value = _uiState.value.copy(isLoading = false, syncMessage = "转正完成")
                        // ✨ 修正：传入保存好的 currentManagerId 刷新列表
                        refreshAll(currentManagerId)
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "收尾失败")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "系统错误")
                }
            }
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
                val response = RetrofitClient.adminService.auditItem(id, newStatus)
                if (response.success) fetchPendingItems()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "操作失败")
            }
        }
    }
}