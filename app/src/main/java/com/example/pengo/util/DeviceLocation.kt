package com.example.pengo.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object DeviceLocation {
    /** Returns a fresh fix, falling back to the last known one. Caller must hold location permission. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val fresh = suspendCancellableCoroutine { cont ->
            val token = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { token.cancel() }
        }
        if (fresh != null) return fresh
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}
