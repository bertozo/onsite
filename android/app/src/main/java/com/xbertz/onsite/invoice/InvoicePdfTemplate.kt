package com.xbertz.onsite.invoice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.xbertz.onsite.R
import com.xbertz.onsite.report.ReportColumn
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/**
 * Fixed A4 layout shared by every invoice the app produces. Change this file to
 * change the look of all invoices; the content comes in through [InvoiceData] and
 * the labels through the [Resources] passed to [render] (so the PDF follows the app language).
 *
 * Structure (top to bottom):
 *  1. Header - "INVOICE" title, number/date/period, provider photo.
 *  2. Parties - "FROM" (profile) and "BILL TO" (company) side by side.
 *  3. Table - one row per worked day, with the columns the user enabled (see
 *     [ReportColumn]); widths come from [columnSpecs] so the layout stays uniform.
 *  4. Totals - total hours and, when a rate was given, the amount due.
 *  5. Payment details - bank BSB/account from the profile.
 *  6. Footer - "Page x of y" and generation note.
 */
object InvoicePdfTemplate {

    // A4 in PostScript points (1/72 in), the unit PdfDocument works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    private const val ROW_HEIGHT = 22f
    private const val TABLE_HEADER_HEIGHT = 26f
    private const val FOOTER_HEIGHT = 36f
    private const val HEADER_BLOCK_HEIGHT = 190f // header + parties, fixed so pagination is predictable
    private const val TOTALS_BLOCK_HEIGHT = 190f // totals + payment details

    private val ACCENT = Color.rgb(0x1E, 0x5A, 0x96)
    private val ACCENT_LIGHT = Color.rgb(0xE3, 0xEC, 0xF7)
    private val TEXT = Color.rgb(0x1F, 0x23, 0x28)
    private val MUTED = Color.rgb(0x6B, 0x72, 0x7A)
    private val LINE = Color.rgb(0xD5, 0xDA, 0xE0)
    private val ZEBRA = Color.rgb(0xF6, 0xF8, 0xFA)

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

    private val titlePaint = textPaint(28f, ACCENT, Typeface.BOLD)
    private val headingPaint = textPaint(9f, MUTED, Typeface.BOLD).apply { letterSpacing = 0.12f }
    private val bodyPaint = textPaint(10f, TEXT)
    private val bodySmallPaint = textPaint(8.5f, TEXT)
    private val bodyBoldPaint = textPaint(10f, TEXT, Typeface.BOLD)
    private val mutedPaint = textPaint(9f, MUTED)
    private val tableHeaderPaint = textPaint(9f, ACCENT, Typeface.BOLD)
    private val totalLabelPaint = textPaint(11f, TEXT, Typeface.BOLD)
    private val totalValuePaint = textPaint(14f, ACCENT, Typeface.BOLD)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = LINE
    }

    /** A resolved table column: where it sits, how wide it is and how to fill a cell. */
    private class Column(
        val title: String,
        val x: Float,
        val width: Float,
        val alignRight: Boolean,
        val value: (InvoiceLine) -> String
    )

    /** Template widths: fixed columns in points; text columns share the leftover width by weight. */
    private class ColumnSpec(val fixedWidth: Float?, val weight: Float, val alignRight: Boolean)

    private val columnSpecs = mapOf(
        ReportColumn.DATE to ColumnSpec(62f, 0f, alignRight = false),
        ReportColumn.SITE to ColumnSpec(null, 1f, alignRight = false),
        ReportColumn.ADDRESS to ColumnSpec(null, 1.6f, alignRight = false),
        ReportColumn.COMPANY to ColumnSpec(null, 1f, alignRight = false),
        ReportColumn.JOB_TYPE to ColumnSpec(null, 1f, alignRight = false),
        ReportColumn.START_TIME to ColumnSpec(44f, 0f, alignRight = true),
        ReportColumn.END_TIME to ColumnSpec(44f, 0f, alignRight = true),
        ReportColumn.HOURS to ColumnSpec(48f, 0f, alignRight = true)
    )
    private val rateSpec = ColumnSpec(58f, 0f, alignRight = true)
    private val amountSpec = ColumnSpec(68f, 0f, alignRight = true)

    private fun columns(data: InvoiceData, res: Resources): List<Column> {
        val locale = res.configuration.locales[0]
        val entries = mutableListOf<Triple<String, ColumnSpec, (InvoiceLine) -> String>>()
        data.columns.forEach { column ->
            entries += Triple(res.getString(column.labelRes).uppercase(locale), columnSpecs.getValue(column)) { line -> line.valueFor(column) }
        }
        data.hourlyRate?.let { rate ->
            entries += Triple(res.getString(R.string.pdf_col_rate), rateSpec) { formatMoney(rate) }
            entries += Triple(res.getString(R.string.pdf_col_amount), amountSpec) { line ->
                formatMoney(Math.round(line.hours * rate * 100) / 100.0)
            }
        }

        val fixedSum = entries.sumOf { (it.second.fixedWidth ?: 0f).toDouble() }.toFloat()
        val totalWeight = entries.sumOf { it.second.weight.toDouble() }.toFloat()
        val leftover = (CONTENT_WIDTH - fixedSum).coerceAtLeast(0f)
        val flexUnit = if (totalWeight > 0f) leftover / totalWeight else 0f

        var x = 0f
        return entries.mapIndexed { index, (title, spec, value) ->
            var width = spec.fixedWidth ?: (spec.weight * flexUnit)
            // With no text column enabled, let the first column absorb the free space so the table still spans the page.
            if (totalWeight == 0f && index == 0) width += leftover
            Column(title, x, width, spec.alignRight, value).also { x += width }
        }
    }

    /** Renders [data] into [outFile] using [res] for labels. Pure CPU + file IO; call it off the main thread. */
    fun render(data: InvoiceData, outFile: File, res: Resources) {
        val document = PdfDocument()
        val photo = loadPhoto(data.provider.photoPath)
        val cols = columns(data, res)
        val cellPaint = if (cols.size > 5) bodySmallPaint else bodyPaint

        // Pagination is computed up front so each footer can print "Page x of y".
        val firstTableTop = MARGIN + HEADER_BLOCK_HEIGHT
        val tableBottomLimit = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT
        val firstPageRows = rowsThatFit(tableBottomLimit - firstTableTop - TABLE_HEADER_HEIGHT)
        val nextPageRows = rowsThatFit(tableBottomLimit - MARGIN - TABLE_HEADER_HEIGHT)
        val pages = paginate(data.lines, firstPageRows, nextPageRows)

        // The totals block follows the last row; if it doesn't fit, it gets its own page.
        val lastTableTop = if (pages.size == 1) firstTableTop else MARGIN
        val lastTableBottom = lastTableTop + TABLE_HEADER_HEIGHT + pages.last().size * ROW_HEIGHT
        val totalsOnOwnPage = lastTableBottom + 16f + TOTALS_BLOCK_HEIGHT > tableBottomLimit
        val totalPages = pages.size + if (totalsOnOwnPage) 1 else 0

        var rowOffset = 0
        pages.forEachIndexed { pageIndex, rows ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val tableTop = if (pageIndex == 0) {
                val headerBottom = drawHeader(canvas, data, photo, res)
                drawParties(canvas, data, headerBottom, res)
                firstTableTop
            } else {
                MARGIN
            }

            val tableBottom = drawTable(canvas, cols, rows, rowOffset, tableTop, cellPaint)
            rowOffset += rows.size

            if (pageIndex == pages.lastIndex && !totalsOnOwnPage) {
                drawTotals(canvas, data, tableBottom + 16f, res)
            }
            drawFooter(canvas, data, pageIndex + 1, totalPages, res)
            document.finishPage(page)
        }

        if (totalsOnOwnPage) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, totalPages).create()
            val page = document.startPage(pageInfo)
            drawTotals(page.canvas, data, MARGIN, res)
            drawFooter(page.canvas, data, totalPages, totalPages, res)
            document.finishPage(page)
        }

        FileOutputStream(outFile).use { document.writeTo(it) }
        document.close()
        photo?.recycle()
    }

    // ---------------------------------------------------------------------
    // Sections
    // ---------------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, data: InvoiceData, photo: Bitmap?, res: Resources): Float {
        val top = MARGIN
        canvas.drawText(res.getString(R.string.pdf_title), MARGIN, top + 28f, titlePaint)

        // Accent rule under the title.
        fillPaint.color = ACCENT
        canvas.drawRect(MARGIN, top + 38f, MARGIN + 60f, top + 41f, fillPaint)

        var y = top + 60f
        y = drawLabelValue(canvas, res.getString(R.string.pdf_invoice_no), data.invoiceNumber, MARGIN, y)
        y = drawLabelValue(canvas, res.getString(R.string.pdf_issue_date), data.issueDate.format(dateFormatter), MARGIN, y)
        val period = "${data.periodStart.format(dateFormatter)} - ${data.periodEnd.format(dateFormatter)}"
        y = drawLabelValue(canvas, res.getString(R.string.pdf_period), period, MARGIN, y)

        if (photo != null) {
            val size = 64f
            val left = PAGE_WIDTH - MARGIN - size
            val bounds = RectF(left, top, left + size, top + size)
            val clip = Path().apply { addOval(bounds, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(photo, null, bounds, fillPaint)
            canvas.restore()
        }
        return y + 12f
    }

    private fun drawParties(canvas: Canvas, data: InvoiceData, startY: Float, res: Resources): Float {
        val columnWidth = CONTENT_WIDTH / 2f - 10f
        val leftX = MARGIN
        val rightX = MARGIN + CONTENT_WIDTH / 2f + 10f

        canvas.drawText(res.getString(R.string.pdf_from), leftX, startY, headingPaint)
        canvas.drawText(res.getString(R.string.pdf_bill_to), rightX, startY, headingPaint)

        val provider = data.provider
        val providerLines = listOfNotNull(
            provider.name,
            provider.abn?.takeIf { it.isNotBlank() }?.let { res.getString(R.string.abn_value, it) },
            provider.role,
            provider.phone,
            provider.email
        ).filter { it.isNotBlank() }

        val client = data.client
        val clientLines = listOfNotNull(
            client.name,
            client.abn?.takeIf { it.isNotBlank() }?.let { res.getString(R.string.abn_value, it) },
            client.phone,
            client.email
        ).filter { it.isNotBlank() }

        val leftBottom = drawParty(canvas, providerLines, leftX, startY + 16f, columnWidth)
        val rightBottom = drawParty(canvas, clientLines, rightX, startY + 16f, columnWidth)
        return maxOf(leftBottom, rightBottom)
    }

    private fun drawParty(canvas: Canvas, lines: List<String>, x: Float, startY: Float, maxWidth: Float): Float {
        var y = startY
        lines.forEachIndexed { index, line ->
            val paint = if (index == 0) bodyBoldPaint else bodyPaint
            canvas.drawText(ellipsize(line, paint, maxWidth), x, y, paint)
            y += 14f
        }
        return y
    }

    private fun drawTable(
        canvas: Canvas,
        cols: List<Column>,
        rows: List<InvoiceLine>,
        rowOffset: Int,
        startY: Float,
        cellPaint: Paint
    ): Float {
        var y = startY

        fillPaint.color = ACCENT_LIGHT
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + TABLE_HEADER_HEIGHT, fillPaint)
        val headerBaseline = y + TABLE_HEADER_HEIGHT / 2f + 3.5f
        cols.forEach { col -> drawCell(canvas, col.title, col, headerBaseline, tableHeaderPaint) }
        y += TABLE_HEADER_HEIGHT

        rows.forEachIndexed { index, line ->
            if ((rowOffset + index) % 2 == 1) {
                fillPaint.color = ZEBRA
                canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + ROW_HEIGHT, fillPaint)
            }
            val baseline = y + ROW_HEIGHT / 2f + 3.5f
            cols.forEach { col -> drawCell(canvas, col.value(line), col, baseline, cellPaint) }
            y += ROW_HEIGHT
            canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, linePaint)
        }
        return y
    }

    private fun drawTotals(canvas: Canvas, data: InvoiceData, startY: Float, res: Resources) {
        val boxWidth = 220f
        val boxLeft = MARGIN + CONTENT_WIDTH - boxWidth
        val rightEdge = MARGIN + CONTENT_WIDTH
        var y = startY

        canvas.drawText(res.getString(R.string.pdf_total_hours), boxLeft, y + 14f, totalLabelPaint)
        drawRightAligned(canvas, formatHours(data.totalHours), rightEdge, y + 14f, totalLabelPaint)
        y += 22f

        data.hourlyRate?.let { rate ->
            canvas.drawText(res.getString(R.string.pdf_hourly_rate), boxLeft, y + 12f, bodyPaint)
            drawRightAligned(canvas, formatMoney(rate), rightEdge, y + 12f, bodyPaint)
            y += 20f

            fillPaint.color = ACCENT_LIGHT
            canvas.drawRect(boxLeft - 10f, y, rightEdge, y + 32f, fillPaint)
            canvas.drawText(res.getString(R.string.pdf_total_due), boxLeft, y + 21f, totalLabelPaint)
            drawRightAligned(canvas, formatMoney(data.totalAmount ?: 0.0), rightEdge - 4f, y + 21f, totalValuePaint)
            y += 32f
        }

        y += 28f
        canvas.drawText(res.getString(R.string.pdf_payment_details), MARGIN, y, headingPaint)
        y += 16f
        val provider = data.provider
        val paymentLines = listOfNotNull(
            provider.name.takeIf { it.isNotBlank() }?.let { res.getString(R.string.pdf_account_name, it) },
            provider.bankBsb?.takeIf { it.isNotBlank() }?.let { res.getString(R.string.pdf_bsb, it) },
            provider.bankAccount?.takeIf { it.isNotBlank() }?.let { res.getString(R.string.pdf_account_number, it) }
        ).ifEmpty { listOf(res.getString(R.string.pdf_no_bank_details)) }
        paymentLines.forEach { line ->
            canvas.drawText(line, MARGIN, y, bodyPaint)
            y += 14f
        }
    }

    private fun drawFooter(canvas: Canvas, data: InvoiceData, pageNumber: Int, totalPages: Int, res: Resources) {
        val y = PAGE_HEIGHT - MARGIN + 4f
        canvas.drawLine(MARGIN, y - 16f, MARGIN + CONTENT_WIDTH, y - 16f, linePaint)
        canvas.drawText(res.getString(R.string.pdf_footer, data.invoiceNumber), MARGIN, y, mutedPaint)
        drawRightAligned(canvas, res.getString(R.string.pdf_page, pageNumber, totalPages), MARGIN + CONTENT_WIDTH, y, mutedPaint)
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun rowsThatFit(availableHeight: Float): Int = maxOf(1, (availableHeight / ROW_HEIGHT).toInt())

    private fun paginate(lines: List<InvoiceLine>, firstPageRows: Int, nextPageRows: Int): List<List<InvoiceLine>> {
        if (lines.isEmpty()) return listOf(emptyList())
        val pages = mutableListOf<List<InvoiceLine>>()
        var index = 0
        while (index < lines.size) {
            val capacity = if (pages.isEmpty()) firstPageRows else nextPageRows
            val end = min(lines.size, index + capacity)
            pages += lines.subList(index, end)
            index = end
        }
        return pages
    }

    private fun drawLabelValue(canvas: Canvas, label: String, value: String, x: Float, y: Float): Float {
        canvas.drawText(label, x, y, mutedPaint)
        canvas.drawText(value, x + 70f, y, bodyBoldPaint)
        return y + 15f
    }

    private fun drawCell(canvas: Canvas, text: String, col: Column, baseline: Float, paint: Paint) {
        val padding = 6f
        val clipped = ellipsize(text, paint, col.width - 2 * padding)
        if (col.alignRight) {
            drawRightAligned(canvas, clipped, MARGIN + col.x + col.width - padding, baseline, paint)
        } else {
            canvas.drawText(clipped, MARGIN + col.x + padding, baseline, paint)
        }
    }

    private fun drawRightAligned(canvas: Canvas, text: String, rightX: Float, baseline: Float, paint: Paint) {
        canvas.drawText(text, rightX - paint.measureText(text), baseline, paint)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "..."
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) end--
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private fun formatHours(hours: Double): String = String.format(Locale.ENGLISH, "%.2f", hours)

    private fun formatMoney(amount: Double): String = String.format(Locale.ENGLISH, "$%,.2f", amount)

    private fun textPaint(size: Float, color: Int, style: Int = Typeface.NORMAL): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
        }
    }

    /** Decodes and center-crops the profile photo to a small square; null if missing or unreadable. */
    private fun loadPhoto(path: String?): Bitmap? {
        if (path.isNullOrBlank() || !File(path).exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = maxOf(1, min(bounds.outWidth, bounds.outHeight) / 256)
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val side = min(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(
                decoded,
                (decoded.width - side) / 2,
                (decoded.height - side) / 2,
                side,
                side
            )
            if (square !== decoded) decoded.recycle()
            square
        } catch (e: Exception) {
            null
        }
    }
}
