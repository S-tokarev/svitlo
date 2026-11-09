import Foundation

enum SvitloClient {
    private static let schedule = URL(string: "https://alerts.energy/kyivska-oblast/schedule/kyivska-oblast-oblenergo/2.1")!
    private static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    static func page(for group: String) -> URL {
        let queue = group.range(of: #"^[1-6]\.[12]$"#, options: .regularExpression) == nil ? "2.1" : group
        return URL(string: "https://alerts.energy/kyivska-oblast/schedule/kyivska-oblast-oblenergo/\(queue)")!
    }

    static func fetchPage() async throws -> String {
        var request = URLRequest(url: schedule)
        request.timeoutInterval = 20
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/json;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        request.setValue("https://alerts.energy/", forHTTPHeaderField: "Referer")
        request.setValue("uk,ru;q=0.9", forHTTPHeaderField: "Accept-Language")
        let (data, response) = try await URLSession.shared.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else { throw SvitloError.message("Сайт ответил \(code)") }
        guard let body = String(data: data, encoding: .utf8), !body.isEmpty else {
            throw SvitloError.message("Сайт отдал пустую страницу")
        }
        return body
    }
}
