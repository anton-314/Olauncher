package app.olauncher.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.inSpans
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityBreathingPauseBinding
import app.olauncher.helper.AnkiCardHtml
import app.olauncher.helper.AnkiDroid
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hideStatusBar
import app.olauncher.helper.isDarkThemeOn
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.startActivitySafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Full screen breathing pause before a paused app. With an Anki deck chosen in settings, the due
 * cards of that deck are reviewed first and the app opens after the last answer; without one, or
 * when no card is due, "Open" unlocks after a countdown.
 *
 * Started by MainActivity for a launch from the launcher, which opens the app on RESULT_OK.
 * Started by MyAccessibilityService when a paused app came to the front some other way, e.g.
 * from recents: the app is already open underneath, so closing goes back to the home screen.
 */
class BreathingPauseActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_LABEL = "label"
        const val EXTRA_FROM_LAUNCHER = "fromLauncher"

        // AnkiDroid may have to start first; don't keep the user waiting longer for cards
        private const val ANKI_TIMEOUT_MS = 3000L
    }

    private lateinit var prefs: Prefs
    private lateinit var binding: ActivityBreathingPauseBinding
    private var appPackage = ""
    private var fromLauncher = false
    private var remaining = Constants.BREATHING_PAUSE_SECONDS
    private var breathing: ValueAnimator? = null

    private var ankiCards: List<AnkiDroid.DueCard> = emptyList()
    private var ankiIndex = 0
    private var cardContent: AnkiDroid.CardContent? = null
    private var cardShownAt = 0L
    private var cardView: WebView? = null

    private val countdown = object : Runnable {
        override fun run() {
            val openLabel = getString(R.string.open_app)
            if (remaining > 0) {
                binding.tvOpen.text = "$openLabel ($remaining)"
                binding.tvOpen.isEnabled = false
                binding.tvOpen.alpha = 0.5f
                remaining--
                binding.root.postDelayed(this, 1000)
            } else {
                binding.tvOpen.text = openLabel
                binding.tvOpen.isEnabled = true
                binding.tvOpen.alpha = 1f
            }
        }
    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        // The service can start this while the launcher process is not running
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        appPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (appPackage.isEmpty()) {
            finish()
            return
        }
        fromLauncher = intent.getBooleanExtra(EXTRA_FROM_LAUNCHER, false)
        if (prefs.boldFont) theme.applyStyle(R.style.BoldFontOverlay, true)

        binding = ActivityBreathingPauseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (!prefs.showStatusBar) window.hideStatusBar()

        val label = intent.getStringExtra(EXTRA_LABEL) ?: appLabel(appPackage)
        binding.tvMessage.text = getString(R.string.breathing_pause_message, label)
        binding.tvClose.setOnClickListener { close() }
        binding.tvAnkiClose.setOnClickListener { close() }
        binding.tvOpen.setOnClickListener { open() }
        binding.tvShowAnswer.setOnClickListener { showAnswer() }
        binding.answerAgain.setOnClickListener { answer(1) }
        binding.answerHard.setOnClickListener { answer(2) }
        binding.answerGood.setOnClickListener { answer(3) }
        binding.answerEasy.setOnClickListener { answer(4) }
        onBackPressedDispatcher.addCallback(this) { close() }

        if (!isEinkDisplay() && !isSystemAnimationsDisabled()) startBreathing()
        if (prefs.ankiDeckId != 0L && AnkiDroid.hasPermission(this)) loadAnkiCards()
        else startCountdown()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.root.removeCallbacks(countdown)
        breathing?.cancel()
        cardView?.destroy()
        super.onDestroy()
    }

    private fun startCountdown() {
        binding.tvOpen.isInvisible = false
        countdown.run()
    }

    // One breath over the whole countdown: the circle grows while breathing in, shrinks while breathing out
    private fun startBreathing() {
        val halfBreathMs = Constants.BREATHING_PAUSE_SECONDS * 1000L / 2
        breathing = ValueAnimator.ofFloat(0.6f, 1f).apply {
            duration = halfBreathMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                binding.breathCircle.scaleX = scale
                binding.breathCircle.scaleY = scale
            }
            start()
        }
    }

    private fun open() {
        prefs.grantBreathingPause(appPackage)
        if (fromLauncher) setResult(RESULT_OK)
        finish()
    }

    private fun close() {
        if (!fromLauncher)
            startActivitySafely(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        finish()
    }

    // Anki review

    // The breathing circle keeps going while the cards load; without a card the countdown takes over
    private fun loadAnkiCards() {
        binding.tvOpen.isInvisible = true
        val deckId = prefs.ankiDeckId
        val limit = prefs.ankiCardCount
        lifecycleScope.launch {
            val due = async(Dispatchers.IO) { AnkiDroid.getDueCards(applicationContext, deckId, limit) }
            val cards = withTimeoutOrNull(ANKI_TIMEOUT_MS) { due.await() }.orEmpty()
            val content = cards.firstOrNull()?.let { loadCardContent(it) }
            if (content == null) {
                startCountdown()
                return@launch
            }
            ankiCards = cards
            breathing?.cancel()
            binding.pauseLayout.isVisible = false
            binding.ankiLayout.isVisible = true
            showCard(content)
        }
    }

    private suspend fun loadCardContent(card: AnkiDroid.DueCard) =
        withContext(Dispatchers.IO) { AnkiDroid.getCardContent(applicationContext, card) }

    private fun showCard(content: AnkiDroid.CardContent) {
        cardContent = content
        binding.tvAnkiProgress.text = "${ankiIndex + 1} / ${ankiCards.size}"
        binding.tvShowAnswer.isVisible = true
        binding.answerButtons.isInvisible = true
        loadCardPage(content.question, content.css)
        cardShownAt = SystemClock.elapsedRealtime()
    }

    private fun showAnswer() {
        val content = cardContent ?: return
        loadCardPage(content.answer, content.css)
        val times = ankiCards[ankiIndex].nextReviewTimes
        listOf(
            binding.answerAgain to R.string.anki_again,
            binding.answerHard to R.string.anki_hard,
            binding.answerGood to R.string.anki_good,
            binding.answerEasy to R.string.anki_easy,
        ).forEachIndexed { i, (button, label) ->
            button.text = SpannableStringBuilder(getString(label)).apply {
                times.getOrNull(i)?.let { time -> append("\n").inSpans(RelativeSizeSpan(0.75f)) { append(time) } }
            }
        }
        binding.tvShowAnswer.isVisible = false
        binding.answerButtons.isInvisible = false
    }

    // The app opens after the last card; a card that fails to load ends the review early
    private fun answer(ease: Int) {
        binding.answerButtons.isInvisible = true
        val card = ankiCards[ankiIndex]
        val timeTaken = SystemClock.elapsedRealtime() - cardShownAt
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { AnkiDroid.answer(applicationContext, card, ease, timeTaken) }
            ankiIndex++
            val content = ankiCards.getOrNull(ankiIndex)?.let { loadCardContent(it) }
            if (content == null) open() else showCard(content)
        }
    }

    private fun loadCardPage(content: String, css: String) {
        val textColor = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and getColorFromAttr(R.attr.primaryColor))
        val page = AnkiCardHtml.build(content, css, textColor, isDarkThemeOn())
        val view = cardView ?: createCardView().also { cardView = it }
        view.loadDataWithBaseURL(AnkiCardHtml.BASE_URL, page, "text/html", "utf-8", null)
    }

    // JavaScript is needed for KaTeX; the page can reach nothing but the card's media and KaTeX
    @SuppressLint("SetJavaScriptEnabled")
    private fun createCardView(): WebView = WebView(this).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        webViewClient = CardWebViewClient(assets, AnkiDroid.mediaDirectory)
        binding.ankiCardContainer.addView(
            this,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private class CardWebViewClient(
        private val assets: AssetManager,
        private val mediaDirectory: File,
    ) : WebViewClient() {

        // Links in a card don't navigate away from it
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            val url = request.url
            if (url.host != AnkiCardHtml.HOST) return notFound()
            val path = url.path.orEmpty()
            return try {
                if (path.startsWith(AnkiCardHtml.KATEX_PATH)) {
                    val name = path.removePrefix(AnkiCardHtml.KATEX_PATH)
                    if (name.contains("..")) return notFound()
                    WebResourceResponse(mimeType(name), null, assets.open("katex/$name"))
                } else {
                    // Without storage access this throws and the image stays empty
                    val file = AnkiCardHtml.mediaFile(mediaDirectory, path) ?: return notFound()
                    WebResourceResponse(mimeType(file.name), null, FileInputStream(file))
                }
            } catch (_: Exception) {
                notFound()
            }
        }

        private fun mimeType(name: String): String = when (val extension = name.substringAfterLast('.').lowercase()) {
            "js" -> "text/javascript"
            "css" -> "text/css"
            "woff2" -> "font/woff2"
            "svg" -> "image/svg+xml"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }

        private fun notFound() =
            WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private fun appLabel(appPackage: String): String {
        val renamed = prefs.getAppRenameLabel(appPackage)
        if (renamed.isNotBlank()) return renamed
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(appPackage, 0)).toString()
        } catch (_: Exception) {
            appPackage
        }
    }
}
