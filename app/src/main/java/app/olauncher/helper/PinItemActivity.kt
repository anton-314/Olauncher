package app.olauncher.helper

import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R

class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setBackgroundDrawable(null)

        // This activity is exported, so any app can start it with any intent. Pin requests exist only
        // on Android 8+, and a crafted extra fails to unparcel; neither may crash the launcher process.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }
        val pinItemRequest = try {
            getSystemService(LauncherApps::class.java).getPinItemRequest(intent)?.takeIf { it.isValid }
        } catch (e: Exception) {
            null
        }

        when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> showToast(R.string.invalid_pin_request)
        }

        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest)

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                showToast(R.string.widgets_not_supported)

            else -> showToast(R.string.pin_action_not_supported)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = runCatching { pinItemRequest.shortcutInfo }.getOrNull()
        if (shortcutInfo != null) {
            val success = runCatching { pinItemRequest.accept() }.getOrDefault(false)
            val message = when (success) {
                true -> R.string.shortcut_pinned
                false -> R.string.shortcut_pin_failed
            }
            showToast(message)
        } else {
            showToast(R.string.invalid_shortcut)
        }
    }
}
