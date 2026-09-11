package com.storynest.android

import android.app.Application
import com.storynest.android.data.local.BookFileStore
import com.storynest.android.data.local.StoryNestDatabase
import com.storynest.android.data.prefs.SettingsRepository
import com.storynest.android.data.repository.BookRepository

class StoryNestApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var books: BookRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        val db = StoryNestDatabase.get(this)
        books = BookRepository(
            dao = db.bookDao(),
            files = BookFileStore(this),
            settings = settings
        )
    }
}
