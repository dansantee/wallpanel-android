# Changelog

Changes in this fork relative to the original WallPanel project as archived, which is
preserved on the tag `upstream-archived-v0.12.0`.

```bash
git diff upstream-archived-v0.12.0..master
```

Every entry was verified on a Samsung Galaxy A21 running Android 12, not only by a successful build.

## 2026-09-01

Merged to `master` via PR #1 (`44d86bf`). Upstream is abandoned, so `master` is this fork's own line
of development rather than a pristine mirror.

### Fixed — it now builds from a clean clone

**Firebase removed** (`bca33a1`). Upstream applies the `com.google.gms.google-services` and
`firebase-crashlytics` Gradle plugins, but `google-services.json` is not in the repository, so
configuration fails immediately. Rather than stand up a Firebase project for a personal sideload
build, Firebase was removed outright: both buildscript classpaths and plugin ids, the
`firebase-core` / `firebase-bom` / `firebase-analytics` / `firebase-crashlytics-ktx` dependencies,
and `CrashlyticsDebugTree` (which existed solely to feed `FirebaseCrashlytics`). Logging falls back
to the already-present, Firebase-free `CrashlyticsTree`.

**`local.properties` made optional** (`b7d04f2`). The `dev` flavor called `properties.load()` on it
unconditionally. Gradle evaluates *every* product flavor block regardless of which flavor is being
assembled, so a clone without the file could not configure. Five near-identical accessor functions
were replaced with one `localProperty(key, fallback)` helper. Verified by building with the file
renamed away.

### Fixed — bugs specific to sideloaded builds

**Settings were wiped on every launch** (`12bbc6c`). `BrowserActivityNative.onCreate` seeded the
developer's own broker and dashboard into settings behind `if (BuildConfig.DEBUG)`. That guard
selects build *type*, not product *flavor*. The `prod` flavor hardcodes `BROKER`,
`BROKER_USERNAME`, `BROKER_PASS` and `HASS_URL` to empty strings — so a `prodDebug` build, exactly
what you get when sideloading, ran the seeding with prod's empty values and blanked the user's real
launch URL and MQTT credentials on every start. The visible symptom was the app silently reverting
to the default `wallpanel.xyz` page after any restart, because `Configuration.getStringPref` falls
back to the default when the stored value is empty. Now guarded on `BuildConfig.BASE_ENVIRONMENT`,
the flavor marker the build file already defines.

**Opening settings bounced to the lock screen** (`2d4357b`). `SettingsActivity` had no keyguard
flags in code or manifest, while `BaseBrowserActivity` sets `FLAG_DISMISS_KEYGUARD`,
`FLAG_SHOW_WHEN_LOCKED` and `FLAG_TURN_SCREEN_ON`. Launching settings therefore stopped occluding
the keyguard and the lock screen surfaced, with the settings screen already open behind it. The
app's own settings PIN was never involved. `SettingsActivity` now sets the same flags.

**Back exited to the lock screen** (`2d4357b`). There was no back handling anywhere in the browser
activity — no `onBackPressed`, no `canGoBack()` — so Back fell through to the default `finish()`.
With WallPanel set as the device launcher, finishing the home activity leaves the system nowhere to
go. Back now walks WebView history if there is any and otherwise reloads the dashboard; it can no
longer finish the activity.

**Navigation bar flashed on wake** (`67c5c03`, `f2feae1`). Immersive mode was applied in exactly one
place, `onWindowFocusChanged`, using the deprecated `SYSTEM_UI_FLAG_*` API. Reworked onto
`WindowInsetsController` on API 30+ (legacy flags retained below), applied from `onCreate` and
`onResume` as well, with an insets listener and a short burst of re-hides after wake.

This measurably sped up the wake path but did **not** eliminate the flash. On Android 11+
`IMMERSIVE_STICKY` becomes `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, under which the system shows
the bars transiently in response to touch input — and the wake gesture is touch input. An app
cannot suppress that. The real fix is device-side: remove the navigation bar (see README).

### Changed — build cleanup

**LeakCanary dropped from debug builds** (`07c51c2`). It shipped to the panel via
`debugImplementation`, posting "found N retained objects" notifications, adding its own launcher
icon and periodically forcing GC. Confirmed removed: LeakCanary components in `dumpsys package`
went from 17 to 0. The release-only LeakCanary artifacts remain and are inert.

**Dead build configuration removed** (`b7d04f2`):

- `kapt "dagger-compiler"` was declared twice in the same dependency block
- `testImplementation 'junit:junit:4.+'` pinned to `4.13.2`; a dynamic version means the same
  commit can resolve differently on a later build
- `logging-interceptor` raised `3.4.1` → `3.12.13` to match `okhttp`; same library family, nine
  minor versions apart
- **Picasso removed** — declared as a dependency with zero references in source; Glide is the image
  loader actually in use
- `force "com.android.support:appcompat-v7:28.0.0"` removed, along with the commented-out
  `resolutionStrategy` block beneath it. It pinned a legacy support artifact while `useAndroidX`
  and `enableJetifier` are both enabled
- six unused `versions.*` variables removed (`support`, `navigation`, `constraint_layout`,
  `stetho`, `archVersion`, `archRoomVersion`)
- the `buildToolsVersion` pin removed; AGP selects its own

Net −43 lines, no functional change, verified with a clean build plus on-device run.

## Attempted and reverted

**Core library desugaring is not a drop-in replacement for `android-retrofix` at AGP 7.3.**

Replacing the third-party `android-retrofix` bytecode-rewriting plugin and its
`android-retrostreams` / `android-retrofuture` backports with Google's first-party
`coreLibraryDesugaringEnabled` looks like a clean win with no `minSdkVersion` change. **It builds
successfully, which is the trap.** Inspecting the resulting APK's dex:

| symbol | result |
|---|---|
| `j$/util/stream/Stream` | desugared |
| `j$/util/function/Function` | desugared |
| `j$/util/concurrent/CompletableFuture` | **absent** |
| `Ljava/util/concurrent/CompletableFuture;` | **still referenced, un-rewritten** |

`CompletableFuture` is API 24+ and the HiveMQ MQTT client uses it, so the app would throw
`NoClassDefFoundError` on API 19–23 at runtime — invisible on a modern test device.
(`java.util.concurrent.Flow` is also referenced, and that is API 28+.)

`desugar_jdk_libs` only covers `CompletableFuture` from **2.x**, which requires **AGP 7.4+** and in
turn a Gradle upgrade. Note that the 1.2.3 artifact *contains* the `CompletableFuture` class — that
is misleading, because what matters is D8's conversion spec, which does not rewrite references to
it. Do not judge coverage by unzipping the artifact; check the dex.

Revisit only as part of an AGP/Gradle upgrade.
