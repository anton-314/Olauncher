package app.olauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.ui.BreathingPauseActivity

class MyAccessibilityService : AccessibilityService() {

    // Package of the activity in front, to notice when a paused app comes to the front
    private var frontPackage: String? = null
    private val isActivityCache = HashMap<String, Boolean>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    // No prefs are written here: this service runs in its own process, and SharedPreferences are
    // not multi-process safe, so the launcher process would overwrite the value with its cached copy.
    // Lock mode is switched on in settings, before the user is sent to enable this service.

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowStateChanged(event)
                AccessibilityEvent.TYPE_VIEW_CLICKED ->
                    if (event.packageName == packageName) onLauncherViewClicked(event)

                else -> {}
            }
        } catch (e: Exception) {
            return
        }
    }

    private fun onLauncherViewClicked(event: AccessibilityEvent) {
        val source: AccessibilityNodeInfo = event.source ?: return
        if (source.className != "android.widget.FrameLayout") return

        when (source.contentDescription) {
            getString(R.string.lock_layout_description) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
            // Home button for recents feature disabled
            // getString(R.string.recents_layout_description) -> {
            //     performGlobalAction(GLOBAL_ACTION_RECENTS)
            // }
        }
    }

    // Shows the breathing pause over a paused app that came to the front other than through the
    // launcher, e.g. from recents or a notification. Launches from the launcher already went
    // through the pause, which grants the app for a few seconds.
    private fun onWindowStateChanged(event: AccessibilityEvent) {
        val appPackage = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        // Dialogs, the keyboard and the notification shade change the window state too;
        // only an activity means that another app came to the front
        if (appPackage == frontPackage || !isActivity(appPackage, className)) return
        frontPackage = appPackage
        if (appPackage == packageName) return

        val prefs = Prefs.fromServiceProcess(this)
        if (appPackage !in prefs.pausedApps || prefs.isBreathingPauseGranted(appPackage)) return
        startActivity(
            Intent(this, BreathingPauseActivity::class.java)
                .putExtra(BreathingPauseActivity.EXTRA_PACKAGE, appPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    private fun isActivity(appPackage: String, className: String): Boolean =
        isActivityCache.getOrPut("$appPackage/$className") {
            try {
                val component = ComponentName(appPackage, className)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
                else
                    @Suppress("DEPRECATION")
                    packageManager.getActivityInfo(component, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

    override fun onInterrupt() {

    }
}
