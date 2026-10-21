package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.helper.AppSearch
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val appPauseListener: (AppModel) -> Unit,
    private val isAppPaused: (String) -> Boolean,
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
    // The number of apps found, or null without a search
    private val searchResultsListener: (Int?) -> Unit = {},
    // Whether an app's long press menu is open, so that back can close it
    private val menuOpenListener: (Boolean) -> Unit = {},
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1

        // Rebinds only the letter column and the highlight of the search match
        private const val PAYLOAD_SEARCH = "search"

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.identity == newItem.identity

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    private var autoLaunch = true
    private var isBangSearch = false
    var allowAutoLaunch = true
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()

    // The search text the list is filtered by
    private var query: CharSequence = ""
    private var openMenu: ViewHolder? = null

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun getItemViewType(position: Int): Int {
        return when (appFilteredList.getOrNull(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            else -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_PRIVATE_HEADER -> PrivateSpaceHeaderViewHolder(
                AdapterPrivateSpaceHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> ViewHolder(
                AdapterAppDrawerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                this
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (PAYLOAD_SEARCH in payloads && holder is ViewHolder) {
            try {
                val appModel = appFilteredList[holder.bindingAdapterPosition]
                holder.bindSearch(appModel, letterAt(holder.bindingAdapterPosition), query)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            if (appFilteredList.isEmpty() || position == RecyclerView.NO_POSITION) return
            val appModel = appFilteredList[holder.bindingAdapterPosition]
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> {
                    holder.bind(
                        appLabelGravity,
                        privateSpaceToggleListener,
                        privateSpaceSettingsListener,
                    )
                }

                is ViewHolder -> holder.bind(
                    flag,
                    appLabelGravity,
                    myUserHandle,
                    appModel,
                    letterAt(holder.bindingAdapterPosition),
                    query,
                    appClickListener,
                    appDeleteListener,
                    appInfoListener,
                    appHideListener,
                    appPauseListener,
                    isAppPaused,
                    appRenameListener
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolder) holder.closeMenu()
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = charSearch?.startsWith("!") ?: false
                autoLaunch = allowAutoLaunch && (charSearch?.startsWith(" ")?.not() ?: true)

                val appFilteredList = (if (charSearch.isNullOrBlank()) appsList
                else appsList.filter { app ->
                    app !is AppModel.PrivateSpaceHeader && AppSearch.matches(app.appLabel, charSearch)
                } as MutableList<AppModel>)

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.values?.let {
                    val items = it as MutableList<AppModel>
                    appFilteredList = items
                    query = constraint?.toString().orEmpty()
                    submitList(appFilteredList) {
                        notifyItemRangeChanged(0, itemCount, PAYLOAD_SEARCH)
                        searchResultsListener(if (query.isBlank()) null else appFilteredList.size)
                        autoLaunch()
                    }
                }
            }
        }
    }

    private fun autoLaunch() {
        try {
            if (itemCount == 1
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
                && appFilteredList.isNotEmpty()
                && appFilteredList[0] !is AppModel.PrivateSpaceHeader
            ) appClickListener(appFilteredList[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // The first letter shows only at the first app starting with it, and not while searching
    private fun letterAt(position: Int): String {
        if (query.isNotBlank()) return ""
        val letter = appFilteredList.getOrNull(position)?.let { letterOf(it) }.orEmpty()
        val previous = appFilteredList.getOrNull(position - 1)?.let { letterOf(it) }
        return if (letter == previous) "" else letter
    }

    // Without its accent, as the sorting puts "Ä" with the "A"s
    private fun letterOf(appModel: AppModel): String {
        if (appModel is AppModel.PrivateSpaceHeader || appModel.appLabel.isEmpty()) return ""
        val first = appModel.appLabel.codePointAt(0)
        if (!Character.isLetterOrDigit(first)) return ""
        return Normalizer.normalize(String(Character.toChars(first)), Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .uppercase()
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        // Add empty app for bottom padding in recyclerview and assign to list
        appsList.add(
            AppModel.App(
                appLabel = "",
                key = null,
                appPackage = "",
                activityClassName = "",
                isNew = false,
                user = android.os.Process.myUserHandle()
            )
        )
        this.appsList = appsList
        this.appFilteredList = appsList
        // Apps that stay in the list keep their view, but the app before them may have changed
        submitList(appsList) { notifyItemRangeChanged(0, itemCount, PAYLOAD_SEARCH) }
    }

    fun launchFirstInList() {
        val first = appFilteredList.firstOrNull { it !is AppModel.PrivateSpaceHeader }
        if (first != null) appClickListener(first)
    }

    /** Closes the open long press menu, if there is one. */
    fun closeMenu(): Boolean {
        val holder = openMenu ?: return false
        holder.closeMenu()
        return true
    }

    private fun onMenuOpened(holder: ViewHolder) {
        if (openMenu !== holder) openMenu?.closeMenu()
        openMenu = holder
        menuOpenListener(true)
    }

    private fun onMenuClosed(holder: ViewHolder) {
        if (openMenu !== holder) return
        openMenu = null
        menuOpenListener(false)
    }

    class PrivateSpaceHeaderViewHolder(private val binding: AdapterPrivateSpaceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            toggleListener: () -> Unit,
            settingsListener: () -> Unit,
        ) = with(binding) {
            privateSpaceTitle.gravity = appLabelGravity
            privateSpaceTitle.setOnClickListener { toggleListener() }
            privateSpaceTitle.setOnLongClickListener {
                settingsListener()
                true
            }
        }
    }

    class ViewHolder(
        private val binding: AdapterAppDrawerBinding,
        private val adapter: AppDrawerAdapter,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val matchColor = binding.root.context.getColorFromAttr(R.attr.primaryColor)
        private val restColor = binding.root.context.getColorFromAttr(R.attr.textSecondaryColor)

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            letter: String,
            query: CharSequence,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appPauseListener: (AppModel) -> Unit,
            isAppPaused: (String) -> Boolean,
            appRenameListener: (AppModel, String) -> Unit,
        ) = with(binding) {
            // A recycled view may still show the menu of the app it showed before
            closeMenu()

            bindSearch(appModel, letter, query)
            appTitle.gravity = appLabelGravity
            appMenuTitle.text = appModel.appLabel
            otherProfileIndicator.isVisible = appModel.user != myUserHandle

            // While a menu is open, a tap on another app only closes it
            appRow.setOnClickListener {
                if (!adapter.closeMenu()) clickListener(appModel)
            }

            appRow.setOnLongClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    appDelete.alpha = when (
                        appModel is AppModel.PinnedShortcut || !root.context.isSystemApp(appModel.appPackage, appModel.user)
                    ) {
                        true -> 1.0f
                        false -> 0.5f
                    }
                    appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS)
                        root.context.getString(R.string.adapter_show)
                    else
                        root.context.getString(R.string.adapter_hide)
                    appHide.alpha = when (appModel is AppModel.PinnedShortcut) {
                        true -> 0.5f
                        false -> 1.0f
                    }
                    appPause.text = if (isAppPaused(appModel.appPackage))
                        root.context.getString(R.string.pause) + " ✓"
                    else
                        root.context.getString(R.string.pause)
                    // Only allow renaming non hidden apps
                    appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                    openMenu(appHideLayout)
                }
                true
            }

            // Configure rename behavior
            appRename.setOnClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    openMenu(renameLayout)
                    etAppRename.showKeyboard()
                    etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }
            etAppRename.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    etAppRename.hint = ""
                }
            })
            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode == EditorInfo.IME_ACTION_DONE) {
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        closeMenu()
                    }
                    true
                }
                false
            }
            tvSaveRename.setOnClickListener {
                etAppRename.hideKeyboard()
                val renameLabel = etAppRename.text.toString().trim()
                if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                    appRenameListener(appModel, renameLabel)
                } else {
                    // Saving an empty name clears the rename. Storing getAppName() instead would
                    // freeze the label and give a pinned shortcut its app's name.
                    appRenameListener(appModel, "")
                }
                closeMenu()
            }
            appInfo.setOnClickListener { appInfoListener(appModel) }
            appDelete.setOnClickListener { appDeleteListener(appModel) }
            appRenameClose.setOnClickListener {
                etAppRename.hideKeyboard()
                closeMenu()
            }
            appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
            appPause.setOnClickListener {
                appPauseListener(appModel)
                closeMenu()
            }
        }

        // The part of the name that matched the search stands out, the rest steps back
        fun bindSearch(appModel: AppModel, letter: String, query: CharSequence) = with(binding) {
            appLetter.text = letter
            val label = buildString {
                append(appModel.appLabel)
                if (appModel.isNew) append(" ✦")
            }
            val match = AppSearch.matchRange(appModel.appLabel, query)
            appTitle.text = if (match == null) label else SpannableString(label).apply {
                setSpan(ForegroundColorSpan(restColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(matchColor), match.first, match.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // The menu takes the place of the app's row
        private fun openMenu(menu: View) = with(binding) {
            appRow.visibility = View.INVISIBLE
            appHideLayout.isVisible = menu == appHideLayout
            renameLayout.isVisible = menu == renameLayout
            adapter.onMenuOpened(this@ViewHolder)
        }

        fun closeMenu() = with(binding) {
            appHideLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            appRow.visibility = View.VISIBLE
            adapter.onMenuClosed(this@ViewHolder)
        }

        private fun getAppName(context: Context, appPackage: String, user: UserHandle): String {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            return try {
                val activityList = launcherApps.getActivityList(appPackage, user)
                if (activityList.isNotEmpty()) {
                    activityList.first().label.toString()
                } else {
                    val packageManager = context.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(appPackage, 0)
                    ).toString()
                }
            } catch (_: Exception) {
                "" // As a fallback, display an empty string.
            }
        }
    }
}
