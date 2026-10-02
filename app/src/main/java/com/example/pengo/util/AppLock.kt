package com.example.pengo.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fingerprint / face / screen-lock protection when returning to the app. */
object AppLock {
    private const val RELOCK_AFTER_MS = 60_000L
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundAt = 0L

    fun isAvailable(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= 28 &&
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onAppStart(context: Context) {
        _locked.value = Prefs.settings.value.appLock && isAvailable(context)
    }

    fun onBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    fun onForeground() {
        if (Prefs.settings.value.appLock && backgroundAt > 0 &&
            System.currentTimeMillis() - backgroundAt > RELOCK_AFTER_MS
        ) _locked.value = true
    }

    fun unlockWithoutPrompt() {
        _locked.value = false
    }

    fun prompt(activity: FragmentActivity, title: String = "Unlock PeNgo", onResult: (Boolean) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _locked.value = false
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onResult(false) }
    }
}
