import Foundation

/// Reads a public alerts.energy region page.
/// Household operators are kept. Ukrzaliznytsia shares group numbers and is ignored.
/// An empty hour array is not a plan, and it does not mean the power is on.
enum AlertsEnergy {
    static let source = "alerts.energy"
    static let kyivOblastDtek = "kyivska_oblast_oblenergo"

    static func page(for region: String) -> URL {
        URL(string: "https://alerts.energy/\(Regions.normalize(region))")!
    }

    private struct Piece {
        var outages: [Outage]
        var stamp: String
    }

    private struct Supplier {
        var id: String
        var name: String
        var type: String
        var priority: Int
    }

    static func parse(html: String, operatorId: String = "") -> ParsedPage {
        guard let json = stateJson(html),
              let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return emptyPage() }
        return parseRoot(root, operatorId: operatorId)
    }

    /// Shutdown and supplier arrays from the documented JSON API. No network.
    static func parseApi(shutdowns: String, suppliers: String, operatorId: String = "") -> ParsedPage {
        guard let shutdownData = shutdowns.data(using: .utf8),
              let shutdownRows = try? JSONSerialization.jsonObject(with: shutdownData) as? [Any] else {
            return emptyPage()
        }
        let supplierRows: [Any]
        if let data = suppliers.data(using: .utf8),
           let rows = try? JSONSerialization.jsonObject(with: data) as? [Any] {
            supplierRows = rows
        } else {
            supplierRows = []
        }
        let root: [String: Any] = [
            "shutdowns": ["data": shutdownRows],
            "suppliers": ["data": supplierRows],
        ]
        return parseRoot(root, operatorId: operatorId)
    }

    private static func parseRoot(_ root: [String: Any], operatorId: String) -> ParsedPage {
        guard let rows = findRows(root) else { return emptyPage() }

        let listed = suppliers(root)
        var initiators = Set<String>()
        for row in rows {
            let queue = text(row, "queue")
            guard isQueue(queue) else { continue }
            let initiator = text(row, "initiator")
            if !initiator.isEmpty { initiators.insert(initiator) }
        }
        let operator = choose(listed, initiators, operatorId.trimmingCharacters(in: .whitespacesAndNewlines))
        if operator.isEmpty { return emptyPage() }

        var todayDate: Date?
        var tomorrowDate: Date?
        var todayGroups: [String: Piece] = [:]
        var tomorrowGroups: [String: Piece] = [:]
        var notes: [String] = []
        var seenNotes = Set<String>()

        for row in rows {
            if text(row, "initiator") != operator { continue }
            let queue = text(row, "queue")
            guard isQueue(queue) else { continue }
            if todayDate == nil { todayDate = civil(text(row, "todayDate", "today_date")) }
            if tomorrowDate == nil { tomorrowDate = civil(text(row, "tomorrowDate", "tomorrow_date")) }
            let stamp = stamp(text(row, "updated"))
            let messages = messages(row, "todayAnnouncements", "today_announcements")
                + messages(row, "tomorrowAnnouncements", "tomorrow_announcements")
            let todayPlan = plan(row, "today")
            let tomorrowPlan = plan(row, "tomorrow")
            let today = todayDate
            let tomorrow = tomorrowDate
            if let todayPlan, todayGroups[queue] == nil {
                todayGroups[queue] = Piece(outages: todayPlan, stamp: stamp)
                remember(messages, into: &notes, seen: &seenNotes)
            }
            let tomorrowFollows = tomorrow != nil && (today == nil || isAfter(tomorrow!, today!))
            if tomorrowFollows, let tomorrowPlan, tomorrowGroups[queue] == nil {
                tomorrowGroups[queue] = Piece(outages: tomorrowPlan, stamp: stamp)
                remember(messages, into: &notes, seen: &seenNotes)
            }
        }

        let today = todayDate
        let tomorrow = tomorrowDate
        var days: [DaySchedule] = []
        if let today, !todayGroups.isEmpty {
            days.append(day("today", today, todayGroups))
        }
        if let tomorrow, !tomorrowGroups.isEmpty, today == nil || isAfter(tomorrow, today!) {
            days.append(day("tomorrow", tomorrow, tomorrowGroups))
        }
        if days.isEmpty { return emptyPage() }
        return ParsedPage(
            days: days,
            live: [],
            houses: [],
            tomorrowMissing: !days.contains { $0.day == "tomorrow" },
            note: String(notes.joined(separator: "\n").prefix(800)),
            source: source,
            operatorId: operator,
            operators: options(listed, initiators)
        )
    }

    private static func choose(_ suppliers: [Supplier], _ initiators: Set<String>, _ preferred: String) -> String {
        if !preferred.isEmpty { return initiators.contains(preferred) ? preferred : "" }
        if let match = suppliers.filter({ $0.type != "uz" }).sorted(by: { $0.priority > $1.priority }).first(where: { initiators.contains($0.id) }) {
            return match.id
        }
        let household = initiators.filter { !$0.hasSuffix("_uz") }.sorted()
        return household.first { $0.contains("oblenergo") } ?? household.first ?? ""
    }

    private static func options(_ suppliers: [Supplier], _ initiators: Set<String>) -> [GridOperator] {
        let listed = suppliers.filter { $0.type != "uz" }.sorted { $0.priority > $1.priority }
        if !listed.isEmpty { return listed.map { GridOperator(id: $0.id, name: $0.name) } }
        return initiators.filter { !$0.hasSuffix("_uz") }.sorted().map { GridOperator(id: $0, name: $0) }
    }

    private static func suppliers(_ root: [String: Any]) -> [Supplier] {
        for (key, value) in root where key.contains("suppliers") {
            guard let object = value as? [String: Any], let data = object["data"] as? [Any] else { continue }
            return data.compactMap { item in
                guard let row = item as? [String: Any] else { return nil }
                let id = text(row, "supplierKey", "supplier_key", "slug")
                if id.isEmpty { return nil }
                let priority = (row["priority"] as? NSNumber)?.intValue ?? 0
                let name = text(row, "supplierName", "supplier_name")
                return Supplier(id: id, name: name.isEmpty ? id : name, type: text(row, "supplierType", "supplier_type"), priority: priority)
            }
        }
        return []
    }

    private static func remember(_ messages: [String], into notes: inout [String], seen: inout Set<String>) {
        for message in messages where seen.insert(message).inserted {
            notes.append(message)
        }
    }

    /// Nil means this side has no confirmed plan. An empty list means a published plan with no outage.
    private static func plan(_ row: [String: Any], _ which: String) -> [Outage]? {
        let status = text(row, "\(which)Status", "\(which)_status")
        let planned = ints(row[which])
        let extra = possible(row, which)
        let hours: [Int]?
        switch status {
        case "NoShutdowns":
            hours = planned.count == 24 ? planned : Array(repeating: 0, count: 24)
        case "ScheduleApplies", "EmergencyShutdowns", "":
            hours = planned.count == 24 ? planned : nil
        default:
            hours = nil
        }
        guard let hours else { return nil }
        return outages(planned: hours, possible: extra)
    }

    private static func possible(_ row: [String: Any], _ which: String) -> [Int] {
        if let direct = row["\(which)Possible"] ?? row["\(which)_possible"], !(direct is NSNull) {
            return ints(direct)
        }
        let published = (row["\(which)PublishedSchedule"] as? [String: Any])
            ?? (row["\(which)_published_schedule"] as? [String: Any])
        return ints(published?["possible"])
    }

    /// 0 is clear, 1 is the whole hour, 2 is the first half, 3 is the second. Possible hours count too.
    private static func outages(planned: [Int], possible: [Int]) -> [Outage] {
        var slots = [Bool](repeating: false, count: 48)
        for hour in 0..<24 {
            mark(&slots, hour: hour, code: hour < planned.count ? planned[hour] : 0)
            mark(&slots, hour: hour, code: hour < possible.count ? possible[hour] : 0)
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
        if let from = start {
            result.append(Outage(startMin: from, endMin: 1440))
        }
        return result
    }

    private static func mark(_ slots: inout [Bool], hour: Int, code: Int) {
        switch code {
        case 1:
            slots[hour * 2] = true
            slots[hour * 2 + 1] = true
        case 2:
            slots[hour * 2] = true
        case 3:
            slots[hour * 2 + 1] = true
        default:
            break
        }
    }

    private static func day(_ name: String, _ date: Date, _ groups: [String: Piece]) -> DaySchedule {
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
            outages[queue] = groups[queue]?.outages ?? []
            updated[queue] = groups[queue]?.stamp ?? ""
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

    private static func findRows(_ root: [String: Any]) -> [[String: Any]]? {
        for value in root.values {
            guard let object = value as? [String: Any], let data = object["data"] as? [Any] else { continue }
            let rows = data.compactMap { $0 as? [String: Any] }
            let matches = rows.contains { row in
                row["initiator"] != nil && row["queue"] != nil && row["today"] != nil
            }
            if matches { return rows }
        }
        return nil
    }

    private static func stateJson(_ html: String) -> String? {
        let marker = #"<script id="ng-state" type="application/json">"#
        guard let start = html.range(of: marker),
              let end = html.range(of: "</script>", range: start.upperBound..<html.endIndex) else { return nil }
        return String(html[start.upperBound..<end.lowerBound])
    }

    private static func messages(_ row: [String: Any], _ keys: String...) -> [String] {
        for key in keys {
            guard let list = row[key] as? [Any] else { continue }
            return list.compactMap { item in
                guard let object = item as? [String: Any], let message = object["message"] as? String else { return nil }
                let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed.isEmpty ? nil : trimmed
            }
        }
        return []
    }

    private static func ints(_ value: Any?) -> [Int] {
        guard let list = value as? [Any] else { return [] }
        return list.map { item in
            (item as? NSNumber)?.intValue ?? 0
        }
    }

    private static func text(_ row: [String: Any], _ keys: String...) -> String {
        for key in keys {
            if row[key] is NSNull { continue }
            if let value = row[key] as? String {
                let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { return trimmed }
            }
        }
        return ""
    }

    private static func civil(_ raw: String) -> Date? {
        let parts = raw.split(separator: "-")
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

    private static func isAfter(_ later: Date, _ earlier: Date) -> Bool {
        let calendar = ScheduleMath.calendar()
        let left = calendar.dateComponents([.year, .month, .day], from: later)
        let right = calendar.dateComponents([.year, .month, .day], from: earlier)
        if (left.year ?? 0) != (right.year ?? 0) { return (left.year ?? 0) > (right.year ?? 0) }
        if (left.month ?? 0) != (right.month ?? 0) { return (left.month ?? 0) > (right.month ?? 0) }
        return (left.day ?? 0) > (right.day ?? 0)
    }

    private static func formatDay(_ date: Date) -> String {
        let parts = ScheduleMath.calendar().dateComponents([.day, .month, .year], from: date)
        return String(format: "%02d.%02d.%04d", parts.day ?? 0, parts.month ?? 0, parts.year ?? 0)
    }

    private static func stamp(_ updated: String) -> String {
        guard let date = parseInstant(updated) else { return "" }
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

    private static func isQueue(_ value: String) -> Bool {
        value.range(of: #"^\d{1,3}(\.\d{1,2})?$"#, options: .regularExpression) != nil
    }

    private static func emptyPage() -> ParsedPage {
        ParsedPage(days: [], live: [], houses: [], tomorrowMissing: true, note: "")
    }
}
