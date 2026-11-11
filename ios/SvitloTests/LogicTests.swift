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
