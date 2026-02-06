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

enum class FilterMode { ALL, PENDING, APPROVED, REJECTED }

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

    private val db = AppDatabase.getInstance(application)
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
        viewModelScope.launch {
            try {
                // 传入 0 以获取全量意向订单
                val response = RetrofitClient.adminService.getIntentOrders(0)
                if (response.success) {
                    _uiState.value = _uiState.value.copy(intentOrders = response.data ?: emptyList())
                }
            } catch (e: Exception) {
                android.util.Log.e("AuditFlow", "获取意向订单列表失败", e)
            }
        }
    }

    fun approveAndConvertOrder(order: Order) {
        android.util.Log.d("AuditFlow", "1. 触发转正流程: OrderID=${order.id}")
        _uiState.value = _uiState.value.copy(isLoading = true, syncMessage = "正在生成正式卷宗...", errorMessage = null)

        viewModelScope.launch {
            try {
                scrollGenerator.generateFormalScroll(order) { imageFile ->
                    android.util.Log.d("AuditFlow", "3. 卷宗生成成功，准备上传: ${imageFile.absolutePath}")
                    handleGeneratedScroll(order, imageFile)
                }
            } catch (e: Exception) {
                android.util.Log.e("AuditFlow", "截图生成失败: ${e.message}")
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

                // 2. 准备 RequestBody (对应 AdminApiService 定义)
                val textType = "text/plain".toMediaTypeOrNull()
                val orderIdBody = order.id.toString().toRequestBody(textType)
                val giftNameBody = (order.targetGiftName ?: "正式卷宗").toRequestBody(textType)
                val qtyBody = order.targetQty.toString().toRequestBody(textType)
                val dateBody = (order.deliveryDate ?: "待定").toRequestBody(textType)
                val contactBody = (order.contactMethod ?: "System").toRequestBody(textType)
                val statusBody = "1".toRequestBody(textType)

                android.util.Log.d("AuditFlow", "4. 开始同步到云端: ${order.id}")

                // 3. 执行请求 (这里使用的是 AdminApiService.kt 中的 updateOrderIntent)
                val response = RetrofitClient.adminService.updateOrderIntent(
                    orderId = orderIdBody,
                    giftName = giftNameBody,
                    qty = qtyBody,
                    date = dateBody,
                    contact = contactBody,
                    status = statusBody,
                    formalImage = formalImagePart
                )

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        android.util.Log.d("AuditFlow", "5. ✅ 同步成功: ${response.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            syncMessage = "转正成功！卷宗已存入 formal_orders"
                        )
                        fetchIntentOrders() // 刷新列表
                    } else {
                        android.util.Log.e("AuditFlow", "5. ❌ 同步被拒绝: ${response.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "同步失败: ${response.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                // 如果 PHP 报错（500）或网络断开，会进到这里
                android.util.Log.e("AuditFlow", "5. ❌ 网络层异常: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "网络连接异常，请检查服务器日志"
                    )
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