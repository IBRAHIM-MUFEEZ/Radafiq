# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Radafiq is a financial ledger platform for tracking customer credit, EMI plans, savings, and account dues. Two independent apps share a Firebase Firestore backend — there is no shared code between them, only a shared data model.

| Platform | Directory | Stack |
|---|---|---|
| Android | `app/` | Kotlin, Jetpack Compose, MVVM, Navigation Compose, Gradle 9.5.0 |
| Web (PWA) | `web-app/` | React 18, TypeScript, Vite 6, React Router v6 |

The `open-design/` directory is a **separate, unrelated project** (the [Open Design](https://github.com/Open-Design/Open-Design) app) checked into this repo. Its `CLAUDE.md` and `AGENTS.md` apply only within that directory.

## Common commands

### Web app (`web-app/`)

```bash
npm run dev       # dev server → http://localhost:3000
npm run build     # tsc + vite build → dist/
npm run preview   # preview production build
npx firebase deploy --only hosting  # deploy to Firebase Hosting
```

### Android app (`app/`)

```bash
./gradlew assembleDebug     # debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease   # release APK (requires KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD env vars)
```

Open in Android Studio → Gradle sync → run on API 24+ device/emulator.

### Firebase

All data lives at `radafiq-272f9` (Firebase project). Firestore security rules are in `firestore.rules`. Deploy them from the Firebase Console or CLI.

## Architecture

### Shared data model (both platforms mirror this)

```
users/{uid}/
  profile/main          # UserProfile (displayName, businessName, email, photo)
  customers/            # CustomerSummary (transactions[], balance, savings, accounts)
  accounts/             # CardSummary (accountKind, bill, pending, payable, used/limit)
  transactions/         # CustomerTransaction (amount, kind, emi, splits, settlements)
  payments/             # Payment records
  savings/              # SavingsEntry (deposit/withdrawal per customer)
```

- `CardSummary` represents bank accounts, credit cards, or persons with amounts tracked.
- `CustomerTransaction` supports EMI (installment schedules), split transactions (across multiple accounts), partial payments, and settlements.
- `AccountKind` enum: `bank_account`, `credit_card`, `person`.

### Android architecture

```
UI (Compose screens) → MainViewModel (StateFlow-based) → FirebaseRepository (Firestore)
```

- **Single ViewModel**: `MainViewModel.kt` (~900+ lines) holds all app state via `StateFlow`. There is no DI framework — the ViewModel manually instantiates `FirebaseRepository()`.
- **Optimistic updates**: `MainViewModel` mutates `_customers` in-memory on write so the UI reflects changes immediately; the Firestore snapshot overwrites with server-confirmed data shortly after.
- **Draft state**: `DraftTransactionState` persists in-progress transaction forms across lock/unlock cycles.
- **Auth**: `CredentialManagerHelper` (Credential Manager API) + Firebase Auth + Google Sign-In.
- **Security**: `AppSecurityRepository` handles passcode (PBKDF2), `BiometricAuthManager` for biometric unlock.
- **Backup**: `DriveBackupRepository` (Google Drive API) + `BackupJsonSerializer` for JSON export/import.
- **Reminders**: `WorkManager` periodic task for credit card due notifications.

### Web architecture

```
React pages → AppContext (React Context) → firebaseRepository.ts (Firestore)
```

- **Single context**: `AppContext.tsx` holds all global state (`cards`, `customers`, `deletedCustomers`, `savingsEntries`, `settlementHistory`, etc.) plus auth, security, backup, and theme logic in one large file.
- **No global store library** — just `useState` + `useCallback` + `useEffect` in a Context provider.
- **Settings** and **security** (passcode hash, salt, recovery) stored in `localStorage` — intentional trade-off (no secure store on web).
- **Passkey/WebAuthn**: `src/utils/passkey.ts` for platform authenticator (Windows Hello, Touch ID).
- **Statement PDF**: Generated client-side via jsPDF in `src/utils/statementGenerator.ts`.

### Cross-platform design system

| Token | Value |
|---|---|
| Primary | #667EEA (Purple) |
| Secondary | #764BA2 (Violet) / #F093FB (Pink) |
| Error | #F5576C |
| Success | #4CAF50 |
| Warning | #FF9800 |

Web uses the same color palette and glass-morphism theme as Android. Both support light/dark mode.

## Build & version config

- **AGP** 9.2.1, **Kotlin** 2.4.0-RC2, **Gradle** 9.5.0
- Version catalog at `gradle/libs.versions.toml`
- Compile SDK 35, Min SDK 24, Target SDK 35
- Compose BOM `2024.12.01` (hardcoded in `app/build.gradle.kts`, overrides catalog value)
- `versionCode = 2`, `versionName = "1.1"` — increment `versionCode` before every Play Store submission (marked `FIX-23`)
- ProGuard enabled for release (`proguard-rules.pro`)
- `isCoreLibraryDesugaringEnabled = true` for `java.time` on older API levels
- Release signing uses env vars: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- ELF patching (`scripts/patch_elf_16kb.py`) runs automatically after native lib merging for 16 KB page alignment on Android 15+ devices

## Codebase quirks

- **`app/build.gradle.kts`** contains inline `FIX-*` comments marking known issues — read them before editing.
- **`web-app/BUGS_FIXED.md`** catalogs 66 bugs (21 fixed, 45 documented but unfixed) — check before editing to avoid reintroducing known issues.
- **No linter, formatter, or test infrastructure** is configured for either app.
- Firebase config files (`google-services.json`, `.env.local`, `src/firebase.ts`) are in `.gitignore` but exist on disk.
- Web app stores passcode hash + settings in `localStorage` — intentional; no secure storage API on web.
- Web app uses `Set` for `selectedAccountIds` in settings (serialized as `Array` in JSON) — be careful comparing/modifying.
- Backup restore writes across collections with no atomic rollback — partial failure can silently corrupt data (BUG-20).
- **AGP deprecation warning**: `Project.android()` extension is deprecated; use `ApplicationExtension` instead. AGP 10 will remove it.
- **Kotlin plugin redundancy**: `org.jetbrains.kotlin.android` is redundant since AGP 9.0; can be removed after migrating to built-in Kotlin support (remove `android.builtInKotlin=true` and `android.newDsl=false` from `gradle.properties`).
- The `CredentialManagerHelper` uses the modern Credential Manager API; the old `GoogleSignInHelper.kt` was deleted. `play-services-auth-base` is kept only for Drive backup token acquisition (`@Suppress("DEPRECATION")` applied).