import Foundation

enum SvitloClient {
    private static let schedule = URL(string: "https://vyshgorod.in.ua/svitlo")!
    private static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    static func page(for group: String, source: String = "", region: String = Regions.kyivOblast) -> URL {
        _ = group
        let slug = Regions.normalize(region)
        if source == AlertsEnergy.source || slug != Regions.kyivOblast {
            return AlertsEnergy.page(for: slug)
        }
        return schedule
    }

    static func fetchPage() async throws -> String {
        try await fetch(schedule, referer: "https://vyshgorod.in.ua/")
    }

    static func fetchAlerts(region: String = Regions.kyivOblast) async throws -> String {
        try await fetch(AlertsEnergy.page(for: region), referer: "https://alerts.energy/")
    }

    private static func fetch(_ url: URL, referer: String) async throws -> String {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/json;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        request.setValue(referer, forHTTPHeaderField: "Referer")
        request.setValue("uk,ru;q=0.9", forHTTPHeaderField: "Accept-Language")
        let (data, response) = try await URLSession.shared.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else { throw SvitloError.message(Copy.siteAnswered(code)) }
        guard let body = String(data: data, encoding: .utf8), !body.isEmpty else {
            throw SvitloError.message(Copy.emptyPage())
        }
        return body
    }
}
