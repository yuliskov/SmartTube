# ExoPlayer GVR extension #

The GVR extension wraps the [Google VR SDK for Android][]. It provides a
GvrAudioProcessor, which uses [GvrAudioSurround][] to provide binaural rendering
of surround sound and ambisonic soundfields.

[Google VR SDK for Android]: https://developers.google.com/vr/android/
[GvrAudioSurround]: https://developers.google.com/vr/android/reference/com/google/vr/sdk/audio/GvrAudioSurround

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-gvr:2.X.X'
```

where `2.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

## Using the extension ##

* If using `DefaultRenderersFactory`, override
  `DefaultRenderersFactory.buildAudioProcessors` to return a
  `GvrAudioProcessor`.
* If constructing renderers directly, pass a `GvrAudioProcessor` to
  `MediaCodecAudioRenderer`'s constructor.

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.gvr.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
