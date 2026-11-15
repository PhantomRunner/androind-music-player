# Violet Music

Native, offline Android music player with an original Midnight Violet interface. **M0–M2** provide Home, Search, Library, album/artist browsing, Settings, audio permissions, actual MediaStore indexing, a persistent Room catalogue and real Media3 playback. Persistent collection editing is the next implementation step.

## Open and run

Requirements: Android Studio with Android SDK Platform **36**, Build Tools **35.0.0**, and **JDK 17** for Gradle. Minimum Android version: **8.0 / API 26**; compile/target SDK: **36**. Set Android Studio's Gradle JDK to 17; a newer system Java is not automatically compatible with the pinned Gradle release.

1. Open this directory in Android Studio and let Gradle sync.
2. Set `ANDROID_HOME` to your Android SDK directory, or let Android Studio generate an ignored `local.properties` with `sdk.dir`.
3. Start an API 26+ emulator or connect your Android phone with USB debugging enabled.
4. Select the `app` configuration and press Run. For a physical device, accept its USB debugging prompt first.
5. Tap **Allow music access**. Put your own music in the device's Music folder and wait for Android to index it. A scan starts after permission is granted; later scans are available under **Settings → Scan device music**.
6. Browse Library's Tracks, Albums and Artists, and search by title, artist or album. Tap a track to play, or use Play all/Shuffle. Open the mini player for seek, repeat, shuffle and queue controls. Playback continues in the background and exposes system media controls.

Command line, with `JAVA_HOME` pointing to JDK 17 and the SDK configured:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
# An emulator or explicitly selected test device must already be running:
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
# Install only on a device you intentionally choose:
adb -s YOUR_DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Downloading build dependencies requires Internet; the installed app has **no Internet permission**, accounts, network features or telemetry.

## Permissions and catalogue behavior

- API 33+: `READ_MEDIA_AUDIO`. API 26–32: `READ_EXTERNAL_STORAGE`, capped at API 32. No write, all-files, image, video or notification permission.
- Permission is requested only through the user's action. Declining leaves navigation and the saved catalogue usable. Android settings is available when Android stops showing the permission dialog.
- Access is rechecked on Activity resume, and a scan runs on first permitted entry per ViewModel lifetime or after access is regained. Navigation and recomposition do not start new scans. Further refreshes are manual.
- Only MediaStore entries marked as music are indexed; there is no hidden duration cutoff. Files outside Android's music index, unsupported/corrupt files, or files excluded with `.nomedia` may not appear.
- API 29+ scans every currently mounted external MediaStore volume separately; API 26–28 uses the platform's legacy external collection. Metadata is processed in batches of **128**. No audio bytes or filesystem paths enter Room.
- Each app track has a UUID, separate from its unique `(volumeName, mediaStoreId)` asset key. Rescanning updates metadata and keeps the UUID. MediaStore re-indexing or storage resets can change OS IDs; identity across those events is not guaranteed.
- Only a full successful scan, followed by permission and mounted-volume rechecks, can mark unseen assets unavailable. Entries on an unmounted volume remain saved as unavailable. Returning media restores availability and retains identity if the OS key is unchanged.
- Denied, interrupted, cancelled and failed scans never reconcile missing rows. Successfully imported batches can remain after an interrupted scan; the prior successful scan timestamp is retained. No catalogue rows are deleted by indexing.
- File readability/format validation belongs to playback. Indexing records OS metadata and handles missing artwork with an original placeholder. Unavailable items remain visible.
- Search debounces by 300 ms, uses parameterized title/artist/album queries, and treats `%`, `_` and `\` literally. SQLite's built-in case folding handles ASCII; full Unicode-insensitive search is not yet implemented.

## Structure and dependencies

One `app` module, package by feature:

```text
dev.violet.music
  MainActivity / VioletApplication  Android entry points, permission lifecycle, Hilt
  core/model                      Immutable catalogue models
  core/data                       LibraryRepository and Room-backed implementation
  core/database                   Room entities, DAO, transactional catalogue writer
  core/media                      Read-only MediaStore scanner and scan coordinator
  core/playback                   MediaSessionService, controller facade and URI resolver
  core/di                         Application-scoped bindings and database
  core/ui                         Shared components, navigation shell, theme
  feature/home                    Real recent additions and counts
  feature/library                 Shared catalogue ViewModel, tracks/albums/artists
  feature/search                  Debounced local search UI
  feature/settings                Refresh, permission and indexing information
  feature/player                  Mini player, Now Playing and queue
```

Compose reads immutable `LibraryUiState` using `collectAsStateWithLifecycle`; paged lists consume repository `Flow<PagingData<…>>`, with a 250-row page-cache budget per list. Composables never access Room or MediaStore. Hilt supplies one repository/indexer per app; Room is the persistent app-facing source of truth. MediaStore queries run on `Dispatchers.IO`; Room runs database transactions and observable queries on its own executors.

Versions are pinned in `gradle/libs.versions.toml`, using the available stable toolchain rather than tracking latest releases:

| Dependency | Version | Purpose |
| --- | --- | --- |
| Gradle / Android Gradle Plugin | 8.13 / 8.13.0 | Android build; [compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes) |
| Kotlin + Compose compiler / KSP | 2.2.10 / 2.2.10-2.0.2 | Kotlin, Compose and generated Room/Hilt code |
| Compose BOM | 2025.08.00 | UI and Material 3 |
| Core / Activity / Lifecycle | 1.15.0 / 1.10.1 / 2.9.0 | Platform compatibility and lifecycle-aware state |
| Navigation Compose | 2.9.8 | Screen navigation and back stack |
| Room | 2.8.5 | Catalogue, transactions, schema export; [release](https://developer.android.com/jetpack/androidx/releases/room) |
| Paging | 3.3.6 | Bounded Room list loading; [release](https://developer.android.com/jetpack/androidx/releases/paging#3.3.6) |
| Hilt | 2.57.1 | Application/repository/ViewModel injection; [release](https://github.com/google/dagger/releases/tag/dagger-2.57.1) |
| Coroutines | 1.9.0 | Structured asynchronous work and Flow |
| Coil | 2.7.0 | Asynchronous local `content://` artwork loading |
| JUnit / AndroidX test runner | 4.13.2 / 1.7.0 | JVM and instrumented verification |
| Espresso | 3.7.0 | Compose test input compatibility with current Android APIs |
| Media3 ExoPlayer / Session | 1.8.1 | Single background player, media session and system controls |
| Coroutines Guava | 1.9.0 | Cancellable Media3 future/Room coroutine integration |

Database v1 contains only `tracks`, `media_assets`, and `scan_state`. Its generated schema is in `app/schemas`. Future changes must increment the version, export the new schema and supply a tested migration that preserves UUIDs and user data. There is no destructive migration fallback. [Architecture decisions](docs/architecture-decisions.md) explain milestone boundaries, scan completeness and deferred functionality.

## Verification

Run the commands above for build, unit, lint and device verification. Tests cover scan completion/failure/cancellation/overlap, metadata fallback and escaped search; Room integration covers repeat imports, stable IDs, cross-volume identity, missing/restored items, interrupted/denied scan preservation, transactions, paging, search and database reopen. Compose tests exercise navigation and search editing. The MediaStore integration test creates one explicitly named, silent WAV fixture and removes only that fixture afterward; it needs API 29+ and grants the test app audio read permission. Run instrumented tests on an emulator or a device reserved for testing.

Validated on **2026-11-15**, using JDK 17, SDK 36 and a temporary read-only API 37 phone AVD:

```sh
ANDROID_SERIAL=emulator-5584 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --continue --console=plain
# BUILD SUCCESSFUL
```

| Check | Actual result |
| --- | --- |
| Debug APK | Built successfully |
| JVM unit tests | 12 passed, 0 failures |
| Instrumented tests | 12 passed, 0 failures: 9 Room/repository, 1 actual MediaStore, 2 Compose UI |
| Android lint | Passed, no errors; warnings remain |
| Manual emulator smoke check | Permission dialog, real generated MP3 indexing, title/artist search, saved catalogue after process restart and permission revocation verified |
| M0 gate | Build, launch and navigation verified on emulator |
| M1 gate | Catalogue, stable identity, search and failure preservation verified with test media; physical-phone and removable-storage checks below remain pending |

No physical device was connected or modified. API 26–32 permission behavior is statically checked by lint, but was not exercised on an older Android runtime. Actual SD-card detach/reattach and a large personal library have not been measured. The AVD was started without saving snapshots, so verification did not persist changes to the original emulator image.

Manual checks on a physical phone remain useful:

1. Deny audio access, restart, and enable it through Android settings. Confirm catalogue entries remain.
2. Scan at least ten of your own tracks. Search title/artist/album, open an album/artist, restart the app, and confirm the saved catalogue.
3. Repeat a scan and confirm there are no duplicates.
4. Add/remove a test audio file outside Violet, then scan. A removed item should remain visibly unavailable.
5. Unmount/remount a removable volume and scan. Interrupt a scan or revoke access midway; unseen entries must stay saved.
6. Increase system font size, rotate, navigate with TalkBack, and check empty/error states. All interactive controls use at least 48dp touch targets.

## Playback and next milestone

The service owns the only ExoPlayer. UI controllers connect to that session; opening/closing screens or an Activity does not create a new player. The app uses audio focus, pauses on disconnected headphones and holds a playback wake lock. Media3 manages the foreground playback notification; no unrelated notification permission or network permission is requested. Queue metadata is resolved by app UUID in batches, using only local MediaStore assets. Missing/corrupt audio produces a recoverable error; the service advances past failed queue items without an infinite repeat loop. Queue/position are session state; full process death and reboot do not automatically restore/start playback.

M2 verification added two instrumented tests: actual WAV playback, queue/seek/shuffle/repeat, background position advancement, platform media transport controls, reconnection, removal/stop and an unavailable UUID. The full M2 run passed 12 JVM tests and 14 instrumented tests, with build and lint passing. Bluetooth hardware and manufacturer-specific power management remain physical-device checks.

M3 adds persistent playlists/favourites. Server APIs, authentication and sync remain outside the offline MVP; `serverId` is only a nullable storage field.

Git uses `main`, with local milestone commits and no remote. Local starter requirement files are ignored to keep them out of commits.
