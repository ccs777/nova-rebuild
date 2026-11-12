# Roadmap

Milestones ordered by what blocks daily use. Each one ends with a device gate; nothing here is
"done" until its gate has been exercised on hardware.

| Milestone | Goal | Gate |
|---|---|---|
| M0 Shippable base | signed release, `targetSdk 35`, `LauncherApps`-first app list (no `QUERY_ALL_PACKAGES`) | signed APK installs as default home |
| M1 Model + tests | incremental `LauncherModel`/store writes, unit tests for store/backup/mapping | drop on page 5 stays within a frame; `:app:testDebugUnitTest` green |
| M2 Widgets | `AppWidgetHost`, placement, resize via `spanX/spanY`, restore-time re-bind | clock + calendar placed, resized, survive process death and backup restore |
| M3 Shortcuts | deep + legacy shortcuts, per-item swipe-up action | shortcut launches and survives reload |
| M4 Icons | normalization, adaptive shape override, mask fallback | mixed pack/unpacked icons render at equal weight |
| M5 State & displays | `onSaveInstanceState`, landscape/tablet grids, secondary display | process kill with drawer open restores to the drawer |
| M6 Parity extras | read Nova's own `.novabackup`, search providers, settings search | import a real Nova backup |

## Status

- M0: done. `targetSdk 35`, `QUERY_ALL_PACKAGES` removed (LauncherApps first, PackageManager
  fallback behind manifest `<queries>`), release signing from `keystore.properties`. Gate passed:
  signed APK installs and runs; drawer lists apps through the fallback path when the app is not
  the default home.
- M1: done. `LauncherModel` + `LauncherChange`, incremental store writes, `readCommon` now
  round-trips `container` and `_id` (both bugs caught by the new suite). Gate passed:
  `:app:testDebugUnitTest` green (6 tests) and the activity no longer calls `store.save`.
- M2: placement + bind flow verified on device (picker → `AllowBindAppWidgetActivity` → hosted
  Chrome search widget). Span resize and restore-time re-bind are implemented, not yet exercised.
- M3–M6: not started.

## Known debt

- `LauncherActivity` still rebuilds every page after a mutation; M1 replaces that with model
  callbacks.
- Widget bind permission flow (`ACTION_APPWIDGET_BIND`) is not wired yet.
- Backup stores `favorites.json` rather than a `nova.db` copy; M6 adds a Nova-format reader.
