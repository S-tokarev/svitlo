package ua.`in`.vyshgorod.svitlo.data

import java.util.Locale

/** Ukrainian is the base language. Russian and English follow the system language. */
enum class AppLanguage {
    Uk,
    Ru,
    En,
    ;

    fun code(): String = when (this) {
        Uk -> "uk"
        Ru -> "ru"
        En -> "en"
    }
}

object L10n {
    @Volatile
    var language: AppLanguage = fromLocale(Locale.getDefault())

    private fun fromLocale(locale: Locale): AppLanguage {
        return when (locale.language.lowercase(Locale.ROOT)) {
            "ru" -> AppLanguage.Ru
            "en" -> AppLanguage.En
            else -> AppLanguage.Uk
        }
    }

    /** Empty or unknown code keeps the system language, with Ukrainian as the fallback. */
    fun applyCode(code: String?) {
        language = when (code) {
            "uk" -> AppLanguage.Uk
            "ru" -> AppLanguage.Ru
            "en" -> AppLanguage.En
            else -> fromLocale(Locale.getDefault())
        }
    }

    private fun t(uk: String, ru: String, en: String): String = when (language) {
        AppLanguage.Uk -> uk
        AppLanguage.Ru -> ru
        AppLanguage.En -> en
    }

    fun appTitle() = t("Графік світла", "График света", "Power schedule")
    fun today() = t("Сьогодні", "Сегодня", "Today")
    fun tomorrow() = t("Завтра", "Завтра", "Tomorrow")
    fun refresh() = t("Оновити", "Обновить", "Refresh")
    fun refreshing() = t("Оновлюємо…", "Обновляем…", "Updating…")
    fun refreshingShort() = t("Оновлення", "Обновление", "Updating")
    fun powerOn() = t("Світло є", "Свет есть", "Power is on")
    fun powerOff() = t("Світла немає", "Света нет", "Power is off")
    fun powerOnShort() = t("світло є", "свет есть", "power on")
    fun powerOffShort() = t("світла немає", "света нет", "power off")
    fun chooseGroup() = t("Оберіть групу", "Выберите группу", "Choose a group")
    fun noData() = t("Немає даних", "Нет данных", "No data")
    fun noDataShort() = t("немає даних", "нет данных", "no data")
    fun groupMissing() = t("Групу не вибрано", "Группа не выбрана", "No group selected")
    fun groupSection() = t("Група", "Группа", "Group")
    fun regionSection() = t("Область", "Область", "Region")
    fun operatorSection() = t("Оператор", "Оператор", "Operator")
    fun groupsSoon() = t(
        "Оновіть графік, щоб побачити групи цієї області",
        "Обновите график, чтобы увидеть группы этой области",
        "Refresh the schedule to see this region's groups",
    )
    fun addresses() = t("Адреси", "Адреса", "Addresses")
    fun addressesHint() = t(
        "Можна зберегти кілька. Адреса обирає групу.",
        "Можно сохранить несколько. Адрес выбирает группу.",
        "You can save several. The address picks the group.",
    )
    fun remove() = t("Прибрати", "Убрать", "Remove")
    fun addressHint() = t(
        "Вулиця і будинок, наприклад Буча Шевченка 11",
        "Улица и дом, например Буча Шевченка 11",
        "Street and house, for example Bucha Shevchenka 11",
    )
    fun search() = t("Знайти групу", "Найти группу", "Find group")
    fun searching() = t("Шукаємо…", "Ищем…", "Searching…")
    fun searchDesktop() = t("Знайти", "Найти", "Find")
    fun languageSection() = t("Мова", "Язык", "Language")
    fun appearance() = t("Оформлення", "Оформление", "Appearance")
    fun themeSystem() = t("Система", "Система", "System")
    fun themeLight() = t("Світла", "Светлая", "Light")
    fun themeDark() = t("Темна", "Тёмная", "Dark")
    fun notifications() = t("Сповіщення", "Уведомления", "Notifications")
    fun warnBefore() = t("За 10 хвилин до вимкнення", "За 10 минут до отключения", "10 minutes before an outage")
    fun notifyOn() = t(
        "Коли за графіком увімкнуть світло",
        "Когда по графику включат свет",
        "When the schedule turns the power on",
    )
    fun quietNight() = t("Не турбувати вночі", "Не беспокоить ночью", "Quiet at night")
    fun quietNightDesktop() = t(
        "Не турбувати вночі, з 23:00 до 07:00",
        "Не беспокоить ночью, с 23:00 до 07:00",
        "Quiet at night, from 23:00 to 07:00",
    )
    fun quietHint() = t(
        "З 23:00 до 07:00 за Києвом сповіщення не надходять.",
        "С 23:00 до 07:00 по Киеву уведомления не приходят.",
        "From 23:00 to 07:00 Kyiv time, notifications stay silent.",
    )
    fun allowNotifications() = t("Дозволити сповіщення", "Разрешить уведомления", "Allow notifications")
    fun batteryHint() = t(
        "Щоб попередження приходило вчасно, дозвольте застосунку працювати у фоні.",
        "Чтобы предупреждение приходило вовремя, разрешите приложению работать в фоне.",
        "Allow the app to run in the background so the warning arrives on time.",
    )
    fun allowBackground() = t("Дозволити роботу у фоні", "Разрешить работу в фоне", "Allow background")
    fun operatorNote() = t("Повідомлення оператора", "Сообщение оператора", "Operator message")
    fun dtekCredit() = t(
        "ДТЕК · Київські регіональні електромережі",
        "ДТЕК · Киевские региональные электросети",
        "DTEK · Kyiv regional electricity networks",
    )
    fun openSchedule() = t("Відкрити графік на сайті", "Открыть график на сайте", "Open the schedule on the site")
    fun openScheduleShort() = t("Графік на сайті", "График на сайте", "Schedule on the site")
    fun none() = t("немає", "нет", "none")
    fun now() = t("зараз", "сейчас", "now")
    fun lightWord() = t("світло", "свет", "on")
    fun offWord() = t("немає", "нет", "off")
    fun channelName() = t("Світло", "Свет", "Power")
    fun channelDescription() = t(
        "Поява світла, попередження за 10 хвилин і зміна графіка",
        "Появление света, предупреждение за 10 минут и смена графика",
        "Power returning, a 10-minute warning, and schedule changes",
    )
    fun scheduleChanged() = t("Графік змінився", "График изменился", "Schedule changed")
    fun scheduleChangedTail() = t(
        "розклад на сайті оновили",
        "расписание на сайте обновили",
        "the schedule on the site was updated",
    )
    fun powerReturned() = t("За графіком світло увімкнули", "По графику свет включили", "Power is back on schedule")
    fun powerShouldReturn() = t(
        "За графіком світло мають увімкнути",
        "По графику свет должны включить",
        "Power should come back on schedule",
    )
    fun powerOnTail() = t("за графіком світло є", "по графику свет есть", "power is on by the schedule")
    fun waitForPower() = t("за графіком чекайте ввімкнення", "по графику ждите включения", "wait for power by the schedule")
    fun anyMinute() = t(
        "Світло вимкнуть з хвилини на хвилину",
        "Свет выключат с минуты на минуту",
        "Power goes off any minute",
    )
    fun openApp() = t("Відкрийте застосунок", "Откройте приложение", "Open the app")
    fun noUpcomingOutage() = t("Вимкнень за графіком немає", "Отключений по графику нет", "No outages on the schedule")
    fun noUpcomingOutageLong() = t(
        "Найближчих вимкнень за графіком немає",
        "Ближайших отключений по графику нет",
        "No upcoming outages on the schedule",
    )
    fun unknownOnTime() = t("Час увімкнення невідомий", "Время включения неизвестно", "Switch-on time is unknown")
    fun pullToRefresh() = t(
        "Потягніть оновлення в застосунку",
        "Потяните обновление в приложении",
        "Refresh from the app",
    )
    fun refreshInApp() = t(
        "Оновіть графік у застосунку",
        "Обновите график в приложении",
        "Refresh the schedule in the app",
    )
    fun scheduleWillLoad() = t("Графік з’явиться з сайту", "График подтянется с сайта", "The schedule will load from the site")
    fun onTimeUnknown() = t(
        "Коли увімкнуть, на графіку не сказано",
        "Когда включат, на графике не сказано",
        "The schedule does not say when power returns",
    )
    fun statusUnknown() = t(
        "Не вдалося зрозуміти стан цієї групи",
        "Не удалось понять статус этой группы",
        "Could not tell the status of this group",
    )
    fun offUntilMidnight() = t(
        "Світла немає до кінця дня, до 24:00",
        "Света нет до конца дня, до 24:00",
        "Power stays off until the end of the day, 24:00",
    )
    fun plannedShouldBeOn() = t(
        "За графіком групи світло має бути",
        "По графику группы свет должен быть",
        "The group schedule says power should be on",
    )
    fun plannedShouldBeOff() = t(
        "За графіком групи світла бути не повинно",
        "По графику группы света быть не должно",
        "The group schedule says power should be off",
    )
    fun refreshFailed() = t("Не вдалося завантажити графік", "Не удалось загрузить график", "Could not load the schedule")
    fun updateFailed() = t("Не вдалося оновити графік", "Не удалось обновить график", "Could not refresh the schedule")
    fun emptySchedule() = t(
        "Сайт віддав сторінку без графіка",
        "Сайт отдал страницу без графика",
        "The site returned a page without a schedule",
    )
    fun bothSitesFailed() = t(
        "Не вдалося взяти графік ні з vyshgorod.in.ua, ні з alerts.energy",
        "Не удалось взять график ни с vyshgorod.in.ua, ни с alerts.energy",
        "Could not load the schedule from vyshgorod.in.ua or alerts.energy",
    )
    fun alertsFailed() = t(
        "Не вдалося взяти графік з alerts.energy",
        "Не удалось взять график с alerts.energy",
        "Could not load the schedule from alerts.energy",
    )
    fun tooMany() = t(
        "Сайт просить зачекати: забагато запитів. Попередній графік лишається на екрані.",
        "Сайт просит подождать: слишком много запросов. Предыдущий график остаётся на экране.",
        "The site asked us to wait: too many requests. The previous schedule stays on screen.",
    )
    fun apiKeySection() = t("Ключ API", "Ключ API", "API key")
    fun apiKeyHint() = t(
        "Свій ключ з кабінету alerts.energy. Тоді графік береться з API, а не зі сторінки, яку сайт блокує. Ключ лишається тільки на цьому пристрої.",
        "Свой ключ из кабинета alerts.energy. Тогда график берётся из API, а не со страницы, которую сайт блокирует. Ключ остаётся только на этом устройстве.",
        "Your key from the alerts.energy account. The schedule then comes from the API instead of the page the site blocks. The key stays on this device.",
    )
    fun saveKey() = t("Зберегти ключ", "Сохранить ключ", "Save key")
    fun fallbackCredit() = t(
        "Графік з alerts.energy: основний сайт не відповів",
        "График с alerts.energy: основной сайт не ответил",
        "Schedule from alerts.energy: the main site did not respond",
    )
    fun alertsCredit() = t(
        "Графік з alerts.energy",
        "График с alerts.energy",
        "Schedule from alerts.energy",
    )
    fun yasnoCredit() = t("Графік з YASNO", "График с YASNO", "Schedule from YASNO")
    fun dtekScheduleCredit() = t(
        "Графік з сайту ДТЕК",
        "График с сайта ДТЕК",
        "Schedule from the DTEK site",
    )
    fun emptyPage() = t("Сайт віддав порожню сторінку", "Сайт отдал пустую страницу", "The site returned an empty page")
    fun windowMissing() = t("Вікно застосунку не знайдено", "Окно приложения не найдено", "App window was not found")
    fun typeTwoLetters() = t("Введіть хоча б 2 літери", "Введите хотя бы 2 буквы", "Type at least 2 letters")
    fun typeTwoLettersDesktop() = t(
        "Введіть хоча б дві літери",
        "Введите хотя бы две буквы",
        "Type at least two letters",
    )
    fun nothingFound() = t("Нічого не знайдено", "Ничего не найдено", "Nothing found")
    fun searchFailed() = t("Пошук не відповів", "Поиск не ответил", "Search did not respond")
    fun streetHasNoGroup() = t(
        "ДТЕК не вказав групу для цієї вулиці",
        "ДТЕК не указал группу для этой улицы",
        "DTEK did not list a group for this street",
    )
    fun houseMissing() = t(
        "Такого будинку немає. Показано будинки цієї вулиці.",
        "Такого дома нет. Показаны дома этой улицы.",
        "That house is not listed. Showing houses on this street.",
    )
    fun narrowByHouse() = t(
        "Введіть номер будинку, щоб звузити список.",
        "Введите номер дома, чтобы сузить список.",
        "Type a house number to narrow the list.",
    )
    fun manyStreets() = t(
        "Багато вулиць. Додайте населений пункт.",
        "Много улиц. Добавьте населённый пункт.",
        "Too many streets. Add the town.",
    )
    fun typeStreet() = t("Введіть назву вулиці", "Введите название улицы", "Type a street name")
    fun directoryEmpty() = t("Довідник адрес порожній", "Справочник адресов пуст", "The address directory is empty")
    fun noRequestKey() = t("Немає ключа запиту", "Нет ключа запроса", "No request key")
    fun trayHint() = t(
        "Вікно можна закрити: програма залишиться в треї й перевірить графік за 15 хвилин. Клацання по значку знову відкриє вікно.",
        "Окно можно закрыть: программа останется в трее и проверит график через 15 минут. Щелчок по значку снова откроет окно.",
        "You can close the window: the app stays in the tray and checks the schedule in 15 minutes. Click the icon to open it again.",
    )
    fun widget() = t("Віджет", "Виджет", "Widget")
    fun hideWidget() = t("Сховати віджет", "Скрыть виджет", "Hide widget")
    fun open() = t("Відкрити", "Открыть", "Open")
    fun exit() = t("Вихід", "Выход", "Quit")
    fun addAddressInWindow() = t(
        "Додайте адресу у вікні програми.",
        "Добавьте адрес в окне программы.",
        "Add an address in the app window.",
    )
    fun warnToggleDesktop() = t(
        "Попереджати за 10 хвилин до вимкнення",
        "Предупреждать за 10 минут до отключения",
        "Warn 10 minutes before an outage",
    )
    fun notifyOnDesktop() = t(
        "Повідомляти, коли за графіком увімкнуть світло",
        "Сообщать, когда по графику включат свет",
        "Notify when the schedule turns the power on",
    )
    fun widgetDescription() = t(
        "Стан світла обраної групи за графіком ДТЕК",
        "Статус света выбранной группы по графику ДТЕК",
        "Power status of the selected group from the DTEK schedule",
    )

    fun groupTitle(group: String) = t("Група $group", "Группа $group", "Group $group")
    fun savedPlaceStatus(group: String, on: Boolean?) = t(
        "група $group · ${stateShort(on)}",
        "группа $group · ${stateShort(on)}",
        "group $group · ${stateShort(on)}",
    )

    private fun stateShort(on: Boolean?) = when (on) {
        true -> powerOnShort()
        false -> powerOffShort()
        null -> noDataShort()
    }

    fun placeHeading(place: String?, group: String): String {
        if (!place.isNullOrBlank()) return t("$place · група $group", "$place · группа $group", "$place · group $group")
        if (group.isBlank()) return groupMissing()
        return groupTitle(group)
    }

    fun addressHit(label: String, group: String): String {
        if (group.isBlank()) return label
        return t("$label  ·  група $group", "$label  ·  группа $group", "$label  ·  group $group")
    }

    fun sliceLabel(powerOn: Boolean, current: Boolean): String {
        val name = if (powerOn) powerOnShort() else powerOffShort()
        return if (current) "$name · ${now()}" else name
    }

    fun siteUpdated(stamp: String) = t(
        "Графік на сайті оновлено $stamp",
        "График на сайте обновлён $stamp",
        "Schedule on the site updated $stamp",
    )

    fun updated(time: String) = t("Оновлено $time", "Обновлено $time", "Updated $time")
    fun through(duration: String) = t("через $duration", "через $duration", "in $duration")
    fun offAt(time: String) = t(
        "За графіком вимкнуть о $time",
        "По графику выключат в $time",
        "Scheduled off at $time",
    )
    fun onAt(time: String) = t(
        "За графіком увімкнуть о $time",
        "По графику включат в $time",
        "Scheduled on at $time",
    )
    fun offAtIn(time: String, duration: String) = "${offAt(time)} · ${through(duration)}"
    fun onAtIn(time: String, duration: String) = "${onAt(time)} · ${through(duration)}"
    fun offUntil(duration: String) = t(
        "Світла немає до 24:00 · через $duration",
        "Света нет до 24:00 · через $duration",
        "Power off until 24:00 · in $duration",
    )
    fun widgetFooter(group: String, time: String) = t(
        "Група $group · оновлено $time",
        "Группа $group · обновлено $time",
        "Group $group · updated $time",
    )
    fun warnTitle(minutes: Int): String {
        if (minutes <= 1) return anyMinute()
        return t(
            "Світло вимкнуть через $minutes хв",
            "Свет выключат через $minutes мин",
            "Power goes off in $minutes min",
        )
    }

    fun offDetail(time: String) = t(
        "за графіком вимкнення о $time",
        "по графику отключение в $time",
        "scheduled off at $time",
    )

    fun notice(group: String, place: String?, tail: String): String {
        val head = groupTitle(group)
        return if (place.isNullOrBlank()) "$head · $tail" else "$head · $place · $tail"
    }

    fun siteAnswered(code: Int) = t("Сайт відповів $code", "Сайт ответил $code", "The site answered $code")
    fun dtekAnswered(code: Int) = t("ДТЕК відповів $code", "ДТЕК ответил $code", "DTEK answered $code")
    fun dtekClosed() = t(
        "ДТЕК тимчасово закрив довідник",
        "ДТЕК временно закрыл справочник",
        "DTEK temporarily closed the directory",
    )
    fun dtekNoKey() = t("ДТЕК не видав ключ запиту", "ДТЕК не выдал ключ запроса", "DTEK did not issue a request key")
    fun dtekSilent() = t("ДТЕК не відповів", "ДТЕК не ответил", "DTEK did not respond")
    fun tomorrowAt(clock: String) = t("завтра $clock", "завтра $clock", "tomorrow $clock")

    fun month(month: Int, language: AppLanguage = this.language): String {
        val names = when (language) {
            AppLanguage.Uk -> ukMonths
            AppLanguage.Ru -> ruMonths
            AppLanguage.En -> enMonths
        }
        return names.getOrElse(month - 1) { "" }
    }

    fun duration(hours: Int, minutes: Int): String = when (language) {
        AppLanguage.Uk -> span(hours, minutes, "год", "хв")
        AppLanguage.Ru -> span(hours, minutes, "ч", "мин")
        AppLanguage.En -> span(hours, minutes, "h", "min")
    }

    fun compact(hours: Int, minutes: Int): String = when (language) {
        AppLanguage.Uk -> span(hours, minutes, "г", "хв")
        AppLanguage.Ru -> span(hours, minutes, "ч", "м")
        AppLanguage.En -> span(hours, minutes, "h", "m")
    }

    private fun span(hours: Int, minutes: Int, hour: String, minute: String): String = when {
        hours > 0 && minutes > 0 -> "$hours $hour $minutes $minute"
        hours > 0 -> "$hours $hour"
        else -> "$minutes $minute"
    }

    private val ukMonths = arrayOf(
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня",
    )
    private val ruMonths = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val enMonths = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
}
