import SwiftUI

struct MainView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.openURL) private var openURL

    private var palette: SvitloPalette { SvitloPalette.resolve(model.theme, systemDark: colorScheme == .dark) }
    private var nowMs: Int64 { ScheduleMath.millis(model.now) }
    private var picture: PowerPicture? {
        guard let snapshot = model.snapshot, !model.selection.group.isEmpty else { return nil }
        return ScheduleMath.picture(snapshot: snapshot, selection: model.selection, now: nowMs)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                statusCard
                if let note = model.snapshot?.note, !note.isEmpty { noteCard(note) }
                if let error = model.error { errorCard(error) }
                schedule
                groups
                address
                appearance
                notifications
                Button(Copy.openScheduleShort()) { openURL(SvitloClient.page(for: model.selection.group)) }
                    .font(.subheadline)
                    .foregroundStyle(palette.accent)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(16)
        }
        .background(palette.paper.ignoresSafeArea())
        .onReceive(NotificationCenter.default.publisher(for: .svitloUpdated)) { _ in
            model.applyExternalUpdate()
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            Text(Copy.appTitle())
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(palette.ink)
            Spacer()
            if model.loading { ProgressView().tint(palette.accent) }
            Button(model.loading ? Copy.refreshing() : Copy.refresh()) { Task { await model.refresh() } }
                .buttonStyle(.borderedProminent)
                .tint(palette.accent)
                .disabled(model.loading)
        }
    }

    private var statusCard: some View {
        let hint = nextHint(picture, nowMs)
        let on = picture?.displayOn
        let color = on == true ? palette.powerOnText : on == false ? palette.powerOffText : palette.ink
        let title = on == true ? Copy.powerOn() : on == false ? Copy.powerOff() : (model.selection.group.isEmpty ? Copy.chooseGroup() : Copy.noData())
        return card {
            Text(heading)
                .font(.caption)
                .foregroundStyle(palette.muted)
            Text(title)
                .font(.system(size: 36, weight: .bold))
                .foregroundStyle(color)
            Text(hint.whenText)
                .font(.body)
                .foregroundStyle(palette.ink)
            if let duration = hint.duration {
                Text(duration)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(color)
            }
            if let fetched = model.snapshot?.fetchedAt, fetched > 0 {
                Text(Copy.updated(ScheduleMath.formatDayTime(fetched, now: nowMs)))
                    .font(.caption)
                    .foregroundStyle(palette.muted)
            }
        }
    }

    private var heading: String {
        Copy.placeHeading(model.selection.placeLabel, model.selection.group)
    }

    @ViewBuilder private var schedule: some View {
        if let picture, !picture.outagesToday.isEmpty || picture.plannedOn != nil {
            DayTrack(
                title: picture.scheduleLabel.isEmpty ? Copy.today() : picture.scheduleLabel,
                outages: picture.outagesToday,
                nowMin: ScheduleMath.minuteOfDay(nowMs),
                palette: palette
            )
        }
        if let picture, !picture.outagesTomorrow.isEmpty {
            DayTrack(title: Copy.tomorrow(), outages: picture.outagesTomorrow, nowMin: nil, palette: palette)
        } else if let snapshot = model.snapshot, ScheduleMath.groupHasTomorrow(snapshot: snapshot, group: model.selection.group) {
            DayTrack(title: Copy.tomorrow(), outages: [], nowMin: nil, palette: palette)
        }
    }

    private var groups: some View {
        let items = ScheduleMath.knownGroups(model.snapshot)
        return card {
            Text(Copy.groupSection()).font(.headline).foregroundStyle(palette.ink)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                ForEach(items, id: \.self) { group in
                    let selected = group == model.selection.group
                    let on = model.snapshot.flatMap { ScheduleMath.picture(snapshot: $0, selection: Selection(group: group), now: nowMs).displayOn }
                    Button(group) { model.selectGroup(group) }
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(chipColor(selected: selected, on: on), in: RoundedRectangle(cornerRadius: 12))
                        .foregroundStyle(selected ? palette.ink : palette.muted)
                }
            }
        }
    }

    private var address: some View {
        card {
            Text(Copy.addresses()).font(.headline).foregroundStyle(palette.ink)
            Text(Copy.addressesHint())
                .font(.caption)
                .foregroundStyle(palette.muted)
            ForEach(model.places, id: \.placeKey) { place in
                let on = model.snapshot.flatMap { ScheduleMath.picture(snapshot: $0, selection: place, now: nowMs).displayOn }
                let selected = place.placeKey == model.selection.placeKey
                HStack {
                    Button {
                        model.focusPlace(place)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(place.placeLabel ?? Copy.groupTitle(place.group))
                                .foregroundStyle(palette.ink)
                            Text(Copy.savedPlaceStatus(place.group, on))
                                .font(.caption)
                                .foregroundStyle(on == true ? palette.powerOnText : on == false ? palette.powerOffText : palette.muted)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Button(Copy.remove()) { model.removePlace(place) }
                        .font(.caption)
                        .foregroundStyle(palette.muted)
                }
                .padding(8)
                .background(selected ? palette.card2 : Color.clear, in: RoundedRectangle(cornerRadius: 12))
            }
            TextField(Copy.addressHint(), text: $model.query)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(12)
                .background(palette.field, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(palette.line))
            Button(model.searching ? Copy.searching() : Copy.search()) { Task { await model.search() } }
                .buttonStyle(.bordered)
                .disabled(model.searching)
            if let searchError = model.searchError {
                Text(searchError).font(.caption).foregroundStyle(palette.muted)
            }
            ForEach(model.results.prefix(40)) { hit in
                Button {
                    Task { await model.pick(hit) }
                } label: {
                    Text(Copy.addressHit(hit.label, hit.group))
                        .font(.subheadline)
                        .foregroundStyle(palette.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(10)
                        .background(palette.card2, in: RoundedRectangle(cornerRadius: 12))
                }
            }
        }
    }

    private var appearance: some View {
        card {
            Text(Copy.appearance()).font(.headline).foregroundStyle(palette.ink)
            HStack {
                ForEach(ThemeMode.allCases) { mode in
                    Button(mode.title) { model.setTheme(mode) }
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(model.theme == mode ? palette.card2 : Color.clear, in: RoundedRectangle(cornerRadius: 12))
                        .foregroundStyle(model.theme == mode ? palette.ink : palette.muted)
                }
            }
        }
    }

    private var notifications: some View {
        card {
            Text(Copy.notifications()).font(.headline).foregroundStyle(palette.ink)
            toggle(Copy.warnBefore(), model.warnEnabled) { model.setWarn($0) }
            toggle(Copy.notifyOn(), model.notifyOn) { model.setNotify($0) }
            toggle(Copy.quietNight(), model.quietNight) { model.setQuiet($0) }
            Text(Copy.quietHint())
                .font(.caption)
                .foregroundStyle(palette.muted)
        }
    }

    private func toggle(_ title: String, _ value: Bool, _ set: @escaping (Bool) -> Void) -> some View {
        Toggle(title, isOn: Binding(get: { value }, set: set))
            .font(.subheadline)
            .foregroundStyle(palette.ink)
            .tint(palette.accent)
    }

    private func noteCard(_ note: String) -> some View {
        card {
            Text(Copy.operatorNote()).font(.caption.weight(.semibold)).foregroundStyle(palette.amber)
            Text(note).foregroundStyle(palette.ink)
        }
    }

    private func errorCard(_ text: String) -> some View {
        card { Text(text).foregroundStyle(palette.powerOffText) }
    }

    private func card<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8, content: content)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(palette.card, in: RoundedRectangle(cornerRadius: 18))
    }

    private func chipColor(selected: Bool, on: Bool?) -> Color {
        guard selected else { return palette.card2 }
        if on == true { return palette.powerOn }
        if on == false { return palette.powerOff }
        return palette.card2
    }
}

private struct Hint {
    var whenText: String
    var duration: String?
}

private func nextHint(_ picture: PowerPicture?, _ now: Int64) -> Hint {
    guard let picture else { return Hint(whenText: Copy.scheduleWillLoad(), duration: nil) }
    if picture.displayOn == true, let off = picture.nextOffAt {
        return Hint(whenText: Copy.offAt(ScheduleMath.formatDayTime(off, now: now)), duration: Copy.through(ScheduleMath.formatDuration(off - now)))
    }
    if picture.displayOn == true { return Hint(whenText: Copy.noUpcomingOutageLong(), duration: nil) }
    if picture.displayOn == false, let back = picture.nextOnAt {
        return Hint(whenText: Copy.onAt(ScheduleMath.formatDayTime(back, now: now)), duration: Copy.through(ScheduleMath.formatDuration(back - now)))
    }
    if picture.displayOn == false, let until = picture.offUntil {
        return Hint(whenText: Copy.offUntilMidnight(), duration: Copy.through(ScheduleMath.formatDuration(until - now)))
    }
    if picture.displayOn == false { return Hint(whenText: Copy.onTimeUnknown(), duration: nil) }
    return Hint(whenText: Copy.statusUnknown(), duration: nil)
}

struct DaySlice: Identifiable {
    var start: Int
    var end: Int
    var powerOn: Bool
    var id: Int { start }
    var length: Int { end - start }
}

func daySlices(_ outages: [Outage]) -> [DaySlice] {
    var cursor = 0
    var slices: [DaySlice] = []
    for outage in outages.sorted(by: { $0.startMin < $1.startMin }) {
        let start = min(1440, max(0, outage.startMin))
        let end = min(1440, max(start, outage.endMin))
        if start > cursor { slices.append(DaySlice(start: cursor, end: start, powerOn: true)) }
        if end > start { slices.append(DaySlice(start: start, end: end, powerOn: false)) }
        cursor = max(cursor, end)
    }
    if cursor < 1440 { slices.append(DaySlice(start: cursor, end: 1440, powerOn: true)) }
    return slices
}

struct DayTrack: View {
    var title: String
    var outages: [Outage]
    var nowMin: Int?
    var palette: SvitloPalette

    var body: some View {
        let slices = daySlices(outages)
        let onMinutes = slices.filter(\.powerOn).reduce(0) { $0 + $1.length }
        let offMinutes = slices.filter { !$0.powerOn }.reduce(0) { $0 + $1.length }
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title).font(.headline).foregroundStyle(palette.ink)
                Spacer()
                Text("\(Copy.lightWord()) \(compact(onMinutes))").font(.caption.weight(.semibold)).foregroundStyle(palette.powerOnText)
                Text("\(Copy.offWord()) \(compact(offMinutes))").font(.caption.weight(.semibold)).foregroundStyle(palette.powerOffText)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    HStack(spacing: 0) {
                        ForEach(slices) { slice in
                            ZStack {
                                Rectangle().fill(slice.powerOn ? palette.powerOn : palette.powerOff)
                                if slice.length >= 90 {
                                    Text(compact(slice.length))
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(slice.powerOn ? palette.onPowerOn : palette.onPowerOff)
                                }
                            }
                            .frame(width: geo.size.width * CGFloat(slice.length) / 1440)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    if let nowMin {
                        Rectangle()
                            .fill(palette.now)
                            .frame(width: 2, height: 44)
                            .offset(x: geo.size.width * CGFloat(nowMin) / 1440 - 1)
                    }
                }
            }
            .frame(height: 44)
            marks(slices)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.card, in: RoundedRectangle(cornerRadius: 18))
    }

    private func marks(_ slices: [DaySlice]) -> some View {
        let switches = slices.compactMap { slice -> (minute: Int, powerOn: Bool)? in
            guard (1..<1440).contains(slice.start) else { return nil }
            return (slice.start, slice.powerOn)
        }
        return GeometryReader { geo in
            let placed = placedHours(switches, width: geo.size.width)
            ZStack(alignment: .topLeading) {
                ForEach(placed, id: \.minute) { item in
                    Text(item.text)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(item.powerOn.map { $0 ? palette.powerOnText : palette.powerOffText } ?? palette.muted)
                        .fixedSize()
                        .offset(x: item.left)
                }
            }
        }
        .frame(height: 18)
    }

    private func shortHour(_ minute: Int) -> String {
        minute % 60 == 0 ? String(format: "%02d", minute / 60) : String(format: "%02d:%02d", minute / 60, minute % 60)
    }

    private func placedHours(_ switches: [(minute: Int, powerOn: Bool)], width: CGFloat) -> [(minute: Int, text: String, powerOn: Bool?, left: CGFloat)] {
        var items: [(minute: Int, text: String, powerOn: Bool?)] = [(0, "00", nil)]
        items += switches.map { ($0.minute, shortHour($0.minute), Optional($0.powerOn)) }
        items.append((1440, "24", nil))
        let widths = items.map { CGFloat($0.text.count) * 7 + 2 }
        let lefts = ScheduleMath.placeHourLabels(minutes: items.map(\.minute), widths: widths, track: width, gap: 3)
        return zip(items, lefts).map { item, left in
            (item.minute, item.text, item.powerOn, left)
        }
    }

    private func compact(_ minutes: Int) -> String {
        Copy.duration(hours: minutes / 60, minutes: minutes % 60)
    }
}
