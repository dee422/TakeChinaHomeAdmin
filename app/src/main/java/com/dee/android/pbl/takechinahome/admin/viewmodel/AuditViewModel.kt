package com.dee.android.pbl.takechinahome.admin.viewmodel

import android.app.Application
import android.util.Log
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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
    // 保存当前的经理ID，防止刷新时丢失上下文
    private var currentManagerId: Int = 0

    private val _uiState = mutableStateOf(AuditUiState())
    val uiState: State<AuditUiState> = _uiState

    private val db = AppDatabase.getInstance(application)
    private val scrollGenerator = ScrollGenerator(application)

    init {
        // 初始化时可默认刷新一次（ID 1 或从 Session 获取）
        refreshAll(1)
    }

    // --- 数据刷新与同步 ---

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
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "获取意向单失败: ${e.message}")
            }
        }
    }

    fun fetchFormalOrders() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminService.getFormalOrders()
                if (response.success) {
                    _uiState.value = _uiState.value.copy(formalOrders = response.data ?: emptyList())
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "获取正式库失败", e)
            }
        }
    }

    // --- 转正流程 (Intent -> Formal) ---

    fun approveAndConvertOrder(order: Order, managerEmail: String = "admin@ichessgeek.com") {
        Log.d("AuditFlow", "触发转正流程: OrderID=${order.id}")
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

                val response = RetrofitClient.adminService.updateOrderIntent(
                    orderId = order.id.toString().toRequestBody(textType),
                    managerId = currentManagerId.toString().toRequestBody(textType),
                    managerName = "斯嘉丽".toRequestBody(textType),
                    giftName = (order.targetGiftName ?: "").toRequestBody(textType),
                    qty = order.targetQty.toString().toRequestBody(textType),
                    date = (order.deliveryDate ?: "").toRequestBody(textType),
                    contact = (order.contactMethod ?: "").toRequestBody(textType),
                    status = "1".toRequestBody(textType),
                    formalImage = formalImagePart
                )

                if (response.success) {
                    // 🚩 锁定成功后，务必立刻刷新意向订单列表，UI 才会显示“已锁定”
                    withContext(Dispatchers.Main) {
                        fetchIntentOrders(currentManagerId)
                    }
                    // 然后再进行物理搬家
                    finalizeTransaction(order, file.absolutePath, managerEmail)
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "同步失败: ${response.message}")
                    }
                }
            } catch (e: Exception) {
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

    // --- ✨ 新增：正式订单终态管理 (Completed / Terminated) ---

    fun updateFormalOrderStatus(orderId: Int, newStatus: String, managerId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 这里调用 updateOrderIntent 接口，利用 status 字段更新
                // 在后端逻辑中，status='Completed' 或 'Terminated' 会触发不同的结果
                val textType = "text/plain".toMediaTypeOrNull()

                // 这里的逻辑是：既然是更新正式订单，我们只需要传 ID 和新的 Status
                // 其他字段可以根据后端需求保持可选或传原值
                val res = RetrofitClient.adminService.updateOrderIntent(
                    orderId = orderId.toString().toRequestBody(textType),
                    managerId = managerId.toString().toRequestBody(textType),
                    managerName = "".toRequestBody(textType),
                    giftName = "".toRequestBody(textType), // 后端应处理空值不更新
                    qty = "".toRequestBody(textType),
                    date = "".toRequestBody(textType),
                    contact = "".toRequestBody(textType),
                    status = newStatus.toRequestBody(textType), // ✨ 核心：传入 "Completed" 或 "Terminated"
                    formalImage = null
                )

                if (res.success) {
                    //Log.d("AuditFlow", "订单 #$orderId 状态已更新为: $newStatus")
                    refreshAll(managerId)
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "更新失败: ${res.message}")
                }
            } catch (e: Exception) {
                //Log.e("AuditFlow", "更新正式订单异常", e)
                _uiState.value = _uiState.value.copy(errorMessage = "网络连接异常")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // --- 物件审核逻辑 ---

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