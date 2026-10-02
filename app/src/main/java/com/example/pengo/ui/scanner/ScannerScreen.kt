package com.example.pengo.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.pengo.data.CartRepository
import com.example.pengo.data.CatalogRepository
import com.example.pengo.data.OrderRepository
import com.example.pengo.data.Product
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.SCAN_MODE_STORE
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.ProductImage
import com.example.pengo.ui.components.QuantityStepper
import com.example.pengo.ui.components.rememberProductActions
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.Prefs
import com.example.pengo.util.rupees
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(nav: NavHostController, mode: String, storeId: String?) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        PengoBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Rounded.CameraAlt,
                    title = "Camera access needed",
                    message = "Allow camera access to scan store QR codes and product barcodes.",
                    actionText = "Allow camera",
                    onAction = { launcher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
        return
    }

    if (mode == SCAN_MODE_STORE) StoreQrScanner(nav) else ProductBarcodeScanner(nav, storeId)
}

@Composable
private fun StoreQrScanner(nav: NavHostController) {
    var handled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val beep = rememberBeep()
    ScannerFrame(
        title = "Scan store QR",
        hint = "Point the camera at the PeNgo QR code at the store entrance or billing desk.",
        onBack = { nav.popBackStack() },
        onCode = { code ->
            if (handled) return@ScannerFrame
            handled = true
            beep()
            scope.launch { OrderRepository.logScan(code, "store_qr") }
            nav.navigate(Routes.Store(qr = code)) {
                popUpTo<Routes.Scanner> { inclusive = true }
            }
        },
    )
}

@Composable
private fun ProductBarcodeScanner(nav: NavHostController, storeId: String?) {
    val scope = rememberCoroutineScope()
    val actions = rememberProductActions()
    val cart by CartRepository.lines.collectAsStateWithLifecycle()
    val beep = rememberBeep()
    var lastCode by remember { mutableStateOf<String?>(null) }
    var lastAt by remember { mutableStateOf(0L) }
    var looking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Product?>(null) }
    var notFound by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        ScannerFrame(
            title = "Scan product",
            hint = if (storeId != null) "Scan a barcode to add it straight to your cart." else "Scan any product barcode to find it.",
            onBack = { nav.popBackStack() },
            onCode = { code ->
                val now = System.currentTimeMillis()
                if (looking || (code == lastCode && now - lastAt < 3000)) return@ScannerFrame
                lastCode = code
                lastAt = now
                looking = true
                beep()
                scope.launch {
                    OrderRepository.logScan(code, "product_barcode")
                    runCatching { CatalogRepository.productByBarcode(code, storeId) }
                        .onSuccess { product ->
                            found = product
                            notFound = if (product == null) code else null
                        }
                        .onFailure { notFound = it.friendlyMessage() }
                    looking = false
                }
            },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            if (looking) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PengoColors.Blue)
                        Spacer(Modifier.width(12.dp))
                        Text("Looking up product…", color = PengoColors.Ink)
                    }
                }
            }
            AnimatedVisibility(found != null && !looking, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                val product = found ?: return@AnimatedVisibility
                val qty = cart.firstOrNull { it.product.id == product.id }?.quantity ?: 0
                GlassCard(Modifier.fillMaxWidth(), corner = 26.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(product.imageUrl, Modifier.size(76.dp), seed = product.id)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink, maxLines = 2)
                            Text(product.price.rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PengoColors.Ink)
                            Text(
                                if (product.inStock) "In stock" else "Out of stock",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (product.inStock) PengoColors.Success else PengoColors.Red,
                            )
                        }
                        if (qty > 0) QuantityStepper(qty, { actions.changeQuantity(product.id, it) }, compact = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton(
                            if (qty > 0) "Add one more" else "Add to cart",
                            { actions.addToCart(product) },
                            Modifier.weight(1f),
                            enabled = product.inStock,
                        )
                        GradientButton("Details", { nav.navigate(Routes.Product(product.id)) }, Modifier.weight(0.7f), brush = PengoGradients.mint)
                    }
                }
            }
            AnimatedVisibility(notFound != null && !looking, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Product not found", style = MaterialTheme.typography.titleSmall, color = PengoColors.Red)
                    Text(
                        if (storeId != null) "This barcode isn't listed in this store. Please ask the staff." else "No product matches barcode ${notFound.orEmpty()}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PengoColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberBeep(): () -> Unit {
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    return { if (Prefs.settings.value.scanSound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
}

@Composable
internal fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (granted) {
        content()
    } else {
        PengoBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Rounded.CameraAlt,
                    title = "Camera access needed",
                    message = "Allow camera access to scan QR codes.",
                    actionText = "Allow camera",
                    onAction = { launcher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }
}

@Composable
internal fun ScannerFrame(title: String, hint: String, onBack: () -> Unit, onCode: (String) -> Unit) {
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(onCode = onCode, onCamera = { camera = it })
        ScanOverlay()
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon({ Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }, onBack)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            RoundIcon({ Icon(if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Torch", tint = Color.White) }) {
                torch = !torch
                camera?.cameraControl?.enableTorch(torch)
            }
        }
        Text(
            hint,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 340.dp, start = 40.dp, end = 40.dp),
        )
    }
}

@Composable
private fun RoundIcon(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun ScanOverlay() {
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "line",
    )
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val box = size.minDimension * 0.68f
        val left = (size.width - box) / 2
        val top = (size.height - box) / 2 - 40.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(box, box),
            cornerRadius = CornerRadius(32.dp.toPx()),
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            brush = Brush.linearGradient(listOf(PengoColors.Blue, PengoColors.Cyan)),
            topLeft = Offset(left, top),
            size = Size(box, box),
            cornerRadius = CornerRadius(32.dp.toPx()),
            style = Stroke(width = 4.dp.toPx()),
        )
        val y = top + 16.dp.toPx() + (box - 32.dp.toPx()) * progress
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, PengoColors.Cyan, Color.Transparent)),
            start = Offset(left + 16.dp.toPx(), y),
            end = Offset(left + box - 16.dp.toPx(), y),
            strokeWidth = 3.dp.toPx(),
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onCode: (String) -> Unit, onCamera: (Camera) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember { BarcodeScanning.getClient() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val latestOnCode by androidx.compose.runtime.rememberUpdatedState(onCode)

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        codes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }?.let { latestOnCode(it) }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                onCamera(camera)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
