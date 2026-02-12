# ExoPlayer Cast extension #

## Description ##

The cast extension is a [Player][] implementation that controls playback on a
Cast receiver app.

[Player]: https://exoplayer.dev/doc/reference/index.html?com/google/android/exoplayer2/Player.html

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-cast:2.X.X'
```

where `2.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the extension ##

Create a `CastPlayer` and use it to integrate Cast into your app using
ExoPlayer's common `Player` interface.
