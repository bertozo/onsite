package com.xbertz.onsite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xbertz.onsite.data.Company
import com.xbertz.onsite.data.Invoice
import com.xbertz.onsite.data.InvoiceStatus
import com.xbertz.onsite.data.TrackingSession
import com.xbertz.onsite.report.summarize
import com.xbertz.onsite.ui.theme.tabularNums
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private val UnbilledAmber = Color(0xFFB8790F)

/**
 * Bottom sheet with one client's numbers: hours this month, what is still unbilled and the latest
 * invoices, plus shortcuts to invoice the open sessions or open the filtered report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySheet(
    company: Company,
    sessions: List<TrackingSession>,
    invoices: List<Invoice>,
    onDismiss: () -> Unit,
    onGenerateInvoice: (sessions: List<TrackingSession>, start: LocalDate, end: LocalDate) -> Unit,
    onViewReport: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val companySessions = remember(sessions, company) { sessions.filter { it.companyName == company.name && it.stopTimestampMillis != null } }
    val month = YearMonth.now()
    val monthSessions = remember(companySessions) {
        companySessions.filter { YearMonth.from(Instant.ofEpochMilli(it.startTimestampMillis).atZone(zone).toLocalDate()) == month }
    }
    val rates = mapOf(company.name to company.hourlyRate)
    val monthSummary = summarize(monthSessions, rates, zone)
    val allSummary = summarize(companySessions, rates, zone)
    val unbilled = companySessions.filter { it.invoiceId == null }
    val recentInvoices = remember(invoices, company) { invoices.filter { it.companyName == company.name }.take(3) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    // Insets are handled by the column below (navigationBarsPadding), so the sheet itself takes none.
    ModalBottomSheet(onDismissRequest = onDismiss, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(company.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            company.hourlyRate?.let { rate ->
                Text(
                    stringResource(R.string.rate_per_hour, formatRateInput(rate)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTile(
                    label = stringResource(R.string.period_this_month),
                    value = durationText(monthSummary.totalMillis),
                    secondary = monthSummary.estimatedAmount?.let { formatMoney(it) },
                    modifier = Modifier.weight(1f)
                )
                SheetTile(
                    label = stringResource(R.string.summary_unbilled),
                    value = durationText(allSummary.unbilledMillis),
                    secondary = allSummary.unbilledAmount?.let { formatMoney(it) },
                    accent = allSummary.unbilledMillis > 0,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.company_recent_invoices),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (recentInvoices.isEmpty()) {
                Text(stringResource(R.string.company_no_invoices), style = MaterialTheme.typography.bodyMedium)
            } else {
                recentInvoices.forEachIndexed { index, invoice ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(invoice.number, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                invoice.issueDate.format(dateFormatter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            invoice.totalAmount?.let { formatMoney(it) } ?: stringResource(R.string.hours_value, formatHours(invoice.totalHours)),
                            style = MaterialTheme.typography.bodyMedium.tabularNums,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        SheetStatusChip(invoice.statusEnum)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val dates = unbilled.map { Instant.ofEpochMilli(it.startTimestampMillis).atZone(zone).toLocalDate() }
                    onGenerateInvoice(unbilled, dates.min(), dates.max())
                },
                enabled = unbilled.isNotEmpty(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (unbilled.isEmpty()) stringResource(R.string.company_nothing_to_invoice) else stringResource(R.string.generate_invoice))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onViewReport, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.view_report))
            }
        }
    }
}

@Composable
private fun durationText(millis: Long): String = stringResource(
    R.string.duration_format,
    TimeUnit.MILLISECONDS.toHours(millis),
    TimeUnit.MILLISECONDS.toMinutes(millis) % 60
)

@Composable
private fun SheetTile(label: String, value: String, modifier: Modifier = Modifier, secondary: String? = null, accent: Boolean = false) {
    val color = if (accent) UnbilledAmber else MaterialTheme.colorScheme.onSurface
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium.tabularNums, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            if (secondary != null) {
                Text(secondary, style = MaterialTheme.typography.bodySmall.tabularNums, color = color)
            }
        }
    }
}

@Composable
private fun SheetStatusChip(status: InvoiceStatus) {
    val (label, color) = when (status) {
        InvoiceStatus.DRAFT -> stringResource(R.string.status_draft) to MaterialTheme.colorScheme.onSurfaceVariant
        InvoiceStatus.SENT -> stringResource(R.string.status_sent) to Color(0xFF1565C0)
        InvoiceStatus.PAID -> stringResource(R.string.status_paid) to Color(0xFF2E7D32)
        InvoiceStatus.VOID -> stringResource(R.string.status_void) to MaterialTheme.colorScheme.error
    }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = color.copy(alpha = 0.12f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
