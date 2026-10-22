# Our own screensaver, online pictures, now playing

Date: 2026-10-22. Fork `sthales/tv-detox`, building on the home additions spec (same TV: TCL G15, Google TV 14, 2.6 GB RAM, 32-bit, UI capped at 1920×1080 by `config_maxUiWidth`).

## Why

Since Google TV's home (`launcherx`) was turned off, Google's screensaver (`dreamx`) can't load or save its settings. The log shows `AccountModeContentProvider` / `AppsOnlyModeContentProvider` missing and `settings update failed`. It still runs with the old settings and costs about 160 MB while running.

The user wants:
- the screensaver to show their own wallpapers (a separate selection from the home's rotation) plus online pictures
- a cooler look
- one experience shared by the idle screensaver and Back on the home

They also want a small "now playing" chip on the home and the screensaver.

## Goals

1. **Our screensaver** (a `DreamService` in the home APK) replaces Google's as the TV's screensaver.
2. **Back on the home shows the same screensaver view**, replacing today's ambient view (wallpaper plus small clock).
3. **Slideshow:**
   - cycles the pictures chosen for the screensaver
   - cross-fades between them (~1.5 s)
   - slow zoom-and-pan drift ("Ken Burns") on each picture
   - change interval set on the TV: **1, 5, 15, 30 min**, default **5 min**
4. **Picture selection.** In Settings → Wallpaper, hold OK on any wallpaper → **Add to screensaver / Remove from screensaver**, marked ★, next to the home rotation's ↻. With nothing chosen and no online source on, it shows the home's current wallpaper.
5. **Online sources, no API keys**, each switchable on the TV:
   - **Places:** Bing image of the day, UHD
   - **Earth:** NASA EPIC natural-colour full disk
   - **Space:** NASA Image Library search results
   - Each picture shows a small caption: title and source.
6. **Clock** on the screensaver: large time plus date.
7. **Now-playing chip**, for whatever media session is playing (or the last one paused):
   - **On the home:** in the top-right icon row, left of the bell. A small glass chip shows album art (or the app icon), a tiny equalizer while playing, and "title · artist" on one line with an ellipsis. Next to it are two icon buttons, **⏯** and **⏭**, all reachable with the arrows. OK on the chip opens the playing app. The chip and buttons are hidden when nothing is playing or paused.
   - **On the screensaver:** display only (art, equalizer, title · artist), with no box, in soft text. Any key exits the screensaver, and the remote's media keys still reach the player.
8. **Screensaver settings on the TV:** Settings → **Screensaver** panel with:
   - change interval
   - Places on/off, Earth on/off, Space on/off
   - **Use as TV screensaver** on/off (writes `screensaver_components`, reversible)
   - **Preview**

## Non-goals

- Video or animated wallpapers, weather, and other widgets.
- NASA APOD, which needs a key.
- Google Earth View, which is unofficial.
- Changing the idle timeout or the 8-hour sleep, which stay the TV's own.
- Uninstalling or disabling `dreamx`. Switching the component is enough and reversible.

## Anti-goals

- Anything running in the background while an app or the PS5 is in front. Downloads happen only while the home is in front.
- Memory or stutter regressions: the home must keep passing the `perf` check (<10% janky, <20 ms median). The screensaver keeps at most two decoded pictures (current plus the one fading in), each about screen size plus the drift margin.
- Network traffic beyond the three image sources.
- Breaking the existing remap, bell, rotation or Delete.

## Facts and constraints

- `TclAppBoot` blocks third-party services from binding at boot unless the app has TCL's `AUTO_START` app-op. This is now allowed by `extras.sh`. Background jobs at boot aren't reliable, so the home itself triggers downloads when it resumes.
- Android only lets the system start a dream; an app can't call `startDream`. So Back on the home is the same *view* hosted inside the home activity, not the dream itself.
- Media sessions are readable with `MediaSessionManager.getActiveSessions(new ComponentName(this, NotifService.class))`, because the bell's notification listener is already granted.
- The app's own folder (`getExternalFilesDir`) holds `wallpapers/` and `banners/`, added in the rotation work. Online pictures go to `online/<source>/` there.
- Measured endpoints (2026-10-22, all no key):
  - `https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=pt-BR`: returns `images[].urlbase` (append `_UHD.jpg`) and `copyright` ("Title (© credit)").
  - `https://epic.gsfc.nasa.gov/api/natural`: returns `[{image, date, caption}]`. The PNG is at `https://epic.gsfc.nasa.gov/archive/natural/YYYY/MM/DD/png/<image>.png` (2048×2048, square).
  - `https://images-api.nasa.gov/search?q=<term>&media_type=image`: returns `collection.items[].data[0].title` and `links[0].href` (preview). The full image comes from the item's asset manifest (`~orig`/`~large`).

## Decisions (the user's)

- Our own screensaver replaces Google's. Back opens the same view.
- The screensaver has its own selection (★), separate from the home rotation (↻).
- Sources: Bing (Places), NASA EPIC (Earth), NASA Image Library (Space). No keys.
- Zoom-and-pan drift: yes.
- The chip has play/pause and skip.

## Decisions (Claude's; the user can override)

- **Interval choices** 1/5/15/30 min, default 5.
- **Home chip layout:** chip plus ⏯ ⏭ as separate focusable icons. OK on the chip opens the app.
- **EPIC pictures** are shown **fitted** (whole Earth on black), not cropped, because they're square full-disk images. Bing and NASA library pictures are cropped to fill.
- **Online refresh** at most once a day per enabled source, triggered when the home resumes. Keep the newest **8 per source** and delete older ones. On failure, keep what's there and try again next day.
- **NASA library search terms** rotate daily through: nebula, galaxy, hubble, webb, earth from iss, aurora. Take the first ~8 results with an image, using `~large` (about 1920 px), not `~orig`.
- **Order:** chosen local pictures and enabled online pictures shuffled once per screensaver start, then cycled.
- **Captions** only on online pictures: bottom-right, small, `title · Bing` / `· NASA`. Bing's title is the part of `copyright` before " (©".
- **Screensaver layout:** clock (large time, small date) bottom-left, now-playing under it, caption bottom-right. A dark gradient at the bottom keeps text readable.
- **`extras.sh`** sets `screensaver_components` to our dream, keeps the `AUTO_START` grant, and documents the one-line revert to `com.google.android.apps.tv.dreamx/.service.Backdrop`.
- **Permission:** add `INTERNET`.

## Settled after the builder check (Claude's calls)
- **Downloads** run on the app process's background executor:
  - Triggered when the home resumes or the dream starts.
  - Not cancelled when the home pauses.
  - Once-a-day per source on success; on failure, retry no sooner than 1 hour later.
- **The TV toggle "Use as TV screensaver" is the master:**
  - When it's on, the home re-applies `screensaver_components` (plus `screensaver_enabled=1`) in `onResume` if the value differs, the same way `healServices` repairs things.
  - `extras.sh` doesn't set the component; it only keeps the `WRITE_SECURE_SETTINGS` / `AUTO_START` grants.
  - Default after install: **on**, applied the first time the home starts with the new build.
- **Turning it off** restores the value saved the first time it was turned on (today Google's `dreamx/.service.Backdrop`). If nothing was saved, it falls back to Backdrop.
- **Media keys** (`MEDIA_PLAY_PAUSE`, `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREVIOUS`) control playback **without leaving** the screensaver or the Back view. Every other key exits.
  - The dream is **interactive** and forwards media keys to the active session's `MediaController.dispatchMediaButtonEvent`; any other key calls `finish()`.
- **Online sources default on** (Places, Earth, Space). The downloads test doesn't need to switch them on.

## Left to the builder

- Class split. Suggested: a `Showcase` view shared by `Dream` and the activity, an `OnlinePictures` downloader, and `NowPlaying` media-session glue.
- Animation details: drift amount around 8–12% zoom with a matching pan, kept inside the picture. The exact cross-fade easing.
- Exact sizes and colours, in the existing glass style.
- Tests:
  - pure-Java helpers in `Config` (Ken Burns start/end rectangles stay inside the picture, Bing caption parsing, interval parsing reuse), with unit tests
  - `ci/extras-test.sh` sections for: Back shows the showcase (keep the `ambient` marker), the dream is registered as the screensaver, online downloads land in the folder (with the network on), and the chip appears when a media session plays (e.g. start Spotify playback via `cmd media_session dispatch play`)

## Done means

On the TV, after a reboot:
1. The idle screensaver is ours and shows ★ wallpapers plus enabled online pictures with drift and cross-fades, a clock, and now-playing.
2. Back on the home shows the same view, and any key returns.
3. Screensaver settings change and **stay changed**.
4. The chip appears on the home while Spotify or YouTube plays. ⏯ and ⏭ work, and OK opens the app.
5. `perf` still passes, and the home's background memory doesn't grow by more than ~10 MB.
6. Turning "Use as TV screensaver" off brings Google's screensaver back.

## Addendum: home redesign and Control Center (user decision, 2026-10-22)

The user chose mockup **B (Apple TV style)** plus Control Center **C**.

### Home (top area)
- The big clock and the date are **removed**. The top-right icon row (Bluetooth, Sound, Picture, Network, bell) is **removed**.
- Top right is **one glass chip**: `[album art ⏯]` (only while something is playing or paused) + **time** + **avatar**.
  - The avatar shows initials on a gradient until a photo is provided (`avatar.sh <TV> photo.jpg` → `files/avatar.png`, cropped to a circle).
  - **OK on ⏯** toggles play/pause.
  - **OK on the time/avatar part** opens Control Center.

### Control Center
- Opens with the **cog button (key 83)** from anywhere (through the key map), and with OK on the chip.
- For now it is hosted by the home: from another app, the cog brings the home to the front with the panel open.
- A **frosted panel** on the right, about 31% of the width. Its background is the current wallpaper **blurred once** (scaled down and back up) and darkened. Nothing is blurred live.
- Contents, top to bottom:
  - avatar, name, **full date** and **time**
  - a **now-playing card** (art, app, title, artist, ⏮ ⏯ ⏭), only when there is a session
  - a **controls grid**, four per row:
    - **Wi-Fi** (shows connected/SSID state; opens network settings)
    - **Bluetooth**, **Sound**, **Picture** (TCL panels, as the old shortcuts did)
    - **Inputs** (TCL's input picker)
    - **Screensaver** (opens the screensaver view now)
    - **Alerts** (count; opens the notification list)
    - **Wallpaper**, **Settings** (TV settings), **Apps**
  - the **latest notification** in one line, then "+N more"
- Back or the cog closes it.
- Game mode and Sleep are **later**.

### Effects on the plan
- **Now playing:** shown in the chip and in Control Center. The separate home chip with ⏯ ⏭ in the icon row is dropped. The screensaver still shows its now-playing line.
- **The bell icon is gone.** The bell's list opens from the **Alerts** tile. Its count and "new" state move to that tile, with content-desc `Notifications N[ new]`.
- **Key map:** the cog's default target becomes `@control` (a home action), replacing `com.android.tv.settings`. `KeyService` launches the home with `--es open control` for `@`-targets.
