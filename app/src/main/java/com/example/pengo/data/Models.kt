package com.example.pengo.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: Int? = 1,
)

@Serializable
data class Review(
    val id: Int,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("product_id") val productId: String? = null,
    val rating: Int = 5,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ReviewInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("product_id") val productId: String,
    val rating: Int,
    val comment: String?,
)

@Serializable
data class DemandRequestInsert(
    @SerialName("clerk_user_id") val requesterId: String,
    @SerialName("store_id") val storeId: String,
    @SerialName("product_id") val productId: String,
)

@Serializable
data class WalletTransaction(
    val id: String,
    val amount: Double = 0.0,
    val type: String? = null,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Category(
    val id: Int,
    val name: String,
    @SerialName("icon_url") val iconUrl: String? = null,
)

@Serializable
data class Store(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("category_id") val categoryId: Int? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rating: Double? = 0.0,
    @SerialName("image_url") val imageUrl: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    @SerialName("is_open") val isOpen: Boolean? = true,
    val code: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val email: String? = null,
    val status: String? = null,
) {
    val fullAddress: String
        get() = listOfNotNull(address, city, state, postalCode)
            .filter { it.isNotBlank() }
            .joinToString(", ")
}

@Serializable
data class ProductImage(
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
data class Product(
    val id: String,
    @SerialName("store_id") val storeId: String? = null,
    val name: String,
    val description: String? = null,
    val price: Double = 0.0,
    @SerialName("category_id") val categoryId: Int? = null,
    @SerialName("stock_quantity") val stockQuantity: Int? = 0,
    val rating: Double? = 0.0,
    val barcode: String? = null,
    @SerialName("barcode_image_url") val barcodeImageUrl: String? = null,
    @SerialName("product_images") val images: List<ProductImage>? = null,
) {
    val imageUrl: String?
        get() = barcodeImageUrl?.takeIf { it.isNotBlank() }
            ?: images?.firstOrNull()?.imageUrl?.takeIf { it.isNotBlank() }

    val allImages: List<String>
        get() = (listOfNotNull(barcodeImageUrl) + images.orEmpty().map { it.imageUrl })
            .filter { it.isNotBlank() }
            .distinct()

    val inStock: Boolean get() = (stockQuantity ?: 0) > 0
}

@Serializable
data class CartRow(
    val id: Int,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("product_id") val productId: String? = null,
    val quantity: Int = 1,
    @SerialName("products") val product: Product? = null,
)

@Serializable
data class CartInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Int,
)

@Serializable
data class WishlistRow(
    val id: Int,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("products") val product: Product? = null,
)

@Serializable
data class WishlistInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("product_id") val productId: String,
)

@Serializable
data class Order(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    val status: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("delivery_address") val deliveryAddress: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("store_id") val storeId: String? = null,
    @SerialName("payment_id") val paymentId: String? = null,
    @SerialName("stores") val store: Store? = null,
    @SerialName("order_items") val items: List<OrderItem>? = null,
    @SerialName("exit_passes") val exitPassesRaw: JsonElement? = null,
) {
    val isPaid: Boolean get() = paymentStatus.equals("paid", ignoreCase = true)

    /** exit_passes.order_id is unique, so the embed may arrive as an object or as an array. */
    val exitPass: ExitPass?
        get() = when (val raw = exitPassesRaw) {
            is JsonObject -> runCatching { lenientJson.decodeFromJsonElement<ExitPass>(raw) }.getOrNull()
            is JsonArray -> raw.firstOrNull()?.let { runCatching { lenientJson.decodeFromJsonElement<ExitPass>(it) }.getOrNull() }
            else -> null
        }
}

private val lenientJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

@Serializable
data class OrderInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("total_amount") val totalAmount: Double,
    val status: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("store_id") val storeId: String?,
    @SerialName("payment_id") val paymentId: String?,
    @SerialName("delivery_address") val deliveryAddress: String? = null,
)

@Serializable
data class OrderItem(
    val id: Int? = null,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("product_id") val productId: String? = null,
    val quantity: Int = 1,
    @SerialName("price_at_purchase") val priceAtPurchase: Double = 0.0,
    @SerialName("products") val product: Product? = null,
)

@Serializable
data class OrderItemInsert(
    @SerialName("order_id") val orderId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Int,
    @SerialName("price_at_purchase") val priceAtPurchase: Double,
)

@Serializable
data class ExitPass(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("store_id") val storeId: String,
    val status: String = "active",
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
) {
    val isUsed: Boolean get() = status.equals("used", true) || verifiedAt != null
    val isExpired: Boolean
        get() = !isUsed && expiresAt?.let {
            runCatching { java.time.OffsetDateTime.parse(it).isBefore(java.time.OffsetDateTime.now()) }.getOrNull()
        } == true
}

@Serializable
data class CheckoutResult(
    @SerialName("order_ids") val orderIds: List<String> = emptyList(),
    val total: Double = 0.0,
)

@Serializable
data class StaffStore(
    @SerialName("store_id") val storeId: String,
    val role: String,
    @SerialName("store_name") val storeName: String? = null,
    @SerialName("store_code") val storeCode: String? = null,
) {
    val isManager: Boolean get() = role == "manager"
    val canCollectPayment: Boolean get() = role == "manager" || role == "cashier"
}

@Serializable
data class StaffMember(
    @SerialName("user_id") val userId: String,
    val role: String,
    val name: String? = null,
    val email: String? = null,
)

@Serializable
data class ScanItem(
    val name: String,
    val quantity: Int = 1,
    val price: Double = 0.0,
    val image: String? = null,
)

@Serializable
data class ScanResult(
    val ok: Boolean? = null,
    val kind: String? = null,
    val verdict: String,
    @SerialName("pass_id") val passId: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    val total: Double = 0.0,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("payment_ref") val paymentRef: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val customer: String? = null,
    @SerialName("store_name") val storeName: String? = null,
    val items: List<ScanItem> = emptyList(),
)

@Serializable
data class DayTotal(val day: String, val total: Double = 0.0)

@Serializable
data class ProductStat(
    val id: String,
    val name: String,
    val qty: Int = 0,
    val revenue: Double = 0.0,
    val stock: Int = 0,
    val n: Int = 0,
)

@Serializable
data class RecentOrder(
    val id: String,
    val total: Double = 0.0,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val customer: String? = null,
)

@Serializable
data class StoreDashboard(
    @SerialName("today_sales") val todaySales: Double = 0.0,
    @SerialName("today_orders") val todayOrders: Int = 0,
    @SerialName("week_sales") val weekSales: Double = 0.0,
    @SerialName("month_sales") val monthSales: Double = 0.0,
    @SerialName("month_orders") val monthOrders: Int = 0,
    @SerialName("customers_30d") val customers30d: Int = 0,
    @SerialName("pending_orders") val pendingOrders: Int = 0,
    val daily: List<DayTotal> = emptyList(),
    @SerialName("top_products") val topProducts: List<ProductStat> = emptyList(),
    @SerialName("low_stock") val lowStock: List<ProductStat> = emptyList(),
    @SerialName("dead_stock") val deadStock: List<ProductStat> = emptyList(),
    val requests: List<ProductStat> = emptyList(),
    @SerialName("recent_orders") val recentOrders: List<RecentOrder> = emptyList(),
)

@Serializable
data class ProductUpsert(
    @SerialName("store_id") val storeId: String,
    val name: String,
    val description: String?,
    val price: Double,
    @SerialName("category_id") val categoryId: Int?,
    @SerialName("stock_quantity") val stockQuantity: Int,
    val barcode: String?,
    @SerialName("barcode_image_url") val barcodeImageUrl: String?,
)

@Serializable
data class Wallet(
    @SerialName("user_id") val userId: String,
    val balance: Double? = 0.0,
    @SerialName("reward_points") val rewardPoints: Int? = 0,
)

@Serializable
data class Coupon(
    val id: Int,
    val code: String,
    @SerialName("discount_percent") val discountPercent: Int? = 0,
    @SerialName("max_discount") val maxDiscount: Double? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("is_active") val isActive: Boolean? = true,
)

@Serializable
data class ScanHistoryInsert(
    @SerialName("user_id") val userId: String,
    val content: String,
    val type: String,
)

@Serializable
data class ScanHistory(
    val id: Int,
    val content: String,
    val type: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

data class CartLine(
    val rowId: Int,
    val product: Product,
    val quantity: Int,
) {
    val lineTotal: Double get() = product.price * quantity
}

data class StoreWithDistance(
    val store: Store,
    val distanceKm: Double,
)
