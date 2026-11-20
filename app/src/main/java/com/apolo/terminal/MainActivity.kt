package com.apolo.terminal

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.apolo.terminal.vm.VmService
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var terminal: WebView
    private lateinit var status: TextView
    private var vmService: VmService? = null
    private var bound = false

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
        header.addView(title)
        header.addView(status)
        header.addView(actions)
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

    override fun onDestroy() {
        if (bound) unbindService(connection)
        terminal.removeJavascriptInterface("ApoloBridge")
        terminal.destroy()
        super.onDestroy()
    }

    private class TerminalBridge(private val input: (String) -> Unit) {
        @JavascriptInterface
        fun sendInput(value: String) = input(value)
    }
}
