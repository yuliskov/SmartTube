# Screen Dimming / Screen Off

SmartTube has two related "dim the screen" features, both driven by a single class:

1. **Screensaver dimming** — after a period of no user input, the screen is dimmed by a
   configurable amount (an in-app screensaver).
2. **Screen off** — a deliberate, user-triggered (or timer-triggered) full or partial
   blackout of the player, toggled by the *Screen dimming* button in the player toolbar.

Neither feature changes the hardware backlight or window brightness. Both work by showing a
full-screen black overlay with a computed alpha on top of the activity.

## Key files

| File | Role |
|---|---|
| `common/.../misc/ScreensaverManager.java` | Central controller: timers, modes, rendering |
| `common/.../misc/MotherActivity.java` | Owns one manager per activity; resets the timer on input |
| `common/.../app/models/playback/controllers/PlayerUIController.java` | Player *Screen dimming* button, screen-off timeout/boot logic |
| `common/.../app/models/playback/controllers/VideoStateController.java` | Enables/disables dimming on play/pause/buffering |
| `smarttubetv/.../ui/playback/actions/ScreenDimmingAction.java` | The toolbar button (`R.id.action_screen_dimming`) |
| `common/.../prefs/GeneralData.java` | Screensaver settings storage |
| `common/.../prefs/PlayerTweaksData.java` | Screen-off settings storage |
| `common/.../utils/Utils.java` | `getColor()` (alpha math), `isScreenOff()`, `enableScreensaver()` |
| `common/src/main/res/layout/dim_container.xml` | The overlay layout (full-screen `FrameLayout`, initially `gone`) |

## How the overlay works

`ScreensaverManager` is created in `MotherActivity` (one per activity). Its constructor calls
`createDimContainer()` (`ScreensaverManager.java:59`), which inflates `R.layout.dim_container`
and attaches it to the window's decor root view, so it sits above all app content.

Dimming = making that view visible with a black background whose alpha encodes the dim
percentage (`ScreensaverManager.java:207-214`):

```java
int screenOffColor    = Utils.getColor(activity, R.color.black, getTweaksData().getScreenOffDimmingPercents());
int screensaverColor  = Utils.getColor(activity, R.color.black, getGeneralData().getScreensaverDimmingPercents());
dimContainer.setBackgroundColor(mMode == MODE_SCREENSAVER ? screensaverColor : screenOffColor);
dimContainer.setVisibility(show ? View.VISIBLE : View.GONE);
```

`Utils.getColor()` (`Utils.java:891`) converts the percentage to an alpha channel:
`ColorUtils.setAlphaComponent(black, 255 * percents / 100)` — so 80% dimming is black at
alpha ~204 over the video, and 100% is fully opaque (true blackout).

## The two modes

`ScreensaverManager` has a `mMode` field with two values (`ScreensaverManager.java:26-27`):

### `MODE_SCREENSAVER` (idle dimming)

- `enable()` (`:108`) schedules `dimScreen()` after `GeneralData.getScreensaverTimeoutMs()`
  (default 60 s). It is re-called on **every input event**, which is what makes it an idle
  timer.
- When the timer fires, `showHideDimming(true)` shows the overlay using the
  *screensaver* dim percentage (default 80%).
- Dimming is **suppressed** (`:198-205`) while a video is playing, during sign-in, or when
  the timeout is set to *Never*.
- In parallel, `showHideScreensaver()` (`:225`) toggles the OS keep-awake flag
  (`FLAG_KEEP_SCREEN_ON` via `Helpers.enableScreensaver/disableScreensaver`): while playing,
  the flag is set so the device never sleeps; when idle-dimmed, the flag is cleared so the
  real system screensaver may eventually start — unless *Disable screensaver* is enabled in
  settings.

### `MODE_SCREEN_OFF` (deliberate blackout)

Entered via `doScreenOff()` (`:135`), which sets the mode and shows the overlay immediately.
Triggered three ways (all in `PlayerUIController`):

1. **Player button** — pressing *Screen dimming* in the player toolbar calls
   `applyScreenOff()` (`PlayerUIController.java:927`).
2. **Auto timeout** — if a screen-off timeout is configured
   (`PlayerTweaksData.getScreenOffTimeoutSec() > 0`), `mTimeoutHandler`
   (`ScreensaverManager.java:38-50`) fires `doScreenOff()` after that many seconds, but only
   while the player is the top view and no dialog is shown. The timer is (re)armed by
   `enableTimeout()` each time the overlay is hidden.
3. **Boot screen off** — if enabled, screen off is re-applied automatically when the playback
   engine starts (`PlayerUIController.java:287-291`), so the state survives track changes.

The overlay uses the *screen-off* dim percentage (default 100%). When it is exactly 100%,
`mIsScreenOff` is set (`:216`) and the player UI overlay is hidden too (`hidePlayerOverlay()`,
`:284`). Other components query this via `Utils.isScreenOff()` to skip work while the screen
is "off" (e.g. SponsorBlock, suggestions loading).

If the percentage is **below 100** ("partial dimming"), `applyScreenOff()` calls
`manager.setBlocked(true)` so the idle screensaver logic cannot override the manually applied
dim — `enable()`/`disable()` are no-ops while blocked (`:109`, `:124`).

## Waking up / cancelling

- **User input**: `MotherActivity` calls `getScreensaverManager().enable()` from
  `dispatchKeyEvent`, `dispatchTouchEvent` and `dispatchGenericMotionEvent`, which cancels the
  pending dim and hides the overlay (via `disable()` → `undimScreen()`).
  Exception: in screen-off mode, DPAD LEFT/RIGHT and volume keys are ignored
  (`MotherActivity.java:138-142`) so seeking/volume changes don't wake the screen.
- **Playback state** (`VideoStateController.showHideScreensaver()`, `:658`): `onPlay` disables
  dimming; `onPause`, `onPlayEnd`, `onBuffering` re-enable the idle timer. These go through
  `enableChecked()`/`disableChecked()`, which do nothing while in `MODE_SCREEN_OFF` — so a
  deliberate blackout is not cancelled by playback events.
- **Lifecycle**: `onResume()` → `enable()`, `onPause()` → `disable()`.
- **Multiple activities**: a static `WeakHashSet` registry plus `notifyRegistry()`
  (`ScreensaverManager.java:302`) ensures that when one activity dims, every other live
  activity's dimming is disabled, so only the topmost activity shows the overlay.
- `disable()` always resets the mode back to `MODE_SCREENSAVER`, ending screen-off mode.

## Settings

**Screensaver (idle) — `GeneralData`, UI in Settings → General
(`GeneralSettingsPresenter.java:455-514`):**

| Setting | Getter | Default | Options |
|---|---|---|---|
| Screen dimming amount | `getScreensaverDimmingPercents()` | 80% | 10–100% |
| Screen dimming timeout | `getScreensaverTimeoutMs()` | 1 min | Never, 5 s – 15 min |
| Disable screensaver | `isScreensaverDisabled()` | true | checkbox (keeps `FLAG_KEEP_SCREEN_ON`) |

Note: with timeout = *Never*, the manager still schedules a 10 s tick
(`ScreensaverManager.java:117-120`) — the overlay is then suppressed in `showHideDimming()`,
but the tick still drives the keep-awake flag and the screen-off timeout re-arm.

**Screen off — `PlayerTweaksData`, UI via long-press on the player's *Screen dimming* button
(`PlayerUIController.showScreenOffDialog()`, `AppDialogUtil.java:676-728`):**

| Setting | Getter | Default | Options |
|---|---|---|---|
| Screen off dimming amount | `getScreenOffDimmingPercents()` | 100% | 10–100% |
| Screen off timeout | `getScreenOffTimeoutSec()` | 0 (disabled) | 0–10 s, 30–180 min |
| Boot screen off | `isBootScreenOffEnabled()` | false | set implicitly by button + partial dim |

Both preference classes persist their fields as delimiter-joined strings at fixed positional
indices, so new fields must be appended, never inserted.

## Related but different: hard screen off

`Utils.isHardScreenOff()` (`Utils.java:877`) queries the OS `PowerManager` interactive state —
i.e. whether the physical display is actually off. It is used for background-playback
decisions (`PlaybackActivity.java:206`) and is unrelated to the in-app dim overlay described
above.
