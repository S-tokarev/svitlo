import Foundation

enum SvitloParser {
    private static let stateKey = "api:/source-registry/areas/kyivska-oblast/shutdowns"
    private static let initiator = "kyivska_oblast_oblenergo"
    private static let queueId = try! NSRegularExpression(pattern: "^[1-6]\\.[12]$")

    static func parse(html: String) -> ParsedPage {
        guard let rows = extractRows(html) else { return emptyPage() }
        return parseRows(rows)
    }

    static func parseRows(_ rows: [Any]) -> ParsedPage {
        var todayGroups: [String: [Outage]] = [:]
        var todayUpdated: [String: String] = [:]
        var tomorrowGroups: [String: [Outage]] = [:]
        var tomorrowUpdated: [String: String] = [:]
        var todayDate = ""
        var tomorrowDate = ""
        var sawOperator = false
        var messages: [String] = []

        for item in rows {
            guard let row = item as? [String: Any] else { continue }
            guard string(row["initiator"]) == initiator else { continue }
            let queue = string(row["queue"])
            guard queueId.firstMatch(in: queue, range: NSRange(queue.startIndex..., in: queue)) != nil else { continue }
            sawOperator = true
            let stamp = formatStamp(string(row["updated"]))
            let rowToday = string(row["todayDate"])
            let rowTomorrow = string(row["tomorrowDate"])
            if todayDate.isEmpty { todayDate = rowToday }
            if tomorrowDate.isEmpty { tomorrowDate = rowTomorrow }
            if publish(row, "todayStatus", "today", "todayPossible", todayDate, "todayDate", &todayGroups, queue), let stamp {
                todayUpdated[queue] = stamp
            }
            if publish(row, "tomorrowStatus", "tomorrow", "tomorrowPossible", tomorrowDate, "tomorrowDate", &tomorrowGroups, queue), let stamp {
                tomorrowUpdated[queue] = stamp
            }
            collectMessages(row["todayAnnouncements"], into: &messages)
            collectMessages(row["tomorrowAnnouncements"], into: &messages)
        }

        guard sawOperator else { return emptyPage() }
        let today = daySchedule("today", todayDate, todayGroups, todayUpdated)
        let tomorrow = tomorrowGroups.isEmpty ? nil : daySchedule("tomorrow", tomorrowDate, tomorrowGroups, tomorrowUpdated)
        return ParsedPage(
            days: [today, tomorrow].compactMap { $0 },
            live: [],
            houses: [],
            tomorrowMissing: tomorrow == nil,
            note: dedupe(messages)
        )
    }

    static func toOutages(_ slots: [Bool]) -> [Outage] {
        var result: [Outage] = []
        var start = -1
        for index in 0...slots.count {
            let on = index < slots.count && slots[index]
            if on && start < 0 { start = index }
            if !on && start >= 0 {
                result.append(Outage(startMin: start * 30, endMin: index * 30))
                start = -1
            }
        }
        return result
    }

    private static func publish(
        _ row: [String: Any],
        _ statusKey: String,
        _ arrayKey: String,
        _ possibleKey: String,
        _ expectedDate: String,
        _ dateKey: String,
        _ groups: inout [String: [Outage]],
        _ queue: String
    ) -> Bool {
        let date = string(row[dateKey])
        guard !expectedDate.isEmpty, date == expectedDate else { return false }
        let slots: [Bool]?
        switch string(row[statusKey]) {
        case "ScheduleApplies":
            slots = combine(readCodes(row, arrayKey, required: true), readCodes(row, possibleKey, required: false), zerosOnly: false)
        case "NoShutdowns":
            slots = combine(readCodes(row, arrayKey, required: true), readCodes(row, possibleKey, required: false), zerosOnly: true)
        default:
            slots = nil
        }
        guard let slots else { return false }
        groups[queue] = toOutages(slots)
        return true
    }

    private enum Hours {
        case absent
        case unusable
        case emergency
        case ok([Bool])
    }

    private static func combine(_ planned: Hours, _ possible: Hours, zerosOnly: Bool) -> [Bool]? {
        guard case .ok(let plan) = planned else { return nil }
        if zerosOnly && plan.contains(true) { return nil }
        switch possible {
        case .absent:
            return plan
        case .ok(let extra):
            return zip(plan, extra).map { $0 || $1 }
        case .emergency, .unusable:
            return nil
        }
    }

    private static func readCodes(_ row: [String: Any], _ key: String, required: Bool) -> Hours {
        guard row.keys.contains(key) else { return required ? .unusable : .absent }
        guard let raw = row[key], !(raw is NSNull) else { return .unusable }
        guard let value = raw as? [Any] else { return .unusable }
        if value.isEmpty { return required ? .unusable : .absent }
        if value.count != 24 { return .unusable }
        var slots = Array(repeating: false, count: 48)
        var emergency = false
        for hour in 0..<24 {
            guard let code = codeAt(value, hour) else { return .unusable }
            switch code {
            case 0:
                break
            case 1:
                slots[hour * 2] = true
                slots[hour * 2 + 1] = true
            case 2:
                slots[hour * 2] = true
            case 3:
                slots[hour * 2 + 1] = true
            case 9:
                emergency = true
            default:
                return .unusable
            }
        }
        return emergency ? .emergency : .ok(slots)
    }

    private static func codeAt(_ array: [Any], _ index: Int) -> Int? {
        guard let number = array[index] as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        let code = number.intValue
        if number.doubleValue != Double(code) { return nil }
        return code
    }

    private static func collectMessages(_ raw: Any?, into messages: inout [String]) {
        guard let array = raw as? [Any] else { return }
        let now = Date()
        for item in array {
            guard let object = item as? [String: Any], string(object["state"]) == "active" else { continue }
            let expires = string(object["expiresAt"])
            if !expires.isEmpty {
                guard let until = ISO8601DateFormatter().date(from: expires), until > now else { continue }
            }
            let message = string(object["message"]).trimmingCharacters(in: .whitespacesAndNewlines)
            if !message.isEmpty { messages.append(message) }
        }
    }

    private static func dedupe(_ messages: [String]) -> String {
        var unique: [String] = []
        for message in messages where !unique.contains(message) {
            unique.append(message)
        }
        return unique
            .filter { message in !unique.contains { $0 != message && $0.contains(message) } }
            .joined(separator: "\n\n")
    }

    private static func daySchedule(
        _ day: String,
        _ iso: String,
        _ groups: [String: [Outage]],
        _ updated: [String: String]
    ) -> DaySchedule? {
        let formatter = DateFormatter()
        formatter.calendar = ScheduleMath.calendar()
        formatter.timeZone = ScheduleMath.zone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: iso) else { return nil }
        let label = DateFormatter()
        label.calendar = ScheduleMath.calendar()
        label.timeZone = ScheduleMath.zone
        label.locale = Locale(identifier: "ru_RU")
        label.dateFormat = "d MMMM"
        let dayDate = DateFormatter()
        dayDate.calendar = ScheduleMath.calendar()
        dayDate.timeZone = ScheduleMath.zone
        dayDate.locale = Locale(identifier: "en_US_POSIX")
        dayDate.dateFormat = "dd.MM.yyyy"
        return DaySchedule(
            day: day,
            label: label.string(from: date),
            updatedDate: dayDate.string(from: date),
            updatedTime: "",
            groups: groups,
            groupUpdated: updated
        )
    }

    private static func formatStamp(_ iso: String) -> String? {
        guard let instant = ISO8601DateFormatter.fractional.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) else {
            return nil
        }
        let day = DateFormatter()
        day.calendar = ScheduleMath.calendar()
        day.timeZone = ScheduleMath.zone
        day.locale = Locale(identifier: "en_US_POSIX")
        day.dateFormat = "dd.MM.yyyy"
        let clock = DateFormatter()
        clock.calendar = ScheduleMath.calendar()
        clock.timeZone = ScheduleMath.zone
        clock.locale = Locale(identifier: "en_US_POSIX")
        clock.dateFormat = "HH:mm"
        return "\(day.string(from: instant)) \(clock.string(from: instant))"
    }

    private static func extractRows(_ html: String) -> [Any]? {
        guard let idAt = html.range(of: "id=\"ng-state\"") else { return nil }
        let head = html[..<idAt.lowerBound]
        guard head.range(of: "<script", options: .backwards) != nil else { return nil }
        guard let jsonStart = html.range(of: ">", range: idAt.upperBound..<html.endIndex),
              let jsonEnd = html.range(of: "</script>", range: jsonStart.upperBound..<html.endIndex) else { return nil }
        var body = String(html[jsonStart.upperBound..<jsonEnd.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        if !body.hasPrefix("{") {
            body = body
                .replacingOccurrences(of: "&amp;", with: "&")
                .replacingOccurrences(of: "&quot;", with: "\"")
                .replacingOccurrences(of: "&#34;", with: "\"")
                .replacingOccurrences(of: "&lt;", with: "<")
                .replacingOccurrences(of: "&gt;", with: ">")
        }
        guard let data = body.data(using: .utf8),
              let state = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let payload = state[stateKey] as? [String: Any],
              let rows = payload["data"] as? [Any] else { return nil }
        return rows
    }

    private static func emptyPage() -> ParsedPage {
        ParsedPage(days: [], live: [], houses: [], tomorrowMissing: true, note: "")
    }

    private static func string(_ value: Any?) -> String {
        if let text = value as? String { return text }
        if let number = value as? NSNumber { return number.stringValue }
        return ""
    }
}

private extension ISO8601DateFormatter {
    static let fractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
