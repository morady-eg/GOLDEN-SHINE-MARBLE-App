package com.goldenshine.marble.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BusinessDocumentEntity::class], version = 1, exportSchema = true)
abstract class GoldenShineDatabase : RoomDatabase() {
    abstract fun documents(): BusinessDocumentDao

    companion object {
        @Volatile private var INSTANCE: GoldenShineDatabase? = null
        fun get(context: Context): GoldenShineDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GoldenShineDatabase::class.java,
                    "golden_shine_marble.db"
                ).build().also { INSTANCE = it }
            }
    }
}
