package com.storynest.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, PageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StoryNestDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var instance: StoryNestDatabase? = null

        fun get(context: Context): StoryNestDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StoryNestDatabase::class.java,
                    "storynest.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
