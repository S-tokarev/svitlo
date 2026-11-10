package ua.`in`.vyshgorod.svitlo.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import java.awt.image.BufferedImage

fun main() = application {
    val model = androidx.compose.runtime.remember { DesktopModel() }
    val trayState = rememberTrayState()
    val open = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        model.onNotify = { title, body ->
            trayState.sendNotification(Notification(title, body))
        }
        model.start()
    }
    Tray(
        state = trayState,
        icon = BitmapPainter(trayImage().toComposeImageBitmap()),
        tooltip = "Светло",
        menu = {
            Item("Открыть") { open.value = true }
            Item("Обновить") { model.refresh() }
            Item("Выход", onClick = ::exitApplication)
        },
    )
    if (open.value) {
        Window(
            onCloseRequest = { open.value = false },
            title = "Светло",
            state = rememberWindowState(width = 980.dp, height = 860.dp),
        ) {
            DesktopApp(model)
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
