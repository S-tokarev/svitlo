import Foundation

/// Group schedule from the DTEK Kyiv regional networks ajax answer.
/// Hour 1 is 00:00–01:00. "yes" is power on, "no" is off, "first" and "second" are halves.
enum DtekSchedule {
    static let source = "dtek"
    static let page = URL(string: "https://www.dtek-krem.com.ua/ua/shutdowns")!

    static func parse(_ json: String) -> ParsedPage {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              flag(root["result"]),
              let fact = root["fact"] as? [String: Any],
              let days = fact["data"] as? [String: Any] else { return emptyPage() }
        let todayMarker = (fact["today"] as? NSNumber)?.int64Value ?? 0
        let stamp = text(fact["update"])
        var byDate: [String: [String: [Outage]]] = [:]
        for (key, value) in days {
            guard let epoch = Int64(key), let groups = value as? [String: Any] else { continue }
            let date = dayKey(epoch)
            var parsed = byDate[date] ?? [:]
            for (raw, hoursValue) in groups {
                guard let queue = queue(raw), let hours = hoursValue as? [String: Any] else { continue }
                if parsed[queue] == nil { parsed[queue] = outages(hours) }
            }
            byDate[date] = parsed
        }
        let marker = todayMarker > 0 ? dayKey(todayMarker) : nil
        let today = byDate.keys.contains(marker ?? "") ? marker : byDate.keys.min()
        let tomorrow = byDate.keys.filter { today == nil || $0 > today! }.min()
        var schedules: [DaySchedule] = []
        if let today, let groups = byDate[today], !groups.isEmpty, let date = date(from: today) {
            schedules.append(day("today", date, groups, stamp))
        }
        if let tomorrow, let groups = byDate[tomorrow], let date = date(from: tomorrow) {
            let published = groups.filter { !$0.value.isEmpty }
            if !published.isEmpty {
                schedules.append(day("tomorrow", date, published, stamp))
            }
        }
        if schedules.isEmpty { return emptyPage() }
        return ParsedPage(
            days: schedules,
            live: [],
            houses: [],
            tomorrowMissing: !schedules.contains { $0.day == "tomorrow" },
            note: "",
            source: source,
            region: Regions.kyivOblast,
            operatorId: AlertsEnergy.kyivOblastDtek,
            operators: [GridOperator(id: AlertsEnergy.kyivOblastDtek, name: "ДТЕК Київські регіональні електромережі")]
        )
    }

    private static func queue(_ raw: String) -> String? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let regex = try? NSRegularExpression(pattern: #"^(?:GPV)?(\d{1,3}(?:\.\d{1,2})?)$"#, options: [.caseInsensitive]) else {
            return nil
        }
        let range = NSRange(value.startIndex..., in: value)
        guard let match = regex.firstMatch(in: value, range: range),
              match.numberOfRanges > 1,
              let group = Range(match.range(at: 1), in: value) else { return nil }
        return String(value[group])
    }

    /// Keys are hours 1...24. A missing hour is not an outage.
    private static func outages(_ hours: [String: Any]) -> [Outage] {
        var slots = [Bool](repeating: false, count: 48)
        for (key, value) in hours {
            guard let hour = Int(key), (1...24).contains(hour), let text = value as? String else { continue }
            mark(&slots, hour: hour - 1, value: text)
        }
        var result: [Outage] = []
        var start: Int?
        for index in 0..<48 {
            if slots[index] {
                if start == nil { start = index * 30 }
            } else if let from = start {
                result.append(Outage(startMin: from, endMin: index * 30))
                start = nil
            }
        }
        if let from = start { result.append(Outage(startMin: from, endMin: 1440)) }
        return result
    }

    private static func mark(_ slots: inout [Bool], hour: Int, value: String) {
        switch value.lowercased() {
        case "no", "maybe":
            slots[hour * 2] = true
            slots[hour * 2 + 1] = true
        case "first", "mfirst":
            slots[hour * 2] = true
        case "second", "msecond":
            slots[hour * 2 + 1] = true
        default:
            break
        }
    }

    private static func day(_ name: String, _ date: Date, _ groups: [String: [Outage]], _ stamp: String) -> DaySchedule {
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
            updated[queue] = stamp
        }
        let time = stamp.split(separator: " ").dropFirst().joined(separator: " ")
        return DaySchedule(
            day: name,
            label: ScheduleMath.dayLabel(date, language: .uk),
            updatedDate: formatDay(date),
            updatedTime: stamp.contains(" ") ? time : "",
            groups: outages,
            groupUpdated: updated
        )
    }

    private static func dayKey(_ epoch: Int64) -> String {
        let formatter = DateFormatter()
        formatter.calendar = ScheduleMath.calendar()
        formatter.timeZone = ScheduleMath.zone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date(timeIntervalSince1970: TimeInterval(epoch)))
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

    private static func formatDay(_ date: Date) -> String {
        let parts = ScheduleMath.calendar().dateComponents([.day, .month, .year], from: date)
        return String(format: "%02d.%02d.%04d", parts.day ?? 0, parts.month ?? 0, parts.year ?? 0)
    }

    private static func flag(_ value: Any?) -> Bool {
        if let flag = value as? Bool { return flag }
        if let number = value as? NSNumber { return number.boolValue }
        return false
    }

    private static func text(_ value: Any?) -> String {
        guard let text = value as? String else { return "" }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func emptyPage() -> ParsedPage {
        ParsedPage(days: [], live: [], houses: [], tomorrowMissing: true, note: "")
    }
}
