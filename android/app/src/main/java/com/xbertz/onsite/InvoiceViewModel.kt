package com.xbertz.onsite

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.Company
import com.xbertz.onsite.data.Invoice
import com.xbertz.onsite.data.InvoiceStatus
import com.xbertz.onsite.data.TrackingSession
import com.xbertz.onsite.invoice.InvoiceData
import com.xbertz.onsite.invoice.InvoiceLine
import com.xbertz.onsite.invoice.InvoicePdfTemplate
import com.xbertz.onsite.invoice.buildInvoiceLines
import com.xbertz.onsite.report.ReportColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** A user-facing message kept as a resource id so the UI resolves it in the current app language. */
data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

/**
 * What the review screen works on: the candidate sessions of a period. Sessions already billed
 * by another invoice are dropped here so they can't be invoiced twice.
 */
data class InvoiceReviewRequest(
    val sessions: List<TrackingSession>,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    /** Pre-selected client, e.g. when the review is opened from the company sheet. */
    val company: Company? = null,
    /** How many worked days were left out because they were already invoiced. */
    val alreadyInvoicedDays: Int = 0
)

data class InvoiceUiState(
    val isGenerating: Boolean = false,
    /** Set right after a PDF is written; the UI shows a snackbar with a share action and then consumes it. */
    val generatedInvoice: Invoice? = null,
    val error: UiMessage? = null
)

class InvoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val invoiceDao = db.invoiceDao()
    private val sessionDao = db.trackingSessionDao()
    private val prefs = application.getSharedPreferences("invoices", Context.MODE_PRIVATE)

    val companies: StateFlow<List<Company>> = db.companyDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoices: StateFlow<List<Invoice>> = invoiceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(InvoiceUiState())
    val uiState: StateFlow<InvoiceUiState> = _uiState

    private val _review = MutableStateFlow<InvoiceReviewRequest?>(null)
    /** The pending review, read by the review screen; null when nothing is being reviewed. */
    val review: StateFlow<InvoiceReviewRequest?> = _review

    /** Suggested number for the next invoice, e.g. "INV-0007". Sequential across the app's lifetime. */
    fun nextInvoiceNumber(): String {
        val next = prefs.getInt(KEY_NEXT_NUMBER, 1)
        return String.format(Locale.ENGLISH, "INV-%04d", next)
    }

    /** Opens the review step for the completed, not-yet-invoiced sessions of a period. */
    fun startReview(sessions: List<TrackingSession>, periodStart: LocalDate, periodEnd: LocalDate, company: Company? = null) {
        val completed = sessions.filter { it.stopTimestampMillis != null }
        val (billed, open) = completed.partition { it.invoiceId != null }
        _review.value = InvoiceReviewRequest(
            sessions = open,
            periodStart = periodStart,
            periodEnd = periodEnd,
            company = company,
            alreadyInvoicedDays = buildInvoiceLines(billed).map { it.date }.distinct().size
        )
        _uiState.update { it.copy(error = null) }
    }

    fun cancelReview() {
        _review.value = null
    }

    /** Rate override for every session of one invoice line (null restores the company default). */
    fun setLineRate(line: InvoiceLine, rate: Double?) {
        viewModelScope.launch {
            sessionDao.setHourlyRate(line.sessionIds, rate)
            // Keep the review in sync without a round trip through the DAO flow.
            _review.update { req ->
                req?.copy(sessions = req.sessions.map { if (it.id in line.sessionIds) it.copy(hourlyRate = rate) else it })
            }
        }
    }

    /**
     * Renders the PDF, stores the [Invoice] row and links the billed sessions to it.
     * [sessions] are the ones the user kept in the review (days can be unticked).
     */
    fun generateInvoice(
        sessions: List<TrackingSession>,
        company: Company,
        invoiceNumber: String,
        hourlyRate: Double?,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        columns: Set<ReportColumn>,
        notes: String?
    ) {
        val res = LocaleManager.resources(getApplication())
        val companySessions = sessions.filter { it.companyName == company.name && it.stopTimestampMillis != null }
        if (companySessions.isEmpty()) {
            _uiState.update { it.copy(error = UiMessage(R.string.invoice_no_days_for_company, listOf(company.name))) }
            return
        }

        _uiState.update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch {
            // Sessions only carry the site label; the address column is looked up from the registered sites.
            val sitesByLabel = db.siteDao().getAll().first().associateBy { it.label }
            val lines = buildInvoiceLines(companySessions, sitesByLabel, defaultRate = hourlyRate)
            // Read the profile on demand rather than through a StateFlow: nothing on the
            // Reports screen collects it, so a shared flow would never start and stay null.
            val provider = db.profileDao().get().first()
            if (provider == null || provider.name.isBlank()) {
                _uiState.update { it.copy(isGenerating = false, error = UiMessage(R.string.invoice_error_profile)) }
                return@launch
            }

            val data = InvoiceData(
                invoiceNumber = invoiceNumber.trim(),
                issueDate = LocalDate.now(),
                periodStart = periodStart,
                periodEnd = periodEnd,
                provider = provider,
                client = company,
                lines = lines,
                columns = ReportColumn.ordered(columns),
                hourlyRate = hourlyRate,
                notes = notes?.trim()?.ifBlank { null }
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = pdfFile(invoiceNumber, company)
                    InvoicePdfTemplate.render(data, file, res)
                    file
                }
            }
            result.onSuccess { file ->
                val invoice = Invoice(
                    number = data.invoiceNumber,
                    companyName = company.name,
                    periodStartEpochDay = periodStart.toEpochDay(),
                    periodEndEpochDay = periodEnd.toEpochDay(),
                    issueDateEpochDay = data.issueDate.toEpochDay(),
                    totalHours = data.totalHours,
                    totalAmount = data.totalAmount,
                    hourlyRate = hourlyRate,
                    pdfPath = file.absolutePath,
                    notes = data.notes,
                    createdAtMillis = System.currentTimeMillis()
                )
                val id = invoiceDao.insert(invoice)
                sessionDao.markInvoiced(companySessions.map { it.id }, id)
                prefs.edit().putInt(KEY_NEXT_NUMBER, prefs.getInt(KEY_NEXT_NUMBER, 1) + 1).apply()
                _review.value = null
                _uiState.update { it.copy(isGenerating = false, generatedInvoice = invoice.copy(id = id)) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isGenerating = false, error = UiMessage(R.string.invoice_error_generate, listOf(e.message.orEmpty())))
                }
            }
        }
    }

    /** Content URI for the stored PDF, or null when the file is gone. */
    fun pdfUri(invoice: Invoice): Uri? {
        val file = File(invoice.pdfPath)
        if (!file.exists()) return null
        val application = getApplication<Application>()
        return FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
    }

    /** First share of a draft moves it to "sent"; later shares leave the status alone. */
    fun markShared(invoice: Invoice) {
        if (invoice.statusEnum != InvoiceStatus.DRAFT) return
        viewModelScope.launch {
            invoiceDao.update(invoice.copy(status = InvoiceStatus.SENT.name, sentAtMillis = System.currentTimeMillis()))
        }
    }

    fun markPaid(invoice: Invoice) {
        viewModelScope.launch {
            invoiceDao.update(invoice.copy(status = InvoiceStatus.PAID.name, paidAtMillis = System.currentTimeMillis()))
        }
    }

    /** Keeps the row for the audit trail but frees its sessions to be invoiced again. */
    fun voidInvoice(invoice: Invoice) {
        viewModelScope.launch {
            invoiceDao.update(invoice.copy(status = InvoiceStatus.VOID.name))
            sessionDao.clearInvoice(invoice.id)
        }
    }

    fun consumeGeneratedInvoice() = _uiState.update { it.copy(generatedInvoice = null) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun pdfFile(invoiceNumber: String, company: Company): File {
        val application = getApplication<Application>()
        val dir = File(application.filesDir, "invoices").apply { mkdirs() }
        val safeNumber = invoiceNumber.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeCompany = company.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        var file = File(dir, "Invoice_${safeNumber}_$safeCompany.pdf")
        // Never overwrite an earlier PDF with the same number (e.g. a voided invoice reissued).
        var attempt = 2
        while (file.exists()) {
            file = File(dir, "Invoice_${safeNumber}_${safeCompany}_$attempt.pdf")
            attempt++
        }
        return file
    }

    private companion object {
        const val KEY_NEXT_NUMBER = "next_invoice_number"
    }
}
