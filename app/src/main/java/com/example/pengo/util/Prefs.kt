package com.example.pengo.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val haptics: Boolean = true,
    val scanSound: Boolean = true,
    val orderNotifications: Boolean = true,
    val offerNotifications: Boolean = false,
    val searchRadiusKm: Int = 5,
    val appLock: Boolean = false,
)

object Prefs {
    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("pengo_settings", Context.MODE_PRIVATE)
        _settings.value = AppSettings(
            haptics = prefs.getBoolean("haptics", true),
            scanSound = prefs.getBoolean("scan_sound", true),
            orderNotifications = prefs.getBoolean("order_notifications", true),
            offerNotifications = prefs.getBoolean("offer_notifications", false),
            searchRadiusKm = prefs.getInt("search_radius_km", 5),
            appLock = prefs.getBoolean("app_lock", false),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putBoolean("haptics", s.haptics)
            .putBoolean("scan_sound", s.scanSound)
            .putBoolean("order_notifications", s.orderNotifications)
            .putBoolean("offer_notifications", s.offerNotifications)
            .putInt("search_radius_km", s.searchRadiusKm)
            .putBoolean("app_lock", s.appLock)
            .apply()
    }

    fun flag(key: String): Boolean = prefs.getBoolean(key, false)

    fun setFlag(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }
}
