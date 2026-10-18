@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.schoolgrades.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.schoolgrades.Config

/**
 * Hosts the real Microsoft/Schoolware sign-in in a WebView. Passwords and MFA are handled
 * by Microsoft's page; the app only ever receives the resulting session cookie.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(
    attempt: Int,
    verifying: Boolean,
    error: String?,
    onReached: () -> Unit,
    onRetry: () -> Unit,
) {
    val reached by rememberUpdatedState(onReached)
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        key(attempt) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val webView = WebView(ctx)
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(webView, true)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null && url.startsWith(Config.START_PAGE)) reached()
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = Unit
                    }
                    webView.loadUrl(Config.LOGIN_URL)
                    webView
                },
                onRelease = { it.destroy() },
            )
        }

        if (error != null && !verifying) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onRetry) { Text("Opnieuw proberen") }
                }
            }
        }

        if (!verifying) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                ),
            ) {
                Text(
                    text = "Alleen Microsoft ziet je inloggegevens. Controleer https://github.com/Hacker-Anirudh/schoolware-mobile om dit te verifiëren.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (verifying) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LoadingIndicator()
                    Text("Aanmelden voltooien…", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
