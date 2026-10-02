package com.example.pengo.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.pengo.data.Product
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.rupees
import java.util.Locale

@Composable
fun ProductImage(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    seed: String = "",
) {
    val brush = PengoGradients.all[(seed.hashCode() and Int.MAX_VALUE) % PengoGradients.all.size]
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(Color(0xFFF3F5F8))) {
        if (url.isNullOrBlank()) {
            ImagePlaceholder(brush)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ShimmerBox(Modifier.fillMaxSize(), corner) },
                error = { ImagePlaceholder(brush) },
            )
        }
    }
}

@Composable
private fun ImagePlaceholder(brush: Brush) {
    Box(
        Modifier
            .fillMaxSize()
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.ShoppingBag, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(36.dp))
    }
}

@Composable
fun HeartButton(saved: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = "Wishlist",
            tint = if (saved) PengoColors.Red else PengoColors.Muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun RatingLabel(rating: Double?) {
    if (rating == null || rating <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Star, null, tint = PengoColors.Warning, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text(String.format(Locale.ENGLISH, "%.1f", rating), style = MaterialTheme.typography.labelMedium, color = PengoColors.Muted)
    }
}

@Composable
fun QuantityStepper(quantity: Int, onChange: (Int) -> Unit, compact: Boolean = false) {
    val size = if (compact) 28.dp else 34.dp
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFEFF5FF)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .clickable { onChange(quantity - 1) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Remove, "Decrease", tint = PengoColors.PrimaryDeep, modifier = Modifier.size(16.dp)) }
        AnimatedContent(targetState = quantity, label = "qty") { q ->
            Text(
                "$q",
                style = MaterialTheme.typography.titleSmall,
                color = PengoColors.Ink,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(PengoGradients.primary)
                .clickable { onChange(quantity + 1) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "Increase", tint = PengoColors.Ink, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun AddButton(onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) PengoGradients.primary else Brush.linearGradient(listOf(PengoColors.Divider, PengoColors.Divider)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, "Add to cart", tint = PengoColors.Ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ProductCard(
    product: Product,
    saved: Boolean,
    cartQuantity: Int,
    onClick: () -> Unit,
    onToggleWishlist: () -> Unit,
    onAdd: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, corner = 22.dp, padding = androidx.compose.foundation.layout.PaddingValues(10.dp), onClick = onClick) {
        Box {
            ProductImage(
                url = product.imageUrl,
                seed = product.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            HeartButton(saved, onToggleWishlist, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (!product.inStock) {
                Text(
                    "Out of stock",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(PengoColors.Red.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            product.name,
            style = MaterialTheme.typography.titleSmall,
            color = PengoColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(40.dp),
        )
        RatingLabel(product.rating)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(product.price.rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PengoColors.Ink)
            if (cartQuantity > 0) {
                QuantityStepper(cartQuantity, onQuantityChange, compact = true)
            } else {
                AddButton(onAdd, enabled = product.inStock)
            }
        }
    }
}

@Composable
fun ProductRow(
    product: Product,
    trailing: @Composable () -> Unit,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        corner = 20.dp,
        padding = androidx.compose.foundation.layout.PaddingValues(10.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductImage(product.imageUrl, Modifier.size(72.dp), corner = 16.dp, seed = product.id)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(subtitle ?: product.price.rupees(), style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted)
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
