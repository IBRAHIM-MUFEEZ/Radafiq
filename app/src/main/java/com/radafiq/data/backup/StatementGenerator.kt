package com.radafiq.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.radafiq.data.models.CustomerSummary
import com.radafiq.data.models.CustomerTransaction
import com.radafiq.data.models.AccountKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class StatementGenerator(private val context: Context) {

    // ── Light palette (modern indigo/cyan) ────────────────────────────────────
    private val LIGHT_BG_PAGE    = 0xFFF5F3FF.toInt()   // light indigo canvas
    private val LIGHT_BG_RAISED  = 0xFFFFFFFF.toInt()   // white card
    private val LIGHT_BG_SOFT    = 0xFFEDE9FE.toInt()   // surfaceVariant
    private val LIGHT_TEXT_PRI   = 0xFF0B0E1A.toInt()   // deep indigo text
    private val LIGHT_TEXT_MUTED = 0xFF6B7280.toInt()   // muted gray
    private val LIGHT_OUTLINE    = 0xFFC7D2FE.toInt()   // light indigo outline

    // ── Dark palette (modern indigo/cyan) ─────────────────────────────────────
    private val DARK_BG_DEEP     = 0xFF11142B.toInt()   // deep indigo
    private val DARK_BG_SOFT     = 0xFF191D3D.toInt()   // mid indigo
    private val DARK_BG_RAISED   = 0xFF1E2248.toInt()   // raised indigo
    private val DARK_TEXT_PRI    = 0xFFEEF2FF.toInt()   // ice white text
    private val DARK_TEXT_MUTED  = 0xFF8B8FBF.toInt()   // muted indigo
    private val DARK_OUTLINE     = 0xFF2D3168.toInt()   // dark indigo outline

    // ── Shared brand colors ───────────────────────────────────────────────────
    private val PRIMARY        = 0xFF6366F1.toInt()   // indigo
    private val PRIMARY_DEEP   = 0xFF4F46E5.toInt()   // deep indigo
    private val TEAL           = 0xFF06B6D4.toInt()   // cyan
    private val GREEN_BRAND    = 0xFF10B981.toInt()   // emerald
    private val RED_ACCENT     = 0xFFEF4444.toInt()   // red
    private val GREEN_SETTLED  = 0xFF10B981.toInt()   // settled = emerald
    private val ORANGE_PENDING = 0xFFF59E0B.toInt()   // amber pending

    // ── Active palette — set per generation ──────────────────────────────────
    private var BG_DEEP      = DARK_BG_DEEP
    private var BG_SOFT      = DARK_BG_SOFT
    private var BG_RAISED    = DARK_BG_RAISED
    private var TEXT_PRIMARY = DARK_TEXT_PRI
    private var TEXT_MUTED   = DARK_TEXT_MUTED
    private var OUTLINE      = DARK_OUTLINE

    private fun applyTheme(isDark: Boolean) {
        if (isDark) {
            BG_DEEP      = DARK_BG_DEEP
            BG_SOFT      = DARK_BG_SOFT
            BG_RAISED    = DARK_BG_RAISED
            TEXT_PRIMARY = DARK_TEXT_PRI
            TEXT_MUTED   = DARK_TEXT_MUTED
            OUTLINE      = DARK_OUTLINE
        } else {
            BG_DEEP      = LIGHT_BG_PAGE
            BG_SOFT      = LIGHT_BG_SOFT
            BG_RAISED    = LIGHT_BG_RAISED
            TEXT_PRIMARY = LIGHT_TEXT_PRI
            TEXT_MUTED   = LIGHT_TEXT_MUTED
            OUTLINE      = LIGHT_OUTLINE
        }
    }

    // ── Font loader — tries bundled Argentum Sans, falls back to system sans ──
    private fun loadTypeface(bold: Boolean = false): Typeface {
        val assetName = if (bold) "fonts/ArgentumSans-SemiBold.ttf" else "fonts/ArgentumSans-Regular.ttf"
        return runCatching {
            Typeface.createFromAsset(context.assets, assetName)
        }.getOrElse {
            Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    suspend fun generateStatement(
        customer: CustomerSummary,
        generatedByName: String = "Radafiq User",
        isDark: Boolean = true
    ): Result<Uri> {
        return withContext(Dispatchers.IO) {
            runCatching {
                applyTheme(isDark)

                val regular = loadTypeface(bold = false)
                val bold    = loadTypeface(bold = true)

                val pdfDocument = PdfDocument()
                val pageWidth  = 595
                val pageHeight = 842
                var pageNumber = 1
                var yPosition  = 0

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                var page   = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                drawPageBackground(canvas, pageWidth, pageHeight, isDark)

                yPosition = drawHeader(canvas, customer, pageWidth, 32, regular, bold)
                yPosition += 18

                yPosition = drawSummary(canvas, customer, pageWidth, yPosition, regular, bold)
                yPosition += 18

                yPosition = drawDuesSummary(canvas, customer, pageWidth, yPosition, regular, bold)
                yPosition += 18

                // Transactions section
                val transactions = customer.transactions
                    .filter { it.isVisibleInTransactions() }
                    .sortedWith(compareByDescending<CustomerTransaction> { it.transactionDate }.thenByDescending { it.amount })

                if (transactions.isNotEmpty()) {
                    yPosition = drawSectionHeader(canvas, "Transactions", pageWidth, yPosition, bold)
                    yPosition += 8

                    // Group splits together
                    val splitMap = linkedMapOf<String, MutableList<CustomerTransaction>>()
                    val orderedGroups = mutableListOf<List<CustomerTransaction>>()
                    transactions.forEach { t ->
                        if (t.splitGroupId.isNotBlank()) {
                            val list = splitMap.getOrPut(t.splitGroupId) { mutableListOf() }
                            if (list.isEmpty()) orderedGroups.add(list)
                            list.add(t)
                        } else {
                            orderedGroups.add(listOf(t))
                        }
                    }

                    for (group in orderedGroups) {
                        if (yPosition > pageHeight - 110) {
                            drawFooter(canvas, pageNumber, pageHeight, generatedByName, regular)
                            pdfDocument.finishPage(page)
                            pageNumber++
                            val newInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page   = pdfDocument.startPage(newInfo)
                            canvas = page.canvas
                            drawPageBackground(canvas, pageWidth, pageHeight, isDark)
                            yPosition = 40
                        }
                        if (group.size > 1) {
                            yPosition = drawSplitTransactionRow(canvas, group, pageWidth, yPosition, regular, bold)
                        } else {
                            yPosition = drawTransactionRow(canvas, group.first(), pageWidth, yPosition, regular, bold)
                        }
                    }
                }

                // EMI schedule
                val emiTransactions = customer.transactions.filter { it.isEmi }
                if (emiTransactions.isNotEmpty()) {
                    if (yPosition > pageHeight - 160) {
                        drawFooter(canvas, pageNumber, pageHeight, generatedByName, regular)
                        pdfDocument.finishPage(page)
                        pageNumber++
                        val newInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page   = pdfDocument.startPage(newInfo)
                        canvas = page.canvas
                        drawPageBackground(canvas, pageWidth, pageHeight, isDark)
                        yPosition = 40
                    }
                    yPosition += 16
                    yPosition = drawEmiSchedule(canvas, emiTransactions, pageWidth, yPosition, regular, bold)
                }

                drawFooter(canvas, pageNumber, pageHeight, generatedByName, regular)
                pdfDocument.finishPage(page)

                val fileName = "statement_${customer.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                pdfDocument.writeTo(file.outputStream())
                pdfDocument.close()

                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
    }

    // ── Page background: gradient matching app theme ──────────────────────────
    private fun drawPageBackground(canvas: Canvas, pageWidth: Int, pageHeight: Int, isDark: Boolean) {
        if (isDark) {
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, pageHeight.toFloat(),
                    intArrayOf(0xFF0B0E1A.toInt(), DARK_BG_DEEP, DARK_BG_SOFT),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
            // Subtle indigo-cyan glow blobs
            val glow1 = Paint().apply { color = 0x2E6366F1.toInt() }  // indigo
            val glow2 = Paint().apply { color = 0x1E10B981.toInt() }  // emerald
            val glow3 = Paint().apply { color = 0x1806B6D4.toInt() }  // cyan
            canvas.drawCircle(pageWidth * 0.18f, pageHeight * 0.10f, 90f, glow1)
            canvas.drawCircle(pageWidth * 0.92f, pageHeight * 0.18f, 75f, glow2)
            canvas.drawCircle(pageWidth * 0.76f, pageHeight * 0.86f, 70f, glow3)
        } else {
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, pageHeight.toFloat(),
                    intArrayOf(0xFFFFFFFF.toInt(), 0xFFEDE9FE.toInt(), LIGHT_BG_SOFT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
            // Subtle light indigo glow blobs
            val glow1 = Paint().apply { color = 0x186366F1.toInt() }  // indigo
            val glow2 = Paint().apply { color = 0x1010B981.toInt() }  // emerald
            canvas.drawCircle(pageWidth * 0.18f, pageHeight * 0.10f, 80f, glow1)
            canvas.drawCircle(pageWidth * 0.88f, pageHeight * 0.15f, 65f, glow2)
        }
    }

    // ── Header: logo + app name + title + customer name ───────────────────────
    private fun drawHeader(
        canvas: Canvas,
        customer: CustomerSummary,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        val logoSize = 44f
        val logoX    = 40f
        val logoY    = startY.toFloat()

        // Draw Radafiq logo (gradient circle + ر letterform)
        drawRadafiqLogo(canvas, logoX, logoY, logoSize)

        // App name beside logo
        val appNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize  = 18f
            typeface  = bold
            color     = PRIMARY
        }
        val appSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize  = 9f
            typeface  = regular
            color     = TEXT_MUTED
        }
        canvas.drawText("Radafiq", logoX + logoSize + 10f, logoY + 28f, appNamePaint)
        canvas.drawText("Customer Statement", logoX + logoSize + 10f, logoY + 42f, appSubPaint)

        // Divider line
        val linePaint = Paint().apply {
            strokeWidth = 1f
            color       = OUTLINE
        }
        val lineY = logoY + logoSize + 12f
        canvas.drawLine(40f, lineY, (pageWidth - 40).toFloat(), lineY, linePaint)

        // Customer name + date
        val customerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = bold
            color    = TEXT_PRIMARY
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            typeface = regular
            color    = TEXT_MUTED
        }
        val generatedDate = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))

        canvas.drawText(customer.name, 40f, lineY + 22f, customerPaint)
        canvas.drawText("Statement generated on $generatedDate", 40f, lineY + 36f, datePaint)

        return (lineY + 50f).toInt()
    }

    // ── Radafiq logo: loads logo.png from assets ──────────────────────────────
    private fun drawRadafiqLogo(canvas: Canvas, x: Float, y: Float, size: Float) {
        val bitmap = runCatching {
            context.assets.open("logo.png").use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        }.getOrNull() ?: return

        val dst = RectF(x, y, x + size, y + size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    // ── Summary: 3 metric boxes ───────────────────────────────────────────────
    private fun drawSummary(
        canvas: Canvas,
        customer: CustomerSummary,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        // FIX-11: "Customer Paid" should reflect actual payments (settled + partial),
        // not the manually-entered creditDueAmount field.
        val actualPaid = customer.settledTransactionAmount + customer.partialPaidAmount
        val boxes = listOf(
            Triple("Total Used",    formatMoney(customer.totalAmount),    PRIMARY),
            Triple("Customer Paid", formatMoney(actualPaid),              GREEN_BRAND),
            Triple("Balance Due",   formatMoney(customer.balance),        if (customer.balance > 0) RED_ACCENT else GREEN_BRAND)
        )
        return drawMetricBoxRow(canvas, boxes, pageWidth, startY, regular, bold)
    }

    // ── Dues summary: paid vs unpaid transaction counts ───────────────────────
    private fun drawDuesSummary(
        canvas: Canvas,
        customer: CustomerSummary,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        val visible = customer.transactions.filter { it.isVisibleInTransactions() }

        // Group splits so each split group counts as 1 logical transaction
        val splitGroups = linkedMapOf<String, MutableList<CustomerTransaction>>()
        val logicalGroups = mutableListOf<List<CustomerTransaction>>()
        visible.forEach { t ->
            if (t.splitGroupId.isNotBlank()) {
                val list = splitGroups.getOrPut(t.splitGroupId) { mutableListOf() }
                if (list.isEmpty()) logicalGroups.add(list)
                list.add(t)
            } else {
                logicalGroups.add(listOf(t))
            }
        }

        val totalTxns = logicalGroups.size
        val settled = logicalGroups.count { group -> group.all { it.isSettled } }
        val partial = logicalGroups.count { group ->
            !group.all { it.isSettled } && group.any { it.partialPaidAmount > 0 }
        }
        val unpaid = logicalGroups.count { group ->
            !group.all { it.isSettled } && group.none { it.partialPaidAmount > 0 }
        }

        val amtSettled = logicalGroups.filter { g -> g.all { it.isSettled } }.sumOf { g -> g.sumOf { it.amount } }
        val amtPartial = logicalGroups.filter { g -> !g.all { it.isSettled } && g.any { it.partialPaidAmount > 0 } }
            .sumOf { g -> g.sumOf { (it.amount - it.partialPaidAmount).coerceAtLeast(0.0) } }
        val amtUnpaid = logicalGroups.filter { g -> !g.all { it.isSettled } && g.none { it.partialPaidAmount > 0 } }
            .sumOf { g -> g.sumOf { it.amount } }

        // Section header
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = bold
            color    = TEXT_MUTED
        }
        canvas.drawText("DUES OVERVIEW", 40f, startY.toFloat(), headerPaint)

        val cardY    = startY + 8
        val cardH    = 62
        val gap      = 8
        val cardW    = (pageWidth - 80 - gap * 2) / 3

        data class DueBox(val label: String, val count: String, val amount: String, val color: Int)
        val dueBoxes = listOf(
            DueBox("Settled",       "$settled of $totalTxns", formatMoney(amtSettled), GREEN_SETTLED),
            DueBox("Partial Paid",  "$partial of $totalTxns", formatMoney(amtPartial), ORANGE_PENDING),
            DueBox("Unpaid",        "$unpaid of $totalTxns",  formatMoney(amtUnpaid),  RED_ACCENT)
        )

        dueBoxes.forEachIndexed { i, box ->
            val left  = 40f + i * (cardW + gap)
            val right = left + cardW
            val top   = cardY.toFloat()
            val bot   = (cardY + cardH).toFloat()
            val radius = 10f

            // Card fill
            val fillPaint = Paint().apply { this.color = BG_RAISED }
            canvas.drawRoundRect(RectF(left, top, right, bot), radius, radius, fillPaint)

            // Left accent bar — clip to card shape so it respects rounded corners
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = box.color }
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(RectF(left, top, right, bot), radius, radius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawRect(RectF(left, top, left + 5f, bot), accentPaint)
            canvas.restore()

            // Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style       = Paint.Style.STROKE
                strokeWidth = 1f
                this.color  = OUTLINE
            }
            canvas.drawRoundRect(RectF(left, top, right, bot), radius, radius, borderPaint)

            // Label
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize   = 8f
                typeface   = bold
                this.color = TEXT_MUTED
            }
            canvas.drawText(box.label, left + 12f, top + 16f, labelPaint)

            // Count
            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize   = 14f
                typeface   = bold
                this.color = box.color
            }
            canvas.drawText(box.count, left + 12f, top + 36f, countPaint)

            // Amount
            val amtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize   = 8f
                typeface   = bold
                this.color = TEXT_MUTED
            }
            canvas.drawText(box.amount, left + 12f, top + 52f, amtPaint)
        }

        return cardY + cardH + 8
    }

    // ── Generic metric box row ────────────────────────────────────────────────
    private fun drawMetricBoxRow(
        canvas: Canvas,
        boxes: List<Triple<String, String, Int>>,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        val gap    = 8
        val boxW   = (pageWidth - 80 - gap * (boxes.size - 1)) / boxes.size
        val boxH   = 52

        boxes.forEachIndexed { i, (label, value, color) ->
            val left  = 40f + i * (boxW + gap)
            val right = left + boxW
            val top   = startY.toFloat()
            val bot   = (startY + boxH).toFloat()

            val fillPaint = Paint().apply { this.color = BG_RAISED }
            canvas.drawRoundRect(RectF(left, top, right, bot), 10f, 10f, fillPaint)

            val borderPaint = Paint().apply {
                style       = Paint.Style.STROKE
                strokeWidth = 1f
                this.color  = OUTLINE
            }
            canvas.drawRoundRect(RectF(left, top, right, bot), 10f, 10f, borderPaint)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize   = 8f
                typeface   = bold
                this.color = TEXT_MUTED
            }
            canvas.drawText(label, left + 10f, top + 16f, labelPaint)

            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize   = 13f
                typeface   = bold
                this.color = color
            }
            canvas.drawText(value, left + 10f, top + 38f, valuePaint)
        }

        return startY + boxH + 6
    }

    // ── Section header ────────────────────────────────────────────────────────
    private fun drawSectionHeader(
        canvas: Canvas,
        title: String,
        pageWidth: Int,
        startY: Int,
        bold: Typeface
    ): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = bold
            color    = TEXT_MUTED
        }
        canvas.drawText(title.uppercase(), 40f, startY.toFloat(), paint)

        val linePaint = Paint().apply {
            strokeWidth = 1f
            color       = OUTLINE
        }
        canvas.drawLine(40f, (startY + 5).toFloat(), (pageWidth - 40).toFloat(), (startY + 5).toFloat(), linePaint)

        return startY + 14
    }

    // ── Split transaction row ─────────────────────────────────────────────────
    private fun drawSplitTransactionRow(
        canvas: Canvas,
        splits: List<CustomerTransaction>,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        val first = splits.first()
        val total = splits.sumOf { it.amount }
        val allSettled = splits.all { it.isSettled }
        val rowH = 38
        val left  = 40f
        val right = (pageWidth - 40).toFloat()

        val rowFill = Paint().apply { color = BG_RAISED }
        canvas.drawRoundRect(RectF(left, startY.toFloat(), right, (startY + rowH).toFloat()), 8f, 8f, rowFill)

        val statusColor = when {
            allSettled -> GREEN_SETTLED
            splits.any { it.partialPaidAmount > 0 } -> ORANGE_PENDING
            else -> RED_ACCENT
        }
        val barPaint = Paint().apply { color = PRIMARY }
        canvas.drawRoundRect(RectF(left, startY.toFloat(), left + 4f, (startY + rowH).toFloat()), 4f, 4f, barPaint)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = bold; color = TEXT_PRIMARY }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; typeface = bold; color = TEXT_MUTED }
        val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = bold; color = PRIMARY }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; typeface = regular; color = statusColor }

        canvas.drawText(first.transactionDate, left + 10f, startY + 14f, labelPaint)
        canvas.drawText(first.name, left + 80f, startY + 14f, namePaint)
        canvas.drawText(formatMoney(total), right - 120f, startY + 14f, amountPaint)
        val splitStatus = when {
            allSettled -> "✓ Settled"
            splits.any { it.partialPaidAmount > 0 } -> "~ Partial"
            else -> "✗ Unpaid"
        }
        canvas.drawText(splitStatus, right - 120f, startY + 26f, statusPaint)

        val sepPaint = Paint().apply { strokeWidth = 0.5f; color = OUTLINE }
        canvas.drawLine(left, (startY + rowH).toFloat(), right, (startY + rowH).toFloat(), sepPaint)

        return startY + rowH + 4
    }

    // ── Transaction row ───────────────────────────────────────────────────────
    private fun drawTransactionRow(
        canvas: Canvas,
        transaction: CustomerTransaction,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        val rowH   = 38
        val left   = 40f
        val right  = (pageWidth - 40).toFloat()

        // Row background
        val rowFill = Paint().apply { color = BG_RAISED }
        canvas.drawRoundRect(RectF(left, startY.toFloat(), right, (startY + rowH).toFloat()), 8f, 8f, rowFill)

        // Status color + left bar
        val statusColor = when {
            transaction.isSettled                          -> GREEN_SETTLED
            transaction.partialPaidAmount > 0              -> ORANGE_PENDING
            else                                           -> RED_ACCENT
        }
        val barPaint = Paint().apply { color = statusColor }
        canvas.drawRoundRect(RectF(left, startY.toFloat(), left + 4f, (startY + rowH).toFloat()), 4f, 4f, barPaint)

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            typeface = bold
            color    = TEXT_MUTED
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = bold
            color    = TEXT_PRIMARY
        }
        val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = bold
            color    = PRIMARY
        }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            typeface = regular
            color    = statusColor
        }

        val textTop = startY + 14f
        val textBot = startY + 28f

        canvas.drawText(transaction.transactionDate, left + 10f, textTop, datePaint)
        canvas.drawText(transaction.name, left + 80f, textTop, namePaint)

        val statusText = when {
            transaction.isSettled                     -> "✓ Settled"
            transaction.partialPaidAmount > 0         -> "~ Partial ${formatMoney(transaction.partialPaidAmount)}"
            else                                      -> "✗ Unpaid"
        }
        canvas.drawText(formatMoney(transaction.amount), right - 120f, textTop, amountPaint)
        canvas.drawText(statusText, right - 120f, textBot, statusPaint)

        // Separator
        val sepPaint = Paint().apply {
            strokeWidth = 0.5f
            color       = OUTLINE
        }
        canvas.drawLine(left, (startY + rowH).toFloat(), right, (startY + rowH).toFloat(), sepPaint)

        return startY + rowH + 4
    }

    // ── EMI schedule ──────────────────────────────────────────────────────────
    private fun drawEmiSchedule(
        canvas: Canvas,
        emiTransactions: List<CustomerTransaction>,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        var yPos = drawSectionHeader(canvas, "EMI Schedule", pageWidth, startY, bold)
        yPos += 4

        val grouped = emiTransactions.groupBy { it.emiGroupId }

        for ((_, txns) in grouped) {
            val sorted = txns.sortedBy { it.emiIndex }
            val first  = sorted.first()

            val groupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f
                typeface = bold
                color    = TEAL
            }
            canvas.drawText("${first.name.substringBefore(" — EMI")} — ${sorted.size} instalments", 40f, yPos.toFloat(), groupPaint)
            yPos += 14

            val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                typeface = regular
                color    = TEXT_MUTED
            }
            for (tx in sorted) {
                val statusColor = if (tx.isSettled) GREEN_SETTLED else ORANGE_PENDING
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor }
                canvas.drawCircle(50f, yPos - 3f, 3f, dot)
                canvas.drawText("EMI ${tx.emiIndex + 1}/${tx.emiTotal}", 60f, yPos.toFloat(), cellPaint)
                canvas.drawText(tx.transactionDate, 140f, yPos.toFloat(), cellPaint)
                canvas.drawText(formatMoney(tx.amount), (pageWidth - 100).toFloat(), yPos.toFloat(), cellPaint)
                yPos += 13
            }
            yPos += 8
        }

        return yPos
    }

    // ── Footer ────────────────────────────────────────────────────────────────
    private fun drawFooter(
        canvas: Canvas,
        pageNumber: Int,
        pageHeight: Int,
        generatedByName: String,
        regular: Typeface
    ) {
        // Footer divider
        val linePaint = Paint().apply {
            strokeWidth = 0.8f
            color       = OUTLINE
        }
        canvas.drawLine(40f, (pageHeight - 44).toFloat(), 555f, (pageHeight - 44).toFloat(), linePaint)

        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            typeface = regular
            color    = PRIMARY
        }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            typeface = regular
            color    = TEXT_MUTED
        }

        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))

        // Left: generated by actual user name
        canvas.drawText("Generated by: $generatedByName", 40f, (pageHeight - 28).toFloat(), leftPaint)

        // Center: timestamp
        canvas.drawText("Generated: $timestamp", 40f, (pageHeight - 16).toFloat(), rightPaint)

        // Right: page + branding
        canvas.drawText("Page $pageNumber  •  Radafiq", 430f, (pageHeight - 16).toFloat(), rightPaint)
    }

    private fun formatMoney(amount: Double): String {
        val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return "₹${formatter.format(amount)}"
    }
}
