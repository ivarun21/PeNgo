package com.example.pengo.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ShoppingItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
)

/** Shopping list kept on the device, so it works offline. */
object ShoppingListRepository {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val items: StateFlow<List<ShoppingItem>> = _items.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("pengo_shopping_list", Context.MODE_PRIVATE)
        _items.value = prefs.getString("items", null)
            ?.let { runCatching { json.decodeFromString<List<ShoppingItem>>(it) }.getOrNull() }
            .orEmpty()
    }

    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (_items.value.any { it.text.equals(clean, ignoreCase = true) && !it.done }) return
        save(_items.value + ShoppingItem(System.currentTimeMillis(), clean))
    }

    fun toggle(id: Long) = save(_items.value.map { if (it.id == id) it.copy(done = !it.done) else it })

    fun remove(id: Long) = save(_items.value.filterNot { it.id == id })

    fun clearDone() = save(_items.value.filterNot { it.done })

    /** Ticks off list items that match a product the user just added to the cart. */
    fun checkOffFor(productName: String): Boolean {
        val name = productName.lowercase()
        var changed = false
        val updated = _items.value.map { item ->
            if (item.done) return@map item
            val words = item.text.lowercase().split(' ').filter { it.length >= 3 }
            val matches = name.contains(item.text.lowercase()) || (words.isNotEmpty() && words.all { name.contains(it) })
            if (matches) {
                changed = true
                item.copy(done = true)
            } else item
        }
        if (changed) save(updated)
        return changed
    }

    private fun save(list: List<ShoppingItem>) {
        _items.value = list
        if (::prefs.isInitialized) prefs.edit().putString("items", json.encodeToString(list)).apply()
    }
}
