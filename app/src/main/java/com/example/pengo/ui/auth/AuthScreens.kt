package com.example.pengo.ui.auth

import android.util.Patterns
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pengo.R
import com.example.pengo.data.AuthDestination
import com.example.pengo.data.AuthRepository
import com.example.pengo.data.Backend
import com.example.pengo.data.SignUpResult
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.PengoTextField
import com.example.pengo.ui.components.PengoTopBar
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PengoLogo(size: Dp = 96.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(18.dp, RoundedCornerShape(size / 3), spotColor = Color(0x6664A1FF))
            .clip(RoundedCornerShape(size / 3))
            .background(PengoGradients.primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "PeNgo", modifier = Modifier.size(size * 1.4f))
    }
}

@Composable
private fun DecorBlobs() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(260.dp)
                .offset(x = (-80).dp, y = (-60).dp)
                .blur(60.dp)
                .alpha(0.55f)
                .clip(CircleShape)
                .background(PengoGradients.primary)
        )
        Box(
            Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = 120.dp)
                .blur(60.dp)
                .alpha(0.45f)
                .clip(CircleShape)
                .background(PengoGradients.peach)
        )
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 80.dp)
                .blur(70.dp)
                .alpha(0.4f)
                .clip(CircleShape)
                .background(PengoGradients.mint)
        )
    }
}

@Composable
fun SplashScreen(onResolved: (AuthDestination) -> Unit) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { alpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        val destination = async { runCatching { AuthRepository.resolveStartDestination() }.getOrDefault(AuthDestination.Login) }
        delay(1400)
        onResolved(destination.await())
    }

    PengoBackground {
        DecorBlobs()
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.scale(scale.value)) { PengoLogo(120.dp) }
            Spacer(Modifier.height(24.dp))
            Text(
                "PeNgo",
                style = MaterialTheme.typography.displaySmall,
                color = PengoColors.Ink,
                modifier = Modifier.alpha(alpha.value),
            )
            Text(
                "Scan • Pay • Walk out",
                style = MaterialTheme.typography.bodyLarge,
                color = PengoColors.Muted,
                modifier = Modifier.alpha(alpha.value),
            )
        }
    }
}

@Composable
private fun AuthHeader(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        PengoLogo(84.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = PengoColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = PengoColors.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error == null) return
    Text(
        error,
        color = PengoColors.Red,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PengoColors.Red.copy(alpha = 0.08f))
            .padding(12.dp),
    )
}

@Composable
private fun ConfigWarning() {
    if (Backend.isConfigured) return
    Text(
        "Server keys missing: add PENGO_ANON_KEY in local.properties and rebuild the app.",
        color = PengoColors.Warning,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PengoColors.Warning.copy(alpha = 0.1f))
            .padding(12.dp),
    )
}

@Composable
fun LoginScreen(onAuthenticated: (AuthDestination) -> Unit, onRegister: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var googleStarted by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (Backend.isConfigured) {
        LaunchedEffect(googleStarted) {
            if (!googleStarted) return@LaunchedEffect
            AuthRepository.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    loading = true
                    runCatching { AuthRepository.destinationForCurrentUser() }
                        .onSuccess(onAuthenticated)
                        .onFailure { error = it.friendlyMessage(); loading = false }
                }
            }
        }
    }

    fun submit() {
        error = when {
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
            password.length < 6 -> "Password must be at least 6 characters."
            else -> null
        }
        if (error != null) return
        loading = true
        scope.launch {
            runCatching { AuthRepository.signIn(email, password) }
                .onSuccess(onAuthenticated)
                .onFailure { error = it.friendlyMessage() }
            loading = false
        }
    }

    PengoBackground {
        DecorBlobs()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AuthHeader("Welcome back", "Log in to scan, shop and skip the billing queue.")
            Spacer(Modifier.height(28.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 28.dp, padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                ConfigWarning()
                PengoTextField(email, { email = it }, "Email", leadingIcon = Icons.Rounded.Email, keyboardType = KeyboardType.Email)
                Spacer(Modifier.height(12.dp))
                PengoTextField(password, { password = it }, "Password", leadingIcon = Icons.Rounded.Lock, isPassword = true)
                Spacer(Modifier.height(12.dp))
                ErrorText(error)
                Spacer(Modifier.height(12.dp))
                GradientButton("Log in", ::submit, Modifier.fillMaxWidth(), loading = loading)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = PengoColors.Divider)
                    Text("  or  ", color = PengoColors.Muted, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.weight(1f), color = PengoColors.Divider)
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        error = null
                        googleStarted = true
                        scope.launch {
                            runCatching { AuthRepository.signInWithGoogle() }
                                .onFailure { error = it.friendlyMessage(); googleStarted = false }
                        }
                    },
                    enabled = !loading,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, PengoColors.Divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text("G", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PengoColors.Blue)
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google", color = PengoColors.Ink)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("New to PeNgo?", color = PengoColors.Muted)
                TextButton(onClick = onRegister) { Text("Create account", color = PengoColors.PrimaryDeep, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun RegisterScreen(onAuthenticated: (AuthDestination) -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        info = null
        error = when {
            name.trim().length < 2 -> "Please enter your full name."
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
            phone.filter { it.isDigit() }.length !in 10..13 -> "Enter a valid mobile number."
            password.length < 6 -> "Password must be at least 6 characters."
            password != confirm -> "Passwords do not match."
            else -> null
        }
        if (error != null) return
        loading = true
        scope.launch {
            runCatching { AuthRepository.signUp(name, email, phone, password) }
                .onSuccess { result ->
                    when (result) {
                        SignUpResult.LoggedIn -> onAuthenticated(AuthDestination.Home)
                        SignUpResult.VerifyEmail ->
                            info = "Account created! We sent a verification link to ${email.trim()}. Verify it, then log in."
                    }
                }
                .onFailure { error = it.friendlyMessage() }
            loading = false
        }
    }

    PengoBackground {
        DecorBlobs()
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                AuthHeader("Create account", "Join PeNgo and shop without waiting in lines.")
                Spacer(Modifier.height(24.dp))
                GlassCard(Modifier.fillMaxWidth(), corner = 28.dp, padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                    ConfigWarning()
                    PengoTextField(name, { name = it }, "Full name", leadingIcon = Icons.Rounded.Person)
                    Spacer(Modifier.height(10.dp))
                    PengoTextField(email, { email = it }, "Email", leadingIcon = Icons.Rounded.Email, keyboardType = KeyboardType.Email)
                    Spacer(Modifier.height(10.dp))
                    PengoTextField(phone, { phone = it }, "Mobile number", leadingIcon = Icons.Rounded.Phone, keyboardType = KeyboardType.Phone)
                    Spacer(Modifier.height(10.dp))
                    PengoTextField(password, { password = it }, "Password", leadingIcon = Icons.Rounded.Lock, isPassword = true)
                    Spacer(Modifier.height(10.dp))
                    PengoTextField(confirm, { confirm = it }, "Confirm password", leadingIcon = Icons.Rounded.Lock, isPassword = true)
                    Spacer(Modifier.height(12.dp))
                    ErrorText(error)
                    if (info != null) {
                        Text(
                            info!!,
                            color = PengoColors.Success,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PengoColors.Success.copy(alpha = 0.1f))
                                .padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Create account", ::submit, Modifier.fillMaxWidth(), loading = loading, brush = PengoGradients.peach)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Already have an account?", color = PengoColors.Muted)
                    TextButton(onClick = onBack) { Text("Log in", color = PengoColors.PrimaryDeep, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(onDone: () -> Unit, onLogout: () -> Unit) {
    val defaults = remember { runCatching { AuthRepository.pendingProfileDefaults() }.getOrDefault("" to "") }
    var name by rememberSaveable { mutableStateOf(defaults.first) }
    var phone by rememberSaveable { mutableStateOf(defaults.second) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    PengoBackground {
        DecorBlobs()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AuthHeader("Complete your profile", "Just one step left before you start shopping.")
            Spacer(Modifier.height(24.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 28.dp, padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                PengoTextField(name, { name = it }, "Full name", leadingIcon = Icons.Rounded.Person)
                Spacer(Modifier.height(10.dp))
                PengoTextField(phone, { phone = it }, "Mobile number", leadingIcon = Icons.Rounded.Phone, keyboardType = KeyboardType.Phone)
                Spacer(Modifier.height(12.dp))
                ErrorText(error)
                Spacer(Modifier.height(12.dp))
                GradientButton("Continue", {
                    error = when {
                        name.trim().length < 2 -> "Please enter your full name."
                        phone.filter { it.isDigit() }.length !in 10..13 -> "Enter a valid mobile number."
                        else -> null
                    }
                    if (error == null) {
                        loading = true
                        scope.launch {
                            runCatching {
                                AuthRepository.saveProfile(AuthRepository.requireUserId(), name, phone, AuthRepository.currentEmail())
                            }
                                .onSuccess { onDone() }
                                .onFailure { error = it.friendlyMessage() }
                            loading = false
                        }
                    }
                }, Modifier.fillMaxWidth(), loading = loading, brush = PengoGradients.mint)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { scope.launch { AuthRepository.signOut(); onLogout() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Use a different account", color = PengoColors.Muted)
            }
        }
    }
}
