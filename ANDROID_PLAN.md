# Android app plan

Turn the workout tracker into a sideloaded native Android app with an in-app updater.
No Play Store and no GitHub Pages: the app is fully offline, its data lives in the app's
own storage, and new versions are GitHub Releases that the app checks for and installs.

Written 2026-09-27, at the end of the `redesign/v2` work (last commit `e62ec50`).

---

## Decisions already made

| Topic | Decision |
|---|---|
| Distribution | Sideload an APK from **GitHub Releases** on `nbilic/w-tracker` (public repo). No Play Store. |
| Hosting | **Retire GitHub Pages** once the app is installed and data is migrated. |
| App shell | **Capacitor**, with the web files **bundled inside the APK** (no remote `server.url`). Fully offline. |
| Updates | In-app check of the latest GitHub Release, then download and install the APK. The user confirms Android's install prompt, which is unavoidable for sideloaded apps. |
| Builds | **GitHub Actions** builds and signs the APK on a version tag. No Android Studio needed locally. |
| Signing | One release keystore, created locally with `keytool` (Java 22 is installed), stored as repo secrets. **Never lose it**: updates must use the same key. |
| Storage | App-private WebView storage, plus a JSON mirror file covered by **Android Auto Backup** (to the user's Google account). |
| Later, optional | A "live update" plugin (e.g. Capgo self-hosted) to swap web files without reinstalling. Only if the per-update install prompt gets annoying. |

---

## Current state (facts to build on)

- **App:** a single file, `index.html` (~4.4k lines, vanilla JS), plus `sw.js`, `manifest.json`, `icon-192.png` and `icon-512.png` at the repo root.
- **Branches:**
  - `redesign/v2` has all recent work and is **14 commits ahead of `main`**, which it hasn't been merged into.
  - An old branch, `origin/native/android` (commit `fa05774`, April), scaffolded Capacitor **6.x** with `appId: dev.nbilic.workout`, `appName: Workout Tracker`, `webDir: www`, minSdk 22 / compileSdk 34 / targetSdk 34. It **moved `index.html` into `www/`** and predates the redesign. **Don't merge it.** Use it only as a reference.
- **Local leftovers (untracked, don't commit):** `android/` and `www/` from checking out that branch, `node_modules/`, and `.redesign_handoff/` (the design bundle). There's no `.gitignore` yet.
- **Storage keys (all `localStorage`):**
  - `workoutData`: all app data
  - `workoutBackup`: the in-progress session
  - `w-snapshot`: undo for import/delete
  - `w-theme`
  - `w-onboarded`
- **Web-only APIs that need a native replacement or removal** (line numbers approximate):

| Where | Web API today | Native replacement |
|---|---|---|
| `<head>` L13–15 | Google Fonts CDN (Fraunces, Geist, Geist Mono) | **Bundle the font files locally** (OFL-licensed); otherwise there are no fonts offline |
| `triggerHaptic` ~L1292 | `navigator.vibrate` | `@capacitor/haptics` |
| `acquireWakeLock` ~L1299 | Screen Wake Lock API | `@capacitor-community/keep-awake` |
| `requestNotificationPermission` / `sendRestNotification` ~L1309–1320 | Notification API via the service worker, fired when the rest timer ends (unreliable when the phone is locked) | `@capacitor/local-notifications`: **schedule** the alarm at `restStartedAt + restDuration` when a rest starts; reschedule on ±30; cancel on dismiss |
| `unlockAudio` / beep ~L1326 | Web Audio | Keep it (works in the WebView). The scheduled notification's sound covers the locked-phone case |
| `showScreen` / `popstate` ~L1436–1456 | History API for back navigation | `@capacitor/app` `backButton` listener: close the open sheet → go back a screen → minimize on home |
| boot ~L4405 | `serviceWorker.register('sw.js')` | Skip on native (`Capacitor.isNativePlatform()`); drop `sw.js` from the app build |
| boot ~L4407 | `navigator.storage.persist()` | Not needed on native (harmless); skip it there |
| `backupData` ~L4090 | Web Share with a `.txt` file | `@capacitor/filesystem` (write to cache) + `@capacitor/share`, so a real `.json` file can be shared again |
| Settings footer | Hard-coded "REDESIGN v2.0" | `App.getInfo()` version |

---

## Phases

### Phase 0: Tidy the repo (short)
1. Merge `redesign/v2` into `main` (open a PR or fast-forward, whichever the user prefers). Native work starts from `main` on a new branch, `native/app`.
2. Add a `.gitignore` covering `node_modules/`, `android/app/build/`, `www/`, `.redesign_handoff/`, `*.keystore` and `*.jks`.
3. Delete the stale local `android/` and `www/` leftovers **after** checking they're untracked junk (they are, as of this writing).

**Done when:** `main` has the redesign, `native/app` is created, and `git status` is clean.

### Phase 1: Capacitor project with bundled files
1. Add `package.json` with the latest stable `@capacitor/core`, `@capacitor/cli` and `@capacitor/android` (check npm; the April scaffold used 6.x).
2. **Keep `index.html` at the repo root** (don't move it like the old branch did). Add a small build script (`scripts/build-web.js`) that copies `index.html`, the icons and the bundled fonts into `www/`, and **strips the service worker registration**. `www/` stays gitignored; it's a build output.
3. `capacitor.config.json`: `appId: dev.nbilic.workout`, `appName: Workout Tracker`, `webDir: www`, `backgroundColor: #0e0d0c`, `android.webContentsDebuggingEnabled: false` for release builds.
4. `npx cap add android` → a fresh `android/` project, committed. Set the targetSdk to what the installed Capacitor version recommends.
5. Bundle the fonts: download the woff2 files for Fraunces (600/700, normal + italic), Geist (400–700) and Geist Mono (400–700) into `fonts/`, and replace the Google Fonts `<link>` with local `@font-face` rules. This also works for any web preview.
6. App icon: generate the Android launcher icons from `icon-512.png` (adaptive icon: foreground plus the `#0e0d0c` background). The current icon is the **old neon-pink design with a broken "W"** glyph, so it's worth a quick ember-style redo here.

**Done when:** `npx cap sync android` succeeds and the Gradle debug build (`./gradlew assembleDebug`, first run in CI, see Phase 4) produces an APK that opens the app offline with the correct fonts.

### Phase 2: Native behaviour
Use `Capacitor.isNativePlatform()` so the same `index.html` still works in a browser for quick previews/tests.
1. **Haptics:** `triggerHaptic` → `@capacitor/haptics` (keep the pattern mapping; on native, use `Haptics.impact`/`vibrate` with durations).
2. **Keep the screen awake:** `acquireWakeLock`/`releaseWakeLock` → keep-awake plugin.
3. **Rest alarm on a locked phone:** schedule a local notification when a rest starts, reschedule on ±30, cancel on `dismissRest`/finish. Request notification permission on the first workout (Android 13+). If using exact alarms, check the Android 12+/14 exact-alarm permission and fall back to inexact scheduling if it's denied.
4. **Back button:** `App.addListener('backButton', …)` → close any open `.sheet-overlay.active` → otherwise go back through screens (reuse the current screen stack) → on home, `App.minimizeApp()`. Remove the `popstate` handling on native.
5. **Backup:** write the JSON to `Filesystem` (cache directory) as `workout-backup-YYYY-MM-DD.json`, then `Share.share({ files: [uri] })`. Restore keeps using the file input (the WebView's file chooser works with Capacitor).
6. **Auto Backup safety net:** after each `saveData`, debounce-write `workoutData` to `Filesystem` (`Directory.Data`, e.g. `data/workoutData.json`). On boot, if `localStorage` is empty but that file exists (e.g. after reinstalling or moving to a new phone), offer to restore from it. Make sure the manifest has `android:allowBackup="true"`. Auto Backup covers the app's files directory, runs roughly daily (idle + charging + Wi-Fi) and has a 25 MB cap, which is plenty.
7. **Version label:** Settings shows `App.getInfo().version`.

**Done when:** on a real phone the rest alarm fires with the screen locked, the back button behaves, the screen stays awake during a workout, and backup shares a `.json` to Drive.

### Phase 3: In-app updater
1. On launch (at most once every ~6 h, and never mid-workout), `fetch('https://api.github.com/repos/nbilic/w-tracker/releases/latest')`. No auth is needed for a public repo; the limit is 60 requests per hour per IP.
2. Compare `tag_name` (e.g. `v1.4.0`) with `App.getInfo().version`. If it's newer and has an `.apk` asset, show a quiet row on Home (in the same style as the backup nudge): `Update available · v1.4.0 · Install`.
3. **Install, option A (preferred):** a tiny custom Capacitor plugin in the Android project (~50 lines of Java/Kotlin) that downloads the APK with `DownloadManager` (or `Filesystem.downloadFile`) into app storage, then launches the installer with a `FileProvider` content URI and `ACTION_VIEW` / `application/vnd.android.package-archive`. It needs the `REQUEST_INSTALL_PACKAGES` permission and a FileProvider entry in the manifest. The first time, Android sends the user to "Install unknown apps → allow for Workout Tracker".
4. **Install, option B (fallback, zero native code):** open `browser_download_url` in the system browser; Chrome downloads it and the user taps the downloaded file to install. Keep this as the fallback if option A fails.
5. Show the release notes (the release `body`) in a small sheet before installing.

**Done when:** installing an older build and then publishing a newer Release leads to "Update available" → Install → Android's prompt → the app restarts on the new version **with all data intact**.

### Phase 4: CI releases (GitHub Actions)
1. **Keystore (the user does this once, locally):**
   `keytool -genkeypair -v -keystore workout-release.jks -alias workout -keyalg RSA -keysize 2048 -validity 10000`
   Back up the `.jks` file and its passwords somewhere safe **outside** the repo (password manager + Drive).
2. **Repo secrets:** `ANDROID_KEYSTORE_B64` (the base64 of the `.jks`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`.
3. **`android/app/build.gradle`:** a `signingConfigs.release` block reading from environment variables; `versionName` from the tag (`1.4.0`) and `versionCode` derived from it (`major*10000 + minor*100 + patch`) or `github.run_number`, always increasing.
4. **`.github/workflows/release.yml`,** triggered on tags `v*`:
   - checkout → setup-node → `npm ci` → `node scripts/build-web.js` → `npx cap sync android`
   - setup-java (Temurin 21) → decode the keystore → `./gradlew assembleRelease`
   - create a GitHub Release for the tag with the signed APK attached (e.g. `softprops/action-gh-release`), with notes taken from the tag message or auto-generated.
5. **Releasing from then on:** bump the version, `git tag v1.4.0 && git push origin v1.4.0`, and the Action publishes the Release; the phone sees it on its next launch.

**Done when:** pushing a tag produces a Release with a signed APK within about 10 minutes.

### Phase 5: Switch over
1. Build `v1.0.0` through CI and install it on the phone from the Release page (the first install is manual).
2. In the **current web app**: Settings → Back up. In the **native app**: Settings → Restore from backup (it accepts `.txt`/`.json`). Check History, PRs, routines, learned rest times and notes all came across.
3. Use the native app for a week of workouts. Once happy, **turn off GitHub Pages** (repo Settings → Pages → Unpublish/None) and uninstall the old web app from the home screen.
4. Clean up the web-only code that is now dead on native, or keep it behind `isNativePlatform()` checks if browser previews are still useful for development.

---

## Risks and gotchas
- **Lost keystore = no in-place updates.** You'd have to uninstall (wiping data unless backed up) and reinstall with a new key. Back up the `.jks` twice.
- **Install prompts can't be skipped.** Every update needs one tap on Android's prompt.
- **Exact alarms:** on Android 14+, `SCHEDULE_EXACT_ALARM` isn't granted by default. The rest alarm must degrade gracefully (inexact, or just the in-app beep).
- **WebView differences:** check `color-mix()`, `overflow-x: clip`, `backdrop-filter` and the `inputmode` keyboards on the phone's WebView version (Android System WebView updates via the Play Store, so it's usually current).
- **The first Gradle build is slow** (~5–10 min in CI). Cache Gradle in the workflow.
- **GitHub API rate limit** (60/h, unauthenticated) is fine for one user checking at most every few hours.
- **Auto Backup** depends on the phone's Google backup being enabled (Settings → Google → Backup). Check it once.

## What the user needs to do (everything else is code)
1. Decide how to merge `redesign/v2` into `main` (PR or fast-forward).
2. Run the `keytool` command once, and add the 4 repo secrets (GitHub → Settings → Secrets and variables → Actions).
3. On the phone: install the first APK, allow "install unknown apps" when prompted, and do the backup → restore migration.
4. After a trial week, unpublish GitHub Pages.

## Suggested session order
Phase 0 → Phase 1 + Phase 4 together (you need CI to get a buildable APK without Android Studio) → install and check on the phone → Phase 2 → Phase 3 → Phase 5.
