# ExoPlayer FFmpeg extension #

The FFmpeg extension provides `FfmpegAudioRenderer`, which uses FFmpeg for
decoding and can render audio encoded in a variety of formats.

## License note ##

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this extension also requires building and including one or
more external libraries as described below. These are licensed separately.

[Apache 2.0]: https://github.com/google/ExoPlayer/blob/release-v2/LICENSE

## Build instructions ##

To use this extension you need to clone the ExoPlayer repository and depend on
its modules locally. Instructions for doing this can be found in ExoPlayer's
[top level README][]. The extension is not provided via JCenter (see [#2781][]
for more information).

In addition, it's necessary to build the extension's native components as
follows:

* Set the following environment variables:

```
cd "<path to exoplayer checkout>"
FFMPEG_EXT_PATH="$(pwd)/extensions/ffmpeg/src/main/jni"
```

* Download the [Android NDK][] and set its location in an environment variable.
  Only versions up to NDK 15c are supported currently.

```
NDK_PATH="<path to Android NDK>"
```

* Set up host platform ("darwin-x86_64" for Mac OS X):

```
HOST_PLATFORM="linux-x86_64"
```

* Fetch and build FFmpeg. The configuration flags determine which formats will
  be supported. See the [Supported formats][] page for more details of the
  available flags.

For example, to fetch and build FFmpeg release 4.0 for armeabi-v7a,
  arm64-v8a and x86 on Linux x86_64:

```
COMMON_OPTIONS="\
    --target-os=android \
    --disable-static \
    --enable-shared \
    --disable-doc \
    --disable-programs \
    --disable-everything \
    --disable-avdevice \
    --disable-avformat \
    --disable-swscale \
    --disable-postproc \
    --disable-avfilter \
    --disable-symver \
    --disable-swresample \
    --enable-avresample \
    --enable-decoder=vorbis \
    --enable-decoder=opus \
    --enable-decoder=flac \
    " && \
cd "${FFMPEG_EXT_PATH}" && \
(git -C ffmpeg pull || git clone git://source.ffmpeg.org/ffmpeg ffmpeg) && \
cd ffmpeg && git checkout release/4.0 && \
./configure \
    --libdir=android-libs/armeabi-v7a \
    --arch=arm \
    --cpu=armv7-a \
    --cross-prefix="${NDK_PATH}/toolchains/arm-linux-androideabi-4.9/prebuilt/${HOST_PLATFORM}/bin/arm-linux-androideabi-" \
    --sysroot="${NDK_PATH}/platforms/android-9/arch-arm/" \
    --extra-cflags="-march=armv7-a -mfloat-abi=softfp" \
    --extra-ldflags="-Wl,--fix-cortex-a8" \
    --extra-ldexeflags=-pie \
    ${COMMON_OPTIONS} \
    && \
make -j4 && make install-libs && \
make clean && ./configure \
    --libdir=android-libs/arm64-v8a \
    --arch=aarch64 \
    --cpu=armv8-a \
    --cross-prefix="${NDK_PATH}/toolchains/aarch64-linux-android-4.9/prebuilt/${HOST_PLATFORM}/bin/aarch64-linux-android-" \
    --sysroot="${NDK_PATH}/platforms/android-21/arch-arm64/" \
    --extra-ldexeflags=-pie \
    ${COMMON_OPTIONS} \
    && \
make -j4 && make install-libs && \
make clean && ./configure \
    --libdir=android-libs/x86 \
    --arch=x86 \
    --cpu=i686 \
    --cross-prefix="${NDK_PATH}/toolchains/x86-4.9/prebuilt/${HOST_PLATFORM}/bin/i686-linux-android-" \
    --sysroot="${NDK_PATH}/platforms/android-9/arch-x86/" \
    --extra-ldexeflags=-pie \
    --disable-asm \
    ${COMMON_OPTIONS} \
    && \
make -j4 && make install-libs && \
make clean
```

* Build the JNI native libraries, setting `APP_ABI` to include the architectures
  built in the previous step. For example:

```
cd "${FFMPEG_EXT_PATH}" && \
${NDK_PATH}/ndk-build APP_ABI="armeabi-v7a arm64-v8a x86" -j4
```

## Using the extension ##

Once you've followed the instructions above to check out, build and depend on
the extension, the next step is to tell ExoPlayer to use `FfmpegAudioRenderer`.
How you do this depends on which player API you're using:

* If you're passing a `DefaultRenderersFactory` to
  `ExoPlayerFactory.newSimpleInstance`, you can enable using the extension by
  setting the `extensionRendererMode` parameter of the `DefaultRenderersFactory`
  constructor to `EXTENSION_RENDERER_MODE_ON`. This will use
  `FfmpegAudioRenderer` for playback if `MediaCodecAudioRenderer` doesn't
  support the input format. Pass `EXTENSION_RENDERER_MODE_PREFER` to give
  `FfmpegAudioRenderer` priority over `MediaCodecAudioRenderer`.
* If you've subclassed `DefaultRenderersFactory`, add an `FfmpegAudioRenderer`
  to the output list in `buildAudioRenderers`. ExoPlayer will use the first
  `Renderer` in the list that supports the input media format.
* If you've implemented your own `RenderersFactory`, return an
  `FfmpegAudioRenderer` instance from `createRenderers`. ExoPlayer will use the
  first `Renderer` in the returned array that supports the input media format.
* If you're using `ExoPlayerFactory.newInstance`, pass an `FfmpegAudioRenderer`
  in the array of `Renderer`s. ExoPlayer will use the first `Renderer` in the
  list that supports the input media format.

Note: These instructions assume you're using `DefaultTrackSelector`. If you have
a custom track selector the choice of `Renderer` is up to your implementation,
so you need to make sure you are passing an `FfmpegAudioRenderer` to the player,
then implement your own logic to use the renderer for a given track.

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[Android NDK]: https://developer.android.com/tools/sdk/ndk/index.html
[#2781]: https://github.com/google/ExoPlayer/issues/2781
[Supported formats]: https://exoplayer.dev/supported-formats.html#ffmpeg-extension

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.ffmpeg.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
