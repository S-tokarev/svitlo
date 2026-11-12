import Foundation

/// Kyiv city and Dnipro use the public YASNO API. Kyiv oblast uses the DTEK ajax schedule.
/// If that call fails, oblast still tries vyshgorod.in.ua/svitlo and then alerts.energy.
/// A personal alerts.energy key is the later JSON fallback.
/// After «too many requests» that path waits, so the next refresh does not hit the same site again.
enum ScheduleLoad {
    private static let quietLock = NSLock()
    private static var htmlQuietUntilMs: Int64 = 0
    private static var apiQuietUntilMs: Int64 = 0
    private static var dtekQuietUntilMs: Int64 = 0
    private static var yasnoQuietUntilMs: Int64 = 0

    static func load(region: String = Regions.kyivOblast, operatorId: String = "", apiKey: String = "") async throws -> ParsedPage {
        let now = ScheduleMath.millis(Date())
        let slug = Regions.normalize(region)
        let household = operatorId.isEmpty || operatorId == AlertsEnergy.kyivOblastDtek
        let key = SvitloClient.normalizeApiKey(apiKey)
        if let direct = try await direct(slug: slug, operatorId: operatorId, household: household, now: now) {
            return direct
        }
        if !key.isEmpty && now >= apiQuiet() {
            do {
                var page = try await loadApi(slug: slug, operatorId: operatorId, key: key)
                if !page.days.isEmpty {
                    page.region = slug
                    return page
                }
            } catch let rate as RateLimited {
                let until = now + rate.waitMs
                setQuiet(api: until, html: until)
                throw SvitloError.message(Copy.tooMany())
            } catch {
                // A rejected key can still use the public page.
            }
        }
        if ScheduleMath.millis(Date()) < htmlQuiet() {
            throw SvitloError.message(Copy.tooMany())
        }
        do {
            if slug == Regions.kyivOblast && household {
                if let html = try await tolerate({ try await SvitloClient.fetchPage() }) {
                    var page = SvitloParser.parse(html: html)
                    if !page.days.isEmpty {
                        page.region = slug
                        page.operatorId = AlertsEnergy.kyivOblastDtek
                        return page
                    }
                }
            }
            if let html = try await tolerate({ try await SvitloClient.fetchAlerts(region: slug) }) {
                var page = AlertsEnergy.parse(html: html, operatorId: operatorId)
                if !page.days.isEmpty {
                    page.region = slug
                    return page
                }
            }
        } catch let rate as RateLimited {
            setQuiet(api: nil, html: ScheduleMath.millis(Date()) + rate.waitMs)
            throw SvitloError.message(Copy.tooMany())
        }
        let message = slug == Regions.kyivOblast && household ? Copy.bothSitesFailed() : Copy.alertsFailed()
        throw SvitloError.message(message)
    }

    /// A published direct chart, or nil when the older pages should be tried. A quiet clock does not fall through.
    private static func direct(slug: String, operatorId: String, household: Bool, now: Int64) async throws -> ParsedPage? {
        let fromDtek = slug == Regions.kyivOblast && household
        let fromYasno = Yasno.covers(slug)
        if !fromDtek && !fromYasno { return nil }
        if fromDtek && now < dtekQuiet() { throw SvitloError.message(Copy.tooMany()) }
        if fromYasno && now < yasnoQuiet() { throw SvitloError.message(Copy.tooMany()) }
        do {
            let page: ParsedPage?
            if fromDtek {
                let json = try await AddressDirectory.shared.oblastSchedule()
                page = DtekSchedule.parse(json)
            } else {
                page = try await Yasno.load(region: slug, operatorId: operatorId)
            }
            if var page, !page.days.isEmpty {
                page.region = slug
                return page
            }
        } catch let rate as RateLimited {
            let until = now + rate.waitMs
            if fromDtek { setQuiet(dtek: until) } else { setQuiet(yasno: until) }
            throw SvitloError.message(Copy.tooMany())
        } catch {
        }
        return nil
    }

    private static func loadApi(slug: String, operatorId: String, key: String) async throws -> ParsedPage {
        let base = "https://alerts.energy/api/v1/source-registry/areas/\(slug)"
        guard let shutdownsUrl = URL(string: "\(base)/shutdowns"),
              let suppliersUrl = URL(string: "\(base)/suppliers") else {
            throw ApiDenied()
        }
        let shutdowns = try await SvitloClient.fetchJson(shutdownsUrl, apiKey: key)
        let suppliers: String
        do {
            suppliers = try await SvitloClient.fetchJson(suppliersUrl, apiKey: key)
        } catch let rate as RateLimited {
            throw rate
        } catch {
            suppliers = "[]"
        }
        return AlertsEnergy.parseApi(shutdowns: shutdowns, suppliers: suppliers, operatorId: operatorId)
    }

    private static func tolerate(_ block: () async throws -> String) async throws -> String? {
        do {
            return try await block()
        } catch let rate as RateLimited {
            throw rate
        } catch {
            return nil
        }
    }

    private static func apiQuiet() -> Int64 {
        quietLock.lock()
        defer { quietLock.unlock() }
        return apiQuietUntilMs
    }

    private static func htmlQuiet() -> Int64 {
        quietLock.lock()
        defer { quietLock.unlock() }
        return htmlQuietUntilMs
    }

    private static func dtekQuiet() -> Int64 {
        quietLock.lock()
        defer { quietLock.unlock() }
        return dtekQuietUntilMs
    }

    private static func yasnoQuiet() -> Int64 {
        quietLock.lock()
        defer { quietLock.unlock() }
        return yasnoQuietUntilMs
    }

    private static func setQuiet(api: Int64? = nil, html: Int64? = nil, dtek: Int64? = nil, yasno: Int64? = nil) {
        quietLock.lock()
        if let api { apiQuietUntilMs = api }
        if let html { htmlQuietUntilMs = html }
        if let dtek { dtekQuietUntilMs = dtek }
        if let yasno { yasnoQuietUntilMs = yasno }
        quietLock.unlock()
    }
}
