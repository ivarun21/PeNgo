package com.example.pengo.data

import io.github.jan.supabase.postgrest.from

object ProfileRepository {
    suspend fun getProfile(uid: String): Profile? =
        Backend.client.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<Profile>()

    suspend fun myProfile(): Profile? = AuthRepository.currentUserId()?.let { getProfile(it) }

    suspend fun updateProfile(fullName: String, phone: String) {
        val uid = AuthRepository.requireUserId()
        Backend.client.from("profiles").update({
            set("full_name", fullName.trim())
            set("phone_number", normalizePhone(phone))
            set("updated_at", java.time.OffsetDateTime.now().toString())
        }) {
            filter { eq("id", uid) }
        }
    }

    suspend fun transactions(): List<WalletTransaction> {
        val uid = AuthRepository.currentUserId() ?: return emptyList()
        return Backend.client.from("transactions")
            .select {
                filter { eq("user_id", uid) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(100)
            }
            .decodeList<WalletTransaction>()
    }

    suspend fun wallet(): Wallet? {
        val uid = AuthRepository.currentUserId() ?: return null
        return runCatching {
            Backend.client.from("wallet")
                .select { filter { eq("user_id", uid) } }
                .decodeSingleOrNull<Wallet>()
        }.getOrNull()
    }
}
