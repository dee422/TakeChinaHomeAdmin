package com.dee.android.pbl.takechinahome.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dee.android.pbl.takechinahome.admin.data.model.Order
import com.dee.android.pbl.takechinahome.admin.data.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OrderViewModel : ViewModel() {

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 刷新订单
    fun fetchOrders(managerId: Int) {
        android.util.Log.d("OrderDebug", "正在请求接口，传入的 managerId 是: $managerId")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.adminService.getIntentOrders(managerId)
                if (response.success) {
                    _orders.value = response.data ?: emptyList()
                    // 💡 调试日志
                    android.util.Log.d("OrderDebug", "成功获取到 ${response.data?.size} 条订单")
                }
            } catch (e: Exception) {
                // 💡 这里会告诉你到底是哪个字段解析崩了
                android.util.Log.e("OrderDebug", "解析失败: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 转正订单
    fun confirmIntent(orderId: Int, managerId: Int) {
        viewModelScope.launch {
            try {
                // 1. 发送转正请求
                val response = RetrofitClient.adminService.convertToFormal(orderId, managerId)

                // 💡 关键：无论成功失败，都打个 Log 看看
                android.util.Log.d("OrderDebug", "转正请求结果: ${response.success}, 消息: ${response.message}")

                if (response.success) {
                    // 2. 只有成功了才刷新
                    fetchOrders(managerId)
                }
            } catch (e: Exception) {
                android.util.Log.e("OrderDebug", "网络请求崩溃: ${e.message}")
            }
        }
    }

    // 完成订单
    fun completeOrder(orderId: Int, managerId: Int) {
        viewModelScope.launch {
            try {
                // 只有这里才应该发送 "Completed"
                val response = RetrofitClient.adminService.updateOrderStatus(orderId, "Completed", 0)
                if (response.success) {
                    fetchOrders(managerId)
                }
            } catch (e: Exception) {
                android.util.Log.e("OrderDebug", "交付请求失败: ${e.message}")
            }
        }
    }

    private fun updateOrder(orderId: Int, status: String, isIntent: Int, managerId: Int) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.adminService.updateOrderStatus(orderId, status, isIntent)
                if (response.success) {
                    fetchOrders(managerId) // 成功后自动刷新
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}