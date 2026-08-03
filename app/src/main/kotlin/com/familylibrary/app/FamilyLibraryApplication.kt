package com.familylibrary.app

import android.app.Application

class FamilyLibraryApplication : Application() {
    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        serviceLocator = ServiceLocator(this)
    }

    companion object {
        fun from(app: Application): FamilyLibraryApplication =
            app as FamilyLibraryApplication
    }
}
