package ua.`in`.vyshgorod.svitlo.ui

import ua.`in`.vyshgorod.svitlo.data.AddressHit
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.Snapshot
import ua.`in`.vyshgorod.svitlo.data.ThemeMode

data class ScreenState(
    val loading: Boolean = false,
    val error: String? = null,
    val selection: Selection = Selection(),
    val snapshot: Snapshot? = null,
    val query: String = "",
    val results: List<AddressHit> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val warnEnabled: Boolean = true,
    val notifyOn: Boolean = true,
    val quietNight: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val notificationsGranted: Boolean = true,
    val batteryFree: Boolean = true,
)
