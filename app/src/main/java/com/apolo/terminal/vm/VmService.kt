package com.apolo.terminal.vm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class VmService : Service() {
    inner class LocalBinder : Binder() {
        fun service(): VmService = this@VmService
    }

    private val binder = LocalBinder()
    private val executor = Executors.newSingleThreadExecutor()
    private var process: Process? = null
    private var outputListener: ((String) -> Unit)? = null
    private var statusListener: ((String) -> Unit)? = null
    private val outputBuffer = StringBuilder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startForeground(NOTIFICATION_ID, buildNotification("Inicializando Linux convidado"))
            startVm()
        }
        return START_STICKY
    }

    fun setOutputListener(listener: (String) -> Unit) {
        outputListener = listener
        synchronized(outputBuffer) {
            if (outputBuffer.isNotEmpty()) listener(outputBuffer.toString())
        }
    }

    fun setStatusListener(listener: (String) -> Unit) {
        statusListener = listener
    }

    fun startVm() {
        executor.execute {
            if (process?.isAlive == true) {
                publishStatus("VM já está em execução")
                return@execute
            }

            val bin = File(filesDir, "bin/qemu-system-aarch64")
            val kernel = File(filesDir, "vm/Image")
            val disk = File(filesDir, "vm/apolo-rootfs.ext4")
            val missing = listOf(bin to "QEMU ARM64", kernel to "Kernel Linux", disk to "Disco ext4")
                .filter { !it.first.isFile }
                .joinToString { it.second }

            if (missing.isNotEmpty()) {
                publishStatus("Runtime incompleto: falta $missing. A VM não será simulada.")
                return@execute
            }

            bin.setExecutable(true, true)
            publishStatus("Iniciando QEMU ARM64 em modo TCG…")
            try {
                val command = listOf(
                    bin.absolutePath,
                    "-machine", "virt",
                    "-cpu", "cortex-a57",
                    "-m", "2048",
                    "-smp", "2",
                    "-kernel", kernel.absolutePath,
                    "-drive", "if=none,id=rootdisk,file=${disk.absolutePath},format=raw",
                    "-device", "virtio-blk-device,drive=rootdisk",
                    "-netdev", "user,id=net0,hostfwd=tcp:127.0.0.1:2222-:22",
                    "-device", "virtio-net-device,netdev=net0",
                    "-nographic",
                    "-monitor", "none",
                    "-serial", "stdio",
                    "-append", "console=ttyAMA0,115200 root=/dev/vda rw rootwait init=/sbin/apolo-init"
                )
                val child = ProcessBuilder(command).directory(filesDir).redirectErrorStream(true).start()
                process = child
                publishStatus("QEMU iniciado; aguardando boot do kernel Linux…")
                val reader = InputStreamReader(child.inputStream, StandardCharsets.UTF_8)
                val chars = CharArray(4096)
                while (true) {
                    val count = reader.read(chars)
                    if (count < 0) break
                    val chunk = String(chars, 0, count)
                    synchronized(outputBuffer) {
                        outputBuffer.append(chunk)
                        if (outputBuffer.length > MAX_OUTPUT_CHARS) {
                            outputBuffer.delete(0, outputBuffer.length - MAX_OUTPUT_CHARS)
                        }
                    }
                    outputListener?.invoke(chunk)
                    if (chunk.contains("APOLO_GUEST_BOOTED")) {
                        publishStatus("Kernel Linux iniciado · verificando serviços do guest")
                    }
                }
                val exit = child.waitFor()
                publishStatus("QEMU encerrou (código $exit)")
                process = null
            } catch (error: Exception) {
                publishStatus("Falha ao iniciar QEMU: ${error.message}")
                process = null
            }
        }
    }

    fun sendInput(value: String) {
        try {
            process?.outputStream?.apply {
                write(value.toByteArray(StandardCharsets.UTF_8))
                flush()
            }
        } catch (error: Exception) {
            publishStatus("Falha ao enviar entrada ao terminal: ${error.message}")
        }
    }

    fun stopVm() {
        executor.execute {
            publishStatus("Encerrando VM…")
            process?.destroy()
            if (process?.isAlive == true) process?.destroyForcibly()
            process = null
            publishStatus("VM parada")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun publishStatus(message: String) {
        statusListener?.invoke(message)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.notify(NOTIFICATION_ID, buildNotification(message))
        }
    }

    private fun buildNotification(message: String): Notification {
        val channelId = "apolo_vm"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "APOLO Linux VM", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("APOLO Terminal")
                .setContentText(message)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("APOLO Terminal")
                .setContentText(message)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        process?.destroy()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.apolo.terminal.action.START_VM"
        private const val NOTIFICATION_ID = 4101
        private const val MAX_OUTPUT_CHARS = 65536
    }
}
