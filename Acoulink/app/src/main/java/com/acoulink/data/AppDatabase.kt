package com.acoulink.data

import android.content.Context

/**
 * Main Database manager for local offline persistence in AcouLink.
 * Uses native SQLite implementation ensuring 100% startup reliability on Android devices.
 */
class AppDatabase private constructor(private val dao: MessageDao) {

    fun messageDao(): MessageDao = dao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = AppDatabase(SQLiteMessageDao(context.applicationContext))
                INSTANCE = instance
                instance
            }
        }
    }
}
