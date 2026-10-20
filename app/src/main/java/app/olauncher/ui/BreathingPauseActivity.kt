package app.olauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityBreathingPauseBinding
import app.olauncher.helper.hideStatusBar
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.startActivitySafely

/**
 * Full screen breathing pause before a paused app. "Open" unlocks after a countdown.
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
    }

    private lateinit var prefs: Prefs
    private lateinit var binding: ActivityBreathingPauseBinding
    private var appPackage = ""
    private var fromLauncher = false
    private var remaining = Constants.BREATHING_PAUSE_SECONDS
    private var breathing: ValueAnimator? = null

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
        binding.tvOpen.setOnClickListener { open() }
        onBackPressedDispatcher.addCallback(this) { close() }

        countdown.run()
        if (!isEinkDisplay() && !isSystemAnimationsDisabled()) startBreathing()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.root.removeCallbacks(countdown)
        breathing?.cancel()
        super.onDestroy()
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
