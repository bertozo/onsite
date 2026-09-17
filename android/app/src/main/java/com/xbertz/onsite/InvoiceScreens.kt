package com.xbertz.onsite

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xbertz.onsite.data.Invoice
import com.xbertz.onsite.data.InvoiceStatus
import com.xbertz.onsite.invoice.InvoiceLine
import com.xbertz.onsite.invoice.buildInvoiceLines
import com.xbertz.onsite.ui.theme.tabularNums
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

internal fun formatMoney(amount: Double): String = String.format(Locale.ENGLISH, "$%,.2f", amount)

internal fun formatHours(hours: Double): String = String.format(Locale.ENGLISH, "%.2f", hours)

/** Hands a PDF to the system share sheet (email, WhatsApp, save...). */
internal fun shareInvoicePdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_invoice)))
}

/** Opens a PDF in whatever viewer the phone has. */
internal fun openInvoicePdf(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_pdf)))
}

private val StatusPaidGreen = Color(0xFF2E7D32)
private val StatusSentBlue = Color(0xFF1565C0)

@Composable
private fun InvoiceStatusChip(status: InvoiceStatus) {
    val (label, color) = when (status) {
        InvoiceStatus.DRAFT -> stringResource(R.string.status_draft) to MaterialTheme.colorScheme.onSurfaceVariant
        InvoiceStatus.SENT -> stringResource(R.string.status_sent) to StatusSentBlue
        InvoiceStatus.PAID -> stringResource(R.string.status_paid) to StatusPaidGreen
        InvoiceStatus.VOID -> stringResource(R.string.status_void) to MaterialTheme.colorScheme.error
    }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = color.copy(alpha = 0.12f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Review step (before the PDF)
// ---------------------------------------------------------------------------

/**
 * Lets the user check the invoice before the PDF exists: pick the client, number and default rate,
 * untick days, override the rate of a day and add notes. Works on [InvoiceViewModel.review].
 */
@Composable
fun InvoiceReviewScreen(
    onBack: () -> Unit,
    onGenerated: () -> Unit,
    invoiceViewModel: InvoiceViewModel = viewModel(),
    trackerViewModel: LocationTrackerViewModel = viewModel(),
    reportViewModel: ReportViewModel = viewModel()
) {
    val request by invoiceViewModel.review.collectAsState()
    val companies by invoiceViewModel.companies.collectAsState()
    val sites by trackerViewModel.sites.collectAsState()
    val columnSelection by reportViewModel.columns.collectAsState()
    val uiState by invoiceViewModel.uiState.collectAsState()
    val locale = LocalContext.current.resources.configuration.locales[0]
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val lineDateFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }

    val cancel = {
        invoiceViewModel.cancelReview()
        onBack()
    }
    BackHandler(onBack = cancel)

    // Leave as soon as the PDF exists; the Reports screen shows the confirmation.
    LaunchedEffect(uiState.generatedInvoice?.id) {
        if (uiState.generatedInvoice != null) onGenerated()
    }

    val req = request
    if (req == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val companyNamesInPeriod = remember(req) { req.sessions.mapNotNull { it.companyName }.distinct() }
    var company by remember(req.company, companies) {
        mutableStateOf(req.company ?: companies.firstOrNull { it.name == companyNamesInPeriod.singleOrNull() })
    }
    var number by remember { mutableStateOf(invoiceViewModel.nextInvoiceNumber()) }
    var rateText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var excludedDates by remember { mutableStateOf(setOf<LocalDate>()) }
    var companyExpanded by remember { mutableStateOf(false) }
    var rateDialogLine by remember { mutableStateOf<InvoiceLine?>(null) }

    // The company's default rate seeds the field whenever the client changes.
    LaunchedEffect(company?.id) {
        rateText = company?.hourlyRate?.let { formatRateInput(it) }.orEmpty()
    }

    val zone = remember { ZoneId.systemDefault() }
    val sitesByLabel = remember(sites) { sites.associateBy { it.label } }
    val defaultRate = Validators.parseAmount(rateText)
    val companySessions = remember(req, company) { req.sessions.filter { it.companyName == company?.name } }
    val allLines = remember(companySessions, sitesByLabel, defaultRate) {
        buildInvoiceLines(companySessions, sitesByLabel, zone, defaultRate)
    }
    val allDates = remember(allLines) { allLines.map { it.date }.distinct() }
    val includedLines = allLines.filter { it.date !in excludedDates }
    val includedSessions = companySessions.filter {
        Instant.ofEpochMilli(it.startTimestampMillis).atZone(zone).toLocalDate() !in excludedDates
    }
    val totalHours = Math.round(includedLines.sumOf { it.hours } * 100) / 100.0
    val showAmounts = includedLines.any { it.hourlyRate != null }
    val totalAmount = if (showAmounts) includedLines.sumOf { it.amount ?: 0.0 } else null
    val canGenerate = company != null && number.isNotBlank() && Validators.amountOk(rateText) &&
        includedLines.isNotEmpty() && !uiState.isGenerating

    rateDialogLine?.let { line ->
        LineRateDialog(
            line = line,
            title = stringResource(R.string.invoice_line_rate_title, line.date.format(lineDateFormatter)),
            companyRate = company?.hourlyRate,
            onDismiss = { rateDialogLine = null },
            onConfirm = { rate ->
                invoiceViewModel.setLineRate(line, rate)
                rateDialogLine = null
            }
        )
    }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.invoice_review_title), onBack = cancel) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (companies.isEmpty()) {
                        Text(stringResource(R.string.invoice_register_company_first), color = MaterialTheme.colorScheme.error)
                    } else {
                        TrackerDropdown(
                            label = stringResource(R.string.bill_to),
                            icon = Icons.Filled.Business,
                            value = company?.name ?: stringResource(R.string.select_company),
                            expanded = companyExpanded,
                            enabled = !uiState.isGenerating && req.company == null,
                            onExpandedChange = { companyExpanded = it },
                            onDismiss = { companyExpanded = false }
                        ) {
                            companies.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.name) },
                                    onClick = {
                                        company = option
                                        excludedDates = emptySet()
                                        companyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${req.periodStart.format(dateFormatter)} – ${req.periodEnd.format(dateFormatter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text(stringResource(R.string.invoice_number)) },
                        leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                        enabled = !uiState.isGenerating,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text(stringResource(R.string.invoice_default_rate)) },
                        leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                        isError = !Validators.amountOk(rateText),
                        supportingText = {
                            Text(
                                if (!Validators.amountOk(rateText)) stringResource(R.string.enter_valid_amount)
                                else stringResource(R.string.leave_blank_hours_only)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !uiState.isGenerating,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.invoice_notes)) },
                        enabled = !uiState.isGenerating,
                        minLines = 2,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(title = stringResource(R.string.invoice_days_included, allDates.size - excludedDates.count { it in allDates }, allDates.size))
            }
            if (req.alreadyInvoicedDays > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.invoice_already_invoiced, req.alreadyInvoicedDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            if (company == null) {
                EmptyState(text = stringResource(R.string.select_company))
            } else if (allLines.isEmpty()) {
                EmptyState(text = stringResource(R.string.invoice_no_days_for_company, company?.name.orEmpty()))
            } else {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column {
                        LineHeaderRow(showAmounts = showAmounts)
                        allLines.forEach { line ->
                            val included = line.date !in excludedDates
                            InvoiceLineRow(
                                line = line,
                                included = included,
                                dateText = line.date.format(lineDateFormatter).trimEnd('.').replaceFirstChar { it.titlecase(locale) },
                                showAmounts = showAmounts,
                                onToggle = { excludedDates = if (included) excludedDates + line.date else excludedDates - line.date },
                                onClick = { rateDialogLine = line }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.total),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                stringResource(R.string.hours_value, formatHours(totalHours)),
                                style = MaterialTheme.typography.bodyLarge.tabularNums,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (totalAmount != null) {
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    formatMoney(totalAmount),
                                    style = MaterialTheme.typography.titleMedium.tabularNums,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                if (includedLines.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.invoice_select_a_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            uiState.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(error.resId, *error.args.toTypedArray()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val client = company ?: return@Button
                    invoiceViewModel.generateInvoice(
                        sessions = includedSessions,
                        company = client,
                        invoiceNumber = number,
                        hourlyRate = defaultRate,
                        periodStart = req.periodStart,
                        periodEnd = req.periodEnd,
                        columns = columnSelection,
                        notes = notes
                    )
                },
                enabled = canGenerate,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.generate_pdf), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LineHeaderRow(showAmounts: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(40.dp))
        HeaderCell(stringResource(R.string.date), Modifier.weight(1.4f), TextAlign.Start)
        HeaderCell(stringResource(R.string.hours), Modifier.weight(0.8f), TextAlign.End)
        if (showAmounts) {
            HeaderCell(stringResource(R.string.rate), Modifier.weight(0.9f), TextAlign.End)
            HeaderCell(stringResource(R.string.amount), Modifier.weight(1.1f), TextAlign.End)
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text,
        modifier = modifier,
        textAlign = align,
        maxLines = 1,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun InvoiceLineRow(
    line: InvoiceLine,
    included: Boolean,
    dateText: String,
    showAmounts: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val textColor = if (included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val decoration = if (included) null else TextDecoration.LineThrough
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = included, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1.4f)) {
            Text(dateText, style = MaterialTheme.typography.bodyMedium, color = textColor, textDecoration = decoration, maxLines = 1)
            val detail = line.sites.joinToString(", ")
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(
            formatHours(line.hours),
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium.tabularNums,
            color = textColor,
            textDecoration = decoration
        )
        if (showAmounts) {
            Text(
                line.hourlyRate?.let { formatMoney(it) } ?: "—",
                modifier = Modifier.weight(0.9f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium.tabularNums,
                color = textColor,
                textDecoration = decoration
            )
            Text(
                line.amount?.let { formatMoney(it) } ?: "—",
                modifier = Modifier.weight(1.1f),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium.tabularNums,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textDecoration = decoration
            )
        }
    }
}

/** Rate override for one worked day; blank restores the default. */
@Composable
private fun LineRateDialog(
    line: InvoiceLine,
    title: String,
    companyRate: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit
) {
    var rateText by remember { mutableStateOf(line.hourlyRate?.let { formatRateInput(it) }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
        title = { Text(title) },
        text = { SessionRateField(value = rateText, onValueChange = { rateText = it }, companyRate = companyRate) },
        confirmButton = {
            TextButton(onClick = { onConfirm(Validators.parseAmount(rateText)) }, enabled = Validators.amountOk(rateText)) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------------------
// Invoices list
// ---------------------------------------------------------------------------

@Composable
fun InvoicesScreen(
    onBack: () -> Unit,
    onGoToReports: () -> Unit,
    invoiceViewModel: InvoiceViewModel = viewModel()
) {
    val invoices by invoiceViewModel.invoices.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pdfMissing = stringResource(R.string.pdf_missing)
    var statusFilter by remember { mutableStateOf<InvoiceStatus?>(null) }
    var voiding by remember { mutableStateOf<Invoice?>(null) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    val visible = remember(invoices, statusFilter) {
        statusFilter?.let { f -> invoices.filter { it.statusEnum == f } } ?: invoices
    }

    fun withPdf(invoice: Invoice, action: (Uri) -> Unit) {
        val uri = invoiceViewModel.pdfUri(invoice)
        if (uri == null) scope.launch { snackbarHostState.showSnackbar(pdfMissing) } else action(uri)
    }

    voiding?.let { invoice ->
        AlertDialog(
            onDismissRequest = { voiding = null },
            icon = { Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.void_invoice)) },
            text = {
                Text(
                    stringResource(
                        R.string.void_invoice_confirm,
                        invoice.number,
                        invoice.totalAmount?.let { formatMoney(it) } ?: stringResource(R.string.hours_value, formatHours(invoice.totalHours))
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { invoiceViewModel.voidInvoice(invoice); voiding = null }) {
                    Text(stringResource(R.string.void_invoice), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { voiding = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.menu_invoices), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text(stringResource(R.string.filter_all)) })
                FilterChip(selected = statusFilter == InvoiceStatus.DRAFT, onClick = { statusFilter = InvoiceStatus.DRAFT }, label = { Text(stringResource(R.string.status_draft)) })
                FilterChip(selected = statusFilter == InvoiceStatus.SENT, onClick = { statusFilter = InvoiceStatus.SENT }, label = { Text(stringResource(R.string.status_sent)) })
                FilterChip(selected = statusFilter == InvoiceStatus.PAID, onClick = { statusFilter = InvoiceStatus.PAID }, label = { Text(stringResource(R.string.status_paid)) })
            }
            Spacer(Modifier.height(12.dp))

            if (visible.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyState(text = stringResource(R.string.invoices_empty))
                    if (invoices.isEmpty()) {
                        OutlinedButton(onClick = onGoToReports, shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(R.string.go_to_reports))
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(visible, key = { it.id }) { invoice ->
                        InvoiceRow(
                            invoice = invoice,
                            periodText = "${invoice.periodStart.format(dateFormatter)} – ${invoice.periodEnd.format(dateFormatter)}",
                            onOpen = { withPdf(invoice) { openInvoicePdf(context, it) } },
                            onShare = {
                                withPdf(invoice) { uri ->
                                    shareInvoicePdf(context, uri)
                                    invoiceViewModel.markShared(invoice)
                                }
                            },
                            onMarkPaid = { invoiceViewModel.markPaid(invoice) },
                            onVoid = { voiding = invoice }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceRow(
    invoice: Invoice,
    periodText: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onMarkPaid: () -> Unit,
    onVoid: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val status = invoice.statusEnum
    val voided = status == InvoiceStatus.VOID
    val decoration = if (voided) TextDecoration.LineThrough else null

    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                    .padding(9.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(invoice.number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textDecoration = decoration)
                    Spacer(Modifier.width(8.dp))
                    InvoiceStatusChip(status)
                }
                Text(invoice.companyName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(periodText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    invoice.totalAmount?.let { formatMoney(it) } ?: stringResource(R.string.hours_value, formatHours(invoice.totalHours)),
                    style = MaterialTheme.typography.titleMedium.tabularNums,
                    fontWeight = FontWeight.Bold,
                    textDecoration = decoration
                )
                if (invoice.totalAmount != null) {
                    Text(
                        stringResource(R.string.hours_value, formatHours(invoice.totalHours)),
                        style = MaterialTheme.typography.bodySmall.tabularNums,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_pdf)) },
                        leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                        onClick = { menuOpen = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { menuOpen = false; onShare() }
                    )
                    if (status == InvoiceStatus.DRAFT || status == InvoiceStatus.SENT) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mark_paid)) },
                            leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusPaidGreen) },
                            onClick = { menuOpen = false; onMarkPaid() }
                        )
                    }
                    if (!voided) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.void_invoice), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onVoid() }
                        )
                    }
                }
            }
        }
    }
}
