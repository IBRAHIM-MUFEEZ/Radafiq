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
import com.radafiq.data.models.CustomerSettlementEntry
import com.radafiq.data.models.SavingsEntry
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
        isDark: Boolean = true,
        includeSettled: Boolean = true,
        includeEmiDetails: Boolean = true,
        includeSettlementHistory: Boolean = true,
        includeSavingsDetails: Boolean = true,
        settlementHistory: List<CustomerSettlementEntry>? = null,
        savingsEntries: List<SavingsEntry>? = null
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

                // Build ordered groups for transactions
                val allTransactions = customer.transactions
                    .filter { it.isVisibleInTransactions() }
                    .sortedWith(compareByDescending<CustomerTransaction> { it.transactionDate }.thenByDescending { it.amount })

                // Group splits together
                val splitMap = linkedMapOf<String, MutableList<CustomerTransaction>>()
                val allOrderedGroups = mutableListOf<List<CustomerTransaction>>()
                allTransactions.forEach { t ->
                    if (t.splitGroupId.isNotBlank()) {
                        val list = splitMap.getOrPut(t.splitGroupId) { mutableListOf() }
                        if (list.isEmpty()) allOrderedGroups.add(list)
                        list.add(t)
                    } else {
                        allOrderedGroups.add(listOf(t))
                    }
                }

                val settledGroups = allOrderedGroups.filter { g -> g.all { it.isSettled } }
                val unsettledGroups = allOrderedGroups.filter { g -> !g.all { it.isSettled } }

                // If including only unsettled, recompute totals from unsettled groups
                val effectiveGroups = if (includeSettled) allOrderedGroups else unsettledGroups
                val effectiveTotal = effectiveGroups.sumOf { g -> g.sumOf { it.amount } }
                val effectivePaid = effectiveGroups.sumOf { g ->
                    g.sumOf { it.partialPaidAmount } + if (g.all { it.isSettled }) g.sumOf { it.amount } else 0.0
                }
                val effectiveBalance = effectiveTotal - effectivePaid

                yPosition = drawSummary(canvas, effectiveTotal, effectivePaid, effectiveBalance, pageWidth, yPosition, regular, bold)
                yPosition += 18

                yPosition = drawDuesSummary(canvas, effectiveGroups, pageWidth, yPosition, regular, bold)
                yPosition += 18

                fun drawGroupList(groups: List<List<CustomerTransaction>>, y: Int): Int {
                    var yy = y
                    for (group in groups) {
                        if (yy > pageHeight - 110) {
                            drawFooter(canvas, pageNumber, pageHeight, generatedByName, regular)
                            pdfDocument.finishPage(page)
                            pageNumber++
                            val newInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page   = pdfDocument.startPage(newInfo)
                            canvas = page.canvas
                            drawPageBackground(canvas, pageWidth, pageHeight, isDark)
                            yy = 40
                        }
                        yy = if (group.size > 1) {
                            drawSplitTransactionRow(canvas, group, pageWidth, yy, regular, bold)
                        } else {
                            drawTransactionRow(canvas, group.first(), pageWidth, yy, regular, bold)
                        }
                    }
                    return yy
                }

                // If including settled, draw settled transactions section
                if (includeSettled && settledGroups.isNotEmpty()) {
                    yPosition = drawSectionHeader(canvas, "Settled Transactions (${settledGroups.size})", pageWidth, yPosition, bold)
                    yPosition += 8
                    yPosition = drawGroupList(settledGroups, yPosition)
                    yPosition += 12
                }

                // Unsettled transactions — always shown
                if (unsettledGroups.isNotEmpty()) {
                    val unsettledLabel = if (includeSettled) "Unsettled" else "All Transactions"
                    yPosition = drawSectionHeader(canvas, "$unsettledLabel Transactions (${unsettledGroups.size})", pageWidth, yPosition, bold)
                    yPosition += 8
                    yPosition = drawGroupList(unsettledGroups, yPosition)
                    yPosition += 12
                }

                // EMI schedule
                val emiTransactions = customer.transactions.filter { it.isEmi }
                if (includeEmiDetails && emiTransactions.isNotEmpty()) {
                    // Pre-calculate actual EMI schedule height to prevent overflow past footer.
                    // The layout constants below match those inside drawEmiSchedule:
                    //   - 34 = 16 (pre-section gap) + 14 (drawSectionHeader return) + 4 (yPos += 4)
                    //   - 40 = 32 (headerRowH) + 8 (post-group spacing)
                    //   - 38 = 36 (rowH) + 2 (row advance)
                    // Footer area (~44px at page bottom) is reserved separately.
                    val emiGrouped = emiTransactions.groupBy { it.emiGroupId }
                    val emiScheduleHeight = 34 + emiGrouped.values.sumOf { txns ->
                        val sorted = txns.sortedBy { it.emiIndex }
                        40 + sorted.size * 38
                    }
                    if (yPosition > pageHeight - emiScheduleHeight - 44) {
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

                // Settlement history
                if (includeSettlementHistory && !settlementHistory.isNullOrEmpty()) {
                    yPosition += 12
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
                    yPosition = drawSectionHeader(canvas, "Settlement History", pageWidth, yPosition, bold)
                    yPosition += 8

                    val sortedHistory = settlementHistory.sortedByDescending { it.timestamp }
                    // Hoisted paints for settlement history rows
                    val shRowFillPaint = Paint().apply { color = BG_RAISED }
                    val shDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; typeface = bold; color = TEXT_MUTED }
                    val shNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = bold; color = TEXT_PRIMARY }
                    val shLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; typeface = regular; color = TEXT_MUTED }
                    val shSepPaint = Paint().apply { strokeWidth = 0.5f; color = OUTLINE }
                    for (entry in sortedHistory) {
                        if (yPosition > pageHeight - 90) {
                            drawFooter(canvas, pageNumber, pageHeight, generatedByName, regular)
                            pdfDocument.finishPage(page)
                            pageNumber++
                            val newInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page   = pdfDocument.startPage(newInfo)
                            canvas = page.canvas
                            drawPageBackground(canvas, pageWidth, pageHeight, isDark)
                            yPosition = 40
                        }
                        yPosition = drawSettlementHistoryRow(canvas, entry, pageWidth, yPosition, regular, bold, shRowFillPaint, shDatePaint, shNamePaint, shLabelPaint, shSepPaint)
                    }
                    yPosition += 8
                }

                // Savings details
                val effectiveSavings = savingsEntries ?: customer.savingsEntries
                if (includeSavingsDetails && effectiveSavings.isNotEmpty()) {
                    yPosition += 12
                    if (yPosition > pageHeight - 180) {
                        drawFooter(canvas, pageNumber, pageHeight, generatedByName, regular)
                        pdfDocument.finishPage(page)
                        pageNumber++
                        val newInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page   = pdfDocument.startPage(newInfo)
                        canvas = page.canvas
                        drawPageBackground(canvas, pageWidth, pageHeight, isDark)
                        yPosition = 40
                    }
                    yPosition = drawSavingsSection(canvas, effectiveSavings, pageWidth, pageHeight, yPosition, regular, bold)
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
            // Subtle indigo-cyan glow blobs — tucked into page corners away from content
            val glow1 = Paint().apply { color = 0x1A6366F1.toInt() }  // indigo (reduced alpha)
            val glow2 = Paint().apply { color = 0x1210B981.toInt() }  // emerald (reduced alpha)
            val glow3 = Paint().apply { color = 0x0F06B6D4.toInt() }  // cyan (reduced alpha)
            canvas.drawCircle(pageWidth * 0.92f, pageHeight * 0.04f, 70f, glow1)
            canvas.drawCircle(pageWidth * 0.06f, pageHeight * 0.96f, 80f, glow2)
            canvas.drawCircle(pageWidth * 0.95f, pageHeight * 0.93f, 60f, glow3)
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
            // Subtle light indigo glow blobs — tucked into page corners away from content
            val glow1 = Paint().apply { color = 0x126366F1.toInt() }  // indigo (reduced alpha)
            val glow2 = Paint().apply { color = 0x0C10B981.toInt() }  // emerald (reduced alpha)
            canvas.drawCircle(pageWidth * 0.93f, pageHeight * 0.04f, 65f, glow1)
            canvas.drawCircle(pageWidth * 0.05f, pageHeight * 0.95f, 70f, glow2)
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

    // ── Radafiq logo: loads logo-Photoroom.png from assets ─────────────────
    private fun drawRadafiqLogo(canvas: Canvas, x: Float, y: Float, size: Float) {
        val bitmap = runCatching {
            context.assets.open("logo-Photoroom.png").use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        }.getOrNull() ?: return

        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val scale = minOf(size / bw, size / bh)
        val w = bw * scale
        val h = bh * scale
        val offsetX = (size - w) / 2f
        val offsetY = (size - h) / 2f
        val dst = RectF(x + offsetX, y + offsetY, x + offsetX + w, y + offsetY + h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    // ── Summary: 3 metric boxes ───────────────────────────────────────────────
    private fun drawSummary(
        canvas: Canvas,
        totalAmount: Double,
        paidAmount: Double,
        balance: Double,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        // FIX-11: "Customer Paid" should reflect actual payments (settled + partial),
        // not the manually-entered creditDueAmount field.
        val boxes = listOf(
            Triple("Total Used",    formatMoney(totalAmount),   PRIMARY),
            Triple("Customer Paid", formatMoney(paidAmount),    GREEN_BRAND),
            Triple("Balance Due",   formatMoney(balance),       if (balance > 0) RED_ACCENT else GREEN_BRAND)
        )
        return drawMetricBoxRow(canvas, boxes, pageWidth, startY, regular, bold)
    }

    // ── Dues summary: paid vs unpaid transaction counts ───────────────────────
    private fun drawDuesSummary(
        canvas: Canvas,
        logicalGroups: List<List<CustomerTransaction>>,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
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

    // ── EMI schedule with glass-morphism card styling ──────────────────────────
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
        val cardLeft = 40f
        val cardRight = (pageWidth - 40).toFloat()

        for ((_, txns) in grouped) {
            val sorted = txns.sortedBy { it.emiIndex }
            val first  = sorted.first()
            val groupName = first.name.substringBefore(" — EMI").ifBlank { first.name }
            val headerRowH = 32
            val rowH = 36

            // Card background for EMI group
            val cardFill = Paint().apply { color = BG_RAISED }
            val groupHeight = headerRowH + sorted.size * (rowH + 2) + 4
            val cardTop = yPos.toFloat()
            val cardBot = (yPos + groupHeight).toFloat()

            // Create card path for clipping accent bar to rounded corners
            val cardRadius = 10f
            val cardRect = RectF(cardLeft, cardTop, cardRight, cardBot)
            val cardPath = Path().apply {
                addRoundRect(cardRect, cardRadius, cardRadius, Path.Direction.CW)
            }

            // Fill card background
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardFill)

            // Left accent bar — clipped to card shape so corners don't bleed
            canvas.save()
            canvas.clipPath(cardPath)
            val barPaint = Paint().apply { color = TEAL }
            canvas.drawRect(RectF(cardLeft, cardTop, cardLeft + 4f, cardBot), barPaint)
            canvas.restore()

            // Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = OUTLINE
            }
            canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderPaint)

            // Group header row — slightly darker background
            val headerBgPaint = Paint().apply { color = if (isDark()) 0xFF1A1E42.toInt() else 0xFFEEF2FF.toInt() }
            canvas.drawRoundRect(
                RectF(cardLeft + 4f, cardTop, cardRight, cardTop + headerRowH),
                0f, 0f, headerBgPaint
            )

            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f; typeface = bold; color = TEAL
            }
            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f; typeface = regular; color = TEXT_MUTED
                textAlign = Paint.Align.RIGHT
            }
            val paidCount = sorted.count { it.isSettled }
            canvas.drawText(groupName, cardLeft + 14f, yPos + 20f, namePaint)
            canvas.drawText(
                "$paidCount/${sorted.size} paid",
                cardRight - 14f, yPos + 20f, countPaint
            )
            yPos += headerRowH

            // Separator after header
            val sepPaint = Paint().apply { strokeWidth = 0.5f; color = OUTLINE }
            canvas.drawLine(cardLeft + 4f, yPos.toFloat(), cardRight, yPos.toFloat(), sepPaint)

            // Installment rows
            val instDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 8f; typeface = bold; color = TEXT_MUTED
            }
            val instLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f; typeface = regular; color = TEXT_PRIMARY
            }
            val instAmountPaintRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f; typeface = bold; textAlign = Paint.Align.RIGHT
            }
            val instStatusPaintRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 8f; typeface = bold; textAlign = Paint.Align.RIGHT
            }

            for (tx in sorted) {
                val statusColor = if (tx.isSettled) GREEN_SETTLED else ORANGE_PENDING
                instAmountPaintRight.color = statusColor
                instStatusPaintRight.color = statusColor

                // Status dot — vertically centered
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor }
                canvas.drawCircle(cardLeft + 14f, yPos + rowH / 2f, 3.5f, dot)

                val leftCol = cardLeft + 28f

                canvas.drawText(
                    "EMI ${tx.emiIndex + 1}/${tx.emiTotal}",
                    leftCol, yPos + 14f, instDatePaint
                )
                canvas.drawText(
                    tx.transactionDate,
                    leftCol, yPos + 28f, instLabelPaint
                )

                // Amount + status — right-aligned from same edge
                val rightEdge = cardRight - 14f
                canvas.drawText(
                    formatMoney(tx.amount),
                    rightEdge, yPos + 14f, instAmountPaintRight
                )
                // Same status label length: pad with leading space so alignment matches
                val statusLabel = if (tx.isSettled) "Paid ✓" else "Pending"
                canvas.drawText(statusLabel, rightEdge, yPos + 28f, instStatusPaintRight)

                // Row separator
                canvas.drawLine(
                    cardLeft + 12f, (yPos + rowH).toFloat(),
                    cardRight - 4f, (yPos + rowH).toFloat(), sepPaint
                )
                yPos += rowH + 2
            }
            yPos += 8
        }

        return yPos
    }

    /** Helper to determine if the active palette is dark mode. */
    private fun isDark(): Boolean = BG_DEEP == DARK_BG_DEEP

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

    private fun drawSettlementHistoryRow(
        canvas: Canvas,
        entry: CustomerSettlementEntry,
        pageWidth: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface,
        rowFillPaint: Paint,
        datePaint: Paint,
        namePaint: Paint,
        labelPaint: Paint,
        sepPaint: Paint
    ): Int {
        val rowH   = 38
        val left   = 40f
        val right  = (pageWidth - 40).toFloat()

        val entryColor = when (entry.type) {
            "settled" -> GREEN_SETTLED
            "partial" -> ORANGE_PENDING
            else -> RED_ACCENT
        }

        canvas.drawRoundRect(RectF(left, startY.toFloat(), right, (startY + rowH).toFloat()), 8f, 8f, rowFillPaint)

        val barPaint = Paint().apply { color = entryColor }
        canvas.drawRoundRect(RectF(left, startY.toFloat(), left + 4f, (startY + rowH).toFloat()), 4f, 4f, barPaint)

        val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = bold; color = entryColor }

        val textTop = startY + 14f
        val textBot = startY + 28f

        canvas.drawText(entry.date, left + 10f, textTop, datePaint)
        canvas.drawText(entry.transactionName, left + 80f, textTop, namePaint)
        canvas.drawText(entry.label, left + 80f, textBot, labelPaint)
        canvas.drawText(formatMoney(entry.amount), right - 120f, textTop, amountPaint)

        canvas.drawLine(left, (startY + rowH).toFloat(), right, (startY + rowH).toFloat(), sepPaint)

        return startY + rowH + 4
    }

    private fun drawSavingsSection(
        canvas: Canvas,
        entries: List<SavingsEntry>,
        pageWidth: Int,
        pageHeight: Int,
        startY: Int,
        regular: Typeface,
        bold: Typeface
    ): Int {
        var yPos = drawSectionHeader(canvas, "Savings Details", pageWidth, startY, bold)
        yPos += 8

        val sorted = entries.sortedByDescending { it.date }
        val totalDeposits = sorted.filter { it.type == com.radafiq.data.models.SavingsType.DEPOSIT }.sumOf { it.amount }
        val totalWithdrawals = sorted.filter { it.type == com.radafiq.data.models.SavingsType.WITHDRAWAL }.sumOf { it.amount }
        val netSavings = totalDeposits - totalWithdrawals

        val boxes = listOf(
            Triple("Total Deposits",  formatMoney(totalDeposits),   GREEN_BRAND),
            Triple("Total Withdrawn", formatMoney(totalWithdrawals), ORANGE_PENDING),
            Triple("Net Savings",     formatMoney(netSavings),      if (netSavings >= 0) GREEN_BRAND else RED_ACCENT)
        )
        yPos = drawMetricBoxRow(canvas, boxes, pageWidth, yPos, regular, bold)
        yPos += 10

        // Hoisted paints — same properties for all entries, created once
        val rowFillPaint = Paint().apply { color = BG_RAISED }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; typeface = bold; color = TEXT_MUTED }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = bold; color = TEXT_PRIMARY }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; typeface = regular; color = TEXT_MUTED }
        val sepPaint = Paint().apply { strokeWidth = 0.5f; color = OUTLINE }

        for (entry in sorted) {
            if (yPos > pageHeight - 90) break // prevent overflow (matches other section thresholds)
            val rowH   = 38
            val left   = 40f
            val right  = (pageWidth - 40).toFloat()

            val isDeposit = entry.type == com.radafiq.data.models.SavingsType.DEPOSIT
            val entryColor = if (isDeposit) GREEN_BRAND else RED_ACCENT

            canvas.drawRoundRect(RectF(left, yPos.toFloat(), right, (yPos + rowH).toFloat()), 8f, 8f, rowFillPaint)

            val barPaint = Paint().apply { color = entryColor }
            canvas.drawRoundRect(RectF(left, yPos.toFloat(), left + 4f, (yPos + rowH).toFloat()), 4f, 4f, barPaint)

            val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = bold; color = entryColor }

            val label = if (isDeposit) "Deposit" else "Withdrawal"
            canvas.drawText(entry.date, left + 10f, yPos + 14f, datePaint)
            canvas.drawText(label, left + 80f, yPos + 14f, namePaint)
            if (entry.note.isNotBlank()) {
                canvas.drawText(entry.note, left + 80f, yPos + 28f, notePaint)
            }
            canvas.drawText(formatMoney(entry.amount), right - 120f, yPos + 14f, amountPaint)

            canvas.drawLine(left, (yPos + rowH).toFloat(), right, (yPos + rowH).toFloat(), sepPaint)

            yPos += rowH + 4
        }

        return yPos
    }

    private fun formatMoney(amount: Double): String {
        val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return "₹${formatter.format(amount)}"
    }
}
