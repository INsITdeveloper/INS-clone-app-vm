package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CloneAppEntity::class, IpcHookLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VirtualSpaceDatabase : RoomDatabase() {
    abstract fun virtualSpaceDao(): VirtualSpaceDao

    companion object {
        @Volatile
        private var INSTANCE: VirtualSpaceDatabase? = null

        fun getInstance(context: Context): VirtualSpaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VirtualSpaceDatabase::class.java,
                    "ins_virtual_space_engine.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
