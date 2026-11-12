import Foundation

struct RateLimited: LocalizedError {
    let waitMs: Int64
    var errorDescription: String? { Copy.tooMany() }
}

struct ApiDenied: Error {}

enum SvitloClient {
    private static let schedule = URL(string: "https://vyshgorod.in.ua/svitlo")!
    private static let userAgent = "Svitlo/1.0 (power schedule)"

    static func page(for group: String, source: String = "", region: String = Regions.kyivOblast) -> URL {
        _ = group
        let slug = Regions.normalize(region)
        if source == Yasno.source { return Yasno.page(for: slug) }
        if source == DtekSchedule.source { return DtekSchedule.page }
        if source == AlertsEnergy.source { return AlertsEnergy.page(for: slug) }
        if Yasno.covers(slug) { return Yasno.page(for: slug) }
        if slug == Regions.kyivOblast { return schedule }
        return AlertsEnergy.page(for: slug)
    }

    /// Public JSON that does not need a key.
    static func fetchPublic(_ url: URL) async throws -> String {
        try await request(url, json: true) { request in
            request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
            request.setValue("application/json", forHTTPHeaderField: "Accept")
        }
    }

    static func fetchPage() async throws -> String {
        try await readHtml(schedule, referer: "https://vyshgorod.in.ua/")
    }

    static func fetchAlerts(region: String = Regions.kyivOblast) async throws -> String {
        try await readHtml(AlertsEnergy.page(for: region), referer: "https://alerts.energy/")
    }

    /// Documented call. The key is the user's own subscription key, sent only as a header.
    static func fetchJson(_ url: URL, apiKey: String) async throws -> String {
        let key = normalizeApiKey(apiKey)
        return try await request(url, json: true) { request in
            request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
            request.setValue("application/json", forHTTPHeaderField: "Accept")
            request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        }
    }

    static func normalizeApiKey(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.lowercased().hasPrefix("bearer ") {
            value = String(value.dropFirst(7)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return value
    }

    /// A real schedule page embeds ng-state. A short refusal page does not.
    static func looksLimited(code: Int, body: String) -> Bool {
        if code == 429 { return true }
        if body.contains("ng-state") { return false }
        return body.contains("Забагато запитів")
            || body.range(of: "Too many requests", options: .caseInsensitive) != nil
    }

    static func retryWaitMs(_ retryAfter: String?) -> Int64 {
        let seconds = Int64(retryAfter?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "") ?? 600
        return min(max(seconds * 1000, 60_000), 30 * 60 * 1000)
    }

    private static func readHtml(_ url: URL, referer: String) async throws -> String {
        try await request(url, json: false) { request in
            request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
            request.setValue("text/html,application/json;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
            request.setValue(referer, forHTTPHeaderField: "Referer")
            request.setValue("uk,ru;q=0.9", forHTTPHeaderField: "Accept-Language")
        }
    }

    private static func request(
        _ url: URL,
        json: Bool,
        headers: (inout URLRequest) -> Void
    ) async throws -> String {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        headers(&request)
        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        let code = http?.statusCode ?? 0
        let body = String(data: data, encoding: .utf8) ?? ""
        if looksLimited(code: code, body: body) {
            throw RateLimited(waitMs: retryWaitMs(http?.value(forHTTPHeaderField: "Retry-After")))
        }
        if json && (code == 401 || code == 403) { throw ApiDenied() }
        guard (200...299).contains(code) else { throw SvitloError.message(Copy.siteAnswered(code)) }
        guard !body.isEmpty else { throw SvitloError.message(Copy.emptyPage()) }
        return body
    }
}
