import Foundation

/// Kyiv oblast tries vyshgorod.in.ua/svitlo first.
/// Every other region, and a failed oblast page, comes from alerts.energy.
enum ScheduleLoad {
    static func load(region: String = Regions.kyivOblast, operatorId: String = "") async throws -> ParsedPage {
        let slug = Regions.normalize(region)
        let household = operatorId.isEmpty || operatorId == AlertsEnergy.kyivOblastDtek
        if slug == Regions.kyivOblast && household {
            if let html = try? await SvitloClient.fetchPage() {
                var page = SvitloParser.parse(html: html)
                if !page.days.isEmpty {
                    page.region = slug
                    page.operatorId = AlertsEnergy.kyivOblastDtek
                    return page
                }
            }
        }
        if let html = try? await SvitloClient.fetchAlerts(region: slug) {
            var page = AlertsEnergy.parse(html: html, operatorId: operatorId)
            if !page.days.isEmpty {
                page.region = slug
                return page
            }
        }
        let message = slug == Regions.kyivOblast && household ? Copy.bothSitesFailed() : Copy.alertsFailed()
        throw SvitloError.message(message)
    }
}
