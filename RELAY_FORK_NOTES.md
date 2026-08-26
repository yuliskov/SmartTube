# RelayTube patch boundaries

RelayTube deliberately separates upstream-friendly UI work from optional Relay integration.

## Upstream-friendly Material You UI

These changes have no Relay dependency and can be proposed or cherry-picked independently:

- `tv/ui/material/MaterialYouColors.java`
- browse color, card-outline, and spacing resources
- future Compose Material 3 browse, search, and settings modules

## Optional Relay bridge

All Relay-specific playback sharing is isolated to:

- `tv/integration/RelayPlaybackReporter.java`
- one import and one `RelayPlaybackReporter.publish(...)` call in `PlaybackFragment`
- `RELAY_INTEGRATION.md`

To remove Relay integration completely, delete the reporter, then remove that import and call from
`PlaybackFragment`. No player behavior, history storage, theme token, or UI class depends on it.

The bridge is package-targeted to Relay and carries only the active video snapshot; SmartTube's
private history remains internal.
