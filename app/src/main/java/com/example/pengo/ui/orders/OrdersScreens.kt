package com.example.pengo.ui.orders

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.pengo.data.AuthRepository
import com.example.pengo.data.Order
import com.example.pengo.data.ORDER_QR_PREFIX
import com.example.pengo.data.OrderRepository
import com.example.pengo.util.Notifier
import com.example.pengo.data.ProfileRepository
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.cart.PaymentConfirmDialog
import com.example.pengo.ui.components.CenteredLoader
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.GradientCard
import com.example.pengo.ui.components.IconBubble
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.PengoTopBar
import com.example.pengo.ui.components.ProductImage
import com.example.pengo.ui.components.ShimmerBox
import com.example.pengo.ui.components.StatusChip
import com.example.pengo.ui.components.UiMessages
import com.example.pengo.ui.main.TAB_ORDERS
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.InvoicePdf
import com.example.pengo.util.QrCode
import com.example.pengo.util.Upi
import com.example.pengo.util.prettyDate
import com.example.pengo.util.rupees
import com.example.pengo.util.shortId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OrdersState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val orders: List<Order> = emptyList(),
    val error: String? = null,
)

class OrdersViewModel : ViewModel() {
    private val _state = MutableStateFlow(OrdersState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        _state.update { it.copy(refreshing = refresh, error = null) }
        viewModelScope.launch {
            runCatching { OrderRepository.myOrders() }
                .onSuccess { list -> _state.update { OrdersState(loading = false, orders = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.friendlyMessage()) } }
        }
    }
}

@Composable
fun orderStatus(order: Order): Pair<String, Color> = when {
    order.status.equals("cancelled", true) -> "CANCELLED" to PengoColors.Red
    order.exitPass?.status.equals("used", true) || order.exitPass?.verifiedAt != null -> "EXITED" to PengoColors.Muted
    order.isPaid -> "PAID" to PengoColors.Success
    else -> "PAYMENT PENDING" to PengoColors.Warning
}

@Composable
fun OrdersScreen(nav: NavHostController, vm: OrdersViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(refresh = true) }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Text("Order history", style = MaterialTheme.typography.headlineSmall, color = PengoColors.Ink)
                    Text("Bills, exit passes and invoices", style = MaterialTheme.typography.bodyMedium, color = PengoColors.Muted)
                }
            }
            when {
                state.loading -> items(4) { ShimmerBox(Modifier.fillMaxWidth().height(110.dp), corner = 22.dp) }
                state.orders.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = if (state.error != null) "Couldn't load orders" else "No orders yet",
                        message = state.error ?: "Your bills and exit passes will appear here after checkout.",
                        brush = PengoGradients.peach,
                        actionText = if (state.error != null) "Retry" else null,
                        onAction = if (state.error != null) ({ vm.load() }) else null,
                    )
                }
                else -> items(state.orders, key = { it.id }) { order ->
                    OrderCard(order) { nav.navigate(Routes.OrderDetail(order.id)) }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, onClick: () -> Unit) {
    val (label, color) = orderStatus(order)
    val items = order.items.orEmpty()
    GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Storefront, PengoGradients.lavender, size = 42.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(order.store?.name ?: "PeNgo store", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                Text("#${order.id.shortId()} • ${order.createdAt.prettyDate()}", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
            }
            StatusChip(label, color)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                items.take(4).forEach { item ->
                    ProductImage(
                        item.product?.imageUrl,
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(2.dp),
                        corner = 10.dp,
                        seed = item.productId.orEmpty(),
                    )
                }
                if (items.size > 4) {
                    Text("  +${items.size - 4}", style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${items.sumOf { it.quantity }} items", style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted)
                Text(order.totalAmount.rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PengoColors.Ink)
            }
        }
    }
}

@Composable
fun OrderDetailScreen(nav: NavHostController, orderId: String, justPlaced: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<Order?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmPayment by remember { mutableStateOf(false) }

    LaunchedEffect(orderId, reloadKey) {
        runCatching { OrderRepository.order(orderId) }
            .onSuccess { order = it; error = if (it == null) "Order not found" else null }
            .onFailure { error = it.friendlyMessage() }
    }

    val waitingForCounter = order?.let { !it.isPaid && !it.status.equals("cancelled", true) } == true
    LaunchedEffect(waitingForCounter) {
        while (waitingForCounter) {
            delay(5_000)
            val fresh = runCatching { OrderRepository.order(orderId) }.getOrNull() ?: continue
            if (fresh.isPaid) Notifier.orderUpdate("Payment received", "Your exit pass for order #${fresh.id.shortId()} is ready.")
            order = fresh
            if (fresh.isPaid || fresh.status.equals("cancelled", true)) break
        }
    }

    fun markPaid(ref: String?) {
        val current = order ?: return
        busy = true
        scope.launch {
            runCatching { OrderRepository.confirmUpi(listOf(current.id), ref?.ifBlank { null } ?: "UPI-${current.id.shortId()}") }
                .onSuccess {
                    UiMessages.show("Payment recorded. Your exit pass is ready.")
                    Notifier.orderUpdate("Payment successful", "Your exit pass for order #${current.id.shortId()} is ready.")
                    reloadKey++
                }
                .onFailure { UiMessages.show(it.friendlyMessage()) }
            busy = false
        }
    }

    val upiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val response = Upi.parseResponse(result.data?.getStringExtra("response"))
        when (response.status) {
            Upi.Status.SUCCESS -> markPaid(response.txnRef)
            Upi.Status.FAILED -> UiMessages.show("Payment failed")
            else -> confirmPayment = true
        }
    }

    fun invoice(share: Boolean) {
        val current = order ?: return
        busy = true
        scope.launch {
            runCatching {
                val profile = ProfileRepository.myProfile()
                withContext(Dispatchers.IO) { InvoicePdf.create(context, current, profile, AuthRepository.currentEmail()) }
            }.onSuccess { file ->
                if (share) {
                    InvoicePdf.share(context, file)
                } else {
                    val uri = withContext(Dispatchers.IO) { InvoicePdf.saveToDownloads(context, file) }
                    if (uri != null) UiMessages.show("Invoice saved to Downloads") else InvoicePdf.share(context, file)
                }
            }.onFailure { UiMessages.show("Couldn't create the invoice") }
            busy = false
        }
    }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar(
                "Order #${orderId.shortId()}",
                onBack = {
                    if (justPlaced) nav.navigate(Routes.Main(TAB_ORDERS)) { popUpTo(0) { inclusive = true } }
                    else nav.popBackStack()
                },
            )
            val current = order
            when {
                current == null && error == null -> CenteredLoader()
                current == null -> EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "Couldn't open order", error.orEmpty(), actionText = "Retry", onAction = { reloadKey++ })
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { ExitPassCard(current, justPlaced) }
                    if (!current.isPaid && current.status.equals("cancelled", true)) {
                        item {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text("Reservation expired", style = MaterialTheme.typography.titleMedium, color = PengoColors.Red)
                                Text(
                                    "This order wasn't paid within 30 minutes, so the items were released. If you already paid, tap below and enter your UPI reference.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PengoColors.Muted,
                                )
                                Spacer(Modifier.height(12.dp))
                                GradientButton("I already paid", { confirmPayment = true }, Modifier.fillMaxWidth(), brush = PengoGradients.lavender, loading = busy)
                            }
                        }
                    } else if (!current.isPaid) {
                        item { CounterQrCard(current) }
                        item {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text("Payment pending", style = MaterialTheme.typography.titleMedium, color = PengoColors.Warning)
                                Text(
                                    "Pay ${current.totalAmount.rupees()} by UPI now, or show the QR above at the counter. Items stay reserved for 30 minutes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PengoColors.Muted,
                                )
                                Spacer(Modifier.height(12.dp))
                                GradientButton("Pay ${current.totalAmount.rupees()} with UPI", {
                                    val intent = Intent(Intent.ACTION_VIEW, Upi.payUri(current.totalAmount, "PeNgo order ${current.id.shortId()}", current.id.shortId()))
                                    runCatching { upiLauncher.launch(Intent.createChooser(intent, "Pay with UPI")) }
                                        .onFailure { UiMessages.show("No UPI app found on this phone") }
                                }, Modifier.fillMaxWidth(), loading = busy)
                            }
                        }
                    }
                    item { BillCard(current) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GradientButton("Download", { invoice(share = false) }, Modifier.weight(1f), icon = Icons.Rounded.Download, loading = busy, brush = PengoGradients.mint)
                            GradientButton("Share", { invoice(share = true) }, Modifier.weight(1f), icon = Icons.Rounded.Share, enabled = !busy, brush = PengoGradients.peach)
                        }
                    }
                }
            }
        }
    }

    if (confirmPayment) {
        PaymentConfirmDialog(
            amount = order?.totalAmount ?: 0.0,
            onPaid = { confirmPayment = false; markPaid(it) },
            onDismiss = { confirmPayment = false },
        )
    }
}

@Composable
private fun CounterQrCard(order: Order) {
    val qr = remember(order.id) { QrCode.bitmap(ORDER_QR_PREFIX + order.id, 600).asImageBitmap() }
    GradientCard(PengoGradients.peach, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pay at counter", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "Show this QR to the cashier. Your exit pass appears here as soon as they confirm payment.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).padding(12.dp)) {
                Image(qr, "Order QR", Modifier.size(180.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("#${order.id.shortId()} • ${order.totalAmount.rupees()}", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExitPassCard(order: Order, justPlaced: Boolean) {
    val pass = order.exitPass
    if (!order.isPaid || pass == null) return
    val used = pass.isUsed
    if (pass.isExpired) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Exit pass expired", style = MaterialTheme.typography.titleMedium, color = PengoColors.Red)
            Text(
                "Exit passes are valid for 3 hours after payment. Please show this bill to the staff at the counter.",
                style = MaterialTheme.typography.bodySmall,
                color = PengoColors.Muted,
            )
        }
        return
    }
    val qr = remember(pass.id) { QrCode.bitmap(pass.id, 700).asImageBitmap() }
    GradientCard(if (used) PengoGradients.lavender else PengoGradients.mint, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (justPlaced) Icons.Rounded.CheckCircle else Icons.Rounded.VerifiedUser, null, tint = PengoColors.Ink)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (used) "Exit pass used" else if (justPlaced) "Payment successful!" else "Exit pass",
                    style = MaterialTheme.typography.titleLarge,
                    color = PengoColors.Ink,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    used -> "Verified at exit ${pass.verifiedAt.prettyDate()}"
                    pass.expiresAt != null -> "Show this QR at the store exit. Valid till ${pass.expiresAt.prettyDate()}."
                    else -> "Show this QR at the store exit and walk out."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PengoColors.Ink.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(14.dp)
            ) {
                Image(qr, "Exit pass QR", Modifier.size(210.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(pass.id, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PengoColors.Ink, letterSpacing = 2.sp)
            Text("${order.items.orEmpty().sumOf { it.quantity }} items • ${order.totalAmount.rupees()}", style = MaterialTheme.typography.bodySmall, color = PengoColors.Ink.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun BillCard(order: Order) {
    val items = order.items.orEmpty()
    val itemsTotal = items.sumOf { it.priceAtPurchase * it.quantity }
    val discount = (itemsTotal - order.totalAmount).coerceAtLeast(0.0)
    val (label, color) = orderStatus(order)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(order.store?.name ?: "PeNgo store", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                Text(order.createdAt.prettyDate(), style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
            }
            StatusChip(label, color)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = PengoColors.Divider)
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductImage(item.product?.imageUrl, Modifier.size(46.dp), corner = 12.dp, seed = item.productId.orEmpty())
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.product?.name ?: "Item", style = MaterialTheme.typography.bodyMedium, color = PengoColors.Ink, maxLines = 2)
                    Text("${item.priceAtPurchase.rupees()} × ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                }
                Text((item.priceAtPurchase * item.quantity).rupees(), style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = PengoColors.Divider)
        BillRow("Subtotal", itemsTotal.rupees())
        if (discount > 0.009) BillRow("Discount", "- ${discount.rupees()}", PengoColors.Success)
        BillRow("Payment", "${(order.paymentMethod ?: "-").uppercase()} • ${(order.paymentStatus ?: "unpaid").uppercase()}")
        order.paymentId?.let { BillRow("Reference", it) }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Total", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink, modifier = Modifier.weight(1f))
            Text(order.totalAmount.rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PengoColors.Ink)
        }
    }
}

@Composable
private fun BillRow(label: String, value: String, color: Color = PengoColors.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PengoColors.Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
