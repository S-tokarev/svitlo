import Foundation

enum SvitloParser {
    private static let queueId = try! NSRegularExpression(pattern: "^[1-6]\\.[12]$")
    private static let months = [
        "січня": 1, "лютого": 2, "березня": 3, "квітня": 4, "травня": 5, "червня": 6,
        "липня": 7, "серпня": 8, "вересня": 9, "жовтня": 10, "листопада": 11, "грудня": 12,
    ]

    static func parse(html: String) -> ParsedPage {
        var chosen: [String: (date: Date, mode: String, schedule: DaySchedule)] = [:]
        for item in lightCharts(html) {
            guard let schedule = parseChart(item.svg, day: "today"),
                  let date = parseDay(schedule.updatedDate) else { continue }
            let key = schedule.updatedDate
            if let current = chosen[key] {
                if current.mode != "changes" && item.mode == "changes" {
                    chosen[key] = (date, item.mode, schedule)
                }
            } else {
                chosen[key] = (date, item.mode, schedule)
            }
        }
        let ordered = chosen.values.sorted { $0.date < $1.date }
        guard let todayHit = ordered.first, !todayHit.schedule.groups.isEmpty else { return emptyPage() }
        var today = todayHit.schedule
        today.day = "today"
        var days = [today]
        if ordered.count > 1 {
            var tomorrow = ordered[1].schedule
            tomorrow.day = "tomorrow"
            days.append(tomorrow)
        }
        return ParsedPage(
            days: days,
            live: parseLive(html),
            houses: parseHouses(html),
            tomorrowMissing: ordered.count < 2,
            note: ""
        )
    }

    /// Light charts only. The site draws up to two days, and each day twice.
    private static func lightCharts(_ html: String) -> [(svg: String, mode: String)] {
        let marker = "<div class=\"sg-svg sg-svg-light\">"
        var result: [(svg: String, mode: String)] = []
        var from = html.startIndex
        while result.count < 8, let start = html.range(of: marker, range: from..<html.endIndex) {
            guard let svgStart = html.range(of: "<svg", range: start.lowerBound..<html.endIndex),
                  let svgEnd = html.range(of: "</svg></div>", range: svgStart.lowerBound..<html.endIndex) else { break }
            let close = html.index(svgEnd.lowerBound, offsetBy: "</svg>".count)
            result.append((String(html[svgStart.lowerBound..<close]), modeBefore(html, start.lowerBound)))
            from = svgEnd.upperBound
        }
        return result
    }

    private static func modeBefore(_ html: String, _ start: String.Index) -> String {
        let windowStart = html.index(start, offsetBy: -500, limitedBy: html.startIndex) ?? html.startIndex
        let window = String(html[windowStart..<start])
        return captures(#"data-mode="([^"]+)""#, in: window).last?.dropFirst().first ?? ""
    }

    private static func parseChart(_ svg: String, day: String) -> DaySchedule? {
        let runs = texts(svg)
        guard let track = track(svg),
              let stampDate = runs.compactMap({ updatedDate($0.body) }).first,
              let date = chartDate(runs, stamp: stampDate) else { return nil }
        let stampClock = runs.filter { $0.y < 90 && $0.body.range(of: #"^\d{2}:\d{2}$"#, options: .regularExpression) != nil }
            .max { $0.x < $1.x }?.body
        let stamp = [stampDate, stampClock].compactMap { $0 }.joined(separator: " ")
        var groups: [String: [Outage]] = [:]
        let bars = offRects(svg)
        for label in runs where label.x < 180 && queueId.matches(label.body) {
            let row = bars.filter { abs($0.centerY - label.y) <= 24 }
            let merged = merge(row.map { ($0.x, $0.x + $0.width) }.sorted { $0.0 < $1.0 })
            groups[label.body] = merged.compactMap { segment in
                let from = minuteAt(segment.0, track)
                let to = minuteAt(segment.1, track)
                return to > from ? Outage(startMin: from, endMin: to) : nil
            }
        }
        guard !groups.isEmpty else { return nil }
        return DaySchedule(
            day: day,
            label: ScheduleMath.dayLabel(date),
            updatedDate: format(date, "dd.MM.yyyy", Locale(identifier: "en_US_POSIX")),
            updatedTime: "",
            groups: groups,
            groupUpdated: Dictionary(uniqueKeysWithValues: groups.keys.map { ($0, stamp) })
        )
    }

    private static func parseLive(_ html: String) -> [LiveGroup] {
        let pattern = #"<span class="group ([^"]*)" data-g="([^"]*)">\s*<b>[^<]*</b>\s*<small>([^<]*)</small>\s*<span class="mono[^"]*" data-sec="(\d+)""#
        var seen: [String] = []
        var result: [LiveGroup] = []
        for match in captures(pattern, in: html) {
            let classes = match[1].split(whereSeparator: \.isWhitespace).map(String.init)
            let group = match[2]
            guard queueId.matches(group), !seen.contains(group) else { continue }
            let toward: String
            if match[3].contains("OFF") { toward = "OFF" }
            else if match[3].contains("ON") { toward = "ON" }
            else { continue }
            guard let seconds = Int(match[4]) else { continue }
            seen.append(group)
            result.append(LiveGroup(
                id: group,
                powerOn: classes.contains("on") && !classes.contains("off"),
                toward: toward,
                seconds: seconds
            ))
        }
        return result
    }

    private static func parseHouses(_ html: String) -> [HouseStatus] {
        let pattern = #"<div class="house([^"]*)"\s+data-g="([^"]*)"\s+data-st="([^"]*)"\s+data-q="[^"]*">(.*?)</div>"#
        return captures(pattern, in: html, dotAll: true).compactMap { match in
            let group = match[2]
            guard queueId.matches(group) else { return nil }
            let body = match[4]
            let address = unescape(first(body, #"<span class="addr"><b>(.*?)</b>"#) ?? "")
            guard !address.isEmpty else { return nil }
            let complex = first(body, #"<small class="complex-tag">(.*?)</small>"#).map(unescape)?.nilIfBlank
            let since = first(body, #"title="([^"]*з [^"]+)""#).map(unescape)
            return HouseStatus(
                group: group,
                powerOn: match[3] == "on",
                address: address,
                complex: complex,
                since: since
            )
        }
    }

    private struct TextRun {
        var x: Double
        var y: Double
        var body: String
    }

    private struct Bar {
        var x: Double
        var width: Double
        var centerY: Double
    }

    private struct Track {
        var origin: Double
        var width: Double
    }

    private static func texts(_ svg: String) -> [TextRun] {
        captures(#"<text\b([^>]*)>(.*?)</text>"#, in: svg, dotAll: true).compactMap { match in
            let values = attributes(match[1])
            guard let x = Double(values["x"] ?? ""), let y = Double(values["y"] ?? "") else { return nil }
            let body = unescape(match[2].replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)).trimmingCharacters(in: .whitespacesAndNewlines)
            return body.isEmpty ? nil : TextRun(x: x, y: y, body: body)
        }
    }

    private static func offRects(_ svg: String) -> [Bar] {
        captures(#"<rect\b([^>]*)/?>"#, in: svg).compactMap { match in
            let values = attributes(match[1])
            guard values["fill"]?.range(of: "CBCAC5", options: .caseInsensitive) != nil,
                  let x = Double(values["x"] ?? ""),
                  let y = Double(values["y"] ?? ""),
                  let width = Double(values["width"] ?? ""),
                  let height = Double(values["height"] ?? ""),
                  height >= 20, height <= 60 else { return nil }
            return Bar(x: x, width: width, centerY: y + height / 2)
        }
    }

    private static func track(_ svg: String) -> Track? {
        for match in captures(#"<rect\b([^>]*)/?>"#, in: svg) {
            let values = attributes(match[1])
            guard let x = Double(values["x"] ?? ""), let width = Double(values["width"] ?? "") else { continue }
            if x >= 180 && x <= 280 && width >= 1000 && width <= 1300 { return Track(origin: x, width: width) }
        }
        return nil
    }

    private static func minuteAt(_ x: Double, _ track: Track) -> Int {
        let raw = (x - track.origin) / track.width * 1440
        let snapped = Int((raw / 30).rounded()) * 30
        return min(1440, max(0, snapped))
    }

    private static func merge(_ segments: [(Double, Double)]) -> [(Double, Double)] {
        var merged: [(Double, Double)] = []
        for (start, end) in segments {
            if let last = merged.last, start <= last.1 + 3 {
                merged[merged.count - 1] = (last.0, max(last.1, end))
            } else {
                merged.append((start, end))
            }
        }
        return merged
    }

    private static func updatedDate(_ body: String) -> String? {
        first(body, #"Оновлено\s+(\d{2}\.\d{2}\.\d{4})"#)
    }

    private static func chartDate(_ runs: [TextRun], stamp: String) -> Date? {
        guard let phrase = runs.first(where: { run in months.keys.contains { run.body.lowercased().contains($0) } })?.body,
              let match = captures(#"(\d{1,2})\s+([а-яіїєґА-ЯІЇЄҐ]+)"#, in: phrase).first,
              let day = Int(match[1]),
              let month = months[match[2].lowercased(with: Locale(identifier: "uk"))],
              let stampDate = parseDay(stamp) else { return nil }
        var parts = Calendar(identifier: .gregorian).dateComponents([.year], from: stampDate)
        parts.month = month
        parts.day = day
        parts.calendar = ScheduleMath.calendar()
        parts.timeZone = ScheduleMath.zone
        guard let candidate = ScheduleMath.calendar().date(from: parts) else { return nil }
        let limit = ScheduleMath.calendar().date(byAdding: .day, value: -1, to: stampDate) ?? stampDate
        if candidate < limit {
            return ScheduleMath.calendar().date(byAdding: .year, value: 1, to: candidate)
        }
        return candidate
    }

    private static func parseDay(_ value: String) -> Date? {
        let formatter = DateFormatter()
        formatter.calendar = ScheduleMath.calendar()
        formatter.timeZone = ScheduleMath.zone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "dd.MM.yyyy"
        return formatter.date(from: value)
    }

    private static func format(_ date: Date, _ pattern: String, _ locale: Locale) -> String {
        let formatter = DateFormatter()
        formatter.calendar = ScheduleMath.calendar()
        formatter.timeZone = ScheduleMath.zone
        formatter.locale = locale
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }

    private static func attributes(_ source: String) -> [String: String] {
        var result: [String: String] = [:]
        for match in captures(#"([\w:-]+)="([^"]*)""#, in: source) {
            result[match[1]] = match[2]
        }
        return result
    }

    private static func first(_ text: String, _ pattern: String) -> String? {
        captures(pattern, in: text, dotAll: true).first?.dropFirst().first
    }

    private static func captures(_ pattern: String, in text: String, dotAll: Bool = false) -> [[String]] {
        let options: NSRegularExpression.Options = dotAll ? [.dotMatchesLineSeparators] : []
        guard let expression = try? NSRegularExpression(pattern: pattern, options: options) else { return [] }
        let range = NSRange(text.startIndex..., in: text)
        return expression.matches(in: text, range: range).map { match in
            (0..<match.numberOfRanges).map { index in
                guard let piece = Range(match.range(at: index), in: text) else { return "" }
                return String(text[piece])
            }
        }
    }

    private static func unescape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#34;", with: "\"")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func emptyPage() -> ParsedPage {
        ParsedPage(days: [], live: [], houses: [], tomorrowMissing: true, note: "")
    }
}

private extension NSRegularExpression {
    func matches(_ value: String) -> Bool {
        let range = NSRange(value.startIndex..., in: value)
        return firstMatch(in: value, range: range) != nil
    }
}

private extension String {
    var nilIfBlank: String? { isEmpty ? nil : self }
}
