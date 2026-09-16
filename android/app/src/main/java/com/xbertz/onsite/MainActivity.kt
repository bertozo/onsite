package com.xbertz.onsite

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xbertz.onsite.data.Company
import com.xbertz.onsite.data.JobType
import com.xbertz.onsite.data.PlannedJob
import com.xbertz.onsite.data.Site
import com.xbertz.onsite.data.TrackingSession
import com.xbertz.onsite.invoice.buildInvoiceLines
import com.xbertz.onsite.report.ReportColumn
import com.xbertz.onsite.ui.theme.OnSiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    // Apply the language chosen in Settings before any resource is resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            OnSiteTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}

private enum class Screen {
    MENU, TRACKER, REPORTS, COMPANIES, SITES, JOB_TYPES, PROFILE, SETTINGS, PLANNING
}

@Composable
fun AppRoot(settingsViewModel: SettingsViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.MENU) }

    if (screen != Screen.MENU) {
        BackHandler { screen = Screen.MENU }
    }

    when (screen) {
        Screen.MENU -> MainMenuScreen(
            onProfileClick = { screen = Screen.PROFILE },
            onCompaniesClick = { screen = Screen.COMPANIES },
            onSitesClick = { screen = Screen.SITES },
            onJobTypesClick = { screen = Screen.JOB_TYPES },
            onTrackerClick = { screen = Screen.TRACKER },
            onReportsClick = { screen = Screen.REPORTS },
            onPlanningClick = { screen = Screen.PLANNING },
            onSettingsClick = { screen = Screen.SETTINGS }
        )
        Screen.TRACKER -> TrackerScreen(onBack = { screen = Screen.MENU })
        Screen.REPORTS -> ReportsScreen(onBack = { screen = Screen.MENU })
        Screen.COMPANIES -> CompaniesScreen(onBack = { screen = Screen.MENU })
        Screen.SITES -> SitesScreen(onBack = { screen = Screen.MENU })
        Screen.JOB_TYPES -> JobTypesScreen(onBack = { screen = Screen.MENU })
        Screen.PROFILE -> ProfileScreen(onBack = { screen = Screen.MENU })
        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.MENU }, viewModel = settingsViewModel)
        Screen.PLANNING -> PlanningScreen(onBack = { screen = Screen.MENU })
    }
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

private data class MenuAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InfoBanner(icon: ImageVector, text: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = contentColor)
        }
    }
}

@Composable
private fun EntityListItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerDropdown(
    label: String,
    icon: ImageVector,
    value: String,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            readOnly = true,
            value = value,
            onValueChange = {},
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = onDismiss, content = content)
    }
}

private fun showDatePicker(context: Context, initialDate: LocalDate, onDatePicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onDatePicked(LocalDate.of(year, month + 1, dayOfMonth)) },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth
    ).show()
}

private fun showTimePicker(context: Context, initialTime: LocalTime, onTimePicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onTimePicked(LocalTime.of(hour, minute)) },
        initialTime.hour,
        initialTime.minute,
        true
    ).show()
}

// ---------------------------------------------------------------------------
// Main menu
// ---------------------------------------------------------------------------

@Composable
fun MainMenuScreen(
    onProfileClick: () -> Unit,
    onCompaniesClick: () -> Unit,
    onSitesClick: () -> Unit,
    onJobTypesClick: () -> Unit,
    onTrackerClick: () -> Unit,
    onReportsClick: () -> Unit,
    onPlanningClick: () -> Unit,
    onSettingsClick: () -> Unit,
    profileViewModel: ProfileViewModel = viewModel(),
    calendarViewModel: CalendarViewModel = viewModel()
) {
    val profile by profileViewModel.uiState.collectAsState()
    val profilePhoto = rememberBitmapFromFile(profile.photoPath)
    val calendar by calendarViewModel.uiState.collectAsState()

    val secondaryActions = listOf(
        MenuAction(stringResource(R.string.menu_reports), stringResource(R.string.menu_reports_subtitle), Icons.Filled.Assessment, onReportsClick),
        MenuAction(stringResource(R.string.menu_companies), stringResource(R.string.menu_companies_subtitle), Icons.Filled.Business, onCompaniesClick),
        MenuAction(stringResource(R.string.menu_sites), stringResource(R.string.menu_sites_subtitle), Icons.Filled.Place, onSitesClick),
        MenuAction(stringResource(R.string.menu_job_types), stringResource(R.string.menu_job_types_subtitle), Icons.Filled.Work, onJobTypesClick)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Profile lives up here as a small avatar (photo when set), next to the settings gear.
            IconButton(onClick = onProfileClick) {
                if (profilePhoto != null) {
                    Image(
                        bitmap = profilePhoto,
                        contentDescription = stringResource(R.string.menu_profile),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(30.dp).clip(CircleShape)
                    )
                } else {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = stringResource(R.string.menu_profile),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onPlanningClick) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = stringResource(R.string.menu_planning),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        CalendarPanel(
            state = calendar.calendar,
            onSelectDate = calendarViewModel::selectDate,
            onToggleExpanded = calendarViewModel::toggleExpanded,
            onShift = calendarViewModel::shift,
            onToday = calendarViewModel::goToToday
        )
        Spacer(Modifier.height(16.dp))
        CalendarDaySessions(state = calendar)

        Spacer(Modifier.height(24.dp))

        ElevatedCard(
            onClick = onTrackerClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.start_tracking),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        stringResource(R.string.start_tracking_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.manage),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        secondaryActions.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { action ->
                    MenuCard(action = action, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MenuCard(action: MenuAction, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = action.onClick, modifier = modifier.height(124.dp), shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Calendar panel (week strip that expands to the month) + home day sessions
// ---------------------------------------------------------------------------

/** Month title + navigation, weekday labels and either the Mon–Sun strip or the full month grid. */
@Composable
private fun CalendarPanel(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onToggleExpanded: () -> Unit,
    onShift: (forward: Boolean) -> Unit,
    onToday: () -> Unit
) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val today = remember { LocalDate.now() }
    val title = remember(state.titleMonth, locale) {
        val month = state.titleMonth.month.getDisplayName(JavaTextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        if (state.titleMonth.year == today.year) month else "$month ${state.titleMonth.year}"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The whole title is the expand/collapse toggle, like a dropdown.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onToggleExpanded)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (state.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(if (state.expanded) R.string.calendar_collapse else R.string.calendar_expand)
                    )
                }
                IconButton(onClick = { onShift(false) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.calendar_previous))
                }
                IconButton(onClick = { onShift(true) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.calendar_next))
                }
                IconButton(onClick = onToday) {
                    Icon(Icons.Filled.Today, contentDescription = stringResource(R.string.calendar_today))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        day.getDisplayName(JavaTextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            val weeks: List<LocalDate> = if (state.expanded) {
                // Every Monday from the one on/before the 1st up to the one covering the last day of the month.
                val first = state.visibleMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val last = state.visibleMonth.atEndOfMonth()
                generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= last }.toList()
            } else {
                listOf(state.weekStart)
            }

            weeks.forEach { monday ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    (0L until 7L).forEach { offset ->
                        val date = monday.plusDays(offset)
                        CalendarDayCell(
                            date = date,
                            selected = date == state.selectedDate,
                            isToday = date == today,
                            dimmed = state.expanded && YearMonth.from(date) != state.visibleMonth,
                            hasSessions = date in state.markedDays,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    dimmed: Boolean,
    hasSessions: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(onClick = onClick)
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
        // Marker for days that have sessions; kept as an empty slot otherwise so rows stay the same height.
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    if (hasSessions) (if (dimmed) textColor else MaterialTheme.colorScheme.primary) else Color.Transparent
                )
        )
    }
}

/** The selected day's sessions under the calendar, read-only; editing stays on the tracker screen. */
@Composable
private fun CalendarDaySessions(state: HomeCalendarUiState) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH) }
    val totalMillis = state.dayTotalMillis
    val totalText = stringResource(
        R.string.duration_format,
        TimeUnit.MILLISECONDS.toHours(totalMillis),
        TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
    )

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.calendar_sessions_on, state.calendar.selectedDate.format(dateFormatter)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (state.daySessions.isNotEmpty()) {
                Text(
                    stringResource(R.string.calendar_day_total, totalText),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.daySessions.isEmpty()) {
            Text(
                stringResource(R.string.calendar_no_sessions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.daySessions.forEachIndexed { index, session ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                CalendarSessionRow(session)
            }
        }
    }
}

@Composable
private fun CalendarSessionRow(session: TrackingSession) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH) }
    val zone = remember { ZoneId.systemDefault() }
    val start = Instant.ofEpochMilli(session.startTimestampMillis).atZone(zone).toLocalTime()
    val end = session.stopTimestampMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
    val durationMillis = session.durationMillis ?: 0L
    val details = listOfNotNull(
        session.companyName?.takeIf { it.isNotBlank() },
        session.jobTypeLabel?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.siteLabel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.site),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${start.format(timeFormatter)} – ${end?.format(timeFormatter) ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    stringResource(
                        R.string.duration_format,
                        TimeUnit.MILLISECONDS.toHours(durationMillis),
                        TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Planning (jobs scheduled per calendar day)
// ---------------------------------------------------------------------------

@Composable
fun PlanningScreen(onBack: () -> Unit, viewModel: PlanningViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val companies by viewModel.companies.collectAsState()
    val sites by viewModel.sites.collectAsState()
    val jobTypes by viewModel.jobTypes.collectAsState()
    val locale = LocalContext.current.resources.configuration.locales[0]
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMM", locale) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingJob by remember { mutableStateOf<PlannedJob?>(null) }
    var deletingJob by remember { mutableStateOf<PlannedJob?>(null) }

    if (showAddDialog) {
        EditPlannedJobDialog(
            existing = null,
            initialDate = uiState.calendar.selectedDate,
            companies = companies,
            sites = sites,
            jobTypes = jobTypes,
            onDismiss = { showAddDialog = false },
            onConfirm = { job ->
                viewModel.saveJob(job)
                viewModel.selectDate(job.date)
                showAddDialog = false
            }
        )
    }
    editingJob?.let { job ->
        EditPlannedJobDialog(
            existing = job,
            initialDate = job.date,
            companies = companies,
            sites = sites,
            jobTypes = jobTypes,
            onDismiss = { editingJob = null },
            onConfirm = { updated ->
                viewModel.saveJob(updated)
                editingJob = null
            }
        )
    }
    deletingJob?.let { job ->
        DeletePlannedJobDialog(
            job = job,
            onDismiss = { deletingJob = null },
            onConfirm = {
                viewModel.deleteJob(job)
                deletingJob = null
            }
        )
    }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.menu_planning), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            CalendarPanel(
                state = uiState.calendar,
                onSelectDate = viewModel::selectDate,
                onToggleExpanded = viewModel::toggleExpanded,
                onShift = viewModel::shift,
                onToday = viewModel::goToToday
            )

            Spacer(Modifier.height(20.dp))
            Text(
                uiState.calendar.selectedDate.format(dayFormatter).trimEnd('.').uppercase(locale),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(title = stringResource(R.string.planning_jobs), count = uiState.dayJobs.size)
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.planning_add_job))
                }
            }
            Spacer(Modifier.height(8.dp))

            if (uiState.dayJobs.isEmpty()) {
                EmptyState(text = stringResource(R.string.planning_empty))
            } else {
                uiState.dayJobs.forEachIndexed { index, job ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    PlannedJobRow(
                        job = job,
                        onEditClick = { editingJob = job },
                        onDeleteClick = { deletingJob = job }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlannedJobRow(job: PlannedJob, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH) }
    val timeText = job.endTime?.let { "${job.startTime.format(timeFormatter)} – ${it.format(timeFormatter)}" }
        ?: job.startTime.format(timeFormatter)
    val details = listOfNotNull(
        job.companyName?.takeIf { it.isNotBlank() },
        job.jobTypeLabel?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    timeText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val title = job.siteLabel?.takeIf { it.isNotBlank() }
                    ?: details.ifBlank { null }
                    ?: job.notes.orEmpty()
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (details.isNotBlank() && title != details) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!job.notes.isNullOrBlank() && title != job.notes) {
                    Text(job.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Add (when [existing] is null) or edit a planned job. Company/site/job type are optional here, unlike a tracked session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPlannedJobDialog(
    existing: PlannedJob?,
    initialDate: LocalDate,
    companies: List<Company>,
    sites: List<Site>,
    jobTypes: List<JobType>,
    onDismiss: () -> Unit,
    onConfirm: (PlannedJob) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    var companyName by remember { mutableStateOf(existing?.companyName) }
    var siteLabel by remember { mutableStateOf(existing?.siteLabel) }
    var jobTypeLabel by remember { mutableStateOf(existing?.jobTypeLabel) }
    var date by remember { mutableStateOf(initialDate) }
    var startTime by remember { mutableStateOf(existing?.startTime ?: LocalTime.of(7, 0)) }
    var endTime by remember { mutableStateOf(existing?.endTime) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var companyExpanded by remember { mutableStateOf(false) }
    var siteExpanded by remember { mutableStateOf(false) }
    var jobTypeExpanded by remember { mutableStateOf(false) }

    val canSave = companyName != null || siteLabel != null || jobTypeLabel != null || notes.isNotBlank()
    val none = stringResource(R.string.planning_none)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EventNote, contentDescription = null) },
        title = { Text(stringResource(if (existing == null) R.string.planning_add_job else R.string.planning_edit_job)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(
                    onClick = { showDatePicker(context, date) { date = it } },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${stringResource(R.string.date)}: ${date.format(dateFormatter)}")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showTimePicker(context, startTime) { startTime = it } },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(startTime.format(timeFormatter))
                    }
                    OutlinedButton(
                        onClick = { showTimePicker(context, endTime ?: startTime.plusHours(1)) { endTime = it } },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(endTime?.format(timeFormatter) ?: "–")
                    }
                    if (endTime != null) {
                        IconButton(onClick = { endTime = null }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.planning_clear_end_time), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.start_time),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.planning_end_time_optional),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))
                TrackerDropdown(
                    label = stringResource(R.string.company),
                    icon = Icons.Filled.Business,
                    value = companyName ?: stringResource(R.string.select_company),
                    expanded = companyExpanded,
                    enabled = true,
                    onExpandedChange = { companyExpanded = it },
                    onDismiss = { companyExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text(none) }, onClick = { companyName = null; companyExpanded = false })
                    companies.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name) }, onClick = { companyName = option.name; companyExpanded = false })
                    }
                }
                Spacer(Modifier.height(10.dp))
                TrackerDropdown(
                    label = stringResource(R.string.site),
                    icon = Icons.Filled.Place,
                    value = siteLabel ?: stringResource(R.string.select_site),
                    expanded = siteExpanded,
                    enabled = true,
                    onExpandedChange = { siteExpanded = it },
                    onDismiss = { siteExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text(none) }, onClick = { siteLabel = null; siteExpanded = false })
                    sites.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { siteLabel = option.label; siteExpanded = false })
                    }
                }
                Spacer(Modifier.height(10.dp))
                TrackerDropdown(
                    label = stringResource(R.string.job_description),
                    icon = Icons.Filled.Work,
                    value = jobTypeLabel ?: stringResource(R.string.select_job_type),
                    expanded = jobTypeExpanded,
                    enabled = true,
                    onExpandedChange = { jobTypeExpanded = it },
                    onDismiss = { jobTypeExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text(none) }, onClick = { jobTypeLabel = null; jobTypeExpanded = false })
                    jobTypes.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name) }, onClick = { jobTypeLabel = option.name; jobTypeExpanded = false })
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.planning_notes)) },
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!canSave) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.planning_details_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        PlannedJob(
                            id = existing?.id ?: 0L,
                            dateEpochDay = date.toEpochDay(),
                            startMinute = startTime.toSecondOfDay() / 60,
                            endMinute = endTime?.let { it.toSecondOfDay() / 60 },
                            companyName = companyName,
                            siteLabel = siteLabel,
                            jobTypeLabel = jobTypeLabel,
                            notes = notes.trim().ifBlank { null }
                        )
                    )
                },
                enabled = canSave
            ) {
                Text(stringResource(if (existing == null) R.string.add else R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeletePlannedJobDialog(job: PlannedJob, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete_entry)) },
        text = {
            val label = job.siteLabel?.takeIf { it.isNotBlank() }
            Text(
                if (label != null) stringResource(R.string.delete_entry_confirm_named, label)
                else stringResource(R.string.delete_entry_confirm)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current

    // The locale is applied in MainActivity.attachBaseContext, so the Activity must be rebuilt.
    val selectLanguage: (AppLanguage) -> Unit = { picked ->
        if (picked != language) {
            viewModel.setLanguage(picked)
            context.findActivity()?.recreate()
        }
    }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.settings), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.appearance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    SettingsOptionRow(
                        icon = Icons.Filled.BrightnessAuto,
                        title = stringResource(R.string.theme_system),
                        subtitle = stringResource(R.string.theme_system_subtitle),
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                    )
                    SettingsOptionRow(
                        icon = Icons.Filled.LightMode,
                        title = stringResource(R.string.theme_light),
                        subtitle = stringResource(R.string.theme_light_subtitle),
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                    )
                    SettingsOptionRow(
                        icon = Icons.Filled.DarkMode,
                        title = stringResource(R.string.theme_dark),
                        subtitle = stringResource(R.string.theme_dark_subtitle),
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    SettingsOptionRow(
                        icon = Icons.Filled.Language,
                        title = stringResource(R.string.language_system),
                        subtitle = stringResource(R.string.language_system_subtitle),
                        selected = language == AppLanguage.SYSTEM,
                        onClick = { selectLanguage(AppLanguage.SYSTEM) }
                    )
                    SettingsOptionRow(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.language_english),
                        subtitle = null,
                        selected = language == AppLanguage.ENGLISH,
                        onClick = { selectLanguage(AppLanguage.ENGLISH) }
                    )
                    SettingsOptionRow(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.language_portuguese),
                        subtitle = null,
                        selected = language == AppLanguage.PORTUGUESE,
                        onClick = { selectLanguage(AppLanguage.PORTUGUESE) }
                    )
                    SettingsOptionRow(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.language_spanish),
                        subtitle = null,
                        selected = language == AppLanguage.SPANISH,
                        onClick = { selectLanguage(AppLanguage.SPANISH) }
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SettingsOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

// ---------------------------------------------------------------------------
// Reports
// ---------------------------------------------------------------------------

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: LocationTrackerViewModel = viewModel(),
    invoiceViewModel: InvoiceViewModel = viewModel(),
    reportViewModel: ReportViewModel = viewModel()
) {
    val sessions by viewModel.completedSessions.collectAsState()
    val sites by viewModel.sites.collectAsState()
    val columnSelection by reportViewModel.columns.collectAsState()
    val columns = remember(columnSelection) { ReportColumn.ordered(columnSelection) }
    val sitesByLabel = remember(sites) { sites.associateBy { it.label } }
    val invoiceState by invoiceViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showInvoiceDialog by remember { mutableStateOf(false) }
    var showColumnsDialog by remember { mutableStateOf(false) }

    // Once a PDF is ready, hand it to the system share sheet (open, email, save...).
    LaunchedEffect(invoiceState.generatedPdfUri) {
        val uri = invoiceState.generatedPdfUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_invoice)))
        invoiceViewModel.consumeGeneratedPdf()
        showInvoiceDialog = false
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }

    val filteredSessions = remember(sessions, startDate, endDate) {
        val rangeStartMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rangeEndMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        sessions
            .filter { it.startTimestampMillis in rangeStartMillis until rangeEndMillis }
            .sortedBy { it.startTimestampMillis }
    }
    val totalDurationMillis = filteredSessions.sumOf { it.durationMillis ?: 0L }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.menu_reports), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        showDatePicker(context, startDate) { picked ->
                            startDate = picked
                            if (endDate.isBefore(picked)) endDate = picked
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(startDate.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                }
                Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        showDatePicker(context, endDate) { picked ->
                            endDate = picked
                            if (startDate.isAfter(picked)) startDate = picked
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(endDate.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showColumnsDialog = true },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ViewColumn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${stringResource(R.string.columns)}: " + columns.map { stringResource(it.labelRes) }.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))

            if (filteredSessions.isEmpty()) {
                EmptyState(text = stringResource(R.string.reports_empty))
            } else {
                ReportTable(
                    sessions = filteredSessions,
                    sitesByLabel = sitesByLabel,
                    columns = columns,
                    totalDurationMillis = totalDurationMillis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        invoiceViewModel.clearError()
                        showInvoiceDialog = true
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.generate_invoice))
                }
            }
        }
    }

    if (showColumnsDialog) {
        ReportColumnsDialog(
            selection = columnSelection,
            onToggle = { column, enabled -> reportViewModel.setColumnEnabled(column, enabled) },
            onDismiss = { showColumnsDialog = false }
        )
    }

    if (showInvoiceDialog) {
        val companies by invoiceViewModel.companies.collectAsState()
        GenerateInvoiceDialog(
            sessions = filteredSessions,
            companies = companies,
            columnsLabel = columns.map { stringResource(it.labelRes) }.joinToString(", "),
            suggestedNumber = invoiceViewModel.nextInvoiceNumber(),
            isGenerating = invoiceState.isGenerating,
            error = invoiceState.error,
            onDismiss = { if (!invoiceState.isGenerating) showInvoiceDialog = false },
            onGenerate = { company, number, rate ->
                invoiceViewModel.generateInvoice(
                    sessions = filteredSessions,
                    company = company,
                    invoiceNumber = number,
                    hourlyRate = rate,
                    periodStart = startDate,
                    periodEnd = endDate,
                    columns = columnSelection
                )
            }
        )
    }
}

/** Lets the user turn template columns on/off; order is fixed by [ReportColumn] so every report looks the same. */
@Composable
fun ReportColumnsDialog(
    selection: Set<ReportColumn>,
    onToggle: (ReportColumn, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.ViewColumn, contentDescription = null) },
        title = { Text(stringResource(R.string.report_columns)) },
        text = {
            Column {
                ReportColumn.entries.forEach { column ->
                    val checked = column.alwaysShown || column in selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !column.alwaysShown) { onToggle(column, !checked) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggle(column, it) },
                            enabled = !column.alwaysShown
                        )
                        Text(stringResource(column.labelRes), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.report_columns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateInvoiceDialog(
    sessions: List<TrackingSession>,
    companies: List<Company>,
    columnsLabel: String,
    suggestedNumber: String,
    isGenerating: Boolean,
    error: UiMessage?,
    onDismiss: () -> Unit,
    onGenerate: (company: Company, invoiceNumber: String, hourlyRate: Double?) -> Unit
) {
    // Preselect the company when every session in the report belongs to the same one.
    val companyNamesInReport = remember(sessions) { sessions.mapNotNull { it.companyName }.distinct() }
    var selectedCompany by remember(companies, companyNamesInReport) {
        mutableStateOf(companies.firstOrNull { it.name == companyNamesInReport.singleOrNull() })
    }
    var invoiceNumber by remember { mutableStateOf(suggestedNumber) }
    var rateText by remember { mutableStateOf("") }
    var companyDropdownExpanded by remember { mutableStateOf(false) }

    val parsedRate = rateText.trim().replace(',', '.').toDoubleOrNull()
    val rateInvalid = rateText.isNotBlank() && (parsedRate == null || parsedRate < 0)
    val daysForCompany = remember(sessions, selectedCompany) {
        selectedCompany?.let { company -> buildInvoiceLines(sessions.filter { it.companyName == company.name }).size } ?: 0
    }
    val canGenerate = selectedCompany != null && invoiceNumber.isNotBlank() && !rateInvalid &&
        daysForCompany > 0 && !isGenerating

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generate_invoice)) },
        text = {
            Column {
                if (companies.isEmpty()) {
                    Text(stringResource(R.string.invoice_register_company_first), color = MaterialTheme.colorScheme.error)
                } else {
                    TrackerDropdown(
                        label = stringResource(R.string.bill_to),
                        icon = Icons.Filled.Business,
                        value = selectedCompany?.name ?: stringResource(R.string.select_company),
                        expanded = companyDropdownExpanded,
                        enabled = !isGenerating,
                        onExpandedChange = { companyDropdownExpanded = it },
                        onDismiss = { companyDropdownExpanded = false }
                    ) {
                        companies.forEach { company ->
                            DropdownMenuItem(
                                text = { Text(company.name) },
                                onClick = {
                                    selectedCompany = company
                                    companyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text(stringResource(R.string.invoice_number)) },
                    leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                    enabled = !isGenerating,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text(stringResource(R.string.hourly_rate_optional)) },
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                    supportingText = { Text(if (rateInvalid) stringResource(R.string.enter_valid_amount) else stringResource(R.string.leave_blank_hours_only)) },
                    isError = rateInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !isGenerating,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.invoice_columns_info, columnsLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (selectedCompany != null) {
                    Text(
                        text = if (daysForCompany == 0) {
                            stringResource(R.string.invoice_no_days_for_company, selectedCompany?.name.orEmpty())
                        } else {
                            stringResource(R.string.invoice_days_to_invoice, daysForCompany)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (daysForCompany == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(error.resId, *error.args.toTypedArray()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedCompany?.let { onGenerate(it, invoiceNumber.trim(), parsedRate) } },
                enabled = canGenerate,
                shape = MaterialTheme.shapes.medium
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.generate_pdf))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isGenerating) { Text(stringResource(R.string.cancel)) } }
    )
}

/** On-screen widths for the shared column template; text columns stretch to fill spare width. */
private fun reportColumnMinWidth(column: ReportColumn): Dp = when (column) {
    ReportColumn.DATE -> 92.dp
    ReportColumn.SITE -> 140.dp
    ReportColumn.ADDRESS -> 220.dp
    ReportColumn.COMPANY -> 130.dp
    ReportColumn.JOB_TYPE -> 130.dp
    ReportColumn.START_TIME -> 64.dp
    ReportColumn.END_TIME -> 64.dp
    ReportColumn.HOURS -> 76.dp
}

private fun ReportColumn.isTextColumn() = this in setOf(
    ReportColumn.SITE, ReportColumn.ADDRESS, ReportColumn.COMPANY, ReportColumn.JOB_TYPE
)

private fun ReportColumn.isRightAligned() = this in setOf(
    ReportColumn.START_TIME, ReportColumn.END_TIME, ReportColumn.HOURS
)

@Composable
fun ReportTable(
    sessions: List<TrackingSession>,
    sitesByLabel: Map<String, Site>,
    columns: List<ReportColumn>,
    totalDurationMillis: Long,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.ENGLISH) }
    val horizontalPadding = 16.dp

    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Fit the template into the card; when it is wider than the screen the table scrolls sideways.
            val minTableWidth = columns.fold(0.dp) { acc, c -> acc + reportColumnMinWidth(c) } + horizontalPadding * 2
            val spare = (maxWidth - minTableWidth).coerceAtLeast(0.dp)
            val stretchable = columns.filter { it.isTextColumn() }.ifEmpty { listOf(columns.first()) }
            val widths = columns.associateWith { column ->
                reportColumnMinWidth(column) + if (column in stretchable) spare / stretchable.size else 0.dp
            }
            val tableWidth = maxOf(maxWidth, minTableWidth)

            Column(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .width(tableWidth)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = horizontalPadding, vertical = 12.dp)
                ) {
                    columns.forEach { column ->
                        Text(
                            stringResource(column.labelRes),
                            modifier = Modifier.width(widths.getValue(column)),
                            textAlign = if (column.isRightAligned()) TextAlign.End else TextAlign.Start,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(sessions) { index, session ->
                        val rowColor = if (index % 2 == 0) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowColor)
                                .padding(horizontal = horizontalPadding, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            columns.forEach { column ->
                                val value = if (column == ReportColumn.HOURS) {
                                    val durationMillis = session.durationMillis ?: 0L
                                    stringResource(
                                        R.string.duration_format,
                                        TimeUnit.MILLISECONDS.toHours(durationMillis),
                                        TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
                                    )
                                } else {
                                    reportCellValue(session, column, sitesByLabel, dateFormatter, timeFormatter)
                                }
                                Text(
                                    value,
                                    modifier = Modifier.width(widths.getValue(column)).padding(end = 6.dp),
                                    textAlign = if (column.isRightAligned()) TextAlign.End else TextAlign.Start,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (column == ReportColumn.DATE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val totalHours = TimeUnit.MILLISECONDS.toHours(totalDurationMillis)
                val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDurationMillis) % 60
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = horizontalPadding, vertical = 14.dp)
                ) {
                    Text(
                        stringResource(R.string.total),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.duration_format, totalHours, totalMinutes),
                        modifier = Modifier.width(widths.getValue(ReportColumn.HOURS)).padding(end = 6.dp),
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/** Cell text for every column except HOURS (which needs a string resource and is built in the composable). */
private fun reportCellValue(
    session: TrackingSession,
    column: ReportColumn,
    sitesByLabel: Map<String, Site>,
    dateFormatter: SimpleDateFormat,
    timeFormatter: SimpleDateFormat
): String = when (column) {
    ReportColumn.DATE -> dateFormatter.format(Date(session.startTimestampMillis))
    ReportColumn.SITE -> session.siteLabel?.takeIf { it.isNotBlank() }
        ?: "${"%.6f".format(session.startLatitude)}, ${"%.6f".format(session.startLongitude)}"
    ReportColumn.ADDRESS -> sitesByLabel[session.siteLabel]?.address.orEmpty()
    ReportColumn.COMPANY -> session.companyName.orEmpty()
    ReportColumn.JOB_TYPE -> session.jobTypeLabel.orEmpty()
    ReportColumn.START_TIME -> timeFormatter.format(Date(session.startTimestampMillis))
    ReportColumn.END_TIME -> session.stopTimestampMillis?.let { timeFormatter.format(Date(it)) }.orEmpty()
    ReportColumn.HOURS -> ""
}

// ---------------------------------------------------------------------------
// Companies
// ---------------------------------------------------------------------------

@Composable
fun CompaniesScreen(onBack: () -> Unit, viewModel: CompanyViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val companies by viewModel.companies.collectAsState()
    val editState by viewModel.editState.collectAsState()

    editState?.let { state ->
        EditCompanyDialog(
            state = state,
            onNameChanged = { viewModel.onEditNameChanged(it) },
            onAbnChanged = { viewModel.onEditAbnChanged(it) },
            onPhoneChanged = { viewModel.onEditPhoneChanged(it) },
            onEmailChanged = { viewModel.onEditEmailChanged(it) },
            onDismiss = { viewModel.cancelEditingCompany() },
            onConfirm = { viewModel.saveEditedCompany() }
        )
    }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.menu_companies), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.new_company), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.onNameChanged(it) },
                        label = { Text(stringResource(R.string.company_name)) },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.abn,
                        onValueChange = { viewModel.onAbnChanged(it) },
                        label = { Text(stringResource(R.string.company_abn)) },
                        isError = !Validators.abnOk(uiState.abn),
                        supportingText = if (!Validators.abnOk(uiState.abn)) {
                            { Text(stringResource(R.string.invalid_abn)) }
                        } else null,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.phone,
                        onValueChange = { viewModel.onPhoneChanged(it) },
                        label = { Text(stringResource(R.string.contact_phone)) },
                        isError = !Validators.phoneOk(uiState.phone),
                        supportingText = if (!Validators.phoneOk(uiState.phone)) {
                            { Text(stringResource(R.string.invalid_phone)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.onEmailChanged(it) },
                        label = { Text(stringResource(R.string.billing_email)) },
                        isError = !Validators.emailOk(uiState.email),
                        supportingText = if (!Validators.emailOk(uiState.email)) {
                            { Text(stringResource(R.string.invalid_email)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.saveCompany() },
                        enabled = uiState.name.isNotBlank() && Validators.abnOk(uiState.abn) &&
                            Validators.phoneOk(uiState.phone) && Validators.emailOk(uiState.email),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save_company))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(title = stringResource(R.string.registered_companies), count = companies.size)
            Spacer(Modifier.height(8.dp))

            if (companies.isEmpty()) {
                EmptyState(text = stringResource(R.string.companies_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(companies, key = { it.id }) { company ->
                        EntityListItem(
                            icon = Icons.Filled.Business,
                            title = company.name,
                            subtitle = listOfNotNull(
                                company.abn?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.abn_value, it) },
                                company.phone?.takeIf { it.isNotBlank() },
                                company.email?.takeIf { it.isNotBlank() }
                            ).joinToString("  •  ").ifBlank { null },
                            onEdit = { viewModel.startEditingCompany(company) },
                            onDelete = { viewModel.deleteCompany(company) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditCompanyDialog(
    state: CompanyEditUiState,
    onNameChanged: (String) -> Unit,
    onAbnChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Business, contentDescription = null) },
        title = { Text(stringResource(R.string.edit_company)) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(R.string.company_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.abn,
                    onValueChange = onAbnChanged,
                    label = { Text(stringResource(R.string.company_abn)) },
                    isError = !Validators.abnOk(state.abn),
                    supportingText = if (!Validators.abnOk(state.abn)) {
                        { Text(stringResource(R.string.invalid_abn)) }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = onPhoneChanged,
                    label = { Text(stringResource(R.string.contact_phone)) },
                    isError = !Validators.phoneOk(state.phone),
                    supportingText = if (!Validators.phoneOk(state.phone)) {
                        { Text(stringResource(R.string.invalid_phone)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChanged,
                    label = { Text(stringResource(R.string.billing_email)) },
                    isError = !Validators.emailOk(state.email),
                    supportingText = if (!Validators.emailOk(state.email)) {
                        { Text(stringResource(R.string.invalid_email)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.name.isNotBlank() && Validators.abnOk(state.abn) &&
                    Validators.phoneOk(state.phone) && Validators.emailOk(state.email)
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Job types
// ---------------------------------------------------------------------------

@Composable
fun JobTypesScreen(onBack: () -> Unit, viewModel: JobTypeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val jobTypes by viewModel.jobTypes.collectAsState()
    val editState by viewModel.editState.collectAsState()

    editState?.let { state ->
        EditJobTypeDialog(
            state = state,
            onNameChanged = { viewModel.onEditNameChanged(it) },
            onDismiss = { viewModel.cancelEditingJobType() },
            onConfirm = { viewModel.saveEditedJobType() }
        )
    }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.menu_job_types), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.new_job_type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.onNameChanged(it) },
                        label = { Text(stringResource(R.string.job_type_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Work, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.saveJobType() },
                        enabled = uiState.name.isNotBlank(),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save_job_type))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(title = stringResource(R.string.registered_job_types), count = jobTypes.size)
            Spacer(Modifier.height(8.dp))

            if (jobTypes.isEmpty()) {
                EmptyState(text = stringResource(R.string.job_types_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(jobTypes, key = { it.id }) { jobType ->
                        EntityListItem(
                            icon = Icons.Filled.Work,
                            title = jobType.name,
                            subtitle = null,
                            onEdit = { viewModel.startEditingJobType(jobType) },
                            onDelete = { viewModel.deleteJobType(jobType) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditJobTypeDialog(
    state: JobTypeEditUiState,
    onNameChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Work, contentDescription = null) },
        title = { Text(stringResource(R.string.edit_job_type)) },
        text = {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.job_type_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Sites
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(onBack: () -> Unit, viewModel: SiteViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val sites by viewModel.sites.collectAsState()
    val editState by viewModel.editState.collectAsState()
    val editSuggestions by viewModel.editSuggestions.collectAsState()
    var addressDropdownExpanded by remember { mutableStateOf(false) }

    editState?.let { state ->
        EditSiteDialog(
            state = state,
            suggestions = editSuggestions,
            onLabelChanged = { viewModel.onEditLabelChanged(it) },
            onAddressQueryChanged = { viewModel.onEditAddressQueryChanged(it) },
            onSelectSuggestion = { viewModel.selectEditAddress(it) },
            onDismiss = { viewModel.cancelEditingSite() },
            onConfirm = { viewModel.saveEditedSite() }
        )
    }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.menu_sites), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.new_site), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.label,
                        onValueChange = { viewModel.onLabelChanged(it) },
                        label = { Text(stringResource(R.string.site_label)) },
                        leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = addressDropdownExpanded && suggestions.isNotEmpty(),
                        onExpandedChange = { addressDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = uiState.addressQuery,
                            onValueChange = {
                                viewModel.onAddressQueryChanged(it)
                                addressDropdownExpanded = true
                            },
                            label = { Text(stringResource(R.string.address)) },
                            placeholder = { Text(stringResource(R.string.address_placeholder)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = addressDropdownExpanded && suggestions.isNotEmpty(),
                            onDismissRequest = { addressDropdownExpanded = false }
                        ) {
                            suggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion.label) },
                                    onClick = {
                                        viewModel.selectAddress(suggestion)
                                        addressDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (uiState.latitude == null && uiState.addressQuery.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.site_select_address_set),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.saveSite() },
                        enabled = uiState.label.isNotBlank() && uiState.latitude != null,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save_site))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(title = stringResource(R.string.registered_sites), count = sites.size)
            Spacer(Modifier.height(8.dp))

            if (sites.isEmpty()) {
                EmptyState(text = stringResource(R.string.sites_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(sites, key = { it.id }) { site ->
                        EntityListItem(
                            icon = Icons.Filled.Place,
                            title = site.label,
                            subtitle = site.address,
                            onEdit = { viewModel.startEditingSite(site) },
                            onDelete = { viewModel.deleteSite(site) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSiteDialog(
    state: SiteEditUiState,
    suggestions: List<AddressSuggestion>,
    onLabelChanged: (String) -> Unit,
    onAddressQueryChanged: (String) -> Unit,
    onSelectSuggestion: (AddressSuggestion) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Place, contentDescription = null) },
        title = { Text(stringResource(R.string.edit_site)) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.label,
                    onValueChange = onLabelChanged,
                    label = { Text(stringResource(R.string.site_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded && suggestions.isNotEmpty(),
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.addressQuery,
                        onValueChange = {
                            onAddressQueryChanged(it)
                            dropdownExpanded = true
                        },
                        label = { Text(stringResource(R.string.address)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded && suggestions.isNotEmpty(),
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion.label) },
                                onClick = {
                                    onSelectSuggestion(suggestion)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (state.latitude == null && state.addressQuery.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.site_select_address_update),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.label.isNotBlank() && state.latitude != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

@Composable
fun rememberBitmapFromFile(path: String?): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = if (path != null) {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            }
        } else {
            null
        }
    }
    return bitmap
}

@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val photoBitmap = rememberBitmapFromFile(uiState.photoPath)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { viewModel.onPhotoPicked(it) } }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.menu_profile), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = stringResource(R.string.profile_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(120.dp).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.change_photo),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.personal_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.onNameChanged(it) },
                        label = { Text(stringResource(R.string.full_name)) },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.phone,
                        onValueChange = { viewModel.onPhoneChanged(it) },
                        label = { Text(stringResource(R.string.phone)) },
                        isError = !Validators.phoneOk(uiState.phone),
                        supportingText = if (!Validators.phoneOk(uiState.phone)) {
                            { Text(stringResource(R.string.invalid_phone)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.onEmailChanged(it) },
                        label = { Text(stringResource(R.string.email)) },
                        isError = !Validators.emailOk(uiState.email),
                        supportingText = if (!Validators.emailOk(uiState.email)) {
                            { Text(stringResource(R.string.invalid_email)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.role,
                        onValueChange = { viewModel.onRoleChanged(it) },
                        label = { Text(stringResource(R.string.role_position)) },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.abn,
                        onValueChange = { viewModel.onAbnChanged(it) },
                        label = { Text(stringResource(R.string.profile_abn)) },
                        isError = !Validators.abnOk(uiState.abn),
                        supportingText = if (!Validators.abnOk(uiState.abn)) {
                            { Text(stringResource(R.string.invalid_abn)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.banking_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.bankBsb,
                        onValueChange = { viewModel.onBankBsbChanged(it) },
                        label = { Text(stringResource(R.string.bsb)) },
                        isError = !Validators.bsbOk(uiState.bankBsb),
                        supportingText = if (!Validators.bsbOk(uiState.bankBsb)) {
                            { Text(stringResource(R.string.invalid_bsb)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.bankAccount,
                        onValueChange = { viewModel.onBankAccountChanged(it) },
                        label = { Text(stringResource(R.string.account_number)) },
                        isError = !Validators.accountNumberOk(uiState.bankAccount),
                        supportingText = if (!Validators.accountNumberOk(uiState.bankAccount)) {
                            { Text(stringResource(R.string.invalid_account_number)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { viewModel.saveProfile() },
                enabled = uiState.name.isNotBlank() && uiState.isValid,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save))
            }

            if (uiState.isSaved) {
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.profile_saved), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Tracker (Start tracking screen)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(onBack: () -> Unit, viewModel: LocationTrackerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val sessions by viewModel.completedSessions.collectAsState()
    val companies by viewModel.companies.collectAsState()
    val sites by viewModel.sites.collectAsState()
    val jobTypes by viewModel.jobTypes.collectAsState()
    var editingSession by remember { mutableStateOf<TrackingSession?>(null) }
    var deletingSession by remember { mutableStateOf<TrackingSession?>(null) }
    var showAddSessionDialog by remember { mutableStateOf(false) }
    var companyDropdownExpanded by remember { mutableStateOf(false) }
    var siteDropdownExpanded by remember { mutableStateOf(false) }
    var jobTypeDropdownExpanded by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = fineGranted
        if (!fineGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    uiState.lastCompletedSession?.let { session ->
        SessionSummaryDialog(session = session, onDismiss = { viewModel.dismissSessionSummary() })
    }

    editingSession?.let { session ->
        EditDurationDialog(
            session = session,
            onDismiss = { editingSession = null },
            onConfirm = { newDurationMillis ->
                viewModel.updateSessionDuration(session, newDurationMillis)
                editingSession = null
            }
        )
    }

    deletingSession?.let { session ->
        DeleteSessionDialog(
            session = session,
            onDismiss = { deletingSession = null },
            onConfirm = {
                viewModel.deleteSession(session)
                deletingSession = null
            }
        )
    }

    if (showAddSessionDialog) {
        AddSessionDialog(
            companies = companies,
            sites = sites,
            jobTypes = jobTypes,
            initialCompany = uiState.selectedCompany,
            initialSite = uiState.selectedSite,
            initialJobType = uiState.selectedJobType,
            onDismiss = { showAddSessionDialog = false },
            onConfirm = { company, site, jobType, startMillis, stopMillis ->
                viewModel.addManualSession(company, site, jobType, startMillis, stopMillis)
                showAddSessionDialog = false
            }
        )
    }

    Scaffold(topBar = { AppTopBar(title = stringResource(R.string.app_name), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (!hasLocationPermission) {
                InfoBanner(
                    icon = Icons.Filled.LocationOff,
                    text = stringResource(R.string.location_permission_required),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(12.dp))
            }

            val missing = listOfNotNull(
                stringResource(R.string.missing_companies).takeIf { companies.isEmpty() },
                stringResource(R.string.missing_sites).takeIf { sites.isEmpty() },
                stringResource(R.string.missing_job_types).takeIf { jobTypes.isEmpty() }
            )
            if (missing.isNotEmpty()) {
                InfoBanner(
                    icon = Icons.Filled.Info,
                    text = stringResource(R.string.setup_missing_banner, missing.joinToString(", ")),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(12.dp))
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.session_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))

                    if (companies.isNotEmpty()) {
                        TrackerDropdown(
                            label = stringResource(R.string.company),
                            icon = Icons.Filled.Business,
                            value = uiState.selectedCompany?.name ?: stringResource(R.string.select_company),
                            expanded = companyDropdownExpanded,
                            enabled = !uiState.isSessionActive,
                            onExpandedChange = { companyDropdownExpanded = it && !uiState.isSessionActive },
                            onDismiss = { companyDropdownExpanded = false }
                        ) {
                            companies.forEach { company ->
                                DropdownMenuItem(
                                    text = { Text(company.name) },
                                    onClick = {
                                        viewModel.selectCompany(company)
                                        companyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    if (sites.isNotEmpty()) {
                        TrackerDropdown(
                            label = stringResource(R.string.site),
                            icon = Icons.Filled.Place,
                            value = uiState.selectedSite?.label ?: stringResource(R.string.select_site),
                            expanded = siteDropdownExpanded,
                            enabled = !uiState.isSessionActive,
                            onExpandedChange = { siteDropdownExpanded = it && !uiState.isSessionActive },
                            onDismiss = { siteDropdownExpanded = false }
                        ) {
                            sites.forEach { site ->
                                DropdownMenuItem(
                                    text = { Text(site.label) },
                                    onClick = {
                                        viewModel.selectSite(site)
                                        siteDropdownExpanded = false
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    if (jobTypes.isNotEmpty()) {
                        TrackerDropdown(
                            label = stringResource(R.string.job_description),
                            icon = Icons.Filled.Work,
                            value = uiState.selectedJobType?.name ?: stringResource(R.string.select_job_type),
                            expanded = jobTypeDropdownExpanded,
                            enabled = !uiState.isSessionActive,
                            onExpandedChange = { jobTypeDropdownExpanded = it && !uiState.isSessionActive },
                            onDismiss = { jobTypeDropdownExpanded = false }
                        ) {
                            jobTypes.forEach { jobType ->
                                DropdownMenuItem(
                                    text = { Text(jobType.name) },
                                    onClick = {
                                        viewModel.selectJobType(jobType)
                                        jobTypeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    uiState.locationErrorRes?.let { resId ->
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(resId), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val canStart = hasLocationPermission && uiState.selectedCompany != null &&
                uiState.selectedSite != null && uiState.selectedJobType != null &&
                !uiState.isSessionActive && !uiState.isProcessing
            val canStop = uiState.isSessionActive && !uiState.isProcessing

            if (!uiState.isSessionActive) {
                Button(
                    onClick = { viewModel.startTracking() },
                    enabled = canStart,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.stopTracking() },
                    enabled = canStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stop), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showAddSessionDialog = true },
                enabled = companies.isNotEmpty() && sites.isNotEmpty() && jobTypes.isNotEmpty(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_session_manually))
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(title = stringResource(R.string.history), count = sessions.size)
            Spacer(Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                EmptyState(text = stringResource(R.string.sessions_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            onEditClick = { editingSession = session },
                            onDeleteClick = { deletingSession = session }
                        )
                    }
                }
            }
        }
    }
}

/** Lets the user type in a session that wasn't tracked with GPS (date + start/end time). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionDialog(
    companies: List<Company>,
    sites: List<Site>,
    jobTypes: List<JobType>,
    initialCompany: Company?,
    initialSite: Site?,
    initialJobType: JobType?,
    onDismiss: () -> Unit,
    onConfirm: (company: Company, site: Site, jobType: JobType, startMillis: Long, stopMillis: Long) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    var company by remember { mutableStateOf(initialCompany ?: companies.singleOrNull()) }
    var site by remember { mutableStateOf(initialSite ?: sites.singleOrNull()) }
    var jobType by remember { mutableStateOf(initialJobType ?: jobTypes.singleOrNull()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.of(7, 0)) }
    // Duration is what gets saved; the end time shown is always start + duration, so the
    // three fields stay consistent whichever one the user edits (and shifts can cross midnight).
    var hoursText by remember { mutableStateOf("8") }
    var minutesText by remember { mutableStateOf("30") }
    var companyExpanded by remember { mutableStateOf(false) }
    var siteExpanded by remember { mutableStateOf(false) }
    var jobTypeExpanded by remember { mutableStateOf(false) }

    val durationMinutes = (hoursText.toLongOrNull() ?: 0L) * 60 + (minutesText.toLongOrNull() ?: 0L).coerceIn(0, 59)
    val endTime = startTime.plusMinutes(durationMinutes)
    val zone = ZoneId.systemDefault()
    val startMillis = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
    val stopMillis = startMillis + TimeUnit.MINUTES.toMillis(durationMinutes)
    val canSave = company != null && site != null && jobType != null && durationMinutes > 0

    // Minutes from [from] to [to] on the clock; a "to" at or before "from" is read as the next day.
    fun minutesBetween(from: LocalTime, to: LocalTime): Long {
        val diff = Duration.between(from, to).toMinutes()
        return if (diff <= 0) diff + 24 * 60 else diff
    }
    fun setDuration(minutes: Long) {
        hoursText = (minutes / 60).toString()
        minutesText = (minutes % 60).toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EditCalendar, contentDescription = null) },
        title = { Text(stringResource(R.string.add_session)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TrackerDropdown(
                    label = stringResource(R.string.company),
                    icon = Icons.Filled.Business,
                    value = company?.name ?: stringResource(R.string.select_company),
                    expanded = companyExpanded,
                    enabled = true,
                    onExpandedChange = { companyExpanded = it },
                    onDismiss = { companyExpanded = false }
                ) {
                    companies.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name) }, onClick = { company = option; companyExpanded = false })
                    }
                }
                Spacer(Modifier.height(10.dp))
                TrackerDropdown(
                    label = stringResource(R.string.site),
                    icon = Icons.Filled.Place,
                    value = site?.label ?: stringResource(R.string.select_site),
                    expanded = siteExpanded,
                    enabled = true,
                    onExpandedChange = { siteExpanded = it },
                    onDismiss = { siteExpanded = false }
                ) {
                    sites.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { site = option; siteExpanded = false })
                    }
                }
                Spacer(Modifier.height(10.dp))
                TrackerDropdown(
                    label = stringResource(R.string.job_description),
                    icon = Icons.Filled.Work,
                    value = jobType?.name ?: stringResource(R.string.select_job_type),
                    expanded = jobTypeExpanded,
                    enabled = true,
                    onExpandedChange = { jobTypeExpanded = it },
                    onDismiss = { jobTypeExpanded = false }
                ) {
                    jobTypes.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name) }, onClick = { jobType = option; jobTypeExpanded = false })
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { showDatePicker(context, date) { date = it } },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${stringResource(R.string.date)}: ${date.format(dateFormatter)}")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showTimePicker(context, startTime) { picked ->
                                val keptEnd = endTime
                                startTime = picked
                                setDuration(minutesBetween(picked, keptEnd))
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(startTime.format(timeFormatter))
                    }
                    OutlinedButton(
                        onClick = { showTimePicker(context, endTime) { setDuration(minutesBetween(startTime, it)) } },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(endTime.format(timeFormatter))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.start_time),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.end_time),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { value -> hoursText = value.filter { it.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.hours)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { value -> minutesText = value.filter { it.isDigit() }.take(2) },
                        label = { Text(stringResource(R.string.minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = (minutesText.toIntOrNull() ?: 0) > 59,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (durationMinutes <= 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.duration_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(company!!, site!!, jobType!!, startMillis, stopMillis) },
                enabled = canSave
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SessionRow(session: TrackingSession, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    val durationMillis = session.durationMillis ?: 0L
    val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH) }
    val dateLine = dateFormatter.format(Date(session.startTimestampMillis))

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!session.siteLabel.isNullOrBlank()) {
                    Text(session.siteLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (!session.jobTypeLabel.isNullOrBlank()) {
                    Text(session.jobTypeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    stringResource(R.string.duration_format, hours, minutes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun EditDurationDialog(session: TrackingSession, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val durationMillis = session.durationMillis ?: 0L
    var hoursText by remember { mutableStateOf(TimeUnit.MILLISECONDS.toHours(durationMillis).toString()) }
    var minutesText by remember { mutableStateOf((TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.edit_duration)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { value -> hoursText = value.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.hours)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { value -> minutesText = value.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hours = hoursText.toLongOrNull() ?: 0
                val minutes = (minutesText.toLongOrNull() ?: 0).coerceIn(0, 59)
                onConfirm(TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes))
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteSessionDialog(session: TrackingSession, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete_entry)) },
        text = {
            val label = session.siteLabel?.takeIf { it.isNotBlank() }
            Text(
                if (label != null) stringResource(R.string.delete_entry_confirm_named, label)
                else stringResource(R.string.delete_entry_confirm)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SessionSummaryDialog(session: TrackingSession, onDismiss: () -> Unit) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH) }
    val durationMillis = session.durationMillis ?: 0L
    val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.session_finished)) },
        text = {
            Column {
                if (!session.siteLabel.isNullOrBlank()) {
                    Text(stringResource(R.string.session_site, session.siteLabel))
                }
                if (!session.companyName.isNullOrBlank()) {
                    Text(stringResource(R.string.session_company, session.companyName))
                }
                if (!session.jobTypeLabel.isNullOrBlank()) {
                    Text(stringResource(R.string.session_job_type, session.jobTypeLabel))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.session_start, formatter.format(Date(session.startTimestampMillis))))
                session.stopTimestampMillis?.let {
                    Text(stringResource(R.string.session_end, formatter.format(Date(it))))
                }
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = stringResource(R.string.session_duration, hours, minutes),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.session_start_location,
                        "%.6f".format(session.startLatitude),
                        "%.6f".format(session.startLongitude)
                    )
                )
                if (session.stopLatitude != null && session.stopLongitude != null) {
                    Text(
                        stringResource(
                            R.string.session_end_location,
                            "%.6f".format(session.stopLatitude),
                            "%.6f".format(session.stopLongitude)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
