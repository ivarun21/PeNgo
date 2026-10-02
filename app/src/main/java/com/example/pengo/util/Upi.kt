package com.example.pengo.util

import android.net.Uri
import com.example.pengo.BuildConfig
import java.util.Locale

object Upi {
    val merchantId: String get() = BuildConfig.MERCHANT_UPI_ID
    val merchantName: String get() = BuildConfig.MERCHANT_NAME

    /** Standard `upi://pay` link understood by GPay, PhonePe, Paytm, BHIM and bank apps. */
    fun payUri(amount: Double?, note: String, txnRef: String? = null): Uri {
        val builder = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", merchantId)
            .appendQueryParameter("pn", merchantName)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", note)
        if (amount != null && amount > 0) {
            builder.appendQueryParameter("am", String.format(Locale.ENGLISH, "%.2f", amount))
        }
        if (!txnRef.isNullOrBlank()) builder.appendQueryParameter("tr", txnRef)
        return builder.build()
    }

    enum class Status { SUCCESS, FAILED, SUBMITTED, UNKNOWN }

    data class Response(val status: Status, val txnRef: String?)

    /** Parses the `Status=SUCCESS&txnId=...` string some UPI apps return. */
    fun parseResponse(raw: String?): Response {
        if (raw.isNullOrBlank()) return Response(Status.UNKNOWN, null)
        val map = raw.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx).lowercase() to Uri.decode(part.substring(idx + 1))
        }.toMap()
        val status = when (map["status"]?.lowercase()) {
            "success" -> Status.SUCCESS
            "failure", "failed" -> Status.FAILED
            "submitted", "pending" -> Status.SUBMITTED
            else -> Status.UNKNOWN
        }
        val ref = map["approvalrefno"] ?: map["txnref"] ?: map["txnid"]
        return Response(status, ref)
    }
}
