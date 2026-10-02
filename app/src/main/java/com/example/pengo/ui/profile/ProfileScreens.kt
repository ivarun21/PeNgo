package com.example.pengo.ui.profile

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.pengo.BuildConfig
import com.example.pengo.data.AuthRepository
import com.example.pengo.data.CartRepository
import com.example.pengo.data.OrderRepository
import com.example.pengo.data.Product
import com.example.pengo.data.Profile
import com.example.pengo.data.ProfileRepository
import com.example.pengo.data.ScanHistory
import com.example.pengo.data.StaffRepository
import com.example.pengo.data.Order
import com.example.pengo.util.AppLock
import androidx.fragment.app.FragmentActivity
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Storefront
import com.example.pengo.data.Wallet
import com.example.pengo.data.WalletTransaction
import com.example.pengo.data.WishlistRepository
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.components.CenteredLoader
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.GradientCard
import com.example.pengo.ui.components.IconBubble
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.PengoTextField
import com.example.pengo.ui.components.PengoTopBar
import com.example.pengo.ui.components.ProductRow
import com.example.pengo.ui.components.QuantityStepper
import com.example.pengo.ui.components.UiMessages
import com.example.pengo.ui.components.rememberProductActions
import com.example.pengo.ui.main.TAB_ORDERS
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.Prefs
import com.example.pengo.util.prettyDate
import com.example.pengo.util.rupees
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(nav: NavHostController, onOpenTab: (Int) -> Unit, onLoggedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var orderCount by remember { mutableStateOf<Int?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    val wishlist by WishlistRepository.ids.collectAsStateWithLifecycle()
    val staffStores by StaffRepository.stores.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        launch { StaffRepository.refreshStores() }
        val p = async { runCatching { ProfileRepository.myProfile() }.getOrNull() }
        val w = async { ProfileRepository.wallet() }
        val o = async { runCatching { OrderRepository.myOrders().size }.getOrNull() }
        profile = p.await()
        wallet = w.await()
        orderCount = o.await()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, color = PengoColors.Ink)

        GradientCard(PengoGradients.mint, Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile?.fullName?.take(1)?.uppercase() ?: "P",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PengoColors.PrimaryDeep,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.fullName ?: "PeNgo shopper", style = MaterialTheme.typography.titleLarge, color = PengoColors.Ink)
                    Text(profile?.email ?: AuthRepository.currentEmail().orEmpty(), style = MaterialTheme.typography.bodySmall, color = PengoColors.Ink.copy(alpha = 0.7f))
                    profile?.phoneNumber?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = PengoColors.Ink.copy(alpha = 0.7f))
                    }
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f))
                        .clickable { nav.navigate(Routes.EditProfile) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Edit, "Edit profile", tint = PengoColors.Ink) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Orders", orderCount?.toString() ?: "–", Icons.AutoMirrored.Rounded.ReceiptLong, PengoGradients.peach, Modifier.weight(1f)) { onOpenTab(TAB_ORDERS) }
            StatCard("Wishlist", wishlist.size.toString(), Icons.Rounded.Favorite, PengoGradients.pink, Modifier.weight(1f)) { nav.navigate(Routes.Wishlist) }
            StatCard("Points", (wallet?.rewardPoints ?: 0).toString(), Icons.Rounded.Stars, PengoGradients.primary, Modifier.weight(1f)) { nav.navigate(Routes.Wallet) }
        }

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp), onClick = { nav.navigate(Routes.Wallet) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.AccountBalanceWallet, PengoGradients.lavender)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PeNgo Wallet", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                    Text("Cashback & rewards balance", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                }
                Text((wallet?.balance ?: 0.0).rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PengoColors.Ink)
            }
        }

        if (staffStores.isNotEmpty()) {
            GradientCard(PengoGradients.primary, Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.Staff) }) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Storefront, null, tint = PengoColors.PrimaryDeep)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Staff mode", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                        Text(
                            staffStores.joinToString { "${it.storeName ?: "Store"} • ${it.role}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = PengoColors.Ink.copy(alpha = 0.7f),
                            maxLines = 2,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = PengoColors.Ink)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) {
            MenuRow("Shopping list", Icons.AutoMirrored.Rounded.PlaylistAddCheck, PengoGradients.primary) { nav.navigate(Routes.ShoppingList) }
            MenuRow("Edit profile", Icons.Rounded.Person, PengoGradients.mint) { nav.navigate(Routes.EditProfile) }
            MenuRow("My wishlist", Icons.Rounded.Favorite, PengoGradients.pink) { nav.navigate(Routes.Wishlist) }
            MenuRow("Order history", Icons.AutoMirrored.Rounded.ReceiptLong, PengoGradients.peach) { onOpenTab(TAB_ORDERS) }
            MenuRow("Scan history", Icons.Rounded.History, PengoGradients.lavender) { nav.navigate(Routes.ScanHistory) }
            MenuRow("Settings", Icons.Rounded.Settings, PengoGradients.primary) { nav.navigate(Routes.Settings) }
        }

        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp), onClick = { confirmLogout = true }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.AutoMirrored.Rounded.Logout, PengoGradients.pink, size = 38.dp, iconSize = 18.dp)
                Spacer(Modifier.width(14.dp))
                Text("Log out", style = MaterialTheme.typography.titleSmall, color = PengoColors.Red)
            }
        }
    }

    if (confirmLogout) {
        LogoutDialog(
            onConfirm = {
                confirmLogout = false
                scope.launch { AuthRepository.signOut(); onLoggedOut() }
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log out?") },
        text = { Text("You'll need to log in again to shop with PeNgo.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Log out", color = PengoColors.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, corner = 20.dp, padding = PaddingValues(12.dp), onClick = onClick) {
        IconBubble(icon, brush, size = 34.dp, iconSize = 18.dp)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = PengoColors.Ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted)
    }
}

@Composable
private fun MenuRow(title: String, icon: ImageVector, brush: Brush, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, brush, size = 38.dp, iconSize = 18.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = PengoColors.Muted)
    }
}

@Composable
fun EditProfileScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val p = runCatching { ProfileRepository.myProfile() }.getOrNull()
        name = p?.fullName.orEmpty()
        phone = p?.phoneNumber.orEmpty()
        email = p?.email ?: AuthRepository.currentEmail().orEmpty()
        loaded = true
    }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("Edit profile", onBack = { nav.popBackStack() })
            if (!loaded) {
                CenteredLoader()
                return@Column
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
                    PengoTextField(name, { name = it }, "Full name", leadingIcon = Icons.Rounded.Person)
                    Spacer(Modifier.height(10.dp))
                    PengoTextField(phone, { phone = it }, "Mobile number", leadingIcon = Icons.Rounded.Phone, keyboardType = KeyboardType.Phone)
                    Spacer(Modifier.height(10.dp))
                    Text("Email", style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted)
                    Text(email, style = MaterialTheme.typography.bodyLarge, color = PengoColors.Ink)
                    Text("Email is linked to your login and can't be changed here.", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(error!!, color = PengoColors.Red, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    GradientButton("Save changes", {
                        error = when {
                            name.trim().length < 2 -> "Please enter your full name."
                            phone.filter { it.isDigit() }.length !in 10..13 -> "Enter a valid mobile number."
                            else -> null
                        }
                        if (error == null) {
                            saving = true
                            scope.launch {
                                runCatching { ProfileRepository.updateProfile(name, phone) }
                                    .onSuccess { UiMessages.show("Profile updated"); nav.popBackStack() }
                                    .onFailure { error = it.friendlyMessage() }
                                saving = false
                            }
                        }
                    }, Modifier.fillMaxWidth(), loading = saving, brush = PengoGradients.mint)
                }
            }
        }
    }
}

@Composable
fun WishlistScreen(nav: NavHostController) {
    var products by remember { mutableStateOf<List<Product>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ids by WishlistRepository.ids.collectAsStateWithLifecycle()
    val cart by CartRepository.lines.collectAsStateWithLifecycle()
    val actions = rememberProductActions()

    LaunchedEffect(Unit) {
        runCatching { WishlistRepository.products() }
            .onSuccess { products = it }
            .onFailure { error = it.friendlyMessage(); products = emptyList() }
    }
    val visible = products.orEmpty().filter { it.id in ids }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("My wishlist", onBack = { nav.popBackStack() })
            when {
                products == null -> CenteredLoader()
                visible.isEmpty() -> EmptyState(
                    Icons.Rounded.Favorite,
                    if (error != null) "Couldn't load wishlist" else "Your wishlist is empty",
                    error ?: "Tap the heart on any product to save it here.",
                    brush = PengoGradients.pink,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.id }) { product ->
                        val qty = cart.firstOrNull { it.product.id == product.id }?.quantity ?: 0
                        ProductRow(
                            product = product,
                            subtitle = product.price.rupees() + if (product.inStock) "  •  In stock" else "  •  Out of stock",
                            onClick = { nav.navigate(Routes.Product(product.id)) },
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    com.example.pengo.ui.components.HeartButton(true, { actions.toggleWishlist(product) })
                                    Spacer(Modifier.height(6.dp))
                                    if (qty > 0) {
                                        QuantityStepper(qty, { actions.changeQuantity(product.id, it) }, compact = true)
                                    } else {
                                        com.example.pengo.ui.components.AddButton({ actions.addToCart(product) }, enabled = product.inStock)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class Tier(val name: String, val min: Int, val next: Int?)

private val tiers = listOf(
    Tier("Bronze", 0, 200),
    Tier("Silver", 200, 1000),
    Tier("Gold", 1000, 5000),
    Tier("Platinum", 5000, null),
)

private data class Spending(
    val thisMonth: Double,
    val lastMonth: Double,
    val orders: Int,
    val average: Double,
    val topStore: String?,
    val totalSaved: Double,
)

private fun spendingOf(orders: List<Order>): Spending {
    val paid = orders.filter { it.isPaid }
    val now = java.time.YearMonth.now()
    fun monthOf(o: Order) = runCatching {
        java.time.YearMonth.from(java.time.OffsetDateTime.parse(o.createdAt).atZoneSameInstant(java.time.ZoneId.systemDefault()))
    }.getOrNull()
    val thisMonth = paid.filter { monthOf(it) == now }.sumOf { it.totalAmount }
    val lastMonth = paid.filter { monthOf(it) == now.minusMonths(1) }.sumOf { it.totalAmount }
    val saved = paid.sumOf { o -> (o.items.orEmpty().sumOf { it.priceAtPurchase * it.quantity } - o.totalAmount).coerceAtLeast(0.0) }
    return Spending(
        thisMonth = thisMonth,
        lastMonth = lastMonth,
        orders = paid.size,
        average = if (paid.isEmpty()) 0.0 else paid.sumOf { it.totalAmount } / paid.size,
        topStore = paid.groupBy { it.store?.name }.maxByOrNull { (_, v) -> v.size }?.key,
        totalSaved = saved,
    )
}

@Composable
private fun SpendingCard(s: Spending) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Insights, PengoGradients.mint, size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text("Spending insights", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("This month", style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted)
                Text(s.thisMonth.rupees(), style = MaterialTheme.typography.titleLarge, color = PengoColors.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text("Last month", style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted)
                Text(s.lastMonth.rupees(), style = MaterialTheme.typography.titleLarge, color = PengoColors.Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        val trend = when {
            s.lastMonth <= 0.0 && s.thisMonth <= 0.0 -> "No purchases yet. Your monthly trend will show here."
            s.lastMonth <= 0.0 -> "Your first month shopping with PeNgo."
            s.thisMonth > s.lastMonth -> "You've spent ${((s.thisMonth / s.lastMonth - 1) * 100).toInt()}% more than last month."
            else -> "You've spent ${((1 - s.thisMonth / s.lastMonth) * 100).toInt()}% less than last month."
        }
        Text(trend, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
        Spacer(Modifier.height(10.dp))
        Text(
            "${s.orders} paid orders • average ${s.average.rupees()}" +
                (s.topStore?.let { " • most visited: $it" } ?: "") +
                (if (s.totalSaved > 0) " • saved ${s.totalSaved.rupees()} with coupons" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = PengoColors.Ink,
        )
    }
}

@Composable
private fun TierCard(points: Int) {
    val tier = tiers.last { points >= it.min }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.WorkspacePremium, PengoGradients.peach, size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${tier.name} member", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                Text(
                    tier.next?.let { "${it - points} more points to ${tiers[tiers.indexOf(tier) + 1].name}" } ?: "You've reached the top tier",
                    style = MaterialTheme.typography.bodySmall,
                    color = PengoColors.Muted,
                )
            }
        }
        tier.next?.let { next ->
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { ((points - tier.min).toFloat() / (next - tier.min)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = PengoColors.Purple,
                trackColor = PengoColors.Divider,
            )
        }
    }
}

@Composable
fun WalletScreen(nav: NavHostController) {
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var history by remember { mutableStateOf<List<WalletTransaction>?>(null) }
    var spending by remember { mutableStateOf<Spending?>(null) }
    LaunchedEffect(Unit) {
        launch { spending = runCatching { spendingOf(OrderRepository.myOrders()) }.getOrNull() }
        wallet = ProfileRepository.wallet()
        history = runCatching { ProfileRepository.transactions() }.getOrDefault(emptyList())
    }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("PeNgo Wallet", onBack = { nav.popBackStack() })
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    GradientCard(PengoGradients.lavender, Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp)) {
                            Text("Wallet balance", style = MaterialTheme.typography.labelLarge, color = PengoColors.Ink.copy(alpha = 0.7f))
                            Text((wallet?.balance ?: 0.0).rupees(), style = MaterialTheme.typography.displaySmall, color = PengoColors.Ink)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Stars, null, tint = PengoColors.Ink)
                                Spacer(Modifier.width(6.dp))
                                Text("${wallet?.rewardPoints ?: 0} reward points", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Earn 1 point for every ₹10 you pay through PeNgo.", style = MaterialTheme.typography.bodySmall, color = PengoColors.Ink.copy(alpha = 0.7f))
                        }
                    }
                }
                item { TierCard(wallet?.rewardPoints ?: 0) }
                spending?.let { s -> item { SpendingCard(s) } }
                item { Text("History", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink) }
                val list = history
                when {
                    list == null -> item { CenteredLoader(Modifier.height(120.dp)) }
                    list.isEmpty() -> item {
                        Text("No wallet activity yet. Pay for an order to start earning points.", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                    }
                    else -> items(list, key = { it.id }) { tx ->
                        val reward = tx.type == "reward"
                        GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, padding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(
                                    if (reward) Icons.Rounded.Stars else Icons.Rounded.AccountBalanceWallet,
                                    if (reward) PengoGradients.primary else PengoGradients.mint,
                                    size = 38.dp,
                                    iconSize = 18.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tx.description ?: tx.type ?: "Transaction", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                                    Text(tx.createdAt.prettyDate(), style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                                }
                                Text(
                                    if (reward) "+${tx.amount.toInt()} pts" else (if (tx.amount >= 0) "+" else "") + tx.amount.rupees(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.amount >= 0) PengoColors.Success else PengoColors.Red,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanHistoryScreen(nav: NavHostController) {
    var items by remember { mutableStateOf<List<ScanHistory>?>(null) }
    LaunchedEffect(Unit) { items = runCatching { OrderRepository.scanHistory() }.getOrDefault(emptyList()) }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("Scan history", onBack = { nav.popBackStack() })
            val list = items
            when {
                list == null -> CenteredLoader()
                list.isEmpty() -> EmptyState(Icons.Rounded.QrCodeScanner, "No scans yet", "Store QR and barcode scans will show up here.", brush = PengoGradients.lavender)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { scan ->
                        GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, padding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(
                                    Icons.Rounded.QrCodeScanner,
                                    if (scan.type == "store_qr") PengoGradients.primary else PengoGradients.mint,
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(scan.content, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink, maxLines = 1)
                                    Text(
                                        (if (scan.type == "store_qr") "Store QR" else "Product barcode") + " • " + scan.createdAt.prettyDate(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PengoColors.Muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(nav: NavHostController, onLoggedOut: () -> Unit) {
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("Settings", onBack = { nav.popBackStack() })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsGroup("Shopping") {
                    ToggleRow("Haptic feedback", "Vibrate on add to cart and scans", Icons.Rounded.Vibration, PengoGradients.mint, settings.haptics) { v ->
                        Prefs.update { it.copy(haptics = v) }
                    }
                    ToggleRow("Scan sound", "Beep when a code is scanned", Icons.AutoMirrored.Rounded.VolumeUp, PengoGradients.primary, settings.scanSound) { v ->
                        Prefs.update { it.copy(scanSound = v) }
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.LocationOn, PengoGradients.lavender, size = 38.dp, iconSize = 18.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Nearby store radius", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                                Text("Show stores within ${settings.searchRadiusKm} km on the map", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                            }
                        }
                        Slider(
                            value = settings.searchRadiusKm.toFloat(),
                            onValueChange = { v -> Prefs.update { it.copy(searchRadiusKm = v.toInt()) } },
                            valueRange = 1f..20f,
                            steps = 18,
                            colors = SliderDefaults.colors(thumbColor = PengoColors.PrimaryDeep, activeTrackColor = PengoColors.Blue),
                        )
                    }
                }
                SettingsGroup("Notifications") {
                    ToggleRow("Order updates", "Payment and exit pass alerts", Icons.Rounded.Notifications, PengoGradients.peach, settings.orderNotifications) { v ->
                        Prefs.update { it.copy(orderNotifications = v) }
                    }
                    ToggleRow("Offers & coupons", "Deals from stores near you", Icons.Rounded.Stars, PengoGradients.pink, settings.offerNotifications) { v ->
                        Prefs.update { it.copy(offerNotifications = v) }
                    }
                }
                SettingsGroup("Security") {
                    val lockAvailable = remember { AppLock.isAvailable(context) }
                    ToggleRow(
                        "App lock",
                        if (lockAvailable) "Ask for fingerprint, face or screen lock when opening PeNgo" else "Set up a screen lock on this phone to use this",
                        Icons.Rounded.Fingerprint,
                        PengoGradients.lavender,
                        settings.appLock,
                    ) { v ->
                        val activity = context as? FragmentActivity
                        when {
                            !v -> Prefs.update { it.copy(appLock = false) }
                            !lockAvailable || activity == null -> UiMessages.show("Set up a fingerprint or screen lock in phone settings first")
                            else -> AppLock.prompt(activity, "Turn on app lock") { ok ->
                                if (ok) Prefs.update { it.copy(appLock = true) }
                            }
                        }
                    }
                }
                SettingsGroup("Support") {
                    MenuRow("Help & support", Icons.AutoMirrored.Rounded.HelpOutline, PengoGradients.mint, "Chat with us on WhatsApp") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/917206959166"))) }
                    }
                    MenuRow("Privacy & security", Icons.Rounded.PrivacyTip, PengoGradients.lavender, "Your password is never stored by PeNgo") {
                        UiMessages.show("Passwords are handled securely by the login service and never stored in your profile.")
                    }
                    MenuRow("About PeNgo", Icons.Rounded.Info, PengoGradients.primary, "Version ${BuildConfig.VERSION_NAME}") {
                        UiMessages.show("PeNgo • Scan, pay & walk out • v${BuildConfig.VERSION_NAME}")
                    }
                }
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp), onClick = { confirmLogout = true }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.AutoMirrored.Rounded.Logout, PengoGradients.pink, size = 38.dp, iconSize = 18.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Log out", style = MaterialTheme.typography.titleSmall, color = PengoColors.Red)
                    }
                }
            }
        }
    }

    if (confirmLogout) {
        LogoutDialog(
            onConfirm = {
                confirmLogout = false
                scope.launch { AuthRepository.signOut(); onLoggedOut() }
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = PengoColors.Muted, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, icon: ImageVector, brush: Brush, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, brush, size = 38.dp, iconSize = 18.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = PengoColors.Blue, checkedThumbColor = Color.White),
        )
    }
}
