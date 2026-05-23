package com.aliahad.brainrotop.analytics

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScreenTimeSessionEntity::class,
        BlockEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BrainrotopDatabase : RoomDatabase() {
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        @Volatile
        private var instance: BrainrotopDatabase? = null

        fun get(context: Context): BrainrotopDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrainrotopDatabase::class.java,
                    "brainrotop_analytics.db",
                ).build().also { instance = it }
            }
    }
}
