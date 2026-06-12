# Radafiq — Agent Guide

## Repo overview

Monorepo with two independent apps sharing a Firebase Firestore backend:

- **`app/`** — Android (Kotlin, Jetpack Compose, MVVM, Navigation Compose, Gradle 9.5.0)
- **`web-app/`** — PWA (React 18, TypeScript, Vite 6, React Router v6)

Both sync to the same Firestore database at `radafiq-272f9` (already configured). Data is per-user at `users/{uid}/...`. No shared code between apps — they mirror each other.

## Commands

### Web app (`web-app/`)

```bash
npm run dev       # dev server on port 3000
npm run build     # tsc + vite build → dist/
npm run preview   # vite preview
npx firebase deploy --only hosting  # deploy to Firebase Hosting
```

Env vars: `VITE_*` prefix, loaded via `import.meta.env`. Copy `.env.example` → `.env.local`. Already done.

### Android app (`app/`)

```bash
./gradlew assembleDebug  # debug APK
./gradlew assembleRelease # release APK (requires signing env vars)
```

Open in Android Studio → Gradle sync → run on API 24+ device/emulator.

## Build & config notes

- **AGP 9.2.1**, Kotlin 2.4.0-RC2, Gradle 9.5.0 (version catalog at `gradle/libs.versions.toml`)
- Compile SDK 35, Min SDK 24, Target SDK 35
- Compose BOM `2024.12.01` with Material 3 (hardcoded in `app/build.gradle.kts`, overrides catalog value)
- Release signing uses `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` env vars (see `app/build.gradle.kts:34-38`)
- `versionCode = 2`, `versionName = "1.1"` — increment before Play Store submission
- ProGuard enabled for release (`proguard-rules.pro`)
- `isCoreLibraryDesugaringEnabled = true` for `java.time` on older API levels

## Architecture

- **View layer** → `app/src/main/java/com/radafiq/ui/` (Compose screens) or `web-app/src/pages/`
- **State** → `MainViewModel.kt` (Android) or `AppContext.tsx` (web, React Context)
- **Data** → `FirebaseRepository.kt` (Android, 934 lines) or `firebaseRepository.ts` (web, ~600 lines)
- **Auth** → Firebase Auth + Google Sign-In (both platforms)
- **Security** → `AppSecurityRepository`/`AppLockScreen` (Android) or `security.ts` + `AppLock.tsx` (web, localStorage-based)
- **Backup** → JSON export/import + Google Drive (Android uses Drive API; web Drive sync is a stub — BUG-11)

### Firestore collections (per user)

```
users/{uid}/profile/main, customers/, accounts/, transactions/, payments/, savings/
```

## Codebase quirks

- `app/build.gradle.kts` contains inline `FIX-*` comments marking known issues — read them before editing
- Web app stores passcode hash + settings in `localStorage` (no secure store on web) — intentional
- `BUGS_FIXED.md` in `web-app/` catalogs 66 bugs (21 fixed, 45 documented but unfixed) — check before editing to avoid reintroducing known issues
- No linter, formatter, or test infrastructure is configured for either app
- `CredFlowApp/`, `passkey-demo/`, `.features/`, `figma-plugin/`, and `build/` were removed as unused (May 2026)
- Firebase config files (`google-services.json`, `.env.local`, `src/firebase.ts`) are in `.gitignore` but exist on disk
- Web app uses `Set` for `selectedAccountIds` in settings (serialized as `Array`) — be careful when comparing/modifying
- Backup restore writes across collections with no rollback — partial failure can silently corrupt data (BUG-20)
- AGP 9.2.1 build warns: `Project.android()` extension is deprecated; use `ApplicationExtension` instead. AGP 10 will remove it
- AGP 9.2.1 build warns: `org.jetbrains.kotlin.android` plugin is redundant since AGP 9.0; can be removed after migrating to built-in Kotlin support (remove `android.builtInKotlin=true` and `android.newDsl=false` from `gradle.properties`)
