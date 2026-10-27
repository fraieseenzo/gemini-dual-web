package com.example.geminidualweb

import android.app.Application
import android.os.Build
import android.webkit.WebView
import androidx.webkit.WebViewFeature

class GeminiDualApplication : Application() {

    companion object {
        const val PROFILE_ALPHA = "account_alpha"
        const val PROFILE_BETA = "account_beta"
        const val FALLBACK_SUFFIX = "gemini_dual_isolated_suffix"

        var isMultiProfileSupported: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        configureWebViewIsolation()
    }

    private fun configureWebViewIsolation() {
        // Apply safe data-directory suffixing before querying WebView provider features
        // so that older Android runtimes without MULTI_PROFILE still have an isolated WebView data directory
        // and never throw IllegalStateException due to prior WebView provider initialization.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName() ?: packageName
            val sanitizedProcess = processName.replace(":", "_").replace(".", "_")
            val suffix = "${FALLBACK_SUFFIX}_$sanitizedProcess"
            runCatching {
                WebView.setDataDirectorySuffix(suffix)
            }
        }

        isMultiProfileSupported = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (_: Throwable) {
            false
        }
    }
}
