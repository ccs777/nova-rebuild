# Nova Launcher 8.0.18 — artifact investigation and reconstruction

> Publishing note: the pulled APK, the decompiled sources and the runtime captures referenced
> below stay on the analysis workstation. They are proprietary third-party material and are not
> committed here; only the findings and the reconstruction are.

Reverse-engineering pass over the installed Nova Launcher, followed by a working Kotlin/Android
launcher rebuilt from what the artifact actually does. Every claim below is tied to an artifact in
`evidence/`, a decompiled source path, or a live device observation.

## Target

| Field | Value | Source |
|---|---|---|
| Package | `com.teslacoilsw.launcher` | `pm path` / manifest |
| Version | 8.0.18 (versionCode 80018) | `dumpsys package` |
| minSdk / targetSdk / compileSdk | 26 / 33 / 34 | `aapt2 dump badging` |
| Artifact | `artifacts/nova-8.0.18-base.apk`, 11 553 431 bytes | `adb pull` from `/data/app/.../base.apk` |
| SHA-256 | `56c2cf80bd689faf0cf20e64ad1e2d87622e3d159d798c0045d6cff6d11bec2f` | `shasum -a 256` |
| Code | single `classes.dex`, 9 367 656 bytes, 11 242 classes | `dexdump -f` |
| Obfuscation | classes + resource names (`res/zz.xml`, `res/zp.png`) | `unzip -l` |

REA evidence record: `ev_227d8ea395fe8b298655f068ab370829aa893d0fbbc9476896868336b369e512`
(`inspect_artifact`, coverage `complete-within-substeps`). REA MCP was not registered in this
session (`doctor`: `config_drift` for `~/.omp/agent/mcp.json`), so REA ran through its CLI
(`npx rea-agents@6.7.0`); Android code tools were unavailable because no JADX MCP jar is
installed, so decompilation used `jadx 1.5.6` and manifest decoding used `aapt2`/`apktool`.

## What the artifact shows

**Structure.** Nova is an AOSP `com.android.launcher3` fork with Nova layers added under
`com.teslacoilsw.launcher.{preferences,drawer.drawergroups,theme,search,screenoff,wallpaper}`.
Manifest inventory: 42 activities, 16 receivers, 11 services, 6 providers
(`evidence/manifest.xmltree`). Notable entries: `LauncherProvider`, `SecondaryDisplayLauncher`,
`SessionCommitReceiver`, `AppWidgetsRestoredReceiver`, `NovaAccessibilityService`,
`NotificationListener`, `RestoreBackupFileHandler`, `DrawerTabSetupActivity`,
`GroupAppListActivity`, and the bundled Sesame bridge
(`ninja.sesame.lib.bridge.v1.access.*`), Tesladirect licensing, Branch, Spotify SDK, Places.

**Item schema.** The `favorites` DDL is present verbatim in the dex:

```sql
CREATE TABLE favorites (_id INTEGER PRIMARY KEY,title TEXT,intent TEXT,container INTEGER,
screen INTEGER,cellX REAL,cellY REAL,spanX REAL,spanY REAL,itemType INTEGER,
appWidgetId INTEGER NOT NULL DEFAULT -1,iconPackage TEXT,iconResource TEXT,icon BLOB,
appWidgetProvider TEXT,modified INTEGER NOT NULL DEFAULT 0,restored INTEGER NOT NULL DEFAULT 0,
profileId INTEGER DEFAULT -1,rank INTEGER NOT NULL DEFAULT 0,options INTEGER NOT NULL DEFAULT 0,
appWidgetSource INTEGER NOT NULL DEFAULT -1, customIconSource TEXT, customIconLoadedState TEXT,
flingUpIntent TEXT,flingDownIntent TEXT,zOrder INTEGER DEFAULT 0,novaFlags INTEGER NOT NULL DEFAULT 0);
```

Sibling tables: `allapps`, `appgroups`, `drawer_groups`, `customIcons` (with `flags`,
`modified`, `tabColor`, `customIconSource`). Containers are the AOSP negatives
(`DESKTOP=-100`, `HOTSEAT=-101`) with folder children referencing the folder row id.

**Backup format.** `.novabackup` is a ZIP: `gd/m.java` writes `nova.xml` (prefs map, minus
`bug_report_userid`), `supportDetails.txt`, and the database files; restore reads `nova.xml` /
`com.teslacoilsw.launcher_preferences.xml` and unpacks to `restored/nova.db`. Declared MIME:
`application/vnd.novalauncher.backup`. Icon export is a separate `novaIconExport.zip`.

**Icon packs.** Discovery via `org.adw.launcher.THEMES`, `com.gau.go.launcherex.theme`,
`com.anddoes.launcher.THEME`; per-component mapping from `appfilter.xml`
(`ComponentInfo{pkg/cls}` → drawable); legacy `iconback`/`iconmask`/`iconupon` composition;
`content://$authority/theme_config` provider for masking and adaptive-icon overrides.

**Gestures.** String evidence lists pinch, swipe, two-finger swipe, rotate and double-tap, plus
swipe-edge and swipe-up/down actions (`swipeEdge`, `swipeToOpen`, `LAUNCHER_ALLAPPS_*`).

## What was rebuilt

`build/novarebuild` is a two-module Gradle project: the launcher (`dev.novare.launcher`) and a
synthetic ADW icon pack (`dev.novare.testpack`) used to prove pack loading.

| Nova evidence | Rebuild |
|---|---|
| `favorites` DDL + container scheme | `db/LauncherDb.kt` (same columns), `db/LauncherStore.kt` (folder rows reference parent id) |
| `drawer_groups` / `appgroups` | `DrawerTab`, tab bar, per-tab filtering |
| `customIcons` | `customIcons` table + SAF icon override in `IconCache` |
| `.novabackup` ZIP with `nova.xml` + DB | `BackupManager` ZIP with `meta.json`, `favorites.json`, `nova.xml` |
| `appfilter.xml`, iconback/mask/upon | `app/IconPackManager.kt`, `ui/ComposedIconDrawable.kt` |
| launcher3 `Workspace`/`CellLayout`/`DragLayer` | `ui/CellLayout.kt`, `ui/PagedView.kt`, `ui/DragLayer.kt`, `ui/DragController.kt` |
| swipe gestures | `DragLayer` vertical-swipe detection + `LauncherAccessibilityService` global action |

Deliberate deviations: item graph is serialized as JSON inside the backup ZIP (readable across
schema changes) instead of copying `nova.db`; resource names are not obfuscated; the launcher
targets `targetSdk 34` rather than 33.

## Verification ledger

Device A — Galaxy S23 (`RZCW91F71WX`, 1080×2340), screenshots and UI dumps in `evidence/verify`:

- Launch with 4-app dock seeded from `ACTION_DIAL` / `VIEW https` / `STILL_IMAGE_CAMERA` / `SENDTO`.
- App drawer lists all installed apps after `QUERY_ALL_PACKAGES`; search "calc" → Calculator, FocalCharge.
- Drag from drawer to workspace places the item at the drop cell (`drop-1.xml`).
- Drag icon onto icon creates a folder; DB: folder row `container=-100`, children `container=1`.
- Long-press icon → Edit / Change icon / App info / Uninstall / Remove; long-press empty → menu at touch point.
- Settings: Columns 5 → 4 applied and reflowed; 6 tested; backup section and dynamic icon-pack list render.
- Backup: wrote `nova-2026-11-11_23-02.novarebuild.zip` to Downloads (798 B, entries verified).
- Restore: after 6 columns + an extra desktop item, restore returned prefs to `workspace_columns=5`, removed the extra item, kept the 4 dock apps.
- Icon pack: `dev.novare.testpack` discovered, selected, Calculator icon rendered its drawable (magenta/cyan pixels at `(286,786)-(380,880)`).
- Drawer tab: created `utilities` (`drawer_groups` row), `appgroups` membership filtered the grid to Chrome.

Device B — Haptique RS90 (`ODA2101250116T0629`, Android 12, 480×800 @220 dpi): app runs with the
narrow-screen 4-column default; `dumpsys activity top` shows workspace `0..558`, dock 132 px,
search row 77 px. Icon size now clamps to the cell (`AppIconView.onMeasure`).

Not exercised on-device: widget hosting/resize (widgets are out of scope), page auto-add by edge
drag, delete-target drop, folder rename typing, and notification-shade expansion (the RS90 ROM
accepts `GLOBAL_ACTION_NOTIFICATIONS` but does not open the shade; gesture detection and service
invocation were observed).

## Layout

Analysis workstation layout (not published):

```
artifacts/   pulled APK
evidence/    REA inventory, manifest tree, dex headers, jadx log, runtime captures, verify/*
work/        decompiled sources (jadx) and extracted dex
build/       this Gradle project (app + testpack)
```
