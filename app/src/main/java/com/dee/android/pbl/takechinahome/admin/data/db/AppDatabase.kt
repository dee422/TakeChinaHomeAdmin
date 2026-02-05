package com.dee.android.pbl.takechinahome.admin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dee.android.pbl.takechinahome.admin.data.model.PendingTask

@Database(entities = [PendingTask::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingTaskDao(): PendingTaskDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "take_china_home_admin.db"
                ).build().also { instance = it }
            }
    }
}