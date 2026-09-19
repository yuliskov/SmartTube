---
name: smarttube-build
description: Build or validate SmartTube Android changes, diagnose Gradle setup, or prepare APKs.
---

# SmartTube build and validation

Use the repository wrapper and resolve the actual shared-module roots before choosing tasks. Match validation to the changed module and requested variant; preserve pinned dependencies and existing signing configuration.

Read [references/build.md](references/build.md) for environment setup, Gradle commands, CI parity, and signing/device boundaries. Documentation-only work does not need this workflow.

Complete when relevant checks have passed and the requested artifact exists, or identify the precise environment blocker and complete independent checks. Distinguish compilation, tests, APK creation, signing, and device verification in the result.
