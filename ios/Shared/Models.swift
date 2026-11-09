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
}

struct Snapshot: Codable, Equatable {
    var fetchedAt: Int64
    var days: [DaySchedule]
    var live: [LiveGroup]
    var houses: [HouseStatus]
    var tomorrowMissing: Bool
    var note: String
}

struct ParsedPage: Equatable {
    var days: [DaySchedule]
    var live: [LiveGroup]
    var houses: [HouseStatus]
    var tomorrowMissing: Bool
    var note: String
}

struct PowerPicture: Equatable {
    var displayOn: Bool?
    var plannedOn: Bool?
    var houseOn: Bool?
    var nextOffAt: Int64?
    var nextOnAt: Int64?
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
        case .system: return "Система"
        case .light: return "Светлая"
        case .dark: return "Тёмная"
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
