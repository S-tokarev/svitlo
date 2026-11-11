import Foundation

enum ScheduleMath {
    static let warnBeforeMs: Int64 = 10 * 60 * 1000
    static let zone: TimeZone = TimeZone(identifier: "Europe/Kyiv")
        ?? TimeZone(identifier: "Europe/Kiev")
        ?? TimeZone(secondsFromGMT: 2 * 60 * 60)!

    private static let horizonMs: Int64 = 36 * 60 * 60 * 1000
    private static let liveFreshMs: Int64 = 20 * 60 * 1000
    private static let mergeMs: Int64 = 20 * 60 * 1000

    static func dayLabel(_ date: Date, language: AppLanguage = Copy.language) -> String {
        let parts = calendar().dateComponents([.day, .month], from: date)
        return dayLabel(day: parts.day ?? 1, month: parts.month ?? 0, language: language)
    }

    static func dayLabel(_ updatedDate: String, language: AppLanguage = Copy.language) -> String {
        let parts = updatedDate.split(separator: ".")
        guard parts.count >= 2, let day = Int(parts[0]), let month = Int(parts[1]) else { return "" }
        return dayLabel(day: day, month: month, language: language)
    }

    private static func dayLabel(day: Int, month: Int, language: AppLanguage) -> String {
        let name = Copy.month(month, language: language)
        guard !name.isEmpty else { return "" }
        return "\(day) \(name)"
    }

    static func calendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        calendar.locale = Locale(identifier: "ru_RU")
        return calendar
    }

    static func picture(snapshot: Snapshot, selection: Selection, now: Int64) -> PowerPicture {
        let todaySchedule = snapshot.days.first { $0.day == "today" }
        let tomorrowSchedule = nextDay(snapshot)
        let scheduleDate = todaySchedule.flatMap { parseDate($0.updatedDate) }
        let group = selection.group
        let todayOutages = todaySchedule?.groups[group] ?? []
        let tomorrowOutages = tomorrowSchedule?.groups[group] ?? []
        let today = startOfDay(date(from: now))

        var ranges: [Range] = []
        let publishedToday = scheduleDate == today && todaySchedule?.groups[group] != nil
        let publishedYesterday = scheduleDate == adding(days: -1, to: today)
        if publishedToday, let scheduleDate {
            ranges += absoluteRanges(scheduleDate, todayOutages)
        }
        if tomorrowSchedule?.groups[group] != nil && (publishedToday || publishedYesterday) {
            let day = publishedYesterday ? today : adding(days: 1, to: scheduleDate ?? today)
            ranges += absoluteRanges(day, tomorrowOutages)
        }
        ranges = mergeTouching(ranges.sorted { $0.start < $1.start })

        let coversToday = publishedToday || (publishedYesterday && tomorrowSchedule?.groups[group] != nil)
        let plannedOn: Bool? = coversToday ? !ranges.contains { now >= $0.start && now < $0.end } : nil

        let live = snapshot.live.first { $0.id == group }
        let liveFresh = live != nil && now - snapshot.fetchedAt >= 0 && now - snapshot.fetchedAt <= liveFreshMs
        var nextOff = ranges.filter { $0.start > now }.map(\.start).min()
        var nextOn = ranges.first { now >= $0.start && now < $0.end }?.end
            ?? ranges.filter { $0.start > now }.min { $0.start < $1.start }?.end

        if liveFresh, let live {
            let changeAt = snapshot.fetchedAt + Int64(live.seconds) * 1000
            if changeAt > now - 60_000 {
                if live.powerOn && live.toward == "OFF" { nextOff = changeAt }
                if !live.powerOn && live.toward == "ON" {
                    let scheduled = nextOn
                    let liveStopsAtMidnight = isDayBoundary(changeAt) && scheduled != nil && (scheduled ?? 0) > changeAt + 60_000
                    if !liveStopsAtMidnight { nextOn = changeAt }
                }
            }
        }
        let tomorrowKnown = tomorrowSchedule?.groups[group] != nil && (publishedToday || publishedYesterday)
        let offUntil = nextOn.flatMap { isDayBoundary($0) && !tomorrowKnown ? $0 : nil }
        if offUntil != nil { nextOn = nil }

        let house = findHouse(snapshot.houses, selection)
        let displayOn: Bool? = {
            if let house { return house.powerOn }
            if liveFresh, let live { return live.powerOn }
            return plannedOn
        }()

        var offMoments = ranges.map(\.start).filter { $0 > now && $0 <= now + horizonMs }
        if let nextOff, nextOff > now, nextOff <= now + horizonMs {
            offMoments.removeAll { abs($0 - nextOff) < mergeMs }
            offMoments.append(nextOff)
        }
        var onMoments = ranges.map(\.end).filter { $0 > now && $0 <= now + horizonMs }
        if let nextOn, nextOn > now, nextOn <= now + horizonMs {
            onMoments.removeAll { abs($0 - nextOn) < mergeMs }
            onMoments.append(nextOn)
        }
        if !tomorrowKnown { onMoments.removeAll { isDayBoundary($0) } }

        let shownToday: [Outage] = {
            if publishedToday { return todayOutages }
            if publishedYesterday, tomorrowSchedule != nil { return tomorrowOutages }
            return []
        }()
        let shownTomorrow = publishedToday && tomorrowSchedule != nil ? tomorrowOutages : []
        let updated = todaySchedule?.groupUpdated[group]?.nilIfBlank
            ?? [todaySchedule?.updatedDate, todaySchedule?.updatedTime]
                .compactMap { $0?.nilIfBlank }
                .joined(separator: " ")

        return PowerPicture(
            displayOn: displayOn,
            plannedOn: liveFresh && live != nil ? live?.powerOn : plannedOn,
            houseOn: house?.powerOn,
            nextOffAt: nextOff.flatMap { $0 > now ? $0 : nil },
            nextOnAt: nextOn.flatMap { $0 > now ? $0 : nil },
            offUntil: offUntil.flatMap { $0 > now ? $0 : nil },
            dueWarnOffAt: nextOff.flatMap { $0 > now && $0 - now <= warnBeforeMs ? $0 : nil },
            warnAts: offMoments.map { $0 - warnBeforeMs }.filter { $0 > now + 5_000 }.sorted(),
            appearAts: onMoments.sorted(),
            outagesToday: shownToday,
            outagesTomorrow: shownTomorrow,
            scheduleLabel: dayLabel(todaySchedule?.updatedDate ?? "").nilIfBlank
                ?? todaySchedule?.label
                ?? "",
            updatedLabel: updated,
            usingHouse: house != nil
        )
    }

    /// A chart labeled tomorrow counts only when its date is the next calendar day.
    static func groupHasTomorrow(snapshot: Snapshot, group: String) -> Bool {
        nextDay(snapshot)?.groups[group] != nil
    }

    static func knownGroups(_ snapshot: Snapshot?) -> [String] {
        var keys: Set<String> = [
            "1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2",
        ]
        snapshot?.days.forEach { keys.formUnion($0.groups.keys) }
        snapshot?.live.forEach { keys.insert($0.id) }
        snapshot?.houses.forEach { keys.insert($0.group) }
        return keys.sorted {
            let left = ($0.split(separator: ".").first.flatMap { Int($0) } ?? 9, $0)
            let right = ($1.split(separator: ".").first.flatMap { Int($0) } ?? 9, $1)
            return left < right
        }
    }

    static func scheduleChanged(previous: Snapshot, next: Snapshot, group: String, now: Int64) -> Bool {
        let today = startOfDay(date(from: now))
        let old = plans(previous, group)
        let fresh = plans(next, group)
        return Set(old.keys).union(fresh.keys).contains { key in
            if let date = parseDate(key), date < today { return false }
            return normalize(old[key]) != normalize(fresh[key])
        }
    }

    static func planStamp(snapshot: Snapshot, group: String) -> String {
        plans(snapshot, group)
            .sorted { $0.key < $1.key }
            .map { date, outages in
                date + ":" + normalize(outages).map { "\($0.startMin)-\($0.endMin)" }.joined(separator: ",")
            }
            .joined(separator: "|")
    }

    static func minuteOfDay(_ epochMs: Int64) -> Int {
        let parts = calendar().dateComponents([.hour, .minute], from: date(from: epochMs))
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }

    /// 23:00 inclusive until 07:00 exclusive.
    static func isQuietHour(_ epochMs: Int64) -> Bool {
        let minute = minuteOfDay(epochMs)
        return minute >= 23 * 60 || minute < 7 * 60
    }

    static func formatDayTime(_ epochMs: Int64, now: Int64) -> String {
        let whenDay = startOfDay(date(from: epochMs))
        let nowDay = startOfDay(date(from: now))
        let clock = formatClock(epochMs)
        if whenDay == nowDay { return clock }
        if whenDay == adding(days: 1, to: nowDay) { return Copy.tomorrowAt(clock) }
        let parts = calendar().dateComponents([.day, .month], from: date(from: epochMs))
        let day = parts.day ?? 0
        let month = String(format: "%02d", parts.month ?? 0)
        return "\(day).\(month) \(clock)"
    }

    static func formatDuration(_ ms: Int64) -> String {
        let total = max(0, Int(ms / 60_000))
        return Copy.duration(hours: total / 60, minutes: total % 60)
    }

    static func formatClock(minutes: Int) -> String {
        let capped = min(1440, max(0, minutes))
        return String(format: "%02d:%02d", capped / 60, capped % 60)
    }

    static func date(from epochMs: Int64) -> Date {
        Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000)
    }

    static func millis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    private struct Range {
        var start: Int64
        var end: Int64
    }

    private static func mergeTouching(_ ranges: [Range]) -> [Range] {
        var merged: [Range] = []
        for range in ranges {
            if let last = merged.last, range.start <= last.end {
                merged[merged.count - 1] = Range(start: last.start, end: max(last.end, range.end))
            } else {
                merged.append(range)
            }
        }
        return merged
    }

    private static func nextDay(_ snapshot: Snapshot) -> DaySchedule? {
        guard let anchor = snapshot.days.first(where: { $0.day == "today" }).flatMap({ parseDate($0.updatedDate) }),
              let tomorrow = snapshot.days.first(where: { $0.day == "tomorrow" }),
              let date = parseDate(tomorrow.updatedDate),
              date == adding(days: 1, to: anchor) else { return nil }
        return tomorrow
    }

    private static func isDayBoundary(_ epochMs: Int64) -> Bool {
        let parts = calendar().dateComponents([.hour, .minute], from: date(from: epochMs))
        return parts.hour == 0 && parts.minute == 0
    }

    private static func plans(_ snapshot: Snapshot, _ group: String) -> [String: [Outage]] {
        var map: [String: [Outage]] = [:]
        for day in snapshot.days where !day.updatedDate.isEmpty {
            guard let intervals = day.groups[group] else { continue }
            map[day.updatedDate] = intervals
        }
        return map
    }

    private static func normalize(_ outages: [Outage]?) -> [Outage] {
        (outages ?? []).sorted { lhs, rhs in
            if lhs.startMin != rhs.startMin { return lhs.startMin < rhs.startMin }
            return lhs.endMin < rhs.endMin
        }
    }

    private static func absoluteRanges(_ day: Date, _ outages: [Outage]) -> [Range] {
        let origin = millis(startOfDay(day))
        return outages.map { outage in
            Range(
                start: origin + Int64(outage.startMin) * 60_000,
                end: origin + Int64(outage.endMin) * 60_000
            )
        }
    }

    private static func startOfDay(_ date: Date) -> Date {
        calendar().startOfDay(for: date)
    }

    private static func adding(days: Int, to date: Date) -> Date {
        calendar().date(byAdding: .day, value: days, to: startOfDay(date)) ?? date
    }

    private static func parseDate(_ value: String) -> Date? {
        let formatter = DateFormatter()
        formatter.calendar = calendar()
        formatter.timeZone = zone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "dd.MM.yyyy"
        return formatter.date(from: value).map(startOfDay)
    }

    private static func formatClock(_ epochMs: Int64) -> String {
        let parts = calendar().dateComponents([.hour, .minute], from: date(from: epochMs))
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }

    private static func findHouse(_ houses: [HouseStatus], _ selection: Selection) -> HouseStatus? {
        guard let street = selection.street, let house = selection.house else { return nil }
        let streetKey = normStreet(street)
        let houseKey = normHouse(house)
        guard !streetKey.isEmpty, !houseKey.isEmpty else { return nil }
        return houses.first { item in
            guard item.group == selection.group else { return false }
            let parts = item.address.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            guard parts.count >= 2 else { return false }
            return normStreet(parts[0]) == streetKey && normHouse(parts[1]) == houseKey
        }
    }

    private static func normStreet(_ value: String) -> String {
        value.lowercased()
            .replacingOccurrences(of: "вул.", with: "")
            .replacingOccurrences(of: "вулиця", with: "")
            .replacingOccurrences(of: "пров.", with: "")
            .replacingOccurrences(of: "провулок", with: "")
            .replacingOccurrences(of: "просп.", with: "")
            .replacingOccurrences(of: ".", with: "")
            .replacingOccurrences(of: " ", with: "")
    }

    private static func normHouse(_ value: String) -> String {
        value.lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: ".", with: "")
    }

    static func placeHourLabels(minutes: [Int], widths: [CGFloat], track: CGFloat, gap: CGFloat) -> [CGFloat] {
        let count = minutes.count
        guard count > 0 else { return [] }
        let safeTrack = max(track, 1)
        var lefts = Array(repeating: CGFloat(0), count: count)
        lefts[0] = min(max(0, safeTrack * CGFloat(minutes[0]) / 1440 - widths[0] / 2), max(0, safeTrack - widths[0]))
        for index in 1..<count {
            let ideal = safeTrack * CGFloat(minutes[index]) / 1440 - widths[index] / 2
            let minLeft = lefts[index - 1] + widths[index - 1] + gap
            lefts[index] = max(ideal, minLeft)
        }
        let overflow = lefts[count - 1] + widths[count - 1] - safeTrack
        if overflow > 0 {
            lefts[count - 1] = max(0, safeTrack - widths[count - 1])
            if count >= 2 {
                for index in stride(from: count - 2, through: 0, by: -1) {
                    let maxLeft = lefts[index + 1] - gap - widths[index]
                    if lefts[index] > maxLeft { lefts[index] = maxLeft }
                }
            }
        }
        if lefts[0] < 0 {
            let total = widths.reduce(0, +)
            let free = max(0, safeTrack - total)
            let step = count > 1 ? free / CGFloat(count - 1) : 0
            var cursor: CGFloat = 0
            for index in 0..<count {
                lefts[index] = cursor
                cursor += widths[index] + step
            }
        }
        return lefts
    }
}

private extension String {
    var nilIfBlank: String? { isEmpty ? nil : self }
}
