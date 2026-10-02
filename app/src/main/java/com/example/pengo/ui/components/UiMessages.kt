package com.example.pengo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.pengo.data.CartRepository
import com.example.pengo.data.Product
import com.example.pengo.data.ShoppingListRepository
import com.example.pengo.data.WishlistRepository
import com.example.pengo.data.friendlyMessage
import com.example.pengo.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

object UiMessages {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

class ProductActions(
    private val scope: CoroutineScope,
    private val haptic: () -> Unit,
) {
    fun addToCart(product: Product, quantity: Int = 1) {
        if (!product.inStock) {
            UiMessages.show("${product.name} is out of stock")
            return
        }
        haptic()
        scope.launch {
            runCatching { CartRepository.add(product, quantity) }
                .onSuccess {
                    val ticked = ShoppingListRepository.checkOffFor(product.name)
                    UiMessages.show("${product.name} added to cart" + if (ticked) " • ticked off your list" else "")
                }
                .onFailure { UiMessages.show(it.friendlyMessage()) }
        }
    }

    fun changeQuantity(productId: String, quantity: Int) {
        val line = CartRepository.lines.value.firstOrNull { it.product.id == productId } ?: return
        haptic()
        scope.launch {
            runCatching { CartRepository.setQuantity(line, quantity) }
                .onFailure {
                    UiMessages.show(it.friendlyMessage())
                    runCatching { CartRepository.refresh() }
                }
        }
    }

    fun toggleWishlist(product: Product) {
        haptic()
        val saving = product.id !in WishlistRepository.ids.value
        scope.launch {
            runCatching { WishlistRepository.toggle(product.id) }
                .onSuccess { UiMessages.show(if (saving) "Saved to wishlist" else "Removed from wishlist") }
                .onFailure { UiMessages.show(it.friendlyMessage()) }
        }
    }
}

@Composable
fun rememberProductActions(): ProductActions {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    return remember(scope, haptic) {
        ProductActions(scope) {
            if (Prefs.settings.value.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}
