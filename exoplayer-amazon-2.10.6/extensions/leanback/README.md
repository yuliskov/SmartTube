# ExoPlayer Leanback extension #

This [Leanback][] Extension provides a [PlayerAdapter][] implementation for
ExoPlayer.

[PlayerAdapter]: https://developer.android.com/reference/android/support/v17/leanback/media/PlayerAdapter.html
[Leanback]: https://developer.android.com/reference/android/support/v17/leanback/package-summary.html

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-leanback:2.X.X'
```

where `2.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.leanback.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
