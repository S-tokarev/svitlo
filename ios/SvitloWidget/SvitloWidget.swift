import SwiftUI
import WidgetKit

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
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> WidgetEntry {
        entry(at: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (WidgetEntry) -> Void) {
        completion(entry(at: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WidgetEntry>) -> Void) {
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
        completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(30 * 60))))
    }

    private func entry(at date: Date) -> WidgetEntry {
        let selection = AppStore.loadSelection()
        let snapshot = AppStore.loadSnapshot()
        guard !selection.group.isEmpty, let snapshot else {
            return WidgetEntry(
                date: date, background: Color(hex: 0x22201C), statusColor: Color(hex: 0xF6F1E7),
                ink: Color(hex: 0xF6F1E7), muted: Color(hex: 0xB6AB9C),
                kicker: "Светло", status: "Выберите группу", next: "Откройте приложение",
                footer: "vyshgorod.in.ua/svitlo"
            )
        }
        let now = ScheduleMath.millis(date)
        let picture = ScheduleMath.picture(snapshot: snapshot, selection: selection, now: now)
        let place = selection.placeLabel
        let kicker = place ?? "Группа \(selection.group)"
        let footer = "Группа \(selection.group) · обновлено \(ScheduleMath.formatDayTime(snapshot.fetchedAt, now: now))"
        switch picture.displayOn {
        case true:
            let next: String
            if let off = picture.nextOffAt {
                next = "По графику выключат в \(ScheduleMath.formatDayTime(off, now: now)) · через \(ScheduleMath.formatDuration(off - now))"
            } else {
                next = "Отключений по графику нет"
            }
            return WidgetEntry(
                date: date, background: Color(hex: 0xD7F6E4), statusColor: Color(hex: 0x0F6B38),
                ink: Color(hex: 0x143024), muted: Color(hex: 0x3D6B52),
                kicker: kicker, status: "Свет есть", next: next, footer: footer
            )
        case false:
            let next: String
            if let on = picture.nextOnAt {
                next = "По графику включат в \(ScheduleMath.formatDayTime(on, now: now)) · через \(ScheduleMath.formatDuration(on - now))"
            } else {
                next = "Время включения неизвестно"
            }
            return WidgetEntry(
                date: date, background: Color(hex: 0xF8D5CC), statusColor: Color(hex: 0xA33E2C),
                ink: Color(hex: 0x3C120C), muted: Color(hex: 0x7A463C),
                kicker: kicker, status: "Света нет", next: next, footer: footer
            )
        default:
            return WidgetEntry(
                date: date, background: Color(hex: 0x22201C), statusColor: Color(hex: 0xF6F1E7),
                ink: Color(hex: 0xF6F1E7), muted: Color(hex: 0xB6AB9C),
                kicker: kicker, status: "Нет данных", next: "Обновите график в приложении", footer: footer
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
        .configurationDisplayName("Светло")
        .description("Статус света выбранной группы по графику ДТЕК")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct SvitloWidgetBundle: WidgetBundle {
    var body: some Widget { SvitloWidget() }
}
