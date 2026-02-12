# ExoPlayer Flac extension #

The Flac extension provides `FlacExtractor` and `LibflacAudioRenderer`, which
use libFLAC (the Flac decoding library) to extract and decode FLAC audio.

## License note ##

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this extension also requires building and including one or
more external libraries as described below. These are licensed separately.

[Apache 2.0]: https://github.com/google/ExoPlayer/blob/release-v2/LICENSE

## Build instructions ##

To use this extension you need to clone the ExoPlayer repository and depend on
its modules locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

In addition, it's necessary to build the extension's native components as
follows:

* Set the following environment variables:

```
cd "<path to exoplayer checkout>"
EXOPLAYER_ROOT="$(pwd)"
FLAC_EXT_PATH="${EXOPLAYER_ROOT}/extensions/flac/src/main"
```

* Download the [Android NDK][] (version <= 17c) and set its location in an
  environment variable:

```
NDK_PATH="<path to Android NDK>"
```

* Download and extract flac-1.3.2 as "${FLAC_EXT_PATH}/jni/flac" folder:

```
cd "${FLAC_EXT_PATH}/jni" && \
curl https://ftp.osuosl.org/pub/xiph/releases/flac/flac-1.3.2.tar.xz | tar xJ && \
mv flac-1.3.2 flac
```

* Build the JNI native libraries from the command line:

```
cd "${FLAC_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j4
```

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html

## Using the extension ##

Once you've followed the instructions above to check out, build and depend on
the extension, the next step is to tell ExoPlayer to use the extractor and/or
renderer.

### Using `FlacExtractor` ###

`FlacExtractor` is used via `ExtractorMediaSource`. If you're using
`DefaultExtractorsFactory`, `FlacExtractor` will automatically be used to read
`.flac` files. If you're not using `DefaultExtractorsFactory`, return a
`FlacExtractor` from your `ExtractorsFactory.createExtractors` implementation.

### Using `LibflacAudioRenderer` ###

* If you're passing a `DefaultRenderersFactory` to
  `ExoPlayerFactory.newSimpleInstance`, you can enable using the extension by
  setting the `extensionRendererMode` parameter of the `DefaultRenderersFactory`
  constructor to `EXTENSION_RENDERER_MODE_ON`. This will use
  `LibflacAudioRenderer` for playback if `MediaCodecAudioRenderer` doesn't
  support the input format. Pass `EXTENSION_RENDERER_MODE_PREFER` to give
  `LibflacAudioRenderer` priority over `MediaCodecAudioRenderer`.
* If you've subclassed `DefaultRenderersFactory`, add a `LibflacAudioRenderer`
  to the output list in `buildAudioRenderers`. ExoPlayer will use the first
  `Renderer` in the list that supports the input media format.
* If you've implemented your own `RenderersFactory`, return a
  `LibflacAudioRenderer` instance from `createRenderers`. ExoPlayer will use the
  first `Renderer` in the returned array that supports the input media format.
* If you're using `ExoPlayerFactory.newInstance`, pass a `LibflacAudioRenderer`
  in the array of `Renderer`s. ExoPlayer will use the first `Renderer` in the
  list that supports the input media format.

Note: These instructions assume you're using `DefaultTrackSelector`. If you have
a custom track selector the choice of `Renderer` is up to your implementation,
so you need to make sure you are passing an `LibflacAudioRenderer` to the
player, then implement your own logic to use the renderer for a given track.

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.flac.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
