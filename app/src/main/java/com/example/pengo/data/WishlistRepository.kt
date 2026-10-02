package com.example.pengo.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WishlistRepository {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    suspend fun refreshIds() {
        val uid = AuthRepository.currentUserId() ?: return
        _ids.value = Backend.client.from("wishlist")
            .select(Columns.list("id", "product_id")) { filter { eq("user_id", uid) } }
            .decodeList<WishlistRow>()
            .mapNotNull { it.productId }
            .toSet()
    }

    suspend fun products(): List<Product> {
        val uid = AuthRepository.currentUserId() ?: return emptyList()
        val rows = Backend.client.from("wishlist")
            .select(Columns.raw("id, product_id, products(*, product_images(image_url))")) {
                filter { eq("user_id", uid) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList<WishlistRow>()
        _ids.value = rows.mapNotNull { it.productId }.toSet()
        return rows.mapNotNull { it.product }
    }

    /** Optimistically flips the heart, rolling back if the request fails. */
    suspend fun toggle(productId: String) {
        val uid = AuthRepository.requireUserId()
        val wasSaved = productId in _ids.value
        _ids.value = if (wasSaved) _ids.value - productId else _ids.value + productId
        try {
            if (wasSaved) {
                Backend.client.from("wishlist").delete {
                    filter {
                        eq("user_id", uid)
                        eq("product_id", productId)
                    }
                }
            } else {
                Backend.client.from("wishlist").insert(WishlistInsert(uid, productId))
            }
        } catch (e: Exception) {
            _ids.value = if (wasSaved) _ids.value + productId else _ids.value - productId
            throw e
        }
    }

    fun reset() {
        _ids.value = emptySet()
    }
}
