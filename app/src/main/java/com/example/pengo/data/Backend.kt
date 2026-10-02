package com.example.pengo.data

import com.example.pengo.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.UnknownHostException

object Backend {
    const val DEEPLINK_SCHEME = "pengo"
    const val DEEPLINK_HOST = "login-callback"

    val isConfigured: Boolean
        get() = BuildConfig.PENGO_API_URL.startsWith("https://") && BuildConfig.PENGO_ANON_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.PENGO_API_URL,
            supabaseKey = BuildConfig.PENGO_ANON_KEY.ifBlank { "missing-key" },
        ) {
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    explicitNulls = false
                }
            )
            install(Auth) {
                scheme = DEEPLINK_SCHEME
                host = DEEPLINK_HOST
                flowType = FlowType.PKCE
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Storage)
        }
    }
}

/** Server functions raise "PENGO: <message>" for errors that are safe to show to the user. */
private val serverMessage = Regex("PENGO:\\s*([^\"\\n]+)")

/** Converts backend exceptions into short, user-friendly messages. */
fun Throwable.friendlyMessage(): String {
    if (!Backend.isConfigured) return "App is not connected yet. Please add the server keys in local.properties."
    serverMessage.find(message.orEmpty())?.let { return it.groupValues[1].trim().trimEnd('.', ',') }
    val raw = (message ?: "").lowercase()
    return when {
        this is UnknownHostException || this is IOException && raw.contains("unable to resolve") ->
            "No internet connection. Please check your network."
        raw.contains("invalid login credentials") -> "Incorrect email or password."
        raw.contains("email not confirmed") -> "Please verify your email first, then log in."
        raw.contains("user already registered") || raw.contains("already been registered") ->
            "An account with this email already exists. Please log in."
        raw.contains("password should be") || raw.contains("weak password") ->
            "Password is too weak. Use at least 6 characters."
        raw.contains("rate limit") || raw.contains("too many") -> "Too many attempts. Please wait a minute and try again."
        raw.contains("duplicate key") && raw.contains("phone") -> "This phone number is already linked to another account."
        raw.contains("provider is not enabled") || raw.contains("unsupported provider") ->
            "Google sign-in is not configured yet."
        raw.contains("row-level security") || raw.contains("permission denied") ->
            "You don't have permission for this action."
        raw.contains("timeout") || raw.contains("timed out") -> "Server is taking too long. Please try again."
        raw.contains("failed to connect") || raw.contains("unable to resolve host") || raw.contains("network") ->
            "No internet connection. Please check your network."
        else -> "Something went wrong. Please try again."
    }
}
