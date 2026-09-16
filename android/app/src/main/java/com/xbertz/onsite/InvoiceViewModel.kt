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
import com.xbertz.onsite.data.TrackingSession
import com.xbertz.onsite.invoice.InvoiceData
import com.xbertz.onsite.invoice.InvoicePdfTemplate
import com.xbertz.onsite.invoice.buildInvoiceLines
import com.xbertz.onsite.report.ReportColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** A user-facing message kept as a resource id so the UI resolves it in the current app language. */
data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

data class InvoiceUiState(
    val isGenerating: Boolean = false,
    /** Content URI of the last generated PDF; the UI shares it and then calls [InvoiceViewModel.consumeGeneratedPdf]. */
    val generatedPdfUri: Uri? = null,
    val error: UiMessage? = null
)

class InvoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val prefs = application.getSharedPreferences("invoices", Context.MODE_PRIVATE)

    val companies: StateFlow<List<Company>> = db.companyDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(InvoiceUiState())
    val uiState: StateFlow<InvoiceUiState> = _uiState

    /** Suggested number for the next invoice, e.g. "INV-0007". Sequential across the app's lifetime. */
    fun nextInvoiceNumber(): String {
        val next = prefs.getInt(KEY_NEXT_NUMBER, 1)
        return String.format(Locale.ENGLISH, "INV-%04d", next)
    }

    fun generateInvoice(
        sessions: List<TrackingSession>,
        company: Company,
        invoiceNumber: String,
        hourlyRate: Double?,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        columns: Set<ReportColumn>
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
            val lines = buildInvoiceLines(companySessions, sitesByLabel)
            // Read the profile on demand rather than through a StateFlow: nothing on the
            // Reports screen collects it, so a shared flow would never start and stay null.
            val provider = db.profileDao().get().first()
            if (provider == null || provider.name.isBlank()) {
                _uiState.update { it.copy(isGenerating = false, error = UiMessage(R.string.invoice_error_profile)) }
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val application = getApplication<Application>()
                    val dir = File(application.cacheDir, "invoices").apply { mkdirs() }
                    val safeNumber = invoiceNumber.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val safeCompany = company.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val file = File(dir, "Invoice_${safeNumber}_$safeCompany.pdf")

                    InvoicePdfTemplate.render(
                        InvoiceData(
                            invoiceNumber = invoiceNumber.trim(),
                            issueDate = LocalDate.now(),
                            periodStart = periodStart,
                            periodEnd = periodEnd,
                            provider = provider,
                            client = company,
                            lines = lines,
                            columns = ReportColumn.ordered(columns),
                            hourlyRate = hourlyRate
                        ),
                        file,
                        res
                    )
                    FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
                }
            }
            result.onSuccess { uri ->
                prefs.edit().putInt(KEY_NEXT_NUMBER, prefs.getInt(KEY_NEXT_NUMBER, 1) + 1).apply()
                _uiState.update { it.copy(isGenerating = false, generatedPdfUri = uri) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isGenerating = false, error = UiMessage(R.string.invoice_error_generate, listOf(e.message.orEmpty())))
                }
            }
        }
    }

    fun consumeGeneratedPdf() = _uiState.update { it.copy(generatedPdfUri = null) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private companion object {
        const val KEY_NEXT_NUMBER = "next_invoice_number"
    }
}
