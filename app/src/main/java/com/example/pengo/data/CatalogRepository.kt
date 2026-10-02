package com.example.pengo.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val PRODUCT_COLUMNS = Columns.raw("*, product_images(image_url)")

object CatalogRepository {
    private val storeCache = mutableMapOf<String, Store>()
    private val storeProductsCache = mutableMapOf<String, List<Product>>()

    fun cachedStore(id: String): Store? = storeCache[id]
    fun cachedStoreProducts(storeId: String): List<Product>? = storeProductsCache[storeId]

    suspend fun categories(): List<Category> =
        Backend.client.from("categories").select().decodeList<Category>()

    suspend fun featuredProducts(limit: Long = 30): List<Product> =
        Backend.client.from("products")
            .select(PRODUCT_COLUMNS) {
                order("rating", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(limit)
            }
            .decodeList<Product>()

    suspend fun productsByCategory(categoryId: Int): List<Product> =
        Backend.client.from("products")
            .select(PRODUCT_COLUMNS) {
                filter { eq("category_id", categoryId) }
                limit(50)
            }
            .decodeList<Product>()

    suspend fun searchProducts(query: String): List<Product> =
        Backend.client.from("products")
            .select(PRODUCT_COLUMNS) {
                filter { ilike("name", "%${query.trim()}%") }
                limit(50)
            }
            .decodeList<Product>()

    suspend fun product(id: String): Product? =
        Backend.client.from("products")
            .select(PRODUCT_COLUMNS) { filter { eq("id", id) } }
            .decodeSingleOrNull<Product>()

    suspend fun productsForStore(storeId: String): List<Product> {
        val list = Backend.client.from("products")
            .select(PRODUCT_COLUMNS) { filter { eq("store_id", storeId) } }
            .decodeList<Product>()
            .sortedWith(compareByDescending<Product> { it.inStock }.thenBy { it.name.lowercase() })
        storeProductsCache[storeId] = list
        return list
    }

    suspend fun productByBarcode(barcode: String, storeId: String?): Product? {
        val code = barcode.trim()
        return Backend.client.from("products")
            .select(PRODUCT_COLUMNS) {
                filter {
                    eq("barcode", code)
                    if (storeId != null) eq("store_id", storeId)
                }
                limit(1)
            }
            .decodeList<Product>()
            .firstOrNull()
    }

    suspend fun store(id: String): Store? {
        storeCache[id]?.let { return it }
        return Backend.client.from("stores")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Store>()
            ?.also { storeCache[it.id] = it }
    }

    suspend fun storesByIds(ids: Collection<String>): List<Store> {
        if (ids.isEmpty()) return emptyList()
        val missing = ids.filter { it !in storeCache }
        if (missing.isNotEmpty()) {
            Backend.client.from("stores")
                .select { filter { isIn("id", missing) } }
                .decodeList<Store>()
                .forEach { storeCache[it.id] = it }
        }
        return ids.mapNotNull { storeCache[it] }
    }

    /**
     * Store QR codes may contain the store UUID, the store `code`, a pengo:// link
     * or a URL ending with the id. All of them are resolved here.
     */
    suspend fun storeFromQr(raw: String): Store? {
        val text = raw.trim()
        val candidate = when {
            text.startsWith("{") -> Regex("\"(?:store_id|id|code)\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            text.contains("/") -> text.trimEnd('/').substringAfterLast('/').substringBefore('?')
            text.contains("=") -> text.substringAfterLast('=')
            else -> text
        } ?: text

        if (UUID_REGEX.matches(candidate)) {
            store(candidate)?.let { return it }
        }
        return Backend.client.from("stores")
            .select { filter { eq("code", candidate) } }
            .decodeList<Store>()
            .firstOrNull()
            ?.also { storeCache[it.id] = it }
    }

    /**
     * Pre-filters with a bounding box on the server, then keeps only stores whose
     * real (haversine) distance is within [radiusKm].
     */
    suspend fun nearbyStores(lat: Double, lng: Double, radiusKm: Double = 5.0): List<StoreWithDistance> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        val stores = Backend.client.from("stores")
            .select {
                filter {
                    gte("latitude", lat - latDelta)
                    lte("latitude", lat + latDelta)
                    gte("longitude", lng - lngDelta)
                    lte("longitude", lng + lngDelta)
                }
            }
            .decodeList<Store>()
        stores.forEach { storeCache[it.id] = it }
        return stores
            .filter { it.status == null || it.status == "active" }
            .mapNotNull { s ->
                val sLat = s.latitude ?: return@mapNotNull null
                val sLng = s.longitude ?: return@mapNotNull null
                StoreWithDistance(s, haversineKm(lat, lng, sLat, sLng))
            }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
    }

    suspend fun reviews(productId: String): List<Review> =
        Backend.client.from("reviews")
            .select {
                filter { eq("product_id", productId) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(30)
            }
            .decodeList<Review>()

    /** One review per user per product: an existing review is replaced. */
    suspend fun submitReview(productId: String, rating: Int, comment: String) {
        val uid = AuthRepository.requireUserId()
        Backend.client.from("reviews").delete {
            filter {
                eq("user_id", uid)
                eq("product_id", productId)
            }
        }
        Backend.client.from("reviews").insert(
            ReviewInsert(uid, productId, rating.coerceIn(1, 5), comment.trim().ifBlank { null })
        )
    }

    suspend fun requestRestock(product: Product) {
        val uid = AuthRepository.requireUserId()
        val storeId = product.storeId ?: error("This product isn't linked to a store.")
        Backend.client.from("demand_requests").insert(DemandRequestInsert(uid, storeId, product.id))
    }

    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}

fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}
