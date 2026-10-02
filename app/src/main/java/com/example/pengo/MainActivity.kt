package com.example.pengo

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.example.pengo.data.Backend
import com.example.pengo.ui.PengoNavHost
import com.example.pengo.ui.theme.PengoTheme
import com.example.pengo.util.AppLock
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) AppLock.onAppStart(this)
        handleAuthDeeplink(intent)
        setContent {
            PengoTheme {
                PengoNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.onBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
    }

    private fun handleAuthDeeplink(intent: Intent?) {
        if (intent?.data?.scheme == Backend.DEEPLINK_SCHEME && Backend.isConfigured) {
            runCatching { Backend.client.handleDeeplinks(intent) }
        }
    }
}
