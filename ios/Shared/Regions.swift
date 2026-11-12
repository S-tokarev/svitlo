import Foundation

/// Territory whose outage groups are loaded. Kyiv city is not an oblast.
struct Region: Equatable, Identifiable {
    var slug: String
    var uk: String
    var ru: String
    var en: String

    var id: String { slug }

    func title(language: AppLanguage = Copy.language) -> String {
        switch language {
        case .uk: return uk
        case .ru: return ru
        case .en: return en
        }
    }
}

enum Regions {
    static let kyivOblast = "kyivska-oblast"
    static let kyiv = "kyiv"

    static let all: [Region] = [
        Region(slug: kyiv, uk: "Київ", ru: "Киев", en: "Kyiv"),
        Region(slug: kyivOblast, uk: "Київська область", ru: "Киевская область", en: "Kyiv oblast"),
        Region(slug: "cherkaska-oblast", uk: "Черкаська область", ru: "Черкасская область", en: "Cherkasy oblast"),
        Region(slug: "chernigivska-oblast", uk: "Чернігівська область", ru: "Черниговская область", en: "Chernihiv oblast"),
        Region(slug: "chernivecka-oblast", uk: "Чернівецька область", ru: "Черновицкая область", en: "Chernivtsi oblast"),
        Region(slug: "dnipropetrovska-oblast", uk: "Дніпропетровська область", ru: "Днепропетровская область", en: "Dnipropetrovsk oblast"),
        Region(slug: "donetska-oblast", uk: "Донецька область", ru: "Донецкая область", en: "Donetsk oblast"),
        Region(slug: "ivano-frankivska-oblast", uk: "Івано-Франківська область", ru: "Ивано-Франковская область", en: "Ivano-Frankivsk oblast"),
        Region(slug: "kharkivska-oblast", uk: "Харківська область", ru: "Харьковская область", en: "Kharkiv oblast"),
        Region(slug: "khersonska-oblast", uk: "Херсонська область", ru: "Херсонская область", en: "Kherson oblast"),
        Region(slug: "khmelnytska-oblast", uk: "Хмельницька область", ru: "Хмельницкая область", en: "Khmelnytskyi oblast"),
        Region(slug: "kirovogradska-oblast", uk: "Кіровоградська область", ru: "Кировоградская область", en: "Kirovohrad oblast"),
        Region(slug: "lvivska-oblast", uk: "Львівська область", ru: "Львовская область", en: "Lviv oblast"),
        Region(slug: "luganska-oblast", uk: "Луганська область", ru: "Луганская область", en: "Luhansk oblast"),
        Region(slug: "mikolayivska-oblast", uk: "Миколаївська область", ru: "Николаевская область", en: "Mykolaiv oblast"),
        Region(slug: "odeska-oblast", uk: "Одеська область", ru: "Одесская область", en: "Odesa oblast"),
        Region(slug: "poltavska-oblast", uk: "Полтавська область", ru: "Полтавская область", en: "Poltava oblast"),
        Region(slug: "rivnenska-oblast", uk: "Рівненська область", ru: "Ровненская область", en: "Rivne oblast"),
        Region(slug: "sumska-oblast", uk: "Сумська область", ru: "Сумская область", en: "Sumy oblast"),
        Region(slug: "ternopilska-oblast", uk: "Тернопільська область", ru: "Тернопольская область", en: "Ternopil oblast"),
        Region(slug: "vinnytska-oblast", uk: "Вінницька область", ru: "Винницкая область", en: "Vinnytsia oblast"),
        Region(slug: "volynska-oblast", uk: "Волинська область", ru: "Волынская область", en: "Volyn oblast"),
        Region(slug: "zakarpatska-oblast", uk: "Закарпатська область", ru: "Закарпатская область", en: "Zakarpattia oblast"),
        Region(slug: "zaporizka-oblast", uk: "Запорізька область", ru: "Запорожская область", en: "Zaporizhzhia oblast"),
        Region(slug: "zhytomyrska-oblast", uk: "Житомирська область", ru: "Житомирская область", en: "Zhytomyr oblast"),
    ]

    static func normalize(_ slug: String?) -> String {
        let trimmed = slug?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return all.first { $0.slug == trimmed }?.slug ?? kyivOblast
    }

    static func find(_ slug: String?) -> Region {
        let key = normalize(slug)
        return all.first { $0.slug == key } ?? all[1]
    }

    /// A blank saved region is Kyiv oblast, so an old schedule still matches that choice.
    static func same(_ left: String?, _ right: String?) -> Bool {
        normalize(left) == normalize(right)
    }
}
