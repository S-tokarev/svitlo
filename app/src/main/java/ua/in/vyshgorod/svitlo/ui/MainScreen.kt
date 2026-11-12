package ua.`in`.vyshgorod.svitlo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ua.`in`.vyshgorod.svitlo.data.AddressHit
import ua.`in`.vyshgorod.svitlo.data.AlertsEnergy
import ua.`in`.vyshgorod.svitlo.data.AppLanguage
import ua.`in`.vyshgorod.svitlo.data.GridOperator
import ua.`in`.vyshgorod.svitlo.data.Regions
import ua.`in`.vyshgorod.svitlo.data.Snapshot
import ua.`in`.vyshgorod.svitlo.data.L10n
import ua.`in`.vyshgorod.svitlo.data.Outage
import ua.`in`.vyshgorod.svitlo.data.PowerPicture
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.ThemeMode

@Composable
fun MainScreen(
    state: ScreenState,
    onRefresh: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onPickAddress: (AddressHit) -> Unit,
    onFocusPlace: (Selection) -> Unit,
    onRemovePlace: (Selection) -> Unit,
    onWarn: (Boolean) -> Unit,
    onNotifyOn: (Boolean) -> Unit,
    onQuietNight: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onRegion: (String) -> Unit,
    onOperator: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onOpenSource: () -> Unit,
) {
    L10n.language = state.language
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val chart = state.snapshot?.takeIf { Regions.same(it.region, state.region) }
    val picture = chart?.takeIf { state.selection.group.isNotBlank() }?.let {
        ScheduleMath.picture(it, state.selection, now)
    }
    val groups = ScheduleMath.knownGroups(state.snapshot, state.region)
    val tomorrowPublished = chart?.let {
        ScheduleMath.groupHasTomorrow(it, state.selection.group)
    } == true

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LocalSvitloColors.current.paper),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L10n.appTitle(), color = LocalSvitloColors.current.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalSvitloColors.current.accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = onRefresh,
                    enabled = !state.loading,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(if (state.loading) L10n.refreshing() else L10n.refresh())
                }
            }
        }

        item {
            StatusCard(state.selection, picture, chart?.fetchedAt, now)
        }

        val note = chart?.note?.takeIf { it.isNotBlank() }
        if (note != null) {
            item {
                Panel(tint = LocalSvitloColors.current.note) {
                    Text(L10n.operatorNote(), color = LocalSvitloColors.current.amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(note, color = LocalSvitloColors.current.ink, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }

        val errorText = state.error
        if (errorText != null) {
            item {
                Panel(tint = LocalSvitloColors.current.danger) {
                    Text(errorText, color = LocalSvitloColors.current.powerOffText, fontSize = 15.sp)
                }
            }
        }

        if (picture != null && picture.outagesToday.isNotEmpty()) {
            item {
                DayScheduleCard(
                    title = if (picture.scheduleLabel.isBlank()) L10n.today() else picture.scheduleLabel,
                    outages = picture.outagesToday,
                    nowMin = ScheduleMath.minuteOfDay(now),
                )
            }
        } else if (picture?.plannedOn != null) {
            item {
                DayScheduleCard(
                    title = if (picture.scheduleLabel.isBlank()) L10n.today() else picture.scheduleLabel,
                    outages = emptyList(),
                    nowMin = ScheduleMath.minuteOfDay(now),
                )
            }
        }

        if (picture != null && picture.outagesTomorrow.isNotEmpty()) {
            item {
                DayScheduleCard(
                    title = L10n.tomorrow(),
                    outages = picture.outagesTomorrow,
                    nowMin = null,
                )
            }
        } else if (tomorrowPublished) {
            item {
                DayScheduleCard(title = L10n.tomorrow(), outages = emptyList(), nowMin = null)
            }
        }

        item {
            Panel {
                RegionPicker(
                    state.region,
                    chart?.operators.orEmpty(),
                    state.operatorId.ifBlank { chart?.operatorId.orEmpty() },
                    onRegion,
                    onOperator,
                )
                Spacer(Modifier.height(14.dp))
                SectionTitle(L10n.groupSection())
                Spacer(Modifier.height(10.dp))
                if (groups.isEmpty()) {
                    Text(L10n.groupsSoon(), color = LocalSvitloColors.current.muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
                groups.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { group ->
                            val selected = group == state.selection.group
                            val on = chart?.let {
                                ScheduleMath.picture(it, Selection(group), now).displayOn
                            }
                            GroupChip(
                                label = group,
                                selected = selected,
                                powerOn = on,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectGroup(group) },
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (state.region == Regions.KYIV_OBLAST) item {
            Panel {
                SectionTitle(L10n.addresses())
                Spacer(Modifier.height(4.dp))
                Text(
                    L10n.addressesHint(),
                    color = LocalSvitloColors.current.muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                if (state.places.isNotEmpty()) Spacer(Modifier.height(8.dp))
                state.places.forEach { place ->
                    val on = chart?.let { ScheduleMath.picture(it, place, now).displayOn }
                    val selected = place.placeKey == state.selection.placeKey
                    val title = place.placeLabel ?: L10n.groupTitle(place.group)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) LocalSvitloColors.current.card2 else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).clickable { onFocusPlace(place) }) {
                            Text(title, color = LocalSvitloColors.current.ink, fontWeight = FontWeight.Medium)
                            Text(
                                L10n.savedPlaceStatus(place.group, on),
                                color = when (on) {
                                    true -> LocalSvitloColors.current.powerOnText
                                    false -> LocalSvitloColors.current.powerOffText
                                    null -> LocalSvitloColors.current.muted
                                },
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            L10n.remove(),
                            color = LocalSvitloColors.current.muted,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { onRemovePlace(place) }.padding(8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(L10n.addressHint()) },
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSearch,
                    enabled = !state.searching,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (state.searching) L10n.searching() else L10n.search())
                }
                state.searchError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = LocalSvitloColors.current.muted, fontSize = 13.sp)
                }
                if (state.results.isNotEmpty()) Spacer(Modifier.height(6.dp))
                state.results.take(40).forEach { hit ->
                    Text(
                        if (hit.group.isBlank()) "${hit.city}, ${hit.street}"
                        else L10n.addressHit(hit.label, hit.group),
                        color = LocalSvitloColors.current.ink,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LocalSvitloColors.current.card2)
                            .clickable { onPickAddress(hit) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        item {
            Panel {
                SectionTitle(L10n.languageSection())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice("Українська", state.language == AppLanguage.Uk, Modifier.weight(1f)) {
                        onLanguage(AppLanguage.Uk)
                    }
                    ThemeChoice("Русский", state.language == AppLanguage.Ru, Modifier.weight(1f)) {
                        onLanguage(AppLanguage.Ru)
                    }
                    ThemeChoice("English", state.language == AppLanguage.En, Modifier.weight(1f)) {
                        onLanguage(AppLanguage.En)
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle(L10n.appearance())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice(L10n.themeSystem(), state.themeMode == ThemeMode.System, Modifier.weight(1f)) {
                        onTheme(ThemeMode.System)
                    }
                    ThemeChoice(L10n.themeLight(), state.themeMode == ThemeMode.Light, Modifier.weight(1f)) {
                        onTheme(ThemeMode.Light)
                    }
                    ThemeChoice(L10n.themeDark(), state.themeMode == ThemeMode.Dark, Modifier.weight(1f)) {
                        onTheme(ThemeMode.Dark)
                    }
                }
            }
        }

        item {
            Panel {
                SectionTitle(L10n.notifications())
                Spacer(Modifier.height(4.dp))
                SettingRow(L10n.warnBefore(), state.warnEnabled, onWarn)
                SettingRow(L10n.notifyOn(), state.notifyOn, onNotifyOn)
                SettingRow(L10n.quietNight(), state.quietNight, onQuietNight)
                Text(
                    L10n.quietHint(),
                    color = LocalSvitloColors.current.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                if (!state.notificationsGranted) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRequestNotifications,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(L10n.allowNotifications()) }
                }
                if (!state.batteryFree) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L10n.batteryHint(),
                        color = LocalSvitloColors.current.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRequestBattery,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(L10n.allowBackground()) }
                }
            }
        }

        item {
            val updated = picture?.updatedLabel?.takeIf { it.isNotBlank() }
            Column(Modifier.clickable(onClick = onOpenSource).padding(horizontal = 4.dp, vertical = 2.dp)) {
                if (updated != null) {
                    Text(L10n.siteUpdated(updated), color = LocalSvitloColors.current.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                }
                if (Regions.same(state.region, Regions.KYIV_OBLAST)) {
                    Text(L10n.dtekCredit(), color = LocalSvitloColors.current.muted, fontSize = 12.sp)
                }
                scheduleCredit(chart, state.region)?.let { credit ->
                    Spacer(Modifier.height(2.dp))
                    Text(credit, color = LocalSvitloColors.current.muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(L10n.openSchedule(), color = LocalSvitloColors.current.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusCard(selection: Selection, picture: PowerPicture?, fetchedAt: Long?, now: Long) {
    val on = picture?.displayOn
    val title = when {
        selection.group.isBlank() -> L10n.chooseGroup()
        on == true -> L10n.powerOn()
        on == false -> L10n.powerOff()
        else -> L10n.noData()
    }
    val color = when (on) {
        true -> LocalSvitloColors.current.powerOnText
        false -> LocalSvitloColors.current.powerOffText
        null -> LocalSvitloColors.current.ink
    }
    val hint = nextHint(picture, now)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, LocalSvitloColors.current.line, RoundedCornerShape(28.dp))
            .background(LocalSvitloColors.current.card),
    ) {
        val strip = when (on) {
            true -> LocalSvitloColors.current.powerOn
            false -> LocalSvitloColors.current.powerOff
            null -> LocalSvitloColors.current.card2
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(strip))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            val wide = maxWidth > 520.dp && hint.duration != null
            Column {
                if (wide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            StatusHeading(selection, title, color)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(hint.duration.orEmpty(), color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(hint.whenText, color = LocalSvitloColors.current.ink, fontSize = 14.sp)
                        }
                    }
                } else {
                    StatusHeading(selection, title, color)
                    Spacer(Modifier.height(10.dp))
                    Text(hint.whenText, color = LocalSvitloColors.current.ink, fontSize = 16.sp, lineHeight = 22.sp)
                    if (hint.duration != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(hint.duration, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                val planned = picture?.plannedOn
                val houseOn = picture?.houseOn
                if (picture?.usingHouse == true && houseOn != null && planned != null && houseOn != planned) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (planned) L10n.plannedShouldBeOn() else L10n.plannedShouldBeOff(),
                        color = LocalSvitloColors.current.amber,
                        fontSize = 13.sp,
                    )
                }
                if (fetchedAt != null && fetchedAt > 0L) {
                    Spacer(Modifier.height(10.dp))
                    Text(L10n.updated(ScheduleMath.formatDayTime(fetchedAt, now)), color = LocalSvitloColors.current.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusHeading(selection: Selection, title: String, color: Color) {
    Text(
        L10n.placeHeading(selection.placeLabel, selection.group),
        color = LocalSvitloColors.current.muted,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(4.dp))
    Text(title, color = color, fontSize = 36.sp, fontWeight = FontWeight.Bold)
}

private data class NextHint(val whenText: String, val duration: String?)

private fun nextHint(picture: PowerPicture?, now: Long): NextHint {
    if (picture == null) return NextHint(L10n.scheduleWillLoad(), null)
    val on = picture.displayOn
    val off = picture.nextOffAt
    val back = picture.nextOnAt
    return when {
        on == true && off != null -> NextHint(
            L10n.offAt(ScheduleMath.formatDayTime(off, now)),
            L10n.through(ScheduleMath.formatDuration(off - now)),
        )
        on == true -> NextHint(L10n.noUpcomingOutageLong(), null)
        on == false && back != null -> NextHint(
            L10n.onAt(ScheduleMath.formatDayTime(back, now)),
            L10n.through(ScheduleMath.formatDuration(back - now)),
        )
        on == false && picture.offUntil != null -> NextHint(
            L10n.offUntilMidnight(),
            L10n.through(ScheduleMath.formatDuration(picture.offUntil - now)),
        )
        on == false -> NextHint(L10n.onTimeUnknown(), null)
        else -> NextHint(L10n.statusUnknown(), null)
    }
}

@Composable
private fun DayScheduleCard(title: String, outages: List<Outage>, nowMin: Int?) {
    val slices = remember(outages) { daySlices(outages) }
    val onMinutes = slices.filter { it.powerOn }.sumOf { it.length }
    val offMinutes = slices.filter { !it.powerOn }.sumOf { it.length }
    Panel {
        Text(title, color = LocalSvitloColors.current.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalChip(L10n.powerOn(), onMinutes, LocalSvitloColors.current.powerOnText, LocalSvitloColors.current.powerOn, Modifier.weight(1f))
            TotalChip(L10n.powerOff(), offMinutes, LocalSvitloColors.current.powerOffText, LocalSvitloColors.current.powerOff, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        DayTrack(slices, nowMin)
        Spacer(Modifier.height(12.dp))
        slices.forEach { slice ->
            SliceRow(slice, nowMin)
        }
    }
}

@Composable
private fun TotalChip(
    label: String,
    minutes: Int,
    textColor: Color,
    dot: Color,
    modifier: Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LocalSvitloColors.current.card2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(label, color = LocalSvitloColors.current.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (minutes <= 0) L10n.none() else ScheduleMath.formatDuration(minutes * 60_000L),
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DayTrack(slices: List<DaySlice>, nowMin: Int?) {
    val c = LocalSvitloColors.current
    val measurer = rememberTextMeasurer()
    val marks = remember(slices) { switchMarks(slices) }
    val onFill = c.powerOn
    val offFill = c.powerOff
    val onInk = c.onPowerOn
    val offInk = c.onPowerOff
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth
        val density = LocalDensity.current
        val fitted = remember(chipsKey(marks), trackWidth) {
            fittedHourLabels(measurer, marks, with(density) { trackWidth.toPx() }, with(density) { 3.dp.toPx() })
        }
        val labelStyle = fitted.style
        val chips = fitted.chips
        val lefts = with(density) { fitted.lefts.map { it.toDp() } }
        val rowHeight = with(density) { fitted.height.toDp().coerceAtLeast(16.dp) }
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    slices.forEach { slice ->
                        val start = size.width * slice.startMin / 1440f
                        val end = size.width * slice.endMin / 1440f
                        val fill = if (slice.powerOn) onFill else offFill
                        drawRect(fill, topLeft = Offset(start, 0f), size = Size(end - start, size.height))
                        val label = compactDuration(slice.length)
                        val layout = measurer.measure(
                            label,
                            style = TextStyle(
                                color = if (slice.powerOn) onInk else offInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        val pad = 8.dp.toPx()
                        if (layout.size.width + pad < end - start && layout.size.height < size.height) {
                            drawText(
                                layout,
                                topLeft = Offset(
                                    start + (end - start - layout.size.width) / 2f,
                                    (size.height - layout.size.height) / 2f,
                                ),
                            )
                        }
                    }
                    marks.forEach { mark ->
                        val x = size.width * mark.minute / 1440f
                        drawLine(
                            Color.Black.copy(alpha = 0.45f),
                            Offset(x, 0f),
                            Offset(x, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(rowHeight)) {
                chips.forEachIndexed { index, chip ->
                    val left = lefts[index]
                    Text(
                        chip.text,
                        color = when (chip.turnsOn) {
                            true -> c.powerOnText
                            false -> c.powerOffText
                            null -> c.muted
                        },
                        style = labelStyle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(x = left),
                    )
                }
            }
        }
        if (nowMin != null) {
            val x = trackWidth * nowMin / 1440f
            Box(
                Modifier
                    .offset(x = (x - 1.dp).coerceIn(0.dp, (trackWidth - 2.dp).coerceAtLeast(0.dp)))
                    .width(2.dp)
                    .height(40.dp)
                    .background(c.now),
            )
            Box(
                Modifier
                    .offset(
                        x = (x - 6.dp).coerceIn(0.dp, (trackWidth - 12.dp).coerceAtLeast(0.dp)),
                        y = 14.dp,
                    )
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(c.now)
                    .border(2.dp, c.paper, CircleShape),
            )
        }
    }
}

@Composable
private fun SliceRow(slice: DaySlice, nowMin: Int?) {
    val current = nowMin != null && nowMin >= slice.startMin && nowMin < slice.endMin
    val accent = if (slice.powerOn) LocalSvitloColors.current.powerOnText else LocalSvitloColors.current.powerOffText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) LocalSvitloColors.current.card2 else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (slice.powerOn) LocalSvitloColors.current.powerOn else LocalSvitloColors.current.powerOff),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${ScheduleMath.formatClock(slice.startMin)}–${ScheduleMath.formatClock(slice.endMin)}",
                color = LocalSvitloColors.current.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                L10n.sliceLabel(slice.powerOn, current),
                color = if (current) accent else LocalSvitloColors.current.muted,
                fontSize = 12.sp,
            )
        }
        Text(
            ScheduleMath.formatDuration(slice.length * 60_000L),
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    powerOn: Boolean?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val dot = when (powerOn) {
        true -> LocalSvitloColors.current.powerOn
        false -> LocalSvitloColors.current.powerOff
        null -> LocalSvitloColors.current.muted
    }
    val ring = when {
        selected && powerOn == true -> LocalSvitloColors.current.powerOnText
        selected && powerOn == false -> LocalSvitloColors.current.powerOffText
        selected -> LocalSvitloColors.current.amber
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) LocalSvitloColors.current.card2 else LocalSvitloColors.current.chip)
            .border(1.5.dp, ring, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.height(5.dp))
        Text(label, color = if (selected) LocalSvitloColors.current.ink else LocalSvitloColors.current.muted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun SettingRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = LocalSvitloColors.current.ink, modifier = Modifier.weight(1f), fontSize = 15.sp, lineHeight = 20.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LocalSvitloColors.current.accent,
                checkedThumbColor = LocalSvitloColors.current.onAccent,
                uncheckedTrackColor = LocalSvitloColors.current.chip,
                uncheckedThumbColor = LocalSvitloColors.current.muted,
                uncheckedBorderColor = LocalSvitloColors.current.muted,
            ),
        )
    }
}

private fun scheduleCredit(snapshot: Snapshot?, region: String): String? {
    if (snapshot?.source != AlertsEnergy.SOURCE) return null
    if (!Regions.same(snapshot.region, region)) return null
    return if (Regions.same(region, Regions.KYIV_OBLAST)) L10n.fallbackCredit() else L10n.alertsCredit()
}

@Composable
private fun RegionPicker(
    region: String,
    operators: List<GridOperator>,
    operatorId: String,
    onRegion: (String) -> Unit,
    onOperator: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalSvitloColors.current
    SectionTitle(L10n.regionSection())
    Spacer(Modifier.height(8.dp))
    Text(
        Regions.find(region).title(),
        color = colors.ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card2)
            .clickable { open = !open }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
    if (open) {
        Spacer(Modifier.height(6.dp))
        Regions.all.forEach { item ->
            val selected = item.slug == Regions.normalize(region)
            Text(
                item.title(),
                color = if (selected) colors.accent else colors.ink,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        open = false
                        onRegion(item.slug)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    if (operators.size > 1) {
        Spacer(Modifier.height(12.dp))
        SectionTitle(L10n.operatorSection())
        Spacer(Modifier.height(8.dp))
        operators.forEach { operator ->
            val selected = operator.id == operatorId
            Text(
                operator.name,
                color = if (selected) colors.accent else colors.ink,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOperator(operator.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = LocalSvitloColors.current.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Panel(tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalSvitloColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, c.line, RoundedCornerShape(22.dp))
            .background(tint ?: c.card)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalSvitloColors.current.ink,
    unfocusedTextColor = LocalSvitloColors.current.ink,
    focusedBorderColor = LocalSvitloColors.current.accent,
    unfocusedBorderColor = LocalSvitloColors.current.fieldBorder,
    cursorColor = LocalSvitloColors.current.accent,
    focusedContainerColor = LocalSvitloColors.current.field,
    unfocusedContainerColor = LocalSvitloColors.current.field,
    focusedPlaceholderColor = LocalSvitloColors.current.muted,
    unfocusedPlaceholderColor = LocalSvitloColors.current.muted,
    focusedLabelColor = LocalSvitloColors.current.muted,
    unfocusedLabelColor = LocalSvitloColors.current.muted,
)

@Composable
private fun ThemeChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalSvitloColors.current
    Text(
        label,
        color = if (selected) c.onAccent else c.ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.accent else c.chip)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

private data class DaySlice(val startMin: Int, val endMin: Int, val powerOn: Boolean) {
    val length: Int get() = endMin - startMin
}

private fun daySlices(outages: List<Outage>): List<DaySlice> {
    val clipped = outages.mapNotNull { outage ->
        val start = outage.startMin.coerceIn(0, 1440)
        val end = outage.endMin.coerceIn(0, 1440)
        if (end > start) start to end else null
    }.sortedBy { it.first }
    val slices = mutableListOf<DaySlice>()
    var cursor = 0
    for ((start, end) in clipped) {
        if (start > cursor) slices += DaySlice(cursor, start, powerOn = true)
        val from = maxOf(start, cursor)
        if (end > from) slices += DaySlice(from, end, powerOn = false)
        cursor = maxOf(cursor, end)
    }
    if (cursor < 1440) slices += DaySlice(cursor, 1440, powerOn = true)
    return slices
}

private data class SwitchMark(val minute: Int, val turnsOn: Boolean)

private data class HourChip(val text: String, val minute: Int, val turnsOn: Boolean?)

private fun chipsKey(marks: List<SwitchMark>): String = marks.joinToString { "${it.minute}:${it.turnsOn}" }

private data class FittedHours(
    val chips: List<HourChip>,
    val lefts: FloatArray,
    val style: TextStyle,
    val height: Int,
)

private fun fittedHourLabels(
    measurer: TextMeasurer,
    marks: List<SwitchMark>,
    trackPx: Float,
    gapPx: Float,
): FittedHours {
    val chips = listOf(HourChip("00", 0, null)) +
        marks.map { HourChip(hourLabel(it.minute), it.minute, it.turnsOn) } +
        HourChip("24", 1440, null)
    var chosen = FittedHours(chips, FloatArray(chips.size), TextStyle(fontSize = 12.sp), 16)
    for (size in listOf(12, 11, 10, 9)) {
        val style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.SemiBold)
        val measured = chips.map { measurer.measure(it.text, style = style) }
        val widths = measured.map { it.size.width.toFloat() }
        val lefts = ScheduleMath.placeHourLabels(chips.map { it.minute }, widths, trackPx, gapPx)
        val height = measured.maxOf { it.size.height }
        chosen = FittedHours(chips, lefts, style, height)
        val fits = lefts.withIndex().all { (index, left) ->
            left >= -0.5f && left + widths[index] <= trackPx + 1f
        }
        if (fits) break
    }
    return chosen
}

private fun hourLabel(minute: Int): String {
    val hour = minute / 60
    val rest = minute % 60
    return if (rest == 0) "%02d".format(hour) else "%02d:%02d".format(hour, rest)
}

private fun switchMarks(slices: List<DaySlice>): List<SwitchMark> {
    return slices.mapNotNull { slice ->
        if (slice.startMin <= 0 || slice.startMin >= 1440) null
        else SwitchMark(slice.startMin, turnsOn = slice.powerOn)
    }
}

private fun compactDuration(minutes: Int): String {
    return L10n.compact(minutes / 60, minutes % 60)
}
