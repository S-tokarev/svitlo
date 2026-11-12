import XCTest

final class LogicTests: XCTestCase {
    func testReadsChartBarsLiveChipAndHouseSensor() {
        let page = SvitloParser.parse(html: sample(tomorrow: true, live: true, house: true))
        let today = page.days.first { $0.day == "today" }
        XCTAssertEqual(today?.updatedDate, "09.11.2026")
        XCTAssertEqual(today?.groups["1.1"], [
            Outage(startMin: 0, endMin: 360),
            Outage(startMin: 600, endMin: 990),
            Outage(startMin: 1170, endMin: 1440),
        ])
        XCTAssertEqual(today?.groups["2.1"], [
            Outage(startMin: 0, endMin: 210),
            Outage(startMin: 570, endMin: 1050),
            Outage(startMin: 1230, endMin: 1440),
        ])
        XCTAssertEqual(today?.groupUpdated["1.1"], "08.11.2026 23:45")
        let tomorrow = page.days.first { $0.day == "tomorrow" }
        XCTAssertEqual(tomorrow?.groups["1.1"], [Outage(startMin: 0, endMin: 60)])
        XCTAssertFalse(page.tomorrowMissing)
        XCTAssertEqual(page.live.map(\.id), ["1.1"])
        XCTAssertEqual(page.live.first?.powerOn, false)
        XCTAssertEqual(page.live.first?.toward, "ON")
        XCTAssertEqual(page.live.first?.seconds, 100)
        XCTAssertEqual(page.houses.first?.address, "Кургузова, 11В")
        XCTAssertEqual(page.houses.first?.powerOn, false)
    }

    func testPlainCopyOfTheSameDayIsNotTomorrow() {
        let changes = chart("середа, 11 листопада", rows: [("2.1", [Outage(startMin: 240, endMin: 630)])])
        let plain = chart("середа, 11 листопада", rows: [("2.1", [Outage(startMin: 0, endMin: 60)])])
        let page = SvitloParser.parse(html: #"<div data-mode="plain">\#(plain)</div><div data-mode="changes">\#(changes)</div>"#)
        XCTAssertTrue(page.tomorrowMissing)
        XCTAssertEqual(page.days.map(\.day), ["today"])
        XCTAssertEqual(page.days.first?.updatedDate, "11.11.2026")
        XCTAssertEqual(page.days.first?.groups["2.1"], [Outage(startMin: 240, endMin: 630)])
    }

    func testLaterDayStaysTomorrowWhenAPlainCopySitsBetween() {
        let todayChanges = chart("середа, 11 листопада", rows: [("1.1", [Outage(startMin: 0, endMin: 360)])])
        let todayPlain = chart("середа, 11 листопада", rows: [("1.1", [Outage(startMin: 0, endMin: 30)])])
        let tomorrowChanges = chart("четвер, 12 листопада", rows: [("1.1", [Outage(startMin: 0, endMin: 60)])])
        let tomorrowPlain = chart("четвер, 12 листопада", rows: [("1.1", [Outage(startMin: 0, endMin: 90)])])
        let html = #"<div data-mode="changes">\#(todayChanges)</div><div data-mode="plain">\#(todayPlain)</div><div data-mode="changes">\#(tomorrowChanges)</div><div data-mode="plain">\#(tomorrowPlain)</div>"#
        let page = SvitloParser.parse(html: html)
        XCTAssertFalse(page.tomorrowMissing)
        XCTAssertEqual(page.days.first { $0.day == "today" }?.groups["1.1"], [Outage(startMin: 0, endMin: 360)])
        XCTAssertEqual(page.days.first { $0.day == "tomorrow" }?.updatedDate, "12.11.2026")
        XCTAssertEqual(page.days.first { $0.day == "tomorrow" }?.groups["1.1"], [Outage(startMin: 0, endMin: 60)])
    }

    func testSameDateLabeledTomorrowIsHidden() {
        let now = kyiv(2026, 11, 11, 15, 0)
        let outages = [Outage(startMin: 240, endMin: 630), Outage(startMin: 870, endMin: 1320)]
        let stored = snapshot(now, days: [
            DaySchedule(day: "today", label: "11 ноября", updatedDate: "11.11.2026", updatedTime: "", groups: ["2.1": outages], groupUpdated: [:]),
            DaySchedule(day: "tomorrow", label: "11 ноября", updatedDate: "11.11.2026", updatedTime: "", groups: ["2.1": outages], groupUpdated: [:]),
        ])
        let picture = ScheduleMath.picture(snapshot: stored, selection: Selection(group: "2.1"), now: now)
        XCTAssertEqual(picture.outagesTomorrow, [])
        XCTAssertEqual(picture.outagesToday, outages)
        XCTAssertFalse(ScheduleMath.groupHasTomorrow(snapshot: stored, group: "2.1"))
    }

    func testAlertsEnergyMapsDtekHoursAndIgnoresRailway() {
        let railway = alertsRow(initiator: "kyivska_oblast_uz", queue: "1.1", today: Array(repeating: 1, count: 24), todayStatus: "ScheduleApplies")
        let main = alertsRow(
            queue: "1.1",
            today: [1, 2, 3] + Array(repeating: 0, count: 21),
            todayPossible: [0, 0, 2] + Array(repeating: 0, count: 21),
            tomorrow: Array(repeating: 0, count: 24),
            tomorrowStatus: "ScheduleApplies",
            message: "За наказом НЕК Укренерго діють стабілізаційні відключення."
        )
        let clear = alertsRow(
            queue: "1.2",
            today: [],
            todayStatus: "NoShutdowns",
            todayPossible: [1] + Array(repeating: 0, count: 23),
            tomorrow: [],
            tomorrowStatus: "WaitingForSchedule",
            message: "За наказом НЕК Укренерго діють стабілізаційні відключення."
        )
        let page = AlertsEnergy.parse(html: alertsPage([railway, main, clear].joined(separator: ",")))
        let today = page.days.first { $0.day == "today" }
        XCTAssertEqual(today?.updatedDate, "12.11.2026")
        XCTAssertEqual(today?.label, "12 листопада")
        XCTAssertEqual(today?.groups["1.1"], [Outage(startMin: 0, endMin: 90), Outage(startMin: 120, endMin: 180)])
        XCTAssertEqual(today?.groups["1.2"], [Outage(startMin: 0, endMin: 60)])
        XCTAssertEqual(today?.groupUpdated["1.1"], "11.11.2026 20:30")
        let tomorrow = page.days.first { $0.day == "tomorrow" }
        XCTAssertEqual(tomorrow?.groups["1.1"], [])
        XCTAssertNil(tomorrow?.groups["1.2"])
        XCTAssertFalse(page.tomorrowMissing)
        XCTAssertEqual(page.source, AlertsEnergy.source)
        XCTAssertEqual(page.note, "За наказом НЕК Укренерго діють стабілізаційні відключення.")
        let waiting = AlertsEnergy.parse(html: alertsPage(alertsRow(queue: "4.2", today: [], todayStatus: "WaitingForSchedule")))
        XCTAssertTrue(waiting.days.isEmpty)
        XCTAssertEqual(waiting.source, "")
    }

    func testExplicitOperatorKeepsCityQueues() {
        let rows = [
            alertsRow(initiator: "city_dtek", queue: "1.1", today: Array(repeating: 1, count: 24)),
            alertsRow(initiator: "city_other", queue: "60.1", today: [1] + Array(repeating: 0, count: 23)),
            alertsRow(initiator: "city_other", queue: "10.1", today: [2] + Array(repeating: 0, count: 23)),
            alertsRow(initiator: "city_uz", queue: "60.1", today: Array(repeating: 1, count: 24)),
        ].joined(separator: ",")
        let suppliers = #"""
        {"supplierKey":"city_uz","supplierName":"Укрзалізниця","supplierType":"uz","priority":10},
        {"supplierKey":"city_dtek","supplierName":"ДТЕК Київські електромережі","supplierType":"oblenergo","priority":20},
        {"supplierKey":"city_other","supplierName":"Інша мережа","supplierType":"other","priority":5}
        """#
        let html = #"<script id="ng-state" type="application/json">{"suppliers":{"data":[\#(suppliers)]},"shutdowns":{"data":[\#(rows)]}}</script>"#
        let chosen = AlertsEnergy.parse(html: html, operatorId: "city_other")
        let today = chosen.days.first { $0.day == "today" }
        XCTAssertEqual(today?.groups["10.1"], [Outage(startMin: 0, endMin: 30)])
        XCTAssertEqual(today?.groups["60.1"], [Outage(startMin: 0, endMin: 60)])
        XCTAssertNil(today?.groups["1.1"])
        XCTAssertEqual(chosen.operatorId, "city_other")
        XCTAssertEqual(chosen.operators.map(\.id), ["city_dtek", "city_other"])
        let preferred = AlertsEnergy.parse(html: html)
        XCTAssertEqual(preferred.operatorId, "city_dtek")
        XCTAssertEqual(Array(preferred.days.first { $0.day == "today" }?.groups.keys ?? []).sorted(), ["1.1"])
        XCTAssertTrue(AlertsEnergy.parse(html: html, operatorId: "missing").days.isEmpty)
        let city = Snapshot(
            fetchedAt: 0,
            days: [DaySchedule(day: "today", label: "", updatedDate: "", updatedTime: "", groups: ["60.1": [], "10.1": [], "2.1": []], groupUpdated: [:])],
            live: [],
            houses: [],
            tomorrowMissing: true,
            note: "",
            region: Regions.kyiv
        )
        XCTAssertEqual(ScheduleMath.knownGroups(city, region: Regions.kyiv), ["2.1", "10.1", "60.1"])
        XCTAssertTrue(ScheduleMath.knownGroups(city, region: Regions.kyivOblast).isEmpty)
        XCTAssertEqual(ScheduleMath.knownGroups(nil, region: Regions.kyivOblast).count, 12)
        XCTAssertTrue(ScheduleMath.knownGroups(nil, region: Regions.kyiv).isEmpty)
    }

    func testApiArraysUseSnakeCase() {
        let clear = Array(repeating: "0", count: 24).joined(separator: ",")
        let tomorrowOff = (["1"] + Array(repeating: "0", count: 23)).joined(separator: ",")
        let secondHalf = (["3"] + Array(repeating: "0", count: 23)).joined(separator: ",")
        let shutdowns = """
        [{"initiator":"city_dtek","queue":"2.1","updated":"2026-11-11T18:30:18.845Z","today":[\(clear)],"tomorrow":[\(tomorrowOff)],"today_status":"NoShutdowns","tomorrow_status":"ScheduleApplies","today_date":"2026-11-12","tomorrow_date":"2026-11-13"},{"initiator":"other_net","queue":"10.1","updated":"2026-11-11T18:30:18.845Z","today":[\(secondHalf)],"tomorrow":[],"today_status":"ScheduleApplies","tomorrow_status":"WaitingForSchedule","today_date":"2026-11-12","tomorrow_date":"2026-11-13"}]
        """
        let suppliers = """
        [{"supplier_key":"city_uz","supplier_name":"Укрзалізниця","supplier_type":"uz","priority":1},{"supplier_key":"city_dtek","supplier_name":"ДТЕК Київські електромережі","supplier_type":"oblenergo","priority":5}]
        """
        let page = AlertsEnergy.parseApi(shutdowns: shutdowns, suppliers: suppliers)
        XCTAssertEqual(page.source, AlertsEnergy.source)
        XCTAssertEqual(page.operatorId, "city_dtek")
        XCTAssertEqual(page.operators.map(\.name), ["ДТЕК Київські електромережі"])
        let today = page.days.first { $0.day == "today" }
        XCTAssertEqual(today?.groups["2.1"], [])
        XCTAssertEqual(today?.groupUpdated["2.1"], "11.11.2026 20:30")
        let tomorrow = page.days.first { $0.day == "tomorrow" }
        XCTAssertEqual(tomorrow?.groups["2.1"], [Outage(startMin: 0, endMin: 60)])
        let other = AlertsEnergy.parseApi(shutdowns: shutdowns, suppliers: suppliers, operatorId: "other_net")
        XCTAssertEqual(other.days.first { $0.day == "today" }?.groups["10.1"], [Outage(startMin: 30, endMin: 60)])
        XCTAssertTrue(AlertsEnergy.parseApi(shutdowns: "nope", suppliers: "[]").days.isEmpty)
        XCTAssertTrue(SvitloClient.looksLimited(code: 200, body: "Забагато запитів. Спробуйте пізніше."))
        XCTAssertTrue(SvitloClient.looksLimited(code: 429, body: "ok"))
        XCTAssertFalse(SvitloClient.looksLimited(code: 200, body: "<script id=\"ng-state\">Забагато запитів</script>"))
        XCTAssertEqual(SvitloClient.retryWaitMs("1"), 60_000)
        XCTAssertEqual(SvitloClient.retryWaitMs(nil), 600_000)
        XCTAssertEqual(SvitloClient.retryWaitMs("99999"), 1_800_000)
        XCTAssertEqual(SvitloClient.normalizeApiKey("  Bearer secret  "), "secret")
    }

    func testYasnoMapsSlotsAndDropsASameDateTomorrow() {
        let page = Yasno.parse(yasnoSample(), operatorId: "902", operators: [GridOperator(id: "902", name: "ТОВ «ДТЕК КИЇВСЬКІ ЕЛЕКТРОМЕРЕЖІ»")])
        let today = page.days.first { $0.day == "today" }
        XCTAssertEqual(today?.updatedDate, "12.11.2026")
        XCTAssertEqual(today?.label, "12 листопада")
        XCTAssertEqual(today?.updatedTime, "")
        XCTAssertEqual(Set(today?.groups.keys ?? []), Set(["1.1", "2.1", "4.1", "5.1", "10.1"]))
        XCTAssertEqual(today?.groups["1.1"], [Outage(startMin: 120, endMin: 240)])
        XCTAssertEqual(today?.groups["2.1"], [])
        XCTAssertEqual(today?.groups["5.1"], [])
        XCTAssertEqual(today?.groups["4.1"], [Outage(startMin: 100, endMin: 130)])
        XCTAssertEqual(today?.groups["10.1"], [Outage(startMin: 0, endMin: 30)])
        XCTAssertNil(today?.groups["3.1"])
        XCTAssertEqual(today?.groupUpdated["1.1"], "11.11.2026 21:00")
        XCTAssertEqual(today?.groupUpdated["4.1"], "")
        let tomorrow = page.days.first { $0.day == "tomorrow" }
        XCTAssertEqual(tomorrow?.updatedDate, "13.11.2026")
        XCTAssertEqual(tomorrow?.groups.keys.sorted(), ["3.1"])
        XCTAssertEqual(tomorrow?.groups["3.1"], [Outage(startMin: 60, endMin: 120)])
        XCTAssertNil(tomorrow?.groups["2.1"])
        XCTAssertFalse(page.tomorrowMissing)
        XCTAssertEqual(page.source, Yasno.source)
        let waiting = Yasno.parse(
            #"{"1.1":{"today":{"slots":[],"date":"2026-11-12T00:00:00+02:00","status":"WaitingForSchedule"}}}"#,
            operatorId: "902",
            operators: []
        )
        XCTAssertTrue(waiting.days.isEmpty)
        XCTAssertTrue(Yasno.covers(Regions.kyiv))
        XCTAssertTrue(Yasno.covers("dnipropetrovska-oblast"))
        XCTAssertFalse(Yasno.covers(Regions.kyivOblast))
        XCTAssertEqual(SvitloClient.page(for: "", source: Yasno.source, region: Regions.kyiv).absoluteString, "https://static.yasno.ua/kyiv/outages")
        XCTAssertEqual(SvitloClient.page(for: "", region: "dnipropetrovska-oblast").absoluteString, "https://static.yasno.ua/dnipro/outages")
        XCTAssertEqual(SvitloClient.page(for: "", source: DtekSchedule.source).absoluteString, "https://www.dtek-krem.com.ua/ua/shutdowns")
        XCTAssertEqual(SvitloClient.page(for: "", region: Regions.kyivOblast).absoluteString, "https://vyshgorod.in.ua/svitlo")
        XCTAssertEqual(SvitloClient.page(for: "", region: "odeska-oblast").absoluteString, "https://alerts.energy/odeska-oblast")
    }

    func testDtekFactMapsProbedHours() {
        let page = DtekSchedule.parse(dtekFact())
        let today = page.days.first { $0.day == "today" }
        XCTAssertEqual(today?.updatedDate, "12.11.2026")
        XCTAssertEqual(today?.updatedTime, "15:00")
        XCTAssertEqual(today?.label, "12 листопада")
        XCTAssertEqual(Set(today?.groups.keys ?? []), Set(["1.1", "1.2", "3.1", "4.1", "6.2"]))
        XCTAssertEqual(today?.groups["1.1"], [
            Outage(startMin: 0, endMin: 240),
            Outage(startMin: 600, endMin: 870),
            Outage(startMin: 1200, endMin: 1440),
        ])
        XCTAssertEqual(today?.groups["1.2"], [Outage(startMin: 0, endMin: 60)])
        XCTAssertEqual(today?.groups["3.1"], [])
        XCTAssertEqual(today?.groups["4.1"], [Outage(startMin: 840, endMin: 870), Outage(startMin: 930, endMin: 960)])
        XCTAssertEqual(today?.groups["6.2"], [Outage(startMin: 0, endMin: 90), Outage(startMin: 150, endMin: 180)])
        XCTAssertEqual(today?.groupUpdated["1.1"], "12.11.2026 15:00")
        XCTAssertNil(today?.groups["junk"])
        let tomorrow = page.days.first { $0.day == "tomorrow" }
        XCTAssertEqual(tomorrow?.groups["2.1"], [Outage(startMin: 1380, endMin: 1440)])
        XCTAssertEqual(tomorrow?.label, "13 листопада")
        XCTAssertFalse(page.tomorrowMissing)
        XCTAssertEqual(page.source, DtekSchedule.source)
        XCTAssertEqual(page.region, Regions.kyivOblast)
        XCTAssertEqual(page.operatorId, AlertsEnergy.kyivOblastDtek)
        XCTAssertTrue(DtekSchedule.parse(#"{"result":false}"#).days.isEmpty)
    }

    func testEmptyPageDoesNotInventASchedule() {
        let page = SvitloParser.parse(html: "<html>challenge</html>")
        XCTAssertTrue(page.days.isEmpty)
        XCTAssertEqual(page.note, "")
    }

    func testTenMinuteWarningAndNextSwitchUseKyivSchedule() {
        let now = kyiv(2026, 11, 9, 16, 40)
        let parsed = SvitloParser.parse(html: sample(tomorrow: false, live: false, house: false))
        let snapshot = Snapshot(
            fetchedAt: now,
            days: parsed.days,
            live: [],
            houses: [],
            tomorrowMissing: true,
            note: parsed.note
        )
        let picture = ScheduleMath.picture(snapshot: snapshot, selection: Selection(group: "1.1"), now: now)
        XCTAssertEqual(picture.displayOn, true)
        let off = picture.nextOffAt ?? -1
        let parts = ScheduleMath.calendar().dateComponents([.hour, .minute], from: ScheduleMath.date(from: off))
        XCTAssertEqual(parts.hour, 19)
        XCTAssertEqual(parts.minute, 30)
        XCTAssertEqual(picture.warnAts, [off - ScheduleMath.warnBeforeMs])
        XCTAssertNil(picture.dueWarnOffAt)
        XCTAssertEqual(picture.updatedLabel, "08.11.2026 23:45")
    }

    func testLightAfterMidnightUsesTheNextDayInsteadOfZero() {
        let now = kyiv(2026, 11, 9, 23, 10)
        let continued = snapshot(now, days: [
            DaySchedule(day: "today", label: "9 ноября", updatedDate: "09.11.2026", updatedTime: "", groups: ["2.1": [Outage(startMin: 1230, endMin: 1440)]], groupUpdated: [:]),
            DaySchedule(day: "tomorrow", label: "10 ноября", updatedDate: "10.11.2026", updatedTime: "", groups: ["2.1": [Outage(startMin: 0, endMin: 180)]], groupUpdated: [:]),
        ])
        let on = ScheduleMath.picture(snapshot: continued, selection: Selection(group: "2.1"), now: now).nextOnAt ?? -1
        let parts = ScheduleMath.calendar().dateComponents([.day, .hour, .minute], from: ScheduleMath.date(from: on))
        XCTAssertEqual(parts.day, 10)
        XCTAssertEqual(parts.hour, 3)
        XCTAssertEqual(parts.minute, 0)

        let cutoff = snapshot(now, days: [
            DaySchedule(day: "today", label: "9 ноября", updatedDate: "09.11.2026", updatedTime: "", groups: ["2.1": [Outage(startMin: 1230, endMin: 1440)]], groupUpdated: [:]),
        ])
        let cutoffPicture = ScheduleMath.picture(snapshot: cutoff, selection: Selection(group: "2.1"), now: now)
        XCTAssertNil(cutoffPicture.nextOnAt)
        let until = cutoffPicture.offUntil ?? -1
        let untilParts = ScheduleMath.calendar().dateComponents([.day, .hour, .minute], from: ScheduleMath.date(from: until))
        XCTAssertEqual(untilParts.day, 10)
        XCTAssertEqual(untilParts.hour, 0)
        XCTAssertEqual(untilParts.minute, 0)
        XCTAssertNil(ScheduleMath.picture(snapshot: continued, selection: Selection(group: "2.1"), now: now).offUntil)
    }

    func testScheduleChangeIgnoresAFinishedDay() {
        let now = kyiv(2026, 11, 10, 9, 0)
        let before = snapshot(now, days: [
            DaySchedule(day: "today", label: "9 ноября", updatedDate: "09.11.2026", updatedTime: "", groups: ["2.1": [Outage(startMin: 0, endMin: 60)]], groupUpdated: [:]),
            DaySchedule(day: "tomorrow", label: "10 ноября", updatedDate: "10.11.2026", updatedTime: "", groups: ["2.1": [Outage(startMin: 0, endMin: 180)]], groupUpdated: [:]),
        ])
        let rolled = snapshot(now, days: [
            DaySchedule(day: "today", label: "10 ноября", updatedDate: "10.11.2026", updatedTime: "", groups: ["2.1": [Outage(startMin: 0, endMin: 180)]], groupUpdated: [:]),
        ])
        XCTAssertFalse(ScheduleMath.scheduleChanged(previous: before, next: rolled, group: "2.1", now: now))
        let edited = snapshot(now, days: [
            DaySchedule(day: "today", label: "10 ноября", updatedDate: "10.11.2026", updatedTime: "", groups: ["2.1": [Outage(startMin: 0, endMin: 240)]], groupUpdated: [:]),
        ])
        XCTAssertTrue(ScheduleMath.scheduleChanged(previous: rolled, next: edited, group: "2.1", now: now))
    }

    func testQuietHoursCoverNightButNotEvening() {
        XCTAssertTrue(ScheduleMath.isQuietHour(kyiv(2026, 11, 9, 23, 0)))
        XCTAssertTrue(ScheduleMath.isQuietHour(kyiv(2026, 11, 9, 2, 15)))
        XCTAssertTrue(ScheduleMath.isQuietHour(kyiv(2026, 11, 9, 6, 59)))
        XCTAssertFalse(ScheduleMath.isQuietHour(kyiv(2026, 11, 9, 7, 0)))
        XCTAssertFalse(ScheduleMath.isQuietHour(kyiv(2026, 11, 9, 22, 59)))
        XCTAssertFalse(ScheduleMath.isQuietHour(kyiv(2026, 11, 9, 18, 11)))
    }

    func testStreetSearchCoversTheOblastAndPrefersTheNamedTown() {
        let directory = AddressMatch.parseDirectory("""
        {"result":true,"streets":{
          "м. Буча":["вул. Шевченка","вул. Вокзальна"],
          "м. Ірпінь":["вул. Шевченка"],
          "м. Вишгород":["вул. Кургузова"],
          "смт Гостомель":["вул. Кучмів Яр"]
        }}
        """)
        let kurhuzova = AddressMatch.matchStreets(directory, AddressMatch.parseQuery("Кургузова"))
        XCTAssertEqual(kurhuzova.streets.map(\.city), ["м. Вишгород"])
        let bucha = AddressMatch.matchStreets(directory, AddressMatch.parseQuery("Буча Шевченка"))
        XCTAssertEqual(bucha.streets.map(\.city), ["м. Буча"])
        let all = AddressMatch.matchStreets(directory, AddressMatch.parseQuery("вул. Шевченка"))
        XCTAssertEqual(Set(all.streets.map(\.city)), ["м. Буча", "м. Ірпінь"])
        let irpin = AddressMatch.matchStreets(directory, AddressMatch.parseQuery("Ирпень Шевченка 11"))
        XCTAssertEqual(irpin.streets.map(\.city), ["м. Ірпінь"])
        XCTAssertEqual(AddressMatch.parseQuery("Ирпень Шевченка 11").house, "11")
    }

    func testHouseNumbersKeepTheLetterAndDropLongerNumbers() {
        XCTAssertTrue(AddressMatch.houseMatches("11", "11"))
        XCTAssertTrue(AddressMatch.houseMatches("11", "11Б"))
        XCTAssertTrue(AddressMatch.houseMatches("11б", "11Б/с.1"))
        XCTAssertFalse(AddressMatch.houseMatches("11", "111"))
        XCTAssertFalse(AddressMatch.houseMatches("11", "110"))
    }

    func testHousesUseTheShortQueueAndSkipAnotherOperator() {
        let json = """
        {"result":true,"data":{
          "3Б":{"sub_type_reason":["GPV3.1"],"cek":null},
          "11":{"sub_type_reason":["GPV1.2","GPV2.1"],"cek":null},
          "12":{"sub_type_reason":["GPV4.1"],"cek":true},
          "111":{"sub_type_reason":["GPV6.2"],"cek":null}
        }}
        """
        let hits = AddressMatch.parseHouses(json, city: "м. Буча", street: "вул. Шевченка", houseQuery: "11")
        XCTAssertEqual(hits.hits.map(\.group), ["1.2", "2.1"])
        XCTAssertTrue(hits.hits.allSatisfy { $0.house == "11" })
        XCTAssertNil(hits.hint)

        Copy.language = .uk
        let elsewhere = AddressMatch.parseHouses(json, city: "м. Буча", street: "вул. Шевченка", houseQuery: "12")
        XCTAssertTrue(elsewhere.hits.allSatisfy { $0.house != "12" })
        XCTAssertEqual(elsewhere.hits.map(\.group).sorted(), ["1.2", "2.1", "3.1", "6.2"])
        XCTAssertEqual(elsewhere.hint, "Такого будинку немає. Показано будинки цієї вулиці.")
        Copy.language = .ru
        XCTAssertEqual(
            AddressMatch.parseHouses(json, city: "м. Буча", street: "вул. Шевченка", houseQuery: "12").hint,
            "Такого дома нет. Показаны дома этой улицы."
        )
        Copy.language = .en
        XCTAssertEqual(
            AddressMatch.parseHouses(json, city: "м. Буча", street: "вул. Шевченка", houseQuery: "12").hint,
            "That house is not listed. Showing houses on this street."
        )
        Copy.language = .uk
    }

    func testCsrfCookieHoldsTheSecondSerializedString() {
        let cookie = "25ab%3A2%3A%7Bi%3A0%3Bs%3A15%3A%22_csrf-dtek-krem%22%3Bi%3A1%3Bs%3A32%3A%22yOTG-jz1o4Y2vMdssuTULtIJJc0lGGZa%22%3B%7D"
        XCTAssertEqual(AddressMatch.csrfToken(cookie), "yOTG-jz1o4Y2vMdssuTULtIJJc0lGGZa")
        XCTAssertEqual(AddressMatch.maskToken("abc", mask: [1, 2, 3]).count, 8)
    }

    private func yasnoSample() -> String {
        """
        {
          "nope": {"today": {"slots": [{"start": 0, "end": 60, "type": "Definite"}], "date": "2026-11-12T00:00:00+02:00", "status": "ScheduleApplies"}},
          "2.1": {
            "today": {"slots": [], "date": "2026-11-12T00:00:00+02:00", "status": "NoShutdowns"},
            "tomorrow": {"slots": [{"start": 0, "end": 60, "type": "Definite"}], "date": "2026-11-12T00:00:00+02:00", "status": "ScheduleApplies"},
            "updatedOn": "2026-11-11T19:00:01+00:00"
          },
          "10.1": {
            "today": {"slots": [{"start": 0, "end": 30, "type": "Definite"}], "date": "2026-11-12T00:00:00+02:00", "status": "ScheduleApplies"},
            "updatedOn": "2026-11-11T19:00:01+00:00"
          },
          "1.1": {
            "today": {
              "slots": [
                {"start": 0, "end": 120, "type": "NotPlanned"},
                {"start": 120, "end": 180, "type": "Definite"},
                {"start": 180, "end": 240, "type": "Possible"}
              ],
              "date": "2026-11-12T00:00:00+02:00",
              "status": "ScheduleApplies"
            },
            "tomorrow": {"slots": [], "date": "2026-11-13T00:00:00+02:00", "status": "WaitingForSchedule"},
            "updatedOn": "2026-11-11T19:00:01+00:00"
          },
          "3.1": {
            "today": {"slots": [], "date": "2026-11-12T00:00:00+02:00", "status": "EmergencyShutdowns"},
            "tomorrow": {
              "slots": [
                {"start": 60, "end": 90, "type": "Definite"},
                {"start": 90, "end": 120, "type": "Possible"}
              ],
              "date": "2026-11-13T00:00:00+02:00",
              "status": "ScheduleApplies"
            }
          },
          "4.1": {
            "today": {"slots": [{"start": 100, "end": 130, "type": "Definite"}], "date": "2026-11-12T00:00:00+02:00", "status": "EmergencyShutdowns"}
          },
          "5.1": {
            "today": {"slots": [], "date": "2026-11-12T00:00:00+02:00", "status": "NoOutages"}
          }
        }
        """
    }

    private func dtekFact() -> String {
        let probed = (1...24).map { hour -> String in
            let value: String
            switch hour {
            case 1...4, 11...14, 21...24: value = "no"
            case 15: value = "first"
            default: value = "yes"
            }
            return "\"\(hour)\":\"\(value)\""
        }.joined(separator: ",")
        let today = 1_794_434_400
        let tomorrow = today + 86_400
        return """
        {
          "result": true,
          "fact": {
            "update": "12.11.2026 15:00",
            "today": \(today),
            "data": {
              "\(today)": {
                "GPV1.1": {\(probed)},
                "GPV1.2": {"1": "no"},
                "GPV3.1": {"1": "yes", "2": "yes"},
                "GPV4.1": {"15": "first", "16": "second"},
                "GPV6.2": {"1": "maybe", "2": "mfirst", "3": "msecond", "4": "yes"},
                "junk": {"1": "no"}
              },
              "\(tomorrow)": {"GPV2.1": {"24": "no"}}
            }
          }
        }
        """
    }

    private func alertsPage(_ rows: String) -> String {
        #"<script id="ng-state" type="application/json">{"shutdowns":{"data":[\#(rows)]}}</script>"#
    }

    private func alertsRow(
        initiator: String = "kyivska_oblast_oblenergo",
        queue: String,
        today: [Int],
        todayStatus: String = "ScheduleApplies",
        todayPossible: [Int] = [],
        tomorrow: [Int] = [],
        tomorrowStatus: String = "WaitingForSchedule",
        message: String = ""
    ) -> String {
        let announcements = message.isEmpty ? "[]" : #"[{"message":"\#(message)"}]"#
        let todayJson = today.map(String.init).joined(separator: ",")
        let tomorrowJson = tomorrow.map(String.init).joined(separator: ",")
        let possibleJson = todayPossible.map(String.init).joined(separator: ",")
        return """
        {"initiator":"\(initiator)","queue":"\(queue)","updated":"2026-11-11T18:30:18.845Z","today":[\(todayJson)],"tomorrow":[\(tomorrowJson)],"todayStatus":"\(todayStatus)","tomorrowStatus":"\(tomorrowStatus)","todayDate":"2026-11-12","tomorrowDate":"2026-11-13","todayPossible":[\(possibleJson)],"todayAnnouncements":\(announcements)}
        """
    }

    private func kyiv(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int) -> Int64 {
        var parts = DateComponents()
        parts.calendar = ScheduleMath.calendar()
        parts.timeZone = ScheduleMath.zone
        parts.year = year
        parts.month = month
        parts.day = day
        parts.hour = hour
        parts.minute = minute
        let date = ScheduleMath.calendar().date(from: parts)!
        return ScheduleMath.millis(date)
    }

    private func snapshot(_ now: Int64, days: [DaySchedule]) -> Snapshot {
        Snapshot(fetchedAt: now, days: days, live: [], houses: [], tomorrowMissing: !days.contains { $0.day == "tomorrow" }, note: "")
    }

    private func sample(tomorrow: Bool, live: Bool, house: Bool) -> String {
        var html = chart("понеділок, 9 листопада", rows: [
            ("1.1", [Outage(startMin: 0, endMin: 360), Outage(startMin: 600, endMin: 990), Outage(startMin: 1170, endMin: 1440)]),
            ("2.1", [Outage(startMin: 0, endMin: 210), Outage(startMin: 570, endMin: 1050), Outage(startMin: 1230, endMin: 1440)]),
        ])
        if tomorrow {
            html += chart("вівторок, 10 листопада", rows: [("1.1", [Outage(startMin: 0, endMin: 60)])])
        }
        if live {
            html += #"<span class="group off" data-g="1.1"><b>1.1</b><small>до ON</small><span class="mono" data-sec="100">--:--:--</span></span>"#
        }
        if house {
            html += #"<div class="house off" data-g="1.1" data-st="off" data-q="кургузова"><span class="addr"><b>Кургузова, 11В</b><small class="complex-tag">Зірковий</small></span></div>"#
        }
        return html
    }

    private func chart(_ phrase: String, rows: [(String, [Outage])]) -> String {
        var texts = [
            #"<text x="1200" y="63">Оновлено 08.11.2026</text>"#,
            #"<text x="1360" y="64">23:45</text>"#,
            #"<text x="277" y="73">\#(phrase)</text>"#,
        ]
        var rects = [##"<rect x="230" y="120" width="1118" height="40" fill="#FFFFFF"/>"##]
        for (index, row) in rows.enumerated() {
            let labelY = 170.0 + Double(index) * 48
            texts.append(#"<text x="92" y="\#(labelY)">\#(row.0)</text>"#)
            for outage in row.1 {
                let x = 230.0 + Double(outage.startMin) / 1440.0 * 1118.0
                let width = Double(outage.endMin - outage.startMin) / 1440.0 * 1118.0
                rects.append(##"<rect x="\##(x)" y="\##(labelY - 28)" width="\##(width)" height="45" fill="#CBCAC5"/>"##)
            }
        }
        return #"<div class="sg-svg sg-svg-light"><svg>\#(rects.joined())\#(texts.joined())</svg></div>"#
    }
}
