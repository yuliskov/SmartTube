# Release notes #

### 2.10.6 (2019-10-18) ###

* Downloads: Merge downloads in `SegmentDownloader` to improve overall download
  speed ([#5978](https://github.com/google/ExoPlayer/issues/5978))
* Add `Player.onPlaybackSuppressionReasonChanged` to allow listeners to
  detect playbacks suppressions (e.g. transient audio focus loss) directly
  ([#6203](https://github.com/google/ExoPlayer/issues/6203)).
* DASH:
  * Support `Label` elements
    ([#6297](https://github.com/google/ExoPlayer/issues/6297)).
  * Support legacy audio channel configuration
    ([#6523](https://github.com/google/ExoPlayer/issues/6523)).
* HLS: Add support for ID3 in EMSG when using FMP4 streams
  ([spec](https://aomediacodec.github.io/av1-id3/)).
* MP3: Add workaround to avoid prematurely ending playback of some SHOUTcast
  live streams ([#6537](https://github.com/google/ExoPlayer/issues/6537),
  [#6315](https://github.com/google/ExoPlayer/issues/6315) and
  [#5658](https://github.com/google/ExoPlayer/issues/5658)).
* Metadata: Expose the raw ICY metadata through `IcyInfo`
  ([#6476](https://github.com/google/ExoPlayer/issues/6476)).
* UI:
  * Setting `app:played_color` on `PlayerView` and `PlayerControlView` no longer
    adjusts the colors of the scrubber handle , buffered and unplayed parts of
    the time bar. These can be set separately using `app:scrubber_color`,
    `app:buffered_color` and `app_unplayed_color` respectively.
  * Setting `app:ad_marker_color` on `PlayerView` and `PlayerControlView` no
    longer adjusts the color of played ad markers. The color of played ad
    markers can be set separately using `app:played_ad_marker_color`.

### 2.10.5 (2019-09-20) ###

* Add `Player.isPlaying` and `EventListener.onIsPlayingChanged` to check whether
  the playback position is advancing. This helps to determine if playback is
  suppressed due to audio focus loss. Also add
  `Player.getPlaybackSuppressedReason` to determine the reason of the
  suppression ([#6203](https://github.com/google/ExoPlayer/issues/6203)).
* Track selection
  * Add `allowAudioMixedChannelCountAdaptiveness` parameter to
    `DefaultTrackSelector` to allow adaptive selections of audio tracks with
    different channel counts.
  * Improve text selection logic to always prefer the better language matches
    over other selection parameters.
  * Fix audio selection issue where languages are compared by bitrate
    ([#6335](https://github.com/google/ExoPlayer/issues/6335)).
* Performance
  * Increase maximum video buffer size from 13MB to 32MB. The previous default
    was too small for high quality streams.
  * Reset `DefaultBandwidthMeter` to initial values on network change.
  * Bypass sniffing in `ProgressiveMediaPeriod` in case a single extractor is
    provided ([#6325](https://github.com/google/ExoPlayer/issues/6325)).
* Metadata
  * Support EMSG V1 boxes in FMP4.
  * Support unwrapping of nested metadata (e.g. ID3 and SCTE-35 in EMSG).
* Add `HttpDataSource.getResponseCode` to provide the status code associated
  with the most recent HTTP response.
* Fix transitions between packed audio and non-packed audio segments in HLS
  ([#6444](https://github.com/google/ExoPlayer/issues/6444)).
* Fix issue where a request would be retried after encountering an error, even
  though the `LoadErrorHandlingPolicy` classified the error as fatal.
* Fix initialization data handling for FLAC in MP4
  ([#6396](https://github.com/google/ExoPlayer/issues/6396),
  [#6397](https://github.com/google/ExoPlayer/issues/6397)).
* Fix decoder selection for E-AC3 JOC streams
  ([#6398](https://github.com/google/ExoPlayer/issues/6398)).
* Fix `PlayerNotificationManager` to show play icon rather than pause icon when
  playback is ended ([#6324](https://github.com/google/ExoPlayer/issues/6324)).
* RTMP extension: Upgrade LibRtmp-Client-for-Android to fix RTMP playback issues
  ([#4200](https://github.com/google/ExoPlayer/issues/4200),
  [#4249](https://github.com/google/ExoPlayer/issues/4249),
  [#4319](https://github.com/google/ExoPlayer/issues/4319),
  [#4337](https://github.com/google/ExoPlayer/issues/4337)).
* IMA extension: Fix crash in `ImaAdsLoader.onTimelineChanged`
  ([#5831](https://github.com/google/ExoPlayer/issues/5831)).

### 2.10.4 (2019-07-26) ###

* Offline: Add `Scheduler` implementation that uses `WorkManager`.
* Add ability to specify a description when creating notification channels via
  ExoPlayer library classes.
* Switch normalized BCP-47 language codes to use 2-letter ISO 639-1 language
  tags instead of 3-letter ISO 639-2 language tags.
* Ensure the `SilenceMediaSource` position is in range
  ([#6229](https://github.com/google/ExoPlayer/issues/6229)).
* WAV: Calculate correct duration for clipped streams
  ([#6241](https://github.com/google/ExoPlayer/issues/6241)).
* MP3: Use CBR header bitrate, not calculated bitrate. This reverts a change
  from 2.9.3 ([#6238](https://github.com/google/ExoPlayer/issues/6238)).
* Flac extension: Parse `VORBIS_COMMENT` and `PICTURE` metadata
  ([#5527](https://github.com/google/ExoPlayer/issues/5527)).
* Fix issue where initial seek positions get ignored when playing a preroll ad
  ([#6201](https://github.com/google/ExoPlayer/issues/6201)).
* Fix issue where invalid language tags were normalized to "und" instead of
  keeping the original
  ([#6153](https://github.com/google/ExoPlayer/issues/6153)).
* Fix `DataSchemeDataSource` re-opening and range requests
  ([#6192](https://github.com/google/ExoPlayer/issues/6192)).
* Fix Flac and ALAC playback on some LG devices
  ([#5938](https://github.com/google/ExoPlayer/issues/5938)).
* Fix issue when calling `performClick` on `PlayerView` without
  `PlayerControlView`
  ([#6260](https://github.com/google/ExoPlayer/issues/6260)).
* Fix issue where playback speeds are not used in adaptive track selections
  after manual selection changes for other renderers
  ([#6256](https://github.com/google/ExoPlayer/issues/6256)).

### 2.10.3 (2019-07-09) ###

* Display last frame when seeking to end of stream
  ([#2568](https://github.com/google/ExoPlayer/issues/2568)).
* Audio:
  * Fix an issue where not all audio was played out when the configuration
    for the underlying track was changing (e.g., at some period transitions).
  * Fix an issue where playback speed was applied inaccurately in playlists
    ([#6117](https://github.com/google/ExoPlayer/issues/6117)).
* UI: Fix `PlayerView` incorrectly consuming touch events if no controller is
  attached ([#6109](https://github.com/google/ExoPlayer/issues/6109)).
* CEA608: Fix repetition of special North American characters
  ([#6133](https://github.com/google/ExoPlayer/issues/6133)).
* FLV: Fix bug that caused playback of some live streams to not start
  ([#6111](https://github.com/google/ExoPlayer/issues/6111)).
* SmoothStreaming: Parse text stream `Subtype` into `Format.roleFlags`.
* MediaSession extension: Fix `MediaSessionConnector.play()` not resuming
  playback ([#6093](https://github.com/google/ExoPlayer/issues/6093)).

### 2.10.2 (2019-06-03) ###

* Add `ResolvingDataSource` for just-in-time resolution of `DataSpec`s
  ([#5779](https://github.com/google/ExoPlayer/issues/5779)).
* Add `SilenceMediaSource` that can be used to play silence of a given
  duration ([#5735](https://github.com/google/ExoPlayer/issues/5735)).
* Offline:
  * Prevent unexpected `DownloadHelper.Callback.onPrepared` callbacks after
    preparation of a `DownloadHelper` fails
    ([#5915](https://github.com/google/ExoPlayer/issues/5915)).
  * Fix `CacheUtil.cache()` downloading too much data
    ([#5927](https://github.com/google/ExoPlayer/issues/5927)).
  * Fix misreporting cached bytes when caching is paused
    ([#5573](https://github.com/google/ExoPlayer/issues/5573)).
* UI:
  * Allow setting `DefaultTimeBar` attributes on `PlayerView` and
    `PlayerControlView`.
  * Change playback controls toggle from touch down to touch up events
    ([#5784](https://github.com/google/ExoPlayer/issues/5784)).
  * Fix issue where playback controls were not kept visible on key presses
    ([#5963](https://github.com/google/ExoPlayer/issues/5963)).
* Subtitles:
  * CEA-608: Handle XDS and TEXT modes
    ([#5807](https://github.com/google/ExoPlayer/pull/5807)).
  * TTML: Fix bitmap rendering
    ([#5633](https://github.com/google/ExoPlayer/pull/5633)).
* IMA: Fix ad pod index offset calculation without preroll
  ([#5928](https://github.com/google/ExoPlayer/issues/5928)).
* Add a `playWhenReady` flag to MediaSessionConnector.PlaybackPreparer methods
  to indicate whether a controller sent a play or only a prepare command. This
  allows to take advantage of decoder reuse with the MediaSessionConnector
  ([#5891](https://github.com/google/ExoPlayer/issues/5891)).
* Add `ProgressUpdateListener` to `PlayerControlView`
  ([#5834](https://github.com/google/ExoPlayer/issues/5834)).
* Add support for auto-detecting UDP streams in `DefaultDataSource`
  ([#6036](https://github.com/google/ExoPlayer/pull/6036)).
* Allow enabling decoder fallback with `DefaultRenderersFactory`
  ([#5942](https://github.com/google/ExoPlayer/issues/5942)).
* Gracefully handle revoked `ACCESS_NETWORK_STATE` permission
  ([#6019](https://github.com/google/ExoPlayer/issues/6019)).
* Fix decoding problems when seeking back after seeking beyond a mid-roll ad
  ([#6009](https://github.com/google/ExoPlayer/issues/6009)).
* Fix application of `maxAudioBitrate` for adaptive audio track groups
  ([#6006](https://github.com/google/ExoPlayer/issues/6006)).
* Fix bug caused by parallel adaptive track selection using `Format`s without
  bitrate information
  ([#5971](https://github.com/google/ExoPlayer/issues/5971)).
* Fix bug in `CastPlayer.getCurrentWindowIndex()`
  ([#5955](https://github.com/google/ExoPlayer/issues/5955)).

### 2.10.1 (2019-05-16) ###

* Offline: Add option to remove all downloads.
* HLS: Fix `NullPointerException` when using HLS chunkless preparation
  ([#5868](https://github.com/google/ExoPlayer/issues/5868)).
* Fix handling of empty values and line terminators in SHOUTcast ICY metadata
  ([#5876](https://github.com/google/ExoPlayer/issues/5876)).
* Fix DVB subtitles for SDK 28
  ([#5862](https://github.com/google/ExoPlayer/issues/5862)).
* Add a workaround for a decoder failure on ZTE Axon7 mini devices when playing
  48kHz audio ([#5821](https://github.com/google/ExoPlayer/issues/5821)).

### 2.10.0 (2019-04-15) ###

* Core library:
  * Improve decoder re-use between playbacks
    ([#2826](https://github.com/google/ExoPlayer/issues/2826)). Read
    [this blog post](https://medium.com/google-exoplayer/improved-decoder-reuse-in-exoplayer-ef4c6d99591d)
    for more details.
  * Rename `ExtractorMediaSource` to `ProgressiveMediaSource`.
  * Fix issue where using `ProgressiveMediaSource.Factory` would mean that
    `DefaultExtractorsFactory` would be kept by proguard. Custom
    `ExtractorsFactory` instances must now be passed via the
    `ProgressiveMediaSource.Factory` constructor, and `setExtractorsFactory` is
    deprecated.
  * Make the default minimum buffer size equal the maximum buffer size for video
    playbacks ([#2083](https://github.com/google/ExoPlayer/issues/2083)).
  * Move `PriorityTaskManager` from `DefaultLoadControl` to `SimpleExoPlayer`.
  * Add new `ExoPlaybackException` types for remote exceptions and out-of-memory
    errors.
  * Use full BCP 47 language tags in `Format`.
  * Do not retry failed loads whose error is `FileNotFoundException`.
  * Fix issue where not resetting the position for a new `MediaSource` in calls
    to `ExoPlayer.prepare` causes an `IndexOutOfBoundsException`
    ([#5520](https://github.com/google/ExoPlayer/issues/5520)).
* Offline:
  * Improve offline support. `DownloadManager` now tracks all offline content,
    not just tasks in progress. Read
    [this page](https://exoplayer.dev/downloading-media.html) for more details.
* Caching:
  * Improve performance of `SimpleCache`
    ([#4253](https://github.com/google/ExoPlayer/issues/4253)).
  * Cache data with unknown length by default. The previous flag to opt in to
    this behavior (`DataSpec.FLAG_ALLOW_CACHING_UNKNOWN_LENGTH`) has been
    replaced with an opt out flag
    (`DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN`).
* Extractors:
  * MP4/FMP4: Add support for Dolby Vision.
  * MP4: Fix issue handling meta atoms in some streams
    ([#5698](https://github.com/google/ExoPlayer/issues/5698),
    [#5694](https://github.com/google/ExoPlayer/issues/5694)).
  * MP3: Add support for SHOUTcast ICY metadata
    ([#3735](https://github.com/google/ExoPlayer/issues/3735)).
  * MP3: Fix ID3 frame unsychronization
    ([#5673](https://github.com/google/ExoPlayer/issues/5673)).
  * MP3: Fix playback of badly clipped files
    ([#5772](https://github.com/google/ExoPlayer/issues/5772)).
  * MPEG-TS: Enable HDMV DTS stream detection only if a flag is set. By default
    (i.e. if the flag is not set), the 0x82 elementary stream type is now
    treated as an SCTE subtitle track
    ([#5330](https://github.com/google/ExoPlayer/issues/5330)).
* Track selection:
  * Add options for controlling audio track selections to `DefaultTrackSelector`
    ([#3314](https://github.com/google/ExoPlayer/issues/3314)).
  * Update `TrackSelection.Factory` interface to support creating all track
    selections together.
  * Allow to specify a selection reason for a `SelectionOverride`.
  * When no text language preference matches, only select forced text tracks
    whose language matches the selected audio language.
* UI:
  * Update `DefaultTimeBar` based on duration of media and add parameter to set
    the minimum update interval to control the smoothness of the updates
    ([#5040](https://github.com/google/ExoPlayer/issues/5040)).
  * Move creation of dialogs for `TrackSelectionView`s to
    `TrackSelectionDialogBuilder` and add option to select multiple overrides.
  * Change signature of `PlayerNotificationManager.NotificationListener` to
    better fit service requirements.
  * Add option to include navigation actions in the compact mode of
    notifications created using `PlayerNotificationManager`.
  * Fix issues with flickering notifications on KitKat when using
    `PlayerNotificationManager` and `DownloadNotificationUtil`. For the latter,
    applications should switch to using `DownloadNotificationHelper`.
  * Fix accuracy of D-pad seeking in `DefaultTimeBar`
    ([#5767](https://github.com/google/ExoPlayer/issues/5767)).
* Audio:
  * Allow `AudioProcessor`s to be drained of pending output after they are
    reconfigured.
  * Fix an issue that caused audio to be truncated at the end of a period
    when switching to a new period where gapless playback information was newly
    present or newly absent.
  * Add support for reading AC-4 streams
    ([#5303](https://github.com/google/ExoPlayer/pull/5303)).
* Video:
  * Remove `MediaCodecSelector.DEFAULT_WITH_FALLBACK`. Apps should instead
    signal that fallback should be used by passing `true` as the
    `enableDecoderFallback` parameter when instantiating the video renderer.
  * Support video tunneling when the decoder is not listed first for the MIME
    type ([#3100](https://github.com/google/ExoPlayer/issues/3100)).
  * Query `MediaCodecList.ALL_CODECS` when selecting a tunneling decoder
    ([#5547](https://github.com/google/ExoPlayer/issues/5547)).
* DRM:
  * Fix black flicker when keys rotate in DRM protected content
    ([#3561](https://github.com/google/ExoPlayer/issues/3561)).
  * Work around lack of LA_URL attribute in PlayReady key request init data.
* CEA-608: Improved conformance to the specification
  ([#3860](https://github.com/google/ExoPlayer/issues/3860)).
* DASH:
  * Parse role and accessibility descriptors into `Format.roleFlags`.
  * Support multiple CEA-608 channels muxed into FMP4 representations
    ([#5656](https://github.com/google/ExoPlayer/issues/5656)).
* HLS:
  * Prevent unnecessary reloads of initialization segments.
  * Form an adaptive track group out of audio renditions with matching name.
  * Support encrypted initialization segments
    ([#5441](https://github.com/google/ExoPlayer/issues/5441)).
  * Parse `EXT-X-MEDIA` `CHARACTERISTICS` attribute into `Format.roleFlags`.
  * Add metadata entry for HLS tracks to expose master playlist information.
  * Prevent `IndexOutOfBoundsException` in some live HLS scenarios
    ([#5816](https://github.com/google/ExoPlayer/issues/5816)).
* Support for playing spherical videos on Daydream.
* Cast extension: Work around Cast framework returning a limited-size queue
  items list ([#4964](https://github.com/google/ExoPlayer/issues/4964)).
* VP9 extension: Remove RGB output mode and libyuv dependency, and switch to
  surface YUV output as the default. Remove constructor parameters `scaleToFit`
  and `useSurfaceYuvOutput`.
* MediaSession extension:
  * Let apps intercept media button events
    ([#5179](https://github.com/google/ExoPlayer/issues/5179)).
  * Fix issue with `TimelineQueueNavigator` not publishing the queue in shuffled
    order when in shuffle mode.
  * Allow handling of custom commands via `registerCustomCommandReceiver`.
  * Add ability to include an extras `Bundle` when reporting a custom error.
* Log warnings when extension native libraries can't be used, to help with
  diagnosing playback failures
  ([#5788](https://github.com/google/ExoPlayer/issues/5788)).

### 2.9.6 (2019-02-19) ###

* Remove `player` and `isTopLevelSource` parameters from `MediaSource.prepare`.
* IMA extension:
  * Require setting the `Player` on `AdsLoader` instances before
    playback.
  * Remove deprecated `ImaAdsMediaSource`. Create `AdsMediaSource` with an
    `ImaAdsLoader` instead.
  * Remove deprecated `AdsMediaSource` constructors. Listen for media source
    events using `AdsMediaSource.addEventListener`, and ad interaction events by
    adding a listener when building `ImaAdsLoader`.
  * Allow apps to register playback-related obstructing views that are on top of
    their ad display containers via `AdsLoader.AdViewProvider`. `PlayerView`
    implements this interface and will register its control view. This makes it
    possible for ad loading SDKs to calculate ad viewability accurately.
* DASH: Fix issue handling large `EventStream` presentation timestamps
  ([#5490](https://github.com/google/ExoPlayer/issues/5490)).
* HLS: Fix transition to STATE_ENDED when playing fragmented mp4 in chunkless
  preparation ([#5524](https://github.com/google/ExoPlayer/issues/5524)).
* Revert workaround for video quality problems with Amlogic decoders, as this
  may cause problems for some devices and/or non-interlaced content
  ([#5003](https://github.com/google/ExoPlayer/issues/5003)).

### 2.9.5 (2019-01-31) ###

* HLS: Parse `CHANNELS` attribute from `EXT-X-MEDIA` tag.
* ConcatenatingMediaSource:
  * Add `Handler` parameter to methods that take a callback `Runnable`.
  * Fix issue with dropped messages when releasing the source
    ([#5464](https://github.com/google/ExoPlayer/issues/5464)).
* ExtractorMediaSource: Fix issue that could cause the player to get stuck
  buffering at the end of the media.
* PlayerView: Fix issue preventing `OnClickListener` from receiving events
  ([#5433](https://github.com/google/ExoPlayer/issues/5433)).
* IMA extension: Upgrade IMA dependency to 3.10.6.
* Cronet extension: Upgrade Cronet dependency to 71.3578.98.
* OkHttp extension: Upgrade OkHttp dependency to 3.12.1.
* MP3: Wider fix for issue where streams would play twice on some Samsung
  devices ([#4519](https://github.com/google/ExoPlayer/issues/4519)).

### 2.9.4 (2019-01-15) ###

* IMA extension: Clear ads loader listeners on release
  ([#4114](https://github.com/google/ExoPlayer/issues/4114)).
* SmoothStreaming: Fix support for subtitles in DRM protected streams
  ([#5378](https://github.com/google/ExoPlayer/issues/5378)).
* FFmpeg extension: Treat invalid data errors as non-fatal to match the behavior
  of MediaCodec ([#5293](https://github.com/google/ExoPlayer/issues/5293)).
* GVR extension: upgrade GVR SDK dependency to 1.190.0.
* Associate fatal player errors of type SOURCE with the loading source in
  `AnalyticsListener.EventTime`
  ([#5407](https://github.com/google/ExoPlayer/issues/5407)).
* Add `startPositionUs` to `MediaSource.createPeriod`. This fixes an issue where
  using lazy preparation in `ConcatenatingMediaSource` with an
  `ExtractorMediaSource` overrides initial seek positions
  ([#5350](https://github.com/google/ExoPlayer/issues/5350)).
* Add subtext to the `MediaDescriptionAdapter` of the
  `PlayerNotificationManager`.
* Add workaround for video quality problems with Amlogic decoders
  ([#5003](https://github.com/google/ExoPlayer/issues/5003)).
* Fix issue where sending callbacks for playlist changes may cause problems
  because of parallel player access
  ([#5240](https://github.com/google/ExoPlayer/issues/5240)).
* Fix issue with reusing a `ClippingMediaSource` with an inner
  `ExtractorMediaSource` and a non-zero start position
  ([#5351](https://github.com/google/ExoPlayer/issues/5351)).
* Fix issue where uneven track durations in MP4 streams can cause OOM problems
  ([#3670](https://github.com/google/ExoPlayer/issues/3670)).

### 2.9.3 (2018-12-20) ###

* Captions: Support PNG subtitles in SMPTE-TT
  ([#1583](https://github.com/google/ExoPlayer/issues/1583)).
* MPEG-TS: Use random access indicators to minimize the need for
  `FLAG_ALLOW_NON_IDR_KEYFRAMES`.
* Downloading: Reduce time taken to remove downloads
  ([#5136](https://github.com/google/ExoPlayer/issues/5136)).
* MP3:
  * Use the true bitrate for constant-bitrate MP3 seeking.
  * Fix issue where streams would play twice on some Samsung devices
    ([#4519](https://github.com/google/ExoPlayer/issues/4519)).
* Fix regression where some audio formats were incorrectly marked as being
  unplayable due to under-reporting of platform decoder capabilities
  ([#5145](https://github.com/google/ExoPlayer/issues/5145)).
* Fix decode-only frame skipping on Nvidia Shield TV devices.
* Workaround for MiTV (dangal) issue when swapping output surface
  ([#5169](https://github.com/google/ExoPlayer/issues/5169)).

### 2.9.2 (2018-11-28) ###

* HLS:
  * Fix issue causing unnecessary media playlist requests when playing live
    streams ([#5059](https://github.com/google/ExoPlayer/issues/5059)).
  * Fix decoder re-instantiation issue for packed audio streams
    ([#5063](https://github.com/google/ExoPlayer/issues/5063)).
* MP4: Support Opus and FLAC in the MP4 container, and in DASH
  ([#4883](https://github.com/google/ExoPlayer/issues/4883)).
* DASH: Fix detecting the end of live events
  ([#4780](https://github.com/google/ExoPlayer/issues/4780)).
* Spherical video: Fall back to `TYPE_ROTATION_VECTOR` if
  `TYPE_GAME_ROTATION_VECTOR` is unavailable
  ([#5119](https://github.com/google/ExoPlayer/issues/5119)).
* Support seeking for a wider range of MPEG-TS streams
  ([#5097](https://github.com/google/ExoPlayer/issues/5097)).
* Include channel count in audio capabilities check
  ([#4690](https://github.com/google/ExoPlayer/issues/4690)).
* Fix issue with applying the `show_buffering` attribute in `PlayerView`
  ([#5139](https://github.com/google/ExoPlayer/issues/5139)).
* Fix issue where null `Metadata` was output when it failed to decode
  ([#5149](https://github.com/google/ExoPlayer/issues/5149)).
* Fix playback of some invalid but playable MP4 streams by replacing assertions
  with logged warnings in sample table parsing code
  ([#5162](https://github.com/google/ExoPlayer/issues/5162)).
* Fix UUID passed to `MediaCrypto` when using `C.CLEARKEY_UUID` before API 27.

### 2.9.1 (2018-11-01) ###

* Add convenience methods `Player.next`, `Player.previous`, `Player.hasNext`
  and `Player.hasPrevious`
  ([#4863](https://github.com/google/ExoPlayer/issues/4863)).
* Improve initial bandwidth meter estimates using the current country and
  network type.
* IMA extension:
  * For preroll to live stream transitions, project forward the loading position
    to avoid being behind the live window.
  * Let apps specify whether to focus the skip button on ATV
    ([#5019](https://github.com/google/ExoPlayer/issues/5019)).
* MP3:
  * Support seeking based on MLLT metadata
    ([#3241](https://github.com/google/ExoPlayer/issues/3241)).
  * Fix handling of streams with appended data
    ([#4954](https://github.com/google/ExoPlayer/issues/4954)).
* DASH: Parse ProgramInformation element if present in the manifest.
* HLS:
  * Add constructor to `DefaultHlsExtractorFactory` for adding TS payload
    reader factory flags
    ([#4861](https://github.com/google/ExoPlayer/issues/4861)).
  * Fix bug in segment sniffing
    ([#5039](https://github.com/google/ExoPlayer/issues/5039)).
* SubRip: Add support for alignment tags, and remove tags from the displayed
  captions ([#4306](https://github.com/google/ExoPlayer/issues/4306)).
* Fix issue with blind seeking to windows with non-zero offset in a
  `ConcatenatingMediaSource`
  ([#4873](https://github.com/google/ExoPlayer/issues/4873)).
* Fix logic for enabling next and previous actions in `TimelineQueueNavigator`
  ([#5065](https://github.com/google/ExoPlayer/issues/5065)).
* Fix issue where audio focus handling could not be disabled after enabling it
  ([#5055](https://github.com/google/ExoPlayer/issues/5055)).
* Fix issue where subtitles were positioned incorrectly if `SubtitleView` had a
  non-zero position offset to its parent
  ([#4788](https://github.com/google/ExoPlayer/issues/4788)).
* Fix issue where the buffered position was not updated correctly when
  transitioning between periods
  ([#4899](https://github.com/google/ExoPlayer/issues/4899)).
* Fix issue where a `NullPointerException` is thrown when removing an unprepared
  media source from a `ConcatenatingMediaSource` with the `useLazyPreparation`
  option enabled ([#4986](https://github.com/google/ExoPlayer/issues/4986)).
* Work around an issue where a non-empty end-of-stream audio buffer would be
  output with timestamp zero, causing the player position to jump backwards
  ([#5045](https://github.com/google/ExoPlayer/issues/5045)).
* Suppress a spurious assertion failure on some Samsung devices
  ([#4532](https://github.com/google/ExoPlayer/issues/4532)).
* Suppress spurious "references unknown class member" shrinking warning
  ([#4890](https://github.com/google/ExoPlayer/issues/4890)).
* Swap recommended order for google() and jcenter() in gradle config
  ([#4997](https://github.com/google/ExoPlayer/issues/4997)).

### 2.9.0 (2018-09-06) ###

* Turn on Java 8 compiler support for the ExoPlayer library. Apps may need to
  add `compileOptions { targetCompatibility JavaVersion.VERSION_1_8 }` to their
  gradle settings to ensure bytecode compatibility.
* Set `compileSdkVersion` and `targetSdkVersion` to 28.
* Support for automatic audio focus handling via
  `SimpleExoPlayer.setAudioAttributes`.
* Add `ExoPlayer.retry` convenience method.
* Add `AudioListener` for listening to changes in audio configuration during
  playback ([#3994](https://github.com/google/ExoPlayer/issues/3994)).
* Add `LoadErrorHandlingPolicy` to allow configuration of load error handling
  across `MediaSource` implementations
  ([#3370](https://github.com/google/ExoPlayer/issues/3370)).
* Allow passing a `Looper`, which specifies the thread that must be used to
  access the player, when instantiating player instances using
  `ExoPlayerFactory` ([#4278](https://github.com/google/ExoPlayer/issues/4278)).
* Allow setting log level for ExoPlayer logcat output
  ([#4665](https://github.com/google/ExoPlayer/issues/4665)).
* Simplify `BandwidthMeter` injection: The `BandwidthMeter` should now be
  passed directly to `ExoPlayerFactory`, instead of to `TrackSelection.Factory`
  and `DataSource.Factory`. The `BandwidthMeter` is passed to the components
  that need it internally. The `BandwidthMeter` may also be omitted, in which
  case a default instance will be used.
* Spherical video:
  * Support for spherical video by setting `surface_type="spherical_view"` on
    `PlayerView`.
  * Support for
    [VR180](https://github.com/google/spatial-media/blob/master/docs/vr180.md).
* HLS:
  * Support PlayReady.
  * Add container format sniffing
    ([#2025](https://github.com/google/ExoPlayer/issues/2025)).
  * Support alternative `EXT-X-KEY` tags.
  * Support `EXT-X-INDEPENDENT-SEGMENTS` in the master playlist.
  * Support variable substitution
    ([#4422](https://github.com/google/ExoPlayer/issues/4422)).
  * Fix the bitrate being unset on primary track sample formats
    ([#3297](https://github.com/google/ExoPlayer/issues/3297)).
  * Make `HlsMediaSource.Factory` take a factory of trackers instead of a
    tracker instance ([#4814](https://github.com/google/ExoPlayer/issues/4814)).
* DASH:
  * Support `messageData` attribute for in-manifest event streams.
  * Clip periods to their specified durations
    ([#4185](https://github.com/google/ExoPlayer/issues/4185)).
* Improve seeking support for progressive streams:
  * Support seeking in MPEG-TS
    ([#966](https://github.com/google/ExoPlayer/issues/966)).
  * Support seeking in MPEG-PS
    ([#4476](https://github.com/google/ExoPlayer/issues/4476)).
  * Support approximate seeking in ADTS using a constant bitrate assumption
    ([#4548](https://github.com/google/ExoPlayer/issues/4548)). The
    `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` flag must be set on the extractor to
    enable this functionality.
  * Support approximate seeking in AMR using a constant bitrate assumption.
    The `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` flag must be set on the extractor
    to enable this functionality.
  * Add `DefaultExtractorsFactory.setConstantBitrateSeekingEnabled` to enable
    approximate seeking using a constant bitrate assumption on all extractors
    that support it.
* Video:
  * Add callback to `VideoListener` to notify of surface size changes.
  * Improve performance when playing high frame-rate content, and when playing
    at greater than 1x speed
    ([#2777](https://github.com/google/ExoPlayer/issues/2777)).
  * Scale up the initial video decoder maximum input size so playlist
    transitions with small increases in maximum sample size do not require
    reinitialization ([#4510](https://github.com/google/ExoPlayer/issues/4510)).
  * Fix a bug where the player would not transition to the ended state when
    playing video in tunneled mode.
* Audio:
  * Support attaching auxiliary audio effects to the `AudioTrack` via
    `Player.setAuxEffectInfo` and `Player.clearAuxEffectInfo`.
  * Support seamless adaptation while playing xHE-AAC streams.
    ([#4360](https://github.com/google/ExoPlayer/issues/4360)).
  * Increase `AudioTrack` buffer sizes to the theoretical maximum required for
    each encoding for passthrough playbacks
    ([#3803](https://github.com/google/ExoPlayer/issues/3803)).
  * WAV: Fix issue where white noise would be output at the end of playback
    ([#4724](https://github.com/google/ExoPlayer/issues/4724)).
  * MP3: Fix issue where streams would play twice on the SM-T530
    ([#4519](https://github.com/google/ExoPlayer/issues/4519)).
* Analytics:
  * Add callbacks to `DefaultDrmSessionEventListener` and `AnalyticsListener` to
    be notified of acquired and released DRM sessions.
  * Add uri field to `LoadEventInfo` in `MediaSourceEventListener` and
    `AnalyticsListener` callbacks. This uri is the redirected uri if redirection
    occurred ([#2054](https://github.com/google/ExoPlayer/issues/2054)).
  * Add response headers field to `LoadEventInfo` in `MediaSourceEventListener`
    and `AnalyticsListener` callbacks
    ([#4361](https://github.com/google/ExoPlayer/issues/4361) and
    [#4615](https://github.com/google/ExoPlayer/issues/4615)).
* UI components:
  * Add option to `PlayerView` to show buffering view when playWhenReady is
    false ([#4304](https://github.com/google/ExoPlayer/issues/4304)).
  * Allow any `Drawable` to be used as `PlayerView` default artwork.
* ConcatenatingMediaSource:
  * Support lazy preparation of playlist media sources
    ([#3972](https://github.com/google/ExoPlayer/issues/3972)).
  * Support range removal with `removeMediaSourceRange` methods
    ([#4542](https://github.com/google/ExoPlayer/issues/4542)).
  * Support setting a new shuffle order with `setShuffleOrder`
    ([#4791](https://github.com/google/ExoPlayer/issues/4791)).
* MPEG-TS: Support CEA-608/708 in H262
  ([#2565](https://github.com/google/ExoPlayer/issues/2565)).
* Allow configuration of the back buffer in `DefaultLoadControl.Builder`
  ([#4857](https://github.com/google/ExoPlayer/issues/4857)).
* Allow apps to pass a `CacheKeyFactory` for setting custom cache keys when
  creating a `CacheDataSource`.
* Provide additional information for adaptive track selection.
  `TrackSelection.updateSelectedTrack` has two new parameters for the current
  queue of media chunks and iterators for information about upcoming chunks.
* Allow `MediaCodecSelector`s to return multiple compatible decoders for
  `MediaCodecRenderer`, and provide an (optional) `MediaCodecSelector` that
  falls back to less preferred decoders like `MediaCodec.createDecoderByType`
  ([#273](https://github.com/google/ExoPlayer/issues/273)).
* Enable gzip for requests made by `SingleSampleMediaSource`
  ([#4771](https://github.com/google/ExoPlayer/issues/4771)).
* Fix bug reporting buffered position for multi-period windows, and add
  convenience methods `Player.getTotalBufferedDuration` and
  `Player.getContentBufferedDuration`
  ([#4023](https://github.com/google/ExoPlayer/issues/4023)).
* Fix bug where transitions to clipped media sources would happen too early
  ([#4583](https://github.com/google/ExoPlayer/issues/4583)).
* Fix bugs reporting events for multi-period media sources
  ([#4492](https://github.com/google/ExoPlayer/issues/4492) and
  [#4634](https://github.com/google/ExoPlayer/issues/4634)).
* Fix issue where removing looping media from a playlist throws an exception
  ([#4871](https://github.com/google/ExoPlayer/issues/4871).
* Fix issue where the preferred audio or text track would not be selected if
  mapped onto a secondary renderer of the corresponding type
  ([#4711](http://github.com/google/ExoPlayer/issues/4711)).
* Fix issue where errors of upcoming playlist items are thrown too early
  ([#4661](https://github.com/google/ExoPlayer/issues/4661)).
* Allow edit lists which do not start with a sync sample.
  ([#4774](https://github.com/google/ExoPlayer/issues/4774)).
* Fix issue with audio discontinuities at period transitions, e.g. when
  looping ([#3829](https://github.com/google/ExoPlayer/issues/3829)).
* Fix issue where `player.getCurrentTag()` throws an `IndexOutOfBoundsException`
  ([#4822](https://github.com/google/ExoPlayer/issues/4822)).
* Fix bug preventing use of multiple key session support (`multiSession=true`)
  for non-Widevine `DefaultDrmSessionManager` instances
  ([#4834](https://github.com/google/ExoPlayer/issues/4834)).
* Fix issue where audio and video would desynchronize when playing
  concatenations of gapless content
  ([#4559](https://github.com/google/ExoPlayer/issues/4559)).
* IMA extension:
  * Refine the previous fix for empty ad groups to avoid discarding ad breaks
    unnecessarily ([#4030](https://github.com/google/ExoPlayer/issues/4030) and
    [#4280](https://github.com/google/ExoPlayer/issues/4280)).
  * Fix handling of empty postrolls
    ([#4681](https://github.com/google/ExoPlayer/issues/4681)).
  * Fix handling of postrolls with multiple ads
    ([#4710](https://github.com/google/ExoPlayer/issues/4710)).
* MediaSession extension:
  * Add `MediaSessionConnector.setCustomErrorMessage` to support setting custom
    error messages.
  * Add `MediaMetadataProvider` to support setting custom metadata
    ([#3497](https://github.com/google/ExoPlayer/issues/3497)).
* Cronet extension: Now distributed via jCenter.
* FFmpeg extension: Support mu-law and A-law PCM.

### 2.8.4 (2018-08-17) ###

* IMA extension: Improve handling of consecutive empty ad groups
  ([#4030](https://github.com/google/ExoPlayer/issues/4030)),
  ([#4280](https://github.com/google/ExoPlayer/issues/4280)).

### 2.8.3 (2018-07-23) ###

* IMA extension:
  * Fix behavior when creating/releasing the player then releasing
    `ImaAdsLoader` ([#3879](https://github.com/google/ExoPlayer/issues/3879)).
  * Add support for setting slots for companion ads.
* Captions:
  * TTML: Fix an issue with TTML using font size as % of cell resolution that
    makes `SubtitleView.setApplyEmbeddedFontSizes()` not work correctly.
    ([#4491](https://github.com/google/ExoPlayer/issues/4491)).
  * CEA-608: Improve handling of embedded styles
    ([#4321](https://github.com/google/ExoPlayer/issues/4321)).
* DASH:
  * Exclude text streams from duration calculations
    ([#4029](https://github.com/google/ExoPlayer/issues/4029)).
  * Fix freezing when playing multi-period manifests with `EventStream`s
    ([#4492](https://github.com/google/ExoPlayer/issues/4492)).
* DRM: Allow DrmInitData to carry a license server URL
  ([#3393](https://github.com/google/ExoPlayer/issues/3393)).
* MPEG-TS: Fix bug preventing SCTE-35 cues from being output
  ([#4573](https://github.com/google/ExoPlayer/issues/4573)).
* Expose all internal ID3 data stored in MP4 udta boxes, and switch from using
  CommentFrame to InternalFrame for frames with gapless metadata in MP4.
* Add `PlayerView.isControllerVisible`
  ([#4385](https://github.com/google/ExoPlayer/issues/4385)).
* Fix issue playing DRM protected streams on Asus Zenfone 2
  ([#4403](https://github.com/google/ExoPlayer/issues/4413)).
* Add support for multiple audio and video tracks in MPEG-PS streams
  ([#4406](https://github.com/google/ExoPlayer/issues/4406)).
* Add workaround for track index mismatches between trex and tkhd boxes in
  fragmented MP4 files
  ([#4477](https://github.com/google/ExoPlayer/issues/4477)).
* Add workaround for track index mismatches between tfhd and tkhd boxes in
  fragmented MP4 files
  ([#4083](https://github.com/google/ExoPlayer/issues/4083)).
* Ignore all MP4 edit lists if one edit list couldn't be handled
  ([#4348](https://github.com/google/ExoPlayer/issues/4348)).
* Fix issue when switching track selection from an embedded track to a primary
  track in DASH ([#4477](https://github.com/google/ExoPlayer/issues/4477)).
* Fix accessibility class name for `DefaultTimeBar`
  ([#4611](https://github.com/google/ExoPlayer/issues/4611)).
* Improved compatibility with FireOS devices.

### 2.8.2 (2018-06-06) ###

* IMA extension: Don't advertise support for video/mpeg ad media, as we don't
  have an extractor for this
  ([#4297](https://github.com/google/ExoPlayer/issues/4297)).
* DASH: Fix playback getting stuck when playing representations that have both
  sidx atoms and non-zero presentationTimeOffset values.
* HLS:
  * Allow injection of custom playlist trackers.
  * Fix adaptation in live playlists with EXT-X-PROGRAM-DATE-TIME tags.
* Mitigate memory leaks when `MediaSource` loads are slow to cancel
  ([#4249](https://github.com/google/ExoPlayer/issues/4249)).
* Fix inconsistent `Player.EventListener` invocations for recursive player state
  changes ([#4276](https://github.com/google/ExoPlayer/issues/4276)).
* Fix `MediaCodec.native_setSurface` crash on Moto C
  ([#4315](https://github.com/google/ExoPlayer/issues/4315)).
* Fix missing whitespace in CEA-608
  ([#3906](https://github.com/google/ExoPlayer/issues/3906)).
* Fix crash downloading HLS media playlists
  ([#4396](https://github.com/google/ExoPlayer/issues/4396)).
* Fix a bug where download cancellation was ignored
  ([#4403](https://github.com/google/ExoPlayer/issues/4403)).
* Set `METADATA_KEY_TITLE` on media descriptions
  ([#4292](https://github.com/google/ExoPlayer/issues/4292)).
* Allow apps to register custom MIME types
  ([#4264](https://github.com/google/ExoPlayer/issues/4264)).

### 2.8.1 (2018-05-22) ###

* HLS:
  * Fix playback of livestreams with EXT-X-PROGRAM-DATE-TIME tags
    ([#4239](https://github.com/google/ExoPlayer/issues/4239)).
  * Fix playback of clipped streams starting from non-keyframe positions
    ([#4241](https://github.com/google/ExoPlayer/issues/4241)).
* OkHttp extension: Fix to correctly include response headers in thrown
  `InvalidResponseCodeException`s.
* Add possibility to cancel `PlayerMessage`s.
* UI components:
  * Add `PlayerView.setKeepContentOnPlayerReset` to keep the currently displayed
    video frame or media artwork visible when the player is reset
    ([#2843](https://github.com/google/ExoPlayer/issues/2843)).
* Fix crash when switching surface on Moto E(4)
  ([#4134](https://github.com/google/ExoPlayer/issues/4134)).
* Fix a bug that could cause event listeners to be called with inconsistent
  information if an event listener interacted with the player
  ([#4262](https://github.com/google/ExoPlayer/issues/4262)).
* Audio:
  * Fix extraction of PCM in MP4/MOV
    ([#4228](https://github.com/google/ExoPlayer/issues/4228)).
  * FLAC: Supports seeking for FLAC files without SEEKTABLE
    ([#1808](https://github.com/google/ExoPlayer/issues/1808)).
* Captions:
  * TTML:
    * Fix a styling issue when there are multiple regions displayed at the same
      time that can make text size of each region much smaller than defined.
    * Fix an issue when the caption line has no text (empty line or only line
      break), and the line's background is still displayed.
    * Support TTML font size using % correctly (as percentage of document cell
      resolution).

### 2.8.0 (2018-05-03) ###

* Downloading:
  * Add `DownloadService`, `DownloadManager` and related classes
    ([#2643](https://github.com/google/ExoPlayer/issues/2643)). Information on
    using these components to download progressive formats can be found
    [here](https://medium.com/google-exoplayer/downloading-streams-6d259eec7f95).
    To see how to download DASH, HLS and SmoothStreaming media, take a look at
    the app.
  * Updated main demo app to support downloading DASH, HLS, SmoothStreaming and
    progressive media.
* MediaSources:
  * Allow reusing media sources after they have been released and
    also in parallel to allow adding them multiple times to a concatenation.
    ([#3498](https://github.com/google/ExoPlayer/issues/3498)).
  * Merged `DynamicConcatenatingMediaSource` into `ConcatenatingMediaSource` and
    deprecated `DynamicConcatenatingMediaSource`.
  * Allow clipping of child media sources where the period and window have a
    non-zero offset with `ClippingMediaSource`.
  * Allow adding and removing `MediaSourceEventListener`s to MediaSources after
    they have been created. Listening to events is now supported for all
    media sources including composite sources.
  * Added callbacks to `MediaSourceEventListener` to get notified when media
    periods are created, released and being read from.
  * Support live stream clipping with `ClippingMediaSource`.
  * Allow setting tags for all media sources in their factories. The tag of the
    current window can be retrieved with `Player.getCurrentTag`.
* UI components:
  * Add support for displaying error messages and a buffering spinner in
    `PlayerView`.
  * Add support for listening to `AspectRatioFrameLayout`'s aspect ratio update
    ([#3736](https://github.com/google/ExoPlayer/issues/3736)).
  * Add `PlayerNotificationManager` for displaying notifications reflecting the
    player state.
  * Add `TrackSelectionView` for selecting tracks with `DefaultTrackSelector`.
  * Add `TrackNameProvider` for converting track `Format`s to textual
    descriptions, and `DefaultTrackNameProvider` as a default implementation.
* Track selection:
  * Reworked `MappingTrackSelector` and `DefaultTrackSelector`.
  * `DefaultTrackSelector.Parameters` now implements `Parcelable`.
  * Added UI components for track selection (see above).
* Audio:
  * Support extracting data from AMR container formats, including both narrow
    and wide band ([#2527](https://github.com/google/ExoPlayer/issues/2527)).
  * FLAC:
    * Sniff FLAC files correctly if they have ID3 headers
      ([#4055](https://github.com/google/ExoPlayer/issues/4055)).
    * Supports FLAC files with high sample rate (176400 and 192000)
      ([#3769](https://github.com/google/ExoPlayer/issues/3769)).
  * Factor out `AudioTrack` position tracking from `DefaultAudioSink`.
  * Fix an issue where the playback position would pause just after playback
    begins, and poll the audio timestamp less frequently once it starts
    advancing ([#3841](https://github.com/google/ExoPlayer/issues/3841)).
  * Add an option to skip silent audio in `PlaybackParameters`
    ([#2635](https://github.com/google/ExoPlayer/issues/2635)).
  * Fix an issue where playback of TrueHD streams would get stuck after seeking
    due to not finding a syncframe
    ([#3845](https://github.com/google/ExoPlayer/issues/3845)).
  * Fix an issue with eac3-joc playback where a codec would fail to configure
    ([#4165](https://github.com/google/ExoPlayer/issues/4165)).
  * Handle non-empty end-of-stream buffers, to fix gapless playback of streams
    with encoder padding when the decoder returns a non-empty final buffer.
  * Allow trimming more than one sample when applying an elst audio edit via
    gapless playback info.
  * Allow overriding skipping/scaling with custom `AudioProcessor`s
    ([#3142](https://github.com/google/ExoPlayer/issues/3142)).
* Caching:
  * Add release method to the `Cache` interface, and prevent multiple instances
    of `SimpleCache` using the same folder at the same time.
  * Cache redirect URLs
    ([#2360](https://github.com/google/ExoPlayer/issues/2360)).
* DRM:
  * Allow multiple listeners for `DefaultDrmSessionManager`.
  * Pass `DrmSessionManager` to `ExoPlayerFactory` instead of `RendererFactory`.
  * Change minimum API requirement for CBC and pattern encryption from 24 to 25
    ([#4022](https://github.com/google/ExoPlayer/issues/4022)).
  * Fix handling of 307/308 redirects when making license requests
    ([#4108](https://github.com/google/ExoPlayer/issues/4108)).
* HLS:
  * Fix playlist loading error propagation when the current selection does
    not include all of the playlist's variants.
  * Fix SAMPLE-AES-CENC and SAMPLE-AES-CTR EXT-X-KEY methods
    ([#4145](https://github.com/google/ExoPlayer/issues/4145)).
  * Preeptively declare an ID3 track in chunkless preparation
    ([#4016](https://github.com/google/ExoPlayer/issues/4016)).
  * Add support for multiple #EXT-X-MAP tags in a media playlist
    ([#4164](https://github.com/google/ExoPlayer/issues/4182)).
  * Fix seeking in live streams
    ([#4187](https://github.com/google/ExoPlayer/issues/4187)).
* IMA extension:
  * Allow setting the ad media load timeout
    ([#3691](https://github.com/google/ExoPlayer/issues/3691)).
  * Expose ad load errors via `MediaSourceEventListener` on `AdsMediaSource`,
    and allow setting an ad event listener on `ImaAdsLoader`. Deprecate the
    `AdsMediaSource.EventListener`.
* Add `AnalyticsListener` interface which can be registered in
  `SimpleExoPlayer` to receive detailed metadata for each ExoPlayer event.
* Optimize seeking in FMP4 by enabling seeking to the nearest sync sample within
  a fragment. This benefits standalone FMP4 playbacks, DASH and SmoothStreaming.
* Updated default max buffer length in `DefaultLoadControl`.
* Fix ClearKey decryption error if the key contains a forward slash
  ([#4075](https://github.com/google/ExoPlayer/issues/4075)).
* Fix crash when switching surface on Huawei P9 Lite
  ([#4084](https://github.com/google/ExoPlayer/issues/4084)), and Philips QM163E
  ([#4104](https://github.com/google/ExoPlayer/issues/4104)).
* Support ZLIB compressed PGS subtitles.
* Added `getPlaybackError` to `Player` interface.
* Moved initial bitrate estimate from `AdaptiveTrackSelection` to
  `DefaultBandwidthMeter`.
* Removed default renderer time offset of 60000000 from internal player. The
  actual renderer timestamp offset can be obtained by listening to
  `BaseRenderer.onStreamChanged`.
* Added dependencies on checkerframework annotations for static code analysis.

### 2.7.3 (2018-04-04) ###

* Fix ProGuard configuration for Cast, IMA and OkHttp extensions.
* Update OkHttp extension to depend on OkHttp 3.10.0.

### 2.7.2 (2018-03-29) ###

* Gradle: Upgrade Gradle version from 4.1 to 4.4 so it can work with Android
  Studio 3.1 ([#3708](https://github.com/google/ExoPlayer/issues/3708)).
* Match codecs starting with "mp4a" to different Audio MimeTypes
  ([#3779](https://github.com/google/ExoPlayer/issues/3779)).
* Fix ANR issue on Redmi 4X and Redmi Note 4
  ([#4006](https://github.com/google/ExoPlayer/issues/4006)).
* Fix handling of zero padded strings when parsing Matroska streams
  ([#4010](https://github.com/google/ExoPlayer/issues/4010)).
* Fix "Decoder input buffer too small" error when playing some FLAC streams.
* MediaSession extension: Omit fast forward and rewind actions when media is not
  seekable ([#4001](https://github.com/google/ExoPlayer/issues/4001)).

### 2.7.1 (2018-03-09) ###

* Gradle: Replaced 'compile' (deprecated) with 'implementation' and
  'api'. This may lead to build breakage for applications upgrading from
  previous version that rely on indirect dependencies of certain modules. In
  such cases, application developers need to add the missing dependency to
  their gradle file. You can read more about the new dependency configurations
  [here](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#new_configurations).
* HlsMediaSource: Make HLS periods start at zero instead of the epoch.
  Applications that rely on HLS timelines having a period starting at
  the epoch will need to update their handling of HLS timelines. The program
  date time is still available via the informational
  `Timeline.Window.windowStartTimeMs` field
  ([#3865](https://github.com/google/ExoPlayer/issues/3865),
  [#3888](https://github.com/google/ExoPlayer/issues/3888)).
* Enable seeking in MP4 streams where duration is set incorrectly in the track
  header ([#3926](https://github.com/google/ExoPlayer/issues/3926)).
* Video: Force rendering a frame periodically in `MediaCodecVideoRenderer` and
  `LibvpxVideoRenderer`, even if it is late.

### 2.7.0 (2018-02-19) ###

* Player interface:
  * Add optional parameter to `stop` to reset the player when stopping.
  * Add a reason to `EventListener.onTimelineChanged` to distinguish between
    initial preparation, reset and dynamic updates.
  * Add `Player.DISCONTINUITY_REASON_AD_INSERTION` to the possible reasons
    reported in `Eventlistener.onPositionDiscontinuity` to distinguish
    transitions to and from ads within one period from transitions between
    periods.
  * Replaced `ExoPlayer.sendMessages` with `ExoPlayer.createMessage` to allow
    more customization of the message. Now supports setting a message delivery
    playback position and/or a delivery handler
    ([#2189](https://github.com/google/ExoPlayer/issues/2189)).
  * Add `Player.VideoComponent`, `Player.TextComponent` and
    `Player.MetadataComponent` interfaces that define optional video, text and
    metadata output functionality. New `getVideoComponent`, `getTextComponent`
    and `getMetadataComponent` methods provide access to this functionality.
* Add `ExoPlayer.setSeekParameters` for controlling how seek operations are
  performed. The `SeekParameters` class contains defaults for exact seeking and
  seeking to the closest sync points before, either side or after specified seek
  positions. `SeekParameters` are not currently supported when playing HLS
  streams.
* DefaultTrackSelector:
  * Replace `DefaultTrackSelector.Parameters` copy methods with a builder.
  * Support disabling of individual text track selection flags.
* Buffering:
  * Allow a back-buffer of media to be retained behind the current playback
    position, for fast backward seeking. The back-buffer can be configured by
    custom `LoadControl` implementations.
  * Add ability for `SequenceableLoader` to re-evaluate its buffer and discard
    buffered media so that it can be re-buffered in a different quality.
  * Allow more flexible loading strategy when playing media containing multiple
    sub-streams, by allowing injection of custom `CompositeSequenceableLoader`
    factories through `DashMediaSource.Factory`, `HlsMediaSource.Factory`,
    `SsMediaSource.Factory`, and `MergingMediaSource`.
  * Play out existing buffer before retrying for progressive live streams
    ([#1606](https://github.com/google/ExoPlayer/issues/1606)).
* UI components:
  * Generalized player and control views to allow them to bind with any
    `Player`, and renamed them to `PlayerView` and `PlayerControlView`
    respectively.
  * Made `PlayerView` automatically apply video rotation when configured to use
    `TextureView` ([#91](https://github.com/google/ExoPlayer/issues/91)).
  * Made `PlayerView` play button behave correctly when the player is ended
    ([#3689](https://github.com/google/ExoPlayer/issues/3689)), and call a
    `PlaybackPreparer` when the player is idle.
* DRM: Optimistically attempt playback of DRM protected content that does not
  declare scheme specific init data in the manifest. If playback of clear
  samples without keys is allowed, delay DRM session error propagation until
  keys are actually needed
  ([#3630](https://github.com/google/ExoPlayer/issues/3630)).
* DASH:
  * Support in-band Emsg events targeting the player with scheme id
    `urn:mpeg:dash:event:2012` and scheme values "1", "2" and "3".
  * Support EventStream elements in DASH manifests.
* HLS:
    * Add opt-in support for chunkless preparation in HLS. This allows an
      HLS source to finish preparation without downloading any chunks, which can
      significantly reduce initial buffering time
      ([#3149](https://github.com/google/ExoPlayer/issues/3149)). More details
      can be found
      [here](https://medium.com/google-exoplayer/faster-hls-preparation-f6611aa15ea6).
    * Fail if unable to sync with the Transport Stream, rather than entering
      stuck in an indefinite buffering state.
    * Fix mime type propagation
      ([#3653](https://github.com/google/ExoPlayer/issues/3653)).
    * Fix ID3 context reuse across segment format changes
      ([#3622](https://github.com/google/ExoPlayer/issues/3622)).
    * Use long for media sequence numbers
      ([#3747](https://github.com/google/ExoPlayer/issues/3747))
    * Add initial support for the EXT-X-GAP tag.
* Audio:
  * Support TrueHD passthrough for rechunked samples in Matroska files
    ([#2147](https://github.com/google/ExoPlayer/issues/2147)).
  * Support resampling 24-bit and 32-bit integer to 32-bit float for high
    resolution output in `DefaultAudioSink`
    ([#3635](https://github.com/google/ExoPlayer/pull/3635)).
* Captions:
  * Basic support for PGS subtitles
    ([#3008](https://github.com/google/ExoPlayer/issues/3008)).
  * Fix handling of CEA-608 captions where multiple buffers have the same
    presentation timestamp
    ([#3782](https://github.com/google/ExoPlayer/issues/3782)).
* Caching:
  * Fix cache corruption issue
    ([#3762](https://github.com/google/ExoPlayer/issues/3762)).
  * Implement periodic check in `CacheDataSource` to see whether it's possible
    to switch to reading/writing the cache having initially bypassed it.
* IMA extension:
    * Fix the player getting stuck when an ad group fails to load
      ([#3584](https://github.com/google/ExoPlayer/issues/3584)).
    * Work around loadAd not being called beore the LOADED AdEvent arrives
      ([#3552](https://github.com/google/ExoPlayer/issues/3552)).
    * Handle asset mismatch errors
      ([#3801](https://github.com/google/ExoPlayer/issues/3801)).
    * Add support for playing non-Extractor content MediaSources in
      the IMA demo app
      ([#3676](https://github.com/google/ExoPlayer/issues/3676)).
    * Fix handling of ad tags where ad groups are out of order
      ([#3716](https://github.com/google/ExoPlayer/issues/3716)).
    * Fix handling of ad tags with only preroll/postroll ad groups
      ([#3715](https://github.com/google/ExoPlayer/issues/3715)).
    * Propagate ad media preparation errors to IMA so that the ads can be
      skipped.
    * Handle exceptions in IMA callbacks so that can be logged less verbosely.
* New Cast extension. Simplifies toggling between local and Cast playbacks.
* `EventLogger` moved from the demo app into the core library.
* Fix ANR issue on the Huawei P8 Lite, Huawei Y6II, Moto C+, Meizu M5C,
  Lenovo K4 Note and Sony Xperia E5.
  ([#3724](https://github.com/google/ExoPlayer/issues/3724),
  [#3835](https://github.com/google/ExoPlayer/issues/3835)).
* Fix potential NPE when removing media sources from a
  DynamicConcatenatingMediaSource
  ([#3796](https://github.com/google/ExoPlayer/issues/3796)).
* Check `sys.display-size` on Philips ATVs
  ([#3807](https://github.com/google/ExoPlayer/issues/3807)).
* Release `Extractor`s on the loading thread to avoid potentially leaking
  resources when the playback thread has quit by the time the loading task has
  completed.
* ID3: Better handle malformed ID3 data
  ([#3792](https://github.com/google/ExoPlayer/issues/3792).
* Support 14-bit mode and little endianness in DTS PES packets
  ([#3340](https://github.com/google/ExoPlayer/issues/3340)).
* Demo app: Add ability to download not DRM protected content.

### 2.6.1 (2017-12-15) ###

* Add factories to `ExtractorMediaSource`, `HlsMediaSource`, `SsMediaSource`,
  `DashMediaSource` and `SingleSampleMediaSource`.
* Use the same listener `MediaSourceEventListener` for all MediaSource
  implementations.
* IMA extension:
  * Support non-ExtractorMediaSource ads
    ([#3302](https://github.com/google/ExoPlayer/issues/3302)).
  * Skip ads before the ad preceding the player's initial seek position
    ([#3527](https://github.com/google/ExoPlayer/issues/3527)).
  * Fix ad loading when there is no preroll.
  * Add an option to turn off hiding controls during ad playback
    ([#3532](https://github.com/google/ExoPlayer/issues/3532)).
  * Support specifying an ads response instead of an ad tag
    ([#3548](https://github.com/google/ExoPlayer/issues/3548)).
  * Support overriding the ad load timeout
    ([#3556](https://github.com/google/ExoPlayer/issues/3556)).
* DASH: Support time zone designators in ISO8601 UTCTiming elements
  ([#3524](https://github.com/google/ExoPlayer/issues/3524)).
* Audio:
  * Support 32-bit PCM float output from `DefaultAudioSink`, and add an option
    to use this with `FfmpegAudioRenderer`.
  * Add support for extracting 32-bit WAVE files
    ([#3379](https://github.com/google/ExoPlayer/issues/3379)).
  * Support extraction and decoding of Dolby Atmos
    ([#2465](https://github.com/google/ExoPlayer/issues/2465)).
  * Fix handling of playback parameter changes while paused when followed by a
    seek.
* SimpleExoPlayer: Allow multiple audio and video debug listeners.
* DefaultTrackSelector: Support undefined language text track selection when the
  preferred language is not available
  ([#2980](https://github.com/google/ExoPlayer/issues/2980)).
* Add options to `DefaultLoadControl` to set maximum buffer size in bytes and
  to choose whether size or time constraints are prioritized.
* Use surfaceless context for secure `DummySurface`, if available
  ([#3558](https://github.com/google/ExoPlayer/issues/3558)).
* FLV: Fix playback of live streams that do not contain an audio track
  ([#3188](https://github.com/google/ExoPlayer/issues/3188)).
* CEA-608: Fix handling of row count changes in roll-up mode
  ([#3513](https://github.com/google/ExoPlayer/issues/3513)).
* Prevent period transitions when seeking to the end of a period when paused
  ([#2439](https://github.com/google/ExoPlayer/issues/2439)).

### 2.6.0 (2017-11-03) ###

* Removed "r" prefix from versions. This release is "2.6.0", not "r2.6.0".
* New `Player.DefaultEventListener` abstract class can be extended to avoid
  having to implement all methods defined by `Player.EventListener`.
* Added a reason to `EventListener.onPositionDiscontinuity`
  ([#3252](https://github.com/google/ExoPlayer/issues/3252)).
* New `setShuffleModeEnabled` method for enabling shuffled playback.
* SimpleExoPlayer: Support for multiple video, text and metadata outputs.
* Support for `Renderer`s that don't consume any media
  ([#3212](https://github.com/google/ExoPlayer/issues/3212)).
* Fix reporting of internal position discontinuities via
  `Player.onPositionDiscontinuity`. `DISCONTINUITY_REASON_SEEK_ADJUSTMENT` is
  added to disambiguate position adjustments during seeks from other types of
  internal position discontinuity.
* Fix potential `IndexOutOfBoundsException` when calling `ExoPlayer.getDuration`
  ([#3362](https://github.com/google/ExoPlayer/issues/3362)).
* Fix playbacks involving looping, concatenation and ads getting stuck when
  media contains tracks with uneven durations
  ([#1874](https://github.com/google/ExoPlayer/issues/1874)).
* Fix issue with `ContentDataSource` when reading from certain `ContentProvider`
  implementations ([#3426](https://github.com/google/ExoPlayer/issues/3426)).
* Better playback experience when the video decoder cannot keep up, by skipping
  to key-frames. This is particularly relevant for variable speed playbacks.
* Allow `SingleSampleMediaSource` to suppress load errors
  ([#3140](https://github.com/google/ExoPlayer/issues/3140)).
* `DynamicConcatenatingMediaSource`: Allow specifying a callback to be invoked
  after a dynamic playlist modification has been applied
  ([#3407](https://github.com/google/ExoPlayer/issues/3407)).
* Audio: New `AudioSink` interface allows customization of audio output path.
* Offline: Added `Downloader` implementations for DASH, HLS, SmoothStreaming
  and progressive streams.
* Track selection:
  * Fixed adaptive track selection logic for live playbacks
    ([#3017](https://github.com/google/ExoPlayer/issues/3017)).
  * Added ability to select the lowest bitrate tracks.
* DASH:
  * Don't crash when a malformed or unexpected manifest update occurs
    ([#2795](https://github.com/google/ExoPlayer/issues/2795)).
* HLS:
  * Support for Widevine protected FMP4 variants.
  * Support CEA-608 in FMP4 variants.
  * Support extractor injection
    ([#2748](https://github.com/google/ExoPlayer/issues/2748)).
* DRM:
  * Improved compatibility with ClearKey content
    ([#3138](https://github.com/google/ExoPlayer/issues/3138)).
  * Support multiple PSSH boxes of the same type.
  * Retry initial provisioning and key requests if they fail
  * Fix incorrect parsing of non-CENC sinf boxes.
* IMA extension:
  * Expose `AdsLoader` via getter
    ([#3322](https://github.com/google/ExoPlayer/issues/3322)).
  * Handle `setPlayWhenReady` calls during ad playbacks
    ([#3303](https://github.com/google/ExoPlayer/issues/3303)).
  * Ignore seeks if an ad is playing
    ([#3309](https://github.com/google/ExoPlayer/issues/3309)).
  * Improve robustness of `ImaAdsLoader` in case content is not paused between
    content to ad transitions
    ([#3430](https://github.com/google/ExoPlayer/issues/3430)).
* UI:
  * Allow specifying a `Drawable` for the `TimeBar` scrubber
    ([#3337](https://github.com/google/ExoPlayer/issues/3337)).
  * Allow multiple listeners on `TimeBar`
    ([#3406](https://github.com/google/ExoPlayer/issues/3406)).
* New Leanback extension: Simplifies binding Exoplayer to Leanback UI
  components.
* Unit tests moved to Robolectric.
* Misc bugfixes.

### r2.5.4 (2017-10-19) ###

* Remove unnecessary media playlist fetches during playback of live HLS streams.
* Add the ability to inject a HLS playlist parser through `HlsMediaSource`.
* Fix potential `IndexOutOfBoundsException` when using `ImaMediaSource`
  ([#3334](https://github.com/google/ExoPlayer/issues/3334)).
* Fix an issue parsing MP4 content containing non-CENC sinf boxes.
* Fix memory leak when seeking with repeated periods.
* Fix playback position when `ExoPlayer.prepare` is called with `resetPosition`
  set to false.
* Ignore MP4 edit lists that seem invalid
  ([#3351](https://github.com/google/ExoPlayer/issues/3351)).
* Add extractor flag for ignoring all MP4 edit lists
  ([#3358](https://github.com/google/ExoPlayer/issues/3358)).
* Improve extensibility by exposing public constructors for
  `FrameworkMediaCrypto` and by making `DefaultDashChunkSource.getNextChunk`
  non-final.

### r2.5.3 (2017-09-20) ###

* IMA extension: Support skipping of skippable ads on AndroidTV and other
  non-touch devices ([#3258](https://github.com/google/ExoPlayer/issues/3258)).
* HLS: Fix broken WebVTT captions when PTS wraps around
  ([#2928](https://github.com/google/ExoPlayer/issues/2928)).
* Captions: Fix issues rendering CEA-608 captions
  ([#3250](https://github.com/google/ExoPlayer/issues/3250)).
* Workaround broken AAC decoders on Galaxy S6
  ([#3249](https://github.com/google/ExoPlayer/issues/3249)).
* Caching: Fix infinite loop when cache eviction fails
  ([#3260](https://github.com/google/ExoPlayer/issues/3260)).
* Caching: Force use of BouncyCastle on JellyBean to fix decryption issue
  ([#2755](https://github.com/google/ExoPlayer/issues/2755)).

### r2.5.2 (2017-09-11) ###

* IMA extension: Fix issue where ad playback could end prematurely for some
  content types ([#3180](https://github.com/google/ExoPlayer/issues/3180)).
* RTMP extension: Fix SIGABRT on fast RTMP stream restart
  ([#3156](https://github.com/google/ExoPlayer/issues/3156)).
* UI: Allow app to manually specify ad markers
  ([#3184](https://github.com/google/ExoPlayer/issues/3184)).
* DASH: Expose segment indices to subclasses of DefaultDashChunkSource
  ([#3037](https://github.com/google/ExoPlayer/issues/3037)).
* Captions: Added robustness against malformed WebVTT captions
  ([#3228](https://github.com/google/ExoPlayer/issues/3228)).
* DRM: Support forcing a specific license URL.
* Fix playback error when seeking in media loaded through content:// URIs
  ([#3216](https://github.com/google/ExoPlayer/issues/3216)).
* Fix issue playing MP4s in which the last atom specifies a size of zero
  ([#3191](https://github.com/google/ExoPlayer/issues/3191)).
* Workaround playback failures on some Xiaomi devices
  ([#3171](https://github.com/google/ExoPlayer/issues/3171)).
* Workaround SIGSEGV issue on some devices when setting and swapping surface for
  secure playbacks ([#3215](https://github.com/google/ExoPlayer/issues/3215)).
* Workaround for Nexus 7 issue when swapping output surface
  ([#3236](https://github.com/google/ExoPlayer/issues/3236)).
* Workaround for SimpleExoPlayerView's surface not being hidden properly
  ([#3160](https://github.com/google/ExoPlayer/issues/3160)).

### r2.5.1 (2017-08-08) ###

* Fix an issue that could cause the reported playback position to stop advancing
  in some cases.
* Fix an issue where a Surface could be released whilst still in use by the
  player.

### r2.5.0 (2017-08-07) ###

* IMA extension: Wraps the Google Interactive Media Ads (IMA) SDK to provide an
  easy and seamless way of incorporating display ads into ExoPlayer playbacks.
  You can read more about the IMA extension
  [here](https://medium.com/google-exoplayer/playing-ads-with-exoplayer-and-ima-868dfd767ea).
* MediaSession extension: Provides an easy way to connect ExoPlayer with
  MediaSessionCompat in the Android Support Library.
* RTMP extension: An extension for playing streams over RTMP.
* Build: Made it easier for application developers to depend on a local checkout
  of ExoPlayer. You can learn how to do this
  [here](https://medium.com/google-exoplayer/howto-2-depend-on-a-local-checkout-of-exoplayer-bcd7f8531720).
* Core playback improvements:
  * Eliminated re-buffering when changing audio and text track selections during
    playback of progressive streams
    ([#2926](https://github.com/google/ExoPlayer/issues/2926)).
  * New DynamicConcatenatingMediaSource class to support playback of dynamic
    playlists.
  * New ExoPlayer.setRepeatMode method for dynamic toggling of repeat mode
    during playback. Use of setRepeatMode should be preferred to
    LoopingMediaSource for most looping use cases. You can read more about
    setRepeatMode
    [here](https://medium.com/google-exoplayer/repeat-modes-in-exoplayer-19dd85f036d3).
  * Eliminated jank when switching video playback from one Surface to another on
    API level 23+ for unencrypted content, and on devices that support the
    EGL_EXT_protected_content OpenGL extension for protected content
    ([#677](https://github.com/google/ExoPlayer/issues/677)).
  * Enabled ExoPlayer instantiation on background threads without Loopers.
    Events from such players are delivered on the application's main thread.
* HLS improvements:
  * Optimized adaptive switches for playlists that specify the
    EXT-X-INDEPENDENT-SEGMENTS tag.
  * Optimized in-buffer seeking
    ([#551](https://github.com/google/ExoPlayer/issues/551)).
  * Eliminated re-buffering when changing audio and text track selections during
    playback, provided the new selection does not require switching to different
    renditions ([#2718](https://github.com/google/ExoPlayer/issues/2718)).
  * Exposed all media playlist tags in ExoPlayer's MediaPlaylist object.
* DASH: Support for seamless switching across streams in different AdaptationSet
  elements ([#2431](https://github.com/google/ExoPlayer/issues/2431)).
* DRM: Support for additional crypto schemes (cbc1, cbcs and cens) on
  API level 24+ ([#1989](https://github.com/google/ExoPlayer/issues/1989)).
* Captions: Initial support for SSA/ASS subtitles
  ([#889](https://github.com/google/ExoPlayer/issues/889)).
* AndroidTV: Fixed issue where tunneled video playback would not start on some
  devices ([#2985](https://github.com/google/ExoPlayer/issues/2985)).
* MPEG-TS: Fixed segmentation issue when parsing H262
  ([#2891](https://github.com/google/ExoPlayer/issues/2891)).
* Cronet extension: Support for a user-defined fallback if Cronet library is not
  present.
* Fix buffer too small IllegalStateException issue affecting some composite
  media playbacks ([#2900](https://github.com/google/ExoPlayer/issues/2900)).
* Misc bugfixes.

### r2.4.4 (2017-07-19) ###

* HLS/MPEG-TS: Some initial optimizations of MPEG-TS extractor performance
  ([#3040](https://github.com/google/ExoPlayer/issues/3040)).
* HLS: Fix propagation of format identifier for CEA-608
  ([#3033](https://github.com/google/ExoPlayer/issues/3033)).
* HLS: Detect playlist stuck and reset conditions
  ([#2872](https://github.com/google/ExoPlayer/issues/2872)).
* Video: Fix video dimension reporting on some devices
  ([#3007](https://github.com/google/ExoPlayer/issues/3007)).

### r2.4.3 (2017-06-30) ###

* Audio: Workaround custom audio decoders misreporting their maximum supported
  channel counts ([#2940](https://github.com/google/ExoPlayer/issues/2940)).
* Audio: Workaround for broken MediaTek raw decoder on some devices
  ([#2873](https://github.com/google/ExoPlayer/issues/2873)).
* Captions: Fix TTML captions appearing at the top of the screen
  ([#2953](https://github.com/google/ExoPlayer/issues/2953)).
* Captions: Fix handling of some DVB subtitles
  ([#2957](https://github.com/google/ExoPlayer/issues/2957)).
* Track selection: Fix setSelectionOverride(index, tracks, null)
  ([#2988](https://github.com/google/ExoPlayer/issues/2988)).
* GVR extension: Add support for mono input
  ([#2710](https://github.com/google/ExoPlayer/issues/2710)).
* FLAC extension: Fix failing build
  ([#2977](https://github.com/google/ExoPlayer/pull/2977)).
* Misc bugfixes.

### r2.4.2 (2017-06-06) ###

* Stability: Work around Nexus 10 reboot when playing certain content
  ([#2806](https://github.com/google/ExoPlayer/issues/2806)).
* MP3: Correctly treat MP3s with INFO headers as constant bitrate
  ([#2895](https://github.com/google/ExoPlayer/issues/2895)).
* HLS: Use average rather than peak bandwidth when available
  ([#2863](https://github.com/google/ExoPlayer/issues/2863)).
* SmoothStreaming: Fix timeline for live streams
  ([#2760](https://github.com/google/ExoPlayer/issues/2760)).
* UI: Fix DefaultTimeBar invalidation
  ([#2871](https://github.com/google/ExoPlayer/issues/2871)).
* Misc bugfixes.

### r2.4.1 (2017-05-23) ###

* Stability: Avoid OutOfMemoryError in extractors when parsing malformed media
  ([#2780](https://github.com/google/ExoPlayer/issues/2780)).
* Stability: Avoid native crash on Galaxy Nexus. Avoid unnecessarily large codec
  input buffer allocations on all devices
  ([#2607](https://github.com/google/ExoPlayer/issues/2607)).
* Variable speed playback: Fix interpolation for rate/pitch adjustment
  ([#2774](https://github.com/google/ExoPlayer/issues/2774)).
* HLS: Include EXT-X-DATERANGE tags in HlsMediaPlaylist.
* HLS: Don't expose CEA-608 track if CLOSED-CAPTIONS=NONE
  ([#2743](https://github.com/google/ExoPlayer/issues/2743)).
* HLS: Correctly propagate errors loading the media playlist
  ([#2623](https://github.com/google/ExoPlayer/issues/2623)).
* UI: DefaultTimeBar enhancements and bug fixes
  ([#2740](https://github.com/google/ExoPlayer/issues/2740)).
* Ogg: Fix failure to play some Ogg files
  ([#2782](https://github.com/google/ExoPlayer/issues/2782)).
* Captions: Don't select text tack with no language by default.
* Captions: TTML positioning fixes
  ([#2824](https://github.com/google/ExoPlayer/issues/2824)).
* Misc bugfixes.

### r2.4.0 (2017-04-25) ###

* New modular library structure. You can read more about depending on individual
  library modules
  [here](https://medium.com/google-exoplayer/exoplayers-new-modular-structure-a916c0874907).
* Variable speed playback support on API level 16+. You can read more about
  changing the playback speed
  [here](https://medium.com/google-exoplayer/variable-speed-playback-with-exoplayer-e6e6a71e0343)
  ([#26](https://github.com/google/ExoPlayer/issues/26)).
* New time bar view, including support for displaying ad break markers.
* Support DVB subtitles in MPEG-TS and MKV.
* Support adaptive playback for audio only DASH, HLS and SmoothStreaming
  ([#1975](https://github.com/google/ExoPlayer/issues/1975)).
* Support for setting extractor flags on DefaultExtractorsFactory
  ([#2657](https://github.com/google/ExoPlayer/issues/2657)).
* Support injecting custom renderers into SimpleExoPlayer using a new
  RenderersFactory interface.
* Correctly set ExoPlayer's internal thread priority to `THREAD_PRIORITY_AUDIO`.
* TX3G: Support styling and positioning.
* FLV:
  * Support MP3 in FLV.
  * Skip unhandled metadata rather than failing
    ([#2634](https://github.com/google/ExoPlayer/issues/2634)).
  * Fix potential OutOfMemory errors.
* ID3: Better handle malformed ID3 data
  ([#2604](https://github.com/google/ExoPlayer/issues/2604),
  [#2663](https://github.com/google/ExoPlayer/issues/2663)).
* FFmpeg extension: Fixed build instructions
  ([#2561](https://github.com/google/ExoPlayer/issues/2561)).
* VP9 extension: Reduced binary size.
* FLAC extension: Enabled 64 bit targets.
* Misc bugfixes.

### r2.3.1 (2017-03-23) ###

* Fix NPE enabling WebVTT subtitles in DASH streams
  ([#2596](https://github.com/google/ExoPlayer/issues/2596)).
* Fix skipping to keyframes when MediaCodecVideoRenderer is enabled but without
  a Surface ([#2575](https://github.com/google/ExoPlayer/issues/2575)).
* Minor fix for CEA-708 decoder
  ([#2595](https://github.com/google/ExoPlayer/issues/2595)).

### r2.3.0 (2017-03-16) ###

* GVR extension: Wraps the Google VR Audio SDK to provide spatial audio
  rendering. You can read more about the GVR extension
  [here](https://medium.com/google-exoplayer/spatial-audio-with-exoplayer-and-gvr-cecb00e9da5f#.xdjebjd7g).
* DASH improvements:
  * Support embedded CEA-608 closed captions
    ([#2362](https://github.com/google/ExoPlayer/issues/2362)).
  * Support embedded EMSG events
    ([#2176](https://github.com/google/ExoPlayer/issues/2176)).
  * Support mspr:pro manifest element
    ([#2386](https://github.com/google/ExoPlayer/issues/2386)).
  * Correct handling of empty segment indices at the start of live events
    ([#1865](https://github.com/google/ExoPlayer/issues/1865)).
* HLS improvements:
  * Respect initial track selection
    ([#2353](https://github.com/google/ExoPlayer/issues/2353)).
  * Reduced frequency of media playlist requests when playback position is close
    to the live edge ([#2548](https://github.com/google/ExoPlayer/issues/2548)).
  * Exposed the master playlist through ExoPlayer.getCurrentManifest()
    ([#2537](https://github.com/google/ExoPlayer/issues/2537)).
  * Support CLOSED-CAPTIONS #EXT-X-MEDIA type
    ([#341](https://github.com/google/ExoPlayer/issues/341)).
  * Fixed handling of negative values in #EXT-X-SUPPORT
    ([#2495](https://github.com/google/ExoPlayer/issues/2495)).
  * Fixed potential endless buffering state for streams with WebVTT subtitles
    ([#2424](https://github.com/google/ExoPlayer/issues/2424)).
* MPEG-TS improvements:
  * Support for multiple programs.
  * Support for multiple closed captions and caption service descriptors
   ([#2161](https://github.com/google/ExoPlayer/issues/2161)).
* MP3: Add `FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` extractor option to enable
  constant bitrate seeking in MP3 files that would otherwise be unseekable
  ([#2445](https://github.com/google/ExoPlayer/issues/2445)).
* ID3: Better handle malformed ID3 data
  ([#2486](https://github.com/google/ExoPlayer/issues/2486)).
* Track selection: Added maxVideoBitrate parameter to DefaultTrackSelector.
* DRM: Add support for CENC ClearKey on API level 21+
  ([#2361](https://github.com/google/ExoPlayer/issues/2361)).
* DRM: Support dynamic setting of key request headers
  ([#1924](https://github.com/google/ExoPlayer/issues/1924)).
* SmoothStreaming: Fixed handling of start_time placeholder
  ([#2447](https://github.com/google/ExoPlayer/issues/2447)).
* FLAC extension: Fix proguard configuration
  ([#2427](https://github.com/google/ExoPlayer/issues/2427)).
* Misc bugfixes.

### r2.2.0 (2017-01-30) ###

* Demo app: Automatic recovery from BehindLiveWindowException, plus improved
  handling of pausing and resuming live streams
  ([#2344](https://github.com/google/ExoPlayer/issues/2344)).
* AndroidTV: Added Support for tunneled video playback
  ([#1688](https://github.com/google/ExoPlayer/issues/1688)).
* DRM: Renamed StreamingDrmSessionManager to DefaultDrmSessionManager and
  added support for using offline licenses
  ([#876](https://github.com/google/ExoPlayer/issues/876)).
* DRM: Introduce OfflineLicenseHelper to help with offline license acquisition,
  renewal and release.
* UI: Updated player control assets. Added vector drawables for use on API level
  21 and above.
* UI: Made player control seek bar work correctly with key events if focusable
  ([#2278](https://github.com/google/ExoPlayer/issues/2278)).
* HLS: Improved support for streams that use EXT-X-DISCONTINUITY without
  EXT-X-DISCONTINUITY-SEQUENCE
  ([#1789](https://github.com/google/ExoPlayer/issues/1789)).
* HLS: Support for EXT-X-START tag
  ([#1544](https://github.com/google/ExoPlayer/issues/1544)).
* HLS: Check #EXTM3U header is present when parsing the playlist. Fail
  gracefully if not ([#2301](https://github.com/google/ExoPlayer/issues/2301)).
* HLS: Fix memory leak
  ([#2319](https://github.com/google/ExoPlayer/issues/2319)).
* HLS: Fix non-seamless first adaptation where master playlist omits resolution
  tags ([#2096](https://github.com/google/ExoPlayer/issues/2096)).
* HLS: Fix handling of WebVTT subtitle renditions with non-standard segment file
  extensions ([#2025](https://github.com/google/ExoPlayer/issues/2025) and
  [#2355](https://github.com/google/ExoPlayer/issues/2355)).
* HLS: Better handle inconsistent HLS playlist update
  ([#2249](https://github.com/google/ExoPlayer/issues/2249)).
* DASH: Don't overflow when dealing with large segment numbers
  ([#2311](https://github.com/google/ExoPlayer/issues/2311)).
* DASH: Fix propagation of language from the manifest
  ([#2335](https://github.com/google/ExoPlayer/issues/2335)).
* SmoothStreaming: Work around "Offset to sample data was negative" failures
  ([#2292](https://github.com/google/ExoPlayer/issues/2292),
  [#2101](https://github.com/google/ExoPlayer/issues/2101) and
  [#1152](https://github.com/google/ExoPlayer/issues/1152)).
* MP3/ID3: Added support for parsing Chapter and URL link frames
  ([#2316](https://github.com/google/ExoPlayer/issues/2316)).
* MP3/ID3: Handle ID3 frames that end with empty text field
  ([#2309](https://github.com/google/ExoPlayer/issues/2309)).
* Added ClippingMediaSource for playing clipped portions of media
  ([#1988](https://github.com/google/ExoPlayer/issues/1988)).
* Added convenience methods to query whether the current window is dynamic and
  seekable ([#2320](https://github.com/google/ExoPlayer/issues/2320)).
* Support setting of default headers on HttpDataSource.Factory implementations
  ([#2166](https://github.com/google/ExoPlayer/issues/2166)).
* Fixed cache failures when using an encrypted cache content index.
* Fix visual artifacts when switching output surface
  ([#2093](https://github.com/google/ExoPlayer/issues/2093)).
* Fix gradle + proguard configurations.
* Fix player position when replacing the MediaSource
  ([#2369](https://github.com/google/ExoPlayer/issues/2369)).
* Misc bug fixes, including
  [#2330](https://github.com/google/ExoPlayer/issues/2330),
  [#2269](https://github.com/google/ExoPlayer/issues/2269),
  [#2252](https://github.com/google/ExoPlayer/issues/2252),
  [#2264](https://github.com/google/ExoPlayer/issues/2264) and
  [#2290](https://github.com/google/ExoPlayer/issues/2290).

### r2.1.1 (2016-12-20) ###

* Fix some subtitle types (e.g. WebVTT) being displayed out of sync
  ([#2208](https://github.com/google/ExoPlayer/issues/2208)).
* Fix incorrect position reporting for on-demand HLS media that includes
  EXT-X-PROGRAM-DATE-TIME tags
  ([#2224](https://github.com/google/ExoPlayer/issues/2224)).
* Fix issue where playbacks could get stuck in the initial buffering state if
  over 1MB of data needs to be read to initialize the playback.

### r2.1.0 (2016-12-14) ###

* HLS: Support for seeking in live streams
  ([#87](https://github.com/google/ExoPlayer/issues/87)).
* HLS: Improved support:
  * Support for EXT-X-PROGRAM-DATE-TIME
    ([#747](https://github.com/google/ExoPlayer/issues/747)).
  * Improved handling of sample timestamps and their alignment across variants
    and renditions.
  * Fix issue that could cause playbacks to get stuck in an endless initial
    buffering state.
  * Correctly propagate BehindLiveWindowException instead of
    IndexOutOfBoundsException exception
    ([#1695](https://github.com/google/ExoPlayer/issues/1695)).
* MP3/MP4: Support for ID3 metadata, including embedded album art
  ([#979](https://github.com/google/ExoPlayer/issues/979)).
* Improved customization of UI components. You can read about customization of
  ExoPlayer's UI components
  [here](https://medium.com/google-exoplayer/customizing-exoplayers-ui-components-728cf55ee07a#.9ewjg7avi).
* Robustness improvements when handling MediaSource timeline changes and
  MediaPeriod transitions.
* CEA-608: Support for caption styling and positioning.
* MPEG-TS: Improved support:
  * Support injection of custom TS payload readers.
  * Support injection of custom section payload readers.
  * Support SCTE-35 splice information messages.
  * Support multiple table sections in a single PSI section.
  * Fix NullPointerException when an unsupported stream type is encountered
    ([#2149](https://github.com/google/ExoPlayer/issues/2149)).
  * Avoid failure when expected ID3 header not found
    ([#1966](https://github.com/google/ExoPlayer/issues/1966)).
* Improvements to the upstream cache package.
  * Support caching of media segments for DASH, HLS and SmoothStreaming. Note
    that caching of manifest and playlist files is still not supported in the
    (normal) case where the corresponding responses are compressed.
  * Support caching for ExtractorMediaSource based playbacks.
* Improved flexibility of SimpleExoPlayer
  ([#2102](https://github.com/google/ExoPlayer/issues/2102)).
* Fix issue where only the audio of a video would play due to capability
  detection issues ([#2007](https://github.com/google/ExoPlayer/issues/2007),
  [#2034](https://github.com/google/ExoPlayer/issues/2034) and
  [#2157](https://github.com/google/ExoPlayer/issues/2157)).
* Fix issues that could cause ExtractorMediaSource based playbacks to get stuck
  buffering ([#1962](https://github.com/google/ExoPlayer/issues/1962)).
* Correctly set SimpleExoPlayerView surface aspect ratio when an active player
  is attached ([#2077](https://github.com/google/ExoPlayer/issues/2077)).
* OGG: Fix playback of short OGG files
  ([#1976](https://github.com/google/ExoPlayer/issues/1976)).
* MP4: Support `.mp3` tracks
  ([#2066](https://github.com/google/ExoPlayer/issues/2066)).
* SubRip: Don't fail playbacks if SubRip file contains negative timestamps
  ([#2145](https://github.com/google/ExoPlayer/issues/2145)).
* Misc bugfixes.

### r2.0.4 (2016-10-20) ###

* Fix crash on Jellybean devices when using playback controls
  ([#1965](https://github.com/google/ExoPlayer/issues/1965)).

### r2.0.3 (2016-10-17) ###

* Fixed NullPointerException in ExtractorMediaSource
  ([#1914](https://github.com/google/ExoPlayer/issues/1914)).
* Fixed NullPointerException in HlsMediaPeriod
  ([#1907](https://github.com/google/ExoPlayer/issues/1907)).
* Fixed memory leak in PlaybackControlView
  ([#1908](https://github.com/google/ExoPlayer/issues/1908)).
* Fixed strict mode violation when using
  SimpleExoPlayer.setVideoPlayerTextureView().
* Fixed L3 Widevine provisioning
  ([#1925](https://github.com/google/ExoPlayer/issues/1925)).
* Fixed hiding of controls with use_controller="false"
  ([#1919](https://github.com/google/ExoPlayer/issues/1919)).
* Improvements to Cronet network stack extension.
* Misc bug fixes.

### r2.0.2 (2016-10-06) ###

* Fixes for MergingMediaSource and sideloaded subtitles.
  ([#1882](https://github.com/google/ExoPlayer/issues/1882),
  [#1854](https://github.com/google/ExoPlayer/issues/1854),
  [#1900](https://github.com/google/ExoPlayer/issues/1900)).
* Reduced effect of application code leaking player references
  ([#1855](https://github.com/google/ExoPlayer/issues/1855)).
* Initial support for fragmented MP4 in HLS.
* Misc bug fixes and minor features.

### r2.0.1 (2016-09-30) ###

* Fix playback of short duration content
  ([#1837](https://github.com/google/ExoPlayer/issues/1837)).
* Fix MergingMediaSource preparation issue
  ([#1853](https://github.com/google/ExoPlayer/issues/1853)).
* Fix live stream buffering (out of memory) issue
  ([#1825](https://github.com/google/ExoPlayer/issues/1825)).

### r2.0.0 (2016-09-14) ###

ExoPlayer 2.x is a major iteration of the library. It includes significant API
and architectural changes, new features and many bug fixes. You can read about
some of the motivations behind ExoPlayer 2.x
[here](https://medium.com/google-exoplayer/exoplayer-2-x-why-what-and-when-74fd9cb139#.am7h8nytm).

* Root package name changed to `com.google.android.exoplayer2`. The library
  structure and class names have also been sanitized. Read more
  [here](https://medium.com/google-exoplayer/exoplayer-2-x-new-package-and-class-names-ef8e1d9ba96f#.lv8sd4nez).
* Key architectural changes:
  * Late binding between rendering and media source components. Allows the same
    rendering components to be re-used from one playback to another. Enables
    features such as gapless playback through playlists and DASH multi-period
    support.
  * Improved track selection design. More details can be found
    [here](https://medium.com/google-exoplayer/exoplayer-2-x-track-selection-2b62ff712cc9#.n00zo76b6).
  * LoadControl now used to control buffering and loading across all playback
    types.
  * Media source components given additional structure. A new MediaSource class
    has been introduced. MediaSources expose Timelines that describe the media
    they expose, and can consist of multiple MediaPeriods. This enables features
    such as seeking in live playbacks and DASH multi-period support.
  * Responsibility for loading the initial DASH/SmoothStreaming/HLS manifest is
    promoted to the corresponding MediaSource components and is no longer the
    application's responsibility.
  * Higher level abstractions such as SimpleExoPlayer have been added to the
    library. These make the library easier to use for common use cases. The demo
    app is halved in size as a result, whilst at the same time gaining more
    functionality. Read more
    [here](https://medium.com/google-exoplayer/exoplayer-2-x-improved-demo-app-d97171aaaaa1).
  * Enhanced library support for implementing audio extensions. Read more
    [here](https://medium.com/google-exoplayer/exoplayer-2-x-new-audio-features-cfb26c2883a#.ua75vu4s3).
  * Format and MediaFormat are replaced by a single Format class.
* Key new features:
  * Playlist support. Includes support for gapless playback between playlist
    items and consistent application of LoadControl and TrackSelector policies
    when transitioning between items
    ([#1270](https://github.com/google/ExoPlayer/issues/1270)).
  * Seeking in live playbacks for DASH and SmoothStreaming
    ([#291](https://github.com/google/ExoPlayer/issues/291)).
  * DASH multi-period support
    ([#557](https://github.com/google/ExoPlayer/issues/557)).
  * MediaSource composition allows MediaSources to be concatenated into a
    playlist, merged and looped. Read more
    [here](https://medium.com/google-exoplayer/exoplayer-2-x-mediasource-composition-6c285fcbca1f#.zfha8qupz).
  * Looping support (see above)
    ([#490](https://github.com/google/ExoPlayer/issues/490)).
  * Ability to query information about all tracks in a piece of media (including
    those not supported by the device)
    ([#1121](https://github.com/google/ExoPlayer/issues/1121)).
  * Improved player controls.
  * Support for PSSH in fMP4 moof atoms
    ([#1143](https://github.com/google/ExoPlayer/issues/1143)).
  * Support for Opus in Ogg
    ([#1447](https://github.com/google/ExoPlayer/issues/1447)).
  * CacheDataSource support for standalone media file playbacks (mp3, mp4 etc).
  * FFMPEG extension (for audio only).
* Key bug fixes:
  * Removed unnecessary secondary requests when playing standalone media files
    ([#1041](https://github.com/google/ExoPlayer/issues/1041)).
  * Fixed playback of video only (i.e. no audio) live streams
    ([#758](https://github.com/google/ExoPlayer/issues/758)).
  * Fixed silent failure when media buffer is too small
    ([#583](https://github.com/google/ExoPlayer/issues/583)).
  * Suppressed "Sending message to a Handler on a dead thread" warnings
    ([#426](https://github.com/google/ExoPlayer/issues/426)).

# Legacy release notes #

Note: Since ExoPlayer V1 is still being maintained alongside V2, there is some
overlap between these notes and the notes above. r2.0.0 followed from r1.5.11,
and hence it can be assumed that all changes in r1.5.11 and earlier are included
in all V2 releases. This cannot be assumed for changes in r1.5.12 and later,
however it can be assumed that all such changes are included in the most recent
V2 release.

### r1.5.16 ###

* VP9 extension: Reduced binary size.
* FLAC extension: Enabled 64 bit targets and fixed proguard config.
* Misc bugfixes.

### r1.5.15 ###

* SmoothStreaming: Fixed handling of start_time placeholder
  ([#2447](https://github.com/google/ExoPlayer/issues/2447)).
* Misc bugfixes.

### r1.5.14 ###

* Fixed cache failures when using an encrypted cache content index.
* SmoothStreaming: Work around "Offset to sample data was negative" failures
  ([#2292](https://github.com/google/ExoPlayer/issues/2292),
  [#2101](https://github.com/google/ExoPlayer/issues/2101) and
  [#1152](https://github.com/google/ExoPlayer/issues/1152)).

### r1.5.13 ###

* Improvements to the upstream cache package.
* MP4: Support `.mp3` tracks
  ([#2066](https://github.com/google/ExoPlayer/issues/2066)).
* SubRip: Don't fail playbacks if SubRip file contains negative timestamps
  ([#2145](https://github.com/google/ExoPlayer/issues/2145)).
* MPEG-TS: Avoid failure when expected ID3 header not found
  ([#1966](https://github.com/google/ExoPlayer/issues/1966)).
* Misc bugfixes.

### r1.5.12 ###

* Improvements to Cronet network stack extension.
* Fix bug in demo app introduced in r1.5.11 that caused L3 Widevine
  provisioning requests to fail.
* Misc bugfixes.

### r1.5.11 ###

* Cronet network stack extension.
* HLS: Fix propagation of language for alternative audio renditions
  ([#1784](https://github.com/google/ExoPlayer/issues/1784)).
* WebM: Support for subsample encryption.
* ID3: Fix EOS detection for 2-byte encodings
  ([#1774](https://github.com/google/ExoPlayer/issues/1774)).
* MPEG-TS: Support multiple tracks of the same type.
* MPEG-TS: Work toward robust handling of stream corruption.
* Fix ContentDataSource failures triggered by garbage collector
  ([#1759](https://github.com/google/ExoPlayer/issues/1759)).

### r1.5.10 ###

* HLS: Stability fixes.
* MP4: Support for stz2 Atoms.
* Enable 4K format selection on Sony AndroidTV + nVidia SHIELD.
* TX3G caption fixes.

### r1.5.9 ###

* MP4: Fixed incorrect sniffing in some cases (#1523).
* MP4: Improved file compatibility (#1567).
* ID3: Support for TIT2 and APIC frames.
* Fixed querying of platform decoders on some devices.
* Misc bug fixes.

### r1.5.8 ###

* HLS: Fix handling of HTTP redirects.
* Audio: Minor adjustment to improve A/V sync.
* OGG: Support FLAC in OGG.
* TTML: Support regions.
* WAV/PCM: Support 8, 24 and 32-bit WAV and PCM audio.
* Misc bug fixes and performance optimizations.

### r1.5.7 ###

* OGG: Support added for OGG.
* FLAC: Support for FLAC extraction and playback (via an extension).
* HLS: Multiple audio track support (via Renditions).
* FMP4: Support multiple tracks in fragmented MP4 (not applicable to
  DASH/SmoothStreaming).
* WAV: Support for 16-bit WAV files.
* MKV: Support non-square pixel formats.
* Misc bug fixes.

### r1.5.6 ###

* MP3: Fix mono streams playing at 2x speed on some MediaTek based devices
  (#801).
* MP3: Fix playback of some streams when stream length is unknown.
* ID3: Support multiple frames of the same type in a single tag.
* CEA-608: Correctly handle repeated control characters, fixing an issue in
  which captions would immediately disappear.
* AVC3: Fix decoder failures on some MediaTek devices in the case where the
  first buffer fed to the decoder does not start with SPS/PPS NAL units.
* Misc bug fixes.

### r1.5.5 ###

* DASH: Enable MP4 embedded WebVTT playback (#1185)
* HLS: Fix handling of extended ID3 tags in MPEG-TS (#1181)
* MP3: Fix incorrect position calculation in VBRI header (#1197)
* Fix issue seeking backward using SingleSampleSource (#1193)

### r1.5.4 ###

* HLS: Support for variant selection and WebVtt subtitles.
* MP4: Support for embedded WebVtt.
* Improved device compatibility.
* Fix for resource leak (Issue #1066).
* Misc bug fixes + minor features.

### r1.5.3 ###

* Support for FLV (without seeking).
* MP4: Fix for playback of media containing basic edit lists.
* QuickTime: Fix parsing of QuickTime style audio sample entry.
* HLS: Add H262 support for devices that have an H262 decoder.
* Allow AudioTrack PlaybackParams (e.g. speed/pitch) on API level 23+.
* Correctly detect 4K displays on API level 23+.
* Misc bug fixes.

### r1.5.2 ###

* MPEG-TS/HLS: Fix frame drops playing H265 video.
* SmoothStreaming: Fix parsing of ProtectionHeader.

### r1.5.1 ###

* Enable smooth frame release by default.
* Added OkHttpDataSource extension.
* AndroidTV: Correctly detect 4K display size on Bravia devices.
* FMP4: Handle non-sample data in mdat boxes.
* TTML: Fix parsing of some colors on Jellybean.
* SmoothStreaming: Ignore tfdt boxes.
* Misc bug fixes.

### r1.5.0 ###

* Multi-track support.
* DASH: Limited support for multi-period manifests.
* HLS: Smoother format adaptation.
* HLS: Support for MP3 media segments.
* TTML: Support for most embedded TTML styling.
* WebVTT: Enhanced positioning support.
* Initial playback tests.
* Misc bug fixes.

### r1.4.2 ###

* Implemented automatic format detection for regular container formats.
* Added UdpDataSource for connecting to multicast streams.
* Improved robustness for MP4 playbacks.
* Misc bug fixes.

### r1.4.1 ###

* HLS: Fix premature playback failures that could occur in some cases.

### r1.4.0 ###

* Support for extracting Matroska streams (implemented by WebmExtractor).
* Support for tx3g captions in MP4 streams.
* Support for H.265 in MPEG-TS streams on supported devices.
* HLS: Added support for MPEG audio (e.g. MP3) in TS media segments.
* HLS: Improved robustness against missing chunks and variants.
* MP4: Added support for embedded MPEG audio (e.g. MP3).
* TTML: Improved handling of whitespace.
* DASH: Support Mpd.Location element.
* Add option to TsExtractor to allow non-IDR keyframes.
* Added MulticastDataSource for connecting to multicast streams.
* (WorkInProgress) - First steps to supporting seeking in DASH DVR window.
* (WorkInProgress) - First steps to supporting styled + positioned subtitles.
* Misc bug fixes.

### r1.3.3 ###

* HLS: Fix failure when playing HLS AAC streams.
* Misc bug fixes.

### r1.3.2 ###

* DataSource improvements: `DefaultUriDataSource` now handles http://, https://,
  file://, asset:// and content:// URIs automatically. It also handles
  file:///android_asset/* URIs, and file paths like /path/to/media.mp4 where the
  scheme is omitted.
* HLS: Fix for some ID3 events being dropped.
* HLS: Correctly handle 0x0 and floating point RESOLUTION tags.
* Mp3Extractor: robustness improvements.

### r1.3.1 ###

* No notes provided.
