import Foundation

/// Public YASNO schedule for Kyiv city and Dnipro.
/// An empty slot list is not a plan. NotPlanned means the power stays on.
enum Yasno {
    static let source = "yasno"

    private struct Feed {
        var regionId: Int
        var dsoId: String
        var name: String
    }

    private struct Side {
        var queue: String
        var plan: [Outage]
        var date: String?
    }

    static func covers(_ region: String) -> Bool {
        !feeds(region).isEmpty
    }

    static func page(for region: String) -> URL {
        let raw = Regions.normalize(region) == Regions.kyiv
            ? "https://static.yasno.ua/kyiv/outages"
            : "https://static.yasno.ua/dnipro/outages"
        return URL(string: raw)!
    }

    /// Nil when this region is not published by YASNO.
    static func load(region: String, operatorId: String = "") async throws -> ParsedPage? {
        let available = feeds(region)
        if available.isEmpty { return nil }
        let chosen: Feed
        if operatorId.isEmpty {
            guard let first = available.first else { return nil }
            chosen = first
        } else if let match = available.first(where: { $0.dsoId == operatorId }) {
            chosen = match
        } else {
            return emptyPage()
        }
        let body = try await SvitloClient.fetchPublic(url(chosen))
        return parse(
            body,
            operatorId: chosen.dsoId,
            operators: available.map { GridOperator(id: $0.dsoId, name: $0.name) }
        )
    }

    static func parse(_ json: String, operatorId: String, operators: [GridOperator]) -> ParsedPage {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return emptyPage() }
        var todays: [Side] = []
        var tomorrows: [Side] = []
        var stamps: [String: String] = [:]
        for (queue, value) in root {
            guard isQueue(queue), let group = value as? [String: Any] else { continue }
            let updated = stamp(text(group["updatedOn"]))
            if !updated.isEmpty && stamps[queue] == nil { stamps[queue] = updated }
            if let today = group["today"] as? [String: Any], let plan = plan(today) {
                todays.append(Side(queue: queue, plan: plan, date: civil(text(today["date"]))))
            }
            if let tomorrow = group["tomorrow"] as? [String: Any], let plan = plan(tomorrow) {
                tomorrows.append(Side(queue: queue, plan: plan, date: civil(text(tomorrow["date"]))))
            }
        }
        let todayDate = todays.compactMap(\.date).min()
        let todayGroups = sides(todays, date: todayDate, allowUndated: todayDate == nil)
        let tomorrowDate = tomorrows.compactMap(\.date).filter { todayDate == nil || $0 > todayDate! }.min()
        let tomorrowGroups = sides(tomorrows, date: tomorrowDate, allowUndated: false).filter { !$0.value.isEmpty }
        var days: [DaySchedule] = []
        if let todayDate, !todayGroups.isEmpty, let date = date(from: todayDate) {
            days.append(day("today", date, todayGroups, stamps))
        }
        if let tomorrowDate, !tomorrowGroups.isEmpty, let date = date(from: tomorrowDate) {
            days.append(day("tomorrow", date, tomorrowGroups, stamps))
        }
        if days.isEmpty { return emptyPage() }
        return ParsedPage(
            days: days,
            live: [],
            houses: [],
            tomorrowMissing: !days.contains { $0.day == "tomorrow" },
            note: "",
            source: source,
            operatorId: operatorId,
            operators: operators
        )
    }

    private static func feeds(_ region: String) -> [Feed] {
        switch Regions.normalize(region) {
        case Regions.kyiv:
            return [Feed(regionId: 25, dsoId: "902", name: "ТОВ «ДТЕК КИЇВСЬКІ ЕЛЕКТРОМЕРЕЖІ»")]
        case "dnipropetrovska-oblast":
            return [Feed(regionId: 3, dsoId: "301", name: "ДнЕМ"), Feed(regionId: 3, dsoId: "303", name: "ЦЕК")]
        default:
            return []
        }
    }

    /// Keeps groups of one civil date. A same-date tomorrow is not a second day.
    private static func sides(_ items: [Side], date: String?, allowUndated: Bool) -> [String: [Outage]] {
        var groups: [String: [Outage]] = [:]
        for item in items {
            let matches: Bool
            if let date {
                matches = item.date == date
            } else {
                matches = allowUndated && item.date == nil
            }
            if matches && groups[item.queue] == nil { groups[item.queue] = item.plan }
        }
        return groups
    }

    private static func url(_ feed: Feed) -> URL {
        URL(string: "https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/\(feed.regionId)/dsos/\(feed.dsoId)/planned-outages")!
    }

    /// Nil means this side has no confirmed plan. An empty list means the power stays on.
    private static func plan(_ day: [String: Any]) -> [Outage]? {
        let status = text(day["status"])
        if status.caseInsensitiveCompare("WaitingForSchedule") == .orderedSame { return nil }
        let pieces = slots(day)
        if status.caseInsensitiveCompare("EmergencyShutdowns") == .orderedSame && pieces.isEmpty { return nil }
        if day["slots"] == nil && pieces.isEmpty { return nil }
        return pieces
    }

    private static func slots(_ day: [String: Any]) -> [Outage] {
        guard let array = day["slots"] as? [Any] else { return [] }
        var pieces: [(Int, Int)] = []
        for item in array {
            guard let slot = item as? [String: Any] else { continue }
            let type = text(slot["type"])
            let definite = type.caseInsensitiveCompare("Definite") == .orderedSame
            let possible = type.caseInsensitiveCompare("Possible") == .orderedSame
            if !definite && !possible { continue }
            guard let start = intValue(slot["start"]), let end = intValue(slot["end"]), start >= 0, end > start else { continue }
            pieces.append((min(start, 1440), min(end, 1440)))
        }
        return merge(pieces)
    }

    private static func merge(_ pieces: [(Int, Int)]) -> [Outage] {
        var merged: [Outage] = []
        for (start, end) in pieces.sorted(by: { $0.0 < $1.0 }) {
            if let last = merged.last, last.endMin >= start {
                merged[merged.count - 1] = Outage(startMin: last.startMin, endMin: max(last.endMin, end))
            } else {
                merged.append(Outage(startMin: start, endMin: end))
            }
        }
        return merged
    }

    private static func day(
        _ name: String,
        _ date: Date,
        _ groups: [String: [Outage]],
        _ stamps: [String: String]
    ) -> DaySchedule {
        let ordered = groups.keys.sorted { left, right in
            let a = ScheduleMath.groupRank(left)
            let b = ScheduleMath.groupRank(right)
            if a.0 != b.0 { return a.0 < b.0 }
            if a.1 != b.1 { return a.1 < b.1 }
            return left < right
        }
        var outages: [String: [Outage]] = [:]
        var updated: [String: String] = [:]
        for queue in ordered {
            outages[queue] = groups[queue] ?? []
            updated[queue] = stamps[queue] ?? ""
        }
        return DaySchedule(
            day: name,
            label: ScheduleMath.dayLabel(date, language: .uk),
            updatedDate: formatDay(date),
            updatedTime: "",
            groups: outages,
            groupUpdated: updated
        )
    }

    private static func isQueue(_ value: String) -> Bool {
        value.range(of: #"^\d{1,3}(\.\d{1,2})?$"#, options: .regularExpression) != nil
    }

    private static func civil(_ raw: String) -> String? {
        guard raw.count >= 10 else { return nil }
        let key = String(raw.prefix(10))
        let parts = key.split(separator: "-")
        guard parts.count == 3, Int(parts[0]) != nil, Int(parts[1]) != nil, Int(parts[2]) != nil else { return nil }
        return key
    }

    private static func date(from key: String) -> Date? {
        let parts = key.split(separator: "-")
        guard parts.count == 3, let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2]) else { return nil }
        var components = DateComponents()
        components.calendar = ScheduleMath.calendar()
        components.timeZone = ScheduleMath.zone
        components.year = year
        components.month = month
        components.day = day
        components.hour = 12
        return ScheduleMath.calendar().date(from: components)
    }

    private static func stamp(_ raw: String) -> String {
        guard let date = parseInstant(raw) else { return "" }
        let formatter = DateFormatter()
        formatter.calendar = ScheduleMath.calendar()
        formatter.timeZone = ScheduleMath.zone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "dd.MM.yyyy HH:mm"
        return formatter.string(from: date)
    }

    private static func parseInstant(_ value: String) -> Date? {
        guard !value.isEmpty else { return nil }
        let fraction = ISO8601DateFormatter()
        fraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fraction.date(from: value) { return date }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        return plain.date(from: value)
    }

    private static func formatDay(_ date: Date) -> String {
        let parts = ScheduleMath.calendar().dateComponents([.day, .month, .year], from: date)
        return String(format: "%02d.%02d.%04d", parts.day ?? 0, parts.month ?? 0, parts.year ?? 0)
    }

    private static func text(_ value: Any?) -> String {
        guard let value, !(value is NSNull) else { return "" }
        if let text = value as? String { return text.trimmingCharacters(in: .whitespacesAndNewlines) }
        return ""
    }

    private static func intValue(_ value: Any?) -> Int? {
        if let number = value as? NSNumber { return number.intValue }
        if let text = value as? String { return Int(text) }
        return nil
    }

    private static func emptyPage() -> ParsedPage {
        ParsedPage(days: [], live: [], houses: [], tomorrowMissing: true, note: "")
    }
}
