# Build and validation reference

## Sources of truth

- `settings.gradle`: module inclusion and sibling-checkout precedence.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle distribution (currently 7.5).
- `build.gradle`: Android Gradle plugin (currently 7.4.2), repository resolution, compatibility pins, and local Leanback/Fragment replacements.
- Selected `SharedModules/constants.gradle`: SDK, Kotlin, and dependency versions.
- `smarttubetv/build.gradle`: flavors, ABI outputs, signing, and test configuration.
- `.github/workflows/CI.yml`: JDK 17, release lint, and APK assembly. Consult current files if this reference diverges.

## Local checks

In PowerShell, use `./gradlew.bat`; on POSIX, use `./gradlew`. Configure the Android SDK using the existing environment or local SDK location, without committing machine-specific paths. Inspect submodule state before initialization; do not advance submodule revisions or overwrite local edits as setup cleanup.

Choose the smallest relevant tasks. Examples for the app's beta variant:

```powershell
./gradlew.bat :smarttubetv:testStbetaDebugUnitTest
./gradlew.bat :smarttubetv:assembleStbetaDebug
```

Use a module's task and, where supported, `--tests` for focused tests. Check the current task list if task availability is uncertain. Fix failures introduced by the requested change and rerun affected checks; distinguish existing failures from regressions.

For release/CI validation, CI runs:

```powershell
./gradlew.bat lintStbetaRelease
./gradlew.bat clean assembleStbetaRelease
```

CI's `clean` build is useful for CI parity, not a prerequisite for every local edit. APKs are under `smarttubetv/build/outputs/apk/<flavor>/<buildType>/`; confirm the actual output and ABI rather than inventing a filename.

## Boundaries and completion

`keystore.properties`, when present, configures both release and debug signing. Without it, release assembly can produce unsigned output; never label that as a signed release. Do not create/rotate keys or change signing identity as a build workaround.

For Stbeta/Ststable requests, the presence of `smarttubetv/google-services.json` enables Google Services and Crashlytics plugins. Check configured task side effects before treating release work as purely local. VirusTotal uploads, publication, and device installation are separate actions, requiring authorization covering that action and target.

TV focus, remote navigation, and playback behavior need appropriate device/emulator evidence when those behaviors change. Lack of a device does not stop independent implementation or automated checks; report the remaining verification honestly.
