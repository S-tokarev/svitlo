import XCTest

final class LogicTests: XCTestCase {
    func testMapsOblenergoHourCodesAndIgnoresTheOtherOperator() {
        let page = SvitloParser.parse(html: page(rows))
        let today = page.days.first { $0.day == "today" }
        XCTAssertEqual(today?.updatedDate, "09.11.2026")
        XCTAssertEqual(today?.groups["2.1"], [
            Outage(startMin: 0, endMin: 210),
            Outage(startMin: 570, endMin: 1050),
            Outage(startMin: 1230, endMin: 1440),
        ])
        XCTAssertEqual(today?.groups["1.1"], [
            Outage(startMin: 0, endMin: 360),
            Outage(startMin: 600, endMin: 990),
            Outage(startMin: 1170, endMin: 1440),
        ])
        XCTAssertEqual(today?.groupUpdated["2.1"], "09.11.2026 08:05")
        XCTAssertNil(today?.groups["9.9"])
        XCTAssertTrue(page.live.isEmpty)
        XCTAssertTrue(page.houses.isEmpty)
        XCTAssertTrue(page.tomorrowMissing)
        XCTAssertEqual(page.note, "В інших районах діють стабілізаційні відключення.")
    }

    func testUnionsPossibleSlotsAndDropsUnusableDays() {
        let page = SvitloParser.parse(html: page(edgeRows))
        let today = page.days.first { $0.day == "today" }
        XCTAssertEqual(today?.groups["1.1"], [Outage(startMin: 600, endMin: 660)])
        XCTAssertEqual(today?.groups["1.2"], [])
        XCTAssertNil(today?.groups["2.1"])
        XCTAssertNil(today?.groups["2.2"])
        XCTAssertNil(today?.groups["3.1"])
        XCTAssertNil(today?.groups["3.2"])
        let tomorrow = page.days.first { $0.day == "tomorrow" }
        XCTAssertEqual(tomorrow?.groups["1.1"], [Outage(startMin: 0, endMin: 60)])
        XCTAssertFalse(page.tomorrowMissing)
    }

    func testEmptyPageDoesNotInventASchedule() {
        let page = SvitloParser.parse(html: "<html>challenge</html>")
        XCTAssertTrue(page.days.isEmpty)
        XCTAssertEqual(page.note, "")
    }

    func testTenMinuteWarningAndNextSwitchUseKyivSchedule() {
        let now = kyiv(2026, 11, 9, 16, 40)
        let parsed = SvitloParser.parse(html: page(rows))
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

        let elsewhere = AddressMatch.parseHouses(json, city: "м. Буча", street: "вул. Шевченка", houseQuery: "12")
        XCTAssertTrue(elsewhere.hits.allSatisfy { $0.house != "12" })
        XCTAssertEqual(elsewhere.hits.map(\.group).sorted(), ["1.2", "2.1", "3.1", "6.2"])
        XCTAssertEqual(elsewhere.hint, "Такого дома нет. Показаны дома этой улицы.")
    }

    func testCsrfCookieHoldsTheSecondSerializedString() {
        let cookie = "25ab%3A2%3A%7Bi%3A0%3Bs%3A15%3A%22_csrf-dtek-krem%22%3Bi%3A1%3Bs%3A32%3A%22yOTG-jz1o4Y2vMdssuTULtIJJc0lGGZa%22%3B%7D"
        XCTAssertEqual(AddressMatch.csrfToken(cookie), "yOTG-jz1o4Y2vMdssuTULtIJJc0lGGZa")
        XCTAssertEqual(AddressMatch.maskToken("abc", mask: [1, 2, 3]).count, 8)
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

    private func page(_ rows: String) -> String {
        "<script id=\"ng-state\" type=\"application/json\">{\"api:/source-registry/areas/kyivska-oblast/shutdowns\":{\"data\":\(rows),\"ts\":1}}</script>"
    }

    private var zeros: String { Array(repeating: "0", count: 24).joined(separator: ",") }

    private var note: String {
        #"{"state":"active","kind":"Emergency","expiresAt":"2099-01-01T00:00:00Z","message":"В інших районах діють стабілізаційні відключення."}"#
    }

    private var shortNote: String {
        #"{"state":"active","kind":"GPV","expiresAt":"2099-01-01T00:00:00Z","message":"стабілізаційні"}"#
    }

    private var expired: String {
        #"{"state":"active","kind":"Emergency","expiresAt":"2000-01-01T00:00:00Z","message":"прострочене"}"#
    }

    private var rows: String {
        """
        [
          {"initiator":"kyivska_oblast_uz","queue":"2.1","updated":"2026-11-09T06:05:00.201Z","today":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[\(zeros)],"todayAnnouncements":[]},
          {"initiator":"kyivska_oblast_oblenergo","queue":"2.1","updated":"2026-11-09T06:05:00.201Z","today":[1,1,1,2,0,0,0,0,0,3,1,1,1,1,1,1,1,2,0,0,3,1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[\(zeros)],"todayAnnouncements":[\(note),\(shortNote),\(expired)]},
          {"initiator":"kyivska_oblast_oblenergo","queue":"1.1","updated":"2026-11-08T21:45:10.007Z","today":[1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,2,0,0,3,1,1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[\(zeros)],"todayAnnouncements":[\(note)]},
          {"initiator":"kyivska_oblast_oblenergo","queue":"9.9","updated":"2026-11-09T06:05:00.201Z","today":[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10"}
        ]
        """
    }

    private var edgeRows: String {
        """
        [
          {"initiator":"kyivska_oblast_oblenergo","queue":"1.1","updated":"2026-11-09T06:05:00.201Z","today":[\(zeros)],"tomorrow":[1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"todayStatus":"ScheduleApplies","tomorrowStatus":"ScheduleApplies","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0]},
          {"initiator":"kyivska_oblast_oblenergo","queue":"1.2","updated":"2026-11-09T06:05:00.201Z","today":[\(zeros)],"tomorrow":[],"todayStatus":"NoShutdowns","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":[\(zeros)]},
          {"initiator":"kyivska_oblast_oblenergo","queue":"2.1","updated":"2026-11-09T06:05:00.201Z","today":[9,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10"},
          {"initiator":"kyivska_oblast_oblenergo","queue":"2.2","updated":"2026-11-09T06:05:00.201Z","today":[\(zeros)],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10","todayPossible":null},
          {"initiator":"kyivska_oblast_oblenergo","queue":"3.1","updated":"2026-11-09T06:05:00.201Z","today":[1,1,1],"tomorrow":[],"todayStatus":"ScheduleApplies","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10"},
          {"initiator":"kyivska_oblast_oblenergo","queue":"3.2","updated":"2026-11-09T06:05:00.201Z","today":[\(zeros)],"tomorrow":[],"todayStatus":"EmergencyShutdowns","tomorrowStatus":"WaitingForSchedule","todayDate":"2026-11-09","tomorrowDate":"2026-11-10"}
        ]
        """
    }
}
