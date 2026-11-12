import Foundation

enum AddressMatch {
    static let streetLimit = 24
    static let houseLimit = 40

    struct ParsedQuery: Equatable {
        var house: String?
        var tokens: [String]
    }

    struct StreetHit: Equatable {
        var city: String
        var street: String
    }

    struct StreetQuery: Equatable {
        var streets: [StreetHit]
        var truncated: Bool
    }

    static func parseDirectory(_ json: String) -> [String: [String]] {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let streets = root["streets"] as? [String: Any] else { return [:] }
        var result: [String: [String]] = [:]
        for (city, raw) in streets {
            guard let list = raw as? [Any] else { continue }
            let values = list.compactMap { $0 as? String }.filter { !$0.isEmpty }
            if !city.isEmpty && !values.isEmpty { result[city] = values }
        }
        return result
    }

    static func parseHouses(_ json: String, city: String, street: String, houseQuery: String?) -> AddressLookup {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let houses = root["data"] as? [String: Any] else { return AddressLookup(hits: [], hint: nil) }
        var hits: [AddressHit] = []
        for (number, raw) in houses {
            if let houseQuery, !houseMatches(houseQuery, number) { continue }
            for group in groups(of: raw) {
                hits.append(AddressHit(city: city, street: street, house: number, group: group))
            }
        }
        hits.sort {
            let left = (houseOrder($0.house), $0.house, $0.group)
            let right = (houseOrder($1.house), $1.house, $1.group)
            return left < right
        }
        if hits.isEmpty, houseQuery != nil {
            let all = parseHouses(json, city: city, street: street, houseQuery: nil)
            if all.hits.isEmpty { return AddressLookup(hits: [], hint: nil) }
            return AddressLookup(hits: Array(all.hits.prefix(houseLimit)), hint: Copy.houseMissing())
        }
        let hint = hits.count > houseLimit ? Copy.narrowByHouse() : nil
        return AddressLookup(hits: Array(hits.prefix(houseLimit)), hint: hint)
    }

    static func csrfToken(_ cookieValue: String) -> String? {
        let decoded = cookieValue.removingPercentEncoding ?? cookieValue
        let pattern = try! NSRegularExpression(pattern: "i:1;s:\\d+:\"([^\"]+)\"")
        let range = NSRange(decoded.startIndex..., in: decoded)
        guard let match = pattern.firstMatch(in: decoded, range: range),
              let tokenRange = Range(match.range(at: 1), in: decoded) else { return nil }
        return String(decoded[tokenRange])
    }

    static func maskToken(_ token: String, mask: [UInt8]) -> String {
        let raw = Array(token.utf8)
        precondition(mask.count == raw.count)
        let masked = zip(mask, raw).map { $0 ^ $1 }
        return Data(mask + masked).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
    }

    static func parseQuery(_ query: String) -> ParsedQuery {
        let parts = query.split { $0.isWhitespace || $0 == "," }.map(String.init).filter { !$0.isEmpty }
        guard !parts.isEmpty else { return ParsedQuery(house: nil, tokens: []) }
        let house = parts.last.flatMap { houseToken.matches($0) ? $0 : nil }
        let words = house == nil ? parts : parts.dropLast()
        let tokens = words.map { fold(String($0).trimmingCharacters(in: CharacterSet(charactersIn: "."))) }
            .filter { $0.count >= 2 && !stopWords.contains($0) }
        return ParsedQuery(house: house, tokens: tokens)
    }

    static func houseFilter(_ query: String) -> String? { parseQuery(query).house }

    static func matchStreets(_ directory: [String: [String]], _ parsed: ParsedQuery) -> StreetQuery {
        guard !parsed.tokens.isEmpty else { return StreetQuery(streets: [], truncated: false) }
        var scored: [Scored] = []
        for (city, streets) in directory {
            let cityFold = fold(stripPlace(city))
            for street in streets {
                let streetFold = fold(stripStreet(street))
                let streetHits = parsed.tokens.filter { tokenHits(streetFold, $0) }
                guard !streetHits.isEmpty else { continue }
                guard parsed.tokens.allSatisfy({ tokenHits(streetFold, $0) || tokenHits(cityFold, $0) }) else { continue }
                let cityHits = parsed.tokens.filter { tokenHits(cityFold, $0) }
                var score = 10
                if streetFold == streetHits.joined(separator: " ") { score += 50 }
                if streetFold.hasPrefix(streetHits[0]) { score += 20 }
                if !cityHits.isEmpty { score += 30 }
                if streetHits.count == parsed.tokens.count { score += 15 }
                scored.append(Scored(city: city, street: street, score: score))
            }
        }
        guard let best = scored.map(\.score).max() else { return StreetQuery(streets: [], truncated: false) }
        let close = scored
            .filter { $0.score >= best - 15 }
            .sorted { lhs, rhs in
                if lhs.score != rhs.score { return lhs.score > rhs.score }
                if lhs.city != rhs.city { return lhs.city < rhs.city }
                return lhs.street < rhs.street
            }
        return StreetQuery(
            streets: close.prefix(streetLimit).map { StreetHit(city: $0.city, street: $0.street) },
            truncated: close.count > streetLimit
        )
    }

    static func houseMatches(_ wanted: String, _ candidate: String) -> Bool {
        let left = foldHouse(wanted)
        let right = foldHouse(candidate)
        guard !left.isEmpty, !right.isEmpty else { return false }
        if left == right { return true }
        guard right.hasPrefix(left) else { return false }
        let nextIndex = right.index(right.startIndex, offsetBy: left.count)
        guard nextIndex < right.endIndex else { return true }
        return !right[nextIndex].isNumber
    }

    private struct Scored {
        var city: String
        var street: String
        var score: Int
    }

    private static let queueName = try! NSRegularExpression(pattern: "^[1-6]\\.[12]$")
    private static let placePrefix = try! NSRegularExpression(
        pattern: "^(смт|місто|селище|село|с-ще|м|с)\\.?\\s+",
        options: .caseInsensitive
    )
    private static let streetPrefix = try! NSRegularExpression(
        pattern: "^(вулиця|вул|улица|провулок|пров|проспект|просп|бульвар|бул|площа|пл|набережна|наб|шосе|тупик|майдан|проїзд|проезд|ул)\\.?\\s+",
        options: .caseInsensitive
    )
    private static let houseToken = try! NSRegularExpression(
        pattern: "^\\d+[0-9a-zа-яіїєґ/\\-]*$",
        options: .caseInsensitive
    )
    private static let stopWords: Set<String> = [
        "вул", "вулиця", "улица", "ул", "пров", "провулок", "просп", "проспект",
        "бул", "бульвар", "пл", "площа", "наб", "набережна", "шосе", "тупик",
        "майдан", "місто", "село", "селище", "смт", "город",
    ]
    private static let latinHouse: [Character: Character] = {
        let latin = Array("aceiopxkmhtby")
        let cyr = Array("асеіорхкмнтву")
        return Dictionary(uniqueKeysWithValues: zip(latin, cyr))
    }()

    private static func groups(of value: Any) -> [String] {
        let records: [[String: Any]]
        if let object = value as? [String: Any] {
            records = [object]
        } else if let list = value as? [Any] {
            records = list.compactMap { $0 as? [String: Any] }
        } else {
            return []
        }
        if records.contains(where: servedElsewhere) { return [] }
        var seen: [String] = []
        for record in records {
            for reason in reasons(record) {
                guard let queue = queue(of: reason), !seen.contains(queue) else { continue }
                seen.append(queue)
            }
        }
        return seen
    }

    private static func reasons(_ record: [String: Any]) -> [String] {
        switch record["sub_type_reason"] {
        case let list as [Any]:
            return list.compactMap { $0 as? String }.filter { !$0.isEmpty }
        case let text as String where !text.isEmpty:
            return [text]
        default:
            return []
        }
    }

    private static func servedElsewhere(_ record: [String: Any]) -> Bool {
        guard let value = record["cek"], !(value is NSNull) else { return false }
        if let flag = value as? Bool { return flag }
        if let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() { return number.intValue != 0 }
        if let text = value as? String { return !text.isEmpty && text != "0" && text.lowercased() != "false" }
        return false
    }

    private static func queue(of raw: String) -> String? {
        var name = raw.trimmingCharacters(in: .whitespaces)
        if name.hasPrefix("GPV") { name.removeFirst(3) }
        else if name.hasPrefix("gpv") { name.removeFirst(3) }
        let range = NSRange(name.startIndex..., in: name)
        return queueName.firstMatch(in: name, range: range) == nil ? nil : name
    }

    private static func stripPlace(_ city: String) -> String { replacingPrefix(placePrefix, in: city) }
    private static func stripStreet(_ street: String) -> String { replacingPrefix(streetPrefix, in: street) }

    private static func replacingPrefix(_ expression: NSRegularExpression, in value: String) -> String {
        let range = NSRange(value.startIndex..., in: value)
        return expression.stringByReplacingMatches(in: value, range: range, withTemplate: "")
    }

    private static func fold(_ value: String) -> String {
        var folded = ""
        for char in value.lowercased(with: Locale(identifier: "uk")) {
            switch char {
            case "і", "ы": folded.append("и")
            case "ї", "є", "ё", "э": folded.append("е")
            case "ґ": folded.append("г")
            case "ъ", "'", "’", "ʼ", "-": break
            default: folded.append(char)
            }
        }
        return folded
    }

    private static func loose(_ folded: String) -> String {
        folded.filter { !"аеиоуьюя".contains($0) }
    }

    private static func tokenHits(_ haystack: String, _ token: String) -> Bool {
        if haystack.contains(token) { return true }
        guard token.count >= 4 else { return false }
        let needle = loose(token)
        return needle.count >= 3 && loose(haystack).contains(needle)
    }

    private static func foldHouse(_ value: String) -> String {
        String(value.filter { !$0.isWhitespace }.lowercased().map { latinHouse[$0] ?? $0 })
    }

    private static func houseOrder(_ house: String) -> Int {
        Int(house.prefix { $0.isNumber }) ?? Int.max
    }
}

private extension NSRegularExpression {
    func matches(_ value: String) -> Bool {
        let range = NSRange(value.startIndex..., in: value)
        return firstMatch(in: value, range: range) != nil
    }
}

actor AddressDirectory {
    static let shared = AddressDirectory()

    private let ajax = URL(string: "https://www.dtek-krem.com.ua/ua/ajax")!
    private let fresh: TimeInterval = 24 * 60 * 60
    private let session: URLSession
    private let cookies = HTTPCookieStorage()
    private var token: String?
    private var directory: [String: [String]]?
    private var directoryAt = Date.distantPast

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 30
        configuration.httpShouldSetCookies = true
        configuration.httpCookieAcceptPolicy = .always
        cookies.cookieAcceptPolicy = .always
        configuration.httpCookieStorage = cookies
        session = URLSession(configuration: configuration)
    }

    func search(_ query: String) async throws -> AddressLookup {
        let parsed = AddressMatch.parseQuery(query)
        if parsed.tokens.isEmpty { throw SvitloError.message(Copy.typeStreet()) }
        let streets = try await loadDirectory()
        let found = AddressMatch.matchStreets(streets, parsed)
        if found.streets.isEmpty { return AddressLookup(hits: [], hint: nil) }
        if let house = parsed.house, found.streets.count <= 3 {
            var hits: [AddressHit] = []
            for street in found.streets {
                let lookup = try await houses(city: street.city, street: street.street, houseQuery: house)
                hits += lookup.hits.filter { AddressMatch.houseMatches(house, $0.house) }
            }
            if !hits.isEmpty {
                var seen: [String] = []
                let unique = hits.filter { hit in
                    let key = hit.city + hit.street + hit.house + hit.group
                    if seen.contains(key) { return false }
                    seen.append(key)
                    return true
                }
                return AddressLookup(hits: Array(unique.prefix(AddressMatch.houseLimit)), hint: nil)
            }
        }
        return AddressLookup(
            hits: found.streets.map { AddressHit(city: $0.city, street: $0.street, house: "", group: "") },
            hint: found.truncated ? Copy.manyStreets() : nil
        )
    }

    func houses(city: String, street: String, houseQuery: String?) async throws -> AddressLookup {
        _ = try await loadDirectory()
        let json = try await homeNumbers(city: city, street: street, updateFact: "")
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              (root["result"] as? Bool) == true else { return AddressLookup(hits: [], hint: nil) }
        return AddressMatch.parseHouses(json, city: city, street: street, houseQuery: houseQuery)
    }

    /// Group schedule for the whole oblast. Any published street returns the same fact.
    func oblastSchedule() async throws -> String {
        let streets = try await loadDirectory()
        guard let place = streets.first(where: { !$0.value.isEmpty }), let street = place.value.first else {
            throw SvitloError.message(Copy.directoryEmpty())
        }
        return try await homeNumbers(city: place.key, street: street, updateFact: "01.01.2000 00:00")
    }

    private func homeNumbers(city: String, street: String, updateFact: String) async throws -> String {
        let body = form([
            ("method", "getHomeNum"),
            ("data[0][name]", "city"),
            ("data[0][value]", city),
            ("data[1][name]", "street"),
            ("data[1][value]", street),
            ("data[2][name]", "updateFact"),
            ("data[2][value]", updateFact),
        ])
        return try await call(body)
    }

    private func loadDirectory() async throws -> [String: [String]] {
        if let directory, Date().timeIntervalSince(directoryAt) < fresh { return directory }
        let file = AppGroup.cacheDirectory.appendingPathComponent("dtek-streets.json")
        if let cached = readCache(file) {
            directory = cached
            directoryAt = (try? FileManager.default.attributesOfItem(atPath: file.path)[.modificationDate] as? Date) ?? Date()
            return cached
        }
        let json = try await call(form([("method", "getStreets")]))
        let parsed = AddressMatch.parseDirectory(json)
        if parsed.isEmpty { throw SvitloError.message(Copy.directoryEmpty()) }
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? json.write(to: file, atomically: true, encoding: .utf8)
        directory = parsed
        directoryAt = Date()
        return parsed
    }

    private func readCache(_ file: URL) -> [String: [String]]? {
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: file.path),
              let modified = attributes[.modificationDate] as? Date,
              Date().timeIntervalSince(modified) < fresh,
              let text = try? String(contentsOf: file, encoding: .utf8) else { return nil }
        let parsed = AddressMatch.parseDirectory(text)
        return parsed.isEmpty ? nil : parsed
    }

    private func call(_ body: Data) async throws -> String {
        try await ensureToken()
        var answer = try await post(body, withToken: true)
        if answer.code == 400 {
            token = nil
            try await ensureToken()
            answer = try await post(body, withToken: true)
        }
        guard (200...299).contains(answer.code) else { throw SvitloError.message(Copy.dtekAnswered(answer.code)) }
        if answer.body.contains("_Incapsula_Resource") { throw SvitloError.message(Copy.dtekClosed()) }
        return answer.body
    }

    private func ensureToken() async throws {
        if token != nil { return }
        _ = try await post(form([("method", "getStreets")]), withToken: false)
        if token == nil { throw SvitloError.message(Copy.dtekNoKey()) }
    }

    private func post(_ body: Data, withToken: Bool) async throws -> (code: Int, body: String) {
        var request = URLRequest(url: ajax)
        request.httpMethod = "POST"
        request.httpBody = body
        request.setValue("Svitlo/1.0 (power schedule; Kyiv oblast)", forHTTPHeaderField: "User-Agent")
        request.setValue("application/json, text/javascript, */*; q=0.01", forHTTPHeaderField: "Accept")
        request.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        request.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        request.setValue("https://www.dtek-krem.com.ua", forHTTPHeaderField: "Origin")
        request.setValue("https://www.dtek-krem.com.ua/ua/shutdowns", forHTTPHeaderField: "Referer")
        if withToken, let token { request.setValue(AddressMatch.maskToken(token, mask: randomMask(count: token.utf8.count)), forHTTPHeaderField: "X-CSRF-Token") }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw SvitloError.message(Copy.dtekSilent()) }
        absorbCookies(http)
        let text = String(data: data, encoding: .utf8) ?? ""
        if SvitloClient.looksLimited(code: http.statusCode, body: text) {
            throw RateLimited(waitMs: SvitloClient.retryWaitMs(http.value(forHTTPHeaderField: "Retry-After")))
        }
        return (http.statusCode, text)
    }

    private func absorbCookies(_ response: HTTPURLResponse) {
        let header = response.allHeaderFields.reduce(into: [String: String]()) { result, item in
            if let key = item.key as? String, let value = item.value as? String {
                result[key] = value
            }
        }
        for cookie in HTTPCookie.cookies(withResponseHeaderFields: header, for: ajax) {
            cookies.setCookie(cookie)
            if cookie.name == "_csrf-dtek-krem" { token = AddressMatch.csrfToken(cookie.value) ?? token }
        }
        for cookie in cookies.cookies(for: ajax) ?? [] where cookie.name == "_csrf-dtek-krem" {
            token = AddressMatch.csrfToken(cookie.value) ?? token
        }
    }

    private func form(_ pairs: [(String, String)]) -> Data {
        Data(pairs.map { "\(formEncode($0.0))=\(formEncode($0.1))" }.joined(separator: "&").utf8)
    }

    private func formEncode(_ value: String) -> String {
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private func randomMask(count: Int) -> [UInt8] {
        (0..<count).map { _ in UInt8.random(in: .min ... .max) }
    }
}
