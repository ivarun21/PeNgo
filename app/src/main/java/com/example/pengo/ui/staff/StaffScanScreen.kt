package com.example.pengo.ui.staff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.pengo.data.ScanResult
import com.example.pengo.data.StaffRepository
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.ProductImage
import com.example.pengo.ui.components.UiMessages
import com.example.pengo.ui.scanner.CameraPermissionGate
import com.example.pengo.ui.scanner.ScannerFrame
import com.example.pengo.ui.scanner.rememberBeep
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.prettyDate
import com.example.pengo.util.rupees
import com.example.pengo.util.shortId
import kotlinx.coroutines.launch

private data class Verdict(val title: String, val message: String, val color: Color, val icon: ImageVector)

private fun verdictOf(r: ScanResult): Verdict = when (r.verdict) {
    "valid" -> Verdict("Valid exit pass", "Paid and not used yet. Check the items, then allow exit.", PengoColors.Success, Icons.Rounded.CheckCircle)
    "used" -> Verdict("Already used", "This pass was used ${r.verifiedAt.prettyDate()}. Don't allow exit with it again.", PengoColors.Red, Icons.Rounded.Block)
    "expired" -> Verdict("Pass expired", "Exit passes are valid for 3 hours after payment. Check the bill manually.", PengoColors.Warning, Icons.Rounded.Schedule)
    "unpaid" -> Verdict("Payment pending", "This order hasn't been paid yet.", PengoColors.Warning, Icons.Rounded.Payments)
    "cancelled" -> Verdict("Order expired", "It wasn't paid within 30 minutes and the items were released.", PengoColors.Red, Icons.Rounded.Block)
    "no_pass" -> Verdict("No exit pass", "The order is paid but has no exit pass. Ask the customer to reopen the order.", PengoColors.Warning, Icons.Rounded.ErrorOutline)
    "wrong_store" -> Verdict("Different store", "This order belongs to another store.", PengoColors.Red, Icons.Rounded.Block)
    else -> Verdict("Not recognised", "This isn't a PeNgo exit pass or order QR.", PengoColors.Red, Icons.Rounded.ErrorOutline)
}

@Composable
fun StaffScanScreen(nav: NavHostController, storeId: String) {
    val stores by StaffRepository.stores.collectAsStateWithLifecycle()
    val role = stores.firstOrNull { it.storeId == storeId }
    val scope = rememberCoroutineScope()
    val beep = rememberBeep()
    val haptic = LocalHapticFeedback.current
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var exitAllowed by remember { mutableStateOf(false) }

    fun lookup(code: String) {
        busy = true
        scope.launch {
            runCatching { StaffRepository.lookup(code) }
                .onSuccess { result = it }
                .onFailure { UiMessages.show(it.friendlyMessage()) }
            busy = false
        }
    }

    CameraPermissionGate {
        Box(Modifier.fillMaxSize()) {
            ScannerFrame(
                title = if (role?.role == "guard") "Verify exit pass" else "Scan customer QR",
                hint = "Scan the exit pass or the order QR on the customer's phone.",
                onBack = { nav.popBackStack() },
                onCode = { code ->
                    if (busy || result != null) return@ScannerFrame
                    beep()
                    lookup(code)
                },
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                if (busy && result == null) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PengoColors.Blue)
                            Spacer(Modifier.width(12.dp))
                            Text("Checking…", color = PengoColors.Ink)
                        }
                    }
                }
                AnimatedVisibility(result != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    val r = result ?: return@AnimatedVisibility
                    ResultSheet(
                        r = r,
                        exitAllowed = exitAllowed,
                        canCollect = role?.canCollectPayment == true,
                        busy = busy,
                        onAllowExit = {
                            val passId = r.passId ?: return@ResultSheet
                            busy = true
                            scope.launch {
                                runCatching { StaffRepository.allowExit(passId) }
                                    .onSuccess {
                                        result = it
                                        exitAllowed = it.ok == true
                                        if (exitAllowed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        else UiMessages.show("Couldn't verify: ${verdictOf(it).title}")
                                    }
                                    .onFailure { UiMessages.show(it.friendlyMessage()) }
                                busy = false
                            }
                        },
                        onCollect = { method ->
                            val orderId = r.orderId ?: return@ResultSheet
                            busy = true
                            scope.launch {
                                runCatching {
                                    StaffRepository.collectPayment(orderId, method)
                                    StaffRepository.lookup(orderId)
                                }.onSuccess {
                                    result = it
                                    UiMessages.show("Payment recorded. Exit pass issued.")
                                }.onFailure { UiMessages.show(it.friendlyMessage()) }
                                busy = false
                            }
                        },
                        onNext = {
                            result = null
                            exitAllowed = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultSheet(
    r: ScanResult,
    exitAllowed: Boolean,
    canCollect: Boolean,
    busy: Boolean,
    onAllowExit: () -> Unit,
    onCollect: (String) -> Unit,
    onNext: () -> Unit,
) {
    val v = if (exitAllowed) {
        Verdict("Exit allowed", "Pass marked as used. Have a nice day!", PengoColors.Success, Icons.Rounded.CheckCircle)
    } else verdictOf(r)

    GlassCard(Modifier.fillMaxWidth().heightIn(max = 520.dp), corner = 26.dp) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(v.icon, null, tint = v.color, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(v.title, style = MaterialTheme.typography.titleLarge, color = v.color)
                    Text(v.message, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                }
            }
            if (r.orderId != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = PengoColors.Divider)
                Text(
                    "${r.customer ?: "Customer"} • Order #${r.orderId.shortId()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = PengoColors.Ink,
                )
                Text(
                    "${r.createdAt.prettyDate()} • ${(r.paymentMethod ?: "-").uppercase()} ${(r.paymentStatus ?: "").uppercase()}" +
                        (r.paymentRef?.let { "\nRef: $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = PengoColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                r.items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(item.image, Modifier.size(38.dp), corner = 10.dp, seed = item.name)
                        Spacer(Modifier.width(10.dp))
                        Text("${item.name} × ${item.quantity}", style = MaterialTheme.typography.bodyMedium, color = PengoColors.Ink, modifier = Modifier.weight(1f))
                        Text((item.price * item.quantity).rupees(), style = MaterialTheme.typography.bodyMedium, color = PengoColors.Ink)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("${r.items.sumOf { it.quantity }} items", style = MaterialTheme.typography.titleSmall, color = PengoColors.Muted, modifier = Modifier.weight(1f))
                    Text(r.total.rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PengoColors.Ink)
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                exitAllowed -> Unit
                r.verdict == "valid" && r.passId != null ->
                    GradientButton("Allow exit", onAllowExit, Modifier.fillMaxWidth(), brush = PengoGradients.mint, loading = busy)
                (r.verdict == "unpaid" || r.verdict == "cancelled") && canCollect -> {
                    Text("Collect ${r.total.rupees()} from the customer:", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton("Cash", { onCollect("cash") }, Modifier.weight(1f), icon = Icons.Rounded.Payments, loading = busy, brush = PengoGradients.mint)
                        GradientButton("Card", { onCollect("card") }, Modifier.weight(1f), icon = Icons.Rounded.CreditCard, enabled = !busy, brush = PengoGradients.lavender)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GradientButton("Scan next", onNext, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}
