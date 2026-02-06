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
    val formalOrders: List<Order> = emptyList(), // ✨ 新增：正式订单列表状态
    val filterMode: FilterMode = FilterMode.PENDING,
    val errorMessage: String? = null,
    val syncMessage: String? = null
)

class AuditViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = mutableStateOf(AuditUiState())
    val uiState: State<AuditUiState> = _uiState

    private val db = AppDatabase.getInstance(application)
    private val scrollGenerator = ScrollGenerator(application)

    init {
        refreshAll()
    }

    fun refreshAll() {
        fetchPendingItems()
        fetchIntentOrders()
        fetchFormalOrders() // ✨ 新增：初始化时同步获取正式订单
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

    fun fetchIntentOrders() {
        _uiState.value = _uiState.value.copy(intentOrders = emptyList())

        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminService.getIntentOrders(0)
                if (response.success) {
                    _uiState.value = _uiState.value.copy(intentOrders = response.data ?: emptyList())
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "获取意向订单列表失败", e)
            }
        }
    }

    fun fetchFormalOrders() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminService.getFormalOrders() // 调用你刚才加在 ApiService 里的接口
                if (response.success) {
                    _uiState.value = _uiState.value.copy(formalOrders = response.data ?: emptyList())
                    Log.d("AuditFlow", "✅ 正式订单抓取成功: ${response.data?.size}")
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "获取正式订单库失败", e)
            }
        }
    }

    fun approveAndConvertOrder(order: Order) {
        Log.d("AuditFlow", "1. 触发转正流程: OrderID=${order.id}")
        _uiState.value = _uiState.value.copy(isLoading = true, syncMessage = "正在生成正式卷宗...", errorMessage = null)

        viewModelScope.launch {
            try {
                scrollGenerator.generateFormalScroll(order) { imageFile ->
                    Log.d("AuditFlow", "3. 卷宗生成成功，准备上传: ${imageFile.absolutePath}")
                    handleGeneratedScroll(order, imageFile)
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "截图生成失败: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "截图失败: ${e.message}")
            }
        }
    }

    private fun handleGeneratedScroll(order: Order, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 准备 Multipart 图片
                val fileRequestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val formalImagePart = MultipartBody.Part.createFormData(
                    "formal_image",
                    file.name,
                    fileRequestBody
                )

                // 2. 准备 RequestBody
                val textType = "text/plain".toMediaTypeOrNull()
                val orderIdBody = order.id.toString().toRequestBody(textType)
                val giftNameBody = (order.targetGiftName ?: "正式卷宗").toRequestBody(textType)
                val qtyBody = order.targetQty.toString().toRequestBody(textType)
                val dateBody = (order.deliveryDate ?: "待定").toRequestBody(textType)
                val contactBody = (order.contactMethod ?: "System").toRequestBody(textType)
                val statusBody = "1".toRequestBody(textType)

                Log.d("AuditFlow", "4. 开始同步到云端: ${order.id}")

                val response = RetrofitClient.adminService.updateOrderIntent(
                    orderId = orderIdBody,
                    giftName = giftNameBody,
                    qty = qtyBody,
                    date = dateBody,
                    contact = contactBody,
                    status = statusBody,
                    formalImage = formalImagePart
                )

                if (response.success) {
                    Log.d("AuditFlow", "5. ✅ 图片同步成功: ${response.message}")
                    // --- 关键追加：执行最后的事务收尾 ---
                    finalizeTransaction(order, file.absolutePath)
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("AuditFlow", "5. ❌ 图片同步失败: ${response.message}")
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "同步失败: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "5. ❌ 网络层异常: ${e.message}")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "网络连接异常")
                }
            }
        }
    }

    /**
     * 追加步骤：执行入库正式表、清理意向单、发送通知
     * [order] 订单对象
     * [localPath] 图片在手机里的绝对路径
     */
    private fun finalizeTransaction(order: Order, localPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("AuditFlow", "6. 发送收尾请求: ID=${order.id}")
                val managerInfo = order.managerName ?: "SystemAdmin"
                val response = RetrofitClient.adminService.finalizeOrder(
                    orderId = order.id,
                    localPath = localPath,
                    managerEmail = managerInfo
                )

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        Log.d("AuditFlow", "7. ✅ 流程彻底终结")
                        _uiState.value = _uiState.value.copy(
                            syncMessage = "转正成功，订单已移入正式库"
                        )
                        // 🚀 核心改动：流程结束后，同时刷新“意向列表”和“正式列表”
                        fetchIntentOrders()
                        fetchFormalOrders()
                    } else {
                        Log.e("AuditFlow", "❌ 后端返回失败: ${response.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "收尾失败: ${response.message}"
                        )
                        fetchIntentOrders()
                    }
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "❌ 网络/系统异常: ${e.message}")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "异常: ${e.message}")
                    fetchIntentOrders()
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
                _uiState.value = _uiState.value.copy(errorMessage = "操作失败: ${e.localizedMessage}")
            }
        }
    }
}