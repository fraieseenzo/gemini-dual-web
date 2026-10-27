package com.example.geminidualweb

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.geminidualweb.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        private const val GEMINI_URL = "https://gemini.google.com/app"
        private const val KEY_ACTIVE_TAB = "key_active_tab"
    }

    private lateinit var binding: ActivityMainBinding

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null

    private var activePopupDialog: Dialog? = null
    private var activePopupWebView: WebView? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileUploadCallback
        fileUploadCallback = null
        if (callback == null) return@registerForActivityResult

        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            when {
                data == null -> null
                data.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
                }
                data.data != null -> arrayOf(data.data!!)
                else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            }
        } else {
            null
        }
        callback.onReceiveValue(uris)
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val request = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (request == null) return@registerForActivityResult

        val granted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (granted) {
            request.grant(request.resources)
        } else {
            request.deny()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSwipeRefreshContainers()
        setupDualIsolatedWebViews()
        setupBottomNavigation(savedInstanceState)
        setupPromptVaultFab()
        setupBackNavigation()

        if (savedInstanceState == null) {
            binding.webViewAlpha.loadUrl(GEMINI_URL)
            binding.webViewBeta.loadUrl(GEMINI_URL)
        } else {
            val alphaBundle = savedInstanceState.getBundle("webViewAlphaState")
            val betaBundle = savedInstanceState.getBundle("webViewBetaState")
            if (alphaBundle != null) {
                binding.webViewAlpha.restoreState(alphaBundle)
            } else {
                binding.webViewAlpha.loadUrl(GEMINI_URL)
            }
            if (betaBundle != null) {
                binding.webViewBeta.restoreState(betaBundle)
            } else {
                binding.webViewBeta.loadUrl(GEMINI_URL)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_TAB, binding.bottomNavigation.selectedItemId)
        val alphaBundle = Bundle()
        val betaBundle = Bundle()
        binding.webViewAlpha.saveState(alphaBundle)
        binding.webViewBeta.saveState(betaBundle)
        outState.putBundle("webViewAlphaState", alphaBundle)
        outState.putBundle("webViewBetaState", betaBundle)
    }

    private fun setupSwipeRefreshContainers() {
        configureSwipeRefresh(binding.swipeRefreshAlpha, binding.webViewAlpha)
        configureSwipeRefresh(binding.swipeRefreshBeta, binding.webViewBeta)
    }

    private fun configureSwipeRefresh(
        swipeRefresh: SwipeRefreshLayout,
        webView: WebView
    ) {
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface_elevated)
        )
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.gemini_blue),
            ContextCompat.getColor(this, R.color.gemini_purple)
        )
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0
        }
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun setupDualIsolatedWebViews() {
        val multiProfileSupported = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (_: Throwable) {
            false
        }

        if (!multiProfileSupported) {
            Toast.makeText(
                this,
                getString(R.string.profile_fallback_warning),
                Toast.LENGTH_LONG
            ).show()
        }

        configureWebViewInstance(
            webView = binding.webViewAlpha,
            swipeRefresh = binding.swipeRefreshAlpha,
            profileName = GeminiDualApplication.PROFILE_ALPHA,
            multiProfileSupported = multiProfileSupported
        )

        configureWebViewInstance(
            webView = binding.webViewBeta,
            swipeRefresh = binding.swipeRefreshBeta,
            profileName = GeminiDualApplication.PROFILE_BETA,
            multiProfileSupported = multiProfileSupported
        )
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun configureWebViewInstance(
        webView: WebView,
        swipeRefresh: SwipeRefreshLayout?,
        profileName: String,
        multiProfileSupported: Boolean
    ) {
        if (multiProfileSupported) {
            val profileStore = ProfileStore.getInstance()
            val profile = profileStore.getOrCreateProfile(profileName)
            WebViewCompat.setProfile(webView, profileName)
            profile.cookieManager.setAcceptCookie(true)
            profile.cookieManager.setAcceptThirdPartyCookies(webView, true)
        } else {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        webView.setBackgroundColor(Color.BLACK)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Strip "; wv" and standalone "wv" tokens from User-Agent to prevent Google OAuth 403 disallowed_useragent
            userAgentString = buildSanitizedChromeUserAgent(userAgentString)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    false
                } else {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    }
                    true
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                injectAmoledPureBlackCss(view)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                swipeRefresh?.isRefreshing = false
                injectAmoledPureBlackCss(view)
            }
        }

        webView.webChromeClient = createWebChromeClient(
            profileName = profileName,
            multiProfileSupported = multiProfileSupported,
            swipeRefresh = swipeRefresh
        )
    }

    private fun createWebChromeClient(
        profileName: String,
        multiProfileSupported: Boolean,
        swipeRefresh: SwipeRefreshLayout?
    ): WebChromeClient {
        return object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (view == getActiveWebView()) {
                    if (newProgress in 1..99) {
                        binding.topProgressBar.visibility = View.VISIBLE
                        binding.topProgressBar.setProgressCompat(newProgress, true)
                    } else {
                        binding.topProgressBar.visibility = View.GONE
                        swipeRefresh?.isRefreshing = false
                    }
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val resources = request.resources
                val needsAudio = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (needsAudio) {
                    val hasAudioPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasAudioPermission) {
                        request.grant(resources)
                    } else {
                        pendingWebPermissionRequest?.deny()
                        pendingWebPermissionRequest = request
                        audioPermissionLauncher.launch(
                            arrayOf(Manifest.permission.RECORD_AUDIO)
                        )
                    }
                } else {
                    request.grant(resources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                return try {
                    val intent = fileChooserParams.createIntent()
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    fileUploadCallback = null
                    false
                }
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val popupWebView = WebView(this@MainActivity)
                configureWebViewInstance(
                    webView = popupWebView,
                    swipeRefresh = null,
                    profileName = profileName,
                    multiProfileSupported = multiProfileSupported
                )

                val dialog = Dialog(this@MainActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))

                val container = FrameLayout(this@MainActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.BLACK)
                    addView(
                        popupWebView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                dialog.setContentView(container)
                dialog.setOnDismissListener {
                    container.removeAllViews()
                    popupWebView.destroy()
                    if (activePopupWebView == popupWebView) {
                        activePopupWebView = null
                        activePopupDialog = null
                    }
                }

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        super.onCloseWindow(window)
                        dialog.dismiss()
                    }
                }

                activePopupDialog?.dismiss()
                activePopupDialog = dialog
                activePopupWebView = popupWebView
                dialog.show()

                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                super.onCloseWindow(window)
                if (window == activePopupWebView) {
                    activePopupDialog?.dismiss()
                }
            }
        }
    }

    private fun buildSanitizedChromeUserAgent(defaultUserAgent: String): String {
        return defaultUserAgent
            .replace("; wv)", ")")
            .replace("; wv;", ";")
            .replace("; wv ", " ")
            .replace("Version/4.0 ", "")
            .trim()
    }

    private fun setupBottomNavigation(savedInstanceState: Bundle?) {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_account_alpha -> {
                    binding.swipeRefreshAlpha.visibility = View.VISIBLE
                    binding.swipeRefreshBeta.visibility = View.GONE
                    binding.webViewAlpha.requestFocus()
                    true
                }
                R.id.nav_account_beta -> {
                    binding.swipeRefreshAlpha.visibility = View.GONE
                    binding.swipeRefreshBeta.visibility = View.VISIBLE
                    binding.webViewBeta.requestFocus()
                    true
                }
                else -> false
            }
        }

        val initialTab = savedInstanceState?.getInt(KEY_ACTIVE_TAB, R.id.nav_account_alpha)
            ?: R.id.nav_account_alpha
        binding.bottomNavigation.selectedItemId = initialTab
    }

    private fun setupPromptVaultFab() {
        binding.fabPromptVault.setOnClickListener {
            PromptVaultBottomSheet(this) { promptText ->
                injectPromptIntoActiveWebView(promptText)
            }.show()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val popupWebView = activePopupWebView
                    val popupDialog = activePopupDialog
                    if (popupDialog != null && popupDialog.isShowing && popupWebView != null) {
                        if (popupWebView.canGoBack()) {
                            popupWebView.goBack()
                        } else {
                            popupDialog.dismiss()
                        }
                        return
                    }

                    val activeWebView = getActiveWebView()
                    if (activeWebView.canGoBack()) {
                        activeWebView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )
    }

    private fun getActiveWebView(): WebView {
        return if (binding.bottomNavigation.selectedItemId == R.id.nav_account_beta) {
            binding.webViewBeta
        } else {
            binding.webViewAlpha
        }
    }

    private fun injectAmoledPureBlackCss(webView: WebView) {
        val js = """
            (function() {
                var styleId = 'gemini-dual-amoled-pure-black';
                if (document.getElementById(styleId)) return;
                var style = document.createElement('style');
                style.id = styleId;
                style.type = 'text/css';
                style.appendChild(document.createTextNode(
                    ':root, html, body, chat-app, main, [role="main"], .bard-sidenav-container, .chat-container { ' +
                    'background-color: #000000 !important; ' +
                    '--bard-color-surface: #000000 !important; ' +
                    '--gm3-sys-color-surface: #000000 !important; ' +
                    '--gm3-sys-color-background: #000000 !important; ' +
                    '}'
                ));
                (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectPromptIntoActiveWebView(promptText: String) {
        val escapedJsonText = JSONObject.quote(promptText)
        val js = """
            (function() {
                var text = $escapedJsonText;
                var selectors = [
                    'rich-textarea div.ql-editor[contenteditable="true"]',
                    'div.ql-editor[contenteditable="true"]',
                    'div[contenteditable="true"][role="textbox"]',
                    '[contenteditable="true"]',
                    'textarea'
                ];
                var target = null;
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) {
                        target = el;
                        break;
                    }
                }
                if (!target) return false;
                target.focus();
                if (target.tagName && target.tagName.toLowerCase() === 'textarea') {
                    target.value = text;
                    target.dispatchEvent(new Event('input', { bubbles: true }));
                    return true;
                }
                var selection = window.getSelection();
                if (selection) {
                    var range = document.createRange();
                    range.selectNodeContents(target);
                    range.collapse(false);
                    selection.removeAllRanges();
                    selection.addRange(range);
                }
                var inserted = false;
                try {
                    inserted = document.execCommand('insertText', false, text);
                } catch (e) {
                    inserted = false;
                }
                if (!inserted) {
                    target.textContent = (target.textContent || '') + text;
                    target.dispatchEvent(new InputEvent('input', {
                        bubbles: true,
                        cancelable: true,
                        inputType: 'insertText',
                        data: text
                    }));
                }
                return true;
            })();
        """.trimIndent()
        getActiveWebView().evaluateJavascript(js, null)
    }

    override fun onDestroy() {
        activePopupDialog?.dismiss()
        binding.webViewAlpha.destroy()
        binding.webViewBeta.destroy()
        super.onDestroy()
    }
}
