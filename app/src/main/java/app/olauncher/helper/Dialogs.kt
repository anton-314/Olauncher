package app.olauncher.helper

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogBaseBinding

/**
 * Shows a popup menu hanging off the end edge of this view.
 * [configure] can add or tweak items before the menu is shown.
 */
fun View.showPopupMenu(
    @MenuRes menuRes: Int = 0,
    configure: (Menu) -> Unit = {},
    onItemClick: (MenuItem) -> Unit,
): PopupMenu {
    val popup = PopupMenu(context, this, Gravity.END)
    if (menuRes != 0) popup.menuInflater.inflate(menuRes, popup.menu)
    configure(popup.menu)
    popup.setOnMenuItemClickListener { item ->
        onItemClick(item)
        true
    }
    popup.show()
    return popup
}

/**
 * App dialog: shows without bringing back a hidden status bar, and blurs the
 * screen behind it on Android 12+, fading blur and dialog out together on dismiss.
 */
class OlDialog(context: Context) : AlertDialog(context) {

    private var blur: WindowBlur? = null

    fun showRespectingStatusBar() {
        val window = window
        if (window == null || Prefs(context).showStatusBar) {
            show()
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        blur = window?.let { WindowBlur(it).apply { fadeIn() } }
    }

    override fun dismiss() {
        val blur = blur
        this.blur = null
        if (blur == null || window?.decorView?.isAttachedToWindow != true) return dismissSafely()
        blur.fadeOut { dismissSafely() }
    }

    // The fade ends after a delay; if the activity was destroyed meanwhile the window is
    // already gone, and dismissing it again throws "View not attached to window manager"
    private fun dismissSafely() {
        try {
            super.dismiss()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
}

/**
 * Builds a dialog using the app's own layout: a title row with a close icon,
 * an optional [message] or custom [content], and a text [action] at the end.
 * [content] receives the container so the inflated view keeps its XML margins.
 */
fun Context.createDialog(
    @StringRes title: Int,
    @StringRes action: Int,
    @StringRes message: Int = 0,
    @StringRes neutral: Int = 0,
    onNeutral: () -> Unit = {},
    onAction: () -> Unit = {},
    content: ((ViewGroup) -> View)? = null,
): OlDialog {
    val dialog = OlDialog(this)
    val binding = DialogBaseBinding.inflate(LayoutInflater.from(dialog.context))
    binding.tvTitle.setText(title)
    binding.tvAction.setText(action)
    if (message != 0) {
        binding.tvMessage.setText(message)
        binding.tvMessage.isVisible = true
    }
    if (neutral != 0) {
        binding.tvNeutral.setText(neutral)
        binding.tvNeutral.isVisible = true
    }
    content?.let {
        binding.contentContainer.addView(it(binding.contentContainer))
        binding.contentContainer.isVisible = true
    }
    dialog.setView(binding.root)
    binding.ivClose.setOnClickListener { dialog.dismiss() }
    binding.tvNeutral.setOnClickListener {
        onNeutral()
        dialog.dismiss()
    }
    binding.tvAction.setOnClickListener {
        onAction()
        dialog.dismiss()
    }
    return dialog
}

/** Title with a close icon, a message and a single action. */
fun Context.showMessageDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
): OlDialog {
    val dialog = createDialog(title, action, message = message, onAction = onAction)
    dialog.showRespectingStatusBar()
    return dialog
}

/**
 * Asks to take a breath before opening a distracting app. The open action is
 * enabled only after a countdown of [seconds]; closing the dialog cancels.
 */
fun Context.showBreathingPauseDialog(
    appLabel: String,
    seconds: Int,
    onOpen: () -> Unit,
): OlDialog {
    val dialog = OlDialog(this)
    val binding = DialogBaseBinding.inflate(LayoutInflater.from(dialog.context))
    binding.tvTitle.setText(R.string.breathing_pause)
    binding.tvMessage.text = getString(R.string.breathing_pause_message, appLabel)
    binding.tvMessage.isVisible = true
    binding.tvNeutral.setText(R.string.close)
    binding.tvNeutral.isVisible = true

    val openLabel = getString(R.string.open_app)
    var remaining = seconds
    val countdown = object : Runnable {
        override fun run() {
            if (remaining > 0) {
                binding.tvAction.text = "$openLabel ($remaining)"
                binding.tvAction.isEnabled = false
                binding.tvAction.alpha = 0.5f
                remaining--
                binding.root.postDelayed(this, 1000)
            } else {
                binding.tvAction.text = openLabel
                binding.tvAction.isEnabled = true
                binding.tvAction.alpha = 1f
            }
        }
    }
    countdown.run()
    dialog.setOnDismissListener { binding.root.removeCallbacks(countdown) }

    dialog.setView(binding.root)
    binding.ivClose.setOnClickListener { dialog.dismiss() }
    binding.tvNeutral.setOnClickListener { dialog.dismiss() }
    binding.tvAction.setOnClickListener {
        onOpen()
        dialog.dismiss()
    }
    dialog.showRespectingStatusBar()
    return dialog
}
