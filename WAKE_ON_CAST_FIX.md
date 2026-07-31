# Fix: TV Doesn't Wake Up / Disappears From Cast List While Asleep

This document explains a real bug we diagnosed and fixed in SmartTube: on Android 14 /
Google TV devices, casting a video from the YouTube mobile app to SmartTube would not wake
the TV, and the TV would silently disappear from the phone's cast device list a short while
after going to sleep. This used to work fine before Android 14.

Tested on: Acer R4G (Google TV, Realtek chipset), Android 14 (API 34).

## TL;DR — the actual root cause

Android 13/14 introduced a TV-specific power-saving feature called **Low Power Standby**
(a.k.a. "networked standby"). Once the TV has been asleep for a while, Android cuts off
**network access entirely** for every app except a small, hardcoded, OS-level allowlist:

```
com.google.android.apps.mediashell   (official Chromecast/Cast receiver)
com.google.android.katniss           (Google Assistant)
com.google.android.gms
com.android.tv.mdnsoffloadcmd
com.google.android.tv.remote.service
```

SmartTube isn't on that list — no third-party app can add itself to it via any public API.
So even though SmartTube's background service was correctly holding a wake lock, running as
a foreground service, and whitelisted from Doze/battery optimization, the moment the TV
entered Low Power Standby the OS cut its network access outright. SmartTube's cast/remote
connection to YouTube's "Lounge API" died, the TV vanished from the cast list, and no
incoming "play" command could ever reach the app — so there was nothing left to wake the
screen for.

This is a **device/OS setting**, not something fixable purely in app code. The fix is to
turn the feature off at the system level with two `adb shell settings put global` commands
(see below). We also fixed several smaller, real bugs in the app itself along the way (see
[What we changed in the code](#what-we-changed-in-the-code)) — those are necessary too, but
the Low Power Standby setting is what was actually blocking everything end to end.

## Step 1 — Enable Wireless ADB on the TV

You need ADB access to run a couple of one-time `adb shell settings put` commands.

1. On the TV, go to **Settings → Device Preferences → About**.
2. Scroll to **Build** and click it **7 times** until you see "You are now a developer!".
3. Go back to **Settings → Device Preferences → Developer options**.
4. Turn on **USB debugging** (if present) and **Wireless debugging**.
5. Open **Wireless debugging** and note the **IP address and port** shown (e.g.
   `192.168.1.136:37123` for pairing, and a separate port for the actual connection screen —
   Google TV usually shows a plain `IP:PORT` under "IP address & Port" once wireless
   debugging is on).
6. On your PC, with [platform-tools](https://developer.android.com/tools/releases/platform-tools)
   installed and `adb` on your PATH (or use the full path to `adb.exe`):
   ```
   adb pair <ip>:<pairing-port>
   ```
   Enter the 6-digit pairing code shown on the TV when prompted.
7. Then connect:
   ```
   adb connect <ip>:<connect-port>
   ```
8. Confirm the TV shows up:
   ```
   adb devices -l
   ```
   You should see something like:
   ```
   192.168.1.136:5555     device product:ACER_NFF model:R4G device:R4_GTV
   ```

If your TV doesn't expose a separate "Wireless debugging" screen with pairing (older Android
TV skins), you can instead enable USB debugging, plug the TV in via USB once to run
`adb tcpip 5555`, then unplug and use `adb connect <tv-ip>:5555` from then on.

## Step 2 — Disable Low Power Standby (the actual fix)

With the TV connected via `adb`, run:

```
adb shell settings put global low_power_standby_enabled 0
adb shell settings put global network_standby_enable 0
```

That's it — no reboot needed. This is a persistent `Settings.Global` value; it survives
reboots and only resets on a factory reset.

**How to verify it took effect:**
```
adb shell dumpsys power | findstr mLowPowerStandbyActive
```
Should print `mLowPowerStandbyActive=false`. You can also check the network policy for
SmartTube specifically:
```
adb shell dumpsys netpolicy | findstr "UID=10105"
```
(`10105` was SmartTube's UID on our test device — yours may differ; find it with
`adb shell dumpsys package org.smarttube.stable | findstr userId`.) Look for
`effective=NONE` — that means SmartTube isn't currently network-blocked.

> Trade-off: disabling this device-wide slightly increases standby power draw, because the
> TV keeps its WiFi radio active while asleep instead of fully cutting it. For a TV that's
> normally plugged into mains power, this is a non-issue in practice.

## Step 3 — Also make sure these app permissions are granted

These aren't the root cause, but they're required for the code-level fixes below to work,
and are easy to accidentally lose after a fresh install:

1. Open SmartTube once, fully.
2. When/если prompted, allow:
   - **"Display over other apps"** (`SYSTEM_ALERT_WINDOW`)
   - **"Allow background activity" / "Unrestricted battery"** (ignore battery optimizations)
3. In SmartTube's own settings, make sure **Device Link** (the cast/remote-control toggle,
   under Remote & Cast Control settings) is turned **on**, and re-pair with your phone's
   YouTube app if it asks (fresh installs reset this).

You can double check the battery-optimization exemption via adb too:
```
adb shell dumpsys deviceidle whitelist | findstr org.smarttube.stable
```
Should print a line like `user,org.smarttube.stable,<uid>`. If it's missing, open
Settings → Apps → SmartTube → Battery on the TV and set it to unrestricted, or run:
```
adb shell cmd deviceidle whitelist +org.smarttube.stable
```

## What we changed in the code

These fixes address secondary issues found during diagnosis — real bugs, but not the main
blocker described above. Worth keeping regardless.

| File | What changed | Why |
|---|---|---|
| `common/src/main/AndroidManifest.xml` | Added `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `USE_FULL_SCREEN_INTENT` permissions | Needed by the fixes below |
| `common/.../utils/Utils.java` | New `wakeUpScreen()` (real `PowerManager` wake lock, works from any context — the old `turnScreenOn()` silently did nothing when called with an Application context, which is what `ViewManager` always passed it); new `wakeUpAndOpenActivity()` using a full-screen-intent notification (the same background-activity-launch mechanism incoming-call apps use, not subject to Android's background-activity-launch grace-period restriction); `requestIgnoreBatteryOptimizations()` / `requestOverlayPermission()` to actually prompt for those special permissions at runtime (declaring them in the manifest alone does not grant them) | The old wake call was a complete no-op; this makes screen-wake actually work |
| `common/.../misc/RemoteControlService.java` | Holds a `PARTIAL_WAKE_LOCK` for as long as the service is alive | Keeps the CPU from deep-sleeping while the background cast connection needs to stay alive |
| `common/.../app/views/ViewManager.java` | `movePlayerToForeground()` now calls the new wake mechanism | Wires the fix into the actual cast-command handling path |
| `common/.../app/presenters/SplashPresenter.java` | Requests battery-optimization exemption and overlay permission once at first launch | Makes the two special permissions actually get requested from the user |
| `smarttubetv/.../ui/playback/PlaybackActivity.java` | `onResume()` also calls `Utils.turnScreenOn(this)` with a real Activity context | Belt-and-braces: dismisses the keyguard using the officially correct Android API once the Activity actually resumes |
| `MediaServiceCore/.../lounge/LoungeService.java` | Added a 2-second backoff delay in the reconnect loop's generic exception handler | Without it, a persistent failure (e.g. DNS not ready right after WiFi wakes from sleep) turned the reconnect loop into a tight busy-loop retrying every ~10ms — burning CPU and hammering DNS instead of giving the network a moment to recover |

## Bonus: auto-launch SmartTube on every restart

There's no OS-level "boot app" setting exposed on this device (checked
`adb shell settings list global/secure` for anything boot/startup/launch-related — nothing
usable was found), so this couldn't be done with an adb command alone. Instead,
`RemoteControlReceiver` (which already listens for boot broadcasts to restart the background
cast service) now also brings the app's UI to the foreground specifically on boot-related
actions (`BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `LOCKED_BOOT_COMPLETED` — not on the other
actions it listens to like `SCREEN_ON` or `TIME_SET`, which would be far too aggressive).
This relies on the same `SYSTEM_ALERT_WINDOW` permission exemption used elsewhere in this fix
to start an Activity from a background `BroadcastReceiver` context. No extra setup needed
beyond the permissions already covered in Step 3 above.

## Diagnosis notes (for the curious / for future debugging)

We initially suspected — and partially fixed — an Android Background Activity Launch (BAL)
restriction and a `ForegroundServiceStartNotAllowedException` risk (Android 12+ restricts
starting foreground services from the background). Those fixes are real and included above,
and we verified via `adb logcat` that the Activity-launch mechanism does work correctly even
while the TV is genuinely `Asleep` (look for `WAKE_REASON_APPLICATION,
details=android.server.am:TURN_ON:dismissKeyguard` in `dumpsys power` / logcat — that's our
app's wake code succeeding).

The actual blocker only became clear after checking `dumpsys netpolicy` and `dumpsys power`
directly on the device while it was asleep and confirming `mLowPowerStandbyActive=true` with
SmartTube's UID showing `blocked_state={blocked=LOW_POWER_STANDBY, ..., effective=LOW_POWER_STANDBY}`.
If you're debugging a similar issue on a different TV/OEM, check that first:

```
adb shell dumpsys power | findstr mLowPowerStandbyActive
adb shell dumpsys netpolicy | findstr "UID=<your app's uid>"
```
