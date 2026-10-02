package com.example.pengo.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pengo.ui.auth.PengoLogo
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.profile.ShoppingListScreen
import com.example.pengo.ui.staff.InventoryScreen
import com.example.pengo.ui.staff.StaffHomeScreen
import com.example.pengo.ui.staff.StaffScanScreen
import com.example.pengo.ui.staff.StaffTeamScreen
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.AppLock
import com.example.pengo.util.NetworkMonitor
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.pengo.data.AuthDestination
import com.example.pengo.ui.auth.LoginScreen
import com.example.pengo.ui.auth.ProfileSetupScreen
import com.example.pengo.ui.auth.RegisterScreen
import com.example.pengo.ui.auth.SplashScreen
import com.example.pengo.ui.components.UiMessages
import com.example.pengo.ui.main.MainScreen
import com.example.pengo.ui.orders.OrderDetailScreen
import com.example.pengo.ui.product.ProductDetailScreen
import com.example.pengo.ui.product.ProductListScreen
import com.example.pengo.ui.profile.EditProfileScreen
import com.example.pengo.ui.profile.ScanHistoryScreen
import com.example.pengo.ui.profile.SettingsScreen
import com.example.pengo.ui.profile.WishlistScreen
import com.example.pengo.ui.scanner.ScannerScreen
import com.example.pengo.ui.store.StoreScreen
import com.example.pengo.ui.theme.PengoColors
import kotlinx.serialization.Serializable

object Routes {
    @Serializable object Splash
    @Serializable object Login
    @Serializable object Register
    @Serializable object ProfileSetup
    @Serializable data class Main(val tab: Int = 0)
    @Serializable data class Store(val storeId: String? = null, val qr: String? = null)
    @Serializable data class Product(val productId: String)
    @Serializable data class Scanner(val mode: String, val storeId: String? = null)
    @Serializable data class ProductList(val title: String, val categoryId: Int? = null, val query: String? = null)
    @Serializable data class OrderDetail(val orderId: String, val justPlaced: Boolean = false)
    @Serializable object Wishlist
    @Serializable object EditProfile
    @Serializable object Settings
    @Serializable object ScanHistory
    @Serializable object Wallet
    @Serializable object ShoppingList
    @Serializable object Staff
    @Serializable data class StaffScan(val storeId: String)
    @Serializable data class Inventory(val storeId: String)
    @Serializable data class StaffTeam(val storeId: String)
}

const val SCAN_MODE_STORE = "store"
const val SCAN_MODE_PRODUCT = "product"

fun NavHostController.goToAuthDestination(dest: AuthDestination) {
    val target: Any = when (dest) {
        AuthDestination.Home -> Routes.Main()
        AuthDestination.ProfileSetup -> Routes.ProfileSetup
        AuthDestination.Login -> Routes.Login
    }
    navigate(target) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun PengoNavHost() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        UiMessages.messages.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.Splash,
            enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
        ) {
            composable<Routes.Splash>(enterTransition = { fadeIn() }) {
                SplashScreen(onResolved = { nav.goToAuthDestination(it) })
            }
            composable<Routes.Login> {
                LoginScreen(
                    onAuthenticated = { nav.goToAuthDestination(it) },
                    onRegister = { nav.navigate(Routes.Register) },
                )
            }
            composable<Routes.Register> {
                RegisterScreen(
                    onAuthenticated = { nav.goToAuthDestination(it) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Routes.ProfileSetup> {
                ProfileSetupScreen(
                    onDone = { nav.goToAuthDestination(AuthDestination.Home) },
                    onLogout = { nav.goToAuthDestination(AuthDestination.Login) },
                )
            }
            composable<Routes.Main> { entry ->
                MainScreen(
                    nav = nav,
                    initialTab = entry.toRoute<Routes.Main>().tab,
                    onLoggedOut = { nav.goToAuthDestination(AuthDestination.Login) },
                )
            }
            composable<Routes.Store> { entry ->
                val route = entry.toRoute<Routes.Store>()
                StoreScreen(nav = nav, storeId = route.storeId, qr = route.qr)
            }
            composable<Routes.Product> { entry ->
                ProductDetailScreen(nav = nav, productId = entry.toRoute<Routes.Product>().productId)
            }
            composable<Routes.Scanner> { entry ->
                val route = entry.toRoute<Routes.Scanner>()
                ScannerScreen(nav = nav, mode = route.mode, storeId = route.storeId)
            }
            composable<Routes.ProductList> { entry ->
                val route = entry.toRoute<Routes.ProductList>()
                ProductListScreen(nav = nav, title = route.title, categoryId = route.categoryId, query = route.query)
            }
            composable<Routes.OrderDetail> { entry ->
                val route = entry.toRoute<Routes.OrderDetail>()
                OrderDetailScreen(nav = nav, orderId = route.orderId, justPlaced = route.justPlaced)
            }
            composable<Routes.Wishlist> { WishlistScreen(nav) }
            composable<Routes.EditProfile> { EditProfileScreen(nav) }
            composable<Routes.Settings> {
                SettingsScreen(nav, onLoggedOut = { nav.goToAuthDestination(AuthDestination.Login) })
            }
            composable<Routes.ScanHistory> { ScanHistoryScreen(nav) }
            composable<Routes.Wallet> { com.example.pengo.ui.profile.WalletScreen(nav) }
            composable<Routes.ShoppingList> { ShoppingListScreen(nav) }
            composable<Routes.Staff> { StaffHomeScreen(nav) }
            composable<Routes.StaffScan> { entry -> StaffScanScreen(nav, entry.toRoute<Routes.StaffScan>().storeId) }
            composable<Routes.Inventory> { entry -> InventoryScreen(nav, entry.toRoute<Routes.Inventory>().storeId) }
            composable<Routes.StaffTeam> { entry -> StaffTeamScreen(nav, entry.toRoute<Routes.StaffTeam>().storeId) }
        }

        OfflineBanner(Modifier.align(Alignment.TopCenter))

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
        ) { data ->
            Snackbar(
                shape = RoundedCornerShape(16.dp),
                containerColor = PengoColors.Ink,
                contentColor = Color.White,
            ) { Text(data.visuals.message) }
        }

        LockOverlay()
    }
}

@Composable
private fun OfflineBanner(modifier: Modifier) {
    val online by NetworkMonitor.online.collectAsStateWithLifecycle()
    AnimatedVisibility(!online, modifier = modifier, enter = slideInVertically { -it }, exit = slideOutVertically { -it }) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(PengoColors.Ink)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.CloudOff, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("You're offline. Showing saved data.", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LockOverlay() {
    val locked by AppLock.locked.collectAsStateWithLifecycle()
    if (!locked) return
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(Unit) { activity?.let { AppLock.prompt(it) } ?: AppLock.unlockWithoutPrompt() }
    BackHandler { activity?.moveTaskToBack(true) }
    Box(
        Modifier
            .fillMaxSize()
            .background(PengoGradients.background)
            .clickable(interactionSource = null, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            PengoLogo(size = 96.dp)
            Spacer(Modifier.height(20.dp))
            Text("PeNgo is locked", style = MaterialTheme.typography.headlineSmall, color = PengoColors.Ink)
            Spacer(Modifier.height(6.dp))
            Text("Unlock to continue shopping", style = MaterialTheme.typography.bodyMedium, color = PengoColors.Muted)
            Spacer(Modifier.height(24.dp))
            GradientButton("Unlock", { activity?.let { AppLock.prompt(it) } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Fingerprint)
        }
    }
}

private fun Context.findActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
