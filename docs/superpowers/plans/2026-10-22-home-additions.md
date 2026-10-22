# Home Additions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a TCL-button → Stremio remap, image wallpapers, a Back → ambient view, a notification bell and HDMI-tile hiding to the TV Detox home.

**Architecture:** Everything stays in the one plain-Java APK.
- Two new services sit next to `MainActivity`: an `AccessibilityService` for keys and a `NotificationListenerService` for the bell.
- Pure, Android-free parsing helpers go in `Config.java`, so they can be unit-tested with plain `java`.
- New behaviour is configured the same way the app list is today: `am start` extras saved by `saveConfig`, driven by two small scripts.

**Tech Stack:**
- Java 8 source level, Android SDK 34 (min 21), no libraries and no Gradle.
- `build.sh` (aapt2, javac, d8, apksigner), adb, uiautomator for on-device checks.

**Spec:** `docs/superpowers/specs/2026-10-22-home-additions-design.md`

## Global Constraints

- Target TV adb serial: `adb-8c0f7e15522a-GObuOk._adb-tls-connect._tcp`. Call it `$TV`; scripts accept it as-is.
- **Never run `ci/smoke-test.sh` against the real TV.** It ends with `undo.sh`, which re-enables Google TV's home and setup app. Use `ci/extras-test.sh` (Task 0) instead.
- **No new system apps turned off or on.** Only `com.ansub.tvhome` changes on the TV.
- Plain Java, no libraries, no Gradle. Match the existing style: `px()` scaling, `Strings.t()` for labels (new labels are appended to `EN` only), `LineIcon` for icons, and `panel()` / `dropdown()` for menus.
- No comments added beyond the codebase's existing density.
- The home's PSS stays around 30–40 MB with an image wallpaper.
- Prefs file `config` (existing `PREFS`). New keys: `keys`, `hidden_inputs`, `bell_seen`. `wallpaper` now holds a gradient name **or** `image:<filename>`.
- Image folder on the TV: `/sdcard/Pictures/tvhome/`.
- TCL button key code: **4020**. Stremio package: **`com.stremio.one`**. Default key map: `4020:com.stremio.one`.
- Build: `sh build.sh` → `out/tvhome.apk`, signed with `key.jks` (keep it; never commit it, it's in `.gitignore`).
- Work on branch `home-additions` in `/Users/sthales/Documents/Coding/gaming-tv/tv-detox`.

## Review Focus

1. **Mapped key whose app isn't installed** (e.g. Stremio uninstalled): no crash, and the key is not consumed, so the TV behaves as it would without the remap. Pinned in Task 1.
2. **Wallpaper image missing, unreadable, or permission revoked:** falls back to Dusk silently, and the Wallpaper list simply omits it. Pinned in Task 2.
3. **Huge image (e.g. 6000×4000 JPEG):** decoded with sampling to screen size, no OutOfMemoryError, and the home stays near 40 MB. Pinned in Task 2.
4. **Notification with no title, text or contentIntent:** the row still shows the app name, OK does nothing harmful, and nothing crashes. Pinned in Task 4.
5. **All HDMI tiles hidden, or a hidden id the TV no longer has:** the bottom row still lays out (Settings and Add app stay), and "Show hidden inputs" brings everything back. Pinned in Task 5.

---

### Task 0: Branch, unit-test harness, device-test harness, our build on the TV

**Files:**
- Create: `src/com/ansub/tvhome/Config.java`, `ci/ConfigTest.java`, `ci/unit-test.sh`, `ci/extras-test.sh`

**Interfaces:**
- Produces:
  - `final class Config`: no `android.*` imports; this task adds only the class shell.
  - `ci/unit-test.sh` compiles `Config.java` + `ci/ConfigTest.java` into a temp dir and runs `java -ea ConfigTest`, exiting non-zero on failure.
  - `ci/extras-test.sh <TV> [section]` holds shell functions `ui`, `focus`, `wait_home` (copied from `smoke-test.sh`), `fail`, plus one function per later task (`t_keys`, `t_wallpaper`, `t_ambient`, `t_bell`, `t_hdmi`) run in order, or only `section` if given.

- [ ] **Step 1:** `git checkout -b home-additions`.
- [ ] **Step 2:** Write `ci/ConfigTest.java` with `main` calling `assert true` once, and `ci/unit-test.sh` as described. Run: `sh ci/unit-test.sh`. Expected: `OK`.
- [ ] **Step 3:** Write `ci/extras-test.sh` with the helpers and an empty `t_baseline` that asserts Home resolves to `com.ansub.tvhome/.MainActivity` and the clock is in the ui dump.
- [ ] **Step 4:** `sh build.sh`. Expected: `Built out/tvhome.apk`.
- [ ] **Step 5:** Install our build, which replaces the release signature (the app order is lost and re-applied here): `GOOGLE_TV=1 YES=1 sh install.sh $TV "com.google.android.youtube.tv,com.stremio.one,com.spotify.tv.android,com.apple.atve.androidtv.appletv,com.netflix.ninja" "bluetooth,sound,picture"`. Expected: `Done. Press Home on your remote to see it.`
- [ ] **Step 6:** `sh ci/extras-test.sh $TV baseline`. Expected: PASS.
- [ ] **Step 7:** Commit (`ci/`, `Config.java`): `chore: unit and on-TV test harness for home additions`.

### Task 1: TCL button → Stremio (gated)

**Files:**
- Create: `src/com/ansub/tvhome/KeyService.java`, `res/xml/key_service.xml`, `res/values/strings.xml`, `extras.sh`
- Modify:
  - `AndroidManifest.xml`: the service, plus `<queries><package android:name="com.stremio.one"/></queries>`.
  - `build.sh`: compile `res/` with `aapt2 compile --dir res -o out/res.zip` and pass `out/res.zip` to `aapt2 link`.
  - `MainActivity.saveConfig`: accept a `keys` extra.
  - `Config.java`, `ci/ConfigTest.java`, `ci/extras-test.sh`.

**Interfaces:**
- Produces:
  - `static Map<Integer, String> Config.parseKeys(String s)`: input like `"4020:com.stremio.one,84:pkg.x"`. Malformed entries are skipped; null or empty returns an empty map.
  - Prefs `keys` holds the raw string.
  - `KeyService extends AccessibilityService`.
  - `extras.sh <TV> [keys]`, where `keys` defaults to `4020:com.stremio.one`.

- [ ] **Step 1: Failing unit test.** In `ConfigTest`, assert:
  - `parseKeys("4020:com.stremio.one").get(4020)` equals `"com.stremio.one"`
  - `parseKeys("4020:a, 84:b").size()==2`
  - `parseKeys("x:a,4020:,:b,4020").isEmpty()`
  - `parseKeys(null).isEmpty()`

  Run `sh ci/unit-test.sh` and expect a compile failure.
- [ ] **Step 2:** Implement `Config.parseKeys`. Run `sh ci/unit-test.sh` and expect `OK`.
- [ ] **Step 3: Failing device test.** Write `t_keys`:
  1. Run `sh extras.sh $TV`.
  2. Assert `settings get secure enabled_accessibility_services` contains `com.ansub.tvhome/.KeyService`.
  3. Open YouTube (`am start` its leanback intent), wait 4 s, then `input keyevent 4020`. Wait 4 s and assert `focus` contains `com.stremio.one`. This works because shell-injected keys pass through the accessibility input filter.
  4. Review Focus 1: `sh extras.sh $TV "4021:com.not.installed"`, then `input keyevent 4021` on the home; assert the home is still in front and there's no crash in `logcat -b crash`.
  5. Restore with `sh extras.sh $TV`.

  Run `sh ci/extras-test.sh $TV keys` and expect FAIL.
- [ ] **Step 4: Implement.**
  - **`res/xml/key_service.xml`:**
    - `accessibilityFlags="flagRequestFilterKeyEvents"`
    - `canRequestFilterKeyEvents="true"`
    - `accessibilityEventTypes="typeWindowStateChanged"`
    - `description="@string/key_service"`
  - **`KeyService.onKeyEvent`:** reads the map from prefs each time (it's tiny). For a mapped code with a launch intent, it consumes down and up and launches on `ACTION_UP` with `FLAG_ACTIVITY_NEW_TASK`. Anything unmapped or without a launch intent returns `false`. `onAccessibilityEvent` and `onInterrupt` are empty.
  - **`extras.sh`:** appends `com.ansub.tvhome/.KeyService` to `enabled_accessibility_services` (`:`-separated, no duplicates), sets `accessibility_enabled 1`, and sends `am start -n com.ansub.tvhome/.MainActivity --es keys "$KEYS" --es stamp $(date +%s)`.
- [ ] **Step 5:** `sh build.sh && sh install.sh $TV && sh ci/extras-test.sh $TV keys`. Expected: PASS.
- [ ] **Step 6: Gate, on the real remote.** Ask the user to press the TCL button on the home, inside YouTube, and on the PS5 input. Expected: Stremio each time, and Netflix/Prime/YouTube/Disney+ buttons and the quick panel (key 4514) unchanged.
  - **If the key never reaches the service:** move the lookup into `MainActivity.onKeyDown` (home only), keep `KeyService` out of the manifest, and tell the user it works on the home only.
- [ ] **Step 7:** Commit: `feat: remap remote keys to apps (TCL button -> Stremio)`.

### Task 2: Image wallpaper

**Files:**
- Create: `wallpaper.sh`
- Modify:
  - `AndroidManifest.xml`: `READ_MEDIA_IMAGES`, plus `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"`.
  - `MainActivity`: `wallpaper()`, `showWallpaper()`, `showWallpapers()`, `saveConfig`.
  - `Config.java`, `ci/ConfigTest.java`, `ci/extras-test.sh`.

**Interfaces:**
- Produces:
  - `static boolean Config.isImage(String filename)`: true for `.jpg`, `.jpeg`, `.png` and `.webp`, case-insensitive.
  - The prefs `wallpaper` value `image:<filename>`.
  - The root scroller's `contentDescription` set to `"wallpaper:" + <saved value>` (`Dusk` when nothing is saved), for testing.
  - `wallpaper.sh <TV> <image>`.

- [ ] **Step 1: Failing unit test.** Assert:
  - `isImage("a.JPG")` and `isImage("b.webp")` are true
  - `isImage("c.gif")` and `isImage(".jpg.txt")` are false

  Run and expect FAIL.
- [ ] **Step 2:** Implement `Config.isImage`. Run `sh ci/unit-test.sh` and expect `OK`.
- [ ] **Step 3: Failing device test.** Write `t_wallpaper`:
  1. Generate `/tmp/tvhome-test.png` (a 1920×1080 solid colour via `sips` or a tiny PNG checked into `ci/`).
  2. Run `sh wallpaper.sh $TV /tmp/tvhome-test.png`, then press Home.
  3. Assert the ui dump has `content-desc="wallpaper:image:tvhome-test.png"`.
  4. Review Focus 2: `adb shell rm /sdcard/Pictures/tvhome/tvhome-test.png`, then `am force-stop com.ansub.tvhome` and press Home. Assert the home is in front with no crash, and the screenshot's top-left pixel is not the test colour.
  5. Review Focus 3: push a 6000×4000 JPEG made with `sips -z 4000 6000`, select it, and assert there's no crash and `dumpsys meminfo com.ansub.tvhome` TOTAL PSS is below 60000 KB.

  Run and expect FAIL.
- [ ] **Step 4: Implement.**
  - **`wallpaper.sh`:** pushes to `/sdcard/Pictures/tvhome/<basename>` and runs `pm grant com.ansub.tvhome android.permission.READ_MEDIA_IMAGES` (or `READ_EXTERNAL_STORAGE` when SDK < 33). Then it sends `--es wallpaper "image:<basename>" --es stamp ...`.
  - **`saveConfig`:** stores `wallpaper` as given.
  - **`showWallpaper`:** for `image:` values, decodes with `BitmapFactory.Options.inSampleSize` chosen so the image is ≥ screen size, then center-crops to the screen into one bitmap. On top it layers the readability fade as two `GradientDrawable`s:
    - top-left radial, black about 55%
    - bottom linear, black about 60% → transparent over the lower 45%

    Any failure falls back to Dusk.
  - **`showWallpapers()`:** lists the gradients, then each `Config.isImage` file in the folder by filename, with preview and OK/Back as today. Images get a small thumbnail dot from the same decode at 40 px.
- [ ] **Step 5:** Build, install, re-run `sh extras.sh $TV`, then `sh ci/extras-test.sh $TV wallpaper`. Expected: PASS.
- [ ] **Step 6: Manual check.** The user pushes their own image and checks it's readable and selectable in Settings → Wallpaper.
- [ ] **Step 7:** Commit: `feat: image wallpapers pushed from the terminal`.

### Task 3: Back → ambient view

**Files:**
- Modify: `MainActivity` (`onBackPressed`, `dispatchKeyEvent`), `ci/extras-test.sh`.

**Interfaces:**
- Produces:
  - `private boolean ambient`.
  - `void showAmbient(boolean on)` hides or shows the scroller's page content and a bottom-right dimmed `TextClock` (24 h/12 h formats like the main clock, about `px(56)`, alpha `0x99`).
  - The ambient clock's `contentDescription` is `"ambient"`.

- [ ] **Step 1: Failing device test.** Write `t_ambient`:
  1. Press Home, then `input keyevent KEYCODE_BACK`.
  2. Assert the ui dump contains `content-desc="ambient"` and no `text="Settings"`.
  3. `input keyevent KEYCODE_DPAD_CENTER`, then assert `text="Settings"` is back, `ambient` is gone, and `focus` is still the home (OK was consumed and didn't open a tile).
  4. Back twice: assert ambient after the first, and home content after the second.

  Run and expect FAIL.
- [ ] **Step 2: Implement.** `onBackPressed` calls `showAmbient(true)` when not ambient. While ambient, `dispatchKeyEvent` consumes every key's down and up, and calls `showAmbient(false)` on up. Leaving the activity (`onPause`) also clears ambient.
- [ ] **Step 3:** Build, install, `sh ci/extras-test.sh $TV ambient`. Expected: PASS.
- [ ] **Step 4:** Commit: `feat: Back on the home shows an ambient view`.

### Task 4: Notification bell

**Files:**
- Create: `src/com/ansub/tvhome/NotifService.java`
- Modify:
  - `AndroidManifest.xml`: the service with `BIND_NOTIFICATION_LISTENER_SERVICE`.
  - `LineIcon.java`: add a `BELL` op list.
  - `MainActivity`: add the icon in `buildTiles`, the panel, and the dot.
  - `Strings.java`: append to `EN` only: `"Notifications"`, `"No notifications"`, `"Clear all"`.
  - `extras.sh`: `cmd notification allow_listener com.ansub.tvhome/.NotifService`.
  - `ci/extras-test.sh`.

**Interfaces:**
- Produces:
  - `NotifService extends NotificationListenerService` with:
    - `static volatile NotifService live`
    - `static Runnable onChange`
    - `static List<StatusBarNotification> list()`: newest first, group summaries skipped, empty when not connected
    - `static long lastPosted()`
  - It calls `onChange` on the main thread for posted, removed and connected events.
  - The bell icon's `contentDescription` is `"Notifications " + count + (unseen ? " new" : "")`. Unseen means `lastPosted() > prefs bell_seen`.
  - `NOTIFS = "notifs"` is the bell's tile key.

- [ ] **Step 1: Failing device test.** Write `t_bell`:
  1. Run `sh extras.sh $TV`, press Home, and assert the ui dump has `content-desc="Notifications ` with a count ≥ 1 (debugging is always present while testing).
  2. `cmd notification post -S bigtext -t "tvhome test" tvhome-test "hello"`. Assert the content-desc ends with ` new`.
  3. Review Focus 4: `cmd notification post tvhome-empty ""` (no title, no intent), then assert there's no crash.
  4. `cmd notification unpost tvhome-test`, and the same for `tvhome-empty`.
  5. Revoke with `cmd notification disallow_listener com.ansub.tvhome/.NotifService`, press Home, and assert there's no `content-desc="Notifications`. Re-allow afterwards.

  Run and expect FAIL.
- [ ] **Step 2: Implement.**
  - **Bell icon:** added with `addIcon(NOTIFS, ...)` before the network icon, only when `NotifService.live != null`. The dot is a small filled circle layered in the icon's top-right while unseen.
  - **Open:** OK opens `panel(Strings.t("Notifications"), rows, ...)`. Each row reads `app label — title — text` and falls back to the app label alone. A final row reads `Clear all`, and an empty list shows a single `No notifications` row. Opening the panel saves `bell_seen = now`.
  - **Pick a row:** sends its `contentIntent` (if any, catching `CanceledException`). If clearable, it also `cancelNotification(key)`.
  - **Clear all:** `cancelAllNotifications()`.
  - **Live updates:** `onChange` refreshes the icon only, with no tile rebuild.
- [ ] **Step 3:** Build, install, `sh extras.sh $TV`, `sh ci/extras-test.sh $TV bell`. Expected: PASS.
- [ ] **Step 4: Manual check.** The panel lists AirPlay and Wireless debugging. OK on a test notification opens or clears it.
- [ ] **Step 5:** Commit: `feat: notification bell on the home`.

### Task 5: Hide HDMI tiles

**Files:**
- Modify:
  - `MainActivity`: `addTile` editability, `hdmiInputs()`, `SETTINGS_ITEMS`, the menu handling.
  - `Strings.java`: append to `EN`: `"Hide"`, `"Show hidden inputs"`.
  - `Config.java`, `ci/ConfigTest.java`, `ci/extras-test.sh`.

**Interfaces:**
- Produces:
  - `static Set<String> Config.splitSet(String s)` and `static String Config.joinSet(Set<String> s)`, comma-separated and order-stable (`LinkedHashSet`). Null or empty gives an empty set.
  - Prefs `hidden_inputs`.
  - An `am start --es hidden_inputs "<ids>"` extra (also used by the test). `"none"` clears it.

- [ ] **Step 1: Failing unit test.**
  - `splitSet("a,b,a")` equals `{a,b}`
  - `joinSet(splitSet("x,y"))` equals `"x,y"`
  - `splitSet("")` is empty
  - `splitSet(null)` is empty

  Run and expect FAIL.
- [ ] **Step 2:** Implement both. Run `sh ci/unit-test.sh` and expect `OK`.
- [ ] **Step 3: Failing device test.** Write `t_hdmi`:
  1. Count `HDMI` texts in the ui dump (expect 4).
  2. Send `--es hidden_inputs "com.tcl.tvinput/.TvPassThroughService/HW16,com.tcl.tvinput/.TvPassThroughService/HW17,com.tcl.tvinput/.TvPassThroughService/HW18"` and assert 1 HDMI tile remains.
  3. Review Focus 5: send all four ids plus `"bogus/id"` and assert 0 HDMI tiles, with `text="Settings"` and `Add app` still present.
  4. Send `none` and assert 4 again.

  Run and expect FAIL.
- [ ] **Step 4: Implement.**
  - **Menu on HDMI tiles:** hold OK / Menu on an HDMI tile opens `dropdown` with `Hide`, which adds its id to `hidden_inputs` and rebuilds.
  - **Filtering:** `hdmiInputs()` skips saved ids, as well as `isHidden` inputs.
  - **Restoring:** `SETTINGS_ITEMS` gains `Show hidden inputs`, shown only when the set is non-empty; it clears the set.
  - **`saveConfig`:** handles `hidden_inputs`.
- [ ] **Step 5:** Build, install, `sh ci/extras-test.sh $TV hdmi`. Expected: PASS.
- [ ] **Step 6: Manual check.** Hide an empty HDMI port with the remote, then restore it from Settings.
- [ ] **Step 7:** Commit: `feat: hide HDMI tiles from the home`.

### Task 6: Full pass, reboot, record

**Files:**
- Modify: `/Users/sthales/Documents/Coding/gaming-tv/my-tv.txt` (outside the repo), `README.md` (a short "Fork additions" section listing `extras.sh` and `wallpaper.sh`).

- [ ] **Step 1:** `sh ci/unit-test.sh && sh ci/extras-test.sh $TV`. Expected: all sections PASS.
- [ ] **Step 2:** Reboot the TV. The user re-enables Wireless debugging. Re-run `sh ci/extras-test.sh $TV`. Expected: PASS, which proves the accessibility service, listener grant and prefs survive a reboot.
- [ ] **Step 3:** Run `dumpsys meminfo com.ansub.tvhome` with the user's image wallpaper set. Expected: TOTAL PSS ≤ about 40 MB.
- [ ] **Step 4: Manual checklist with the user.**
  - TCL → Stremio, from 3 places
  - other app buttons
  - quick panel
  - PS5 input
  - AirPlay
  - phone remote and Home Assistant
  - bell
  - Back → ambient
  - hiding an HDMI port
- [ ] **Step 5:** Append to `my-tv.txt`:
  - `secure accessibility_enabled 1`
  - a comment noting `extras.sh` must be re-run after a reinstall
- [ ] **Step 6:** Commit: `docs: fork additions in README`.
