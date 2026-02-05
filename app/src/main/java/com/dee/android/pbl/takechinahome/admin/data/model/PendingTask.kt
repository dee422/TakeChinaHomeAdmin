package com.dee.android.pbl.takechinahome.admin.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_tasks")
data class PendingTask(
    @PrimaryKey val orderId: Int,
    val localImagePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    var isUploading: Boolean = false
)