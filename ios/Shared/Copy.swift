import Foundation

/// Ukrainian is the base language. Russian and English follow the system language.
enum AppLanguage {
    case uk
    case ru
    case en
}

enum Copy {
    static var language: AppLanguage = systemLanguage()

    static func systemLanguage() -> AppLanguage {
        let code = (Locale.preferredLanguages.first ?? Locale.current.identifier).lowercased()
        if code.hasPrefix("ru") { return .ru }
        if code.hasPrefix("en") { return .en }
        return .uk
    }

    /// Empty or unknown code keeps the system language, with Ukrainian as the fallback.
    static func applyCode(_ code: String?) {
        switch code {
        case "uk": language = .uk
        case "ru": language = .ru
        case "en": language = .en
        default: language = systemLanguage()
        }
    }

    private static func t(_ uk: String, _ ru: String, _ en: String) -> String {
        switch language {
        case .uk: return uk
        case .ru: return ru
        case .en: return en
        }
    }

    static func appTitle() -> String { t("Графік світла", "График света", "Power schedule") }
    static func today() -> String { t("Сьогодні", "Сегодня", "Today") }
    static func tomorrow() -> String { t("Завтра", "Завтра", "Tomorrow") }
    static func refresh() -> String { t("Оновити", "Обновить", "Refresh") }
    static func refreshing() -> String { t("Оновлюємо…", "Обновляем…", "Updating…") }
    static func powerOn() -> String { t("Світло є", "Свет есть", "Power is on") }
    static func powerOff() -> String { t("Світла немає", "Света нет", "Power is off") }
    static func powerOnShort() -> String { t("світло є", "свет есть", "power on") }
    static func powerOffShort() -> String { t("світла немає", "света нет", "power off") }
    static func chooseGroup() -> String { t("Оберіть групу", "Выберите группу", "Choose a group") }
    static func noData() -> String { t("Немає даних", "Нет данных", "No data") }
    static func noDataShort() -> String { t("немає даних", "нет данных", "no data") }
    static func groupMissing() -> String { t("Групу не вибрано", "Группа не выбрана", "No group selected") }
    static func groupSection() -> String { t("Група", "Группа", "Group") }
    static func regionSection() -> String { t("Область", "Область", "Region") }
    static func operatorSection() -> String { t("Оператор", "Оператор", "Operator") }
    static func groupsSoon() -> String {
        t(
            "Оновіть графік, щоб побачити групи цієї області",
            "Обновите график, чтобы увидеть группы этой области",
            "Refresh the schedule to see this region's groups"
        )
    }
    static func addresses() -> String { t("Адреси", "Адреса", "Addresses") }
    static func addressesHint() -> String {
        t(
            "Можна зберегти кілька. Адреса обирає групу.",
            "Можно сохранить несколько. Адрес выбирает группу.",
            "You can save several. The address picks the group."
        )
    }
    static func remove() -> String { t("Прибрати", "Убрать", "Remove") }
    static func addressHint() -> String {
        t(
            "Вулиця і будинок, наприклад Буча Шевченка 11",
            "Улица и дом, например Буча Шевченка 11",
            "Street and house, for example Bucha Shevchenka 11"
        )
    }
    static func search() -> String { t("Знайти групу", "Найти группу", "Find group") }
    static func searching() -> String { t("Шукаємо…", "Ищем…", "Searching…") }
    static func languageSection() -> String { t("Мова", "Язык", "Language") }
    static func appearance() -> String { t("Оформлення", "Оформление", "Appearance") }
    static func themeSystem() -> String { t("Система", "Система", "System") }
    static func themeLight() -> String { t("Світла", "Светлая", "Light") }
    static func themeDark() -> String { t("Темна", "Тёмная", "Dark") }
    static func notifications() -> String { t("Сповіщення", "Уведомления", "Notifications") }
    static func warnBefore() -> String { t("За 10 хвилин до вимкнення", "За 10 минут до отключения", "10 minutes before an outage") }
    static func notifyOn() -> String {
        t(
            "Коли за графіком увімкнуть світло",
            "Когда по графику включат свет",
            "When the schedule turns the power on"
        )
    }
    static func quietNight() -> String { t("Не турбувати вночі", "Не беспокоить ночью", "Quiet at night") }
    static func quietHint() -> String {
        t(
            "З 23:00 до 07:00 за Києвом сповіщення не надходять.",
            "С 23:00 до 07:00 по Киеву уведомления не приходят.",
            "From 23:00 to 07:00 Kyiv time, notifications stay silent."
        )
    }
    static func operatorNote() -> String { t("Повідомлення оператора", "Сообщение оператора", "Operator message") }
    static func openScheduleShort() -> String { t("Графік на сайті", "График на сайте", "Schedule on the site") }
    static func lightWord() -> String { t("світло", "свет", "on") }
    static func offWord() -> String { t("немає", "нет", "off") }
    static func scheduleChanged() -> String { t("Графік змінився", "График изменился", "Schedule changed") }
    static func scheduleChangedTail() -> String {
        t("розклад на сайті оновили", "расписание на сайте обновили", "the schedule on the site was updated")
    }
    static func powerReturned() -> String { t("За графіком світло увімкнули", "По графику свет включили", "Power is back on schedule") }
    static func powerOnTail() -> String { t("за графіком світло є", "по графику свет есть", "power is on by the schedule") }
    static func openApp() -> String { t("Відкрийте застосунок", "Откройте приложение", "Open the app") }
    static func noUpcomingOutage() -> String { t("Вимкнень за графіком немає", "Отключений по графику нет", "No outages on the schedule") }
    static func noUpcomingOutageLong() -> String {
        t(
            "Найближчих вимкнень за графіком немає",
            "Ближайших отключений по графику нет",
            "No upcoming outages on the schedule"
        )
    }
    static func unknownOnTime() -> String { t("Час увімкнення невідомий", "Время включения неизвестно", "Switch-on time is unknown") }
    static func refreshInApp() -> String { t("Оновіть графік у застосунку", "Обновите график в приложении", "Refresh the schedule in the app") }
    static func scheduleWillLoad() -> String { t("Графік з’явиться з сайту", "График подтянется с сайта", "The schedule will load from the site") }
    static func onTimeUnknown() -> String {
        t(
            "Коли увімкнуть, на графіку не сказано",
            "Когда включат, на графике не сказано",
            "The schedule does not say when power returns"
        )
    }
    static func statusUnknown() -> String {
        t(
            "Не вдалося зрозуміти стан цієї групи",
            "Не удалось понять статус этой группы",
            "Could not tell the status of this group"
        )
    }
    static func offUntilMidnight() -> String {
        t(
            "Світла немає до кінця дня, до 24:00",
            "Света нет до конца дня, до 24:00",
            "Power stays off until the end of the day, 24:00"
        )
    }
    static func emptySchedule() -> String {
        t("Сайт віддав сторінку без графіка", "Сайт отдал страницу без графика", "The site returned a page without a schedule")
    }
    static func emptyPage() -> String { t("Сайт віддав порожню сторінку", "Сайт отдал пустую страницу", "The site returned an empty page") }
    static func typeTwoLetters() -> String { t("Введіть хоча б 2 літери", "Введите хотя бы 2 буквы", "Type at least 2 letters") }
    static func nothingFound() -> String { t("Нічого не знайдено", "Ничего не найдено", "Nothing found") }
    static func streetHasNoGroup() -> String {
        t(
            "ДТЕК не вказав групу для цієї вулиці",
            "ДТЕК не указал группу для этой улицы",
            "DTEK did not list a group for this street"
        )
    }
    static func houseMissing() -> String {
        t(
            "Такого будинку немає. Показано будинки цієї вулиці.",
            "Такого дома нет. Показаны дома этой улицы.",
            "That house is not listed. Showing houses on this street."
        )
    }
    static func narrowByHouse() -> String {
        t(
            "Введіть номер будинку, щоб звузити список.",
            "Введите номер дома, чтобы сузить список.",
            "Type a house number to narrow the list."
        )
    }
    static func manyStreets() -> String {
        t("Багато вулиць. Додайте населений пункт.", "Много улиц. Добавьте населённый пункт.", "Too many streets. Add the town.")
    }
    static func typeStreet() -> String { t("Введіть назву вулиці", "Введите название улицы", "Type a street name") }
    static func directoryEmpty() -> String { t("Довідник адрес порожній", "Справочник адресов пуст", "The address directory is empty") }
    static func widgetDescription() -> String {
        t(
            "Стан світла обраної групи за графіком ДТЕК",
            "Статус света выбранной группы по графику ДТЕК",
            "Power status of the selected group from the DTEK schedule"
        )
    }

    static func groupTitle(_ group: String) -> String { t("Група \(group)", "Группа \(group)", "Group \(group)") }

    static func savedPlaceStatus(_ group: String, _ on: Bool?) -> String {
        let state = stateShort(on)
        return t("група \(group) · \(state)", "группа \(group) · \(state)", "group \(group) · \(state)")
    }

    private static func stateShort(_ on: Bool?) -> String {
        switch on {
        case true: return powerOnShort()
        case false: return powerOffShort()
        default: return noDataShort()
        }
    }

    static func placeHeading(_ place: String?, _ group: String) -> String {
        if let place, !place.isEmpty { return t("\(place) · група \(group)", "\(place) · группа \(group)", "\(place) · group \(group)") }
        if group.isEmpty { return groupMissing() }
        return groupTitle(group)
    }

    static func addressHit(_ label: String, _ group: String) -> String {
        if group.isEmpty { return label }
        return t("\(label)  ·  група \(group)", "\(label)  ·  группа \(group)", "\(label)  ·  group \(group)")
    }

    static func updated(_ time: String) -> String { t("Оновлено \(time)", "Обновлено \(time)", "Updated \(time)") }
    static func through(_ duration: String) -> String { t("через \(duration)", "через \(duration)", "in \(duration)") }
    static func offAt(_ time: String) -> String { t("За графіком вимкнуть о \(time)", "По графику выключат в \(time)", "Scheduled off at \(time)") }
    static func onAt(_ time: String) -> String { t("За графіком увімкнуть о \(time)", "По графику включат в \(time)", "Scheduled on at \(time)") }
    static func offAtIn(_ time: String, _ duration: String) -> String { "\(offAt(time)) · \(through(duration))" }
    static func onAtIn(_ time: String, _ duration: String) -> String { "\(onAt(time)) · \(through(duration))" }
    static func offUntil(_ duration: String) -> String {
        t("Світла немає до 24:00 · через \(duration)", "Света нет до 24:00 · через \(duration)", "Power off until 24:00 · in \(duration)")
    }
    static func widgetFooter(_ group: String, _ time: String) -> String {
        t("Група \(group) · оновлено \(time)", "Группа \(group) · обновлено \(time)", "Group \(group) · updated \(time)")
    }
    static func warnTitle(_ minutes: Int) -> String {
        if minutes <= 1 {
            return t("Світло вимкнуть з хвилини на хвилину", "Свет выключат с минуты на минуту", "Power goes off any minute")
        }
        return t("Світло вимкнуть через \(minutes) хв", "Свет выключат через \(minutes) мин", "Power goes off in \(minutes) min")
    }
    static func offDetail(_ time: String) -> String {
        t("за графіком вимкнення о \(time)", "по графику отключение в \(time)", "scheduled off at \(time)")
    }
    static func notice(_ group: String, _ place: String?, _ tail: String) -> String {
        let head = groupTitle(group)
        if let place, !place.isEmpty { return "\(head) · \(place) · \(tail)" }
        return "\(head) · \(tail)"
    }
    static func siteAnswered(_ code: Int) -> String { t("Сайт відповів \(code)", "Сайт ответил \(code)", "The site answered \(code)") }
    static func bothSitesFailed() -> String {
        t(
            "Не вдалося взяти графік ні з vyshgorod.in.ua, ні з alerts.energy",
            "Не удалось взять график ни с vyshgorod.in.ua, ни с alerts.energy",
            "Could not load the schedule from vyshgorod.in.ua or alerts.energy"
        )
    }
    static func fallbackCredit() -> String {
        t(
            "Графік з alerts.energy: основний сайт не відповів",
            "График с alerts.energy: основной сайт не ответил",
            "Schedule from alerts.energy: the main site did not respond"
        )
    }
    static func alertsFailed() -> String {
        t(
            "Не вдалося взяти графік з alerts.energy",
            "Не удалось взять график с alerts.energy",
            "Could not load the schedule from alerts.energy"
        )
    }
    static func alertsCredit() -> String {
        t("Графік з alerts.energy", "График с alerts.energy", "Schedule from alerts.energy")
    }
    static func dtekAnswered(_ code: Int) -> String { t("ДТЕК відповів \(code)", "ДТЕК ответил \(code)", "DTEK answered \(code)") }
    static func dtekClosed() -> String { t("ДТЕК тимчасово закрив довідник", "ДТЕК временно закрыл справочник", "DTEK temporarily closed the directory") }
    static func dtekNoKey() -> String { t("ДТЕК не видав ключ запиту", "ДТЕК не выдал ключ запроса", "DTEK did not issue a request key") }
    static func dtekSilent() -> String { t("ДТЕК не відповів", "ДТЕК не ответил", "DTEK did not respond") }
    static func tomorrowAt(_ clock: String) -> String { t("завтра \(clock)", "завтра \(clock)", "tomorrow \(clock)") }

    static func month(_ month: Int, language: AppLanguage = Copy.language) -> String {
        let names: [String]
        switch language {
        case .uk: names = ukMonths
        case .ru: names = ruMonths
        case .en: names = enMonths
        }
        guard month >= 1, month <= names.count else { return "" }
        return names[month - 1]
    }

    static func duration(hours: Int, minutes: Int) -> String {
        switch language {
        case .uk: return span(hours, minutes, "год", "хв")
        case .ru: return span(hours, minutes, "ч", "мин")
        case .en: return span(hours, minutes, "h", "min")
        }
    }

    private static func span(_ hours: Int, _ minutes: Int, _ hour: String, _ minute: String) -> String {
        if hours > 0 && minutes > 0 { return "\(hours) \(hour) \(minutes) \(minute)" }
        if hours > 0 { return "\(hours) \(hour)" }
        return "\(minutes) \(minute)"
    }

    private static let ukMonths = [
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня",
    ]
    private static let ruMonths = [
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    ]
    private static let enMonths = [
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ]
}
