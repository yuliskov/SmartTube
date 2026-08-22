# AGENTS.md

## Build

### APK Build Command
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :smarttubetv:assembleStstableDebug
```

### Java Version Fix
AGP 7.4.2 + Java 21 causes a D8 NPE during dexing (`VideoCardPresenter$2.class`). Must use Java 17:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

### Module Compilation (fast check, no APK)
```bash
./gradlew :common:compileStstableDebugJavaWithJavac
```

### Install to TV
```bash
adb connect <ip>:<port>
adb install -r <apk>
# If signature mismatch: adb uninstall <package> && adb install <apk>
```

### Package Name
- `org.smarttube.stable` (stable flavor)
