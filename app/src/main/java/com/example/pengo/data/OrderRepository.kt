package com.example.pengo.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import kotlin.math.roundToLong

private val ORDER_COLUMNS = Columns.raw(
    "*, stores(*), order_items(*, products(*, product_images(image_url))), exit_passes(*)"
)

enum class PaymentMethod(val key: String, val label: String) {
    UPI("upi", "UPI"),
    COUNTER("counter", "Pay at counter"),
}

object OrderRepository {

    suspend fun myOrders(): List<Order> {
        val uid = AuthRepository.currentUserId() ?: return emptyList()
        return Backend.client.from("orders")
            .select(ORDER_COLUMNS) {
                filter { eq("user_id", uid) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList<Order>()
    }

    suspend fun order(id: String): Order? =
        Backend.client.from("orders")
            .select(ORDER_COLUMNS) { filter { eq("id", id) } }
            .decodeSingleOrNull<Order>()

    /**
     * Places the cart on the server, which re-checks prices, stock and the coupon and
     * creates one unpaid order per store. Stock stays reserved for 30 minutes.
     */
    suspend fun checkout(lines: List<CartLine>, method: PaymentMethod, couponCode: String?): CheckoutResult {
        val items = buildJsonArray {
            lines.forEach { line ->
                add(buildJsonObject {
                    put("product_id", line.product.id)
                    put("quantity", line.quantity)
                })
            }
        }
        return Backend.client.postgrest.rpc("pengo_checkout", buildJsonObject {
            put("p_items", items)
            put("p_coupon", couponCode?.takeIf { it.isNotBlank() })
            put("p_method", method.key)
        }).decodeAs<CheckoutResult>()
    }

    /** Records a UPI payment for each order and returns once exit passes exist. */
    suspend fun confirmUpi(orderIds: List<String>, paymentRef: String) {
        orderIds.forEach { id ->
            Backend.client.postgrest.rpc("pengo_confirm_upi", buildJsonObject {
                put("p_order_id", id)
                put("p_ref", paymentRef)
            })
        }
    }

    suspend fun findCoupon(code: String): Coupon? {
        val coupon = Backend.client.from("coupons")
            .select { filter { eq("code", code.trim().uppercase()) } }
            .decodeSingleOrNull<Coupon>()
            ?: return null
        if (coupon.isActive == false) return null
        val expiry = coupon.expiryDate?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        if (expiry != null && expiry.isBefore(OffsetDateTime.now())) return null
        return coupon
    }

    suspend fun logScan(content: String, type: String) {
        val uid = AuthRepository.currentUserId() ?: return
        runCatching { Backend.client.from("scan_history").insert(ScanHistoryInsert(uid, content, type)) }
    }

    suspend fun scanHistory(): List<ScanHistory> {
        val uid = AuthRepository.currentUserId() ?: return emptyList()
        return Backend.client.from("scan_history")
            .select {
                filter { eq("user_id", uid) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(100)
            }
            .decodeList<ScanHistory>()
    }
}

const val ORDER_QR_PREFIX = "PENGO-ORDER:"

fun Coupon.discountFor(subtotal: Double): Double {
    val raw = subtotal * (discountPercent ?: 0) / 100.0
    val capped = maxDiscount?.let { raw.coerceAtMost(it) } ?: raw
    return (capped * 100).roundToLong() / 100.0
}
