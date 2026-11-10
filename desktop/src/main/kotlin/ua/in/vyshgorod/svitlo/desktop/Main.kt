package ua.`in`.vyshgorod.svitlo.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import java.awt.Frame
import java.awt.SystemTray
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

private val showRequests = Channel<Unit>(Channel.CONFLATED)

fun main() {
    if (!SingleInstance.acquire { showRequests.trySend(Unit) }) return
    application {
        val model = remember { DesktopModel() }
        val trayState = rememberTrayState()
        val open = remember { mutableStateOf(true) }
        val front = remember { mutableStateOf(0) }
        val windowState = rememberWindowState(width = 980.dp, height = 860.dp)
        val widgetOpen = remember { mutableStateOf(DesktopStore.widgetOpen()) }
        val lastShow = remember { longArrayOf(0L) }
        fun show() {
            val now = System.currentTimeMillis()
            if (now - lastShow[0] < 350) return
            lastShow[0] = now
            open.value = true
            front.value += 1
        }
        LaunchedEffect(Unit) {
            model.onNotify = { title, body ->
                trayState.sendNotification(Notification(title, body))
            }
            model.start()
        }
        LaunchedEffect(Unit) {
            for (signal in showRequests) show()
        }
        LaunchedEffect(Unit) {
            if (!SystemTray.isSupported()) return@LaunchedEffect
            val tray = SystemTray.getSystemTray()
            repeat(30) {
                val icon = tray.trayIcons.firstOrNull { it.toolTip == "Светло" }
                if (icon != null) {
                    icon.addMouseListener(object : MouseAdapter() {
                        override fun mousePressed(event: MouseEvent) = openIfLeft(event)
                        override fun mouseReleased(event: MouseEvent) = openIfLeft(event)
                        override fun mouseClicked(event: MouseEvent) = openIfLeft(event)
                        private fun openIfLeft(event: MouseEvent) {
                            if (event.button == MouseEvent.BUTTON1 && !event.isPopupTrigger) show()
                        }
                    })
                    return@LaunchedEffect
                }
                delay(100)
            }
        }
        Tray(
            state = trayState,
            icon = BitmapPainter(trayImage().toComposeImageBitmap()),
            tooltip = model.tooltip(),
            onAction = { show() },
            menu = {
                Item(if (widgetOpen.value) "Скрыть виджет" else "Виджет") {
                    widgetOpen.value = !widgetOpen.value
                    DesktopStore.setWidgetOpen(widgetOpen.value)
                }
                Item("Открыть") { show() }
                Item("Обновить") { model.refresh() }
                Item("Выход", onClick = ::exitApplication)
            },
        )
        Window(
            onCloseRequest = { open.value = false },
            title = "Светло",
            state = windowState,
            visible = open.value,
        ) {
            LaunchedEffect(open.value, front.value) {
                if (!open.value) return@LaunchedEffect
                delay(60)
                val frame = window
                if (frame.extendedState and Frame.ICONIFIED != 0) {
                    frame.extendedState = Frame.NORMAL
                }
                frame.isVisible = true
                frame.toFront()
                frame.requestFocus()
                frame.isAlwaysOnTop = true
                frame.isAlwaysOnTop = false
            }
            DesktopApp(model)
        }
        if (widgetOpen.value) {
            Window(
                onCloseRequest = {
                    widgetOpen.value = false
                    DesktopStore.setWidgetOpen(false)
                },
                title = "Светло",
                alwaysOnTop = true,
                state = rememberWindowState(width = 400.dp, height = 680.dp),
            ) {
                DesktopWidget(model)
            }
        }
    }
}

private fun trayImage(): BufferedImage {
    val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
    val canvas = image.createGraphics()
    canvas.color = java.awt.Color(0x1C, 0x6E, 0x8A)
    canvas.fillOval(2, 2, 28, 28)
    canvas.color = java.awt.Color(0xF4, 0xF0, 0xE6)
    canvas.fillOval(10, 10, 12, 12)
    canvas.dispose()
    return image
}
