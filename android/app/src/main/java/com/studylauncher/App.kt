package com.studylauncher

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Runs for every process start (activity, service, receiver), so all entry points are initialised.
        Store.init(this)
        Clock.init(this)
        Notifier.ensureChannels(this)
        Stats.load()
        Tasks.load()
        Pomodoro.load()
    }
}
