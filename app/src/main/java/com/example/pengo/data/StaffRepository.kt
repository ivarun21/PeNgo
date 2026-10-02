package com.example.pengo.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

object StaffRepository {
    private val _stores = MutableStateFlow<List<StaffStore>>(emptyList())
    val stores: StateFlow<List<StaffStore>> = _stores.asStateFlow()

    suspend fun refreshStores(): List<StaffStore> {
        if (AuthRepository.currentUserId() == null) return emptyList()
        val list = runCatching {
            Backend.client.postgrest.rpc("pengo_my_staff_stores").decodeAs<List<StaffStore>>()
        }.getOrDefault(_stores.value)
        _stores.value = list
        return list
    }

    fun reset() {
        _stores.value = emptyList()
    }

    suspend fun lookup(code: String): ScanResult =
        Backend.client.postgrest.rpc("pengo_staff_lookup", buildJsonObject { put("p_code", code.trim()) })
            .decodeAs<ScanResult>()

    suspend fun allowExit(passId: String): ScanResult =
        Backend.client.postgrest.rpc("pengo_staff_verify_exit", buildJsonObject { put("p_pass_id", passId) })
            .decodeAs<ScanResult>()

    suspend fun collectPayment(orderId: String, method: String) {
        Backend.client.postgrest.rpc("pengo_counter_mark_paid", buildJsonObject {
            put("p_order_id", orderId)
            put("p_method", method)
        })
    }

    suspend fun dashboard(storeId: String): StoreDashboard =
        Backend.client.postgrest.rpc("pengo_store_dashboard", buildJsonObject { put("p_store_id", storeId) })
            .decodeAs<StoreDashboard>()

    suspend fun team(storeId: String): List<StaffMember> =
        Backend.client.postgrest.rpc("pengo_list_staff", buildJsonObject { put("p_store_id", storeId) })
            .decodeAs<List<StaffMember>>()

    suspend fun addMember(storeId: String, email: String, role: String) {
        Backend.client.postgrest.rpc("pengo_add_staff", buildJsonObject {
            put("p_store_id", storeId)
            put("p_email", email.trim())
            put("p_role", role)
        })
    }

    suspend fun removeMember(storeId: String, userId: String) {
        Backend.client.postgrest.rpc("pengo_remove_staff", buildJsonObject {
            put("p_store_id", storeId)
            put("p_user_id", userId)
        })
    }

    suspend fun inventory(storeId: String): List<Product> =
        Backend.client.from("products")
            .select(Columns.raw("*, product_images(image_url)")) {
                filter { eq("store_id", storeId) }
                order("name", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }
            .decodeList<Product>()

    suspend fun saveProduct(productId: String?, data: ProductUpsert) {
        if (productId == null) {
            Backend.client.from("products").insert(data)
        } else {
            Backend.client.from("products").update(data) { filter { eq("id", productId) } }
        }
    }

    suspend fun setStock(productId: String, stock: Int) {
        Backend.client.from("products").update({ set("stock_quantity", stock.coerceAtLeast(0)) }) {
            filter { eq("id", productId) }
        }
    }

    suspend fun deleteProduct(productId: String) {
        Backend.client.from("products").delete { filter { eq("id", productId) } }
    }

    /** Uploads a JPEG to the public product-images bucket and returns its URL. */
    suspend fun uploadProductPhoto(storeId: String, jpeg: ByteArray): String {
        val bucket = Backend.client.storage.from("product-images")
        val path = "$storeId/${UUID.randomUUID()}.jpg"
        bucket.upload(path, jpeg) { upsert = false }
        return bucket.publicUrl(path)
    }
}
