package ua.`in`.vyshgorod.svitlo.data

/** Territory whose outage groups are loaded. Kyiv city is not an oblast. */
data class Region(
    val slug: String,
    val uk: String,
    val ru: String,
    val en: String,
) {
    fun title(language: AppLanguage = L10n.language): String = when (language) {
        AppLanguage.Uk -> uk
        AppLanguage.Ru -> ru
        AppLanguage.En -> en
    }
}

object Regions {
    const val KYIV_OBLAST = "kyivska-oblast"
    const val KYIV = "kyiv"

    val all: List<Region> = listOf(
        Region(KYIV, "Київ", "Киев", "Kyiv"),
        Region(KYIV_OBLAST, "Київська область", "Киевская область", "Kyiv oblast"),
        Region("cherkaska-oblast", "Черкаська область", "Черкасская область", "Cherkasy oblast"),
        Region("chernigivska-oblast", "Чернігівська область", "Черниговская область", "Chernihiv oblast"),
        Region("chernivecka-oblast", "Чернівецька область", "Черновицкая область", "Chernivtsi oblast"),
        Region("dnipropetrovska-oblast", "Дніпропетровська область", "Днепропетровская область", "Dnipropetrovsk oblast"),
        Region("donetska-oblast", "Донецька область", "Донецкая область", "Donetsk oblast"),
        Region("ivano-frankivska-oblast", "Івано-Франківська область", "Ивано-Франковская область", "Ivano-Frankivsk oblast"),
        Region("kharkivska-oblast", "Харківська область", "Харьковская область", "Kharkiv oblast"),
        Region("khersonska-oblast", "Херсонська область", "Херсонская область", "Kherson oblast"),
        Region("khmelnytska-oblast", "Хмельницька область", "Хмельницкая область", "Khmelnytskyi oblast"),
        Region("kirovogradska-oblast", "Кіровоградська область", "Кировоградская область", "Kirovohrad oblast"),
        Region("lvivska-oblast", "Львівська область", "Львовская область", "Lviv oblast"),
        Region("luganska-oblast", "Луганська область", "Луганская область", "Luhansk oblast"),
        Region("mikolayivska-oblast", "Миколаївська область", "Николаевская область", "Mykolaiv oblast"),
        Region("odeska-oblast", "Одеська область", "Одесская область", "Odesa oblast"),
        Region("poltavska-oblast", "Полтавська область", "Полтавская область", "Poltava oblast"),
        Region("rivnenska-oblast", "Рівненська область", "Ровненская область", "Rivne oblast"),
        Region("sumska-oblast", "Сумська область", "Сумская область", "Sumy oblast"),
        Region("ternopilska-oblast", "Тернопільська область", "Тернопольская область", "Ternopil oblast"),
        Region("vinnytska-oblast", "Вінницька область", "Винницкая область", "Vinnytsia oblast"),
        Region("volynska-oblast", "Волинська область", "Волынская область", "Volyn oblast"),
        Region("zakarpatska-oblast", "Закарпатська область", "Закарпатская область", "Zakarpattia oblast"),
        Region("zaporizka-oblast", "Запорізька область", "Запорожская область", "Zaporizhzhia oblast"),
        Region("zhytomyrska-oblast", "Житомирська область", "Житомирская область", "Zhytomyr oblast"),
    )

    fun normalize(slug: String?): String = all.firstOrNull { it.slug == slug?.trim() }?.slug ?: KYIV_OBLAST

    fun find(slug: String?): Region = all.first { it.slug == normalize(slug) }

    fun same(left: String?, right: String?): Boolean = normalize(left) == normalize(right)
}
