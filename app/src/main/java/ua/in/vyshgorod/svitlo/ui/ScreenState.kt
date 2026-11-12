package ua.`in`.vyshgorod.svitlo.ui

import ua.`in`.vyshgorod.svitlo.data.AddressHit
import ua.`in`.vyshgorod.svitlo.data.AppLanguage
import ua.`in`.vyshgorod.svitlo.data.L10n
import ua.`in`.vyshgorod.svitlo.data.Regions
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.Snapshot
import ua.`in`.vyshgorod.svitlo.data.ThemeMode

data class ScreenState(
    val loading: Boolean = false,
    val error: String? = null,
    val selection: Selection = Selection(),
    val places: List<Selection> = emptyList(),
    val snapshot: Snapshot? = null,
    val query: String = "",
    val results: List<AddressHit> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val warnEnabled: Boolean = true,
    val notifyOn: Boolean = true,
    val quietNight: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = L10n.language,
    val region: String = Regions.KYIV_OBLAST,
    val operatorId: String = "",
    val apiKey: String = "",
    val notificationsGranted: Boolean = true,
    val batteryFree: Boolean = true,
)
