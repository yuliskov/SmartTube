# ExoPlayer Opus extension #

The Opus extension provides `LibopusAudioRenderer`, which uses libopus (the Opus
decoding library) to decode Opus audio.

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
OPUS_EXT_PATH="${EXOPLAYER_ROOT}/extensions/opus/src/main"
```

* Download the [Android NDK][] and set its location in an environment variable:

```
NDK_PATH="<path to Android NDK>"
```

* Fetch libopus:

```
cd "${OPUS_EXT_PATH}/jni" && \
git clone https://git.xiph.org/opus.git libopus
```

* Run the script to convert arm assembly to NDK compatible format:

```
cd ${OPUS_EXT_PATH}/jni && ./convert_android_asm.sh
```

* Build the JNI native libraries from the command line:

```
cd "${OPUS_EXT_PATH}"/jni && \
${NDK_PATH}/ndk-build APP_ABI=all -j4
```

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html

## Notes ##

* Every time there is a change to the libopus checkout:
  * Arm assembly should be converted by running `convert_android_asm.sh`
  * Clean and re-build the project.
* If you want to use your own version of libopus, place it in
  `${OPUS_EXT_PATH}/jni/libopus`.

## Using the extension ##

Once you've followed the instructions above to check out, build and depend on
the extension, the next step is to tell ExoPlayer to use `LibopusAudioRenderer`.
How you do this depends on which player API you're using:

* If you're passing a `DefaultRenderersFactory` to
  `ExoPlayerFactory.newSimpleInstance`, you can enable using the extension by
  setting the `extensionRendererMode` parameter of the `DefaultRenderersFactory`
  constructor to `EXTENSION_RENDERER_MODE_ON`. This will use
  `LibopusAudioRenderer` for playback if `MediaCodecAudioRenderer` doesn't
  support the input format. Pass `EXTENSION_RENDERER_MODE_PREFER` to give
  `LibopusAudioRenderer` priority over `MediaCodecAudioRenderer`.
* If you've subclassed `DefaultRenderersFactory`, add a `LibopusAudioRenderer`
  to the output list in `buildAudioRenderers`. ExoPlayer will use the first
  `Renderer` in the list that supports the input media format.
* If you've implemented your own `RenderersFactory`, return a
  `LibopusAudioRenderer` instance from `createRenderers`. ExoPlayer will use the
  first `Renderer` in the returned array that supports the input media format.
* If you're using `ExoPlayerFactory.newInstance`, pass a `LibopusAudioRenderer`
  in the array of `Renderer`s. ExoPlayer will use the first `Renderer` in the
  list that supports the input media format.

Note: These instructions assume you're using `DefaultTrackSelector`. If you have
a custom track selector the choice of `Renderer` is up to your implementation,
so you need to make sure you are passing an `LibopusAudioRenderer` to the
player, then implement your own logic to use the renderer for a given track.

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.opus.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
