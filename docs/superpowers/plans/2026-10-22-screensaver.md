# Screensaver, online pictures, now playing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Google's screensaver with the home's own slideshow (★ wallpapers plus online Places/Earth/Space pictures, drift, cross-fade, clock, now playing). Show the same view on Back, and add a now-playing chip with ⏯ ⏭ to the home.

**Architecture:**
- **`Showcase`** is a FrameLayout. It shows two ImageViews cross-fading, each animated by a Matrix along `Config.drift`, plus the clock, caption and now-playing line. It's hosted by the home (Back) and by the new **`Dream`** (a `DreamService`).
- **`OnlinePictures`** downloads into `getExternalFilesDir()/online/<src>/` on a static executor, once a day.
- **`NowPlaying`** wraps `MediaSessionManager` through `NotifService`'s listener permission.

**Tech Stack:** Plain Java (Android SDK 34, min 21), no libraries, built with `build.sh`. Uses `HttpURLConnection` and `org.json` (in the platform).

**Spec:** `docs/superpowers/specs/2026-10-22-screensaver-design.md`

## Global Constraints
- **TV:** adb serial `adb-8c0f7e15522a-GObuOk._adb-tls-connect._tcp`. Never run `ci/smoke-test.sh` on it.
- **Smoothness:** the home must keep passing `ci/extras-test.sh <TV> perf`.
- **Memory:** at most two full pictures decoded in the showcase. Online keeps the newest 8 per source.
- **Prefs** (file `config`):
  - `showcase` (★ names, comma-set)
  - `showcase_every` (`1m|5m|15m|30m`, default `5m`)
  - `src_places` / `src_earth` / `src_space` (boolean, default true)
  - `tv_saver` (boolean, default true)
  - `tv_saver_prev` (string)
  - `online_<src>_at` (long, last success)
  - `online_<src>_try` (long, last attempt)
- **Extras** (for scripts and tests): `--es showcase "<names>|none"`, `--es showcase_every 1m`, `--es sources "places,earth,space|none"`, `--es tv_saver on|off`.
- **Labels:** new ones go in `Strings` EN only.
- **Dream component:** `com.ansub.tvhome/.Dream`. Google's: `com.google.android.apps.tv.dreamx/.service.Backdrop`.

## Review Focus
1. **The network is down or a source returns garbage:** no crash. The slideshow uses what it has (local ★ or the current wallpaper), and the retry waits ≥1 h. Pinned in Task 4.
2. **No ★ and every online source off:** the showcase shows the home's current wallpaper, still drifting. Pinned in Task 1.
3. **A media session with no metadata or art:** the chip shows the app's label and icon, and nothing crashes. Pinned in Task 5.
4. **The user switched Google's screensaver back by other means:** the home only re-applies ours while `tv_saver` is on, and turning it off restores the saved value. Pinned in Task 3.
5. **A key arrives during a cross-fade:** the dream or Back view exits cleanly, with no leaked animators or callbacks (`onDetachedFromWindow` stops everything). Pinned in Task 1.

---

### Task 0: Home redesign and Control Center (addendum in the spec)

**Files:**
- Create: `src/com/ansub/tvhome/ControlCenter.java` (the panel builder)
- Modify:
  - `MainActivity`: remove the big clock and date and the top-right icons; add the chip (time + avatar, plus a now-playing slot filled in Task 5); a blurred-wallpaper helper; `open=control` handling; `onKeyUp(83)` opens Control Center when the home is in front
  - `KeyService`: `@`-targets
  - `extras.sh`: default `83:@control`
  - `Strings`, `LineIcon`: icons for Wi-Fi, Inputs, Screensaver, Alerts, Wallpaper
  - `ci/extras-test.sh`: new `t_control`; `t_bell` reads the Alerts tile in Control Center
  - Create `avatar.sh`
- **Markers:**
  - chip content-desc `chip`
  - panel root `control-center`
  - Alerts tile `Notifications N[ new]`
  - controls by their text: `Wi-Fi`, `Bluetooth`, `Sound`, `Picture`, `Inputs`, `Screensaver`, `Alerts`, `Wallpaper`, `Settings`, `Apps`

- [ ] **Step 1:** Write `t_control`:
  1. home → `chip` is present and there's no large clock text (the clock is only in the chip).
  2. `input keyevent 83` → `control-center` is present, every control label is present, and the full date (`yyyy`) is shown.
  3. BACK → closed.
  4. `am start --es open control` → open.

  Update `t_bell` to open Control Center first. Watch them FAIL.
- [ ] **Step 2:** Implement. The panel is a `Dialog` with gravity END and a background of the blurred wallpaper bitmap (scaled to 1/16 and back up with filtering, then darkened). The tiles reuse the glass focus style.
- [ ] **Step 3:** The tests and `perf` PASS. Commit: `feat: Apple TV style home and Control Center`.

### Task 1: Showcase view on Back

**Files:**
- Create: `src/com/ansub/tvhome/Showcase.java`
- Modify: `MainActivity` (the ambient view becomes the Showcase), `ci/extras-test.sh` (`t_ambient` keeps the `ambient` marker; new `t_showcase`)

**Interfaces:**
- Produces: `Showcase(Context, Host host)`
- Produces: `interface Host { List<Source> pictures(); Bitmap decode(String path, int w, int h, boolean fit); }`
- Produces: `Showcase.start()`, `Showcase.stop()`
- Produces: `static final class Source { String path; String caption; boolean fit; }`

The root's contentDescription is `"ambient"`, and the current caption is in the contentDescription of a child as `"caption:<text>"`.

- [ ] **Step 1:** Write `t_showcase`. With `--es showcase Dusk,Ocean --es showcase_every 10s`:
  - Back → `ambient` is present.
  - After 13 s the shown slide changes. The slide's contentDescription is `slide:<name>`.
  - Any key leaves and the Settings text is back.
  - Run it and watch it FAIL.
- [ ] **Step 2:** Implement `Showcase`:
  - Two ImageViews with `ScaleType.MATRIX`. A ValueAnimator drives a Matrix from `Config.drift(i)` across the interval, plus a 1500 ms alpha cross-fade.
  - Clock: a large `TextClock` bottom-left, with the date above it.
  - Caption: bottom-right.
  - A bottom gradient for readability.
  - Decoding happens on `MainActivity.decoder`, at screen size × `drift` max scale, using the `fit` flag for EPIC.
  - `MainActivity.showAmbient(true)` adds a Showcase over the page and calls `start()`. Leaving stops it and removes it.
  - Media keys pass through (see Task 5); other keys exit.
- [ ] **Step 3:** Run `sh ci/extras-test.sh $TV ambient` and `showcase`, and `perf` → PASS. Commit: `feat: showcase slideshow on Back`.

### Task 2: Screensaver selection and settings panel

**Files:** `MainActivity` (wallpaper hold menu ★, a Settings dropdown item "Screensaver", the panel), `Strings`, `ci/extras-test.sh` (`t_saver_settings`)

- [ ] **Step 1:** Write `t_saver_settings`. Via extras, set `showcase_every 1m` and `sources none`, then open Settings → Screensaver with keys and assert:
  - the rows `Change picture: every 1 min`, `Places: Off`, `Earth: Off`, `Space: Off`, `Use as TV screensaver`, `Preview` exist
  - hold OK on a wallpaper shows `Add to screensaver`

  Watch it FAIL.
- [ ] **Step 2:** Implement:
  - The hold menu adds `Add to screensaver` / `Remove from screensaver`, and the label shows ★.
  - The Screensaver panel rows toggle the prefs; Preview calls `showAmbient(true)`.
  - Extras are handled in `saveConfig`.
- [ ] **Step 3:** Tests PASS. Commit: `feat: screensaver selection and settings`.

### Task 3: Dream service and TV screensaver toggle

**Files:**
- Create: `src/com/ansub/tvhome/Dream.java`
- Modify: `AndroidManifest.xml` (the service with `BIND_DREAM_SERVICE`), `MainActivity.healServices` (re-apply), `ci/extras-test.sh` (`t_dream`)

- [ ] **Step 1:** Write `t_dream`:
  - After home start, `settings get secure screensaver_components` = `com.ansub.tvhome/.Dream`.
  - `--es tv_saver off` → it equals the saved previous value.
  - `--es tv_saver on` → ours again.

  Watch it FAIL.
- [ ] **Step 2:** Implement:
  - `Dream`: interactive, fullscreen, `setContentView(new Showcase(...))`.
  - Keys: media keys go to `NowPlaying.dispatch`, others call `finish()`.
  - The home saves `tv_saver_prev` before the first write and restores it on off.
- [ ] **Step 3:** Tests PASS. Commit: `feat: our own screensaver`.

### Task 4: Online pictures

**Files:**
- Create: `src/com/ansub/tvhome/OnlinePictures.java`
- Modify: `AndroidManifest.xml` (`INTERNET`), `MainActivity` (trigger in `onResume`), `Dream` (trigger on start), `Showcase` sources, `ci/extras-test.sh` (`t_online`)

**Interfaces:**
- Produces: `static void OnlinePictures.refresh(Context)`, which is idempotent and once a day per source
- Produces: `static List<Showcase.Source> OnlinePictures.pictures(Context)`
- Captions are stored next to the files as `<file>.txt`.

- [ ] **Step 1:** Write `t_online`:
  - Wipe the online folder and `online_*_at` (via `--es online_reset 1`), then start the home and wait ≤60 s.
  - Assert there's ≥1 file in each of `online/places`, `online/earth`, `online/space`, each with a `.txt` caption.
  - Assert the files are ≤8 per source.
  - Network off: turn Wi-Fi off and back on with `cmd wifi set-wifi-enabled`. *Unsafe on wireless adb, so this part is skipped; it's covered by the code reading in Review Focus 1.*

  Watch it FAIL.
- [ ] **Step 2:** Implement the three fetchers with `HttpURLConnection` (10 s timeouts), `org.json`, `Config.bingTitle`, and the NASA term of the day. Downloads go to a temp file, then a rename. Prune to the newest 8.
- [ ] **Step 3:** Tests PASS. Commit: `feat: online Places, Earth and Space pictures`.

### Task 5: Now playing (in the chip, Control Center and the screensaver; the old icon-row chip is dropped)

**Files:**
- Create: `src/com/ansub/tvhome/NowPlaying.java`
- Modify: `MainActivity` (chip plus ⏯ ⏭ in the icon row), `Showcase` (now-playing line), `LineIcon` (`PLAY_PAUSE`, `NEXT`), `ci/extras-test.sh` (`t_nowplaying`)

**Interfaces:**
- Produces: `NowPlaying.current(Context)` → `MediaController` or null
- Produces: `NowPlaying.listen(Context, Runnable onChange)` / `unlisten()`
- Produces: `NowPlaying.dispatch(Context, KeyEvent)` → boolean

- [ ] **Step 1:** Write `t_nowplaying`:
  - With nothing playing, there's no `content-desc="nowplaying"`.
  - Start a YouTube session (open YouTube, play its first item with `input keyevent DPAD_CENTER` twice, wait), then go home and assert `nowplaying` is present and `⏯` (content-desc `playpause`) is present.
  - `playpause` → `dumpsys media_session` state flips.

  Watch it FAIL.
- [ ] **Step 2:** Implement the listener on active sessions, plus metadata and state callbacks:
  - **Chip:** glass, with art (64 px), an equalizer (three bars animated only while playing), and "title · artist".
  - **Buttons:** ⏯ ⏭ as `addIcon` entries.
  - **OK on the chip:** launches `controller.getPackageName()`.
  - **Showcase line:** under the clock.
- [ ] **Step 3:** Tests PASS, and `perf` PASS. Commit: `feat: now playing on the home and the screensaver`.

### Task 6: Install session

- [ ] **Step 1:** Install, run `extras.sh`, and migrate the pictures:
  - `mv /sdcard/Pictures/tvhome/*.{jpg,png}` → `files/wallpapers/`
  - `mv /sdcard/Pictures/tvhome/banners/*` → `files/banners/`
  - `chmod 666` the moved files
- [ ] **Step 2:** Run the full `ci/extras-test.sh` → PASS.
- [ ] **Step 3:** Reboot, then run `postboot` → PASS, then confirm the dream is still ours.
- [ ] **Step 4:** Manual check with the user: idle screensaver, Back view, ★ selection, chip ⏯ ⏭.
- [ ] **Step 5:** Final review via code-reviewer (opus).
