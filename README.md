# WallPanel (maintained fork)

An Android app that displays a web dashboard full-screen, intended for a wall-mounted tablet or
phone acting as a home control panel. It points a WebView at a URL — typically Home Assistant — and
adds the things a kiosk device needs: launcher mode, screen wake control, MQTT/HTTP remote control,
sensor reporting, and camera features.

This is a personal fork of the WallPanel project, which was **archived on 2025-05-05** when its
maintainer stepped back and invited others to continue it. The original app is the work of the
WallPanel authors, most recently TheTimeWalker, under the Apache License 2.0.

This fork picks it up from there: it makes the project build again, fixes several bugs that only
surface when the app is sideloaded rather than installed from a store, and documents the device-side
setup a wall panel actually needs. Those changes are listed in [CHANGELOG.md](CHANGELOG.md) and are
mine; everything underneath them is the original authors'.

Links to the original project's website, store listing and maintainer contact have been removed
rather than left pointing at abandoned destinations. Where the app previously linked out, it now
points here.

## Why this fork exists

Upstream at `v0.12.0` **does not build from a clean clone.** Two independent blockers:

1. The `google-services` and `firebase-crashlytics` Gradle plugins are applied, but
   `google-services.json` is not in the repository (it was the maintainer's private config).
2. The `dev` product flavor reads five keys from `local.properties` at *configuration* time, and
   Gradle evaluates every flavor block regardless of which one you are assembling — so a clone
   without that file fails before compiling a single line.

Both are fixed here, along with several bugs that only show up when the app is sideloaded as a
`prodDebug` build rather than installed from Google Play. See [CHANGELOG.md](CHANGELOG.md).

## Status

Runs on a Samsung Galaxy A21 (Android 12) as the device launcher. Every change in this fork was
verified on that hardware, not only by a successful build.

## Building

Requirements:

| | |
|---|---|
| JDK | **11** — AGP 7.3 / Gradle 7.4 / Kotlin 1.6.21. Newer JDKs are not supported by this stack. |
| Android SDK | platform **33**, build-tools **30.0.3**, platform-tools |
| Android Studio | not required; command-line SDK tools are enough |

```bash
export JAVA_HOME=/path/to/jdk-11
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleProdDebug
```

APKs land in `WallPanelApp/build/outputs/apk/prod/debug/`, split per ABI plus a `universal`
variant. Use `arm64-v8a` for any phone from roughly the last decade.

No `local.properties` is required. Create one only if you want to set `sdk.dir` explicitly, or to
supply `dev` flavor values (`code`, `hassUrl`, `broker`, `brokerUsername`, `brokerPass`).

### Installing

```bash
adb install -r WallPanelApp/build/outputs/apk/prod/debug/WallPanelApp-prod-arm64-v8a-debug.apk
```

If a build from Google Play is already installed under `xyz.wallpanel.app`, uninstall it first —
this build is signed with a different key and Android refuses updates across a signature change
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

The `release` build type has **no signing config**, so `assembleProdRelease` produces an unsigned
APK. Debug builds are the practical choice for sideloading.

## Setting up a device as a wall panel

The app is only half the job; these are device settings, and they do not travel with the APK. A
factory reset or a replacement device loses them.

**Point it at your dashboard.** Settings gear → Web Settings → URL. The default settings PIN is
`1234` (`prod` flavor hardcodes it in `WallPanelApp/build.gradle`; change it if the panel is
somewhere guests can reach).

**Make it the launcher**, so the device boots into the dashboard:

```bash
adb shell cmd package set-home-activity \
  "xyz.wallpanel.app/xyz.wallpanel.app.ui.activities.BrowserActivityNative"
```

**Remove the navigation bar.** On Android 11+, immersive mode degrades to
`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, under which the system briefly shows the bars in response
to touch — including the touch that wakes the screen. No app-side code can suppress that. Removing
the bar entirely is the fix. On Samsung:

```bash
adb shell cmd overlay enable com.samsung.internal.systemui.navbar.sec_gestural_no_hint
adb shell settings put global navigation_bar_gesture_hint 0
```

**Turn off the device lock screen** (Settings → Lock screen → Screen lock type → None). The
keyguard sits behind the panel permanently, and any activity that does not explicitly show over it
will let it surface. `adb shell locksettings set-disabled true` reports success on Samsung but does
not work.

**Density**, if the dashboard overflows by a few pixels. Many phones ship a non-standard density
(e.g. 300 dpi = device pixel ratio 1.875), and fractional scaling makes control heights round
inconsistently:

```bash
adb shell wm density 290    # revert: adb shell wm density reset
```

## Known issues

- `minSdkVersion` is **19** (Android 4.4, 2013). This alone pulls in `androidx.multidex`, the
  third-party `android-retrofix` plugin, `android-retrostreams` and `android-retrofuture`. Raising
  it to 24 would let all four go. See CHANGELOG for why core library desugaring is *not* a
  drop-in replacement at AGP 7.3.
- `WallPanelService.kt` is ~1,100 lines covering MQTT, HTTP, sensors, camera and screen control.
- `com.jjoe64.motiondetection` is vendored into the source tree rather than consumed as a
  dependency, so it receives no upstream fixes.
- The `dev`, `qa` and `prod` flavors all share `applicationId xyz.wallpanel.app`, and `qa` differs
  only by a version-name suffix.

## Credits and licence

The overwhelming majority of this codebase is the work of the **original WallPanel authors and its
previous maintainers, most recently TheTimeWalker**, licensed under the
[Apache License 2.0](LICENSE). That licence is unchanged here, and the upstream copyright headers
have been left intact in every file.

Changes made in this fork — the build fixes, the sideload-specific bug fixes, and the documentation
— are listed in [CHANGELOG.md](CHANGELOG.md).

The upstream code as archived is preserved on the tag `upstream-archived-v0.12.0`:

```bash
git diff upstream-archived-v0.12.0..master
```
