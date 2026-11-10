package ua.`in`.vyshgorod.svitlo.desktop

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock

object SingleInstance {
    private var heldLock: FileLock? = null
    private var lockFile: RandomAccessFile? = null
    private var server: ServerSocket? = null

    fun acquire(onShow: () -> Unit): Boolean {
        val lock = File(DesktopStore.directory, "instance.lock")
        val port = File(DesktopStore.directory, "instance.port")
        val file = RandomAccessFile(lock, "rw")
        val grabbed = runCatching { file.channel.tryLock() }.getOrNull()
        if (grabbed == null) {
            file.close()
            ping(port)
            return false
        }
        lockFile = file
        heldLock = grabbed
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        server = socket
        port.writeText(socket.localPort.toString())
        Thread({
            while (!socket.isClosed) {
                try {
                    socket.accept().use { onShow() }
                } catch (_: Exception) {
                    if (socket.isClosed) break
                }
            }
        }, "svitlo-show").apply { isDaemon = true }.start()
        return true
    }

    private fun ping(portFile: File) {
        repeat(25) { attempt ->
            val port = portFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
            if (port != null) {
                val sent = runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 300)
                        socket.getOutputStream().write(1)
                        socket.getOutputStream().flush()
                    }
                }.isSuccess
                if (sent) return
            }
            if (attempt < 24) Thread.sleep(80)
        }
    }
}
