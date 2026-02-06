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

    fun fetchIntentOrders(managerId: Int = 0) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminService.getIntentOrders(managerId)
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

    // ✨ 修正：增加 managerEmail 参数，默认值设为斯嘉丽的邮箱
    fun approveAndConvertOrder(order: Order, managerEmail: String = "admin@ichessgeek.com") {
        Log.d("AuditFlow", "1. 触发转正流程: OrderID=${order.id}, 操作人: $managerEmail")
        _uiState.value = _uiState.value.copy(isLoading = true, syncMessage = "正在生成正式卷宗...", errorMessage = null)

        viewModelScope.launch {
            try {
                scrollGenerator.generateFormalScroll(order) { imageFile ->
                    Log.d("AuditFlow", "3. 卷宗生成成功，准备上传: ${imageFile.absolutePath}")
                    // 将 email 传给下一步
                    handleGeneratedScroll(order, imageFile, managerEmail)
                }
            } catch (e: Exception) {
                Log.e("AuditFlow", "截图生成失败: ${e.message}")
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
                    Log.d("AuditFlow", "5. ✅ 图片同步成功")
                    // 将 email 传给收尾事务
                    finalizeTransaction(order, file.absolutePath, managerEmail)
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "同步失败: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "网络连接异常")
                }
            }
        }
    }

    private fun finalizeTransaction(order: Order, localPath: String, managerEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("AuditFlow", "6. 发送收尾请求: ID=${order.id}, 使用邮箱: $managerEmail")

                val response = RetrofitClient.adminService.finalizeOrder(
                    orderId = order.id,
                    localPath = localPath,
                    managerEmail = managerEmail // ✨ 彻底修复：这里不再使用 SystemAdmin
                )

                withContext(Dispatchers.Main) {
                    if (response.success) {
                        Log.d("AuditFlow", "7. ✅ 流程彻底终结")
                        fetchIntentOrders()
                        fetchFormalOrders()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "收尾失败: ${response.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "异常: ${e.message}")
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