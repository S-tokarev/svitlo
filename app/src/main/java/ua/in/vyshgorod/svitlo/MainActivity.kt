package ua.`in`.vyshgorod.svitlo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.content.pm.PackageManager
import ua.`in`.vyshgorod.svitlo.alert.AlarmScheduler
import ua.`in`.vyshgorod.svitlo.alert.Notifier
import ua.`in`.vyshgorod.svitlo.data.AddressHit
import ua.`in`.vyshgorod.svitlo.data.AddressSearch
import ua.`in`.vyshgorod.svitlo.data.AppLanguage
import ua.`in`.vyshgorod.svitlo.data.L10n
import ua.`in`.vyshgorod.svitlo.data.Selection
import ua.`in`.vyshgorod.svitlo.data.SvitloClient
import ua.`in`.vyshgorod.svitlo.data.SvitloRepository
import ua.`in`.vyshgorod.svitlo.data.SvitloStore
import ua.`in`.vyshgorod.svitlo.data.ThemeMode
import ua.`in`.vyshgorod.svitlo.ui.LocalSvitloColors
import ua.`in`.vyshgorod.svitlo.ui.MainScreen
import ua.`in`.vyshgorod.svitlo.ui.ScreenState
import ua.`in`.vyshgorod.svitlo.ui.SvitloTheme
import ua.`in`.vyshgorod.svitlo.widget.SvitloWidget

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(ScreenState())
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updatePermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensure(this)
        reloadLocal()
        AlarmScheduler.scheduleRefresh(this)
        setContent {
            SvitloTheme(screen.themeMode) {
                val view = LocalView.current
                val colors = LocalSvitloColors.current
                if (!view.isInEditMode) {
                    SideEffect { view.paintSystemBars(colors.paper.toArgb(), !colors.dark) }
                }
                MainScreen(
                    state = screen,
                    onRefresh = ::refresh,
                    onSelectGroup = ::selectGroup,
                    onQuery = { screen = screen.copy(query = it, searchError = null) },
                    onSearch = ::search,
                    onPickAddress = ::pickAddress,
                    onFocusPlace = ::focusPlace,
                    onRemovePlace = ::removePlace,
                    onWarn = ::setWarn,
                    onNotifyOn = ::setNotifyOn,
                    onQuietNight = ::setQuietNight,
                    onTheme = ::setTheme,
                    onLanguage = ::setLanguage,
                    onRequestNotifications = ::requestNotifications,
                    onRequestBattery = ::requestBattery,
                    onOpenSource = {
                        val group = screen.selection.group
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SvitloClient.pageFor(group))))
                    },
                )
            }
        }
        refresh()
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted()) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
        val stored = SvitloStore(this).loadSnapshot()
        if (stored != null && stored.fetchedAt != screen.snapshot?.fetchedAt) {
            screen = screen.copy(snapshot = stored, error = null)
        }
        val fresh = screen.snapshot
        val age = if (fresh == null) Long.MAX_VALUE else System.currentTimeMillis() - fresh.fetchedAt
        if (age >= 15 * 60 * 1000L && !screen.loading) refresh()
        SvitloWidget.updateAll(this)
    }

    private fun reloadLocal() {
        val store = SvitloStore(this)
        screen = screen.copy(
            selection = store.loadSelection(),
            places = store.loadPlaces(),
            snapshot = store.loadSnapshot(),
            warnEnabled = store.warnEnabled(),
            notifyOn = store.notifyWhenOn(),
            quietNight = store.quietNight(),
            themeMode = store.themeMode(),
            language = L10n.language,
            notificationsGranted = notificationsGranted(),
            batteryFree = batteryFree(),
        )
    }

    private fun refresh() {
        screen = screen.copy(loading = true, error = null)
        SvitloRepository.refreshAsync(this) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { snapshot ->
                        screen = screen.copy(loading = false, error = null, snapshot = snapshot)
                    },
                    onFailure = { error ->
                        screen = screen.copy(
                            loading = false,
                            error = error.message ?: L10n.refreshFailed(),
                        )
                    },
                )
            }
        }
    }

    private fun selectGroup(group: String) {
        val store = SvitloStore(this)
        val selection = Selection(group = group)
        store.savePlaces(store.loadPlaces(), selection)
        screen = screen.copy(selection = selection, places = store.loadPlaces(), results = emptyList())
        SvitloRepository.rescheduleFromCache(this)
    }

    private fun focusPlace(place: Selection) {
        val store = SvitloStore(this)
        store.savePlaces(store.loadPlaces(), place)
        screen = screen.copy(selection = place, places = store.loadPlaces(), results = emptyList())
        SvitloWidget.updateAll(this)
    }

    private fun removePlace(place: Selection) {
        val store = SvitloStore(this)
        val places = store.loadPlaces().filterNot { it.placeKey == place.placeKey }
        val focus = if (screen.selection.placeKey == place.placeKey) places.lastOrNull() ?: Selection() else screen.selection
        store.savePlaces(places, focus)
        screen = screen.copy(selection = focus, places = places, results = emptyList())
        SvitloRepository.rescheduleFromCache(this)
    }

    private fun pickAddress(hit: AddressHit) {
        if (hit.group.isBlank()) {
            screen = screen.copy(searching = true, searchError = null, results = emptyList())
            SvitloRepository.housesAsync(this, hit.city, hit.street, AddressSearch.houseFilter(screen.query)) { result ->
                runOnUiThread {
                    result.fold(
                        onSuccess = { lookup -> showLookup(lookup, L10n.streetHasNoGroup()) },
                        onFailure = { error ->
                            screen = screen.copy(searching = false, searchError = error.message ?: L10n.searchFailed())
                        },
                    )
                }
            }
            return
        }
        val selection = Selection(group = hit.group, street = hit.street, house = hit.house, city = hit.city)
        val store = SvitloStore(this)
        store.saveSelection(selection)
        screen = screen.copy(selection = selection, places = store.loadPlaces(), results = emptyList(), query = "")
        SvitloRepository.rescheduleFromCache(this)
    }

    private fun search() {
        val query = screen.query.trim()
        if (query.length < 2) {
            screen = screen.copy(searchError = L10n.typeTwoLetters())
            return
        }
        screen = screen.copy(searching = true, searchError = null)
        SvitloRepository.searchAsync(this, query) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { lookup -> showLookup(lookup, L10n.nothingFound()) },
                    onFailure = { error ->
                        screen = screen.copy(searching = false, searchError = error.message ?: L10n.searchFailed())
                    },
                )
            }
        }
    }

    private fun showLookup(lookup: AddressSearch.Lookup, emptyMessage: String) {
        screen = screen.copy(
            searching = false,
            results = lookup.hits,
            searchError = lookup.hint ?: if (lookup.hits.isEmpty()) emptyMessage else null,
        )
    }

    private fun setWarn(enabled: Boolean) {
        SvitloStore(this).setWarnEnabled(enabled)
        screen = screen.copy(warnEnabled = enabled)
        SvitloRepository.rescheduleFromCache(this)
    }

    private fun setNotifyOn(enabled: Boolean) {
        SvitloStore(this).setNotifyWhenOn(enabled)
        screen = screen.copy(notifyOn = enabled)
        SvitloRepository.rescheduleFromCache(this)
    }

    private fun setQuietNight(enabled: Boolean) {
        SvitloStore(this).setQuietNight(enabled)
        screen = screen.copy(quietNight = enabled)
    }

    private fun setTheme(mode: ThemeMode) {
        SvitloStore(this).setThemeMode(mode)
        screen = screen.copy(themeMode = mode)
    }

    private fun setLanguage(language: AppLanguage) {
        L10n.language = language
        SvitloStore(this).setLanguage(language)
        screen = screen.copy(language = language)
        SvitloWidget.updateAll(this)
        SvitloRepository.rescheduleFromCache(this)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestBattery() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
    }

    private fun updatePermissions() {
        screen = screen.copy(notificationsGranted = notificationsGranted(), batteryFree = batteryFree())
    }

    private fun notificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun batteryFree(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        return power.isIgnoringBatteryOptimizations(packageName)
    }
}

private fun View.paintSystemBars(color: Int, lightBars: Boolean) {
    val window = context.findActivity().window
    window.statusBarColor = color
    window.navigationBarColor = color
    window.decorView.setBackgroundColor(color)
    WindowCompat.getInsetsController(window, this).apply {
        isAppearanceLightStatusBars = lightBars
        isAppearanceLightNavigationBars = lightBars
    }
}

private fun Context.findActivity(): Activity {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    error(L10n.windowMissing())
}
