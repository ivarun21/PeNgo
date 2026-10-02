package com.example.pengo.ui.cart

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.pengo.data.CartLine
import com.example.pengo.data.CartRepository
import com.example.pengo.data.CatalogRepository
import com.example.pengo.data.Coupon
import com.example.pengo.data.OrderRepository
import com.example.pengo.data.PaymentMethod
import com.example.pengo.data.Store
import com.example.pengo.data.discountFor
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.components.CenteredLoader
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.util.Notifier
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.IconBubble
import com.example.pengo.ui.components.ProductRow
import com.example.pengo.ui.components.QuantityStepper
import com.example.pengo.ui.components.UiMessages
import com.example.pengo.ui.components.rememberProductActions
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.Upi
import com.example.pengo.util.rupees
import kotlinx.coroutines.launch

@Composable
fun CartScreen(nav: NavHostController, onBrowse: () -> Unit) {
    val lines by CartRepository.lines.collectAsStateWithLifecycle()
    val actions = rememberProductActions()
    val scope = rememberCoroutineScope()
    var stores by remember { mutableStateOf<Map<String, Store>>(emptyMap()) }
    var couponCode by rememberSaveable { mutableStateOf("") }
    var coupon by remember { mutableStateOf<Coupon?>(null) }
    var couponError by remember { mutableStateOf<String?>(null) }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.UPI) }
    var placing by remember { mutableStateOf(false) }
    var confirmPayment by remember { mutableStateOf(false) }
    var pendingTxnRef by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOrdersCsv by rememberSaveable { mutableStateOf("") }
    val pendingOrderIds = pendingOrdersCsv.split(',').filter { it.isNotBlank() }
    var pendingTotal by rememberSaveable { mutableStateOf(0.0) }
    var confirmClear by remember { mutableStateOf(false) }

    val subtotal = lines.sumOf { it.lineTotal }
    val discount = coupon?.discountFor(subtotal) ?: 0.0
    val total = (subtotal - discount).coerceAtLeast(0.0)

    LaunchedEffect(Unit) { runCatching { CartRepository.refresh() } }
    LaunchedEffect(lines.mapNotNull { it.product.storeId }.toSet()) {
        val ids = lines.mapNotNull { it.product.storeId }.toSet()
        runCatching { CatalogRepository.storesByIds(ids) }.onSuccess { list -> stores = list.associateBy { it.id } }
    }

    fun openOrders(ids: List<String>) {
        if (ids.size > 1) UiMessages.show("${ids.size} orders created, one for each store")
        ids.firstOrNull()?.let { nav.navigate(Routes.OrderDetail(it, justPlaced = true)) }
    }

    fun finishPayment(ref: String) {
        val ids = pendingOrderIds
        if (ids.isEmpty()) return
        placing = true
        scope.launch {
            runCatching { OrderRepository.confirmUpi(ids, ref) }
                .onSuccess {
                    Notifier.orderUpdate("Payment successful", "Your exit pass is ready. Show it at the store exit.")
                    pendingOrdersCsv = ""
                    openOrders(ids)
                }
                .onFailure { UiMessages.show(it.friendlyMessage()); openOrders(ids) }
            placing = false
        }
    }

    fun leaveUnpaid() {
        val ids = pendingOrderIds
        pendingOrdersCsv = ""
        UiMessages.show("Order saved. Pay within 30 minutes to keep your items reserved.")
        openOrders(ids)
    }

    val upiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val response = Upi.parseResponse(result.data?.getStringExtra("response"))
        when (response.status) {
            Upi.Status.SUCCESS -> finishPayment(response.txnRef ?: pendingTxnRef ?: "UPI")
            Upi.Status.FAILED -> {
                UiMessages.show("Payment failed. No money was charged.")
                leaveUnpaid()
            }
            else -> confirmPayment = true
        }
    }

    fun launchUpi(amount: Double) {
        val ref = "PNG${System.currentTimeMillis()}"
        pendingTxnRef = ref
        val intent = Intent(Intent.ACTION_VIEW, Upi.payUri(amount, "PeNgo order", ref))
        runCatching { upiLauncher.launch(Intent.createChooser(intent, "Pay with UPI")) }
            .onFailure {
                UiMessages.show("No UPI app found on this phone")
                leaveUnpaid()
            }
    }

    fun checkout() {
        if (lines.isEmpty() || placing) return
        if (lines.any { !it.product.inStock }) {
            UiMessages.show("Remove out-of-stock items before checkout")
            return
        }
        placing = true
        scope.launch {
            runCatching {
                val result = OrderRepository.checkout(lines, method, coupon?.code)
                runCatching { CartRepository.clear() }
                result
            }.onSuccess { result ->
                coupon = null
                couponCode = ""
                if (method == PaymentMethod.COUNTER) {
                    Notifier.orderUpdate("Order placed", "Show the order QR at the billing counter to pay and get your exit pass.")
                    openOrders(result.orderIds)
                } else {
                    pendingOrdersCsv = result.orderIds.joinToString(",")
                    pendingTotal = result.total
                    launchUpi(result.total)
                }
            }.onFailure {
                UiMessages.show(it.friendlyMessage())
                runCatching { CartRepository.refresh() }
            }
            placing = false
        }
    }

    if (confirmPayment) {
        PaymentConfirmDialog(
            amount = pendingTotal,
            onPaid = { utr ->
                confirmPayment = false
                finishPayment(utr.ifBlank { pendingTxnRef ?: "UPI" })
            },
            onDismiss = {
                confirmPayment = false
                leaveUnpaid()
            },
        )
    }

    if (lines.isEmpty()) {
        if (placing) {
            CenteredLoader()
            return
        }
        Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Rounded.ShoppingCart,
                title = "Your cart is empty",
                message = "Scan a store QR or a product barcode to start adding items.",
                actionText = "Go to Home",
                onAction = onBrowse,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("My Cart", style = MaterialTheme.typography.headlineSmall, color = PengoColors.Ink)
                    Text("${lines.sumOf { it.quantity }} items", style = MaterialTheme.typography.bodyMedium, color = PengoColors.Muted)
                }
                TextButton(onClick = { confirmClear = true }) { Text("Clear", color = PengoColors.Red) }
            }
        }

        lines.groupBy { it.product.storeId }.forEach { (storeId, storeLines) ->
            item(key = "store-$storeId") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    IconBubble(Icons.Rounded.Storefront, PengoGradients.lavender, size = 30.dp, iconSize = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stores[storeId]?.name ?: "Store",
                        style = MaterialTheme.typography.titleSmall,
                        color = PengoColors.Ink,
                    )
                }
            }
            items(storeLines, key = { it.rowId }) { line ->
                CartLineRow(
                    line = line,
                    onClick = { nav.navigate(Routes.Product(line.product.id)) },
                    onQuantity = { actions.changeQuantity(line.product.id, it) },
                    onRemove = {
                        scope.launch { runCatching { CartRepository.remove(line) }.onFailure { UiMessages.show(it.friendlyMessage()) } }
                    },
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalOffer, null, tint = PengoColors.Purple)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply coupon", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                }
                Spacer(Modifier.height(10.dp))
                if (coupon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${coupon!!.code} applied • ${coupon!!.discountPercent ?: 0}% off",
                            color = PengoColors.Success,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { coupon = null; couponCode = "" }) { Text("Remove", color = PengoColors.Red) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = couponCode,
                            onValueChange = { couponCode = it.uppercase(); couponError = null },
                            placeholder = { Text("Enter code") },
                            singleLine = true,
                            isError = couponError != null,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = PengoColors.Divider),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = {
                                if (couponCode.isBlank()) return@OutlinedButton
                                scope.launch {
                                    runCatching { OrderRepository.findCoupon(couponCode) }
                                        .onSuccess {
                                            coupon = it
                                            couponError = if (it == null) "Invalid or expired coupon" else null
                                        }
                                        .onFailure { couponError = it.friendlyMessage() }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, PengoColors.Purple),
                        ) { Text("Apply", color = PengoColors.Purple) }
                    }
                    if (couponError != null) {
                        Text(couponError!!, color = PengoColors.Red, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Payment method", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                Spacer(Modifier.height(10.dp))
                PaymentOption(
                    "UPI",
                    "GPay, PhonePe, Paytm or any UPI app • get exit pass instantly",
                    Icons.Rounded.QrCode2,
                    method == PaymentMethod.UPI,
                ) { method = PaymentMethod.UPI }
                Spacer(Modifier.height(8.dp))
                PaymentOption(
                    "Pay at counter",
                    "Pay cash/card at the store desk",
                    Icons.Rounded.Payments,
                    method == PaymentMethod.COUNTER,
                ) { method = PaymentMethod.COUNTER }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SummaryRow("Subtotal", subtotal.rupees())
                if (discount > 0) SummaryRow("Coupon discount", "- ${discount.rupees()}", PengoColors.Success)
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = PengoColors.Divider)
                SummaryRow("Total", total.rupees(), bold = true)
                Spacer(Modifier.height(16.dp))
                GradientButton(
                    if (method == PaymentMethod.UPI) "Pay ${total.rupees()}" else "Place order",
                    ::checkout,
                    Modifier.fillMaxWidth(),
                    loading = placing,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear cart?") },
            text = { Text("All items will be removed from your cart.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { runCatching { CartRepository.clear() }.onFailure { UiMessages.show(it.friendlyMessage()) } }
                }) { Text("Clear", color = PengoColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CartLineRow(line: CartLine, onClick: () -> Unit, onQuantity: (Int) -> Unit, onRemove: () -> Unit) {
    ProductRow(
        product = line.product,
        subtitle = "${line.product.price.rupees()} × ${line.quantity} = ${line.lineTotal.rupees()}" +
            if (!line.product.inStock) "  •  Out of stock" else "",
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, "Remove", tint = PengoColors.Muted)
                }
                QuantityStepper(line.quantity, onQuantity, compact = true)
            }
        },
    )
}

@Composable
private fun PaymentOption(title: String, subtitle: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFEFF5FF) else Color.Transparent)
            .border(1.dp, if (selected) PengoColors.Blue else PengoColors.Divider, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, if (selected) PengoGradients.primary else PengoGradients.mint, size = 38.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
        }
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            null,
            tint = if (selected) PengoColors.PrimaryDeep else PengoColors.Divider,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = PengoColors.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (bold) PengoColors.Ink else PengoColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun PaymentConfirmDialog(amount: Double, onPaid: (String) -> Unit, onDismiss: () -> Unit) {
    var utr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(Modifier.size(52.dp).clip(CircleShape).background(PengoGradients.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.QrCode2, null, tint = PengoColors.Ink)
            }
        },
        title = { Text("Did the payment go through?") },
        text = {
            Column {
                Text(
                    "Your UPI app didn't confirm the payment of ${amount.rupees()}. If money was debited, enter the UPI reference (UTR) number so the store can verify it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = utr,
                    onValueChange = { utr = it.filter { c -> c.isLetterOrDigit() }.take(22) },
                    label = { Text("UPI reference / UTR") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPaid(utr) }, enabled = utr.length >= 6) { Text("Yes, I paid") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not paid") } },
    )
}
