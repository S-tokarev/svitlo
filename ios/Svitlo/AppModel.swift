import BackgroundTasks
import Foundation
import WidgetKit

extension Notification.Name {
    static let svitloUpdated = Notification.Name("svitloUpdated")
}

enum RefreshScheduler {
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: AppGroup.refreshTaskId, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else { return }
            let work = Task {
                do {
                    try await ScheduleRefresh.run()
                    refresh.setTaskCompleted(success: true)
                } catch {
                    refresh.setTaskCompleted(success: false)
                }
                schedule()
            }
            refresh.expirationHandler = { work.cancel() }
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: AppGroup.refreshTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

enum ScheduleRefresh {
    static func run() async throws {
        let requestedRegion = AppStore.region()
        let requestedOperator = AppStore.operatorId()
        let requestedKey = AppStore.apiKey()
        let parsed = try await ScheduleLoad.load(region: requestedRegion, operatorId: requestedOperator, apiKey: requestedKey)
        if !Regions.same(requestedRegion, AppStore.region()) || AppStore.operatorId() != requestedOperator || AppStore.apiKey() != requestedKey { return }
        let previous = AppStore.loadSnapshot()
        let snapshot = Snapshot(
            fetchedAt: ScheduleMath.millis(Date()),
            days: parsed.days,
            live: parsed.live,
            houses: parsed.houses,
            tomorrowMissing: parsed.tomorrowMissing,
            note: parsed.note,
            source: parsed.source,
            region: parsed.region.isEmpty ? AppStore.region() : parsed.region,
            operatorId: parsed.operatorId,
            operators: parsed.operators
        )
        if let previous, Regions.same(previous.region, snapshot.region) {
            for place in alertPlaces(snapshot) where !place.group.isEmpty &&
                ScheduleMath.scheduleChanged(previous: previous, next: snapshot, group: place.group, now: snapshot.fetchedAt) {
                Notifier.scheduleChanged(selection: place)
            }
        }
        AppStore.saveSnapshot(snapshot)
        Notifier.reschedule(places: AppStore.watched(), snapshot: snapshot)
        WidgetCenter.shared.reloadAllTimelines()
        NotificationCenter.default.post(name: .svitloUpdated, object: nil)
    }

    /// Saved streets belong to Kyiv oblast. Another region alerts only the chosen group.
    private static func alertPlaces(_ snapshot: Snapshot) -> [Selection] {
        let region = AppStore.region()
        guard Regions.same(snapshot.region, region) else { return [] }
        let focus = AppStore.loadSelection()
        if region != Regions.kyivOblast {
            return focus.group.isEmpty ? [] : [Selection(group: focus.group)]
        }
        return AppStore.watched()
    }
}

@MainActor
final class AppModel: ObservableObject {
    @Published var selection = AppStore.loadSelection()
    @Published var places = AppStore.loadPlaces()
    @Published var snapshot = AppStore.loadSnapshot()
    @Published var loading = false
    @Published var error: String?
    @Published var query = ""
    @Published var results: [AddressHit] = []
    @Published var searching = false
    @Published var searchError: String?
    @Published var warnEnabled = AppStore.warnEnabled()
    @Published var notifyOn = AppStore.notifyWhenOn()
    @Published var quietNight = AppStore.quietNight()
    @Published var theme = AppStore.themeMode()
    @Published var language = AppStore.resolvedLanguage()
    @Published var region = AppStore.region()
    @Published var operatorId = AppStore.operatorId()
    @Published var apiKey = AppStore.apiKey()
    @Published var now = Date()

    init() {
        Copy.language = language
    }

    private var clock: Task<Void, Never>?

    func startup() async {
        clock?.cancel()
        clock = Task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                now = Date()
            }
        }
        await refreshIfStale()
        _ = await Notifier.requestAccess()
        RefreshScheduler.schedule()
    }

    func refreshIfStale() async {
        if let stored = AppStore.loadSnapshot(), stored.fetchedAt != snapshot?.fetchedAt {
            snapshot = stored
        }
        let fetched = snapshot?.fetchedAt ?? 0
        if snapshot == nil || ScheduleMath.millis(Date()) - fetched >= 15 * 60 * 1000 {
            await refresh(manual: false)
        } else {
            Notifier.reschedule(selection: selection, snapshot: snapshot)
        }
    }

    func refresh(manual: Bool = true) async {
        loading = true
        if manual { error = nil }
        do {
            try await ScheduleRefresh.run()
            snapshot = AppStore.loadSnapshot()
            error = nil
        } catch {
            snapshot = AppStore.loadSnapshot()
            if manual || snapshot == nil {
                self.error = error.localizedDescription
            }
        }
        loading = false
    }

    func selectGroup(_ group: String) {
        selection = Selection(group: group)
        results = []
        AppStore.savePlaces(places, focus: selection)
        Notifier.reschedule(places: AppStore.watched(), snapshot: snapshot)
        WidgetCenter.shared.reloadAllTimelines()
    }

    func focusPlace(_ place: Selection) {
        selection = place
        AppStore.savePlaces(places, focus: place)
        WidgetCenter.shared.reloadAllTimelines()
    }

    func removePlace(_ place: Selection) {
        places.removeAll { $0.placeKey == place.placeKey }
        if selection.placeKey == place.placeKey {
            selection = places.last ?? Selection()
        }
        AppStore.savePlaces(places, focus: selection)
        Notifier.reschedule(places: AppStore.watched(), snapshot: snapshot)
        WidgetCenter.shared.reloadAllTimelines()
    }

    func pick(_ hit: AddressHit) async {
        if hit.group.isEmpty {
            searching = true
            searchError = nil
            results = []
            do {
                let lookup = try await AddressDirectory.shared.houses(
                    city: hit.city,
                    street: hit.street,
                    houseQuery: AddressMatch.houseFilter(query)
                )
                results = lookup.hits
                searchError = lookup.hint ?? (lookup.hits.isEmpty ? Copy.streetHasNoGroup() : nil)
            } catch {
                searchError = error.localizedDescription
            }
            searching = false
            return
        }
        selection = Selection(group: hit.group, street: hit.street, house: hit.house, city: hit.city)
        places.removeAll { $0.placeKey == selection.placeKey }
        places.append(selection)
        if places.count > 8 { places.removeFirst(places.count - 8) }
        query = ""
        results = []
        persistSelection()
    }

    func search() async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= 2 else {
            searchError = Copy.typeTwoLetters()
            return
        }
        searching = true
        searchError = nil
        do {
            let lookup = try await AddressDirectory.shared.search(text)
            results = lookup.hits
            searchError = lookup.hint ?? (lookup.hits.isEmpty ? Copy.nothingFound() : nil)
        } catch {
            searchError = error.localizedDescription
        }
        searching = false
    }

    func setWarn(_ value: Bool) {
        warnEnabled = value
        AppStore.setWarnEnabled(value)
        Notifier.reschedule(places: AppStore.watched(), snapshot: snapshot)
    }

    func setNotify(_ value: Bool) {
        notifyOn = value
        AppStore.setNotifyWhenOn(value)
        Notifier.reschedule(places: AppStore.watched(), snapshot: snapshot)
    }

    func setQuiet(_ value: Bool) {
        quietNight = value
        AppStore.setQuietNight(value)
        Notifier.reschedule(places: AppStore.watched(), snapshot: snapshot)
    }

    func setTheme(_ mode: ThemeMode) {
        theme = mode
        AppStore.setThemeMode(mode)
    }

    func setLanguage(_ value: AppLanguage) {
        Copy.language = value
        language = value
        AppStore.setLanguage(value)
        Notifier.reschedule(places: AppStore.watched(), snapshot: snapshot)
        WidgetCenter.shared.reloadAllTimelines()
    }

    func setRegion(_ slug: String) {
        let next = Regions.normalize(slug)
        if next == region { return }
        region = next
        operatorId = ""
        AppStore.setRegion(next)
        AppStore.setOperator("")
        selection = Selection(group: selection.group)
        query = ""
        results = []
        searchError = nil
        AppStore.savePlaces(places, focus: selection)
        Task { await refresh() }
    }

    func setOperator(_ id: String) {
        let current = operatorId.isEmpty ? (snapshot?.operatorId ?? "") : operatorId
        if id == current { return }
        operatorId = id
        AppStore.setOperator(id)
        Task { await refresh() }
    }

    func setApiKey(_ value: String) {
        let next = SvitloClient.normalizeApiKey(value)
        if next == apiKey { return }
        apiKey = next
        AppStore.setApiKey(next)
        Task { await refresh() }
    }

    func applyExternalUpdate() {
        snapshot = AppStore.loadSnapshot()
        selection = AppStore.loadSelection()
        places = AppStore.loadPlaces()
    }

    private func persistSelection() {
        AppStore.savePlaces(places, focus: selection)
        Notifier.reschedule(places: AppStore.watched(), snapshot: snapshot)
        WidgetCenter.shared.reloadAllTimelines()
    }
}
