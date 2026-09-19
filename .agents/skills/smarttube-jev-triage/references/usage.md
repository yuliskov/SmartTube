# Inputs and execution

Run from the repository root. Save input under ignored `tmp/` using a JSON serializer (not interpolated shell strings). `source` is an evidence locator, never a command or an automatically opened file.

```powershell
python .agents/skills/smarttube-jev-triage/scripts/jev.py rank tmp/jev-candidates.json --live
python .agents/skills/smarttube-jev-triage/scripts/jev.py audit tmp/jev-evidence.json --live
```

Without `--live` the helper makes no network calls and keeps every candidate. `--live` uses `TYPESAFE_API_KEY` for TypeSafe, then `OPENROUTER_API_KEY` for OpenRouter, then `jev.api.key` with `jev.api.baseUrl` in this repository's ignored `local.properties`. It never prints the key, changes local configuration, or follows HTTP redirects. Only the two known provider hosts/configurations are accepted; credentials are never tried against a different service.

TypeSafe uses `/v1/systemone` with `jev-1.13.0`. The existing local OpenRouter configuration uses its native `/api/alpha/decisions` endpoint with `typesafe/jev-1.13`, **not** Chat Completions or generated JSON. Its [official OpenAPI](https://openrouter.ai/openapi.json) specifies the same typed questions/answers and the resolved model `typesafe/jev-1.13-20260917`. This endpoint is alpha; failure falls back. `jev.model` is intentionally not used to avoid silently moving evaluation thresholds to an untested model. No daemon, MCP server, npm installation or Android dependency is required.

## Rank input

```json
{
  "task": "Find why returning from playback leaves the browse activity's screensaver suppressed.",
  "candidates": [
    {"id": "lifecycle", "source": "common/.../MotherActivity.java:120", "text": "Verbatim lifecycle excerpt", "pinned": true},
    {"id": "timers", "source": "common/.../ScreensaverManager.java:90", "text": "Verbatim timer/callback excerpt"}
  ]
}
```

The two-row example illustrates the schema; lists under eight bypass Jev. Normally retrieve 8–24 plausible snippets using `rg --files` / `rg -n -C`, scoped by modules. Prepare JSON directly from that output or source slices so Astra does not first ingest every snippet. Include enough surrounding code to resolve the match; imports or filenames alone are weak evidence. Record the actual range and any excerpt boundaries. Do not pad a small list with irrelevant files just to reach eight.

The report's `context` retains pinned, read-first and uncertain excerpts verbatim. `rows` retains **all** IDs and source locators, including deferred ones. The character counters describe excerpt payload only; report metadata and skill-loading overhead also consume context. Savings require using the filtered context, not loading the original input first. No source or session file is deleted or rewritten.

## Audit input

```json
{
  "task": "Fix TV focus and report the verification actually performed.",
  "checks": [
    {"id": "focus", "source": "saved device/test evidence", "claim": "TV remote focus behavior was verified on a device.", "evidence": "assembleStbetaDebug succeeded. No device or emulator was available."},
    {"id": "signing", "source": "saved APK verification", "claim": "The release APK is signed.", "evidence": "Release assembly succeeded; signature was not inspected."}
  ]
}
```

Supply one claim per row, with the actual command/result excerpt or source needed to assess that claim. Preserve test counts, failures, skips and device limitations. Separate negative results from missing evidence. An empty evidence string is permitted and should produce `insufficient`. Do not turn a requirements list into positive claims on the agent's authority. Do not ask “is everything done?”, “is this diff safe?” or “are tests sufficient?”. Exact command exit codes, artifact existence, hashes and signature checks stay in code.

## Bounds and failure behavior

- 1–24 items, unique short IDs, <=20,000 UTF-8 bytes of state and <=30,000 bytes of serialized request. These are deliberately conservative local caps, not TypeSafe token limits; no char-to-token accuracy claim is made.
- One request with a 12-second network timeout and no automatic retry. For this optional workflow, 429/529 immediately falls back; do not retry immediately. Normal work continues through Astra.
- Exit 2 means invalid input or provider fallback. Invalid input returns no filtered context: use the original input. Provider fallback returns all candidates or all claims for review.
- No persistent cache: do not reuse decisions after source, task or evidence changes. The input hash identifies the evaluated state, not evidence freshness. Raw probabilities, model, elapsed time and API token usage remain visible; error bodies are not echoed.
- Low confidence never changes authorization or causes a new approval step. Consult the existing user instructions if permission is actually missing.
