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
    private static let snapshotKey = "snapshot"
    private static let warnKey = "warn"
    private static let notifyKey = "notify_on"
    private static let quietKey = "quiet_night"
    private static let themeKey = "theme"

    static func loadSelection() -> Selection {
        decode(Selection.self, key: selectionKey) ?? Selection()
    }

    static func saveSelection(_ selection: Selection) {
        encode(selection, key: selectionKey)
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

    private static func encode<T: Encodable>(_ value: T, key: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        AppGroup.defaults.set(data, forKey: key)
    }

    private static func decode<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = AppGroup.defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}
