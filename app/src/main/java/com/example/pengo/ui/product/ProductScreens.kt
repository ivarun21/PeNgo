package com.example.pengo.ui.product

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.example.pengo.data.AuthRepository
import com.example.pengo.data.Review
import com.example.pengo.ui.components.UiMessages
import com.example.pengo.util.prettyDate
import kotlinx.coroutines.launch
import com.example.pengo.data.CartRepository
import com.example.pengo.data.CatalogRepository
import com.example.pengo.data.Product
import com.example.pengo.data.Store
import com.example.pengo.data.WishlistRepository
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.components.CenteredLoader
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.HeartButton
import com.example.pengo.ui.components.IconBubble
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.PengoTopBar
import com.example.pengo.ui.components.ProductCard
import com.example.pengo.ui.components.ProductImage
import com.example.pengo.ui.components.QuantityStepper
import com.example.pengo.ui.components.RatingLabel
import com.example.pengo.ui.components.ShimmerBox
import com.example.pengo.ui.components.StatusChip
import com.example.pengo.ui.components.rememberProductActions
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.rupees

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductDetailScreen(nav: NavHostController, productId: String) {
    var product by remember { mutableStateOf<Product?>(null) }
    var store by remember { mutableStateOf<Store?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val wishlist by WishlistRepository.ids.collectAsStateWithLifecycle()
    val cart by CartRepository.lines.collectAsStateWithLifecycle()
    val actions = rememberProductActions()

    LaunchedEffect(productId) {
        runCatching { CatalogRepository.product(productId) }
            .onSuccess {
                product = it
                if (it == null) error = "Product not found"
                it?.storeId?.let { id -> store = runCatching { CatalogRepository.store(id) }.getOrNull() }
            }
            .onFailure { error = it.friendlyMessage() }
    }

    PengoBackground {
        val p = product
        when {
            p == null && error == null -> CenteredLoader()
            p == null -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                PengoTopBar("", onBack = { nav.popBackStack() })
                EmptyState(Icons.Rounded.Inventory2, "Couldn't load product", error.orEmpty())
            }
            else -> {
                val qty = cart.firstOrNull { it.product.id == p.id }?.quantity ?: 0
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 120.dp)
                ) {
                    val images = p.allImages
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        if (images.size > 1) {
                            val pager = rememberPagerState { images.size }
                            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                                ProductImage(images[page], Modifier.fillMaxSize(), corner = 0.dp, seed = p.id)
                            }
                            Row(
                                Modifier.align(Alignment.BottomCenter).padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                repeat(images.size) { i ->
                                    Box(
                                        Modifier
                                            .size(if (i == pager.currentPage) 10.dp else 7.dp)
                                            .clip(CircleShape)
                                            .background(if (i == pager.currentPage) PengoColors.PrimaryDeep else Color.White)
                                    )
                                }
                            }
                        } else {
                            ProductImage(p.imageUrl, Modifier.fillMaxSize(), corner = 0.dp, seed = p.id)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .shadow(6.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { nav.popBackStack() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = PengoColors.Ink) }
                            HeartButton(p.id in wishlist, { actions.toggleWishlist(p) }, Modifier.size(44.dp))
                        }
                    }

                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.headlineSmall, color = PengoColors.Ink, modifier = Modifier.weight(1f))
                            StatusChip(
                                if (p.inStock) "IN STOCK" else "OUT OF STOCK",
                                if (p.inStock) PengoColors.Success else PengoColors.Red,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        RatingLabel(p.rating)
                        Spacer(Modifier.height(10.dp))
                        Text(p.price.rupees(), style = MaterialTheme.typography.headlineMedium, color = PengoColors.Ink, fontWeight = FontWeight.ExtraBold)
                        if (p.inStock && (p.stockQuantity ?: 0) in 1..5) {
                            Text("Only ${p.stockQuantity} left", style = MaterialTheme.typography.labelMedium, color = PengoColors.Warning)
                        }
                        if (!p.description.isNullOrBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text("About this product", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(p.description, style = MaterialTheme.typography.bodyMedium, color = PengoColors.Muted)
                        }
                        if (!p.barcode.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Barcode: ${p.barcode}", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                        }
                        store?.let { s ->
                            Spacer(Modifier.height(18.dp))
                            GlassCard(
                                Modifier.fillMaxWidth(),
                                onClick = { nav.navigate(Routes.Store(storeId = s.id)) },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBubble(Icons.Rounded.Storefront, PengoGradients.lavender)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Sold by", style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted)
                                        Text(s.name, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                                        if (s.fullAddress.isNotBlank()) {
                                            Text(s.fullAddress, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted, maxLines = 1)
                                        }
                                    }
                                    Text("Visit", color = PengoColors.PrimaryDeep, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (!p.inStock && p.storeId != null) {
                            Spacer(Modifier.height(14.dp))
                            RestockCard(p)
                        }
                        Spacer(Modifier.height(18.dp))
                        ReviewsSection(p.id)
                    }
                }

                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (qty > 0) {
                        GlassCard(corner = 20.dp, padding = PaddingValues(8.dp)) {
                            QuantityStepper(qty, { actions.changeQuantity(p.id, it) })
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    GradientButton(
                        if (qty > 0) "In cart • ${(p.price * qty).rupees()}" else "Add to cart",
                        { actions.addToCart(p) },
                        Modifier.weight(1f),
                        enabled = p.inStock,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestockCard(product: Product) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.NotificationsActive, PengoGradients.peach)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (sent) "Request sent" else "Want this back?", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                Text(
                    if (sent) "The store knows you're looking for this item." else "Tell the store to restock this product.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PengoColors.Muted,
                )
            }
        }
        if (!sent) {
            Spacer(Modifier.height(12.dp))
            GradientButton("Request restock", {
                sending = true
                scope.launch {
                    runCatching { CatalogRepository.requestRestock(product) }
                        .onSuccess { sent = true; UiMessages.show("Restock request sent to the store") }
                        .onFailure { UiMessages.show(it.friendlyMessage()) }
                    sending = false
                }
            }, Modifier.fillMaxWidth().height(48.dp), loading = sending, brush = PengoGradients.peach)
        }
    }
}

@Composable
private fun ReviewsSection(productId: String) {
    val scope = rememberCoroutineScope()
    var reviews by remember { mutableStateOf<List<Review>?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var writing by remember { mutableStateOf(false) }
    val myId = AuthRepository.currentUserId()

    LaunchedEffect(productId, reloadKey) {
        reviews = runCatching { CatalogRepository.reviews(productId) }.getOrDefault(emptyList())
    }
    val list = reviews.orEmpty()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Ratings & reviews", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
            if (list.isNotEmpty()) {
                Text(
                    String.format(java.util.Locale.ENGLISH, "%.1f ★ from %d review%s", list.map { it.rating }.average(), list.size, if (list.size == 1) "" else "s"),
                    style = MaterialTheme.typography.bodySmall,
                    color = PengoColors.Muted,
                )
            }
        }
        TextButton(onClick = { writing = true }) {
            Text(if (list.any { it.userId == myId }) "Edit review" else "Write review", color = PengoColors.PrimaryDeep)
        }
    }
    Spacer(Modifier.height(8.dp))
    when {
        reviews == null -> ShimmerBox(Modifier.fillMaxWidth().height(70.dp))
        list.isEmpty() -> Text("No reviews yet. Be the first to share your experience!", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.take(10).forEach { review ->
                GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Stars(review.rating)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (review.userId == myId) "You" else "Verified shopper",
                            style = MaterialTheme.typography.labelMedium,
                            color = PengoColors.Muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(review.createdAt.prettyDate().substringBefore(","), style = MaterialTheme.typography.labelSmall, color = PengoColors.Muted)
                    }
                    if (!review.comment.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(review.comment, style = MaterialTheme.typography.bodyMedium, color = PengoColors.Ink)
                    }
                }
            }
        }
    }

    if (writing) {
        val mine = list.firstOrNull { it.userId == myId }
        ReviewDialog(
            initialRating = mine?.rating ?: 5,
            initialComment = mine?.comment.orEmpty(),
            onDismiss = { writing = false },
            onSubmit = { rating, comment ->
                writing = false
                scope.launch {
                    runCatching { CatalogRepository.submitReview(productId, rating, comment) }
                        .onSuccess { UiMessages.show("Thanks for your review!"); reloadKey++ }
                        .onFailure { UiMessages.show(it.friendlyMessage()) }
                }
            },
        )
    }
}

@Composable
private fun Stars(rating: Int, size: Int = 16, onSelect: ((Int) -> Unit)? = null) {
    Row {
        (1..5).forEach { i ->
            Icon(
                if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "$i stars",
                tint = PengoColors.Warning,
                modifier = Modifier
                    .size(size.dp)
                    .then(if (onSelect != null) Modifier.clickable { onSelect(i) } else Modifier),
            )
        }
    }
}

@Composable
private fun ReviewDialog(initialRating: Int, initialComment: String, onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var rating by remember { mutableStateOf(initialRating) }
    var comment by remember { mutableStateOf(initialComment) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate this product") },
        text = {
            Column {
                Stars(rating, size = 36) { rating = it }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(500) },
                    label = { Text("Your review (optional)") },
                    minLines = 3,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(rating, comment) }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ProductListScreen(nav: NavHostController, title: String, categoryId: Int?, query: String?) {
    var products by remember { mutableStateOf<List<Product>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val wishlist by WishlistRepository.ids.collectAsStateWithLifecycle()
    val cart by CartRepository.lines.collectAsStateWithLifecycle()
    val actions = rememberProductActions()

    LaunchedEffect(categoryId, query) {
        runCatching {
            when {
                categoryId != null -> CatalogRepository.productsByCategory(categoryId)
                !query.isNullOrBlank() -> CatalogRepository.searchProducts(query)
                else -> CatalogRepository.featuredProducts()
            }
        }.onSuccess { products = it }.onFailure { error = it.friendlyMessage(); products = emptyList() }
    }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar(title, onBack = { nav.popBackStack() })
            val list = products
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    list == null -> items(6) { ShimmerBox(Modifier.fillMaxWidth().aspectRatio(0.68f), corner = 22.dp) }
                    list.isEmpty() -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        EmptyState(
                            Icons.Rounded.Inventory2,
                            if (error != null) "Couldn't load" else "Nothing found",
                            error ?: "Try another search or category.",
                        )
                    }
                    else -> items(list, key = { it.id }) { product ->
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
}
