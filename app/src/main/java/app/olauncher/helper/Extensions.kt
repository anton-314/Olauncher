package app.olauncher.helper

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.navigation.NavController
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import java.util.Calendar
import java.util.Locale

fun Window.showStatusBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        insetsController?.show(WindowInsets.Type.statusBars())
    else
        @Suppress("DEPRECATION", "InlinedApi")
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
}

fun Window.hideStatusBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        insetsController?.hide(WindowInsets.Type.statusBars())
    else
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
}

fun View.hideKeyboard() {
    this.clearFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun View.showKeyboard(show: Boolean = true) {
    if (show.not()) return
    if (this.requestFocus())
        postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }, 100)
}


@RequiresApi(Build.VERSION_CODES.Q)
fun Activity.showLauncherSelector(requestCode: Int) {
    val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
    if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            resetDefaultLauncher()
        }
    } else
        resetDefaultLauncher()
}

// The home intent is implicit on purpose: it has to show the system's launcher chooser
@SuppressLint("UnsafeImplicitIntentLaunch")
fun Context.resetDefaultLauncher() {
    val componentName = ComponentName(this, FakeHomeActivity::class.java)
    try {
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        val selector = Intent(Intent.ACTION_MAIN)
        selector.addCategory(Intent.CATEGORY_HOME)
        startActivity(selector)
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        // Never leave the fake home screen enabled, or it stays a second launcher choice
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun Context.isDefaultLauncher(): Boolean {
    val launcherPackageName = getDefaultLauncherPackage(this)
    return BuildConfig.APPLICATION_ID == launcherPackageName
}

fun Context.resetLauncherViaFakeActivity() {
    resetDefaultLauncher()
    if (getDefaultLauncherPackage(this).contains("."))
        startActivitySafely(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}

fun Context.openSearch(query: String? = null) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, query ?: "")
    if (!startActivitySafely(intent)) showToast(R.string.unable_to_open_app)
}

/**
 * Starts [intent], returning false instead of crashing when no app handles it. Settings screens
 * and web search are missing on some devices (TVs, de-googled ROMs, work-managed phones).
 */
fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: Exception) {
    e.printStackTrace()
    false
}

/** Navigation calls from stale clicks or late callbacks must only act on the screen they belong to. */
fun NavController.isAt(@IdRes destinationId: Int): Boolean = currentDestination?.id == destinationId

private var isEinkDevice: Boolean? = null

fun Context.isEinkDisplay(): Boolean {
    isEinkDevice?.let { return it }
    return (hasEinkRefreshRate() || isOnyxDevice() || isKnownEinkModel())
        .also { isEinkDevice = it }
}

private fun Context.hasEinkRefreshRate(): Boolean {
    return try {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Check max supported refresh rate, not the current one, because adaptive
        // refresh rate displays can drop to 30Hz or lower without being e-ink
        windowManager.defaultDisplay.supportedModes.maxOf { it.refreshRate } <= Constants.MIN_ANIM_REFRESH_RATE
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// Boox devices report 60Hz+ refresh rates, so the refresh rate check misses them
private fun isOnyxDevice(): Boolean {
    if (Build.MANUFACTURER.equals("ONYX", ignoreCase = true)) return true
    return try {
        // Onyx firmware ships its e-ink SDK classes in the boot classpath
        Class.forName("android.onyx.ViewUpdateHelper")
        true
    } catch (ignored: Throwable) {
        false
    }
}

private fun isKnownEinkModel(): Boolean {
    val brand = Build.BRAND.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val einkOnlyBrands = listOf("onyx", "boox", "dasung", "bigme", "boyue", "meebook", "mudita")
    if (einkOnlyBrands.any { brand.contains(it) || manufacturer.contains(it) }) return true
    // Hisense also sells LCD phones, so match only their e-ink line
    if (brand.contains("hisense") || manufacturer.contains("hisense"))
        return Regex("\\bA[579]\\b|TOUCH|HI READER").containsMatchIn(Build.MODEL.uppercase())
    return false
}

fun Context.isSystemAnimationsDisabled(): Boolean {
    return try {
        Settings.Global.getFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f) == 0f
                || Settings.Global.getFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
                || Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.isPackageInstalled(packageName: String, userHandle: UserHandle = android.os.Process.myUserHandle()): Boolean {
    val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, userHandle)
    return activityInfo.isNotEmpty()
}

fun Context.isCountryIn(): Boolean {
    val country = runCatching {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephonyManager?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: telephonyManager?.networkCountryIso?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: Locale.getDefault().country
    return country.equals("IN", ignoreCase = true)
}

@RequiresApi(Build.VERSION_CODES.Q)
fun Context.appUsagePermissionGranted(): Boolean {
    val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOpsManager.unsafeCheckOpNoThrow(
        "android:get_usage_stats",
        android.os.Process.myUid(),
        packageName
    ) == AppOpsManager.MODE_ALLOWED
}

fun Context.formattedTimeSpent(timeSpent: Long): String {
    val seconds = timeSpent / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        timeSpent == 0L -> "0m"

        hours > 0 -> getString(
            R.string.time_spent_hour,
            hours.toString(),
            remainingMinutes.toString()
        )

        minutes > 0 -> {
            getString(R.string.time_spent_min, minutes.toString())
        }

        else -> "<1m"
    }
}

fun Long.convertEpochToMidnight(): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

// Rounded, not truncated: across a daylight saving change two midnights are 23 or 25 hours apart
fun Long.isDaySince(now: Long = System.currentTimeMillis()): Int =
    Math.round((now.convertEpochToMidnight() - this.convertEpochToMidnight()).toDouble()
            / Constants.ONE_DAY_IN_MILLIS).toInt()

fun Long.hasBeenHours(hours: Int): Boolean =
    ((System.currentTimeMillis() - this) / Constants.ONE_HOUR_IN_MILLIS) >= hours

fun Long.hasBeenMinutes(minutes: Int): Boolean =
    ((System.currentTimeMillis() - this) / Constants.ONE_MINUTE_IN_MILLIS) >= minutes

fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}

// Until an app is picked, the swipes open the camera and the dialer
fun Context.swipeLeftLabel(prefs: Prefs): String =
    if (prefs.appPackageSwipeLeft.isEmpty()) getString(R.string.camera) else prefs.appNameSwipeLeft

fun Context.swipeRightLabel(prefs: Prefs): String =
    if (prefs.appPackageSwipeRight.isEmpty()) getString(R.string.phone) else prefs.appNameSwipeRight
