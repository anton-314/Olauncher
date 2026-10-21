# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Olauncher is a minimal, ad-free Android home-screen launcher (`app.olauncher`), written in Kotlin with a single `app` module. Licensed GPLv3. This repo is a personal fork of upstream Olauncher (tanujnotes), tailored to one user; it is not published to any store. Upstream's promotional parts (Pro/rate/share prompts, GitHub/X/privacy links, ads for other apps, store listing) were removed and should not come back. The project values simplicity: niche features exist but stay hidden. Keep new features small and keep dependencies to a minimum. The only libraries used are AndroidX core/appcompat/recyclerview/lifecycle/navigation/work and Material. KaTeX is bundled as an asset (`assets/katex`) for Anki cards. The fonts Geist and IBM Plex Mono are bundled in `res/font` (SIL OFL, license texts in `assets/licenses`).

## Commands

```bash
./gradlew assembleDebug            # debug APK (applicationId suffix .debug, so it installs alongside release)
./gradlew installDebug             # install on connected device/emulator
./gradlew assembleRelease          # minified release build (R8 + proguard-rules.pro)
./gradlew lint                     # Android lint
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew testDebugUnitTest --tests "app.olauncher.data.ShortcutIdentityTest"   # single test class
```

- Toolchain: Java 17, compileSdk/targetSdk 36, minSdk 24.
- Dependency versions are kept in `gradle/libs.versions.toml` (version catalog, referenced as `libs.*`).
- There are no instrumented tests. The unit tests can only cover pure-Kotlin code, because most logic calls Android framework APIs.
- On a release, bump `versionCode` and `versionName` in `app/build.gradle`.

## Architecture

**Single activity, single shared ViewModel.** `MainActivity` hosts a Navigation component graph (`res/navigation/nav_graph.xml`) with three fragments: `HomeFragment` (the start destination), `AppDrawerFragment` and `SettingsFragment`. All of them share one activity-scoped `MainViewModel`. The fragments obtain it with `ViewModelProvider(activity)` or `activityViewModels()`. The ViewModel communicates with the UI through `MutableLiveData`. For one-shot events (dialogs, launcher reset, messages) it uses `SingleLiveEvent`. `MainActivity` observes `showDialog` and shows the dialog that matches the `Constants.Dialog.*` key.

**App drawer is reused as a picker.** `AppDrawerFragment` receives a `Constants.Key.FLAG` argument:
- `FLAG_LAUNCH_APP` and `FLAG_HIDDEN_APPS` launch the chosen app.
- `FLAG_SET_HOME_APP_1..8`, `FLAG_SET_SWIPE_LEFT/RIGHT_APP`, `FLAG_SET_CLOCK/CALENDAR/SCREEN_TIME_APP` assign the chosen app to a slot.

Whichever flag is passed, the selection goes to `MainViewModel.selectedApp(appModel, flag)`, which dispatches on the flag. A new kind of "pick an app for X" feature should follow this same pattern.

**Persistence is a single SharedPreferences wrapper.** `data/Prefs.kt` (file `app.olauncher`) holds every setting. Home slots 1–8 are stored as separate, explicitly numbered keys, one key per field per slot: name, package, activity class name, user handle string, isShortcut and shortcutId. The `getAppName(location)`-style accessors map a slot index to the matching key. A new per-slot field therefore has to be added to all 8 slots and to the location-based getters. Swipe-left/right, clock, calendar and screen-time apps use dedicated keys. A user's rename of an app label is stored under a key equal to the app's package name.

**App model.** `data/AppModel.kt` is a sealed class with three subclasses:
- `App`: a launchable activity.
- `PinnedShortcut`: a launcher shortcut, launched through `LauncherApps.startShortcut`. Its identity comes from `shortcutIdentity()`, a length-prefixed key built from package + shortcutId + user.
- `PrivateSpaceHeader`: a header row in the drawer for Android 15+ Private Space.

Apps are listed across every `UserManager.userProfiles` (work profiles included). The Private Space profile is excluded from that list and loaded separately. App lists are built in `helper/Utils.kt` (`getAppsList`, `getPrivateSpaceApps`) on `Dispatchers.IO`. Sorting uses `Collator` keys.

**Helpers and system integrations:**
- `helper/Utils.kt` and `helper/Extensions.kt` hold most of the platform logic: app lists, wallpapers, default-launcher checks, dark theme, and similar.
- `FakeHomeActivity` is disabled in the manifest. It is enabled briefly so that Android shows the default-launcher chooser again (a "reset launcher" trick).
- Double-tap-to-lock uses `MyAccessibilityService`, which runs in the separate `:serviceProcess`, or falls back to `listener/DeviceAdmin`.
- Breathing pause: packages in `Prefs.pausedApps` (toggled from the drawer's long-press menu) open only after `ui/BreathingPauseActivity`, a full screen countdown. Launches from the launcher go through it via `MainViewModel.selectedApp`. `MyAccessibilityService` also watches window state changes of all apps and shows it when a paused app comes to the front another way (recents, notifications). It reads prefs with `Prefs.fromServiceProcess` and skips apps the launcher just let through (`grantBreathingPause`).
- Anki in the breathing pause: with a deck chosen in settings (`Prefs.ankiDeckId`), the pause reviews up to `ankiCardCount` due cards before the app opens, and falls back to the countdown when none are due. `helper/AnkiDroid.kt` talks to AnkiDroid's content provider directly (no AnkiDroid library). Cards render in a WebView. `helper/AnkiCardHtml.kt` builds the page, and the WebView client serves only KaTeX (bundled in `assets/katex`, MIT) and media files from `/storage/emulated/0/AnkiDroid/collection.media`. Every other host is blocked. The provider cannot hand out media, so showing images needs all files access.
- `PinItemActivity` handles `CONFIRM_PIN_SHORTCUT` requests coming from other apps.
- `helper/WallpaperWorker` is a WorkManager periodic job. It downloads the daily wallpaper from the gist at `Constants.URL_WALLPAPERS`, and the `INTERNET` permission exists only for this.
- `helper/usageStats/` computes the screen-time figure on the home screen from `UsageStatsManager` events. `MainViewModel.getTodaysScreenTime` runs this computation off the main thread.
- Gestures are handled by `listener/OnSwipeTouchListener` and `ViewSwipeTouchListener`.

## Look

- Dark is white on black, light is the exact inverse; there is no accent color. Colors are theme attributes (`res/values/attrs.xml`): `AppTheme` in `values/styles.xml` sets the light values, the one in `values-night/styles.xml` the dark values, and both inherit everything else from `Base.AppTheme`. Use `?attr/…` in layouts and `getColorFromAttr` in code, never fixed colors.
- Geist (`?attr/mainFontFamily`, Medium with the bold font option) for names, titles and body text. IBM Plex Mono in capitals (`TextMono`, `TextMonoLabel`, `TextMonoValue`, `TextMonoSmall`) for data, values, labels and actions. No text shadows; the home screen gets `home_scrim` behind it instead.
- Settings rows use the `SettingsSection` / `SettingsGroup` / `SettingsRow` styles; the whole row is the tap target, and on/off options show a `ui/SettingSwitch`.
- Sizes live in `values/dimens.xml` only, not per density. `values-land` widens the app list and settings for tablets.
- In styles, use `paddingStart`/`paddingEnd` etc.: the `…Horizontal`/`…Vertical` attributes need API 26 there.

## Localization

Strings live in `res/values-*/strings.xml` (English default, German `de`, Japanese `ja`). Adding a locale takes all three of these changes, or it won't appear:
1. a new `values-<locale>` folder;
2. an entry in `localeFilters` in `app/build.gradle`;
3. an entry in `res/xml/locales_config.xml`, which uses BCP-47 form, e.g. `pt-BR` rather than `pt-rBR`.

When you add or change a string in `values/strings.xml`, keep the translations in sync.
