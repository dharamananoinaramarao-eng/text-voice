package com.example.voicegenerator

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.example.voicegenerator.ui.theme.VoiceGeneratorTheme

class MainActivity : ComponentActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uriResult = if (result.resultCode == RESULT_OK) {
            val intentData = result.data
            if (intentData?.clipData != null) {
                val count = intentData.clipData!!.itemCount
                Array(count) { i -> intentData.clipData!!.getItemAt(i).uri }
            } else if (intentData?.data != null) {
                arrayOf(intentData.data!!)
            } else {
                null
            }
        } else {
            null
        }
        filePathCallback?.onReceiveValue(uriResult)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceGeneratorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    VoiceGeneratorScreen(
                        onShowFileChooser = { callback, params ->
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = callback
                            val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            try {
                                fileChooserLauncher.launch(intent)
                                true
                            } catch (_: Exception) {
                                filePathCallback = null
                                Toast.makeText(this, "No app available to pick file", Toast.LENGTH_SHORT).show()
                                false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceGeneratorScreen(
    modifier: Modifier = Modifier,
    onShowFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean = { _, _ -> false }
) {
    val isEmulator = Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("google_sdk") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
    val backendUrl = if (isEmulator) "http://10.0.2.2:5000" else "http://127.0.0.1:5000"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            return onShowFileChooser(filePathCallback, fileChooserParams)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                return false
                            }
                            try {
                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "No app available to handle this link", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to handle link: $url", Toast.LENGTH_SHORT).show()
                            }
                            return true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                val failedUrl = request.url.toString()
                                val errorHtml = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                        <style>
                                            body { font-family: sans-serif; text-align: center; padding: 24px; background: #f8f9fa; color: #333; }
                                            h2 { color: #d32f2f; }
                                            p { font-size: 14px; line-height: 1.5; color: #555; }
                                            code { background: #e0e0e0; padding: 4px 8px; border-radius: 4px; font-family: monospace; font-size: 13px; }
                                            .btn { display: inline-block; margin-top: 16px; padding: 12px 24px; background: #1976d2; color: white; text-decoration: none; border-radius: 6px; font-weight: bold; }
                                        </style>
                                    </head>
                                    <body>
                                        <h2>Unable to Connect</h2>
                                        <p>Could not load <code>$failedUrl</code></p>
                                        <p><b>Quick Fix Checklist:</b><br>
                                        1. Make sure the Flask server is running: <code>python app.py</code><br>
                                        2. For USB devices, run in the PC terminal: <code>adb reverse tcp:5000 tcp:5000</code><br>
                                        3. Restart the app after the backend is available.</p>
                                        <a class="btn" href="javascript:location.reload()">Retry Connection</a>
                                    </body>
                                    </html>
                                """.trimIndent()
                                view?.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
                            }
                        }
                    }

                    setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                        try {
                            val request = DownloadManager.Request(Uri.parse(url)).apply {
                                setMimeType(mimetype)
                                addRequestHeader("User-Agent", userAgent)
                                setDescription("Downloading voice file...")
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                            }
                            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            dm.enqueue(request)
                            Toast.makeText(context, "Downloading audio...", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    loadUrl(backendUrl)
                }
            },
            update = { webView ->
                if (webView.url != backendUrl && backendUrl.isNotBlank() && webView.url != "about:blank") {
                    webView.loadUrl(backendUrl)
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VoiceGeneratorTheme {
        Text("Voice Generator Preview")
    }
}
