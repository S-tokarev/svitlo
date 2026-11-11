import SwiftUI
import WidgetKit

struct ScaleSlice: Identifiable {
    var start: Int
    var end: Int
    var powerOn: Bool
    var id: Int { start }
}

struct WidgetEntry: TimelineEntry {
    var date: Date
    var background: Color
    var statusColor: Color
    var ink: Color
    var muted: Color
    var kicker: String
    var status: String
    var next: String
    var footer: String
    var slices: [ScaleSlice]
    var nowMin: Int?
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> WidgetEntry {
        entry(at: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (WidgetEntry) -> Void) {
        completion(entry(at: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WidgetEntry>) -> Void) {
        Copy.applyCode(AppStore.languageCode())
        let now = Date()
        var dates = (0..<120).map { now.addingTimeInterval(TimeInterval($0 * 60)) }
        if let snapshot = AppStore.loadSnapshot() {
            let selection = AppStore.loadSelection()
            let picture = ScheduleMath.picture(snapshot: snapshot, selection: selection, now: ScheduleMath.millis(now))
            for moment in [picture.nextOffAt, picture.nextOnAt].compactMap({ $0 }) {
                dates.append(ScheduleMath.date(from: moment))
            }
        }
        let unique = Array(Set(dates.map { Int($0.timeIntervalSince1970 / 60) })).sorted()
        let entries = unique.map { entry(at: Date(timeIntervalSince1970: TimeInterval($0 * 60))) }
        completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(15 * 60))))
    }

    private func entry(at date: Date) -> WidgetEntry {
        Copy.applyCode(AppStore.languageCode())
        let selection = AppStore.loadSelection()
        let snapshot = AppStore.loadSnapshot()
        guard !selection.group.isEmpty, let snapshot else {
            return WidgetEntry(
                date: date, background: Color(hex: 0x22201C), statusColor: Color(hex: 0xF6F1E7),
                ink: Color(hex: 0xF6F1E7), muted: Color(hex: 0xB6AB9C),
                kicker: Copy.appTitle(), status: Copy.chooseGroup(), next: Copy.openApp(),
                footer: "vyshgorod.in.ua/svitlo", slices: [], nowMin: nil
            )
        }
        let now = ScheduleMath.millis(date)
        let picture = ScheduleMath.picture(snapshot: snapshot, selection: selection, now: now)
        let place = selection.placeLabel
        let kicker = place ?? Copy.groupTitle(selection.group)
        let footer = Copy.widgetFooter(selection.group, ScheduleMath.formatDayTime(snapshot.fetchedAt, now: now))
        switch picture.displayOn {
        case true:
            let next: String
            if let off = picture.nextOffAt {
                next = Copy.offAtIn(ScheduleMath.formatDayTime(off, now: now), ScheduleMath.formatDuration(off - now))
            } else {
                next = Copy.noUpcomingOutage()
            }
            return WidgetEntry(
                date: date, background: Color(hex: 0xD7F6E4), statusColor: Color(hex: 0x0F6B38),
                ink: Color(hex: 0x143024), muted: Color(hex: 0x3D6B52),
                kicker: kicker, status: Copy.powerOn(), next: next, footer: footer,
                slices: scaleSlices(picture.outagesToday), nowMin: ScheduleMath.minuteOfDay(now)
            )
        case false:
            let next: String
            if let on = picture.nextOnAt {
                next = Copy.onAtIn(ScheduleMath.formatDayTime(on, now: now), ScheduleMath.formatDuration(on - now))
            } else if let until = picture.offUntil {
                next = Copy.offUntil(ScheduleMath.formatDuration(until - now))
            } else {
                next = Copy.unknownOnTime()
            }
            return WidgetEntry(
                date: date, background: Color(hex: 0xF8D5CC), statusColor: Color(hex: 0xA33E2C),
                ink: Color(hex: 0x3C120C), muted: Color(hex: 0x7A463C),
                kicker: kicker, status: Copy.powerOff(), next: next, footer: footer,
                slices: scaleSlices(picture.outagesToday), nowMin: ScheduleMath.minuteOfDay(now)
            )
        default:
            return WidgetEntry(
                date: date, background: Color(hex: 0x22201C), statusColor: Color(hex: 0xF6F1E7),
                ink: Color(hex: 0xF6F1E7), muted: Color(hex: 0xB6AB9C),
                kicker: kicker, status: Copy.noData(), next: Copy.refreshInApp(), footer: footer,
                slices: [], nowMin: nil
            )
        }
    }
}

struct SvitloWidgetView: View {
    var entry: WidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(entry.kicker).font(.caption).foregroundStyle(entry.muted).lineLimit(2)
            Text(entry.status).font(.title2.bold()).foregroundStyle(entry.statusColor)
            Text(entry.next).font(.subheadline).foregroundStyle(entry.ink).lineLimit(3)
            if !entry.slices.isEmpty {
                GeometryReader { geo in
                    VStack(alignment: .leading, spacing: 2) {
                        ZStack(alignment: .leading) {
                            HStack(spacing: 0) {
                                ForEach(entry.slices) { slice in
                                    Rectangle()
                                        .fill(slice.powerOn ? Color(hex: 0xF4F0E6) : Color(hex: 0x3C120C))
                                        .frame(width: max(0, geo.size.width * CGFloat(slice.end - slice.start) / 1440))
                                }
                            }
                            if let nowMin = entry.nowMin {
                                Rectangle()
                                    .fill(Color(hex: 0x1C6E8A))
                                    .frame(width: 2, height: 8)
                                    .offset(x: max(0, geo.size.width * CGFloat(nowMin) / 1440 - 1))
                            }
                        }
                        .frame(height: 8)
                        .clipShape(RoundedRectangle(cornerRadius: 3))
                        let labels = widgetHourLabels(entry.slices)
                        let widths = labels.map { CGFloat($0.text.count) * 6 + 1 }
                        let lefts = ScheduleMath.placeHourLabels(minutes: labels.map(\.minute), widths: widths, track: geo.size.width, gap: 2)
                        ZStack(alignment: .topLeading) {
                            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                                Text(label.text)
                                    .font(.system(size: 9, weight: .semibold))
                                    .foregroundStyle(entry.ink)
                                    .fixedSize()
                                    .offset(x: lefts[index])
                            }
                        }
                        .frame(height: 12)
                    }
                }
                .frame(height: 22)
            }
            Spacer(minLength: 0)
            Text(entry.footer).font(.caption2).foregroundStyle(entry.muted).lineLimit(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .containerBackground(entry.background, for: .widget)
    }
}

struct SvitloWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "svitlo", provider: Provider()) { entry in
            SvitloWidgetView(entry: entry)
        }
        .configurationDisplayName("Графік світла")
        .description("Стан світла обраної групи за графіком ДТЕК")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct SvitloWidgetBundle: WidgetBundle {
    var body: some Widget { SvitloWidget() }
}

private func widgetHourLabels(_ slices: [ScaleSlice]) -> [(minute: Int, text: String)] {
    var labels: [(minute: Int, text: String)] = [(0, "00")]
    for slice in slices where (1..<1440).contains(slice.start) {
        let text = slice.start % 60 == 0 ? String(format: "%02d", slice.start / 60) : String(format: "%02d:%02d", slice.start / 60, slice.start % 60)
        labels.append((slice.start, text))
    }
    labels.append((1440, "24"))
    return labels
}

private func scaleSlices(_ outages: [Outage]) -> [ScaleSlice] {
    let clipped = outages.compactMap { outage -> (Int, Int)? in
        let start = min(1440, max(0, outage.startMin))
        let end = min(1440, max(0, outage.endMin))
        return end > start ? (start, end) : nil
    }.sorted { $0.0 < $1.0 }
    var slices: [ScaleSlice] = []
    var cursor = 0
    for (start, end) in clipped {
        if start > cursor { slices.append(ScaleSlice(start: cursor, end: start, powerOn: true)) }
        let from = max(start, cursor)
        if end > from { slices.append(ScaleSlice(start: from, end: end, powerOn: false)) }
        cursor = max(cursor, end)
    }
    if cursor < 1440 { slices.append(ScaleSlice(start: cursor, end: 1440, powerOn: true)) }
    return slices
}
