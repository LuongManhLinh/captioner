package io.captioner.data

import android.app.Application

class DatabaseContainer : Application() {
    val database: CaptionerDatabase by lazy { CaptionerDatabase.getDatabase(this) }

    companion object {
        lateinit var instance: DatabaseContainer
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}