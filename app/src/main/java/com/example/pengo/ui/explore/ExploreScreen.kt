package com.example.pengo.ui.explore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.pengo.data.CatalogRepository
import com.example.pengo.data.StoreWithDistance
import com.example.pengo.data.friendlyMessage
import com.example.pengo.ui.Routes
import com.example.pengo.ui.components.EmptyState
import com.example.pengo.ui.components.GlassCard
import com.example.pengo.ui.components.GradientButton
import com.example.pengo.ui.components.IconBubble
import com.example.pengo.ui.components.PengoBackground
import com.example.pengo.ui.components.StatusChip
import com.example.pengo.ui.store.StoreContactRow
import com.example.pengo.ui.theme.PengoColors
import com.example.pengo.ui.theme.PengoGradients
import com.example.pengo.util.DeviceLocation
import com.example.pengo.util.Prefs
import com.example.pengo.util.distanceLabel
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SRC_STORES = "pengo-stores"
private const val SRC_SELECTED = "pengo-selected"
private const val SRC_USER = "pengo-user"
private const val SRC_RADIUS = "pengo-radius"
private const val LAYER_STORES = "pengo-stores-layer"

private data class ExploreUi(
    val loading: Boolean = true,
    val userLat: Double? = null,
    val userLng: Double? = null,
    val stores: List<StoreWithDistance> = emptyList(),
    val error: String? = null,
)

@Composable
fun ExploreScreen(nav: NavHostController) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    if (!granted) {
        PengoBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Rounded.LocationOff,
                    title = "Find stores around you",
                    message = "Allow location access to see PeNgo stores within your area on the map.",
                    brush = PengoGradients.lavender,
                    actionText = "Allow location",
                    onAction = {
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                )
            }
        }
        return
    }

    ExploreMap(nav)
}

@Composable
private fun ExploreMap(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val radiusKm = settings.searchRadiusKm.toDouble()
    var ui by remember { mutableStateOf(ExploreUi()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val listState = rememberLazyListState()

    fun load() {
        ui = ui.copy(loading = true, error = null)
        scope.launch {
            val location = DeviceLocation.current(context)
            if (location == null) {
                ui = ui.copy(loading = false, error = "Couldn't get your location. Turn on GPS and try again.")
                return@launch
            }
            runCatching { CatalogRepository.nearbyStores(location.latitude, location.longitude, radiusKm) }
                .onSuccess { ui = ExploreUi(false, location.latitude, location.longitude, it) }
                .onFailure { ui = ui.copy(loading = false, userLat = location.latitude, userLng = location.longitude, error = it.friendlyMessage()) }
        }
    }

    LaunchedEffect(radiusKm) { load() }

    LaunchedEffect(style, ui, selectedId) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        renderData(s, ui, selectedId, radiusKm)
        val lat = ui.userLat
        val lng = ui.userLng
        if (selectedId == null && lat != null && lng != null) {
            val bounds = radiusBounds(lat, lng, radiusKm)
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 700)
        }
    }

    LaunchedEffect(selectedId) {
        val index = ui.stores.indexOfFirst { it.store.id == selectedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Box(Modifier.fillMaxSize()) {
        MapLibreView(
            onReady = { m, s ->
                map = m
                style = s
                m.uiSettings.isCompassEnabled = false
                m.uiSettings.isAttributionEnabled = true
                m.uiSettings.isLogoEnabled = false
                m.addOnMapClickListener { latLng ->
                    val point = m.projection.toScreenLocation(latLng)
                    val feature = m.queryRenderedFeatures(point, LAYER_STORES).firstOrNull()
                    selectedId = feature?.getStringProperty("id")
                    feature != null
                }
            },
        )

        GlassCard(
            Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            corner = 24.dp,
            padding = PaddingValues(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.Storefront, PengoGradients.primary, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Stores near you", style = MaterialTheme.typography.titleMedium, color = PengoColors.Ink)
                    Text(
                        when {
                            ui.loading -> "Finding stores within ${radiusKm.toInt()} km…"
                            ui.error != null -> ui.error.orEmpty()
                            else -> "${ui.stores.size} store${if (ui.stores.size == 1) "" else "s"} within ${radiusKm.toInt()} km"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ui.error != null) PengoColors.Red else PengoColors.Muted,
                        maxLines = 2,
                    )
                }
                if (ui.loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PengoColors.Blue)
                } else {
                    SmallRound(Icons.Rounded.Refresh) { selectedId = null; load() }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 90.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Box(Modifier.padding(end = 16.dp, bottom = 12.dp)) {
                SmallRound(Icons.Rounded.MyLocation, size = 50) {
                    selectedId = null
                    val lat = ui.userLat
                    val lng = ui.userLng
                    if (lat != null && lng != null) {
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14.0), 600)
                    } else load()
                }
            }
            if (!ui.loading && ui.stores.isEmpty() && ui.error == null) {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text("No stores nearby yet", style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink)
                    Text(
                        "There are no PeNgo stores within ${radiusKm.toInt()} km. You can increase the radius in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PengoColors.Muted,
                    )
                }
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.stores, key = { it.store.id }) { item ->
                    StoreMapCard(
                        item = item,
                        selected = item.store.id == selectedId,
                        context = context,
                        onFocus = {
                            selectedId = item.store.id
                            val lat = item.store.latitude
                            val lng = item.store.longitude
                            if (lat != null && lng != null) {
                                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15.0), 600)
                            }
                        },
                        onOpen = { nav.navigate(Routes.Store(storeId = item.store.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreMapCard(
    item: StoreWithDistance,
    selected: Boolean,
    context: Context,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
) {
    val store = item.store
    GlassCard(
        Modifier
            .width(290.dp)
            .then(if (selected) Modifier.shadow(18.dp, RoundedCornerShape(24.dp), spotColor = PengoColors.Blue) else Modifier),
        corner = 24.dp,
        padding = PaddingValues(14.dp),
        onClick = onFocus,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Storefront, if (selected) PengoGradients.pink else PengoGradients.lavender, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(store.name, style = MaterialTheme.typography.titleSmall, color = PengoColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.distanceKm.distanceLabel() + " away", style = MaterialTheme.typography.labelMedium, color = PengoColors.PrimaryDeep, fontWeight = FontWeight.SemiBold)
            }
            StatusChip(if (store.isOpen != false) "OPEN" else "CLOSED", if (store.isOpen != false) PengoColors.Success else PengoColors.Red)
        }
        if (store.fullAddress.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(store.fullAddress, style = MaterialTheme.typography.bodySmall, color = PengoColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        StoreContactRow(store, context)
        Spacer(Modifier.height(10.dp))
        GradientButton("View products", onOpen, Modifier.fillMaxWidth().height(46.dp))
    }
}

@Composable
private fun SmallRound(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Int = 40, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .shadow(8.dp, CircleShape, spotColor = Color(0x3364A1FF))
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = PengoColors.PrimaryDeep) }
}

@Composable
private fun MapLibreView(onReady: (MapLibreMap, Style) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                    setupLayers(style)
                    onReady(map, style)
                }
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

private fun setupLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_RADIUS))
    style.addSource(GeoJsonSource(SRC_USER))
    style.addSource(GeoJsonSource(SRC_STORES))
    style.addSource(GeoJsonSource(SRC_SELECTED))

    style.addLayer(
        FillLayer("pengo-radius-fill", SRC_RADIUS).withProperties(
            PropertyFactory.fillColor("#64A1FF"),
            PropertyFactory.fillOpacity(0.10f),
        )
    )
    style.addLayer(
        LineLayer("pengo-radius-line", SRC_RADIUS).withProperties(
            PropertyFactory.lineColor("#64A1FF"),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        )
    )
    style.addLayer(
        CircleLayer(LAYER_STORES, SRC_STORES).withProperties(
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleColor("#D278F2"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    style.addLayer(
        CircleLayer("pengo-selected-layer", SRC_SELECTED).withProperties(
            PropertyFactory.circleRadius(15f),
            PropertyFactory.circleColor("#F64848"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(4f),
        )
    )
    style.addLayer(
        CircleLayer("pengo-user-halo", SRC_USER).withProperties(
            PropertyFactory.circleRadius(22f),
            PropertyFactory.circleColor("#64A1FF"),
            PropertyFactory.circleOpacity(0.2f),
        )
    )
    style.addLayer(
        CircleLayer("pengo-user-dot", SRC_USER).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#3D7FE8"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
}

private fun renderData(style: Style, ui: ExploreUi, selectedId: String?, radiusKm: Double) {
    val lat = ui.userLat
    val lng = ui.userLng
    if (lat != null && lng != null) {
        style.getSourceAs<GeoJsonSource>(SRC_USER)?.setGeoJson(Point.fromLngLat(lng, lat))
        style.getSourceAs<GeoJsonSource>(SRC_RADIUS)?.setGeoJson(circlePolygon(lat, lng, radiusKm))
    }
    val features = ui.stores.mapNotNull { item ->
        val sLat = item.store.latitude ?: return@mapNotNull null
        val sLng = item.store.longitude ?: return@mapNotNull null
        Feature.fromGeometry(Point.fromLngLat(sLng, sLat)).apply {
            addStringProperty("id", item.store.id)
            addStringProperty("name", item.store.name)
        }
    }
    style.getSourceAs<GeoJsonSource>(SRC_STORES)?.setGeoJson(FeatureCollection.fromFeatures(features))
    val selected = features.filter { it.getStringProperty("id") == selectedId }
    style.getSourceAs<GeoJsonSource>(SRC_SELECTED)?.setGeoJson(FeatureCollection.fromFeatures(selected))
}

private fun circlePolygon(lat: Double, lng: Double, radiusKm: Double, steps: Int = 72): Polygon {
    val points = (0..steps).map { i ->
        val angle = Math.toRadians(i * 360.0 / steps)
        val dLat = (radiusKm / 111.0) * sin(angle)
        val dLng = (radiusKm / (111.0 * cos(Math.toRadians(lat)))) * cos(angle)
        Point.fromLngLat(lng + dLng, lat + dLat)
    }
    return Polygon.fromLngLats(listOf(points))
}

private fun radiusBounds(lat: Double, lng: Double, radiusKm: Double): LatLngBounds {
    val dLat = radiusKm / 111.0
    val dLng = radiusKm / (111.0 * cos(Math.toRadians(lat)))
    return LatLngBounds.Builder()
        .include(LatLng(lat + dLat, lng + dLng))
        .include(LatLng(lat - dLat, lng - dLng))
        .build()
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
