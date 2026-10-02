package com.example.pengo.ui.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.pengo.data.CartRepository
import com.example.pengo.data.CatalogRepository
import com.example.pengo.data.Product
import com.example.pengo.data.Store
import com.example.pengo.data.WishlistRepository
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.SCAN_MODE_PRODUCT
import com.example.pengo.ui.SCAN_MODE_STORE
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientCard
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.ProductCard
import com.example.pengo.ui.components.ProductImage
import com.example.pengo.ui.components.RatingLabel
import com.example.pengo.ui.components.ShimmerBox
import com.example.pengo.ui.components.StatusChip
import com.example.pengo.ui.components.rememberProductActions
import com.example.pengo.ui.main.TAB_CART
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.rupees
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreState(
    val store: Store? = null,
    val products: List<Product> = emptyList(),
    val loadingStore: Boolean = true,
    val loadingProducts: Boolean = true,
    val error: String? = null,
    val notFound: Boolean = false,
)

class StoreViewModel : ViewModel() {
    private val _state = MutableStateFlow(StoreState())
    val state = _state.asStateFlow()
    private var started = false

    fun start(storeId: String?, qr: String?) {
        if (started) return
        started = true
        val cachedStore = storeId?.let { CatalogRepository.cachedStore(it) }
        if (cachedStore != null) {
            _state.update {
                it.copy(
                    store = cachedStore,
                    loadingStore = false,
                    products = CatalogRepository.cachedStoreProducts(cachedStore.id).orEmpty(),
                )
            }
        }
        load(storeId, qr)
    }

    fun retry(storeId: String?, qr: String?) {
        _state.update { it.copy(error = null, loadingStore = it.store == null, loadingProducts = true) }
        load(storeId, qr)
    }

    private fun load(storeId: String?, qr: String?) {
        viewModelScope.launch {
            try {
                val store = _state.value.store
                    ?: storeId?.let { CatalogRepository.store(it) }
                    ?: qr?.let { CatalogRepository.storeFromQr(it) }
                if (store == null) {
                    _state.update { it.copy(loadingStore = false, loadingProducts = false, notFound = true) }
                    return@launch
                }
                _state.update {
                    it.copy(
                        store = store,
                        loadingStore = false,
                        products = it.products.ifEmpty { CatalogRepository.cachedStoreProducts(store.id).orEmpty() },
                    )
                }
                val products = CatalogRepository.productsForStore(store.id)
                _state.update { it.copy(products = products, loadingProducts = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loadingStore = false, loadingProducts = false, error = e.friendlyMessage()) }
            }
        }
    }
}

@Composable
fun StoreScreen(nav: NavHostController, storeId: String?, qr: String?, vm: StoreViewModel = viewModel()) {
    remember(storeId, qr) { vm.start(storeId, qr); true }
    val state by vm.state.collectAsStateWithLifecycle()
    val wishlist by WishlistRepository.ids.collectAsStateWithLifecycle()
    val cart by CartRepository.lines.collectAsStateWithLifecycle()
    val actions = rememberProductActions()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val store = state.store
    val visible = remember(state.products, query) {
        if (query.isBlank()) state.products
        else state.products.filter { it.name.contains(query.trim(), ignoreCase = true) || it.barcode == query.trim() }
    }

    PengoBackground {
        when {
            state.notFound -> Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Rounded.Storefront,
                    title = "Store not found",
                    message = "This QR code doesn't belong to a PeNgo store. Try scanning again.",
                    actionText = "Scan again",
                    onAction = {
                        nav.navigate(Routes.Scanner(SCAN_MODE_STORE)) { popUpTo<Routes.Store> { inclusive = true } }
                    },
                )
            }
            state.error != null && store == null -> Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Rounded.Storefront,
                    title = "Couldn't open store",
                    message = state.error.orEmpty(),
                    actionText = "Retry",
                    onAction = { vm.retry(storeId, qr) },
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(2) }) {
                    StoreHeader(store, state.loadingStore, onBack = { nav.popBackStack() }, context = context)
                }
                item(span = { GridItemSpan(2) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search in this store") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PengoColors.Muted) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = PengoColors.Blue,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(20.dp), spotColor = Color(0x1A64A1FF)),
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    Text(
                        if (state.loadingProducts && state.products.isEmpty()) "Loading products…"
                        else "${visible.size} products available",
                        style = MaterialTheme.typography.titleMedium,
                        color = PengoColors.Ink,
                    )
                }
                if (state.loadingProducts && state.products.isEmpty()) {
                    items(6) { ShimmerBox(Modifier.fillMaxWidth().aspectRatio(0.68f), corner = 22.dp) }
                } else if (visible.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        EmptyState(
                            icon = Icons.Rounded.Inventory2,
                            title = if (query.isBlank()) "No products listed" else "No match",
                            message = if (query.isBlank()) "This store hasn't added products yet." else "Try a different name or scan the barcode.",
                        )
                    }
                } else {
                    items(visible, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            saved = product.id in wishlist,
                            cartQuantity = cart.firstOrNull { it.product.id == product.id }?.quantity ?: 0,
                            onClick = { nav.navigate(Routes.Product(product.id)) },
                            onToggleWishlist = { actions.toggleWishlist(product) },
                            onAdd = { actions.addToCart(product) },
                            onQuantityChange = { actions.changeQuantity(product.id, it) },
                        )
                    }
                }
            }
        }

        if (store != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.Scanner(SCAN_MODE_PRODUCT, store.id)) },
                    containerColor = Color.White,
                    contentColor = PengoColors.Ink,
                    shape = RoundedCornerShape(20.dp),
                    icon = { Icon(Icons.Rounded.ViewWeek, null, tint = PengoColors.PrimaryDeep) },
                    text = { Text("Scan barcode") },
                )
                val count = cart.sumOf { it.quantity }
                if (count > 0) {
                    Spacer(Modifier.height(12.dp))
                    GradientCard(
                        PengoGradients.primary,
                        Modifier.fillMaxWidth().height(62.dp),
                        corner = 22.dp,
                        onClick = {
                            nav.navigate(Routes.Main(TAB_CART)) { popUpTo<Routes.Main> { inclusive = true } }
                        },
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("$count item${if (count > 1) "s" else ""}", style = MaterialTheme.typography.labelMedium, color = PengoColors.Ink.copy(alpha = 0.7f))
                                Text(cart.sumOf { it.lineTotal }.rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PengoColors.Ink)
                            }
                            Text("View cart", style = MaterialTheme.typography.labelLarge, color = PengoColors.Ink)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = PengoColors.Ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreHeader(store: Store?, loading: Boolean, onBack: () -> Unit, context: Context) {
    Column(Modifier.statusBarsPadding()) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            if (store?.imageUrl.isNullOrBlank()) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(PengoGradients.lavender)) {
                    Icon(
                        Icons.Rounded.Storefront,
                        null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(72.dp).align(Alignment.Center),
                    )
                }
            } else {
                ProductImage(store.imageUrl, Modifier.fillMaxSize(), corner = 26.dp, seed = store.id)
            }
            Box(
                Modifier
                    .padding(12.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = PengoColors.Ink) }
        }
        Spacer(Modifier.height(12.dp))
        if (loading || store == null) {
            ShimmerBox(Modifier.fillMaxWidth(0.6f).height(26.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.9f).height(16.dp))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(store.name, style = MaterialTheme.typography.headlineSmall, color = PengoColors.Ink, modifier = Modifier.weight(1f))
            StatusChip(if (store.isOpen != false) "OPEN" else "CLOSED", if (store.isOpen != false) PengoColors.Success else PengoColors.Red)
        }
        if (store.fullAddress.isNotBlank()) {
            Text(store.fullAddress, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
        }
        Spacer(Modifier.height(4.dp))
        RatingLabel(store.rating)
        Spacer(Modifier.height(12.dp))
        StoreContactRow(store, context)
    }
}

@Composable
fun StoreContactRow(store: Store, context: Context) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!store.phone.isNullOrBlank()) {
            ContactPill("Call", Icons.Rounded.Call, PengoGradients.mint) {
                context.safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${store.phone}")))
            }
        }
        val wa = store.whatsapp ?: store.phone
        if (!wa.isNullOrBlank()) {
            ContactPill("WhatsApp", Icons.AutoMirrored.Rounded.Chat, PengoGradients.primary) {
                val digits = wa.filter { it.isDigit() }.let { if (it.length == 10) "91$it" else it }
                context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
            }
        }
        if (store.latitude != null && store.longitude != null) {
            ContactPill("Directions", Icons.Rounded.Directions, PengoGradients.peach) {
                val uri = Uri.parse("geo:${store.latitude},${store.longitude}?q=${store.latitude},${store.longitude}(${Uri.encode(store.name)})")
                context.safeStart(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }
}

@Composable
private fun ContactPill(label: String, icon: ImageVector, brush: Brush, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = PengoColors.Ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = PengoColors.Ink, fontWeight = FontWeight.SemiBold)
    }
}

fun Context.safeStart(intent: Intent) {
    runCatching { startActivity(intent) }.onFailure {
        com.example.pengo.ui.components.UiMessages.show("No app found to open this")
    }
}
