import Foundation

enum AppGroup {
    static let id = "group.ua.in.vyshgorod.svitlo"
    static let refreshTaskId = "ua.in.vyshgorod.svitlo.refresh"

    static var defaults: UserDefaults {
        UserDefaults(suiteName: id) ?? .standard
    }

    static var cacheDirectory: URL {
        if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: id) {
            return container
        }
        return FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
    }
}

enum AppStore {
    private static let selectionKey = "selection"
    private static let placesKey = "places"
    private static let snapshotKey = "snapshot"
    private static let warnKey = "warn"
    private static let notifyKey = "notify_on"
    private static let quietKey = "quiet_night"
    private static let themeKey = "theme"
    private static let languageKey = "language"
    private static let regionKey = "region"
    private static let operatorKey = "operator"

    static func loadSelection() -> Selection {
        decode(Selection.self, key: selectionKey) ?? Selection()
    }

    static func saveSelection(_ selection: Selection) {
        var places = loadPlaces().filter { $0.placeKey != selection.placeKey }
        if !selection.group.isEmpty { places.append(selection) }
        savePlaces(Array(places.suffix(8)), focus: selection)
    }

    static func loadPlaces() -> [Selection] {
        if AppGroup.defaults.data(forKey: placesKey) != nil {
            return (decode([Selection].self, key: placesKey) ?? []).filter { !$0.group.isEmpty }
        }
        let one = loadSelection()
        return one.group.isEmpty ? [] : [one]
    }

    static func savePlaces(_ places: [Selection], focus: Selection) {
        encode(places.filter { !$0.group.isEmpty }, key: placesKey)
        encode(focus, key: selectionKey)
    }

    static func watched() -> [Selection] {
        let places = loadPlaces()
        if !places.isEmpty { return places }
        let focus = loadSelection()
        return focus.group.isEmpty ? [] : [focus]
    }

    static func loadSnapshot() -> Snapshot? {
        decode(Snapshot.self, key: snapshotKey)
    }

    static func saveSnapshot(_ snapshot: Snapshot) {
        encode(snapshot, key: snapshotKey)
    }

    static func warnEnabled() -> Bool { AppGroup.defaults.object(forKey: warnKey) as? Bool ?? true }
    static func setWarnEnabled(_ value: Bool) { AppGroup.defaults.set(value, forKey: warnKey) }

    static func notifyWhenOn() -> Bool { AppGroup.defaults.object(forKey: notifyKey) as? Bool ?? true }
    static func setNotifyWhenOn(_ value: Bool) { AppGroup.defaults.set(value, forKey: notifyKey) }

    static func quietNight() -> Bool { AppGroup.defaults.bool(forKey: quietKey) }
    static func setQuietNight(_ value: Bool) { AppGroup.defaults.set(value, forKey: quietKey) }

    static func themeMode() -> ThemeMode {
        ThemeMode(rawValue: AppGroup.defaults.string(forKey: themeKey) ?? "") ?? .system
    }

    static func setThemeMode(_ mode: ThemeMode) {
        AppGroup.defaults.set(mode.rawValue, forKey: themeKey)
    }

    static func languageCode() -> String? {
        AppGroup.defaults.string(forKey: languageKey)
    }

    static func resolvedLanguage() -> AppLanguage {
        switch languageCode() {
        case "uk": return .uk
        case "ru": return .ru
        case "en": return .en
        default: return Copy.systemLanguage()
        }
    }

    static func setLanguage(_ language: AppLanguage) {
        let code: String
        switch language {
        case .uk: code = "uk"
        case .ru: code = "ru"
        case .en: code = "en"
        }
        AppGroup.defaults.set(code, forKey: languageKey)
    }

    static func region() -> String {
        Regions.normalize(AppGroup.defaults.string(forKey: regionKey))
    }

    static func setRegion(_ slug: String) {
        AppGroup.defaults.set(Regions.normalize(slug), forKey: regionKey)
    }

    static func operatorId() -> String {
        AppGroup.defaults.string(forKey: operatorKey) ?? ""
    }

    static func setOperator(_ id: String) {
        AppGroup.defaults.set(id, forKey: operatorKey)
    }

    private static func encode<T: Encodable>(_ value: T, key: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        AppGroup.defaults.set(data, forKey: key)
    }

    private static func decode<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = AppGroup.defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}
