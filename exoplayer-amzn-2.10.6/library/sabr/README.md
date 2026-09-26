# ExoPlayer SABR library module #

Provides support for YouTube Dynamic Adaptive Streaming (SABR) content. To
play SABR content, instantiate a `SabrMediaSource` and pass it to
`ExoPlayer.prepare`.

## Subtitles ##

Subtitle tracks supplied by `MediaItemFormatInfo` are external caption files.
They are fetched with an HTTP GET, including the manifest's visitor cookie when
available, and loaded as a single sample covering the period. They must not be
sent to the SABR endpoint or registered as SABR format selections. Audio and
video continue to use SABR container requests.

To run the module's regression tests from the repository root, use JDK 11 for
compatibility with its Robolectric version:

```sh
JAVA_HOME=/path/to/jdk-11 bash ./gradlew :exoplayer-library-sabr:testStstableDebugUnitTest
```

## Links ##

* [Developer Guide][].
* [Javadoc][]: Classes matching `com.google.android.exoplayer2.source.sabr.*`
  belong to this module.

[Developer Guide]: https://exoplayer.dev/sabr.html
[Javadoc]: https://exoplayer.dev/doc/reference/index.html
