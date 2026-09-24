# Test TTS Voiceover on Android TV

For local verification, run:

```sh
./gradlew :common:testStbetaDebugUnitTest :smarttubetv:lintStbetaRelease :smarttubetv:assembleStbetaDebug
```

Use a TV or Android TV emulator for playback checks. A debug APK may have a
different signing key from an installed release APK; install it under a separate
application ID or on a test device to preserve existing app data.

## Player checks

1. In **Settings → Player → TTS Voiceover settings**, enable **TTS Voiceover**
   and select Russian as the voiceover language. Play two English videos; both
   should start voiceover. Turn it off with the player button in the first video;
   the second should still start automatically. Disabling **Enable TTS Voiceover**
   should stop translated audio and hide the button.
2. While Yandex prepares audio, the video should pause, show the existing
   loading indicator and briefly report the target language in a bottom toast.
   The video should resume when audio is ready. If it was already paused, it
   should remain paused. Repeat with an uncached video that requires source
   audio upload, and cancel while waiting.
3. Check pause, seek, speed, volume keys, Home/return, replay, and next video.
   The translated audio should stay in sync. Original volume should return
   after voiceover stops, including when SmartTube's automatic volume is on.
4. With **Original language → Auto (YouTube)**, a video whose original language
   is the target language should be skipped automatically.
   Test a Russian video with Russian selected, and a Belarusian video to ensure
   the Russian rule is not applied to Belarusian. Then manually select an
   original language and verify it takes priority over YouTube's detection.
   Return to **Auto (YouTube)** to restore automatic detection. The player
   button should try voiceover even when YouTube reports the target language.
5. Select **YouTube audio only** and verify that a dubbed audio track starts
   without a second mixed player. The button should restore the original track
   and suppress automatic voiceover for the rest of that video. Repeat with a
   legacy original track labeled only `en`, without `(original)`. Select
   **YouTube audio, then Yandex VOT** and check the fallback when no dub exists.
6. Check English, Russian and Kazakh labels, the target language sent to the
   service, button visibility under **Player buttons**, the four controls under
   **Volume and mixing**, and playback when the translation service fails.
7. With **Live voices** selected and no Yandex account, try a video for which
   Yandex requires sign-in. The app should retry with standard voices instead
   of reporting voiceover unavailable.

The action icon is Google Material Symbols Outlined `voice_selection`
(Apache 2.0). Yandex translation uses an unofficial endpoint; protocol behavior
is based on [`voice-over-translation`](https://github.com/ilyhalight/voice-over-translation)
and [vot.js](https://github.com/FOSWLY/vot.js). Returned audio URLs are
short-lived and are never stored.
