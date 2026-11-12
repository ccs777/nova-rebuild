# NovaRebuild launcher

A home-screen replacement built from the behaviour recovered from Nova Launcher 8.0.18
(`com.teslacoilsw.launcher`, versionCode 80018). Kotlin, plain Android Views, no Compose or
Material dependency; one launcher module plus a synthetic icon pack used by the tests.

## Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > local.properties
~/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`testpack` builds a minimal ADW icon pack (`dev.novare.testpack`) for exercising icon-pack
loading:

```bash
gradle :testpack:assembleDebug
adb install -r testpack/build/outputs/apk/debug/testpack-debug.apk
```

Long-press an empty area of the workspace → *NovaRebuild Settings* to reach preferences. The app
is a normal launcher: set it as the default home app to use it as HOME.

## Features

- **Workspace** — paged grid (`PagedView` + `CellLayout`), cell coordinates with spans, page
  indicator, spare trailing page, long-press drag with auto page flip at screen edges.
- **Dock** — fixed-slot dock whose size is a preference; dropping onto an occupied slot re-seats
  the previous item.
- **Folders** — drop an icon on another icon to create a folder, drop on a folder to append,
  tap to open, grid inside the folder, rename via the title field, drag out to remove.
- **Drag & drop** — long-press starts a drag (`DragController` + `DragLayer`), delete target at
  the top of the screen, drop without moving opens the item menu.
- **App drawer** — all launcher activities via `PackageManager`, search, tabs backed by
  `drawer_groups`/`appgroups`, long-press menu to add to home, add to tab, hide, app info.
- **Icon packs** — ADW/Go/Apex discovery, `appfilter.xml` mapping, legacy
  `iconback`/`iconmask`/`iconupon` composition, per-component custom icons from a picked image.
- **Settings** — grid columns/rows, dock count, icon size, swipe gestures, drawer tabs, hidden
  apps, icon pack, backup/restore/reset. Grid changes re-seat items that no longer fit.
- **Backup** — ZIP with `meta.json`, `favorites.json` and `nova.xml` (preferences); restore
  replaces the item graph and preferences.
- **Gestures** — swipe up opens the drawer; swipe down asks the accessibility service to expand
  the notification shade.

## Data model

`LauncherDb` uses the `favorites` schema recovered from the Nova artifact (including
`flingUpIntent`, `flingDownIntent`, `zOrder`, `novaFlags`), plus `drawer_groups`, `appgroups` and
`customIcons`. Containers follow the AOSP negatives: `DESKTOP=-100`, `HOTSEAT=-101`; a folder's
children carry the folder row id as their container. `customIcons` stores a
`component → content://` mapping for user-chosen icons.

Preferences live in `shared_prefs/nova.xml` and reuse Nova's key names where they overlap
(`workspace_columns`, `workspace_rows`, `dock_count`, `icon_size`, `icon_pack`,
`swipe_up_drawer`, `swipe_down_notifications`, `drawer_tabs`, `hidden_apps`).

## Backup archive

```
meta.json        {"format":"novarebuild","version":1,"created":…}
favorites.json   {"desktop":[item|folder…],"hotseat":[…],"tabs":[…],"customIcons":{…}}
nova.xml         {"workspace_columns":"5",…}
```

Folders nest `children` inside their JSON object; hotseat entries are indexed by array position.
Restore clears `favorites`/`drawer_groups`/`appgroups`/`customIcons`, rewrites them from the
archive, replaces preferences and bumps `layout_revision` so a running launcher reloads.

## Layout of the code

```
app/src/main/java/dev/novare/launcher/
  LauncherActivity.kt          wiring, drop resolution, menus, gestures
  SettingsActivity.kt          SAF backup/restore host
  SettingsFragment.kt          preferences, icon-pack list, hidden apps
  BackupManager.kt             archive read/write
  LauncherAccessibilityService.kt
  app/AppRepository.kt         launcher activities, package-change refresh
  app/IconCache.kt             custom icon → pack → app icon
  app/IconPackManager.kt       pack discovery, appfilter parsing, composition
  db/LauncherDb.kt             schema
  db/LauncherStore.kt          item graph ↔ favorites rows
  db/LauncherPrefs.kt          settings
  model/ItemInfo.kt            ItemInfo / AppItem / FolderItem
  ui/CellLayout.kt             grid placement, hit testing, long-press
  ui/PagedView.kt              snapping pager
  ui/DragLayer.kt              drag interception, vertical swipe detection
  ui/DragController.kt         drag bitmap and touch stream
  ui/AppIconView.kt            icon + label, cell-aware sizing
  ui/AllAppsView.kt            drawer with search and tabs
  ui/FolderView.kt             open-folder overlay
  ui/FolderPreviewDrawable.kt  2×2 folder preview
  ui/ComposedIconDrawable.kt   iconback/iconmask/iconupon
```

## Known limits

Widgets (`AppWidgetHost`), span resizing, notification badges, Sesame integrations and Nova's
scroll effects are not implemented. The backup format matches Nova's ZIP-with-prefs shape but
stores the item graph as JSON rather than copying `nova.db`.
