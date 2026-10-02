package com.example.pengo.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.pengo.data.Order
import com.example.pengo.data.Profile
import java.io.File

object InvoicePdf {
    private const val W = 595
    private const val H = 842
    private const val M = 36f

    private val ink = Color.parseColor("#20242B")
    private val muted = Color.parseColor("#737983")
    private val line = Color.parseColor("#E3E6EA")
    private val blue = Color.parseColor("#64A1FF")
    private val cyan = Color.parseColor("#8BFCFE")
    private val mint = Color.parseColor("#1FAE6B")

    fun create(context: Context, order: Order, profile: Profile?, email: String?): File {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas

        val title = paint(22f, bold = true, color = Color.WHITE)
        val h = paint(11f, bold = true)
        val body = paint(10f)
        val small = paint(9f, color = muted)

        // Header band
        val band = Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), 0f, blue, cyan, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), 96f, band)
        c.drawText("PeNgo", M, 48f, title)
        c.drawText("Scan • Pay • Walk out", M, 70f, paint(10f, color = Color.WHITE))
        val inv = paint(16f, bold = true, color = Color.WHITE).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("TAX INVOICE", W - M, 48f, inv)
        c.drawText("#${order.id.shortId()}", W - M, 70f, paint(10f, color = Color.WHITE).apply { textAlign = Paint.Align.RIGHT })

        var y = 128f
        val store = order.store
        c.drawText("SOLD BY", M, y, small)
        c.drawText("BILLED TO", W / 2f + 10, y, small)
        y += 16f
        c.drawText(store?.name ?: "Pengo Store", M, y, h)
        c.drawText(profile?.fullName ?: "Customer", W / 2f + 10, y, h)
        y += 14f
        val storeLines = listOfNotNull(
            store?.fullAddress?.takeIf { it.isNotBlank() },
            store?.phone?.let { "Phone: $it" },
            store?.email,
        )
        val customerLines = listOfNotNull(
            profile?.phoneNumber?.let { "Phone: $it" },
            (profile?.email ?: email),
        )
        val rows = maxOf(storeLines.size, customerLines.size)
        for (i in 0 until rows) {
            storeLines.getOrNull(i)?.let { c.drawText(it.take(48), M, y, body) }
            customerLines.getOrNull(i)?.let { c.drawText(it.take(48), W / 2f + 10, y, body) }
            y += 13f
        }

        y += 10f
        val meta = listOf(
            "Invoice date" to order.createdAt.prettyDate(),
            "Order ID" to order.id,
            "Payment" to "${(order.paymentMethod ?: "-").uppercase()}  •  ${(order.paymentStatus ?: "unpaid").uppercase()}",
            "Payment ref" to (order.paymentId ?: "-"),
        )
        val box = RectF(M, y, W - M, y + 18f * meta.size + 12f)
        c.drawRoundRect(box, 10f, 10f, Paint().apply { color = Color.parseColor("#F4F6F8") })
        var my = y + 20f
        meta.forEach { (k, v) ->
            c.drawText(k, M + 12, my, small)
            c.drawText(v, M + 110, my, body)
            my += 18f
        }
        y = box.bottom + 24f

        // Items table
        val cols = floatArrayOf(M, M + 28, W - M - 190, W - M - 110, W - M)
        fun header(canvas: Canvas, top: Float): Float {
            canvas.drawRect(M, top - 13f, W - M, top + 7f, Paint().apply { color = Color.parseColor("#EAF2FF") })
            canvas.drawText("#", cols[0] + 6, top, h)
            canvas.drawText("Item", cols[1], top, h)
            canvas.drawText("Qty", cols[2], top, h)
            canvas.drawText("Rate", cols[3], top, h)
            canvas.drawText("Amount", cols[4] - 4, top, paint(11f, bold = true).apply { textAlign = Paint.Align.RIGHT })
            return top + 24f
        }
        y = header(c, y)
        val items = order.items.orEmpty()
        val right = paint(10f).apply { textAlign = Paint.Align.RIGHT }
        items.forEachIndexed { index, item ->
            if (y > H - 220) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                c = page.canvas
                y = header(c, 60f)
            }
            val amount = item.priceAtPurchase * item.quantity
            c.drawText("${index + 1}", cols[0] + 6, y, body)
            c.drawText((item.product?.name ?: "Item").take(40), cols[1], y, body)
            c.drawText("${item.quantity}", cols[2], y, body)
            c.drawText(item.priceAtPurchase.rupees(), cols[3], y, body)
            c.drawText(amount.rupees(), cols[4] - 4, y, right)
            y += 8f
            c.drawLine(M, y, W - M, y, Paint().apply { color = line })
            y += 16f
        }

        val itemsTotal = items.sumOf { it.priceAtPurchase * it.quantity }
        val discount = (itemsTotal - order.totalAmount).coerceAtLeast(0.0)
        y += 6f
        val labelX = W - M - 180
        fun total(label: String, value: String, p: Paint = body) {
            c.drawText(label, labelX, y, p)
            c.drawText(value, W - M - 4, y, Paint(p).apply { textAlign = Paint.Align.RIGHT })
            y += 18f
        }
        total("Subtotal", itemsTotal.rupees())
        if (discount > 0.009) total("Discount", "- ${discount.rupees()}")
        total("Total", order.totalAmount.rupees(), paint(13f, bold = true))
        c.drawText("Prices are inclusive of all applicable taxes.", M, y - 18f, small)

        // Paid stamp + QR
        y += 10f
        if (order.isPaid) {
            val stamp = paint(20f, bold = true, color = mint)
            c.save()
            c.rotate(-12f, M + 60, y + 40)
            c.drawRoundRect(RectF(M, y + 15, M + 130, y + 60), 8f, 8f, Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 3f; color = mint
            })
            c.drawText("PAID", M + 38, y + 46, stamp)
            c.restore()
            order.exitPass?.let {
                c.drawText("Exit pass: ${it.id}", M, y + 90, h)
                c.drawText("Show the exit pass QR in the app at the store exit.", M, y + 104, small)
            }
        } else {
            c.drawText("PAYMENT PENDING", M, y + 40, paint(16f, bold = true, color = Color.parseColor("#F64848")))
        }

        val qrSize = 120
        val qrUri = Upi.payUri(if (order.isPaid) null else order.totalAmount, "Pengo order ${order.id.shortId()}").toString()
        val qr = QrCode.bitmap(qrUri, qrSize * 3)
        val qrLeft = W - M - qrSize
        c.drawBitmap(qr, null, RectF(qrLeft, y, qrLeft + qrSize, y + qrSize), null)
        val center = paint(9f, color = muted).apply { textAlign = Paint.Align.CENTER }
        c.drawText(if (order.isPaid) "Pay to ${Upi.merchantName}" else "Scan to pay", qrLeft + qrSize / 2f, y + qrSize + 14, center)
        c.drawText(Upi.merchantId, qrLeft + qrSize / 2f, y + qrSize + 26, paint(9f, bold = true).apply { textAlign = Paint.Align.CENTER })

        c.drawLine(M, H - 50f, W - M, H - 50f, Paint().apply { color = line })
        c.drawText("Thank you for shopping with PeNgo!", M, H - 32f, h)
        c.drawText("This is a computer generated invoice.", W - M, H - 32f, paint(9f, color = muted).apply { textAlign = Paint.Align.RIGHT })

        doc.finishPage(page)
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "Pengo-Invoice-${order.id.shortId()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share invoice").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Copies the PDF to the public Downloads folder. Returns null on Android 9 and below. */
    fun saveToDownloads(context: Context, file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = ink) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
