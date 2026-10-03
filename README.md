# SafeComp

SafeComp keeps company files in an encrypted vault on an employee's Android phone. The files can
only be opened while the phone is inside the office geofence. If the phone is lost or stolen, or
someone else is using it, the company data stays locked.

> This README is filled in phase by phase. The full architecture, security model and
> limitations sections arrive with the final phase.

## Building

1. Install [Android Studio](https://developer.android.com/studio) (it includes the JDK and Android SDK).
2. Open this folder in Android Studio and let Gradle sync.
3. Connect your phone with USB debugging on, then press **Run**.

From the terminal (after Android Studio is installed):

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest
```

The Gradle wrapper jar was checked against Gradle's published SHA-256, and
`gradle-wrapper.properties` pins the distribution's checksum, so a tampered Gradle download
is rejected.

## Status

- [x] Phase 1: project setup, permission onboarding, screen-capture protection (`FLAG_SECURE`)
- [x] Phase 2: geofence setup and the INSIDE / OUTSIDE / UNKNOWN state
- [ ] Phase 3: encrypted vault
- [ ] Phase 4: boundary behaviour (grace period, auto-close, notifications)
- [ ] Phase 5: local event log
- [ ] Phase 6: demo mode (debug builds only)
