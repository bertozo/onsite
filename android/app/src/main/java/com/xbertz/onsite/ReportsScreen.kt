package com.xbertz.onsite

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xbertz.onsite.report.LabeledTotal
import com.xbertz.onsite.report.ReportColumn
import com.xbertz.onsite.report.ReportPeriod
import com.xbertz.onsite.report.ReportSummary
import com.xbertz.onsite.report.WeekTotal
import com.xbertz.onsite.report.buildCsv
import com.xbertz.onsite.report.summarize
import com.xbertz.onsite.report.totalsByCompany
import com.xbertz.onsite.report.totalsByJobType
import com.xbertz.onsite.report.totalsBySite
import com.xbertz.onsite.report.weeklyTotals
import com.xbertz.onsite.ui.theme.tabularNums
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** "7h 30min" for a duration, through the shared string resource. */
@Composable
private fun durationText(millis: Long): String = stringResource(
    R.string.duration_format,
    TimeUnit.MILLISECONDS.toHours(millis),
    TimeUnit.MILLISECONDS.toMinutes(millis) % 60
)

/** Warning colour for "unbilled" figures; fixed so it reads the same in both themes. */
private val UnbilledAmber = Color(0xFFB8790F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onReviewInvoice: () -> Unit,
    onOpenInvoices: () -> Unit,
    viewModel: LocationTrackerViewModel = viewModel(),
    invoiceViewModel: InvoiceViewModel = viewModel(),
    reportViewModel: ReportViewModel = viewModel()
) {
    val sessions by viewModel.completedSessions.collectAsState()
    val sites by viewModel.sites.collectAsState()
    val companies by invoiceViewModel.companies.collectAsState()
    val columnSelection by reportViewModel.columns.collectAsState()
    val filters by reportViewModel.filters.collectAsState()
    val invoiceState by invoiceViewModel.uiState.collectAsState()
    val columns = remember(columnSelection) { ReportColumn.ordered(columnSelection) }
    val sitesByLabel = remember(sites) { sites.associateBy { it.label } }
    val ratesByCompany = remember(companies) { companies.associate { it.name to it.hourlyRate } }
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    var showColumnsDialog by remember { mutableStateOf(false) }
    var companyMenuOpen by remember { mutableStateOf(false) }
    var periodMenuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    // Coming back from the review step with a fresh PDF: confirm it and offer to share right away.
    val generated = invoiceState.generatedInvoice
    val generatedMessage = generated?.let { stringResource(R.string.invoice_generated, it.number) }
    val shareLabel = stringResource(R.string.share)
    LaunchedEffect(generated?.id) {
        val invoice = generated ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = generatedMessage.orEmpty(),
            actionLabel = shareLabel,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            invoiceViewModel.pdfUri(invoice)?.let { uri ->
                shareInvoicePdf(context, uri)
                invoiceViewModel.markShared(invoice)
            }
        }
        invoiceViewModel.consumeGeneratedInvoice()
    }

    val (startDate, endDate) = filters.range
    val filteredSessions = remember(sessions, filters) {
        val rangeStartMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEndMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        sessions
            .filter { it.startTimestampMillis in rangeStartMillis until rangeEndMillis }
            .filter { filters.company == null || it.companyName == filters.company }
            .filter { !filters.unbilledOnly || it.invoiceId == null }
            .sortedBy { it.startTimestampMillis }
    }
    val summary = remember(filteredSessions, ratesByCompany) { summarize(filteredSessions, ratesByCompany, zone) }
    val weeks = remember(filteredSessions, startDate, endDate) { weeklyTotals(filteredSessions, startDate, endDate, zone) }
    val bySite = remember(filteredSessions) { totalsBySite(filteredSessions) }
    val byCompany = remember(filteredSessions) { totalsByCompany(filteredSessions) }
    val byJobType = remember(filteredSessions) { totalsByJobType(filteredSessions) }

    // Column labels resolved here (composable context) so the CSV builder stays a plain function.
    val columnLabels = ReportColumn.entries.associateWith { stringResource(it.labelRes) }
    val rateLabel = stringResource(R.string.rate)
    val amountLabel = stringResource(R.string.amount)
    val exportTitle = stringResource(R.string.export_csv)

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.menu_reports), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ReportModeSwitch(mode = filters.viewMode, onSelect = { reportViewModel.setViewMode(it) })
            Spacer(Modifier.height(12.dp))

            // Period preset: one dropdown instead of a chip row, so it never needs to scroll or wrap
            // as more presets are added.
            Box {
                FilterChip(
                    selected = true,
                    onClick = { periodMenuOpen = true },
                    label = { Text(stringResource(periodLabelRes(filters.period))) },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                DropdownMenu(expanded = periodMenuOpen, onDismissRequest = { periodMenuOpen = false }) {
                    listOf(
                        ReportPeriod.THIS_WEEK, ReportPeriod.FORTNIGHT, ReportPeriod.THIS_MONTH,
                        ReportPeriod.LAST_MONTH, ReportPeriod.CUSTOM
                    ).forEach { period ->
                        DropdownMenuItem(
                            text = { Text(stringResource(periodLabelRes(period))) },
                            onClick = { reportViewModel.setPeriod(period); periodMenuOpen = false }
                        )
                    }
                }
            }
            if (filters.period == ReportPeriod.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showDatePicker(context, startDate) { reportViewModel.setCustomStart(it) } },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(startDate.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { showDatePicker(context, endDate) { reportViewModel.setCustomEnd(it) } },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(endDate.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${startDate.format(dateFormatter)} – ${endDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Filters: company + unbilled only
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Box {
                    FilterChip(
                        selected = filters.company != null,
                        onClick = { companyMenuOpen = true },
                        label = { Text(filters.company ?: stringResource(R.string.all_companies)) },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenu(expanded = companyMenuOpen, onDismissRequest = { companyMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.all_companies)) },
                            onClick = { reportViewModel.setCompany(null); companyMenuOpen = false }
                        )
                        companies.forEach { company ->
                            DropdownMenuItem(
                                text = { Text(company.name) },
                                onClick = { reportViewModel.setCompany(company.name); companyMenuOpen = false }
                            )
                        }
                    }
                }
                FilterChip(
                    selected = filters.unbilledOnly,
                    onClick = { reportViewModel.setUnbilledOnly(!filters.unbilledOnly) },
                    label = { Text(stringResource(R.string.unbilled_only)) }
                )
            }

            Spacer(Modifier.height(16.dp))
            SummaryCard(summary = summary, showRateHint = summary.estimatedAmount == null && filteredSessions.isNotEmpty())

            when (filters.viewMode) {
                ReportViewMode.LIST -> {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showColumnsDialog = true },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ViewColumn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${stringResource(R.string.columns)}: " + columns.map { stringResource(it.labelRes) }.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    if (filteredSessions.isEmpty()) {
                        EmptyState(text = stringResource(R.string.reports_empty))
                    } else {
                        ReportTable(
                            sessions = filteredSessions,
                            sitesByLabel = sitesByLabel,
                            columns = columns,
                            totalDurationMillis = summary.totalMillis
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val preselected = filters.company?.let { name -> companies.firstOrNull { it.name == name } }
                                    invoiceViewModel.startReview(filteredSessions, startDate, endDate, preselected)
                                    onReviewInvoice()
                                },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.generate_invoice))
                            }
                            OutlinedButton(onClick = onOpenInvoices, shape = MaterialTheme.shapes.medium, modifier = Modifier.height(48.dp)) {
                                Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.menu_invoices))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val csv = buildCsv(filteredSessions, columns, columnLabels, rateLabel, amountLabel, sitesByLabel, ratesByCompany, zone)
                                val uri = reportViewModel.writeCsv(csv, startDate, endDate)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, exportTitle))
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.export_csv))
                        }
                    }
                }
                ReportViewMode.INSIGHTS -> {
                    if (filteredSessions.isEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        EmptyState(text = stringResource(R.string.reports_empty))
                    } else {
                        if (weeks.size > 1 && weeks.any { it.millis > 0 }) {
                            Spacer(Modifier.height(12.dp))
                            WeeklyChartCard(weeks = weeks)
                        }
                        if (bySite.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            BreakdownCard(
                                title = stringResource(R.string.hours_by_site),
                                totals = bySite,
                                emptyLabel = stringResource(R.string.site)
                            )
                        }
                        if (byCompany.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            BreakdownCard(
                                title = stringResource(R.string.hours_by_client),
                                totals = byCompany,
                                emptyLabel = stringResource(R.string.company)
                            )
                        }
                        if (byJobType.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            BreakdownCard(
                                title = stringResource(R.string.hours_by_service),
                                totals = byJobType,
                                emptyLabel = stringResource(R.string.job_type)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showColumnsDialog) {
        ReportColumnsDialog(
            selection = columnSelection,
            onToggle = { column, enabled -> reportViewModel.setColumnEnabled(column, enabled) },
            onDismiss = { showColumnsDialog = false }
        )
    }
}

/** Two-way switch at the top of Reports: the plain session list, or a set of aggregate charts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportModeSwitch(mode: ReportViewMode, onSelect: (ReportViewMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == ReportViewMode.LIST,
            onClick = { onSelect(ReportViewMode.LIST) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
        ) {
            Text(stringResource(R.string.report_mode_list))
        }
        SegmentedButton(
            selected = mode == ReportViewMode.INSIGHTS,
            onClick = { onSelect(ReportViewMode.INSIGHTS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
        ) {
            Text(stringResource(R.string.report_mode_insights))
        }
    }
}

private fun periodLabelRes(period: ReportPeriod): Int = when (period) {
    ReportPeriod.THIS_WEEK -> R.string.period_this_week
    ReportPeriod.FORTNIGHT -> R.string.period_fortnight
    ReportPeriod.THIS_MONTH -> R.string.period_this_month
    ReportPeriod.LAST_MONTH -> R.string.period_last_month
    ReportPeriod.CUSTOM -> R.string.period_custom
}

/** The four headline numbers; "unbilled" is highlighted because it is the one that means money still to collect. */
@Composable
private fun SummaryCard(summary: ReportSummary, showRateHint: Boolean) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile(
                    label = stringResource(R.string.hours),
                    value = durationText(summary.totalMillis),
                    modifier = Modifier.weight(1f)
                )
                SummaryTile(
                    label = stringResource(R.string.summary_days),
                    value = summary.workedDays.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile(
                    label = stringResource(R.string.summary_estimated),
                    value = summary.estimatedAmount?.let { formatMoney(it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                SummaryTile(
                    label = stringResource(R.string.summary_unbilled),
                    value = durationText(summary.unbilledMillis),
                    secondary = summary.unbilledAmount?.let { formatMoney(it) },
                    accent = summary.unbilledMillis > 0,
                    modifier = Modifier.weight(1f)
                )
            }
            if (showRateHint) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.no_rate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier, secondary: String? = null, accent: Boolean = false) {
    val valueColor = if (accent) UnbilledAmber else MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.tabularNums,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
        if (secondary != null) {
            Text(secondary, style = MaterialTheme.typography.bodySmall.tabularNums, color = valueColor)
        }
    }
}

/** Bars per week, drawn with plain composables so they follow the theme without a chart library. */
@Composable
private fun WeeklyChartCard(weeks: List<WeekTotal>) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val labelFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val maxMillis = weeks.maxOf { it.millis }.coerceAtLeast(1L)
    val chartHeight = 110.dp

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.weekly_hours), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().height(chartHeight + 44.dp)
            ) {
                weeks.forEach { week ->
                    val fraction = week.millis.toFloat() / maxMillis
                    val hours = week.millis / 3_600_000.0
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (week.millis == 0L) "" else String.format(java.util.Locale.ENGLISH, "%.1fh", hours),
                            style = MaterialTheme.typography.labelSmall.tabularNums,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height((chartHeight * fraction).coerceAtLeast(3.dp))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (week.millis == 0L) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            week.weekStart.format(labelFormatter).trimEnd('.'),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Hours per label (site, client or service) with a proportional bar, biggest first. */
@Composable
private fun BreakdownCard(title: String, totals: List<LabeledTotal>, emptyLabel: String) {
    val maxMillis = totals.maxOf { it.millis }.coerceAtLeast(1L)
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            totals.forEachIndexed { index, total ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        total.label.ifBlank { emptyLabel },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        durationText(total.millis),
                        style = MaterialTheme.typography.bodyMedium.tabularNums,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(total.millis.toFloat() / maxMillis)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
