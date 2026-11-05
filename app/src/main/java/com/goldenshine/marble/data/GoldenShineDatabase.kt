package com.goldenshine.marble.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BusinessDocumentEntity::class], version = 2, exportSchema = true)
abstract class GoldenShineDatabase : RoomDatabase() {
    abstract fun documents(): BusinessDocumentDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_documents ADD COLUMN quotationMetaJson TEXT NOT NULL DEFAULT '{}' ")
            }
        }

        @Volatile private var INSTANCE: GoldenShineDatabase? = null
        fun get(context: Context): GoldenShineDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GoldenShineDatabase::class.java,
                    "golden_shine_marble.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
