# ExoPlayer Firebase JobDispatcher extension #

**DEPRECATED - Please use [WorkManager extension][] or [PlatformScheduler][] instead.**

This extension provides a Scheduler implementation which uses [Firebase JobDispatcher][].

[WorkManager extension]: https://github.com/google/ExoPlayer/blob/release-v2/extensions/workmanager/README.md
[PlatformScheduler]: https://github.com/google/ExoPlayer/blob/release-v2/library/core/src/main/java/com/google/android/exoplayer2/scheduler/PlatformScheduler.java
[Firebase JobDispatcher]: https://github.com/firebase/firebase-jobdispatcher-android

## Getting the extension ##

The easiest way to use the extension is to add it as a gradle dependency:

```gradle
implementation 'com.google.android.exoplayer:extension-jobdispatcher:2.X.X'
```

where `2.X.X` is the version, which must match the version of the ExoPlayer
library being used.

Alternatively, you can clone the ExoPlayer repository and depend on the module
locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
