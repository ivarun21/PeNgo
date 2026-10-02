package com.example.pengo.ui.staff

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.pengo.data.StaffRepository
import com.example.pengo.data.StaffStore
import com.example.pengo.data.StoreDashboard
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.components.CenteredLoader
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientCard
import com.example.pengo.ui.components.IconBubble
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.PengoTopBar
import com.example.pengo.ui.components.ShimmerBox
import com.example.pengo.ui.components.StatusChip
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.prettyDate
import com.example.pengo.util.rupees
import com.example.pengo.util.shortId

fun roleLabel(role: String) = when (role) {
    "manager" -> "Manager"
    "cashier" -> "Cashier"
    "guard" -> "Guard"
    else -> role.replaceFirstChar { it.uppercase() }
}

@Composable
fun StaffHomeScreen(nav: NavHostController) {
    val stores by StaffRepository.stores.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(stores.isNotEmpty()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var dashboard by remember { mutableStateOf<StoreDashboard?>(null) }
    var dashError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        StaffRepository.refreshStores()
        loaded = true
    }
    val selected = stores.firstOrNull { it.storeId == selectedId } ?: stores.firstOrNull()

    LaunchedEffect(selected?.storeId, reloadKey) {
        val store = selected ?: return@LaunchedEffect
        if (store.role == "guard") return@LaunchedEffect
        runCatching { StaffRepository.dashboard(store.storeId) }
            .onSuccess { dashboard = it; dashError = null }
            .onFailure { dashError = it.friendlyMessage() }
        refreshing = false
    }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("Staff mode", onBack = { nav.popBackStack() })
            when {
                !loaded -> CenteredLoader()
                selected == null -> EmptyState(
                    Icons.Rounded.Storefront,
                    "You're not store staff yet",
                    "Ask your store manager to add your email from Staff mode > Team.",
                    brush = PengoGradients.lavender,
                )
                else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reloadKey++ }) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (stores.size > 1) {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(stores, key = { it.storeId }) { s ->
                                        val active = s.storeId == selected.storeId
                                        Text(
                                            s.storeName ?: "Store",
                                            color = if (active) Color.White else PengoColors.Ink,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(if (active) PengoColors.PrimaryDeep else Color.White)
                                                .clickable { selectedId = s.storeId; dashboard = null }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                        item { StoreHeader(selected) }
                        item { StaffActions(nav, selected) }
                        if (selected.role != "guard") {
                            val d = dashboard
                            when {
                                d != null -> dashboardItems(d)
                                dashError != null -> item {
                                    EmptyState(Icons.AutoMirrored.Rounded.TrendingUp, "Couldn't load the dashboard", dashError.orEmpty(), actionText = "Retry", onAction = { reloadKey++ })
                                }
                                else -> item { ShimmerBox(Modifier.fillMaxWidth().height(220.dp), corner = 24.dp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreHeader(store: StaffStore) {
    GradientCard(PengoGradients.primary, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Storefront, null, tint = PengoColors.PrimaryDeep)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(store.storeName ?: "Store", style = MaterialTheme.typography.titleLarge, color = PengoColors.Ink)
                Text(
                    "Signed in as ${roleLabel(store.role)}" + (store.storeCode?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = PengoColors.Ink.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun StaffActions(nav: NavHostController, store: StaffStore) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(
            if (store.role == "guard") "Verify exit" else "Scan QR",
            Icons.Rounded.QrCodeScanner,
            PengoGradients.mint,
            Modifier.weight(1f),
        ) { nav.navigate(Routes.StaffScan(store.storeId)) }
        if (store.isManager) {
            ActionTile("Inventory", Icons.Rounded.Inventory2, PengoGradients.peach, Modifier.weight(1f)) {
                nav.navigate(Routes.Inventory(store.storeId))
            }
            ActionTile("Team", Icons.Rounded.Groups, PengoGradients.lavender, Modifier.weight(1f)) {
                nav.navigate(Routes.StaffTeam(store.storeId))
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, corner = 20.dp, padding = PaddingValues(vertical = 14.dp, horizontal = 6.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBubble(icon, brush, size = 46.dp, iconSize = 22.dp)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = PengoColors.Ink)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dashboardItems(d: StoreDashboard) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Today", d.todaySales.rupees(), "${d.todayOrders} orders", Modifier.weight(1f))
            Metric("Last 7 days", d.weekSales.rupees(), "${d.pendingOrders} awaiting payment", Modifier.weight(1f))
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("Last 30 days", d.monthSales.rupees(), "${d.monthOrders} orders", Modifier.weight(1f))
            Metric("Customers", d.customers30d.toString(), "in 30 days", Modifier.weight(1f))
        }
    }
    item { SalesChart(d) }
    val insights = insightsFor(d)
    if (insights.isNotEmpty()) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Lightbulb, PengoGradients.peach, size = 34.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Store insights", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                }
                insights.forEach { line ->
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Text("•  ", color = PengoColors.PrimaryDeep, fontWeight = FontWeight.Bold)
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = PengoColors.Ink)
                    }
                }
            }
        }
    }
    if (d.topProducts.isNotEmpty()) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Top sellers (30 days)", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                d.topProducts.forEachIndexed { i, p ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = PengoColors.PrimaryDeep, modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink, maxLines = 1)
                            Text("${p.qty} sold • ${p.stock} left", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                        }
                        Text(p.revenue.rupees(), style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                    }
                }
            }
        }
    }
    if (d.lowStock.isNotEmpty()) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Low stock", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                d.lowStock.forEach { p ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, color = PengoColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                        StatusChip(if (p.stock <= 0) "OUT" else "${p.stock} LEFT", if (p.stock <= 0) PengoColors.Red else PengoColors.Warning)
                    }
                }
            }
        }
    }
    if (d.recentOrders.isNotEmpty()) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Recent orders", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                d.recentOrders.forEach { o ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${o.id.shortId()} • ${o.customer ?: "Customer"}", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink, maxLines = 1)
                            Text("${o.createdAt.prettyDate()} • ${(o.paymentMethod ?: "-").uppercase()}", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(o.total.rupees(), style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                            val (label, color) = when {
                                o.paymentStatus == "paid" -> "PAID" to PengoColors.Success
                                o.status == "cancelled" -> "EXPIRED" to PengoColors.Muted
                                else -> "PENDING" to PengoColors.Warning
                            }
                            StatusChip(label, color)
                        }
                    }
                }
            }
        }
    }
}

private fun insightsFor(d: StoreDashboard): List<String> = buildList {
    if (d.pendingOrders > 0) add("${d.pendingOrders} order(s) are waiting for payment at the counter.")
    d.topProducts.forEach { p ->
        val perDay = p.qty / 30.0
        if (perDay > 0 && p.stock > 0) {
            val daysLeft = (p.stock / perDay).toInt()
            if (daysLeft < 7) add("${p.name} is selling fast. Only about $daysLeft day(s) of stock left, restock soon.")
        }
    }
    d.lowStock.filter { it.stock <= 0 }.take(3).forEach { add("${it.name} is out of stock. Customers can't buy it right now.") }
    d.requests.take(3).forEach { add("${it.n} customer(s) asked for ${it.name} to be restocked.") }
    d.deadStock.take(3).forEach { add("${it.name} hasn't sold in 30 days (${it.stock} in stock). Try a discount or move it to the front.") }
    if (d.todayOrders == 0 && d.weekSales > 0) add("No sales yet today. Last 7 days brought in ${d.weekSales.rupees()}.")
}

@Composable
private fun Metric(label: String, value: String, sub: String, modifier: Modifier) {
    GlassCard(modifier, corner = 20.dp, padding = PaddingValues(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = PengoColors.Ink, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted, maxLines = 1)
    }
}

@Composable
private fun SalesChart(d: StoreDashboard) {
    val days = d.daily
    if (days.isEmpty()) return
    val max = days.maxOf { it.total }.coerceAtLeast(1.0)
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Sales this week", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / days.size
            val barWidth = slot * 0.55f
            days.forEachIndexed { i, day ->
                val h = (day.total / max).toFloat() * size.height
                drawRoundRect(
                    color = Color(0xFFEFF3F8),
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(12f),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(PengoColors.Cyan, PengoColors.Blue)),
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(12f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(day.day, style = MaterialTheme.typography.labelSmall, color = PengoColors.Muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
