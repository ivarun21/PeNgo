package com.example.pengo.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pengo.data.StaffRepository
import com.example.pengo.util.Prefs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.pengo.data.CartRepository
import com.example.pengo.data.WishlistRepository
import com.example.pengo.ui.cart.CartScreen
import com.example.pengo.ui.explore.ExploreScreen
import com.example.pengo.ui.home.HomeScreen
import com.example.pengo.ui.orders.OrdersScreen
import com.example.pengo.ui.profile.ProfileScreen
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Rounded.Home),
    Tab("Explore", Icons.Rounded.Explore),
    Tab("Cart", Icons.Rounded.ShoppingCart),
    Tab("Orders", Icons.AutoMirrored.Rounded.ReceiptLong),
    Tab("Profile", Icons.Rounded.Person),
)

const val TAB_HOME = 0
const val TAB_EXPLORE = 1
const val TAB_CART = 2
const val TAB_ORDERS = 3
const val TAB_PROFILE = 4

@Composable
fun MainScreen(nav: NavHostController, initialTab: Int, onLoggedOut: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(initialTab) }
    val cart by CartRepository.lines.collectAsStateWithLifecycle()
    val cartCount = cart.sumOf { it.quantity }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.flag("asked_notifications")) {
            Prefs.setFlag("asked_notifications")
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { CartRepository.refresh() }
        runCatching { WishlistRepository.refreshIds() }
        StaffRepository.refreshStores()
    }

    BackHandler(enabled = selected != TAB_HOME) { selected = TAB_HOME }

    Box(Modifier.fillMaxSize().background(PengoGradients.background)) {
        Crossfade(targetState = selected, label = "tab") { tab ->
            when (tab) {
                TAB_HOME -> HomeScreen(nav, onOpenTab = { selected = it })
                TAB_EXPLORE -> ExploreScreen(nav)
                TAB_CART -> CartScreen(nav, onBrowse = { selected = TAB_HOME })
                TAB_ORDERS -> OrdersScreen(nav)
                else -> ProfileScreen(nav, onOpenTab = { selected = it }, onLoggedOut = onLoggedOut)
            }
        }
        PillBottomBar(
            selected = selected,
            cartCount = cartCount,
            onSelect = { selected = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PillBottomBar(selected: Int, cartCount: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(66.dp)
            .shadow(20.dp, shape, ambientColor = Color(0x3364A1FF), spotColor = Color(0x5564A1FF))
            .clip(shape)
            .background(Color.White)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = index == selected
            val tint by animateColorAsState(if (active) PengoColors.Ink else PengoColors.Muted, label = "tint")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) PengoGradients.primary else Brush0)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .padding(horizontal = if (active) 14.dp else 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == TAB_CART && cartCount > 0) {
                    BadgedBox(badge = {
                        Badge(containerColor = PengoColors.Red) { Text("$cartCount", color = Color.White) }
                    }) { Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(24.dp)) }
                } else {
                    Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(24.dp))
                }
                AnimatedVisibility(active, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Row {
                        Spacer(Modifier.width(6.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PengoColors.Ink)
                    }
                }
            }
        }
    }
}

private val Brush0 = androidx.compose.ui.graphics.SolidColor(Color.Transparent)
