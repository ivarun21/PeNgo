package com.example.pengo.ui.staff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.pengo.data.CatalogRepository
import com.example.pengo.data.Category
import com.example.pengo.data.Product
import com.example.pengo.data.ProductUpsert
import com.example.pengo.data.StaffMember
import com.example.pengo.data.StaffRepository
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.components.CenteredLoader
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.IconBubble
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.PengoTopBar
import com.example.pengo.ui.components.ProductImage
import com.example.pengo.ui.components.QuantityStepper
import com.example.pengo.ui.components.StatusChip
import com.example.pengo.ui.components.UiMessages
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.rupees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun InventoryScreen(nav: NavHostController, storeId: String) {
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<Product>?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Product?>(null) }
    var creating by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        runCatching { StaffRepository.inventory(storeId) }
            .onSuccess { products = it; error = null }
            .onFailure { error = it.friendlyMessage() }
        if (categories.isEmpty()) categories = runCatching { CatalogRepository.categories() }.getOrDefault(emptyList())
    }

    fun changeStock(product: Product, stock: Int) {
        products = products?.map { if (it.id == product.id) it.copy(stockQuantity = stock) else it }
        scope.launch {
            runCatching { StaffRepository.setStock(product.id, stock) }
                .onFailure { UiMessages.show(it.friendlyMessage()); reloadKey++ }
        }
    }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("Inventory", onBack = { nav.popBackStack() })
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by name or barcode") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PengoColors.Muted) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            val list = products
            when {
                list == null && error == null -> CenteredLoader()
                list == null -> EmptyState(Icons.Rounded.Inventory2, "Couldn't load inventory", error.orEmpty(), actionText = "Retry", onAction = { reloadKey++ })
                list.isEmpty() -> EmptyState(Icons.Rounded.Inventory2, "No products yet", "Tap \"Add product\" to list your first item.", brush = PengoGradients.peach)
                else -> {
                    val q = query.trim().lowercase()
                    val shown = if (q.isEmpty()) list else list.filter { it.name.lowercase().contains(q) || it.barcode.orEmpty().contains(q) }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            val out = list.count { !it.inStock }
                            val low = list.count { (it.stockQuantity ?: 0) in 1..5 }
                            Text(
                                "${list.size} products • $low low • $out out of stock",
                                style = MaterialTheme.typography.bodySmall,
                                color = PengoColors.Muted,
                            )
                        }
                        items(shown, key = { it.id }) { p ->
                            InventoryRow(p, onEdit = { editing = p }, onStock = { changeStock(p, it) })
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add product") },
            containerColor = PengoColors.PrimaryDeep,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }

    if (creating || editing != null) {
        ProductEditor(
            storeId = storeId,
            product = editing,
            categories = categories,
            onDismiss = { creating = false; editing = null },
            onSaved = { creating = false; editing = null; reloadKey++ },
        )
    }
}

@Composable
private fun InventoryRow(p: Product, onEdit: () -> Unit, onStock: (Int) -> Unit) {
    val stock = p.stockQuantity ?: 0
    GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, padding = PaddingValues(10.dp), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductImage(p.imageUrl, Modifier.size(60.dp), corner = 14.dp, seed = p.id)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink, maxLines = 2)
                Text(p.price.rupees() + (p.barcode?.let { " • $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                when {
                    stock <= 0 -> StatusChip("OUT OF STOCK", PengoColors.Red)
                    stock <= 5 -> StatusChip("LOW • $stock", PengoColors.Warning)
                    else -> StatusChip("IN STOCK • $stock", PengoColors.Success)
                }
            }
            QuantityStepper(stock, { onStock(it.coerceAtLeast(0)) }, compact = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditor(
    storeId: String,
    product: Product?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(product?.name.orEmpty()) }
    var price by remember { mutableStateOf(product?.price?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()) }
    var stock by remember { mutableStateOf((product?.stockQuantity ?: 0).toString()) }
    var barcode by remember { mutableStateOf(product?.barcode.orEmpty()) }
    var description by remember { mutableStateOf(product?.description.orEmpty()) }
    var categoryId by remember { mutableStateOf(product?.categoryId) }
    var imageUrl by remember { mutableStateOf(product?.barcodeImageUrl ?: product?.imageUrl) }
    var uploading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            runCatching {
                val jpeg = withContext(Dispatchers.IO) { compressImage(context, uri) } ?: error("Couldn't read that image")
                StaffRepository.uploadProductPhoto(storeId, jpeg)
            }.onSuccess { imageUrl = it }
                .onFailure { UiMessages.show(it.friendlyMessage()) }
            uploading = false
        }
    }

    fun save() {
        val priceValue = price.replace(',', '.').toDoubleOrNull()
        val stockValue = stock.toIntOrNull()
        error = when {
            name.trim().length < 2 -> "Enter the product name"
            priceValue == null || priceValue <= 0 -> "Enter a valid price"
            stockValue == null || stockValue < 0 -> "Enter the stock quantity"
            else -> null
        }
        if (error != null) return
        saving = true
        scope.launch {
            runCatching {
                StaffRepository.saveProduct(
                    product?.id,
                    ProductUpsert(
                        storeId = storeId,
                        name = name.trim(),
                        description = description.trim().ifBlank { null },
                        price = priceValue!!,
                        categoryId = categoryId,
                        stockQuantity = stockValue!!,
                        barcode = barcode.trim().ifBlank { null },
                        barcodeImageUrl = imageUrl?.ifBlank { null },
                    ),
                )
            }.onSuccess {
                UiMessages.show(if (product == null) "Product added" else "Product updated")
                onSaved()
            }.onFailure { error = it.friendlyMessage() }
            saving = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (product == null) "Add product" else "Edit product", style = MaterialTheme.typography.titleLarge, color = PengoColors.Ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF3F5F8))
                        .clickable(enabled = !uploading) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageUrl.isNullOrBlank()) {
                        Icon(Icons.Rounded.AddAPhoto, "Add photo", tint = PengoColors.Muted)
                    } else {
                        ProductImage(imageUrl, Modifier.fillMaxSize(), corner = 20.dp, seed = name)
                    }
                    if (uploading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = PengoColors.Blue)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    if (uploading) "Uploading photo…" else "Tap to ${if (imageUrl.isNullOrBlank()) "add" else "change"} the product photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = PengoColors.Muted,
                )
            }
            EditorField(name, { name = it }, "Product name")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, "Price (₹)", KeyboardType.Decimal, Modifier.weight(1f))
                EditorField(stock, { stock = it.filter(Char::isDigit).take(6) }, "Stock", KeyboardType.Number, Modifier.weight(1f))
            }
            EditorField(barcode, { barcode = it.filter(Char::isLetterOrDigit).take(32) }, "Barcode (optional)", KeyboardType.Number)
            EditorField(description, { description = it.take(500) }, "Description (optional)", singleLine = false)
            if (categories.isNotEmpty()) {
                Text("Category", style = MaterialTheme.typography.labelLarge, color = PengoColors.Muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { c ->
                        val active = c.id == categoryId
                        Text(
                            c.name,
                            color = if (active) Color.White else PengoColors.Ink,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (active) PengoColors.PrimaryDeep else Color(0xFFF1F3F6))
                                .clickable { categoryId = if (active) null else c.id }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            error?.let { Text(it, color = PengoColors.Red, style = MaterialTheme.typography.bodySmall) }
            GradientButton(if (product == null) "Add product" else "Save changes", ::save, Modifier.fillMaxWidth(), loading = saving, enabled = !uploading)
            if (product != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Rounded.Delete, null, tint = PengoColors.Red)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete product", color = PengoColors.Red)
                }
            }
        }
    }

    if (confirmDelete && product != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${product.name}?") },
            text = { Text("Customers won't see it anymore. Products that already have orders can't be deleted; set their stock to 0 instead.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { StaffRepository.deleteProduct(product.id) }
                            .onSuccess { UiMessages.show("Product deleted"); onSaved() }
                            .onFailure {
                                val msg = it.message.orEmpty()
                                UiMessages.show(
                                    if (msg.contains("foreign key", true) || msg.contains("violates", true))
                                        "This product has past orders. Set its stock to 0 instead."
                                    else it.friendlyMessage()
                                )
                            }
                    }
                }) { Text("Delete", color = PengoColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditorField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = PengoColors.Divider),
        modifier = modifier,
    )
}

/** Scales the picked photo to at most 1024 px and encodes it as JPEG. */
private fun compressImage(context: Context, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1024 || bounds.outHeight / (sample * 2) >= 1024) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val scale = 1024f / maxOf(bitmap.width, bitmap.height)
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}

@Composable
fun StaffTeamScreen(nav: NavHostController, storeId: String) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<StaffMember>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("cashier") }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<StaffMember?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        runCatching { StaffRepository.team(storeId) }
            .onSuccess { members = it; error = null }
            .onFailure { error = it.friendlyMessage() }
    }

    PengoBackground {
        Column(Modifier.fillMaxSize()) {
            PengoTopBar("Team", onBack = { nav.popBackStack() })
            LazyColumn(
                Modifier.imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Add staff member", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                        Text("They must sign up in PeNgo first with this email.", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
                        Spacer(Modifier.height(10.dp))
                        EditorField(email, { email = it.trim() }, "Email", KeyboardType.Email)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("cashier", "guard", "manager").forEach { r ->
                                val active = r == role
                                Text(
                                    roleLabel(r),
                                    color = if (active) Color.White else PengoColors.Ink,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (active) PengoColors.PrimaryDeep else Color(0xFFF1F3F6))
                                        .clickable { role = r }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when (role) {
                                "cashier" -> "Can collect counter payments and verify exit passes."
                                "guard" -> "Can only verify exit passes at the door."
                                else -> "Full access: inventory, dashboard and team."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = PengoColors.Muted,
                        )
                        Spacer(Modifier.height(12.dp))
                        GradientButton("Add to team", {
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                UiMessages.show("Enter a valid email")
                                return@GradientButton
                            }
                            adding = true
                            scope.launch {
                                runCatching { StaffRepository.addMember(storeId, email, role) }
                                    .onSuccess { UiMessages.show("Added as ${roleLabel(role)}"); email = ""; reloadKey++ }
                                    .onFailure { UiMessages.show(it.friendlyMessage()) }
                                adding = false
                            }
                        }, Modifier.fillMaxWidth(), loading = adding, brush = PengoGradients.mint)
                    }
                }
                val list = members
                when {
                    list == null && error == null -> item { CenteredLoader(Modifier.height(120.dp)) }
                    list == null -> item { EmptyState(Icons.Rounded.Groups, "Couldn't load team", error.orEmpty(), actionText = "Retry", onAction = { reloadKey++ }) }
                    else -> items(list, key = { it.userId }) { m ->
                        GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, padding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Rounded.Groups, if (m.role == "manager") PengoGradients.primary else PengoGradients.lavender, size = 38.dp, iconSize = 18.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.name ?: m.email ?: "Staff", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                                    Text("${roleLabel(m.role)} • ${m.email.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted, maxLines = 1)
                                }
                                IconButton(onClick = { removing = m }) {
                                    Icon(Icons.Rounded.PersonRemove, "Remove", tint = PengoColors.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${m.name ?: m.email ?: "this member"}?") },
            text = { Text("They will lose staff access to this store immediately.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        runCatching { StaffRepository.removeMember(storeId, m.userId) }
                            .onSuccess { UiMessages.show("Removed from team"); reloadKey++ }
                            .onFailure { UiMessages.show(it.friendlyMessage()) }
                    }
                }) { Text("Remove", color = PengoColors.Red) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
