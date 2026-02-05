package com.dee.android.pbl.takechinahome.admin.data.db

import androidx.room.*
import com.dee.android.pbl.takechinahome.admin.data.model.PendingTask

@Dao
interface PendingTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: PendingTask)

    @Query("SELECT * FROM pending_tasks ORDER BY createdAt ASC")
    suspend fun getAllTasks(): List<PendingTask>

    @Delete
    suspend fun deleteTask(task: PendingTask)

    @Query("SELECT COUNT(*) FROM pending_tasks")
    fun getCount(): kotlinx.coroutines.flow.Flow<Int>
}