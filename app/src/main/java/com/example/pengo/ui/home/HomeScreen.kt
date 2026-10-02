package com.example.pengo.ui.home

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.IconButton
import com.example.pengo.data.OrderRepository
import com.example.pengo.ui.components.UiMessages
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.pengo.data.CartRepository
import com.example.pengo.data.CatalogRepository
import com.example.pengo.data.Category
import com.example.pengo.data.Product
import com.example.pengo.data.ProfileRepository
import com.example.pengo.data.WishlistRepository
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.SCAN_MODE_PRODUCT
import com.example.pengo.ui.SCAN_MODE_STORE
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientCard
import com.example.pengo.ui.components.IconBubble
import com.example.pengo.ui.components.ProductCard
import com.example.pengo.ui.components.ShimmerBox
import com.example.pengo.ui.components.rememberProductActions
import com.example.pengo.ui.main.TAB_EXPLORE
import com.example.pengo.ui.main.TAB_ORDERS
import com.example.pengo.ui.main.TAB_PROFILE
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val name: String = "",
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val buyAgain: List<Product> = emptyList(),
    val forYou: List<Product> = emptyList(),
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        _state.update { it.copy(refreshing = refresh, error = null) }
        viewModelScope.launch {
            val profile = async { runCatching { ProfileRepository.myProfile() }.getOrNull() }
            val categories = async { runCatching { CatalogRepository.categories() }.getOrDefault(emptyList()) }
            val orders = async { runCatching { OrderRepository.myOrders() }.getOrDefault(emptyList()) }
            val products = runCatching { CatalogRepository.featuredProducts() }
            val featured = products.getOrDefault(_state.value.products)
            val pastOrders = orders.await()

            val bought = pastOrders.filter { it.isPaid }
                .flatMap { o -> o.items.orEmpty().mapNotNull { it.product } }
            val buyAgain = bought.groupingBy { it.id }.eachCount().entries
                .sortedByDescending { it.value }
                .mapNotNull { e -> bought.firstOrNull { it.id == e.key } }
                .filter { it.inStock }
                .take(10)

            val likedCategories = (bought.mapNotNull { it.categoryId } +
                featured.filter { it.id in WishlistRepository.ids.value }.mapNotNull { it.categoryId })
                .groupingBy { it }.eachCount()
            val boughtIds = bought.map { it.id }.toSet()
            val forYou = featured
                .filter { it.id !in boughtIds && it.inStock }
                .sortedByDescending { likedCategories[it.categoryId] ?: 0 }
                .take(if (likedCategories.isEmpty()) 0 else 10)

            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    name = profile.await()?.fullName?.substringBefore(' ').orEmpty(),
                    categories = categories.await(),
                    products = featured,
                    buyAgain = buyAgain,
                    forYou = forYou,
                    error = products.exceptionOrNull()?.friendlyMessage(),
                )
            }
            if (refresh) {
                runCatching { CartRepository.refresh() }
                runCatching { WishlistRepository.refreshIds() }
            }
        }
    }
}

@Composable
fun HomeScreen(nav: NavHostController, onOpenTab: (Int) -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val wishlist by WishlistRepository.ids.collectAsStateWithLifecycle()
    val cart by CartRepository.lines.collectAsStateWithLifecycle()
    val actions = rememberProductActions()
    var query by rememberSaveable { mutableStateOf("") }

    fun search(text: String) {
        val q = text.trim()
        if (q.isNotBlank()) nav.navigate(Routes.ProductList(title = "Results for \"$q\"", query = q))
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            query = spoken
            search(spoken)
        }
    }
    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a product name, e.g. \"biscuits\"")
        runCatching { voice.launch(intent) }.onFailure { UiMessages.show("Voice search isn't available on this phone") }
        Unit
    }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(refresh = true) }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(Modifier.statusBarsPadding()) {
                    Spacer(Modifier.height(12.dp))
                    HomeHeader(state.name, onAvatar = { onOpenTab(TAB_PROFILE) }, onWishlist = { nav.navigate(Routes.Wishlist) })
                    Spacer(Modifier.height(18.dp))
                    SearchField(query, { query = it }, onVoice = startVoice) { search(query) }
                    Spacer(Modifier.height(18.dp))
                    ScanStoreHero { nav.navigate(Routes.Scanner(SCAN_MODE_STORE)) }
                    Spacer(Modifier.height(18.dp))
                    QuickActions(
                        onBarcode = { nav.navigate(Routes.Scanner(SCAN_MODE_PRODUCT)) },
                        onNearby = { onOpenTab(TAB_EXPLORE) },
                        onList = { nav.navigate(Routes.ShoppingList) },
                        onOrders = { onOpenTab(TAB_ORDERS) },
                    )
                }
            }

            if (state.buyAgain.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    ProductStrip("Buy again", state.buyAgain, nav, wishlist, cart.associate { it.product.id to it.quantity }, actions)
                }
            }
            if (state.forYou.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    ProductStrip("Picked for you", state.forYou, nav, wishlist, cart.associate { it.product.id to it.quantity }, actions)
                }
            }

            if (state.categories.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        Text("Categories", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.categories, key = { it.id }) { category ->
                                CategoryChip(category) {
                                    nav.navigate(Routes.ProductList(title = category.name, categoryId = category.id))
                                }
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(2) }) {
                Text("Popular products", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
            }

            when {
                state.loading -> items(4) {
                    ShimmerBox(Modifier.fillMaxWidth().aspectRatio(0.68f), corner = 22.dp)
                }
                state.products.isEmpty() -> item(span = { GridItemSpan(2) }) {
                    EmptyState(
                        icon = Icons.Rounded.Inventory2,
                        title = if (state.error != null) "Couldn't load products" else "No products yet",
                        message = state.error ?: "Scan a store QR to see what's available there.",
                        actionText = if (state.error != null) "Retry" else "Scan store QR",
                        onAction = { if (state.error != null) vm.load() else nav.navigate(Routes.Scanner(SCAN_MODE_STORE)) },
                    )
                }
                else -> items(state.products, key = { it.id }) { product ->
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
}

@Composable
private fun HomeHeader(name: String, onAvatar: () -> Unit, onWishlist: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (name.isBlank()) "Hello there" else "Hello, $name",
                style = MaterialTheme.typography.headlineSmall,
                color = PengoColors.Ink,
            )
            Text("Skip the queue. Scan, pay & go.", style = MaterialTheme.typography.bodyMedium, color = PengoColors.Muted)
        }
        Box(
            Modifier
                .size(46.dp)
                .shadow(8.dp, CircleShape, spotColor = Color(0x2264A1FF))
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onWishlist),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.FavoriteBorder, "Wishlist", tint = PengoColors.Red) }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(PengoGradients.peach)
                .clickable(onClick = onAvatar),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase().ifBlank { "P" }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProductStrip(
    title: String,
    products: List<Product>,
    nav: NavHostController,
    wishlist: Set<String>,
    cartQty: Map<String, Int>,
    actions: com.example.pengo.ui.components.ProductActions,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products, key = { "$title-${it.id}" }) { product ->
                ProductCard(
                    product = product,
                    saved = product.id in wishlist,
                    cartQuantity = cartQty[product.id] ?: 0,
                    onClick = { nav.navigate(Routes.Product(product.id)) },
                    onToggleWishlist = { actions.toggleWishlist(product) },
                    onAdd = { actions.addToCart(product) },
                    onQuantityChange = { actions.changeQuantity(product.id, it) },
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onVoice: () -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Search products") },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PengoColors.Muted) },
        trailingIcon = {
            IconButton(onClick = onVoice) { Icon(Icons.Rounded.Mic, "Voice search", tint = PengoColors.PrimaryDeep) }
        },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = PengoColors.Blue,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color(0x1A64A1FF)),
    )
}

@Composable
private fun ScanStoreHero(onClick: () -> Unit) {
    GradientCard(PengoGradients.primary, Modifier.fillMaxWidth().height(160.dp), onClick = onClick) {
        Box(
            Modifier
                .size(180.dp)
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-50).dp)
                .alpha(0.25f)
                .clip(CircleShape)
                .background(Color.White)
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("In a store?", style = MaterialTheme.typography.labelLarge, color = PengoColors.Ink.copy(alpha = 0.7f))
                Spacer(Modifier.height(4.dp))
                Text("Scan Store QR", style = MaterialTheme.typography.headlineSmall, color = PengoColors.Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "See everything available, add to cart and pay from your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PengoColors.Ink.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.QrCodeScanner, null, tint = PengoColors.PrimaryDeep, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun QuickActions(onBarcode: () -> Unit, onNearby: () -> Unit, onList: () -> Unit, onOrders: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction("Barcode", Icons.Rounded.ViewWeek, PengoGradients.mint, onBarcode, Modifier.weight(1f))
        QuickAction("Nearby", Icons.Rounded.LocationOn, PengoGradients.lavender, onNearby, Modifier.weight(1f))
        QuickAction("My list", Icons.AutoMirrored.Rounded.PlaylistAddCheck, PengoGradients.pink, onList, Modifier.weight(1f))
        QuickAction("Orders", Icons.AutoMirrored.Rounded.ReceiptLong, PengoGradients.peach, onOrders, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, brush: Brush, onClick: () -> Unit, modifier: Modifier) {
    GlassCard(modifier, corner = 20.dp, padding = PaddingValues(vertical = 12.dp, horizontal = 4.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBubble(icon, brush, size = 42.dp, iconSize = 20.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = PengoColors.Ink)
        }
    }
}

@Composable
private fun CategoryChip(category: Category, onClick: () -> Unit) {
    val brush = PengoGradients.all[category.id.mod(PengoGradients.all.size)]
    Row(
        Modifier
            .shadow(6.dp, RoundedCornerShape(50), spotColor = Color(0x1A64A1FF))
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
            if (category.iconUrl.isNullOrBlank()) {
                Icon(Icons.Rounded.Category, null, tint = Color.White, modifier = Modifier.size(16.dp))
            } else {
                coil3.compose.AsyncImage(category.iconUrl, null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(category.name, style = MaterialTheme.typography.labelLarge, color = PengoColors.Ink)
    }
}
