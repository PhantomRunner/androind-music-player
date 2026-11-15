# Architecture decisions

## 001 — Scope, identity and storage baseline (2026-11-15)

The milestone prompt authorizes M0/M1, while the technical design's broader phase-one roadmap also describes playback and collections. The milestone takes precedence: implement a real local catalogue now. Defer Media3, playlist/favourite editing, playback history, server features and associated tables until assigned. Version 1 therefore contains only `tracks`, `media_assets` and `scan_state`; additions require explicit non-destructive Room migrations and schema exports.

Use one Kotlin/Compose app module and feature packages. Room owns app-facing catalogue data, the scanner discovers OS audio, and `LibraryRepository` mediates both for a shared catalogue ViewModel. Separate per-screen ViewModels/use-case wrappers are unnecessary for this initial shared read-only feature. Immutable UI state and repository boundaries allow later UI and playback features to use the same catalogue.

Assign UUID track/asset keys. Enforce a unique volume/media-ID index for device asset identity. Metadata upserts preserve UUIDs and optional server IDs, and avoid replace/delete operations that could invalidate future foreign-key references. MediaStore identity can change after an OS re-index, reset or volume change; no hash-based reconciliation is claimed or implemented.

## 002 — Scan completeness and bounded work (2026-11-15)

Scan only on first permitted entry in the current ViewModel lifetime, regained permission, or an explicit refresh. A mutex ignores overlapping refresh requests instead of queuing repeated scans. Query each mounted volume on API 29+ (legacy external collection before that), select OS entries marked as music, and process cursor metadata in batches of 128. Room Paging loads 50 catalogue rows per page with a 250-row page-cache budget (Paging can temporarily retain additional pages while loading); Home observes only eight recent tracks. Dropped pages are reloaded from Room when needed. Full audio bytes are never read during production indexing.

For M1, a bounded full metadata scan is preferable to premature generation-token synchronization: correct reconciliation of removed items and multiple volumes matters first. This is a deliberate tradeoff against the long-term preference for incremental queries. There is no polling, automatic per-screen refresh, custom duration cutoff or benchmark claim. Generation-based incremental updates can be added after measuring large libraries and handling MediaStore version resets.

Commit each import batch transactionally. Only after every mounted-volume query succeeds, permission remains granted and the volume snapshot is unchanged may one transaction reconcile asset/track availability and persist the last-success timestamp. Unseen/unmounted media is marked unavailable, never deleted. A partial failure may leave useful imported metadata, but must not change unseen rows or the previous success timestamp. Cancellation propagates; the next refresh gets a new scan marker. Reappearance of the same OS asset key restores its existing track UUID.

Production indexing treats user audio as read-only. The isolated MediaStore instrumented test is the only code creating/removing an audio fixture; its cleanup targets only the URI that test created.

## 003 — Toolchain, UI and privacy (2026-11-15)

Choose the installed API 36 SDK, minimum API 26, JDK 17, cached Gradle 8.13/AGP 8.13.0 and Kotlin/Compose compiler 2.2.10. Dependency coordinates are resolved by the actual build and pinned in the version catalog. Hilt 2.57.1 provides DI and Paging 3.3.6 prevents whole-library UI list materialization. Room 2.8.5 uses KSP and exports schema v1. These are stable compatible pins; dependency-update lint notices alone do not justify migrating the build to AGP 9.

Use the specified Midnight Violet palette, original geometric vector icons and Coil for local artwork. No generated artwork or fabricated tracks are shown. Track rows are informational until real playback exists; only an empty mini-player slot is provided in the app scaffold. Theme/about cards have no misleading clickable actions. Strings use resources and quantity forms; the initial UI is English.

Request only OS-version-appropriate read-audio permission. Recheck on resume, show a rationale and Android-settings recovery path, and preserve the catalogue when access is absent. The installed application has no Internet permission, network feature, telemetry or audio modification operations. Disable cloud backup and device transfer of app data: device-specific MediaStore references should not be silently restored to another device. Local UUID/server-ID separation keeps future integration possible without implementing networking now.

Search uses parameterized, wildcard-escaped SQLite `LIKE` over titles, artists and albums. Its 300ms debounce keeps typing responsive. Full Unicode case folding and FTS are deferred, with the current ASCII case-folding limit documented in the README.

Instrumented search testing exposed that the Room/KSP-generated query dropped a backslash escape literal. Use the ordinary `!` SQL escape marker and escape that marker as well as `%` and `_`; backslashes remain literal input. Pin Espresso 3.7.0 explicitly for UI tests because the older transitive version calls the removed reflective `InputManager.getInstance` on the available API 37 emulator. [The upstream release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0) document its switch to `getSystemService`. These changes affect query correctness and test compatibility respectively.

## 004 — Authorized offline MVP, playback first (2026-11-15)

The user explicitly expanded the task from the catalogue to a usable offline MVP and authorized intermediate commits. Implement M2, verify it, then M3 and quality work. Networking remains excluded. Existing starter instructions/design files remain local and uncommitted.

M2 uses Media3 1.8.1 ExoPlayer and MediaSession with the existing compatible Kotlin/SDK toolchain; [the stable maintenance release](https://developer.android.com/jetpack/androidx/releases/media3#1.8.1) is verified and the actual dependency build resolves it. Coroutines Guava converts Media3 futures to cancellable coroutine work. The only ExoPlayer lives in `PlaybackService`. The application-scoped controller facade exposes one immutable state, and UI ViewModels acquire/release controller clients without owning the player. Unexpected disconnects trigger at most three reconnect attempts and expose manual recovery. Position is sampled every 500ms only while playing and observed; it is never written to Room on progress ticks.

Catalogue taps capture the selected query/album/artist ordering as a queue of available UUIDs. Home uses its eight visible recent IDs. Metadata/URI resolution queries chunks of 100; the active media queue necessarily retains its own metadata, while catalogue UI remains paged. Resolve every incoming item by UUID, ignoring incoming URIs, and verify the selected file can be opened read-only before replacing the queue. System transports use the same session. Missing/corrupt queued items advance to an untried item; a failed queue cannot loop forever under repeat. No automatic playback after process death/reboot is implemented.

Declare foreground media-playback and wake-lock permissions for background playback. Media3 owns the media notification and default task-removal behavior: ongoing audio continues when the UI is dismissed. No Activity-owned player, streaming source, audio writes or network permissions are added. Instrumented tests verify real playback, seek/queue/repeat/shuffle, background progress, platform transport commands, reconnection and unavailable IDs.

## 005 — Persistent collections and migration (2026-11-15)

Room v2 adds favourites, playlists and entry tables through explicit additive `MIGRATION_1_2`; exported v1 is retained for MigrationTestHelper. Existing catalogue identity and scan state remain untouched. Playlist entries have independent UUIDs and indexed positions, so duplicates are intentional and reorder/removal operate on an entry rather than a track. Only an explicit user-confirmed playlist deletion cascades its entries. Track foreign keys restrict deletion; scans never delete collection references.

All collection operations pass through `CollectionRepository`. Appends/reordering/removal are transactional; range updates keep positions contiguous without rewriting every row. Name validation rejects blank/over-120-character values rather than silently truncating. Favourites are projected into observable Room track queries; there is no second in-memory favourites store. Lists/choosers are paged, while playback captures only ordered available entry IDs and resolves actual metadata through the existing resolver. If a selected entry becomes unavailable, report that failure rather than silently playing another entry.

Move the expanded navigation graph into `AppNavigation` to keep the shell and feature screens small. Dialog state is transient UI state; durable state stays in Room. Align kotlinx.serialization core/JSON via BOM 1.8.1 because Android test consistent resolution otherwise selects the app's older core runtime and Room MigrationTestHelper fails with `GeneratedSerializer` ABI mismatch. This updates an existing transitive runtime; no serialization feature or network data flow is added.

## 006 — Offline MVP quality boundary (2026-11-15)

Version 0.3.0 completes the offline MVP: catalogue/search, real background/session playback, queue/seek/repeat/shuffle, favourites and playlist CRUD/reordering. Full process-death queue resumption, listening history, production publishing and all server features remain deferred. No additional modules, network features or audio modification APIs are added.

Use wrapping action layouts and a single-line ellipsized toolbar title for system font scaling. An unavailable track's row is not a disabled interactive ancestor: its collection menu stays accessible. Expose reconnect actions when the session is unavailable, release disconnected controller clients and check connection state before reuse.

Playback error recovery ignores repeat-one only while advancing past a failed item; valid playback still honors repeat-one. Track failed UUIDs within the queue so repeat-all cannot loop forever over missing/corrupt sources. An instrumented test exercises duplicate selection, a missing middle item under repeat-one, and an entirely missing queue under repeat-all without deleting catalogue rows. Recovery is player/session behavior, not a reason to remove user collection data.

Namespace shared lazy-list notice keys to avoid collisions with Settings actions while scanning or showing an error. The end-to-end collection test explicitly refreshes after adding its fixture, matching the production rule that Activity recreation does not trigger another full scan. Verify with ten labelled native MP3 files and system font scale 200%, and verify a favourite survives force-stop/restart plus reconciliation.

The final APK audit found `ACCESS_NETWORK_STATE` supplied transitively by Media3 common; remove it in the app manifest because this MVP uses only local sources. Playback and the full regression suite pass without it. Keep the session service exported as required for system clients, with the explicit trusted-controller/app-package gate and UUID-only source resolution. Final M0–M4 verification passes 14 JVM and 22 instrumented tests, debug assembly and lint (warnings remain); physical hardware checks stay documented in the README.

## 007 — Direct track selection inside custom playlists (2026-11-16)

The playlist editor's Add tracks action previously navigated to the general Library without retaining an explicit destination or presenting track selection. Keep playlist creation and existing track-menu actions, and open a dedicated `playlist/{id}/add-tracks` destination from the editor instead. Search is independent of Library/Search and saved through the collection ViewModel. The picker reads paged catalogue data and reuses the existing selection repository's complete-query reads, bounded transactions and idempotent entry writes. Confirm appends directly to the route's playlist; cancel before confirmation writes nothing. Successful completion returns to the editor without changing playback. Missing playlists and catalogue/permission failures retain explicit states.

Selection cleanup checks which destination currently owns it so a departing playlist screen cannot erase the picker's selection during a navigation animation. Preserve picker selection during Activity configuration recreation; do not save large selected-ID collections in state Bundles. The regression scenario covers creation, picker navigation, cancellation, searching, selecting 65 songs across unloaded pages, Activity recreation and ordered persistent appends.
