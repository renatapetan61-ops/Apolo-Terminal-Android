package com.apolo.terminal

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors
import com.apolo.terminal.vm.VmService
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var terminal: WebView
    private lateinit var status: TextView
    private var vmService: VmService? = null
    private var bound = false
    private var pendingImport: ImportKind? = null
    private val importExecutor = Executors.newSingleThreadExecutor()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            vmService = (binder as VmService.LocalBinder).service()
            vmService?.setOutputListener { chunk ->
                runOnUiThread {
                    terminal.evaluateJavascript(
                        "window.apoloWrite(${JSONObject.quote(chunk)})",
                        null
                    )
                }
            }
            vmService?.setStatusListener { message -> runOnUiThread { status.text = message } }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vmService = null
            status.text = "VM service disconnected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 15, 28)
        window.navigationBarColor = Color.rgb(10, 15, 28)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 15, 28))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 10)
        }
        val title = TextView(this).apply {
            text = "APOLO TERMINAL"
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        status = TextView(this).apply {
            text = "VM parada · QEMU aguardando"
            textSize = 12f
            setTextColor(Color.rgb(148, 163, 184))
            setPadding(0, 5, 0, 8)
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val start = Button(this).apply {
            text = "Iniciar Linux"
            setOnClickListener { startVm() }
        }
        val stop = Button(this).apply {
            text = "Parar VM"
            setOnClickListener { vmService?.stopVm() }
        }
        actions.addView(start, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(stop, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val imports = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        imports.addView(importButton("QEMU") { chooseImport(ImportKind.QEMU) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        imports.addView(importButton("Kernel") { chooseImport(ImportKind.KERNEL) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        imports.addView(importButton("Disco") { chooseImport(ImportKind.DISK) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(title)
        header.addView(status)
        header.addView(actions)
        header.addView(imports)
        root.addView(header)

        terminal = WebView(this).apply {
            setBackgroundColor(Color.rgb(10, 15, 28))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(TerminalBridge { data -> vmService?.sendInput(data) }, "ApoloBridge")
            loadUrl("file:///android_asset/terminal/index.html")
        }
        root.addView(terminal, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        val serviceIntent = Intent(this, VmService::class.java)
        bound = bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun startVm() {
        val intent = Intent(this, VmService::class.java).setAction(VmService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        status.text = "Preparando VM Linux real…"
    }

    private fun importButton(label: String, action: () -> Unit): Button =
        Button(this).apply { text = "Importar $label"; setOnClickListener { action() } }

    private fun chooseImport(kind: ImportKind) {
        pendingImport = kind
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(picker, FILE_PICKER_REQUEST)
    }

    @Deprecated("Legacy document picker callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_PICKER_REQUEST || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val kind = pendingImport ?: return
        pendingImport = null
        status.text = "Copiando ${kind.label} para o armazenamento privado…"
        importExecutor.execute {
            try {
                val target = File(filesDir, kind.relativePath)
                target.parentFile?.mkdirs()
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Não foi possível abrir o arquivo selecionado" }
                    target.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                }
                if (kind == ImportKind.QEMU) target.setExecutable(true, true)
                runOnUiThread { status.text = "${kind.label} importado: ${target.length()} bytes" }
            } catch (error: Exception) {
                runOnUiThread { status.text = "Falha ao importar ${kind.label}: ${error.message}" }
            }
        }
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        importExecutor.shutdownNow()
        terminal.removeJavascriptInterface("ApoloBridge")
        terminal.destroy()
        super.onDestroy()
    }

    private enum class ImportKind(val label: String, val relativePath: String) {
        QEMU("QEMU ARM64", "bin/qemu-system-aarch64"),
        KERNEL("Kernel Linux", "vm/Image"),
        DISK("Disco ext4", "vm/apolo-rootfs.ext4")
    }

    companion object {
        private const val FILE_PICKER_REQUEST = 7412
    }

    private class TerminalBridge(private val input: (String) -> Unit) {
        @JavascriptInterface
        fun sendInput(value: String) = input(value)
    }
}
