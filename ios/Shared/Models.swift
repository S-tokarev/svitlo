import Foundation

struct Outage: Codable, Equatable {
    var startMin: Int
    var endMin: Int
}

struct DaySchedule: Codable, Equatable {
    var day: String
    var label: String
    var updatedDate: String
    var updatedTime: String
    var groups: [String: [Outage]]
    /// Per queue, "dd.MM.yyyy HH:mm" in Europe/Kyiv.
    var groupUpdated: [String: String]
}

struct LiveGroup: Codable, Equatable {
    var id: String
    var powerOn: Bool
    var toward: String
    var seconds: Int
}

struct HouseStatus: Codable, Equatable {
    var group: String
    var powerOn: Bool
    var address: String
    var complex: String?
    var since: String?
}

struct AddressHit: Codable, Equatable, Identifiable {
    var city: String
    var street: String
    var house: String
    var group: String

    var id: String { "\(city)|\(street)|\(house)|\(group)" }
    var label: String {
        [city, street, house].filter { !$0.isEmpty }.joined(separator: ", ")
    }
}

struct Selection: Codable, Equatable {
    var group: String = ""
    var street: String?
    var house: String?
    var city: String?

    var placeLabel: String? {
        guard let street, let house, !street.isEmpty, !house.isEmpty else { return nil }
        return [city, street, house].compactMap { value in
            guard let value, !value.isEmpty else { return nil }
            return value
        }.joined(separator: ", ")
    }

    var placeKey: String {
        [group, city ?? "", street ?? "", house ?? ""].joined(separator: "\u{1f}")
    }
}

/// Household network operator inside one region. Railway groups are not listed.
struct GridOperator: Codable, Equatable, Identifiable {
    var id: String
    var name: String
}

struct Snapshot: Codable, Equatable {
    var fetchedAt: Int64
    var days: [DaySchedule]
    var live: [LiveGroup]
    var houses: [HouseStatus]
    var tomorrowMissing: Bool
    var note: String
    /// Empty when the schedule came from vyshgorod.in.ua. "alerts.energy" is the fallback.
    var source: String
    /// alerts.energy region slug. Blank means a schedule saved before the region picker.
    var region: String
    var operatorId: String
    var operators: [GridOperator]

    init(
        fetchedAt: Int64,
        days: [DaySchedule],
        live: [LiveGroup],
        houses: [HouseStatus],
        tomorrowMissing: Bool,
        note: String,
        source: String = "",
        region: String = "",
        operatorId: String = "",
        operators: [GridOperator] = []
    ) {
        self.fetchedAt = fetchedAt
        self.days = days
        self.live = live
        self.houses = houses
        self.tomorrowMissing = tomorrowMissing
        self.note = note
        self.source = source
        self.region = region
        self.operatorId = operatorId
        self.operators = operators
    }

    private enum CodingKeys: String, CodingKey {
        case fetchedAt, days, live, houses, tomorrowMissing, note, source, region, operatorId, operators
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        fetchedAt = try container.decode(Int64.self, forKey: .fetchedAt)
        days = try container.decode([DaySchedule].self, forKey: .days)
        live = try container.decode([LiveGroup].self, forKey: .live)
        houses = try container.decode([HouseStatus].self, forKey: .houses)
        tomorrowMissing = try container.decode(Bool.self, forKey: .tomorrowMissing)
        note = try container.decodeIfPresent(String.self, forKey: .note) ?? ""
        source = try container.decodeIfPresent(String.self, forKey: .source) ?? ""
        region = try container.decodeIfPresent(String.self, forKey: .region) ?? ""
        operatorId = try container.decodeIfPresent(String.self, forKey: .operatorId) ?? ""
        operators = try container.decodeIfPresent([GridOperator].self, forKey: .operators) ?? []
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(fetchedAt, forKey: .fetchedAt)
        try container.encode(days, forKey: .days)
        try container.encode(live, forKey: .live)
        try container.encode(houses, forKey: .houses)
        try container.encode(tomorrowMissing, forKey: .tomorrowMissing)
        try container.encode(note, forKey: .note)
        try container.encode(source, forKey: .source)
        try container.encode(region, forKey: .region)
        try container.encode(operatorId, forKey: .operatorId)
        try container.encode(operators, forKey: .operators)
    }
}

struct ParsedPage: Equatable {
    var days: [DaySchedule]
    var live: [LiveGroup]
    var houses: [HouseStatus]
    var tomorrowMissing: Bool
    var note: String
    var source: String = ""
    var region: String = ""
    var operatorId: String = ""
    var operators: [GridOperator] = []
}

struct PowerPicture: Equatable {
    var displayOn: Bool?
    var plannedOn: Bool?
    var houseOn: Bool?
    var nextOffAt: Int64?
    var nextOnAt: Int64?
    var offUntil: Int64?
    var dueWarnOffAt: Int64?
    var warnAts: [Int64]
    var appearAts: [Int64]
    var outagesToday: [Outage]
    var outagesTomorrow: [Outage]
    var scheduleLabel: String
    var updatedLabel: String
    var usingHouse: Bool
}

enum ThemeMode: String, Codable, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }
    var title: String {
        switch self {
        case .system: return Copy.themeSystem()
        case .light: return Copy.themeLight()
        case .dark: return Copy.themeDark()
        }
    }
}

struct AddressLookup: Equatable {
    var hits: [AddressHit]
    var hint: String?
}

enum SvitloError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        if case .message(let text) = self { return text }
        return nil
    }
}
