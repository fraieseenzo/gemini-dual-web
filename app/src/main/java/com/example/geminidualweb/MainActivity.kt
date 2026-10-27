package com.example.geminidualweb

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.TypedValue
import android.view.MotionEvent
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
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    companion object {
        private const val GEMINI_ORIGIN = "https://gemini.google.com"
        private const val GEMINI_URL = "https://gemini.google.com/app"
        private const val KEY_ACTIVE_TAB = "key_active_tab"
        private const val KEY_LAST_ACCOUNT_TAB = "key_last_account_tab"

        private const val PREFS_NAME = "gemini_dual_web_prefs"
        private const val PREF_FAB_VISIBLE = "pref_fab_visible"
        private const val PREF_FAB_SIZE_DP = "pref_fab_size_dp"
        private const val PREF_FAB_X = "pref_fab_x"
        private const val PREF_FAB_Y = "pref_fab_y"
        private const val PREF_FAB_HAS_POS = "pref_fab_has_pos"
        private const val PREF_AMOLED_BLACK = "pref_amoled_black"
        private const val PREF_ACCENT_COLOR = "pref_accent_color"

        private const val FAB_SIZE_MINI_DP = 40
        private const val FAB_SIZE_NORMAL_DP = 56
        private const val FAB_SIZE_LARGE_DP = 64

        private const val ACCENT_BLUE = "blue"
        private const val ACCENT_EMERALD = "emerald"
        private const val ACCENT_WHITE = "white"

        private const val DRAG_THRESHOLD_PX = 10f
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null

    private var activePopupDialog: Dialog? = null
    private var activePopupWebView: WebView? = null

    private var lastActiveAccountTabId: Int = R.id.nav_account_alpha

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

        // Enable remote WebView debugging for inspection via chrome://inspect over USB
        WebView.setWebContentsDebuggingEnabled(true)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSwipeRefreshContainers()
        setupDualIsolatedWebViews()
        setupDraggablePromptVaultFab()
        setupSettingsControls()
        setupBottomNavigation(savedInstanceState)
        setupBackNavigation()

        if (savedInstanceState == null) {
            binding.webViewAlpha.loadUrl(GEMINI_URL)
            binding.webViewBeta.loadUrl(GEMINI_URL)
        } else {
            lastActiveAccountTabId = savedInstanceState.getInt(
                KEY_LAST_ACCOUNT_TAB,
                R.id.nav_account_alpha
            )
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
        outState.putInt(KEY_LAST_ACCOUNT_TAB, lastActiveAccountTabId)
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

        // Register document-start script if supported on this WebView runtime
        val docStartSupported = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        } catch (_: Throwable) {
            false
        }
        if (docStartSupported) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    buildAppPromoSuppressionScript(),
                    setOf(GEMINI_ORIGIN)
                )
            }
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

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectAppPromoSuppression(view)
                syncAmoledPureBlackCss(view)
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                injectAppPromoSuppression(view)
                syncAmoledPureBlackCss(view)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                swipeRefresh?.isRefreshing = false
                injectAppPromoSuppression(view)
                syncAmoledPureBlackCss(view)
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
                if (binding.bottomNavigation.selectedItemId != R.id.nav_settings && view == getActiveWebView()) {
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggablePromptVaultFab() {
        val fab = binding.fabPromptVault

        // Restore persisted FAB coordinates once layout bounds are available
        fab.post {
            if (prefs.getBoolean(PREF_FAB_HAS_POS, false)) {
                val savedX = prefs.getFloat(PREF_FAB_X, fab.x)
                val savedY = prefs.getFloat(PREF_FAB_Y, fab.y)
                val parent = fab.parent as? View
                if (parent != null && parent.width > 0 && parent.height > 0) {
                    val maxX = (parent.width - fab.width).coerceAtLeast(0).toFloat()
                    val maxY = (parent.height - binding.bottomNavigation.height - fab.height)
                        .coerceAtLeast(0)
                        .toFloat()
                    fab.x = savedX.coerceIn(0f, maxX)
                    fab.y = savedY.coerceIn(0f, maxY)
                } else {
                    fab.x = savedX
                    fab.y = savedY
                }
            }
        }

        var downRawX = 0f
        var downRawY = 0f
        var dX = 0f
        var dY = 0f
        var isDragging = false

        fab.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val totalMoveDistance = hypot(
                        (event.rawX - downRawX).toDouble(),
                        (event.rawY - downRawY).toDouble()
                    ).toFloat()

                    if (totalMoveDistance >= DRAG_THRESHOLD_PX || isDragging) {
                        isDragging = true
                        val parent = view.parent as? View
                        val maxX = if (parent != null) {
                            (parent.width - view.width).coerceAtLeast(0).toFloat()
                        } else {
                            Float.MAX_VALUE
                        }
                        val maxY = if (parent != null) {
                            (parent.height - binding.bottomNavigation.height - view.height)
                                .coerceAtLeast(0)
                                .toFloat()
                        } else {
                            Float.MAX_VALUE
                        }

                        val newX = (event.rawX + dX).coerceIn(0f, maxX)
                        val newY = (event.rawY + dY).coerceIn(0f, maxY)
                        view.x = newX
                        view.y = newY
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val totalMoveDistance = hypot(
                        (event.rawX - downRawX).toDouble(),
                        (event.rawY - downRawY).toDouble()
                    ).toFloat()

                    if (totalMoveDistance < DRAG_THRESHOLD_PX && !isDragging) {
                        view.performClick()
                        PromptVaultBottomSheet(this) { promptText ->
                            injectPromptIntoActiveWebView(promptText)
                        }.show()
                    } else {
                        prefs.edit()
                            .putFloat(PREF_FAB_X, view.x)
                            .putFloat(PREF_FAB_Y, view.y)
                            .putBoolean(PREF_FAB_HAS_POS, true)
                            .apply()
                    }
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun setupSettingsControls() {
        val isFabVisible = prefs.getBoolean(PREF_FAB_VISIBLE, true)
        val fabSizeDp = prefs.getInt(PREF_FAB_SIZE_DP, FAB_SIZE_NORMAL_DP)
        val isAmoledBlack = prefs.getBoolean(PREF_AMOLED_BLACK, true)
        val accentPreset = prefs.getString(PREF_ACCENT_COLOR, ACCENT_BLUE) ?: ACCENT_BLUE

        // Initialize UI states
        binding.switchFabVisibility.isChecked = isFabVisible
        applyFabVisibility(isFabVisible)

        when (fabSizeDp) {
            FAB_SIZE_MINI_DP -> binding.rgFabSize.check(R.id.rbFabMini)
            FAB_SIZE_LARGE_DP -> binding.rgFabSize.check(R.id.rbFabLarge)
            else -> binding.rgFabSize.check(R.id.rbFabNormal)
        }
        applyFabSize(fabSizeDp)

        binding.switchAmoledBlack.isChecked = isAmoledBlack

        when (accentPreset) {
            ACCENT_EMERALD -> binding.rgAccentColor.check(R.id.rbAccentEmerald)
            ACCENT_WHITE -> binding.rgAccentColor.check(R.id.rbAccentWhite)
            else -> binding.rgAccentColor.check(R.id.rbAccentBlue)
        }
        applyAccentColorPreset(accentPreset)

        // Listeners
        binding.switchFabVisibility.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_FAB_VISIBLE, isChecked).apply()
            applyFabVisibility(isChecked)
        }

        binding.rgFabSize.setOnCheckedChangeListener { _, checkedId ->
            val selectedSizeDp = when (checkedId) {
                R.id.rbFabMini -> FAB_SIZE_MINI_DP
                R.id.rbFabLarge -> FAB_SIZE_LARGE_DP
                else -> FAB_SIZE_NORMAL_DP
            }
            prefs.edit().putInt(PREF_FAB_SIZE_DP, selectedSizeDp).apply()
            applyFabSize(selectedSizeDp)
        }

        binding.switchAmoledBlack.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_AMOLED_BLACK, isChecked).apply()
            syncAmoledPureBlackCss(binding.webViewAlpha)
            syncAmoledPureBlackCss(binding.webViewBeta)
        }

        binding.rgAccentColor.setOnCheckedChangeListener { _, checkedId ->
            val selectedAccent = when (checkedId) {
                R.id.rbAccentEmerald -> ACCENT_EMERALD
                R.id.rbAccentWhite -> ACCENT_WHITE
                else -> ACCENT_BLUE
            }
            prefs.edit().putString(PREF_ACCENT_COLOR, selectedAccent).apply()
            applyAccentColorPreset(selectedAccent)
        }
    }

    private fun applyFabVisibility(visible: Boolean) {
        binding.fabPromptVault.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun applyFabSize(sizeDp: Int) {
        val sizePx = dpToPx(sizeDp)
        binding.fabPromptVault.customSize = sizePx
        binding.fabPromptVault.requestLayout()
    }

    private fun applyAccentColorPreset(preset: String) {
        val accentColorInt = when (preset) {
            ACCENT_EMERALD -> Color.parseColor("#00E676")
            ACCENT_WHITE -> Color.parseColor("#FFFFFF")
            else -> Color.parseColor("#1A73E8")
        }
        val inactiveColorInt = ContextCompat.getColor(this, R.color.text_secondary)

        val navColorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(accentColorInt, inactiveColorInt)
        )

        binding.bottomNavigation.itemIconTintList = navColorStateList
        binding.bottomNavigation.itemTextColor = navColorStateList

        binding.fabPromptVault.backgroundTintList = ColorStateList.valueOf(accentColorInt)
        val fabIconColor = if (preset == ACCENT_BLUE) Color.WHITE else Color.BLACK
        binding.fabPromptVault.imageTintList = ColorStateList.valueOf(fabIconColor)

        binding.topProgressBar.setIndicatorColor(accentColorInt)
        binding.swipeRefreshAlpha.setColorSchemeColors(accentColorInt)
        binding.swipeRefreshBeta.setColorSchemeColors(accentColorInt)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun setupBottomNavigation(savedInstanceState: Bundle?) {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_account_alpha -> {
                    lastActiveAccountTabId = R.id.nav_account_alpha
                    binding.swipeRefreshAlpha.visibility = View.VISIBLE
                    binding.swipeRefreshBeta.visibility = View.GONE
                    binding.settingsScrollView.visibility = View.GONE
                    binding.webViewAlpha.requestFocus()
                    true
                }
                R.id.nav_account_beta -> {
                    lastActiveAccountTabId = R.id.nav_account_beta
                    binding.swipeRefreshAlpha.visibility = View.GONE
                    binding.swipeRefreshBeta.visibility = View.VISIBLE
                    binding.settingsScrollView.visibility = View.GONE
                    binding.webViewBeta.requestFocus()
                    true
                }
                R.id.nav_settings -> {
                    binding.swipeRefreshAlpha.visibility = View.GONE
                    binding.swipeRefreshBeta.visibility = View.GONE
                    binding.settingsScrollView.visibility = View.VISIBLE
                    binding.topProgressBar.visibility = View.GONE
                    true
                }
                else -> false
            }
        }

        val initialTab = savedInstanceState?.getInt(KEY_ACTIVE_TAB, R.id.nav_account_alpha)
            ?: R.id.nav_account_alpha
        binding.bottomNavigation.selectedItemId = initialTab
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

                    if (binding.bottomNavigation.selectedItemId == R.id.nav_settings) {
                        binding.bottomNavigation.selectedItemId = lastActiveAccountTabId
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
        val currentTab = binding.bottomNavigation.selectedItemId
        return when (currentTab) {
            R.id.nav_account_beta -> binding.webViewBeta
            R.id.nav_account_alpha -> binding.webViewAlpha
            else -> {
                if (lastActiveAccountTabId == R.id.nav_account_beta) {
                    binding.webViewBeta
                } else {
                    binding.webViewAlpha
                }
            }
        }
    }

    private fun buildAppPromoSuppressionScript(): String {
        return """
            (function() {
                if (window.__geminiDualPromoSuppressed) {
                    if (typeof window.__geminiDualRunPromoSweep === 'function') {
                        window.__geminiDualRunPromoSweep();
                    }
                    return;
                }
                window.__geminiDualPromoSuppressed = true;

                window.addEventListener('beforeinstallprompt', function(e) {
                    e.preventDefault();
                    if (e.stopImmediatePropagation) e.stopImmediatePropagation();
                    return false;
                }, true);

                var injectPromoCss = function() {
                    var styleId = 'gemini-dual-suppress-app-promo';
                    if (document.getElementById(styleId)) return;
                    var root = document.head || document.documentElement;
                    if (!root) return;
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.type = 'text/css';
                    style.appendChild(document.createTextNode(
                        '[aria-label*="Get the app" i], ' +
                        '[aria-label*="Install" i], ' +
                        'a[href*="play.google.com/store/apps"], ' +
                        'div[role="dialog"]:has(a[href*="play.google.com"]), ' +
                        'div[role="banner"]:has(a[href*="play.google.com"]), ' +
                        'div[role="dialog"]:has(button[aria-label*="Get the app" i]), ' +
                        'div[role="dialog"]:has(button[aria-label*="Continue in browser" i]) { ' +
                        'display: none !important; ' +
                        'visibility: hidden !important; ' +
                        'opacity: 0 !important; ' +
                        'pointer-events: none !important; ' +
                        '}'
                    ));
                    root.appendChild(style);
                };

                var dismissKeywords = [
                    'continue in browser',
                    'stay in browser',
                    'continue on web',
                    'stay on web',
                    'use web',
                    'not now',
                    'dismiss'
                ];

                var promoContextKeywords = [
                    'get the app',
                    'gemini app',
                    'google play',
                    'install',
                    'open in app',
                    'switch to the app',
                    'better in the app'
                ];

                var sweepAndDismissPromos = function() {
                    injectPromoCss();
                    if (!document.body) return;

                    var candidates = document.querySelectorAll('button, a[role="button"], [role="button"]');
                    for (var i = 0; i < candidates.length; i++) {
                        var btn = candidates[i];
                        var label = ((btn.innerText || btn.textContent || '') + ' ' + (btn.getAttribute('aria-label') || '')).trim().toLowerCase();
                        if (!label) continue;

                        for (var k = 0; k < dismissKeywords.length; k++) {
                            var kw = dismissKeywords[k];
                            if (label === kw || label.indexOf(kw) !== -1) {
                                var container = btn.closest('div[role="dialog"], div[role="banner"], div[role="alertdialog"], aside, section') || btn.parentElement;
                                var containerText = container ? (container.innerText || container.textContent || '').toLowerCase() : '';
                                var isUnconditionalWebContinue = (
                                    kw === 'continue in browser' ||
                                    kw === 'stay in browser' ||
                                    kw === 'continue on web' ||
                                    kw === 'stay on web' ||
                                    kw === 'use web'
                                );
                                var hasPromoContext = isUnconditionalWebContinue;
                                if (!hasPromoContext && containerText) {
                                    for (var p = 0; p < promoContextKeywords.length; p++) {
                                        if (containerText.indexOf(promoContextKeywords[p]) !== -1) {
                                            hasPromoContext = true;
                                            break;
                                        }
                                    }
                                }
                                if (hasPromoContext && !btn.dataset.geminiDualClicked) {
                                    btn.dataset.geminiDualClicked = 'true';
                                    try { btn.click(); } catch (err) {}
                                }
                                break;
                            }
                        }
                    }
                };

                window.__geminiDualRunPromoSweep = sweepAndDismissPromos;
                injectPromoCss();

                var startObserver = function() {
                    var target = document.documentElement || document.body;
                    if (!target) return;
                    sweepAndDismissPromos();
                    var observer = new MutationObserver(function() {
                        sweepAndDismissPromos();
                    });
                    observer.observe(target, { childList: true, subtree: true });
                };

                if (document.documentElement) {
                    startObserver();
                } else {
                    document.addEventListener('DOMContentLoaded', startObserver, { once: true });
                }
            })();
        """.trimIndent()
    }

    private fun injectAppPromoSuppression(webView: WebView) {
        webView.evaluateJavascript(buildAppPromoSuppressionScript(), null)
    }

    private fun syncAmoledPureBlackCss(webView: WebView) {
        val isAmoledEnabled = prefs.getBoolean(PREF_AMOLED_BLACK, true)
        val js = if (isAmoledEnabled) {
            """
            (function() {
                var styleId = 'gemini-dual-amoled-pure-black';
                if (document.getElementById(styleId)) return;
                var root = document.head || document.documentElement;
                if (!root) return;
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
                root.appendChild(style);
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                var existing = document.getElementById('gemini-dual-amoled-pure-black');
                if (existing && existing.parentNode) {
                    existing.parentNode.removeChild(existing);
                }
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(js, null)
    }

    private fun injectPromptIntoActiveWebView(promptText: String) {
        if (binding.bottomNavigation.selectedItemId == R.id.nav_settings) {
            binding.bottomNavigation.selectedItemId = lastActiveAccountTabId
        }
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
