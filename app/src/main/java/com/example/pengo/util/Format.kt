package com.example.pengo.util

import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rupee: NumberFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
}

fun Double.rupees(): String = rupee.format(this)

private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

fun String?.prettyDate(): String {
    if (this.isNullOrBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(this).atZoneSameInstant(ZoneId.systemDefault()).format(dateFormat)
    }.getOrDefault(this.take(16).replace('T', ' '))
}

fun String.shortId(): String = take(8).uppercase()

fun Double.distanceLabel(): String =
    if (this < 1.0) "${(this * 1000).toInt()} m" else String.format(Locale.ENGLISH, "%.1f km", this)
