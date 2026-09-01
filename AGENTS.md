# AGENTS.md

Operating notes for this repository. Read before building, changing or installing this app — most
of what follows was learned the hard way and is not discoverable from the code.

## What this is

A personal fork of the WallPanel Android app, kept alive after the original project was archived on
2025-05-05. It drives one wall-mounted phone showing a Home Assistant dashboard. `master` is this
fork's own line of development, not a mirror of upstream.

The upstream code as archived is on the tag `upstream-archived-v0.12.0`. Use it as the diff base:

```bash
git diff upstream-archived-v0.12.0..master
```

## Toolchain — two JDKs, deliberately

| | |
|---|---|
| **Gradle build** | JDK **11**. AGP 7.3 / Gradle 7.4 / Kotlin 1.6.21 do not support newer JDKs. |
| **`sdkmanager`** | JDK **17**. Android cmdline-tools 19.0 refuses to start on 11: `Java version 17 or higher is required.` |

Set `JAVA_HOME` to **11** for builds. Point it at 17 only for SDK management. Do not "simplify" by
making 17 the global default — the build breaks.

SDK requirements: platform **33**, build-tools **30.0.3**, platform-tools. Android Studio is not
needed.

```bash
export JAVA_HOME=/path/to/jdk-11
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleProdDebug
```

Roughly 4 minutes cold, seconds warm. A clean build is ~6 minutes and can exceed a 10-minute
command timeout on a cold cache — run it in the background rather than assuming it hung.

`local.properties` is **optional** here (it is required upstream). Only needed for `sdk.dir` or
`dev` flavor values. If you write one, use forward slashes: Java `.properties` treats backslash as
an escape, and a shell heredoc will mangle the doubled form into a silently wrong path.

## Build variants — the trap that caused a real bug

Product flavors are `dev` / `qa` / `prod`; build types are `debug` / `release`. We sideload
**`prodDebug`**.

**`BuildConfig.DEBUG` is not a flavor check.** A `prodDebug` build has `DEBUG == true` *and* the
`prod` flavor's hardcoded empty `BROKER` / `HASS_URL` / `BROKER_USERNAME` / `BROKER_PASS`. Code
guarded on `BuildConfig.DEBUG` that writes those into settings therefore wipes the user's real
configuration on every launch. That was a live bug; the guard is now
`BuildConfig.BASE_ENVIRONMENT == "DEV_ENVIRONMENT"`.

When adding developer conveniences, guard on `BASE_ENVIRONMENT`, never on `DEBUG` alone.

Note also that `Configuration.getStringPref` returns the **default** when a stored value is empty,
so blanking a preference silently reverts to a default rather than showing an empty field.

## Working with the device over adb

Debug builds are debuggable, so app-private data is reachable without root — a release build is
not. Preferences round-trip:

```bash
adb shell "run-as xyz.wallpanel.app cat shared_prefs/xyz.wallpanel.app_preferences.xml" > prefs.xml
# edit, then:
adb shell am force-stop xyz.wallpanel.app
cat prefs.xml | adb shell "run-as xyz.wallpanel.app sh -c 'cat > shared_prefs/xyz.wallpanel.app_preferences.xml'"
```

**Force-stop first** or the running app's in-memory copy overwrites the file. The dashboard URL
lives in **two** keys: `setting_app_launchurl` (what loads) and `pref_settings_dashboard_url` (the
settings field). `&` must be XML-escaped as `&amp;`.

### Probes that actually tell you something

```bash
adb shell dumpsys window | grep -E "mLastBehavior|isKeyguardShowing|mKeyguardOccluded"
adb shell dumpsys window windows      # find the BrowserActivityNative block -> "Requested visibility:"
adb shell dumpsys package xyz.wallpanel.app | grep -i leak
adb shell wm density ; adb shell settings get secure navigation_mode
```

`mAppBounds` is **not** a usable immersive-mode signal — it is configuration-level and does not
change when the bars hide.

### Things that look like tests but are not

- `input keyevent KEYCODE_SLEEP` / `KEYCODE_WAKEUP` does **not** reproduce transient-system-bar
  behaviour. That path needs a real touchscreen gesture. Do not conclude "not reproducible" from
  adb-driven wake alone.
- While the screen is dozing, `mResumedActivity` is empty by definition. Use `ResumedActivity:` or
  check `dumpsys power | grep mWakefulness` before reading it as a crash.
- A green build proves nothing about desugaring or about runtime behaviour on older API levels.
  Verify by inspecting the APK's dex for the symbols you expect.

## Verifying a change

Build success is the floor, not the bar. For anything behavioural, install and confirm on device:

```bash
adb install -r WallPanelApp/build/outputs/apk/prod/debug/WallPanelApp-prod-arm64-v8a-debug.apk
adb logcat -d -t 300 | grep -iE "FATAL|AndroidRuntime|ClassNotFound|pageLoadComplete"
```

To confirm *which* build is actually installed, pull it off the device and fingerprint the dex
rather than trusting what you think you installed:

```bash
adb pull "$(adb shell pm path xyz.wallpanel.app | sed 's/package://' | tr -d '\r')" installed.apk
```

Note `build/outputs` can hold a stale APK from a build whose source was later reverted. Rebuild
before publishing or installing an artifact.

## Device-side configuration is not in this repo

The panel depends on device settings that no APK carries, and a factory reset or replacement device
loses them. They are documented in the README: launcher mode, navigation-bar removal, lock screen
off, and a density override. If panel behaviour looks wrong after a device change, check those
before suspecting the app.

## Known dead ends

**Do not swap `android-retrofix` for core library desugaring at AGP 7.3.** It builds cleanly but
leaves `CompletableFuture` un-rewritten, which the HiveMQ MQTT client needs, so the app would fail
at runtime on API 19–23. `desugar_jdk_libs` covers it only from 2.x, requiring AGP 7.4+. Details
and the dex evidence are in CHANGELOG.md.

## Conventions

- Match the surrounding code: Kotlin, 4-space indent, `Timber` for logging, Dagger for injection.
- Comment *why*, especially where a fix compensates for platform behaviour — several fixes here
  look arbitrary without the reason.
- Keep upstream attribution intact. The code is Apache-2.0 and much of it is not ours.
- Do not add links to the original project's site, Play listing or maintainer contact. That work is
  abandoned and those destinations are not ours to send users to; `AboutFragment.PROJECT_URL`
  points at this fork.
