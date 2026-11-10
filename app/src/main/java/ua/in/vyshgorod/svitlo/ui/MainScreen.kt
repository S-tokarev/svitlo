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
import ua.`in`.vyshgorod.svitlo.data.HouseStatus
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
    onWarn: (Boolean) -> Unit,
    onNotifyOn: (Boolean) -> Unit,
    onQuietNight: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onOpenSource: () -> Unit,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val picture = state.snapshot?.takeIf { state.selection.group.isNotBlank() }?.let {
        ScheduleMath.picture(it, state.selection, now)
    }
    val groups = ScheduleMath.knownGroups(state.snapshot)
    val tomorrowPublished = state.snapshot?.days
        ?.firstOrNull { it.day == "tomorrow" }
        ?.groups
        ?.containsKey(state.selection.group) == true

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LocalSvitloColors.current.paper),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Светло", color = LocalSvitloColors.current.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
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
                    Text(if (state.loading) "Обновляем…" else "Обновить")
                }
            }
        }

        item {
            StatusCard(state.selection, picture, state.snapshot?.fetchedAt, now)
        }

        val note = state.snapshot?.note?.takeIf { it.isNotBlank() }
        if (note != null) {
            item {
                Panel(tint = LocalSvitloColors.current.note) {
                    Text("Сообщение оператора", color = LocalSvitloColors.current.amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    title = if (picture.scheduleLabel.isBlank()) "Сегодня" else picture.scheduleLabel,
                    outages = picture.outagesToday,
                    nowMin = ScheduleMath.minuteOfDay(now),
                )
            }
        } else if (picture?.plannedOn != null) {
            item {
                DayScheduleCard(
                    title = if (picture.scheduleLabel.isBlank()) "Сегодня" else picture.scheduleLabel,
                    outages = emptyList(),
                    nowMin = ScheduleMath.minuteOfDay(now),
                )
            }
        } else if (state.selection.group.isNotBlank() && state.snapshot != null) {
            item { QuietNote("На сегодня для этой группы графика на сайте нет.") }
        }

        if (picture != null && picture.outagesTomorrow.isNotEmpty()) {
            item {
                DayScheduleCard(
                    title = "Завтра",
                    outages = picture.outagesTomorrow,
                    nowMin = null,
                )
            }
        } else if (tomorrowPublished) {
            item {
                DayScheduleCard(title = "Завтра", outages = emptyList(), nowMin = null)
            }
        } else if (state.snapshot != null && state.selection.group.isNotBlank()) {
            item { QuietNote("Графика на завтра для этой группы ещё нет.") }
        }

        item {
            Panel {
                SectionTitle("Группа")
                Spacer(Modifier.height(10.dp))
                groups.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { group ->
                            val selected = group == state.selection.group
                            val on = state.snapshot?.let {
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

        item {
            Panel {
                SectionTitle("Адрес")
                Spacer(Modifier.height(4.dp))
                Text(
                    "По всей Киевской области. Адрес выбирает группу. Если дом есть на vyshgorod.in.ua, статус берётся с датчика.",
                    color = LocalSvitloColors.current.muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Улица и дом, например Буча Шевченка 11") },
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSearch,
                    enabled = !state.searching,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (state.searching) "Ищем…" else "Найти группу")
                }
                state.searchError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = LocalSvitloColors.current.muted, fontSize = 13.sp)
                }
                if (state.results.isNotEmpty()) Spacer(Modifier.height(6.dp))
                state.results.take(40).forEach { hit ->
                    Text(
                        if (hit.group.isBlank()) "${hit.city}, ${hit.street}"
                        else "${hit.label}  ·  группа ${hit.group}",
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

        val houses = state.snapshot?.houses?.filter { it.group == state.selection.group }.orEmpty()
        if (houses.isNotEmpty()) {
            item {
                val onCount = houses.count { it.powerOn }
                Panel {
                    SectionTitle("Дома группы ${state.selection.group}")
                    Spacer(Modifier.height(4.dp))
                    Text("Со светом $onCount, без света ${houses.size - onCount}", color = LocalSvitloColors.current.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    houses.sortedBy { it.address }.forEach { house -> HouseRow(house) }
                }
            }
        }

        item {
            Panel {
                SectionTitle("Оформление")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice("Система", state.themeMode == ThemeMode.System, Modifier.weight(1f)) {
                        onTheme(ThemeMode.System)
                    }
                    ThemeChoice("Светлая", state.themeMode == ThemeMode.Light, Modifier.weight(1f)) {
                        onTheme(ThemeMode.Light)
                    }
                    ThemeChoice("Тёмная", state.themeMode == ThemeMode.Dark, Modifier.weight(1f)) {
                        onTheme(ThemeMode.Dark)
                    }
                }
            }
        }

        item {
            Panel {
                SectionTitle("Уведомления")
                Spacer(Modifier.height(4.dp))
                SettingRow("За 10 минут до отключения", state.warnEnabled, onWarn)
                SettingRow("Когда по графику включат свет", state.notifyOn, onNotifyOn)
                SettingRow("Не беспокоить ночью", state.quietNight, onQuietNight)
                Text(
                    "С 23:00 до 07:00 по Киеву уведомления не приходят.",
                    color = LocalSvitloColors.current.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                if (!state.notificationsGranted) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRequestNotifications,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Разрешить уведомления") }
                }
                if (!state.batteryFree) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Чтобы предупреждение приходило вовремя, разрешите приложению работать в фоне.",
                        color = LocalSvitloColors.current.muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRequestBattery,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Разрешить работу в фоне") }
                }
            }
        }

        item {
            val updated = picture?.updatedLabel?.takeIf { it.isNotBlank() }
            Column(Modifier.clickable(onClick = onOpenSource).padding(horizontal = 4.dp, vertical = 2.dp)) {
                if (updated != null) {
                    Text("График на сайте обновлён $updated", color = LocalSvitloColors.current.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                }
                Text("ДТЕК · Киевские региональные электросети", color = LocalSvitloColors.current.muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("Открыть график на сайте", color = LocalSvitloColors.current.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusCard(selection: Selection, picture: PowerPicture?, fetchedAt: Long?, now: Long) {
    val on = picture?.displayOn
    val title = when {
        selection.group.isBlank() -> "Выберите группу"
        on == true -> "Свет есть"
        on == false -> "Света нет"
        else -> "Нет данных"
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
                            StatusHeading(selection, title, color, on, picture?.usingHouse == true)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(hint.duration.orEmpty(), color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(hint.whenText, color = LocalSvitloColors.current.ink, fontSize = 14.sp)
                        }
                    }
                } else {
                    StatusHeading(selection, title, color, on, picture?.usingHouse == true)
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
                        if (planned) "По графику группы свет должен быть" else "По графику группы света быть не должно",
                        color = LocalSvitloColors.current.amber,
                        fontSize = 13.sp,
                    )
                }
                if (fetchedAt != null && fetchedAt > 0L) {
                    Spacer(Modifier.height(10.dp))
                    Text("Обновлено ${ScheduleMath.formatDayTime(fetchedAt, now)}", color = LocalSvitloColors.current.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusHeading(selection: Selection, title: String, color: Color, on: Boolean?, usingHouse: Boolean) {
    val place = selection.placeLabel
    Text(
        when {
            place != null -> "$place · группа ${selection.group}"
            selection.group.isBlank() -> "Группа не выбрана"
            else -> "Группа ${selection.group}"
        },
        color = LocalSvitloColors.current.muted,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(4.dp))
    Text(title, color = color, fontSize = 36.sp, fontWeight = FontWeight.Bold)
    if (selection.group.isNotBlank() && on != null) {
        Text(
            if (usingHouse) "По датчику этого дома" else "По графику",
            color = LocalSvitloColors.current.muted,
            fontSize = 13.sp,
        )
    }
}

private data class NextHint(val whenText: String, val duration: String?)

private fun nextHint(picture: PowerPicture?, now: Long): NextHint {
    if (picture == null) return NextHint("График подтянется с сайта", null)
    val on = picture.displayOn
    val off = picture.nextOffAt
    val back = picture.nextOnAt
    return when {
        on == true && off != null -> NextHint(
            "По графику выключат в ${ScheduleMath.formatDayTime(off, now)}",
            "через ${ScheduleMath.formatDuration(off - now)}",
        )
        on == true -> NextHint("Ближайших отключений по графику нет", null)
        on == false && back != null -> NextHint(
            "По графику включат в ${ScheduleMath.formatDayTime(back, now)}",
            "через ${ScheduleMath.formatDuration(back - now)}",
        )
        on == false -> NextHint("Когда включат, на графике не сказано", null)
        else -> NextHint("Не удалось понять статус этой группы", null)
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
            TotalChip("Свет есть", onMinutes, LocalSvitloColors.current.powerOnText, LocalSvitloColors.current.powerOn, Modifier.weight(1f))
            TotalChip("Света нет", offMinutes, LocalSvitloColors.current.powerOffText, LocalSvitloColors.current.powerOff, Modifier.weight(1f))
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
            if (minutes <= 0) "нет" else ScheduleMath.formatDuration(minutes * 60_000L),
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
        val placed = remember(marks, trackWidth) { placeSwitchLabels(marks, trackWidth) }
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
            Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxWidth().height(46.dp)) {
                Text("00", color = c.muted, fontSize = 11.sp, modifier = Modifier.offset(x = 0.dp, y = 8.dp))
                Text(
                    "24",
                    color = c.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.offset(x = (trackWidth - 16.dp).coerceAtLeast(0.dp), y = 8.dp),
                )
                marks.forEach { mark ->
                    val center = trackWidth * (mark.minute / 1440f)
                    val tickColor = if (mark.turnsOn) c.powerOn else c.powerOff
                    Box(
                        Modifier
                            .offset(x = (center - 1.dp).coerceIn(0.dp, (trackWidth - 2.dp).coerceAtLeast(0.dp)))
                            .width(2.dp)
                            .height(7.dp)
                            .background(tickColor),
                    )
                }
                placed.forEach { (mark, left) ->
                    val color = if (mark.turnsOn) c.powerOnText else c.powerOffText
                    Column(
                        Modifier.offset(x = left, y = 8.dp).width(SwitchLabelWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            ScheduleMath.formatClock(mark.minute),
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (mark.turnsOn) "включат" else "выключат",
                            color = color,
                            fontSize = 10.sp,
                        )
                    }
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
    val name = if (slice.powerOn) "свет есть" else "света нет"
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
                if (current) "$name · сейчас" else name,
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
private fun HouseRow(house: HouseStatus) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (house.powerOn) LocalSvitloColors.current.powerOn else LocalSvitloColors.current.powerOff),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                house.address + (house.complex?.let { " · $it" } ?: ""),
                color = LocalSvitloColors.current.ink,
                fontSize = 14.sp,
            )
            if (!house.powerOn && !house.since.isNullOrBlank()) {
                Text(house.since, color = LocalSvitloColors.current.muted, fontSize = 11.sp)
            }
        }
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
private fun QuietNote(text: String) {
    Panel {
        Text(text, color = LocalSvitloColors.current.muted, fontSize = 15.sp, lineHeight = 21.sp)
    }
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

private val SwitchLabelWidth = 68.dp

private fun switchMarks(slices: List<DaySlice>): List<SwitchMark> {
    return slices.mapNotNull { slice ->
        if (slice.startMin <= 0 || slice.startMin >= 1440) null
        else SwitchMark(slice.startMin, turnsOn = slice.powerOn)
    }
}

private fun placeSwitchLabels(marks: List<SwitchMark>, width: Dp): List<Pair<SwitchMark, Dp>> {
    val label = SwitchLabelWidth
    var cursor = 20.dp
    val limit = (width - label - 20.dp).coerceAtLeast(cursor)
    return marks.map { mark ->
        val ideal = width * (mark.minute / 1440f) - label / 2
        val left = ideal.coerceIn(cursor, limit)
        cursor = (left + label + 4.dp).coerceAtMost(limit)
        mark to left
    }
}

private fun compactDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "${hours}ч ${rest}м"
        hours > 0 -> "${hours}ч"
        else -> "${rest}м"
    }
}
