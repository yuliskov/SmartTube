# Live transport delivery branch compared with main

## Executive summary

This report compares `fix/live-transport-delivery` with the repository's default remote branch, `origin/master`. The user referred to that baseline as “main”; this repository names it `master` and `origin/HEAD` points to it.

The branch is a viewer-side playback repair, not a broadcasting implementation. Its largest changes are:

- viewer-side resolution of active live videos from channel handles and URLs;
- HLS-first live playback with bounded DASH and client-response fallback;
- response-bound player context, proof-token, nonce, player-script, and manifest handling;
- a scoped IPv4 route for signed Googlevideo URLs that reject IPv6 delivery;
- substantial SABR decoding, demultiplexing, scheduling, and session reliability work;
- redacted playback telemetry and a debug-only live/SABR harness;
- tests for the new routing, response rotation, live resolution, SABR parsing, scheduling, and context behavior.

Production live SABR remains explicitly disabled. Live channels use HLS or DASH in normal builds. The SABR changes repair VOD playback and provide the decoder/session groundwork used by the debug harness without exposing live SABR as a production source.

## Comparison baseline

| Item | Main baseline | This branch |
|---|---|---|
| SmartTube ref | `origin/master` at `b2403052e67189f2ce826078c9d4e8ea164243ba` | `fix/live-transport-delivery` |
| Version | 32.32 / code 2422 | 32.40 / code 2430 |
| Parent history | Baseline | 18 commits ahead before the final delivery commit |
| MediaServiceCore | `e37774d7ac2a34811bbfe5f25da9e8fc39fbc163` | `14bce6ca` on `CryptoDragonLady/MediaServiceCore:fix/live-transport-delivery` |
| SharedModules | `9a590e4a` | `11116d27`; two commits later but the tracked trees are identical |

Excluding this report and the APK binaries, the parent repository comparison covers 93 changed paths, 7,472 insertions, and 545 deletions. The MediaServiceCore comparison independently covers 86 paths across 19 commits, with 3,629 insertions and 712 deletions.

## Major differences

### 1. Viewer-side live channel resolution

Main expects playback to begin from an already resolved video. This branch adds a viewer-side pipeline that accepts channel handles, channel URLs, video URLs, or video IDs; resolves a channel's current live item; verifies that the candidate is actually live; and converts it into the normal playback model.

The main additions live under `common/.../playback/live/` and include input parsing, channel resolution, live-route resolution, candidate verification, session status, and source selection. The code does not create streams, upload video, manage broadcast keys, or implement any broadcaster workflow.

### 2. Live source policy and bounded recovery

Main has general player fallback behavior but no explicit live-source state machine. This branch makes live selection deterministic:

1. prefer HLS for an active live player response;
2. fall back to live DASH, then compatible adaptive DASH;
3. keep production live SABR disabled;
4. on a media 403, retire the complete response generation and request another bounded client response;
5. stop after three distinct generations instead of looping indefinitely or retrying stale URLs.

`LivePlayerResponseRetryPolicy` prevents duplicate or stale callbacks from consuming the retry budget twice. `VideoLoaderController` carries the logical video and generation state across player-response rotation.

### 3. Response-bound playback context

Main passes less of the player response's identity into the eventual media requests. This branch treats the following values as one immutable request context:

- selected Innertube client and user agent;
- client playback nonce;
- player-script identity and signature timestamp;
- visitor/data-sync identity;
- player-request and streaming proof tokens plus their binding type;
- response generation and URL expiry.

That context is propagated through MediaServiceCore into SmartTube's media-source construction so an HLS, DASH, direct, or SABR request is not accidentally combined with headers or proof material from a different client response.

### 4. Manifest and native-client handling

MediaServiceCore now transforms HLS and DASH manifest URLs through a protocol-aware helper. It solves manifest `n` challenges, inserts proof tokens in the manifest path where required, extracts expiry timestamps, and preserves those results in the playback context.

VisionOS handling is updated to use its current native identity, visitor bootstrap request, native player endpoint parameters, and matching OS/device metadata. This provides an additional authenticated fallback without pretending that a native response is a web response.

### 5. IPv4-bound signed media compatibility

The exact John McKinney live control isolated a transport-family failure: the same signed HLS segment returned HTTP 200 over IPv4 and HTTP 403 over IPv6. The Googlevideo URL carried an IPv4 `ip` binding. OkHttp worked because its SmartTube DNS path used IPv4, while Default and Cronet could connect to the same hostname over IPv6.

`PlaybackNetworkRoute` recognizes only signed Googlevideo URLs that contain a valid IPv4 binding. For those media sources, `ExoMediaSourceFactory` uses an IPv4-only OkHttp client and reports the effective engine as `OkHttp-IPv4Bound`. Unbound URLs and SABR requests continue to use the configured Default, Cronet, or OkHttp engine.

This is intentionally visible rather than claiming native Cronet/Default success for a request they did not transport.

### 6. SABR decoder and session reliability

Compared with main, the SABR library now has:

- incremental UMP decoding with explicit protocol errors;
- response demultiplexing and audio/video correlation checks;
- separate static/VOD stream context so one track cannot overwrite the other;
- shared dynamic/live session state for the debug harness;
- request scheduling, server backoff, redirect, cookie, and context handling;
- live-window and DVR metadata tracking;
- session snapshots and redacted diagnostics;
- focused fixtures and regression tests for media parts, control dispatch, scheduling, and session coordination.

A key VOD fix bypasses the dynamic live scheduler gate for static manifests. Main could accept a control-only SABR response, record its backoff, return no chunk, and never receive a wake-up that requested the next media response. Static playback now continues its per-track request sequence and reaches media data.

`SabrLiveFeatureFlags.enableSabrLiveProduction()` still returns `false`. The live SABR harness exists only in the debug source set.

### 7. Diagnostics and debug tooling

The branch adds a redacted transport vocabulary covering protocol, stage, effective engine, response generation, client, URL class/hash, status, byte count, content type, first frame, and ready state. Full signed URLs, proof tokens, cookies, and account credentials are not included in these telemetry events.

The on-screen debug information reports the actual effective engine. A debug-only activity can exercise reference live responses and SABR decoder/session behavior without enabling that route in release builds.

### 8. Version and packaging

The beta version changes from 32.32/2422 to 32.40/2430. The debug build produces ABI-specific and universal APKs. Those artifacts are checked into this branch under `artifacts/SmartTube-beta-32.40-debug/` at the user's request.

## Verification evidence

| Check | Result |
|---|---|
| Focused common unit tests for `PlaybackNetworkRoute`, transport telemetry, and live response rotation | Passed |
| `:exoplayer-library-sabr:testStbetaDebugUnitTest --tests ...SabrManifestSchedulingTest` | Passed; `BUILD SUCCESSFUL` |
| `:smarttubetv:assembleStbetaDebug` | Passed; all four APK variants produced |
| `git diff --check` | No whitespace errors; only Git line-ending conversion warnings |
| John McKinney live HLS with Default selected | Reached first frame/ready through the explicit `OkHttp-IPv4Bound` route; continuing media segments returned 2xx |
| John McKinney live HLS with Cronet selected | Reached ready through the same explicit compatibility route; continuing media segments returned 2xx |
| John McKinney live HLS with OkHttp selected | Visibly played; continuing media segments returned 2xx |
| VOD SABR with Cronet selected | Visibly rendered through native Cronet SABR and received SABR media payloads |

Device verification used a OnePlus NE2215 on Android SDK 35. The exact live control was John McKinney video `Nd2h85T9Z-c` on 2026-09-01.

## Included APKs

These are debug-signed beta APKs, not release-signed production artifacts.

| Artifact | Size (bytes) | SHA-256 |
|---|---:|---|
| [SmartTube_beta_32.40_arm64-v8a.apk](../artifacts/SmartTube-beta-32.40-debug/SmartTube_beta_32.40_arm64-v8a.apk) | 33,551,762 | `1d670dea4cb8e705d8c0ee8bcb21e1c9ead6bb40ff88ad924e187e67ff2e6eb4` |
| [SmartTube_beta_32.40_armeabi-v7a.apk](../artifacts/SmartTube-beta-32.40-debug/SmartTube_beta_32.40_armeabi-v7a.apk) | 31,171,870 | `f8eaaf4fd9f513163e10822f9ba343d8e61801a68339af8c80d09e9b2c690fef` |
| [SmartTube_beta_32.40_universal.apk](../artifacts/SmartTube-beta-32.40-debug/SmartTube_beta_32.40_universal.apk) | 45,051,486 | `0a68cf862dc549f6e50107f974640e1fd0ae8b8f4348f6f151f8d80d85ca4eca` |
| [SmartTube_beta_32.40_x86.apk](../artifacts/SmartTube-beta-32.40-debug/SmartTube_beta_32.40_x86.apk) | 34,490,070 | `429b783a79ea906379e91ef3eca53c2836aa99cf961d78fb8f035e51606a2501` |

The same values are available in [SHA256SUMS.txt](../artifacts/SmartTube-beta-32.40-debug/SHA256SUMS.txt).

## Intentional limitations and compatibility notes

- This branch implements playback of live streaming channels; it does not implement broadcasting.
- Production live SABR is hard-disabled. Normal live playback is HLS/DASH.
- Default or Cronet remains the configured engine, but an IPv4-bound signed Googlevideo source is transparently transported by the explicitly reported `OkHttp-IPv4Bound` compatibility route.
- The APKs are debug builds and should not be presented as release-signed packages.
- This branch points the MediaServiceCore submodule URL at the CryptoDragonLady fork so a normal recursive checkout can fetch commit `14bce6ca` reproducibly.
