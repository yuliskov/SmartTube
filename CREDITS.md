# Credits / prior work

This SmartTube integration is based on the **Voice Over Translation** ecosystem and Yandex browser VOT API behavior documented by these open-source projects:

| Project | Author / org | Role |
|---------|----------------|------|
| [voice-over-translation](https://github.com/ilyhalight/voice-over-translation) | [ilyhalight](https://github.com/ilyhalight) | Browser extension; UX and API flow reference |
| [vot-cli](https://github.com/FOSWLY/vot-cli) | [FOSWLY](https://github.com/FOSWLY) | CLI client; protobuf / request patterns |
| [vot.js](https://github.com/FOSWLY/vot.js) | [FOSWLY](https://github.com/FOSWLY) | `fail-audio-js` fallback for `AUDIO_REQUESTED` |

SmartTube TV code in `common/.../vot/` is a **new port** for Android TV (OkHttp + ExoPlayer), not a copy-paste of the extension source tree.

Yandex VOT endpoints are **unofficial** and may change without notice.
