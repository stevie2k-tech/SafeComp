# SafeComp: handoff

_Last updated: 2026-10-23. The repo is now **public**: https://github.com/stevie2k-tech/SafeComp_

## 1. Goal

SafeComp is an Android MVP for a scholarship cybersecurity project.
- Company files live in an encrypted vault (Tink).
- The vault decrypts into memory only when both of these hold:
  - **where:** the phone is INSIDE the office geofence;
  - **who:** the owner authenticated with fingerprint, face or PIN within the last 5 minutes.
- Threat model: **accidental exposure** (a lost or stolen phone, a family member). It is **not** employee surveillance.
- The owner must defend every decision in an interview, so each design choice needs an interview-ready reason.

**Rules for every phase.** These were given verbatim and are non-negotiable:
- No INTERNET permission. Everything stays on the device.
- Files are always encrypted on disk. Location, time and authentication only decide whether SafeComp may decrypt into memory.
- Fail closed: if a condition can't be proven, the vault stays locked.
- The event log never stores coordinates or which apps were used.
- Debug-only features stay out of release builds (use the `src/debug` / `src/release` source sets).
- Every new rule gets unit tests, the README is updated, and important decisions are explained in the report.
- No hardcoded keys and no plaintext on disk.

**Workflow for each phase:**
1. Show the plan and wait for approval.
2. Build, then run all unit tests and lint.
3. Commit and push directly to `main`.
4. Give a short report: what changed, test results, and what to check on the emulator.
5. **Do not start the next phase until the owner says so.**

If anything conflicts with how the code currently works, stop and ask instead of guessing.

## 2. Current state

| Phase | Status |
|---|---|
| 1–6: permissions, geofence and state machine, vault, boundary behaviour, event log, demo mode | Done |
| 8: Focus Mode (screen pinning / Lock Task tied to INSIDE) | Done, emulator-tested |
| 8b: battery-optimisation check (idea from Tikoncha; no code copied) | Done |
| 7: fingerprint / face / PIN unlock, auth-bound key, protected settings | Done: unit tests and lint pass. **The on-device auth flow has not been verified** (the emulator kept crashing under Mac memory pressure) |
| 9: work-hours schedule | Not started (waiting for go-ahead) |
| 10: multiple office zones | Not started |
| 11: Uzbek / Russian / English | Not started (comes last) |

- Last run: 123 unit tests pass and lint is clean (Phase 7).
- A security code review was delivered. Fixes M1, M2, L2 and L3 have been applied (§6A); H1 still needs the owner's decision.

**Environment:**
- Real project: `~/AndroidStudioProjects/SafeComp`. The `~/Desktop/SafeComp` copy is **obsolete**, but the IDE sometimes opens files there.
- Repo: `stevie2k-tech/SafeComp` (private), branch `main`.
- Build command:
  ```
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease
  ```
- Versions: minSdk 26, target/compile 37, AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.20.
- `~/Downloads/SafeComp.apk` is a **debug** build (see H1).

## 3. Active files

All paths are under `app/src/main/java/com/safecomp/`.

**Crypto**
- `crypto/VaultKeystore.kt`: the two Keystore keys.
  - KEK `safecomp_master_key_v2`: 300 s window, biometric or PIN.
  - Canary `safecomp_fingerprint_check`: biometric-only, per-use, invalidated by a new enrollment.
- `crypto/KeyManager.kt`: unwraps the Tink keysets (`safecomp_keysets_v2` prefs) on `unlock()` and drops them on `lock()`. It also handles the legacy migration (verify before delete).
- `crypto/KeysetWrapping.kt`: wrap/unwrap helpers plus the pure `KeyMigration` rules.

**Auth**
- `security/AuthPolicy.kt`: pure, unit-tested rules: `status`, `isFresh`, `maySaveSettings`, `mayReset`, `pinOnly`.
- `security/AuthSession.kt`: runs the session. It ends on background, screen-off, expiry or leaving INSIDE, and logs `AUTH` events.
- `ui/auth/AuthGate.kt`, `ui/auth/AuthGateCard.kt`: BiometricPrompt on API 30+; KeyguardManager confirm-credential below that.

**Vault**
- `vault/VaultRepository.kt`: import and open; `isUnlocked()` needs both INSIDE and auth.

**Focus Mode**
- `focus/` (FocusPolicy, FocusController, FocusCapabilities, FocusActionReceiver, FocusAdmin) and `MainActivity.kt`, which runs the lock-task commands in a RESUMED collector.
- Debug-only: `src/debug/.../FocusAdmins`, `FocusAdminReceiver`, `res/xml/focus_device_admin.xml`. `src/release` has a stub.

**Log and data**
- `ui/log/EventLogViewModel.kt`, `data/EventLogEntry.kt`.
- Room DB is at **v6**, with auto-migrations.
- `data/AppPreferences.kt`.

**Wiring and docs**
- `AppContainer.kt`. Order matters: vaultSession → VaultKeystore → KeyManager → AuthSession → VaultRepository.
- `README.md`: status table, security design, limitations.

**Tests:** in `app/src/test/`, including AuthPolicyTest, KeysetWrappingTest, PermissionStepsTest, EventLogPolicyTest, FocusPolicyTest, BackgroundAdviceTest and SecurityStateMachineTest.

## 4. Changes made (most recent first)

**`404d565` Phase 7: fingerprint / face / PIN unlock.**
- The vault keysets are now wrapped by an auth-bound Keystore AES-GCM key with a 5-minute window (biometric strong or device credential).
- **Why a time-window key and not per-use biometric:**
  - Android invalidates a key on a new fingerprint only if it is per-use and biometric-only. Allowing the PIN or a time window disables that.
  - So a separate canary key detects fingerprint changes, and the next unlock must then use the PIN ("PIN once, keep files").
- Removing the screen lock destroys all auth-bound keys. The UI then shows KEYS_DESTROYED, and Reset is allowed without auth only when the phone has no screen lock (the data is unreadable anyway).
- The session ends on background (ProcessLifecycle `onStop`), on `ACTION_SCREEN_OFF`, on expiry (a 1 s ticker), or when the phone leaves INSIDE.
- Every settings change and Reset require a fresh auth.
- Onboarding gained a SCREEN_LOCK step, and Home shows warnings.
- Legacy installs are migrated on the first unlock, keeping the same keys. The old copies are deleted only after reading them back under the new key.
- New `EventType.AUTH`: AUTHENTICATED, PIN_REQUIRED, KEYS_DESTROYED, KEYS_UPGRADED. DB goes from v5 to v6.
- `MainActivity` is now a `FragmentActivity` (BiometricPrompt needs it).

**`60936e8` Phase 8 / 8b: Focus Mode, battery check, pre-run fixes.**
- Pure FocusPolicy. Focus is allowed only while INSIDE, is opt-in with default on, and never traps the user (unpin gesture, Stop button, notification action, settings switch).
- A user's "No" is remembered until OUTSIDE or the next day.
- The allow-list is stored in Room (`WorkApp`).
- The battery-optimisation dialog has brand-specific advice (Xiaomi, Samsung, Huawei, Oppo and others).

**Earlier commits** cover Phases 1–6, the app icon, video playback on the emulator, and grace-ticker and storage race fixes.

## 5. Failed attempts (and what replaced them)

| Tried | Why it failed | Replaced by |
|---|---|---|
| Focus commands sent through a `SharedFlow` | A command emitted at cold start was lost before the activity collected it | `StateFlow` of a pending command, collected in `repeatOnLifecycle(RESUMED)`, then `commandHandled(id)` |
| Detecting a "No" to the pin dialog through window focus | The Android 17 "Pin app?" dialog is `NOT_FOCUSABLE`, so focus never changes | Touch-based detection: `onUserInteraction()` with a 1.5 s settle and a 60 s timeout |
| Keeping the "user said no" flag in memory | Forgotten after process death, so the app asked again | Persisted in prefs; reset only on OUTSIDE or the next day |
| Running Focus `evaluate` on the main thread | Jank and ANR risk | Moved to `Dispatchers.Default` |
| A recursive lambda with an inferred type | Kotlin compile error (recursive type inference) | Explicit `: Unit` return type |
| Showing "Left the office" for every stop | Wrong for UNKNOWN (for example, location off) | Message depends on the state |
| Making `mayReset` depend on session state | Could get stuck: no way to reset | Purely facts-based (`!deviceSecure \|\| fresh auth`) |
| Using the KEK's own `setInvalidatedByBiometricEnrollment(true)` | Ignored by Android for keys with a time window or a PIN | Separate per-use, biometric-only canary key |
| Verifying Phase 7 on the emulator | Mac memory pressure: system_server deaths, DeadSystemException, ANRs, UiAutomation timeouts | Not solved. Verify on a real phone, or after closing other apps or giving the emulator more RAM |
| Shell loops in zsh | Glob and word-splitting pitfalls | Quote the globs or run under `bash` |

## 6. Specific next steps

**A. Quick security-review fixes: DONE (2026-10-23).**
- **M1:** the import error log and exception message no longer include the file URI or name.
- **M2:** `canaryState()` now fails closed. Any Keystore error means INVALIDATED, so the PIN is required once.
- **L2:** Clear log needs INSIDE and a fresh auth session.
- **L3:** `kekState()` catches any Keystore error and treats it as NEEDS_AUTHENTICATION instead of crashing.

The tests and lint pass. These are Android Keystore paths, so they have no unit tests; verify them on a device.

**Remaining lower-severity items** (documented as limitations, not fixed):
- L1: plaintext metadata (office coordinates, MIME types, sizes, the log).
- L4: snapshot prefs can be forged on a rooted phone.
- L5: legacy no-auth keys remain until the first unlock.
- L6: some rules are enforced by the app only.
- L7: no tapjacking filter (`filterTouchesWhenObscured`).
- No StrongBox.

**B. H1: release build.** The distributed APK is debuggable, and its demo mode can fake INSIDE.
- **Needs the owner's decision:** where the release signing keystore lives. It must stay outside the repo, with the passwords in `~/.gradle/gradle.properties` or environment variables.
- Then `assembleRelease`, check there is no INTERNET permission and no FocusAdminReceiver or demo classes, and replace `~/Downloads/SafeComp.apk`.

**C. The owner runs the Phase 7 device checklist:**
1. `adb shell locksettings set-pin 1234`, then demo Inside → PIN prompt → 5 files are visible and a KEYS_UPGRADED log entry appears.
2. Press Home or turn the screen off → re-auth is required.
3. Every settings save prompts for auth.
4. Add a fingerprint → the next unlock is PIN only.
5. `adb shell locksettings clear --old 1234` → keys destroyed; Reset works without auth.

**D. Phases 9 → 10 → 11.** Each one only after the owner explicitly says "start". Each needs a plan, approval, build, tests and lint, commit and push, and a report.
- **Phase 9: work hours.**
  - Default Mon–Fri 08:00–18:00, Asia/Tashkent; overnight shifts are supported.
  - Warn 5 minutes before the end of work hours.
  - Detect clock tampering: compare the wall clock with the boot clock, and cross-check against GPS time. On a mismatch, fail closed and log it.
- **Phase 10: multiple office zones.**
  - Each zone has a name, centre, radius ≥100 m, and an access level (Full or View-only).
  - Zones can be edited only while INSIDE a zone and after auth.
  - Where zones overlap, the stricter rule wins. Respect Android's geofence limits.
  - Migrate the existing office to a Full zone.
- **Phase 11: languages.**
  - Uzbek (Latin), Russian and English. Move all strings into `values-uz` and `values-ru`, and add a per-app language choice.
  - Give the owner a list of the translations to review, marking any uncertain ones.

**E. After all phases:** update the README status table, the security design and the limitations, then give a final report.

**Housekeeping:**
- Don't commit `strix_runs/` (scanner output in the Desktop copy) without asking.
- This repo is public: never commit signing keys, `local.properties` or anything else secret.
