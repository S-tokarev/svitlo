import Foundation
import UserNotifications

enum Notifier {
    static func requestAccess() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        if settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional {
            return true
        }
        return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func scheduleChanged(selection: Selection) {
        guard !selection.group.isEmpty else { return }
        let now = ScheduleMath.millis(Date())
        if AppStore.quietNight(), ScheduleMath.isQuietHour(now) { return }
        let content = UNMutableNotificationContent()
        content.title = "График изменился"
        content.body = details(selection.group, selection.placeLabel, "расписание на сайте обновили")
        content.sound = .default
        let center = UNUserNotificationCenter.current()
        center.add(UNNotificationRequest(identifier: "plan-\(now)", content: content, trigger: nil))
    }

    static func reschedule(selection: Selection, snapshot: Snapshot?) {
        reschedule(places: AppStore.watched(), snapshot: snapshot)
        _ = selection
    }

    static func reschedule(places: [Selection], snapshot: Snapshot?) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        guard let snapshot else { return }
        let now = ScheduleMath.millis(Date())
        let quiet = AppStore.quietNight()
        for selection in places where !selection.group.isEmpty {
            let picture = ScheduleMath.picture(snapshot: snapshot, selection: selection, now: now)
            let place = selection.placeLabel
            if AppStore.warnEnabled() {
                for fireAt in picture.warnAts.prefix(8) {
                    guard !quiet || !ScheduleMath.isQuietHour(fireAt) else { continue }
                    let offAt = fireAt + ScheduleMath.warnBeforeMs
                    let content = UNMutableNotificationContent()
                    content.title = "Свет выключат через 10 мин"
                    content.body = details(selection.group, place, "по графику отключение в \(ScheduleMath.formatDayTime(offAt, now: fireAt))")
                    content.sound = .default
                    schedule(center, id: "warn-\(selection.placeKey)-\(offAt)", at: fireAt, content: content)
                }
            }
            if AppStore.notifyWhenOn() {
                for onAt in picture.appearAts.prefix(8) {
                    guard !quiet || !ScheduleMath.isQuietHour(onAt) else { continue }
                    let content = UNMutableNotificationContent()
                    content.title = "По графику свет включили"
                    content.body = details(selection.group, place, "по графику свет есть")
                    content.sound = .default
                    schedule(center, id: "on-\(selection.placeKey)-\(onAt)", at: onAt, content: content)
                }
            }
        }
    }

    private static func details(_ group: String, _ place: String?, _ tail: String) -> String {
        var text = "Группа \(group)"
        if let place, !place.isEmpty { text += " · \(place)" }
        text += " · \(tail)"
        return text
    }

    private static func schedule(_ center: UNUserNotificationCenter, id: String, at epochMs: Int64, content: UNNotificationContent) {
        let date = ScheduleMath.date(from: epochMs)
        guard date > Date().addingTimeInterval(2) else { return }
        let components = ScheduleMath.calendar().dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }
}
