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
| Version | 32.32 / code 2422 | 32.41 / code 2431 |
| Parent history | Baseline | 18 commits ahead before the final delivery commit |
| MediaServiceCore | `e37774d7ac2a34811bbfe5f25da9e8fc39fbc163` | `ff588bd0` on `CryptoDragonLady/MediaServiceCore:fix/live-transport-delivery` |
| SharedModules | `9a590e4a` | `11116d27`; two commits later but the tracked trees are identical |

Excluding this report and the APK binaries, the parent repository comparison covers 93 changed paths, 7,472 insertions, and 545 deletions. The MediaServiceCore comparison independently covers 86 paths across 20 commits, with 3,794 insertions and 736 deletions.

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

The beta version changes from 32.32/2422 to 32.41/2431. The debug build produces ABI-specific and universal APKs. The current 32.41 artifacts are checked into the repository root at the user's request. The earlier 32.40 diagnostic build remains under `artifacts/SmartTube-beta-32.40-debug/` for provenance and should not be used for sustained OkHttp playback.

### 9. Sustained-playback memory repair

The 32.40 debug APKs inherited the shared OkHttp profiler and a `BODY`-level logging interceptor on the media client. Both diagnostics fully materialize response bodies: the logger requests the complete response into an Okio buffer, while the profiler peeks up to 10 MiB, converts it to text, splits it into log chunks, and queues those chunks on a handler. Continuous SABR/OkHttp media responses therefore drove Java heap growth until playback restarted or, in the reproduced worst case, the TV powered off.

32.41 derives a playback-only OkHttp client from the configured global client and removes those two debug body interceptors. It preserves decompression, DNS selection, TLS configuration, headers, dispatcher, connection pool, and the IPv4-bound route. API traffic retains existing debug diagnostics; binary media responses do not pass through them.

The focused regression test verifies that the media client removes both unsafe interceptors, keeps the decompression interceptor, shares the original DNS/dispatcher/connection pool, and reuses an already-safe client without copying it.

### 10. Client-bound player scripts and temporary TV disable

The player response now keeps a client-appropriate JavaScript solver identity. Authenticated `TV` and `TV_DOWNGRADED` responses are explicitly bound to the `e937390a` `tv-player-es6-tcl.js`, whose N/SIG routines differ from the generic TV/WEB player. `WEB`, `MWEB`, `WEB_EMBED`, and native clients such as `VISIONOS` retain the player discovered for their own context. The immutable playback request context carries that exact identity and signature timestamp through deciphering.

Device testing showed that suppressing authenticated TV fallback for only the next search was insufficient: later playback-error callbacks could start a fresh search and select TV again. TV-family playback is therefore disabled in 32.41, including the explicit signed-in format synchronization path. The normal candidate sequence now starts `WEB` -> `VISIONOS` -> `MWEB` -> `WEB_EMBED`; the TV-specific script binding remains dormant behind the playback-client gate for a future developer-controlled re-enable.

## Verification evidence

| Check | Result |
|---|---|
| Focused common unit tests for `PlaybackNetworkRoute`, transport telemetry, and live response rotation | Passed |
| `:exoplayer-library-sabr:testStbetaDebugUnitTest --tests ...SabrManifestSchedulingTest` | Passed; `BUILD SUCCESSFUL` |
| `:smarttubetv:assembleStbetaDebug` | Passed; all four APK variants produced |
| Common ExoPlayer unit-test group, including playback OkHttp isolation | Passed; `BUILD SUCCESSFUL` |
| Playback-client policy tests | Passed; assert the preferred `WEB`/`VISIONOS`/`MWEB`/`WEB_EMBED` order and reject all TV-family playback clients |
| TV-disable device trace on SmartTV 4K | Final 32.41 build requested the age-restricted control through `WEB`; no `TV`, `TV_DOWNGRADED`, authenticated-TV fallback, or `TVHTML5` player request appeared |
| `git diff --check` | No whitespace errors; only Git line-ending conversion warnings |
| John McKinney live HLS with Default selected | Reached first frame/ready through the explicit `OkHttp-IPv4Bound` route; continuing media segments returned 2xx |
| John McKinney live HLS with Cronet selected | Reached ready through the same explicit compatibility route; continuing media segments returned 2xx |
| John McKinney live HLS with OkHttp selected | Visibly played; continuing media segments returned 2xx |
| VOD SABR with Cronet selected | Visibly rendered through native Cronet SABR and received SABR media payloads |
| VOD SABR/OkHttp sustained-memory control on SmartTV 4K | 32.41 survived beyond the prior crash window; 12 guarded samples plateaued at 61.3–68.1 MiB Java-heap PSS and 164.5–173.1 MiB total PSS |
| Media-body diagnostic isolation | After startup log backlog drained, a 12,074,374-byte SABR UMP response completed with zero profiler and zero OkHttp BODY lines in the observation window |

Live transport verification used a OnePlus NE2215 on Android SDK 35. The exact live control was John McKinney video `Nd2h85T9Z-c` on 2026-09-01. The sustained-memory verification used the 32-bit `armeabi-v7a` SmartTV 4K at `10.0.120.99:5555` with SABR/OkHttp, `WEB_EMBEDDED_PLAYER`, and VOD `dQw4w9WgXcQ`; the app was force-stopped after the bounded test.

## Included APKs

These are debug-signed beta APKs, not release-signed production artifacts.

| Artifact | Size (bytes) | SHA-256 |
|---|---:|---|
| [SmartTube_beta_32.41_arm64-v8a.apk](../SmartTube_beta_32.41_arm64-v8a.apk) | 33,552,655 | `6bf6629efeddab9851be56b65495b8af801f67aa303ea41b3848c42c558e2a2b` |
| [SmartTube_beta_32.41_armeabi-v7a.apk](../SmartTube_beta_32.41_armeabi-v7a.apk) | 31,172,763 | `db8af3132e7c590ff8d7ffa0d665c605f0b1496745c9c72b2974eba225d68eb7` |
| [SmartTube_beta_32.41_universal.apk](../SmartTube_beta_32.41_universal.apk) | 45,052,388 | `e5c6eff5c15adbd054131db6142c5d34dd0376bea5d1691f72a25ecc95ff3aab` |
| [SmartTube_beta_32.41_x86.apk](../SmartTube_beta_32.41_x86.apk) | 34,490,961 | `594224a2a135c5c743297591744534c8a1fae36dd8757f23900f5c2d2fc7be20` |

The same values are available in [SHA256SUMS-32.41.txt](../SHA256SUMS-32.41.txt).

## Intentional limitations and compatibility notes

- This branch implements playback of live streaming channels; it does not implement broadcasting.
- Production live SABR is hard-disabled. Normal live playback is HLS/DASH.
- Default or Cronet remains the configured engine, but an IPv4-bound signed Googlevideo source is transparently transported by the explicitly reported `OkHttp-IPv4Bound` compatibility route.
- The APKs are debug builds and should not be presented as release-signed packages.
- The 32.41 debug media client deliberately excludes full-body profiler/logger interceptors to keep sustained playback bounded.
- This branch points the MediaServiceCore submodule URL at the CryptoDragonLady fork so a normal recursive checkout can fetch commit `ff588bd0` reproducibly.
