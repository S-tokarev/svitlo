package ua.`in`.vyshgorod.svitlo.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.`in`.vyshgorod.svitlo.data.AppLanguage
import ua.`in`.vyshgorod.svitlo.data.L10n
import ua.`in`.vyshgorod.svitlo.data.Outage
import ua.`in`.vyshgorod.svitlo.data.PowerPicture
import ua.`in`.vyshgorod.svitlo.data.ScheduleMath

private val Paper = Color(0xFFF4F0E6)
private val Ink = Color(0xFF1E1A14)
private val Muted = Color(0xFF6E655A)
private val Card = Color(0xFFFFFCF7)
private val Card2 = Color(0xFFEBE4D6)
private val OnGreen = Color(0xFF1E8F4A)
private val OffRed = Color(0xFFA33E2C)
private val BarOn = Color(0xFF7DDE96)
private val BarOff = Color(0xFFD1543E)
private val OnBarOn = Color(0xFF0E3B22)
private val OnBarOff = Color(0xFF3C120C)
private val Accent = Color(0xFF1C6E8A)

private val PaperDark = Color(0xFF141311)
private val InkDark = Color(0xFFF6F1E7)
private val MutedDark = Color(0xFFB6AB9C)
private val CardDark = Color(0xFF22201C)
private val Card2Dark = Color(0xFF2E2A24)
private val OnGreenDark = Color(0xFF8EE4A8)
private val OffRedDark = Color(0xFFE87864)

@Composable
fun DesktopApp(model: DesktopModel) {
    L10n.language = model.language
    val dark = when (model.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val paper = if (dark) PaperDark else Paper
    val ink = if (dark) InkDark else Ink
    val muted = if (dark) MutedDark else Muted
    val card = if (dark) CardDark else Card
    val card2 = if (dark) Card2Dark else Card2
    val onText = if (dark) OnGreenDark else OnGreen
    val offText = if (dark) OffRedDark else OffRed
    val picture = model.picture
    val now = model.now
    val hint = nextHint(picture, now)
    val scheme = if (dark) {
        darkColorScheme(primary = Accent, onPrimary = Color.White, background = paper, surface = card)
    } else {
        lightColorScheme(primary = Accent, onPrimary = Color.White, background = paper, surface = card)
    }

    MaterialTheme(colorScheme = scheme) {
    Column(
        Modifier.fillMaxSize().background(paper).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.appTitle(), color = ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = model::refresh, enabled = !model.loading) {
                Text(if (model.loading) L10n.refreshingShort() else L10n.refresh())
            }
        }
        Text(
            L10n.trayHint(),
            color = muted,
            fontSize = 13.sp,
        )
        StatusCard(model, picture, hint, card, ink, muted, onText, offText, now)
        model.error?.let { Text(it, color = offText) }
        model.snapshot?.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = ink) }
        val today = picture?.outagesToday
        if (picture != null && (today?.isNotEmpty() == true || picture.plannedOn != null)) {
            DayCard(
                title = picture.scheduleLabel.ifBlank { L10n.today() },
                outages = today.orEmpty(),
                nowMin = ScheduleMath.minuteOfDay(now),
                card = card,
                ink = ink,
                muted = muted,
                onText = onText,
                offText = offText,
            )
        }
        val tomorrow = picture?.outagesTomorrow
        val tomorrowPublished = model.snapshot?.let {
            ScheduleMath.groupHasTomorrow(it, model.selection.group)
        } == true
        if (picture != null && tomorrow != null && (tomorrow.isNotEmpty() || tomorrowPublished)) {
            DayCard(L10n.tomorrow(), tomorrow, null, card, ink, muted, onText, offText)
        }
        GroupCard(model, card, card2, ink, muted, onText, offText)
        SearchCard(model, card, card2, ink, muted)
        SettingsCard(model, card, ink)
    }
    }
}

@Composable
private fun StatusCard(
    model: DesktopModel,
    picture: PowerPicture?,
    hint: Pair<String, String?>,
    card: Color,
    ink: Color,
    muted: Color,
    onText: Color,
    offText: Color,
    now: Long,
) {
    val on = picture?.displayOn
    val color = when (on) {
        true -> onText
        false -> offText
        null -> ink
    }
    val title = when (on) {
        true -> L10n.powerOn()
        false -> L10n.powerOff()
        null -> if (model.selection.group.isBlank()) L10n.chooseGroup() else L10n.noData()
    }
    val heading = L10n.placeHeading(model.selection.placeLabel, model.selection.group)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(card).padding(18.dp)) {
        Text(heading, color = muted, fontSize = 13.sp)
        Text(title, color = color, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(hint.first, color = color, fontSize = 16.sp)
        hint.second?.let { Text(it, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        model.snapshot?.let {
            Spacer(Modifier.height(8.dp))
            Text(L10n.updated(ScheduleMath.formatDayTime(it.fetchedAt, now)), color = muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DayCard(
    title: String,
    outages: List<Outage>,
    nowMin: Int?,
    card: Color,
    ink: Color,
    muted: Color,
    onText: Color,
    offText: Color,
) {
    val slices = daySlices(outages)
    val onMinutes = slices.filter { it.powerOn }.sumOf { it.length }
    val offMinutes = slices.filter { !it.powerOn }.sumOf { it.length }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(card).padding(18.dp)) {
        Text(title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Total(L10n.powerOn(), onMinutes, onText, Modifier.weight(1f))
            Total(L10n.powerOff(), offMinutes, offText, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        DayTrack(
            slices = slices,
            nowMin = nowMin,
            onText = onText,
            offText = offText,
            muted = muted,
            nowColor = ink,
        )
        Spacer(Modifier.height(8.dp))
        slices.forEach { slice ->
            val current = nowMin != null && nowMin >= slice.startMin && nowMin < slice.endMin
            val accent = if (slice.powerOn) onText else offText
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(28.dp).background(accent))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${ScheduleMath.formatClock(slice.startMin)}–${ScheduleMath.formatClock(slice.endMin)}",
                        color = ink,
                    )
                    Text(
                        L10n.sliceLabel(slice.powerOn, current),
                        color = if (current) accent else muted,
                        fontSize = 12.sp,
                    )
                }
                Text(ScheduleMath.formatDuration(slice.length * 60_000L), color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Total(title: String, minutes: Int, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(10.dp)) {
        Text(title, color = color, fontSize = 13.sp)
        Text(ScheduleMath.formatDuration(minutes * 60_000L), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GroupCard(
    model: DesktopModel,
    card: Color,
    card2: Color,
    ink: Color,
    muted: Color,
    onText: Color,
    offText: Color,
) {
    val groups = ScheduleMath.knownGroups(model.snapshot)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(card).padding(18.dp)) {
        Text(L10n.groupSection(), color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        groups.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { group ->
                    val selected = group == model.selection.group
                    val state = model.snapshot?.let {
                        ScheduleMath.picture(it, ua.`in`.vyshgorod.svitlo.data.Selection(group), model.now).displayOn
                    }
                    val dot = when (state) {
                        true -> BarOn
                        false -> BarOff
                        null -> muted
                    }
                    val color = when {
                        selected && state == true -> onText
                        selected && state == false -> offText
                        else -> ink
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) card2 else Color.Transparent)
                            .clickable { model.selectGroup(group) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                        Text(group, color = if (selected) color else muted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCard(model: DesktopModel, card: Color, card2: Color, ink: Color, muted: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(card).padding(18.dp)) {
        Text(L10n.addresses(), color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(L10n.addressesHint(), color = muted, fontSize = 13.sp)
        if (model.places.isNotEmpty()) Spacer(Modifier.height(8.dp))
        model.places.forEach { place ->
            val on = model.snapshot?.let { ScheduleMath.picture(it, place, model.now).displayOn }
            val selected = place.placeKey == model.selection.placeKey
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) card2 else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).clickable { model.focusPlace(place) }) {
                    Text(place.placeLabel ?: L10n.groupTitle(place.group), color = ink, fontWeight = FontWeight.Medium)
                    Text(
                        L10n.savedPlaceStatus(place.group, on),
                        color = when (on) {
                            true -> OnGreen
                            false -> OffRed
                            null -> muted
                        },
                        fontSize = 12.sp,
                    )
                }
                Text(
                    L10n.remove(),
                    color = muted,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { model.removePlace(place) }.padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = model.query,
                onValueChange = { model.query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(L10n.addressHint()) },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = model::search) { Text(L10n.searchDesktop()) }
        }
        model.hint?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = muted, fontSize = 13.sp)
        }
        model.hits.take(12).forEach { hit ->
            Text(
                L10n.addressHit(hit.label, hit.group),
                color = ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(card2)
                    .clickable { model.pick(hit) }
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(model: DesktopModel, card: Color, ink: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(card).padding(18.dp)) {
        Text(L10n.languageSection(), color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AppLanguage.Uk to "Українська",
                AppLanguage.Ru to "Русский",
                AppLanguage.En to "English",
            ).forEach { (value, title) ->
                val selected = model.language == value
                Text(
                    title,
                    color = ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Accent.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { model.updateLanguage(value) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(L10n.appearance(), color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                val selected = model.theme == mode
                Text(
                    mode.title,
                    color = ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Accent.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { model.updateTheme(mode) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Toggle(L10n.warnToggleDesktop(), model.warn, model::updateWarn, ink)
        Toggle(L10n.notifyOnDesktop(), model.notifyOn, model::updateNotify, ink)
        Toggle(L10n.quietNightDesktop(), model.quiet, model::updateQuiet, ink)
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit, ink: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun nextHint(picture: PowerPicture?, now: Long): Pair<String, String?> {
    if (picture == null) return L10n.scheduleWillLoad() to null
    val on = picture.displayOn
    val off = picture.nextOffAt
    val back = picture.nextOnAt
    return when {
        on == true && off != null -> L10n.offAt(ScheduleMath.formatDayTime(off, now)) to
            L10n.through(ScheduleMath.formatDuration(off - now))
        on == true -> L10n.noUpcomingOutageLong() to null
        on == false && back != null -> L10n.onAt(ScheduleMath.formatDayTime(back, now)) to
            L10n.through(ScheduleMath.formatDuration(back - now))
        on == false && picture.offUntil != null -> L10n.offUntilMidnight() to
            L10n.through(ScheduleMath.formatDuration(picture.offUntil - now))
        on == false -> L10n.onTimeUnknown() to null
        else -> L10n.statusUnknown() to null
    }
}

private data class Slice(val startMin: Int, val endMin: Int, val powerOn: Boolean) {
    val length: Int get() = endMin - startMin
}

private fun daySlices(outages: List<Outage>): List<Slice> {
    val clipped = outages.mapNotNull { outage ->
        val start = outage.startMin.coerceIn(0, 1440)
        val end = outage.endMin.coerceIn(0, 1440)
        if (end > start) start to end else null
    }.sortedBy { it.first }
    val slices = mutableListOf<Slice>()
    var cursor = 0
    for ((start, end) in clipped) {
        if (start > cursor) slices += Slice(cursor, start, true)
        val from = maxOf(start, cursor)
        if (end > from) slices += Slice(from, end, false)
        cursor = maxOf(cursor, end)
    }
    if (cursor < 1440) slices += Slice(cursor, 1440, true)
    return slices
}

@Composable
private fun DayTrack(
    slices: List<Slice>,
    nowMin: Int?,
    onText: Color,
    offText: Color,
    muted: Color,
    nowColor: Color,
) {
    val measurer = rememberTextMeasurer()
    val marks = slices.mapNotNull { slice ->
        if (slice.startMin <= 0 || slice.startMin >= 1440) null
        else slice.startMin to slice.powerOn
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth
        val density = LocalDensity.current
        val labelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        val chips = listOf(Triple("00", 0, null as Boolean?)) +
            marks.map { Triple(hourLabel(it.first), it.first, it.second) } +
            Triple("24", 1440, null)
        val measured = chips.map { measurer.measure(it.first, style = labelStyle) }
        val lefts = with(density) {
            val trackPx = trackWidth.toPx()
            val gapPx = 3.dp.toPx()
            val widths = measured.map { it.size.width.toFloat() }
            ScheduleMath.placeHourLabels(chips.map { it.second }, widths, trackPx, gapPx).map { it.toDp() }
        }
        val rowHeight = with(density) {
            measured.maxOf { it.size.height }.toDp().coerceAtLeast(16.dp)
        }
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val durationStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    slices.forEach { slice ->
                        val start = size.width * slice.startMin / 1440f
                        val end = size.width * slice.endMin / 1440f
                        val fill = if (slice.powerOn) BarOn else BarOff
                        val ink = if (slice.powerOn) OnBarOn else OnBarOff
                        drawRect(fill, topLeft = Offset(start, 0f), size = Size(end - start, size.height))
                        val full = ScheduleMath.formatDuration(slice.length * 60_000L)
                        val short = compactDuration(slice.length)
                        val pad = 8.dp.toPx()
                        val width = end - start
                        val fullLayout = measurer.measure(full, style = durationStyle.copy(color = ink))
                        val layout = if (fullLayout.size.width + pad < width) {
                            fullLayout
                        } else {
                            measurer.measure(short, style = durationStyle.copy(color = ink))
                        }
                        if (layout.size.width + pad < width && layout.size.height < size.height) {
                            drawText(
                                layout,
                                topLeft = Offset(
                                    start + (width - layout.size.width) / 2f,
                                    (size.height - layout.size.height) / 2f,
                                ),
                            )
                        }
                    }
                    marks.forEach { mark ->
                        val x = size.width * mark.first / 1440f
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
                        chip.first,
                        color = when (chip.third) {
                            true -> onText
                            false -> offText
                            null -> muted
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
                    .height(44.dp)
                    .background(nowColor),
            )
            Box(
                Modifier
                    .offset(
                        x = (x - 6.dp).coerceIn(0.dp, (trackWidth - 12.dp).coerceAtLeast(0.dp)),
                        y = 16.dp,
                    )
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(nowColor)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            )
        }
    }
}

private fun hourLabel(minute: Int): String {
    val hour = minute / 60
    val rest = minute % 60
    return if (rest == 0) "%02d".format(hour) else "%02d:%02d".format(hour, rest)
}

private fun compactDuration(minutes: Int): String = L10n.compact(minutes / 60, minutes % 60)

@Composable
fun DesktopWidget(model: DesktopModel) {
    L10n.language = model.language
    val dark = when (model.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val paper = if (dark) PaperDark else Paper
    val ink = if (dark) InkDark else Ink
    val muted = if (dark) MutedDark else Muted
    val card = if (dark) CardDark else Card
    val onText = if (dark) OnGreenDark else OnGreen
    val offText = if (dark) OffRedDark else OffRed
    val items = model.watched()
    Column(
        Modifier.fillMaxSize().background(paper).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(L10n.appTitle(), color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) {
            Text(L10n.addAddressInWindow(), color = muted, fontSize = 13.sp)
        }
        items.forEach { place ->
            val picture = model.snapshot?.let { ScheduleMath.picture(it, place, model.now) }
            val on = picture?.displayOn
            val color = when (on) {
                true -> onText
                false -> offText
                null -> ink
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(card).padding(12.dp)) {
                Text(place.placeLabel ?: L10n.groupTitle(place.group), color = muted, fontSize = 12.sp)
                Text(
                    when (on) {
                        true -> L10n.powerOn()
                        false -> L10n.powerOff()
                        null -> L10n.noData()
                    },
                    color = color,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                val hint = nextHint(picture, model.now)
                Text(hint.first, color = color, fontSize = 13.sp)
                hint.second?.let { Text(it, color = color, fontWeight = FontWeight.Bold) }
                if (picture != null && picture.outagesToday.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    DayTrack(
                        slices = daySlices(picture.outagesToday),
                        nowMin = ScheduleMath.minuteOfDay(model.now),
                        onText = onText,
                        offText = offText,
                        muted = muted,
                        nowColor = ink,
                    )
                }
            }
        }
    }
}
