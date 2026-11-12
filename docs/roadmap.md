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
| M6 Parity extras | read Nova's own `.pulsarbackup`, search providers, settings search | import a real Nova backup |

## Status

- M0: done. `targetSdk 35`, `QUERY_ALL_PACKAGES` removed (LauncherApps first, PackageManager
  fallback behind manifest `<queries>`), release signing from `keystore.properties`. Gate passed:
  signed APK installs and runs; drawer lists apps through the fallback path when the app is not
  the default home.
- M1: done. `LauncherModel` + `LauncherChange`, incremental store writes, `readCommon` now
  round-trips `container` and `_id` (both bugs caught by the new suite). Gate passed:
  `:app:testDebugUnitTest` green (6 tests) and the activity no longer calls `store.save`.
- M2: done. Placement + bind permission flow exercised on device (picker →
  `AllowBindAppWidgetActivity` → hosted Chrome search widget); resizing the frame wrote 4x2 spans to
  `favorites`; after a process kill the frame and its widget id were restored. Fixes found by the
  gate: `AppWidgetHost` has no `deleteView` (detach instead), a host view cannot be re-parented
  without removing it first, widget rows were dropped by `LauncherStore.load()` because they have
  no intent, and widget restore ran before the host manager was constructed.
- M3: shortcut discovery (`LauncherApps` first, legacy `ACTION_CREATE_SHORTCUT` fallback), picker
  sheet, and "add to home" integrated into the item menu. Device gate not met on the RS90: it is
  not the default home, so `hasShortcutHostPermission()` is false and only legacy shortcuts are
  visible; needs a device where the launcher holds the role.
- M4–M6: not started.

## Known debt

- `LauncherActivity` still rebuilds every page after a mutation; M1 replaces that with model
  callbacks.
- Widget bind permission flow (`ACTION_APPWIDGET_BIND`) is not wired yet.
- Backup stores `favorites.json` rather than a `pulsar.db` copy; M6 adds a Nova-format reader.

## Backlog: Octopi review (unscheduled)

Features observed in Octopi Launcher (Home Screens) from its
[store listing](https://appgoblin.info/apps/com.otp.octopilauncher) and
[APKMirror changelogs](https://www.apkmirror.com/apk/octopiappfactory/octopi-launcher/). Not yet
assigned to a milestone; each needs a device gate before it counts as done.

| Area | Feature | Notes |
|---|---|---|
| Widgets | auto-scrolling widget stacks | depends on M2 widget hosting |
| Widgets | floating or pinned widgets above the home screen | depends on M2 |
| Widgets | glass-style widget backgrounds | cosmetic |
| Drawer | folders and favorites inside the drawer | `drawer_groups` already covers tabs |
| Drawer | tag-based app organization | extends `appgroups` |
| Drawer | index bar and reachability mode | |
| Gestures | swipe on an icon to launch a different app | per-item swipe action, pairs with M3 |
| Gestures | screen-off via AccessibilityService | `LauncherAccessibilityService` already exists |
| Layouts | separate portrait and landscape layouts | pairs with M5 |
| Layouts | foldable layouts per screen | pairs with M5 |
| Profiles | private space and work profile support | |
| Icons | resizable individual home icons | `spanX/spanY` model |
| Icons | hide status and navigation bars | |
| Icons | notification badges | needs notification listener access |
| Home | lock the home layout against edits | |
| Search | launch an app by a nickname | extends `search` providers in M6 |
| Assistant | set the launcher as the assistant app | |
