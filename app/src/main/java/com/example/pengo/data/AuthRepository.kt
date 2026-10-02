package com.example.pengo.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface AuthDestination {
    data object Login : AuthDestination
    data object ProfileSetup : AuthDestination
    data object Home : AuthDestination
}

sealed interface SignUpResult {
    data object LoggedIn : SignUpResult
    data object VerifyEmail : SignUpResult
}

@Serializable
private data class ProfileUpsert(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String,
    val email: String?,
)

/** Stores Indian numbers as 10 digits so the unique phone_number constraint isn't bypassed by formatting. */
fun normalizePhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length == 12 && digits.startsWith("91")) digits.takeLast(10)
    else if (digits.length == 11 && digits.startsWith("0")) digits.takeLast(10)
    else digits
}

object AuthRepository {
    private val auth get() = Backend.client.auth

    val sessionStatus get() = auth.sessionStatus

    fun currentUserId(): String? = if (Backend.isConfigured) auth.currentUserOrNull()?.id else null

    fun currentEmail(): String? = auth.currentUserOrNull()?.email

    fun requireUserId(): String = currentUserId() ?: error("Please log in again.")

    /** Waits for the stored session to load, then decides where the user should land. */
    suspend fun resolveStartDestination(): AuthDestination {
        if (!Backend.isConfigured) return AuthDestination.Login
        val status = auth.sessionStatus.first { it !is SessionStatus.Initializing }
        if (status !is SessionStatus.Authenticated) return AuthDestination.Login
        return destinationForCurrentUser()
    }

    suspend fun destinationForCurrentUser(): AuthDestination {
        val uid = currentUserId() ?: return AuthDestination.Login
        return if (ProfileRepository.getProfile(uid) != null) AuthDestination.Home else AuthDestination.ProfileSetup
    }

    suspend fun signIn(email: String, password: String): AuthDestination {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return destinationForCurrentUser()
    }

    suspend fun signUp(fullName: String, email: String, phone: String, password: String): SignUpResult {
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("full_name", fullName.trim())
                put("phone_number", phone.trim())
            }
        }
        val uid = currentUserId() ?: return SignUpResult.VerifyEmail
        saveProfile(uid, fullName, phone, email)
        return SignUpResult.LoggedIn
    }

    suspend fun signInWithGoogle() {
        auth.signInWith(Google)
    }

    /** Pre-fill values for the profile setup screen from the signup metadata. */
    fun pendingProfileDefaults(): Pair<String, String> {
        val meta = auth.currentUserOrNull()?.userMetadata
        val name = meta?.get("full_name")?.jsonPrimitive?.content
            ?: meta?.get("name")?.jsonPrimitive?.content
            ?: ""
        val phone = meta?.get("phone_number")?.jsonPrimitive?.content ?: ""
        return name to phone
    }

    suspend fun saveProfile(uid: String, fullName: String, phone: String, email: String?) {
        Backend.client.from("profiles").upsert(
            ProfileUpsert(
                id = uid,
                fullName = fullName.trim(),
                phoneNumber = normalizePhone(phone),
                email = email?.trim()?.ifBlank { null } ?: currentEmail(),
            )
        )
    }

    suspend fun signOut() {
        runCatching { auth.signOut() }
        auth.clearSession()
        CartRepository.reset()
        WishlistRepository.reset()
        StaffRepository.reset()
    }
}
