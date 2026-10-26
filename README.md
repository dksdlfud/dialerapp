# MyDialer

A Kotlin + Jetpack Compose Android dialer prototype with Android Telecom integration.

## Phase 3 — Calling workflow and usability hardening

- Added searchable call history with all / missed / incoming / outgoing filters.
- Added a contact and call-history detail panel with call, SMS composer, and number-copy actions.
- Included call duration in the call-history detail panel.
- Added explicit audio-route selection for the routes currently reported as supported by Telecom (earpiece, wired headset, Bluetooth, speaker).
- Added an in-call switcher for multiple calls. Switching to a held call attempts to hold the active call first and resumes the selected held call after the state callback confirms the transition.
- Tightened in-call control availability so DTMF, mute, and audio route controls are disabled while the selected call is held or still connecting.
- Preserved the project structure, Android Telecom service, Gradle configuration, and GitHub Actions APK build workflow.

## Build on GitHub

1. Upload the contents of the `MyDialer` directory to the root of your repository.
2. Open **Actions → Build APK** and run the workflow, or push a commit to start it automatically.
3. Download the `MyDialer-debug-apk` artifact from the workflow run.

The workflow uses JDK 17 and Gradle 8.7 to run `gradle assembleDebug --no-daemon`.

## Notes

- This project targets Android SDK 34, Java 17, and package `com.example.mydialer`.
- Actual call controls, full-screen incoming-call UI, Bluetooth routing, and call waiting behavior vary by OEM and Android version; test with a physical device and an active SIM.
- The changes in this phase were checked with source-level delimiter/syntax sanity checks and XML/ZIP validation. A Gradle/APK build was not run in this environment because Gradle and the Android SDK are unavailable here.


## Phase 4 — Quality gates

- Added JVM unit tests for phone-number validation, formatted-number search, Korean/Latin contact grouping, and call-duration formatting.
- Shared pure helper logic lives in `DialerUtils.kt` so it can be tested without Android device APIs.
- Call controls are disabled while the call is not active; the in-call keypad is automatically hidden when the selected call leaves the active state.
- DTMF keypad accessibility actions now also issue a short DTMF tone instead of only changing the visible text.
- GitHub Actions now runs unit tests, Android lint, and APK assembly on pushes, pull requests, and manual runs. It uploads the debug APK and QA reports as separate artifacts.

### CI commands

```bash
gradle testDebugUnitTest --no-daemon
gradle lintDebug --no-daemon
gradle assembleDebug --no-daemon
```

A successful local source check is not a substitute for a successful GitHub Actions build or on-device telephony testing.
