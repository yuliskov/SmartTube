# ExoPlayer Cronet extension #

The Cronet extension is an [HttpDataSource][] implementation using [Cronet][].

[HttpDataSource]: https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/HttpDataSource.html
[Cronet]: https://chromium.googlesource.com/chromium/src/+/master/components/cronet?autodive=0%2F%2F

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-cronet:2.X.X'
```

where `2.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md

## Using the extension ##

ExoPlayer requests data through `DataSource` instances. These instances are
either instantiated and injected from application code, or obtained from
instances of `DataSource.Factory` that are instantiated and injected from
application code.

If your application only needs to play http(s) content, using the Cronet
extension is as simple as updating any `DataSource`s and `DataSource.Factory`
instantiations in your application code to use `CronetDataSource` and
`CronetDataSourceFactory` respectively. If your application also needs to play
non-http(s) content such as local files, use
```
new DefaultDataSource(
    ...
    new CronetDataSource(...) /* baseDataSource argument */);
```
and
```
new DefaultDataSourceFactory(
    ...
    new CronetDataSourceFactory(...) /* baseDataSourceFactory argument */);
```
respectively.

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.cronet.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
