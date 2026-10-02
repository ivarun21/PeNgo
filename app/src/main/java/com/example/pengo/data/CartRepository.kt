package com.example.pengo.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CartRepository {
    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    private val mutex = Mutex()

    suspend fun refresh() = mutex.withLock {
        val uid = AuthRepository.currentUserId() ?: return@withLock
        _lines.value = Backend.client.from("cart")
            .select(Columns.raw("id, user_id, product_id, quantity, products(*, product_images(image_url))")) {
                filter { eq("user_id", uid) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
            .decodeList<CartRow>()
            .mapNotNull { row -> row.product?.let { CartLine(row.id, it, row.quantity) } }
    }

    fun quantityOf(productId: String): Int =
        _lines.value.firstOrNull { it.product.id == productId }?.quantity ?: 0

    suspend fun add(product: Product, quantity: Int = 1) {
        val existing = _lines.value.firstOrNull { it.product.id == product.id }
        if (existing != null) {
            setQuantity(existing, existing.quantity + quantity)
            return
        }
        val uid = AuthRepository.requireUserId()
        Backend.client.from("cart").insert(CartInsert(uid, product.id, quantity))
        refresh()
    }

    suspend fun setQuantity(line: CartLine, quantity: Int) {
        if (quantity <= 0) {
            remove(line)
            return
        }
        val stock = line.product.stockQuantity ?: 0
        val safeQty = if (stock > 0) quantity.coerceAtMost(stock) else quantity
        _lines.value = _lines.value.map { if (it.rowId == line.rowId) it.copy(quantity = safeQty) else it }
        Backend.client.from("cart").update({ set("quantity", safeQty) }) {
            filter { eq("id", line.rowId) }
        }
    }

    suspend fun remove(line: CartLine) {
        _lines.value = _lines.value.filterNot { it.rowId == line.rowId }
        Backend.client.from("cart").delete { filter { eq("id", line.rowId) } }
    }

    suspend fun clear() {
        val uid = AuthRepository.currentUserId() ?: return
        Backend.client.from("cart").delete { filter { eq("user_id", uid) } }
        _lines.value = emptyList()
    }

    fun reset() {
        _lines.value = emptyList()
    }
}
