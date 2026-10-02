package com.example.pengo

import android.app.Application
import com.example.pengo.data.ShoppingListRepository
import com.example.pengo.util.NetworkMonitor
import com.example.pengo.util.Notifier
import com.example.pengo.util.Prefs
import org.maplibre.android.MapLibre

class PengoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ShoppingListRepository.init(this)
        NetworkMonitor.init(this)
        Notifier.init(this)
        MapLibre.getInstance(this)
    }
}
