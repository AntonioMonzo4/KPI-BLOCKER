package com.kpiblocker.app

import android.app.Application
import com.kpiblocker.app.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
